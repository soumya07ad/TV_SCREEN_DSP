package com.example.tvscreendsp.data.model

/**
 * Data class representing DSP analysis results.
 * 
 * Used to pass results from the Python DSP module back to Kotlin.
 * These values are then stored in MeasurementEntity.
 */
data class DspResult(
    /**
     * Dominant frequency in Hz.
     */
    val frequency: Double,
    
    /**
     * Signal power in dB (RMS-based).
     */
    val power: Double,
    
    /**
     * Surface tension metric (0-100 normalized).
     */
    val surfaceTension: Double,
    
    /**
     * Classification result.
     * Values: "CRACK", "NORMAL", "NOISE"
     */
    val noiseStatus: String,
    
    /**
     * Confidence score for the classification (0.0 - 1.0).
     */
    val confidence: Double
) {
    /**
     * Returns true if the status indicates a crack was detected.
     */
    fun isCrack(): Boolean = noiseStatus == "CRACK"
    
    /**
     * Returns the confidence as a percentage string.
     */
    fun confidencePercent(): String = "${(confidence * 100).toInt()}%"
}
