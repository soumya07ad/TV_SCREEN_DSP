package com.example.tvscreendsp.ui.measurement

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvscreendsp.audio.MicrophoneRecorder
import com.example.tvscreendsp.audio.RecordingState
import com.example.tvscreendsp.data.local.AppDatabase
import com.example.tvscreendsp.data.local.InputSource
import com.example.tvscreendsp.data.local.MeasurementEntity
import com.example.tvscreendsp.data.model.DspResult
import com.example.tvscreendsp.data.repository.MeasurementRepository
import com.example.tvscreendsp.dsp.DspException
import com.example.tvscreendsp.dsp.PythonDspBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Represents the current state of DSP analysis.
 */
sealed class AnalysisState {
    object Idle : AnalysisState()
    object Analyzing : AnalysisState()
    data class Completed(val result: DspResult) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
}

/**
 * ViewModel for the Measurement screen.
 * 
 * Coordinates:
 * 1. Audio recording via MicrophoneRecorder
 * 2. Database operations via MeasurementRepository
 * 3. DSP analysis via PythonDspBridge
 * 
 * ## Complete Workflow
 * 1. User starts recording
 * 2. MicrophoneRecorder captures 10 seconds of audio
 * 3. WAV file is saved to internal storage
 * 4. Measurement record is created in Room (DSP fields null)
 * 5. PythonDspBridge analyzes the WAV file
 * 6. Measurement record is updated with DSP results
 * 
 * ## Example Usage in Compose
 * ```kotlin
 * @Composable
 * fun MeasurementScreen(viewModel: MeasurementViewModel = viewModel()) {
 *     val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
 *     val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()
 *     val measurements by viewModel.allMeasurements.collectAsStateWithLifecycle()
 *     
 *     Column {
 *         // Recording UI
 *         when (val state = recordingState) {
 *             is RecordingState.Idle -> {
 *                 Button(onClick = { viewModel.startRecording() }) {
 *                     Text("Start Recording")
 *                 }
 *             }
 *             is RecordingState.Recording -> {
 *                 LinearProgressIndicator(progress = state.progressPercent / 100f)
 *                 Text("Recording: ${state.progressPercent}%")
 *             }
 *             is RecordingState.Completed -> {
 *                 // Show analysis state
 *                 when (val analysis = analysisState) {
 *                     is AnalysisState.Analyzing -> Text("Analyzing...")
 *                     is AnalysisState.Completed -> {
 *                         Text("Status: ${analysis.result.noiseStatus}")
 *                         Text("Confidence: ${analysis.result.confidencePercent()}")
 *                     }
 *                     is AnalysisState.Error -> Text("Error: ${analysis.message}")
 *                     else -> {}
 *                 }
 *             }
 *             is RecordingState.Error -> {
 *                 Text("Error: ${state.message}", color = Color.Red)
 *                 Button(onClick = { viewModel.resetState() }) {
 *                     Text("Retry")
 *                 }
 *             }
 *         }
 *         
 *         // Show measurement history
 *         LazyColumn {
 *             items(measurements) { measurement ->
 *                 MeasurementCard(measurement)
 *             }
 *         }
 *     }
 * }
 * ```
 */
class MeasurementViewModel(application: Application) : AndroidViewModel(application) {
    
    // Audio recording
    private val microphoneRecorder = MicrophoneRecorder(application.applicationContext)
    
    // Database
    private val database = AppDatabase.getInstance(application.applicationContext)
    private val repository = MeasurementRepository(database.measurementDao())
    
    // Recording state
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    // Analysis state
    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()
    
    // Current measurement ID (for DSP update)
    private var currentMeasurementId: Long? = null
    private var lastRecordedFile: File? = null
    
    // All measurements (reactive)
    val allMeasurements: StateFlow<List<MeasurementEntity>> = repository.getAllMeasurements()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    init {
        // Initialize Python bridge
        // Note: This should ideally be done in Application.onCreate()
        // but we do it here as a fallback
        if (!PythonDspBridge.isReady()) {
            PythonDspBridge.initialize(application.applicationContext)
        }
    }
    
