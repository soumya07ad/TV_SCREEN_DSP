"""
DSP Analyzer for TV Screen Crack Detection
Uses numpy for accurate FFT-based frequency analysis
"""

import wave
import struct
import numpy as np
from typing import Dict


def analyze_audio(wav_path: str) -> Dict:
    """
    Main entry point for audio analysis using numpy FFT.
    
    Args:
        wav_path: Absolute path to WAV file
        
    Returns:
        Dictionary with:
        - frequency: Dominant frequency in Hz
        - power: Signal power in dB
        - surface_tension: Spectral flatness metric
        - noise_status: "CRACK" | "NORMAL" | "NOISE"
        - confidence: 0.0 to 1.0
        - error: Error message if failed
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
        
        # FFT-based frequency detection
        frequency = find_dominant_frequency_fft(samples, sample_rate)
        
        # Calculate spectral flatness
        surface_tension = calculate_spectral_flatness_fft(samples)
        
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



def read_wav(filepath: str) -> tuple:
    """Read WAV file and return numpy array of samples + sample rate."""
    try:
        with wave.open(filepath, 'rb') as wav:
            n_channels = wav.getnchannels()
            sample_width = wav.getsampwidth()
            framerate = wav.getframerate()
            n_frames = wav.getnframes()
            
            # Read all frames
            raw_data = wav.readframes(n_frames)
            
            # Unpack based on sample width (16-bit PCM)
            if sample_width == 2:
                samples = np.frombuffer(raw_data, dtype=np.int16)
            else:
                return np.array([]), 0
            
            # Convert to mono if stereo
            if n_channels == 2:
                samples = samples[::2]
            
            # Normalize to -1.0 to 1.0
            samples = samples.astype(np.float32) / 32768.0
            
            return samples, framerate
            
    except Exception:
        return np.array([]), 0


def calculate_power_db(samples: np.ndarray) -> float:
    """Calculate RMS power in dB."""
    if len(samples) == 0:
        return -100.0
    
    # RMS
    rms = np.sqrt(np.mean(samples ** 2))
    
    # Convert to dB (avoid log(0))
    if rms < 1e-10:
        return -100.0
    
    db = 20 * np.log10(rms)
    return float(db)


def find_dominant_frequency_fft(samples: np.ndarray, sample_rate: int) -> float:
    """
    Find dominant frequency using FFT.
    More accurate than autocorrelation method.
    """
    if len(samples) < 100:
        return 0.0
    
    # Apply Hanning window to reduce spectral leakage
    window = np.hanning(len(samples))
    windowed = samples * window
    
    # Compute FFT (only positive frequencies)
    fft = np.fft.rfft(windowed)
    magnitude = np.abs(fft)
    
    # Find peak frequency
    freqs = np.fft.rfftfreq(len(samples), 1.0 / sample_rate)
    
    # Ignore DC component and very low frequencies
    valid_idx = freqs > 20
    if not np.any(valid_idx):
        return 0.0
    
    magnitude_valid = magnitude[valid_idx]
    freqs_valid = freqs[valid_idx]
    
    # Find peak
    peak_idx = np.argmax(magnitude_valid)
    dominant_freq = freqs_valid[peak_idx]
    
    return float(dominant_freq)


def calculate_spectral_flatness_fft(samples: np.ndarray) -> float:
    """
    Calculate spectral flatness using FFT.
    Flatness = geometric_mean / arithmetic_mean
    Higher values indicate more noise-like signals (cracks)
    """
    if len(samples) < 100:
        return 0.0
    
    # Apply window
    window = np.hanning(len(samples))
    windowed = samples * window
    
    # Compute FFT magnitude
    fft = np.fft.rfft(windowed)
    magnitude = np.abs(fft)
    
    # Skip DC component
    magnitude = magnitude[1:]
    
    # Add small epsilon to avoid log(0)
    epsilon = 1e-10
    magnitude = magnitude + epsilon
    
    # Spectral flatness = exp(mean(log(power))) / mean(power)
    power = magnitude ** 2
    
    geometric_mean = np.exp(np.mean(np.log(power)))
    arithmetic_mean = np.mean(power)
    
    if arithmetic_mean < epsilon:
        return 0.0
    
    flatness = geometric_mean / arithmetic_mean
    
    return float(flatness)


def classify_noise(frequency: float, power_db: float, surface_tension: float) -> tuple:
    """
    Rule-based classification for crack/noise detection.
    
    Returns:
        (status, confidence)
    """
    
    # Weak signal check
    if power_db < -40:
        return "NOISE", 0.6
    
    # Crack detection heuristics
    crack_score = 0.0
    
    # High frequency content (cracks are sharp/transient)
    if frequency > 2000:
        crack_score += 0.3
    elif frequency > 1000:
        crack_score += 0.15
    
    # High spectral flatness (noise-like, characteristic of cracks)
    if surface_tension > 0.7:
        crack_score += 0.4
    elif surface_tension > 0.5:
        crack_score += 0.2
    
    # Strong signal (cracks often have high amplitude)
    if power_db > -15:
        crack_score += 0.3
    elif power_db > -25:
        crack_score += 0.15
    
    # Classification
    if crack_score >= 0.6:
        confidence = min(0.95, crack_score)
        return "CRACK", confidence
    elif crack_score >= 0.3:
        return "NORMAL", 0.65
    else:
        return "NORMAL", 0.8
