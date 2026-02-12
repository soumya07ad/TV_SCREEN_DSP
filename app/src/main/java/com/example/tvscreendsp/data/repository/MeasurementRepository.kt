package com.example.tvscreendsp.data.repository

import com.example.tvscreendsp.data.local.MeasurementDao
import com.example.tvscreendsp.data.local.MeasurementEntity
import com.example.tvscreendsp.data.model.DspResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing measurement data.
 * 
 * Provides a clean API for the ViewModel to interact with the database
 * without knowing about Room implementation details.
 * 
 * ## Usage Pattern
 * 1. Call [createMeasurement] immediately after WAV file is saved
 * 2. Call [updateWithDspResults] after Python DSP analysis completes
 * 3. Observe [getAllMeasurements] for reactive UI updates
 */
class MeasurementRepository(private val measurementDao: MeasurementDao) {
    
    /**
     * Creates a new measurement record after WAV file is saved.
     * 
     * DSP result fields are null at this point - they will be
     * populated later via [updateWithDspResults].
     * 
     * @param wavFilePath Absolute path to the saved WAV file
     * @param inputSource Input source used (MICROPHONE, USB, BLE)
     * @param triggerCompleted True if hardware handshake completed successfully
     * @param triggerLatencyMs Latency in ms between START and DONE (null if failed/manual)
     * @return The auto-generated ID of the new measurement
     */
    suspend fun createMeasurement(
        wavFilePath: String,
        inputSource: String,
        triggerCompleted: Boolean = false,
        triggerLatencyMs: Long? = null
    ): Long {
        val entity = MeasurementEntity(
            wavFilePath = wavFilePath,
            recordedAt = System.currentTimeMillis(),
            inputSource = inputSource,
            triggerCompleted = triggerCompleted,
            triggerLatencyMs = triggerLatencyMs
        )
        return measurementDao.insert(entity)
    }
    
    /**
     * Updates a measurement with DSP analysis results.
     * 
     * Called after Python DSP module has processed the WAV file.
     * 
     * @param measurementId ID of the measurement to update
     * @param dspResult Results from DSP analysis
     * @return true if update was successful, false if measurement not found
     */
    suspend fun updateWithDspResults(
        measurementId: Long,
        dspResult: DspResult
    ): Boolean {
        val existing = measurementDao.getMeasurementById(measurementId)
            ?: return false
        
        val updated = existing.copy(
            frequency = dspResult.frequency,
            power = dspResult.power,
            surfaceTension = dspResult.surfaceTension,
            noiseStatus = dspResult.noiseStatus,
            confidence = dspResult.confidence,
            analysisCompletedAt = System.currentTimeMillis()
        )
        measurementDao.update(updated)
        return true
    }
    
    /**
     * Retrieves a measurement by ID.
     */
    suspend fun getMeasurementById(id: Long): MeasurementEntity? {
        return measurementDao.getMeasurementById(id)
    }
    
    /**
     * Returns all measurements as a reactive Flow.
     * 
     * UI will automatically update when measurements are added/updated/deleted.
     * Sorted by recordedAt timestamp (newest first).
     */
    fun getAllMeasurements(): Flow<List<MeasurementEntity>> {
        return measurementDao.getAllMeasurements()
    }
    
    /**
     * Retrieves only measurements that have been analyzed.
     */
    fun getAnalyzedMeasurements(): Flow<List<MeasurementEntity>> {
        return measurementDao.getAnalyzedMeasurements()
    }
    
    /**
     * Retrieves measurements pending analysis.
     */
    fun getPendingMeasurements(): Flow<List<MeasurementEntity>> {
        return measurementDao.getPendingMeasurements()
    }
    
    /**
     * Deletes a measurement and its associated WAV file.
     * 
     * @param measurement The measurement to delete
     * @return true if deletion was successful
     */
    suspend fun deleteMeasurement(measurement: MeasurementEntity): Boolean {
        return try {
            // Delete WAV file if it exists
            val wavFile = java.io.File(measurement.wavFilePath)
            if (wavFile.exists()) {
                wavFile.delete()
            }
            
            // Delete database record
            measurementDao.delete(measurement)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Updates the custom name of a measurement.
     * 
     * @param id The measurement ID
     * @param newName The new custom name (empty string to clear)
     */
    suspend fun renameMeasurement(id: Long, newName: String) {
        val finalName = if (newName.isBlank()) null else newName.trim()
        measurementDao.updateCustomName(id, finalName)
    }
    
    /**
     * Returns the total count of measurements.
     */
    suspend fun getMeasurementCount(): Int {
        return measurementDao.getCount()
    }
}
