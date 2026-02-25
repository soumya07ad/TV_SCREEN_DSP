"""
<<<<<<< HEAD
ML-based DSP Analyzer for TV Screen Crack Detection
Uses pure NumPy for MFCC feature extraction and neural network inference.
MFCC implementation carefully matches librosa.feature.mfcc() defaults.
No external dependencies beyond NumPy (supported by Chaquopy).
=======
<<<<<<< Updated upstream
Pure Python DSP Analyzer for TV Screen Crack Detection
No external dependencies - uses only Python standard library
>>>>>>> dev
"""

import os
import json
import wave
import struct
import numpy as np

# Constants matching training parameters (from train_model.ipynb)
SAMPLE_RATE = 22050
DURATION = 3
SAMPLES = SAMPLE_RATE * DURATION
N_MFCC = 40
N_FFT = 2048
HOP_LENGTH = 512
N_MELS = 128


# ============================================================
# MFCC Feature Extraction (matches librosa defaults exactly)
# ============================================================

def hz_to_mel(hz):
    """Convert Hz to Mel scale (Slaney formula, same as librosa htk=False)."""
    f_min = 0.0
    f_sp = 200.0 / 3  # ~66.67 Hz
    min_log_hz = 1000.0
    min_log_mel = (min_log_hz - f_min) / f_sp  # 15.0
    logstep = np.log(6.4) / 27.0  # step size for log region

    if np.isscalar(hz):
        if hz >= min_log_hz:
            return min_log_mel + np.log(hz / min_log_hz) / logstep
        else:
            return (hz - f_min) / f_sp
    
    mels = np.where(
        hz >= min_log_hz,
        min_log_mel + np.log(hz / min_log_hz) / logstep,
        (hz - f_min) / f_sp
    )
    return mels

def mel_to_hz(mel):
    """Convert Mel to Hz (Slaney formula, same as librosa htk=False)."""
    f_min = 0.0
    f_sp = 200.0 / 3
    min_log_hz = 1000.0
    min_log_mel = (min_log_hz - f_min) / f_sp
    logstep = np.log(6.4) / 27.0

    if np.isscalar(mel):
        if mel >= min_log_mel:
            return min_log_hz * np.exp(logstep * (mel - min_log_mel))
        else:
            return f_min + f_sp * mel
    
    freqs = np.where(
        mel >= min_log_mel,
        min_log_hz * np.exp(logstep * (mel - min_log_mel)),
        f_min + f_sp * mel
    )
    return freqs

