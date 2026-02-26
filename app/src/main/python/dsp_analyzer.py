"""
ML-based DSP Analyzer for TV Screen Crack Detection
Uses TFLite for high-performance CNN inference.
No external dependencies beyond NumPy and tflite-runtime (supported by Chaquopy).
"""

import os
import wave
import struct
import numpy as np
try:
    import tflite_runtime.interpreter as tflite
except ImportError:
    try:
        import tensorflow.lite as tflite
    except ImportError:
        # Fallback for systems without either, will crash only when used
        tflite = None

# Constants matching 96.6% CNN training parameters
SAMPLE_RATE = 22050
DURATION = 3
SAMPLES = SAMPLE_RATE * DURATION
N_FFT = 2048
HOP_LENGTH = 512
N_MELS = 128

# ============================================================
# DSP Utilities
# ============================================================

# ============================================================
# Noise Cancellation (spectral gating + high-pass, numpy-only)
# ============================================================

def _stft(signal, n_fft=2048, hop_length=512):
    """Short-Time Fourier Transform."""
    pad_len = n_fft // 2
    padded = np.pad(signal, pad_len, mode='reflect')
    n_frames = 1 + (len(padded) - n_fft) // hop_length
    window = 0.5 * (1 - np.cos(2 * np.pi * np.arange(n_fft) / n_fft))  # Hann
    frames = np.zeros((n_frames, n_fft))
    for i in range(n_frames):
        frames[i] = padded[i * hop_length : i * hop_length + n_fft] * window
    return np.fft.rfft(frames, n=n_fft)  # (n_frames, n_fft//2+1)


def _istft(stft_matrix, hop_length=512, n_fft=2048, target_length=None):
    """Inverse STFT with overlap-add."""
    window = 0.5 * (1 - np.cos(2 * np.pi * np.arange(n_fft) / n_fft))
    n_frames = stft_matrix.shape[0]
    expected_len = n_fft + hop_length * (n_frames - 1)
    output = np.zeros(expected_len)
    window_sum = np.zeros(expected_len)
    for i in range(n_frames):
        frame = np.fft.irfft(stft_matrix[i], n=n_fft) * window
        start = i * hop_length
        output[start:start + n_fft] += frame
        window_sum[start:start + n_fft] += window ** 2
    # Normalize by window overlap
    nonzero = window_sum > 1e-8
    output[nonzero] /= window_sum[nonzero]
    # Remove padding from STFT
    pad_len = n_fft // 2
    output = output[pad_len:]
    if target_length is not None:
        output = output[:target_length]
    return output


def bandpass_filter(signal, sr, lowcut=800, highcut=14000, order=4):
    """Band-pass filter using frequency-domain gain curve (numpy-only).
    
    Focuses on 800Hz - 14kHz to isolate glass tapping 'pings' 
    and reject room talk/background rumble.
    """
    n = len(signal)
    if n == 0: return signal
    freqs = np.fft.rfftfreq(n, d=1.0 / sr)
    spectrum = np.fft.rfft(signal)
    
    # Butterworth-like roll-off in frequency domain
    # Low-cut (high-pass part)
    low_gain = 1.0 / np.sqrt(1.0 + (lowcut / (freqs + 1e-10)) ** (2 * order))
    # High-cut (low-pass part)
    high_gain = 1.0 / np.sqrt(1.0 + (freqs / (highcut + 1e-10)) ** (2 * order))
    
    spectrum *= (low_gain * high_gain)
    return np.fft.irfft(spectrum, n=n)


def proximity_gate(signal, threshold_ratio=0.2, window_ms=20, sr=22050):
    """Silences any window where peak amplitude is < threshold_ratio of the total peak.
    
    Effectively mutes background sounds between the loud taps.
    """
    if len(signal) == 0: return signal
    
    window_size = int((window_ms / 1000.0) * sr)
    max_amp = np.max(np.abs(signal))
    threshold = max_amp * threshold_ratio
    
    gated_signal = np.zeros_like(signal)
    for i in range(0, len(signal), window_size):
        end = min(i + window_size, len(signal))
        window = signal[i:end]
        if np.max(np.abs(window)) > threshold:
            gated_signal[i:end] = window
            
    return gated_signal


