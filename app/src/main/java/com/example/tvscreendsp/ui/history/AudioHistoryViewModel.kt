package com.example.tvscreendsp.ui.history

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvscreendsp.data.local.MeasurementEntity
import com.example.tvscreendsp.data.repository.MeasurementRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for Audio History screen.
 * Manages measurement list and audio playback.
 */
class AudioHistoryViewModel(
    private val repository: MeasurementRepository
) : ViewModel() {
    
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
    
    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