    /**
     * Starts recording audio from the microphone.
     * Recording will automatically stop after 10 seconds.
     * 
     * After recording completes:
     * 1. WAV file is saved
     * 2. Measurement record is created in Room
     * 3. DSP analysis is triggered automatically
     */
    fun startRecording() {
        // Don't start if already recording or analyzing
        if (_recordingState.value is RecordingState.Recording) {
            return
        }
        if (_analysisState.value is AnalysisState.Analyzing) {
            return
        }
        
        // Reset states
        _analysisState.value = AnalysisState.Idle
        
        viewModelScope.launch {
            microphoneRecorder.startRecording().collect { state ->
                _recordingState.value = state
                
                // When recording completes, save to database and analyze
                if (state is RecordingState.Completed) {
                    lastRecordedFile = state.wavFile
                    
                    // Step 1: Save measurement to database
                    saveMeasurementToDatabase(state.wavFile)
                    
                    // Step 2: Run DSP analysis
                    runDspAnalysis(state.wavFile)
                }
            }
        }
    }
    
    /**
     * Creates a measurement record in Room after WAV file is saved.
     */
    private suspend fun saveMeasurementToDatabase(wavFile: File) {
        currentMeasurementId = repository.createMeasurement(
            wavFilePath = wavFile.absolutePath,
            inputSource = InputSource.MICROPHONE
        )
    }
    
    /**
     * Runs DSP analysis on the recorded WAV file.
     * Updates the database with results when complete.
     */
    private fun runDspAnalysis(wavFile: File) {
        _analysisState.value = AnalysisState.Analyzing
        
        viewModelScope.launch {
            try {
                // Call Python DSP bridge
                val dspResult = PythonDspBridge.analyzeAudio(wavFile.absolutePath)
                
                if (dspResult != null) {
                    // Update database with results
                    val measurementId = currentMeasurementId
                    if (measurementId != null) {
                        repository.updateWithDspResults(measurementId, dspResult)
                    }
                    
                    _analysisState.value = AnalysisState.Completed(dspResult)
                } else {
                    _analysisState.value = AnalysisState.Error("Analysis returned no result")
                }
                
            } catch (e: DspException) {
                _analysisState.value = AnalysisState.Error(e.message ?: "DSP analysis failed")
            } catch (e: Exception) {
                _analysisState.value = AnalysisState.Error("Unexpected error: ${e.message}")
            }
        }
    }
    
    /**
     * Manually trigger DSP analysis on an existing measurement.
     * Useful for re-analyzing previous recordings.
     */
    fun analyzeExistingMeasurement(measurement: MeasurementEntity) {
        if (_analysisState.value is AnalysisState.Analyzing) {
            return
        }
        
        currentMeasurementId = measurement.id
        lastRecordedFile = File(measurement.wavFilePath)
        
        runDspAnalysis(File(measurement.wavFilePath))
    }
    
    /**
     * Stops the current recording early.
     */
    fun stopRecording() {
        microphoneRecorder.stopRecording()
    }
    
    /**
     * Resets both recording and analysis states for a new session.
     */
    fun resetState() {
        _recordingState.value = RecordingState.Idle
        _analysisState.value = AnalysisState.Idle
        currentMeasurementId = null
    }
    
    /**
     * Returns the current measurement ID (for DSP processing).
     */
    fun getCurrentMeasurementId(): Long? = currentMeasurementId
    
    /**
     * Returns the last recorded WAV file.
     */
    fun getLastRecordedFile(): File? = lastRecordedFile
    
    /**
     * Returns true if currently recording.
     */
    fun isRecording(): Boolean = microphoneRecorder.isRecording()
    
    /**
     * Returns true if currently analyzing.
     */
    fun isAnalyzing(): Boolean = _analysisState.value is AnalysisState.Analyzing
}
