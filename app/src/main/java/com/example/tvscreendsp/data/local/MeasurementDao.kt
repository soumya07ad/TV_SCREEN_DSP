package com.example.tvscreendsp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for MeasurementEntity.
 * 
 * All methods are suspend functions or return Flow for automatic
 * background thread execution and reactive updates.
 */
@Dao
interface MeasurementDao {
    
    /**
     * Inserts a new measurement record.
     * 
     * Called immediately after WAV file is saved, before DSP analysis.
     * DSP result fields will be null at this point.
     * 
     * @param measurement The measurement entity to insert
     * @return The auto-generated ID of the inserted row
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: MeasurementEntity): Long
    
    /**
     * Updates an existing measurement record.
     * 
     * Called after DSP analysis completes to populate result fields.
     * 
     * @param measurement The measurement entity with updated fields
     */
    @Update
    suspend fun update(measurement: MeasurementEntity)
    
    /**
     * Retrieves a measurement by its ID.
     * 
     * @param id The measurement ID
     * @return The measurement entity, or null if not found
     */
    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun getMeasurementById(id: Long): MeasurementEntity?
    
    /**
     * Retrieves all measurements as a reactive Flow.
     * 
     * Ordered by recordedAt descending (newest first).
     * Flow automatically emits updates when data changes.
     * 
     * @return Flow of all measurements
     */
    @Query("SELECT * FROM measurements ORDER BY recordedAt DESC")
    fun getAllMeasurements(): Flow<List<MeasurementEntity>>
    
    /**
     * Retrieves only measurements that have been analyzed.
     * 
     * @return Flow of analyzed measurements
     */
    @Query("SELECT * FROM measurements WHERE analysisCompletedAt IS NOT NULL ORDER BY recordedAt DESC")
    fun getAnalyzedMeasurements(): Flow<List<MeasurementEntity>>
    
    /**
     * Retrieves only measurements pending analysis.
     * 
     * @return Flow of pending measurements
     */
    @Query("SELECT * FROM measurements WHERE analysisCompletedAt IS NULL ORDER BY recordedAt DESC")
    fun getPendingMeasurements(): Flow<List<MeasurementEntity>>
    
    /**
     * Deletes a measurement by its ID.
     * 
     * Note: This does NOT delete the WAV file from storage.
     * The caller should handle file deletion separately.
     * 
     * @param id The measurement ID to delete
     * @return Number of rows deleted (0 or 1)
     */
    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun deleteById(id: Long): Int
    
    /**
     * Deletes all measurements.
     * 
     * Use with caution - WAV files are NOT deleted.
     */
    @Query("DELETE FROM measurements")
    suspend fun deleteAll()
    
    /**
     * Returns the count of all measurements.
     */
    @Query("SELECT COUNT(*) FROM measurements")
    suspend fun getCount(): Int
}
