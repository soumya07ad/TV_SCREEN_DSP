package com.example.tvscreendsp.ui.history

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvscreendsp.data.local.AppDatabase
import com.example.tvscreendsp.data.local.MeasurementEntity
import com.example.tvscreendsp.data.repository.MeasurementRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for Audio History screen.
 * Manages measurement list and audio playback.
 */
class AudioHistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    // Database and repository
    private val database = AppDatabase.getInstance(application.applicationContext)
    private val repository = MeasurementRepository(database.measurementDao())
    
    // All measurements from database
    val measurements: StateFlow<List<MeasurementEntity>> =
        repository.getAllMeasurements()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    
    // Currently playing measurement ID (null if none playing)
    private val _playingId = MutableStateFlow<Long?>(null)
    val playingId: StateFlow<Long?> = _playingId.asStateFlow()
    
    // MediaPlayer instance (reused for all playback)
    private var mediaPlayer: MediaPlayer? = null
    
    /**
     * Play or stop audio for given measurement.
     */
    fun togglePlayback(measurement: MeasurementEntity) {
        val currentlyPlaying = _playingId.value
        
        if (currentlyPlaying == measurement.id) {
            // Stop current playback
            stopPlayback()
        } else {
            // Start new playback
            startPlayback(measurement)
        }
    }
    
    /**
     * Start playing audio file.
     */
    private fun startPlayback(measurement: MeasurementEntity) {
        // Stop any existing playback
        stopPlayback()
        
        viewModelScope.launch {
            try {
                val file = File(measurement.wavFilePath)
                
                // Check if file exists
                if (!file.exists()) {
                    return@launch
                }
                
                // Create and configure media player
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(measurement.wavFilePath)
                    prepare()
                    
                    // Handle completion
                    setOnCompletionListener {
                        _playingId.value = null
                        release()
                        mediaPlayer = null
                    }
                    
                    // Handle errors
                    setOnErrorListener { _, _, _ ->
                        _playingId.value = null
                        true
                    }
                    
                    start()
                }
                
                _playingId.value = measurement.id
                
            } catch (e: Exception) {
                stopPlayback()
            }
        }
    }
    
    /**
     * Stop current playback and release resources.
     */
    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        _playingId.value = null
    }
    
    // Dialog states
    private val _showRenameDialog = MutableStateFlow<Long?>(null)
    val showRenameDialog: StateFlow<Long?> = _showRenameDialog.asStateFlow()
    
    private val _showDeleteConfirm = MutableStateFlow<Long?>(null)
    val showDeleteConfirm: StateFlow<Long?> = _showDeleteConfirm.asStateFlow()
    
    /**
     * Show rename dialog for a measurement.
     */
    fun showRenameDialog(measurementId: Long) {
        _showRenameDialog.value = measurementId
    }
    
    /**
     * Hide rename dialog.
     */
    fun hideRenameDialog() {
        _showRenameDialog.value = null
    }
    
    /**
     * Rename a measurement.
     */
    fun renameMeasurement(measurementId: Long, newName: String) {
        viewModelScope.launch {
            repository.renameMeasurement(measurementId, newName)
            hideRenameDialog()
        }
    }
    
    /**
     * Show delete confirmation dialog.
     */
    fun showDeleteConfirm(measurementId: Long) {
        _showDeleteConfirm.value = measurementId
    }
    
    /**
     * Hide delete confirmation dialog.
     */
    fun hideDeleteConfirm() {
        _showDeleteConfirm.value = null
    }
    
    /**
     * Delete a measurement and its WAV file.
     */
    fun deleteMeasurement(measurement: MeasurementEntity) {
        viewModelScope.launch {
            // Stop playback if this measurement is playing
            if (_playingId.value == measurement.id) {
                stopPlayback()
            }
            
            repository.deleteMeasurement(measurement)
            hideDeleteConfirm()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
