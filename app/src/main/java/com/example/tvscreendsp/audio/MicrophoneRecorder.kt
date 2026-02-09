package com.example.tvscreendsp.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * Microphone audio recorder using Android's AudioRecord API.
 * 
 * Records exactly 10 seconds of audio in PCM format and saves as WAV file.
 * All recording operations run on background threads via Kotlin Flow.
 * 
 * Usage:
 * ```kotlin
 * val recorder = MicrophoneRecorder(context)
 * recorder.startRecording().collect { state ->
 *     when (state) {
 *         is RecordingState.Recording -> updateProgress(state.progressPercent)
 *         is RecordingState.Completed -> handleWavFile(state.wavFile)
 *         is RecordingState.Error -> showError(state.message)
 *         RecordingState.Idle -> { }
 *     }
 * }
 * ```
 */
class MicrophoneRecorder(private val context: Context) {
    
    private var audioRecord: AudioRecord? = null
    
    @Volatile
    private var isRecording = false
    
    /**
     * Starts recording audio from the microphone.
     * 
     * Records for exactly 10 seconds, then automatically stops and saves the WAV file.
     * Emits RecordingState updates via Flow for progress tracking.
     * 
     * @return Flow of RecordingState representing recording progress and result
     */
    fun startRecording(): Flow<RecordingState> = flow {
        // Check permission first
        if (!hasRecordPermission()) {
            emit(RecordingState.Error("RECORD_AUDIO permission not granted"))
            return@flow
        }
        
        // Calculate buffer size (minimum required by AudioRecord)
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT
        )
        
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            emit(RecordingState.Error("Failed to get minimum buffer size"))
            return@flow
        }
        
        // Use larger buffer for smoother recording (at least 2x minimum)
        val bufferSize = maxOf(minBufferSize * 2, 4096)
        
        try {
            // Initialize AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_CONFIG,
                AudioConfig.AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                emit(RecordingState.Error("Failed to initialize AudioRecord"))
                releaseAudioRecord()
                return@flow
            }
            
            // Prepare output file
            val wavFile = createOutputFile()
            
            // Buffer to accumulate all PCM data for exactly 10 seconds
            val totalPcmBytes = AudioConfig.TOTAL_PCM_BYTES
            val pcmData = ByteArray(totalPcmBytes)
            var bytesRecorded = 0
            
            // Start recording
            isRecording = true
            audioRecord?.startRecording()
            
            emit(RecordingState.Recording(0))
            
            // Read buffer for incremental recording
            val readBuffer = ByteArray(bufferSize)
            
            // Record until we have exactly 10 seconds of audio
            while (isRecording && bytesRecorded < totalPcmBytes && coroutineContext.isActive) {
                val bytesToRead = min(bufferSize, totalPcmBytes - bytesRecorded)
                val bytesRead = audioRecord?.read(readBuffer, 0, bytesToRead) ?: -1
                
                when {
                    bytesRead > 0 -> {
                        // Copy read data to PCM buffer
                        System.arraycopy(readBuffer, 0, pcmData, bytesRecorded, bytesRead)
                        bytesRecorded += bytesRead
                        
                        // Emit progress update (every ~1% change to avoid flooding)
                        val progressPercent = (bytesRecorded * 100) / totalPcmBytes
                        emit(RecordingState.Recording(progressPercent))
                    }
                    bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                        emit(RecordingState.Error("AudioRecord not properly initialized"))
                        break
                    }
                    bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                        emit(RecordingState.Error("Invalid parameters for AudioRecord.read()"))
                        break
                    }
                    bytesRead == AudioRecord.ERROR_DEAD_OBJECT -> {
                        emit(RecordingState.Error("AudioRecord object is no longer valid"))
                        break
                    }
                    bytesRead == AudioRecord.ERROR -> {
                        emit(RecordingState.Error("Generic AudioRecord error"))
                        break
                    }
                }
            }
            
            // Stop recording
            stopRecordingInternal()
            
            // Verify we recorded the expected amount
            if (bytesRecorded < totalPcmBytes) {
                // Recording was interrupted - still save what we have
                val actualData = pcmData.copyOf(bytesRecorded)
                WavFileWriter.writePcmToWav(actualData, wavFile)
                emit(RecordingState.Completed(wavFile))
            } else {
                // Full 10 seconds recorded - write WAV file
                WavFileWriter.writePcmToWav(pcmData, wavFile)
                emit(RecordingState.Completed(wavFile))
            }
            
        } catch (e: SecurityException) {
            emit(RecordingState.Error("Permission denied: ${e.message}"))
        } catch (e: Exception) {
            emit(RecordingState.Error("Recording failed: ${e.message}"))
        } finally {
            releaseAudioRecord()
        }
    }.flowOn(Dispatchers.IO)  // Run on IO dispatcher for file/audio operations
    
    /**
     * Stops the current recording.
     * The Flow will complete with whatever audio was recorded so far.
     */
    fun stopRecording() {
        isRecording = false
    }
    
    /**
     * Returns true if currently recording.
     */
    fun isRecording(): Boolean = isRecording
    
    private fun stopRecordingInternal() {
        isRecording = false
        try {
            audioRecord?.stop()
        } catch (e: IllegalStateException) {
            // Already stopped, ignore
        }
    }
    
    private fun releaseAudioRecord() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore release errors
        }
        audioRecord = null
    }
    
    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Creates output WAV file in app's internal storage.
     * Path: filesDir/audio/measurement_YYYYMMDD_HHmmss.wav
     */
    private fun createOutputFile(): File {
        val audioDir = File(context.filesDir, "audio")
        audioDir.mkdirs()
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(audioDir, "measurement_$timestamp.wav")
    }
}
