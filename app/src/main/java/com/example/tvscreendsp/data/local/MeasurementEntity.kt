package com.example.tvscreendsp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a measurement record.
 * 
 * Stores WAV file metadata and DSP analysis results.
 * NOTE: Raw audio data is NOT stored in the database - only the file path.
 * 
 * DSP result fields are nullable because they are populated AFTER
 * the initial record is created (post-analysis update pattern).
 */
@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Path to the WAV file in internal storage.
     * Example: "/data/data/com.example.tvscreendsp/files/audio/measurement_20260209_160001.wav"
     */
    val wavFilePath: String,
    
    /**
     * Unix timestamp (milliseconds) when recording was created.
     */
    val recordedAt: Long,
    
    /**
     * Input source used for recording.
     * Values: "MICROPHONE", "USB", "BLE"
     */
    val inputSource: String,
    
    // ========== DSP RESULTS (nullable until analysis completes) ==========
    
    /**
     * Dominant frequency detected in Hz.
     * Populated after DSP analysis completes.
     */
    val frequency: Double? = null,
    
    /**
     * Signal power in dB (RMS-based).
     * Populated after DSP analysis completes.
     */
    val power: Double? = null,
    
    /**
     * Surface tension metric (derived from spectral flatness).
     * Populated after DSP analysis completes.
     */
    val surfaceTension: Double? = null,
    
    /**
     * Classification result from DSP analysis.
     * Values: "CRACK", "NORMAL", "NOISE"
     * Populated after DSP analysis completes.
     */
    val noiseStatus: String? = null,
    
    /**
     * Confidence score for the classification (0.0 - 1.0).
     * Populated after DSP analysis completes.
     */
    val confidence: Double? = null,
    
    /**
     * Unix timestamp (milliseconds) when analysis completed.
     * Null if analysis has not been performed yet.
     */
    val analysisCompletedAt: Long? = null
) {
    /**
     * Returns true if DSP analysis has been completed for this measurement.
     */
    fun isAnalyzed(): Boolean = analysisCompletedAt != null
}

/**
 * Input source types for measurement recording.
 */
object InputSource {
    const val MICROPHONE = "MICROPHONE"
    const val USB = "USB"
    const val BLE = "BLE"
}

/**
 * Noise classification status values from DSP analysis.
 */
object NoiseStatus {
    const val CRACK = "CRACK"
    const val NORMAL = "NORMAL"
    const val NOISE = "NOISE"
}
