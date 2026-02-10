"""
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
        return {
            "frequency": 0.0,
            "power": -100.0,
            "surface_tension": 0.0,
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