def noise_cancel(signal, sr, noise_duration=0.5, n_fft=2048, hop_length=512,
                 oversubtract=2.0, floor=0.1):
    """
    Spectral-gate noise reduction.

    Uses the first `noise_duration` seconds as a noise profile estimate
    (before the ESP tap begins), then applies a soft Wiener-style mask.

    Parameters
    ----------
    signal : np.ndarray       – mono float32 signal in [-1, 1]
    sr : int                  – sample rate
    noise_duration : float    – seconds of leading audio used as noise reference
    oversubtract : float      – noise oversubtraction factor (higher = more aggressive)
    floor : float             – spectral floor to avoid musical noise artifacts
    """
    if len(signal) == 0:
        return signal

    # 1. STFT of full signal
    S = _stft(signal, n_fft, hop_length)         # (n_frames, freq_bins)
    mag = np.abs(S)
    phase = np.angle(S)

    # 2. Estimate noise spectrum from first N frames
    noise_samples = int(noise_duration * sr)
    noise_frames = max(1, noise_samples // hop_length)
    noise_profile = np.mean(mag[:noise_frames], axis=0)  # average noise magnitude

    # 3. Spectral subtraction with soft mask
    #    mask = max(floor, 1 - oversubtract * noise / signal)
    mask = 1.0 - oversubtract * (noise_profile[np.newaxis, :] / (mag + 1e-10))
    mask = np.clip(mask, floor, 1.0)

    # 4. Apply mask and reconstruct
    cleaned_mag = mag * mask
    cleaned_S = cleaned_mag * np.exp(1j * phase)

    return _istft(cleaned_S, hop_length, n_fft, target_length=len(signal))


def save_denoised_wav(signal, sr, original_path):
    """Save denoised signal as WAV next to the original file.

    Returns the path of the saved denoised file.
    """
    base, ext = os.path.splitext(original_path)
    denoised_path = base + "_denoised" + ext

    # Convert float32 [-1,1] to int16
    int_signal = np.clip(signal * 32767, -32768, 32767).astype(np.int16)

    import wave as _wave
    with _wave.open(denoised_path, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)  # 16-bit
        wf.setframerate(sr)
        wf.writeframes(int_signal.tobytes())

    return denoised_path


def read_wav_normalized(filepath):
    """Read WAV file and return float32 samples normalized to [-1, 1]."""
    with wave.open(filepath, 'rb') as wav:
        n_channels = wav.getnchannels()
        sample_width = wav.getsampwidth()
        framerate = wav.getframerate()
        n_frames = wav.getnframes()
        raw_data = wav.readframes(n_frames)
        
        if sample_width == 2:
            fmt = '<{}h'.format(n_frames * n_channels)
            samples = np.array(struct.unpack(fmt, raw_data), dtype=np.float32)
        elif sample_width == 1:
            samples = np.array(list(raw_data), dtype=np.float32) - 128.0
            samples = samples / 128.0 * 32768.0
        else:
            return np.zeros(0), 0
        
        if n_channels == 2:
            samples = samples[::2]
        
        samples = samples / 32768.0
        return samples, framerate

def resample(signal, orig_sr, target_sr):
    if orig_sr == target_sr: return signal
    ratio = target_sr / orig_sr
    new_length = int(len(signal) * ratio)
    indices = np.arange(new_length) / ratio
    left = np.floor(indices).astype(int)
    right = np.minimum(left + 1, len(signal) - 1)
    frac = indices - left
    return signal[left] * (1 - frac) + signal[right] * frac

def hz_to_mel(hz):
    f_min, f_sp, min_log_hz, min_log_mel, logstep = 0.0, 200.0/3, 1000.0, 15.0, np.log(6.4)/27.0
    if np.isscalar(hz):
        return min_log_mel + np.log(hz/min_log_hz)/logstep if hz >= min_log_hz else (hz-f_min)/f_sp
    return np.where(hz >= min_log_hz, min_log_mel + np.log(hz/min_log_hz)/logstep, (hz-f_min)/f_sp)

def mel_to_hz(mel):
    f_min, f_sp, min_log_hz, min_log_mel, logstep = 0.0, 200.0/3, 1000.0, 15.0, np.log(6.4)/27.0
    if np.isscalar(mel):
        return min_log_hz * np.exp(logstep*(mel-min_log_mel)) if mel >= min_log_mel else f_min + f_sp*mel
    return np.where(mel >= min_log_mel, min_log_hz * np.exp(logstep*(mel-min_log_mel)), f_min + f_sp*mel)

def create_mel_filterbank(sr, n_fft, n_mels):
    mel_points = np.linspace(hz_to_mel(0.0), hz_to_mel(sr/2.0), n_mels + 2)
    hz_points = mel_to_hz(mel_points)
    fft_freqs = np.linspace(0, sr / 2.0, n_fft // 2 + 1)
    filterbank = np.zeros((n_mels, n_fft // 2 + 1))
    for i in range(n_mels):
        rising = (fft_freqs - hz_points[i]) / (hz_points[i+1] - hz_points[i])
        falling = (hz_points[i+2] - fft_freqs) / (hz_points[i+2] - hz_points[i+1])
        filterbank[i] = np.maximum(0, np.minimum(rising, falling))
        filterbank[i] *= 2.0 / (hz_points[i+2] - hz_points[i])
    return filterbank

def power_to_db(S, ref=1.0, amin=1e-10, top_db=80.0):
    log_spec = 10.0 * np.log10(np.maximum(amin, S))
    log_spec -= 10.0 * np.log10(np.maximum(amin, ref))
    if top_db is not None:
        log_spec = np.maximum(log_spec, log_spec.max() - top_db)
    return log_spec

# ============================================================
# Feature Extraction
# ============================================================

_mel_basis = None
def get_mel_basis():
    global _mel_basis
    if _mel_basis is None:
        _mel_basis = create_mel_filterbank(SAMPLE_RATE, N_FFT, N_MELS)
    return _mel_basis

def extract_features(file_path, signal_override=None, sr_override=None):
    """Extract Log-Mel Spectrogram matching training pipeline.

    If *signal_override* is provided, it is used instead of reading from
    *file_path* (allows reusing an already-denoised signal).
    """
    if signal_override is not None:
        signal = signal_override
        sr = sr_override if sr_override else SAMPLE_RATE
    else:
        signal, sr = read_wav_normalized(file_path)

    if sr != SAMPLE_RATE and sr > 0: signal = resample(signal, sr, SAMPLE_RATE)
    if len(signal) > SAMPLES: signal = signal[:SAMPLES]
    else: signal = np.pad(signal, (0, max(0, SAMPLES - len(signal))), "constant")
    
    # librosa.feature.melspectrogram(center=True)
    pad_len = N_FFT // 2
    padded_signal = np.pad(signal, pad_len, mode='reflect')
    
    n_frames = 1 + (len(padded_signal) - N_FFT) // HOP_LENGTH
    frames = np.zeros((n_frames, N_FFT))
    for i in range(n_frames):
        frames[i] = padded_signal[i*HOP_LENGTH : i*HOP_LENGTH+N_FFT]
    
    # Hann window
    window = 0.5 * (1 - np.cos(2 * np.pi * np.arange(N_FFT) / N_FFT))
    fft_mag = np.abs(np.fft.rfft(frames * window, n=N_FFT)) ** 2
    
    mel_basis = get_mel_basis()
    mel_spec = np.dot(fft_mag, mel_basis.T).T
    
    # Log-dB scale (ref=np.max)
    return power_to_db(mel_spec, ref=np.max(mel_spec))

# ============================================================
# TFLite Inference
# ============================================================

_interpreter = None
_input_details = None
_output_details = None

def _get_interpreter():
    global _interpreter, _input_details, _output_details
    if _interpreter is None:
        model_path = os.path.join(os.path.dirname(__file__), "tv_sound_model.tflite")
        _interpreter = tflite.Interpreter(model_path=model_path)
        _interpreter.allocate_tensors()
        _input_details = _interpreter.get_input_details()
        _output_details = _interpreter.get_output_details()
    return _interpreter, _input_details, _output_details

def predict_tflite(spec):
    # spec shape: (128, 130)
    # Add batch and channel dimensions: (1, 128, 130, 1)
    x = spec.reshape(1, 128, 130, 1).astype(np.float32)
    
    interp, i_det, o_det = _get_interpreter()
    interp.set_tensor(i_det[0]['index'], x)
    interp.invoke()
    return interp.get_tensor(o_det[0]['index'])

# ============================================================
# Entry Point & Stats
# ============================================================

def analyze_audio(wav_path):
    try:
        # 1. Read raw audio
        print("[DSP] Reading raw audio: %s" % wav_path)
        signal, sr = read_wav_normalized(wav_path)
        print("[DSP] Audio loaded: %d samples, sr=%d" % (len(signal), sr))

        # 2. Noise cancellation pipeline (Close-Range Optimized)
        print("[DSP] Running spectral-gate noise cancellation...")
        clean_signal = noise_cancel(signal, sr)
        
        print("[DSP] Applying band-pass filter (800Hz-14kHz)...")
        clean_signal = bandpass_filter(clean_signal, sr, lowcut=800, highcut=14000)
        
        print("[DSP] Applying proximity gate (5cm energy gate)...")
        clean_signal = proximity_gate(clean_signal, threshold_ratio=0.2, sr=sr)
        
        # Final Normalization to ensure model sees consistent volume
        max_peak = np.max(np.abs(clean_signal))
        if max_peak > 1e-6:
            clean_signal = clean_signal / max_peak
            
        print("[DSP] Noise cancellation complete!")

        # 3. Save denoised WAV
        denoised_path = save_denoised_wav(clean_signal, sr, wav_path)
        print("[DSP] Denoised WAV saved → %s" % denoised_path)

        # 4. Extract features from denoised signal (skip re-reading file)
        print("[DSP] Extracting Mel spectrogram from denoised signal...")
        spec = extract_features(wav_path, signal_override=clean_signal, sr_override=sr)
        pred = predict_tflite(spec)
        
        class_idx = int(np.argmax(pred))
        confidence = float(np.max(pred))
        
        f, p, s = _quick_stats(wav_path)
        
        print("[DSP] Result: %s (confidence=%.2f%%)" % (
            "NORMAL" if class_idx == 0 else "CRACK", confidence * 100))
        
        return {
            "frequency": f,
            "power": p,
            "surface_tension": s,
            "noise_status": "NORMAL" if class_idx == 0 else "CRACK",
            "confidence": confidence,
            "denoised_path": denoised_path
        }
    except Exception as e:
        return {
            "frequency": 0.0,
            "power": -100.0,
            "surface_tension": 0.0,
            "noise_status": "ERROR",
            "confidence": 0.0,
            "error": str(e)
        }

def _quick_stats(filepath):
    try:
        sig, sr = read_wav_normalized(filepath)
        if len(sig) == 0: return 0.0, -100.0, 0.0
        
        rms = np.sqrt(np.mean(sig**2))
        db = float(20 * np.log10(rms)) if rms > 1e-10 else -100.0
        
        n = len(sig)
        freq = 0.0
        if n > 256:
            fft_mag = np.abs(np.fft.rfft(sig * np.hanning(n)))
            freqs = np.fft.rfftfreq(n, d=1.0/sr)
            idx = np.argmax(fft_mag[max(1, int(20*n/sr)):]) + max(1, int(20*n/sr))
            freq = float(freqs[idx])
            
        st = 0.0
        if n > 256:
            fft = np.abs(np.fft.rfft(sig))
            freqs = np.fft.rfftfreq(n, d=1.0/sr)
            m_sum = np.sum(fft)
            if m_sum > 1e-10:
                st = min(100.0, np.sum(freqs * fft) / m_sum / 80.0)
                
        return freq, db, st
    except:
        return 0.0, -100.0, 0.0
