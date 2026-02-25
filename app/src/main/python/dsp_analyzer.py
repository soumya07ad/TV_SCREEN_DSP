"""
<<<<<<< Updated upstream
Pure Python DSP Analyzer for TV Screen Crack Detection
No external dependencies - uses only Python standard library
"""

import wave
import struct
import math
from typing import Dict


def analyze_audio(wav_path: str) -> Dict:
    """
    Main entry point for audio analysis using pure Python.
    
    Returns dictionary with all 5 required keys:
    - frequency, power, surface_tension, noise_status, confidence
    """
    try:
        # Read WAV file
        samples, sample_rate = read_wav(wav_path)
        
        # Handle empty/invalid files
        if len(samples) == 0:
            return {
                "frequency": 0.0,
                "power": -100.0,
                "surface_tension": 0.0,
                "noise_status": "NOISE",
                "confidence": 0.0
            }
        
        # Calculate power
        power_db = calculate_power_db(samples)
        
        # Check for weak signal
        if power_db < -50:
            return {
                "frequency": 0.0,
                "power": float(power_db),
                "surface_tension": 0.0,
                "noise_status": "NOISE",
                "confidence": 0.5
            }
        
        # Autocorrelation-based frequency detection
        frequency = find_dominant_frequency(samples, sample_rate)
        
        # Calculate spectral flatness
        surface_tension = calculate_spectral_flatness(samples)
        
        # Classify
        noise_status, confidence = classify_noise(
            frequency, power_db, surface_tension
        )
        
        return {
            "frequency": float(frequency),
            "power": float(power_db),
            "surface_tension": float(surface_tension),
            "noise_status": noise_status,
            "confidence": float(confidence)
        }
        
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
        return {
            "frequency": 0.0,
            "power": -100.0,
            "surface_tension": 0.0,
<<<<<<< Updated upstream
            "noise_status": "NOISE",
            "confidence": 0.0
        }


def read_wav(filepath: str):
    """Read WAV file and return normalized samples + sample rate."""
    try:
        with wave.open(filepath, 'rb') as wav:
            n_channels = wav.getnchannels()
            sample_width = wav.getsampwidth()
            framerate = wav.getframerate()
            n_frames = wav.getnframes()
            
            # Read all frames
            raw_data = wav.readframes(n_frames)
            
            # Unpack based on sample width
            if sample_width == 2:  # 16-bit PCM
                fmt = f'{n_frames * n_channels}h'
                samples = struct.unpack(fmt, raw_data)
            else:
                return [], 0
            
            # Convert to mono if stereo
            if n_channels == 2:
                samples = [samples[i] for i in range(0, len(samples), 2)]
            
            # Normalize to -1.0 to 1.0
            max_val = 32768.0
            normalized = [s / max_val for s in samples]
            
            return normalized, framerate
            
    except Exception:
        return [], 0


def calculate_power_db(samples):
    """Calculate RMS power in dB."""
    if not samples:
        return -100.0
    
    # RMS
    sum_squares = sum(s * s for s in samples)
    rms = math.sqrt(sum_squares / len(samples))
    
    # Convert to dB (avoid log(0))
    if rms < 1e-10:
        return -100.0
    
    db = 20 * math.log10(rms)
    return db


def find_dominant_frequency(samples, sample_rate):
    """
    Find dominant frequency using autocorrelation.
    Pure Python implementation without FFT.
    """
    if len(samples) < 100:
        return 0.0
    
    # Use first 8192 samples for speed
    chunk_size = min(8192, len(samples))
    signal = samples[:chunk_size]
    
    # Autocorrelation for lag detection
    max_lag = min(2000, chunk_size // 2)
    max_corr = 0.0
    best_lag = 0
    
    for lag in range(20, max_lag):  # Skip very low frequencies
        correlation = sum(signal[i] * signal[i - lag] 
                         for i in range(lag, len(signal)))
        
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