def create_mel_filterbank(sr, n_fft, n_mels):
    """
    Create Mel filterbank with Slaney normalization.
    Matches librosa.filters.mel(sr, n_fft, n_mels, norm='slaney', htk=False).
    """
    fmin = 0.0
    fmax = sr / 2.0
    
    # Mel points
    mel_min = hz_to_mel(fmin)
    mel_max = hz_to_mel(fmax)
    mel_points = np.linspace(mel_min, mel_max, n_mels + 2)
    hz_points = mel_to_hz(mel_points)
    
    # FFT frequencies
    fft_freqs = np.linspace(0, sr / 2.0, n_fft // 2 + 1)
    
    filterbank = np.zeros((n_mels, n_fft // 2 + 1))
    
    for i in range(n_mels):
        lower = hz_points[i]
        center = hz_points[i + 1]
        upper = hz_points[i + 2]
        
        # Rising slope
        if center > lower:
            rising = (fft_freqs - lower) / (center - lower)
        else:
            rising = np.zeros_like(fft_freqs)
        
        # Falling slope
        if upper > center:
            falling = (upper - fft_freqs) / (upper - center)
        else:
            falling = np.zeros_like(fft_freqs)
        
        filterbank[i] = np.maximum(0, np.minimum(rising, falling))
        
        # Slaney normalization: normalize by bandwidth (2 / (upper - lower))
        enorm = 2.0 / (hz_points[i + 2] - hz_points[i])
        filterbank[i] *= enorm
    
    return filterbank

def dct_matrix(n_mfcc, n_mels):
    """Create orthonormal DCT-II matrix (same as scipy.fftpack.dct type 2, norm='ortho')."""
    dct = np.zeros((n_mfcc, n_mels))
    for k in range(n_mfcc):
        if k == 0:
            scale = np.sqrt(1.0 / n_mels)
        else:
            scale = np.sqrt(2.0 / n_mels)
        for n in range(n_mels):
            dct[k, n] = scale * np.cos(np.pi * k * (2 * n + 1) / (2 * n_mels))
    return dct

def power_to_db(S, ref=1.0, amin=1e-10, top_db=80.0):
    """Convert power spectrogram to dB. Matches librosa.power_to_db()."""
    log_spec = 10.0 * np.log10(np.maximum(amin, S))
    log_spec -= 10.0 * np.log10(np.maximum(amin, ref))
    
    if top_db is not None:
        log_spec = np.maximum(log_spec, log_spec.max() - top_db)
    
    return log_spec

def extract_mfcc(signal, sr, n_mfcc=40, n_fft=2048, hop_length=512, n_mels=128):
    """
    Extract MFCC features from audio signal.
    Matches librosa.feature.mfcc(y=signal, sr=sr, n_mfcc=n_mfcc) defaults.
    """
    # Frame the signal
    n_frames = 1 + (len(signal) - n_fft) // hop_length
    if n_frames <= 0:
        n_frames = 1
    
    frames = np.zeros((n_frames, n_fft))
    for i in range(n_frames):
        start = i * hop_length
        end = start + n_fft
        if end <= len(signal):
            frames[i] = signal[start:end]
        else:
            frames[i, :len(signal) - start] = signal[start:]
    
    # Apply Hann window (librosa uses scipy.signal.get_window('hann', n_fft, fftbins=True))
    window = 0.5 * (1 - np.cos(2 * np.pi * np.arange(n_fft) / n_fft))
    frames = frames * window
    
    # FFT -> power spectrum (|FFT|^2 / n_fft for librosa's default power=2.0)
    fft_result = np.fft.rfft(frames, n=n_fft)
    power_spectrum = np.abs(fft_result) ** 2
    
    # Mel filterbank with Slaney normalization
    mel_basis = create_mel_filterbank(sr, n_fft, n_mels)
    mel_spec = np.dot(power_spectrum, mel_basis.T)
    
    # Convert to dB (librosa.power_to_db)
    log_mel_spec = power_to_db(mel_spec, ref=1.0)
    
    # DCT to get MFCCs (type-II, orthonormal)
    dct = dct_matrix(n_mfcc, n_mels)
    mfcc = np.dot(log_mel_spec, dct.T)
    
    return mfcc.T  # Shape: (n_mfcc, n_frames)


# ============================================================
# Neural Network Inference (pure NumPy)
# ============================================================

def relu(x):
    return np.maximum(0, x)

def softmax(x):
    e_x = np.exp(x - np.max(x))
    return e_x / e_x.sum(axis=1, keepdims=True)

class NeuralNet:
    def __init__(self, weights_path):
        with open(weights_path, 'r') as f:
            weights = json.load(f)
        
        # Dense(40 -> 256), Dense(256 -> 128), Dense(128 -> 2)
        # Layer names may vary between model versions, so load by order
        layer_names = list(weights.keys())
        self.w1 = np.array(weights[layer_names[0]][0])
        self.b1 = np.array(weights[layer_names[0]][1])
        self.w2 = np.array(weights[layer_names[1]][0])
        self.b2 = np.array(weights[layer_names[1]][1])
        self.w3 = np.array(weights[layer_names[2]][0])
        self.b3 = np.array(weights[layer_names[2]][1])

    def predict(self, x):
        z1 = np.dot(x, self.w1) + self.b1
        a1 = relu(z1)
        z2 = np.dot(a1, self.w2) + self.b2
        a2 = relu(z2)
        z3 = np.dot(a2, self.w3) + self.b3
        return softmax(z3)


# ============================================================
# WAV File Reader
# ============================================================

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
        
        # Convert to mono
        if n_channels == 2:
            samples = samples[::2]
        
        samples = samples / 32768.0
        return samples, framerate


def resample(signal, orig_sr, target_sr):
    """Resample signal using linear interpolation."""
    if orig_sr == target_sr:
        return signal
    ratio = target_sr / orig_sr
    new_length = int(len(signal) * ratio)
    indices = np.arange(new_length) / ratio
    left = np.floor(indices).astype(int)
    right = np.minimum(left + 1, len(signal) - 1)
    frac = indices - left
    return signal[left] * (1 - frac) + signal[right] * frac


# ============================================================
# Main Entry Point
# ============================================================

_model = None

def _get_model():
    global _model
    if _model is None:
        script_dir = os.path.dirname(__file__)
        weights_path = os.path.join(script_dir, "model_weights.json")
        _model = NeuralNet(weights_path)
    return _model


def extract_features(file_path):
    """
    Extract 40 MFCC features from a WAV file.
    Matches training pipeline: sr=22050, duration=3s, n_mfcc=40.
    """
    signal, sr = read_wav_normalized(file_path)
    
    if sr != SAMPLE_RATE and sr > 0:
        signal = resample(signal, sr, SAMPLE_RATE)
    
    if len(signal) > SAMPLES:
        signal = signal[:SAMPLES]
    else:
        signal = np.pad(signal, (0, max(0, SAMPLES - len(signal))), "constant")
    
    mfcc = extract_mfcc(signal, SAMPLE_RATE, n_mfcc=N_MFCC,
                        n_fft=N_FFT, hop_length=HOP_LENGTH, n_mels=N_MELS)
    
    mfcc_mean = np.mean(mfcc, axis=1)
    return mfcc_mean


def analyze_audio(wav_path):
    """
    Main entry point for audio analysis.
    Returns dictionary with all required keys for the Kotlin bridge.
    """
    try:
        features = extract_features(wav_path)
        features = features.reshape(1, -1)
        
        net = _get_model()
        prediction = net.predict(features)
        
        class_idx = int(np.argmax(prediction))
        confidence = float(np.max(prediction))
        
        frequency, power, surface_tension = _quick_stats(wav_path)
        status = "NORMAL" if class_idx == 0 else "CRACK"
        
        return {
            "frequency": float(frequency),
            "power": float(power),
            "surface_tension": float(surface_tension),
            "noise_status": status,
            "confidence": confidence
        }
        
<<<<<<< HEAD
    except Exception as e:
=======
    except Exception:
        # CRITICAL: Always return all required keys, never partial dict
=======
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

def extract_features(file_path):
    """Extract Log-Mel Spectrogram matching training pipeline."""
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
        spec = extract_features(wav_path)
        pred = predict_tflite(spec)
        
        class_idx = int(np.argmax(pred))
        confidence = float(np.max(pred))
        
        f, p, s = _quick_stats(wav_path)
        
        return {
            "frequency": f,
            "power": p,
            "surface_tension": s,
            "noise_status": "NORMAL" if class_idx == 0 else "CRACK",
            "confidence": confidence
        }
    except Exception as e:
>>>>>>> Stashed changes
>>>>>>> dev
        return {
            "frequency": 0.0,
            "power": -100.0,
            "surface_tension": 0.0,
<<<<<<< Updated upstream
            "noise_status": "NOISE",
            "confidence": 0.0,
            "error": str(e)
        }


def _quick_stats(filepath):
    """Calculate frequency, power, and surface tension for UI display."""
    try:
        signal, sr = read_wav_normalized(filepath)
        if len(signal) == 0:
            return 0.0, -100.0, 0.0
        
<<<<<<< HEAD
        # ── Power (RMS in dB) ──
        rms = np.sqrt(np.mean(signal ** 2))
        db = float(20 * np.log10(rms)) if rms > 1e-10 else -100.0
        
        # ── Dominant frequency via FFT ──
        freq = 0.0
        n = len(signal)
        if n > 256:
            # Use a window to reduce spectral leakage
            window = np.hanning(n)
            windowed = signal * window
            fft_mag = np.abs(np.fft.rfft(windowed))
            freqs = np.fft.rfftfreq(n, d=1.0 / sr)
            # Ignore DC and very low frequencies (< 20 Hz)
            min_idx = max(1, int(20 * n / sr))
            if min_idx < len(fft_mag):
                peak_idx = int(np.argmax(fft_mag[min_idx:])) + min_idx
                freq = float(freqs[peak_idx])
        
        # ── Surface tension (spectral centroid, normalized 0-100) ──
        # Higher centroid = more high-frequency energy = potential crack
        surface_tension = 0.0
        if n > 256:
            fft_mag = np.abs(np.fft.rfft(signal))
            freqs = np.fft.rfftfreq(n, d=1.0 / sr)
            mag_sum = np.sum(fft_mag)
            if mag_sum > 1e-10:
                centroid = np.sum(freqs * fft_mag) / mag_sum
                # Normalize: typical audio centroid range 0-8000 Hz -> 0-100
                surface_tension = min(100.0, max(0.0, centroid / 80.0))
        
        return freq, db, surface_tension
    except Exception:
        return 0.0, -100.0, 0.0
=======
        if correlation > max_corr:
            max_corr = correlation
            best_lag = lag
    
    if best_lag == 0:
        return 0.0
    
    frequency = sample_rate / best_lag
    return frequency


def calculate_spectral_flatness(samples):
    """
    Calculate spectral flatness (surface tension proxy).
    Pure Python approximation using variance.
    """
    if len(samples) < 100:
        return 0.0
    
    # Calculate variance as proxy for spectral spread
    mean = sum(samples) / len(samples)
    variance = sum((s - mean) ** 2 for s in samples) / len(samples)
    
    # Normalize to 0-1 range
    # Higher variance = more noise-like = higher flatness
    flatness = min(1.0, math.sqrt(variance) * 10)
    
    return flatness


def classify_noise(frequency, power_db, surface_tension):
    """
    Rule-based classification.
    
    Returns:
        (status, confidence)
    """
    
    # Weak signal check
    if power_db < -40:
        return "NOISE", 0.6
    
    # Crack detection heuristics
    crack_indicators = 0
    
    # High frequency content (cracks are sharp/transient)
    if frequency > 1500:
        crack_indicators += 1
    
    # High spectral flatness (noise-like)
    if surface_tension > 0.6:
        crack_indicators += 1
    
    # Strong signal
    if power_db > -20:
        crack_indicators += 1
    
    # Classification
    if crack_indicators >= 2:
        confidence = min(0.9, 0.5 + (crack_indicators * 0.2))
        return "CRACK", confidence
    elif crack_indicators == 1:
        return "NORMAL", 0.7
    else:
        return "NORMAL", 0.8
=======
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
>>>>>>> Stashed changes
>>>>>>> dev
