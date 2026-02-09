package com.example.tvscreendsp.audio

import java.io.File

/**
 * Sealed class representing the state of audio recording.
 */
sealed class RecordingState {
    /** Initial state before recording starts */
    data object Idle : RecordingState()
    
    /** Recording in progress with progress percentage (0-100) */
    data class Recording(val progressPercent: Int) : RecordingState()
    
    /** Recording completed successfully */
    data class Completed(val wavFile: File) : RecordingState()
    
    /** Recording failed with error message */
    data class Error(val message: String) : RecordingState()
}
