package com.example.tvscreendsp.audio

import android.media.AudioFormat

/**
 * Audio recording configuration constants.
 * Standard settings for DSP analysis.
 */
object AudioConfig {
    /** Sample rate in Hz - standard for audio analysis */
    const val SAMPLE_RATE = 44100
    
    /** Mono channel for single-source recording */
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    
    /** 16-bit PCM for adequate dynamic range */
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    /** Recording duration in milliseconds */
    const val RECORD_DURATION_MS = 10_000L
    
    /** Bytes per sample (16-bit = 2 bytes) */
    const val BYTES_PER_SAMPLE = 2
    
    /** Number of channels (mono = 1) */
    const val NUM_CHANNELS = 1
    
    /**
     * Total bytes for 10 seconds of audio.
     * Formula: sampleRate * durationSeconds * bytesPerSample * numChannels
     * = 44100 * 10 * 2 * 1 = 882,000 bytes
     */
    val TOTAL_PCM_BYTES: Int
        get() = (SAMPLE_RATE * (RECORD_DURATION_MS / 1000) * BYTES_PER_SAMPLE * NUM_CHANNELS).toInt()
}
