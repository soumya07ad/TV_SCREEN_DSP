package com.example.tvscreendsp.ui.measurement

import android.app.Application
import android.hardware.usb.UsbManager
import android.util.Log
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
import com.example.tvscreendsp.usb.UsbConnectionState
import com.example.tvscreendsp.usb.UsbPermissionHandler
import com.example.tvscreendsp.usb.UsbUartManager
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.delay
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

// ──────────────────────────────────────────────────────────────────
// USB TRIGGER MODE
// ──────────────────────────────────────────────────────────────────

/**
 * Indicates how the physical tap was triggered during a measurement.
 *
 * - [MANUAL]: No USB device connected; user physically tapped the screen
 * - [USB_UART]: External device triggered via USB-UART serial command
 */
enum class TriggerMode {
    MANUAL,
    USB_UART
}

/**
 * ViewModel for the Measurement screen.
 * 
 * Coordinates:
 * 1. Audio recording via MicrophoneRecorder
 * 2. USB-UART trigger via UsbUartManager (NEW)
 * 3. Database operations via MeasurementRepository
 * 4. DSP analysis via PythonDspBridge
 * 
 * ## Complete Workflow (Updated with USB Trigger)
 * 1. User taps "Measure Noise"
 * 2. MicrophoneRecorder starts immediately (t=0ms)
 * 3. After 300ms delay, USB trigger "TAP\n" is sent (t=300ms)
 * 4. External device physically taps the TV screen
 * 5. MicrophoneRecorder captures the tap sound
 * 6. After 10 seconds, WAV file is saved
 * 7. Measurement record is created in Room (DSP fields null)
 * 8. PythonDspBridge analyzes the WAV file
 * 9. Measurement record is updated with DSP results
 *
 * ## Graceful Degradation
 * If USB is not connected or fails at any point, the app
 * silently falls back to MANUAL mode. The recording and DSP
 * pipeline are NEVER interrupted by USB failures.
 * 
 * ## Example Usage in Compose
 * ```kotlin
 * @Composable
 * fun MeasurementScreen(viewModel: MeasurementViewModel = viewModel()) {
 *     val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
 *     val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()
 *     val usbState by viewModel.usbConnectionState.collectAsStateWithLifecycle()
 *     
 *     // Show USB status indicator
 *     UsbStatusChip(usbState)
 *     
 *     // Recording + analysis UI (existing)
 *     MeasurementContent(recordingState, analysisState)
 * }
 * ```
 *
 * @param application Application context
 * @param usbUartManager Serial I/O manager for USB-UART trigger
 * @param usbPermissionHandler Handles USB permission dialog
 */
class MeasurementViewModel(
    application: Application,
    private val usbUartManager: UsbUartManager,
    private val usbPermissionHandler: UsbPermissionHandler,
    private val repository: MeasurementRepository,
    private val pythonDspBridge: PythonDspBridge,
    private val microphoneRecorder: MicrophoneRecorder
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MeasurementViewModel"

        /**
         * Delay before sending START command to ESP32.
         * Allows ESP32 to complete its initialization and stabilize
         * after USB connection before receiving commands.
         */
        private const val HANDSHAKE_STABILIZATION_DELAY_MS = 2000L

        /**
         * Maximum time to wait for ESP32 to respond with "DONE"
         * after sending "START\n".
         */
        private const val HANDSHAKE_TIMEOUT_MS = 5000L
    }
    
    // ──────────────────────────────────────────────────────────────
    // EXISTING: Audio recording
    // ──────────────────────────────────────────────────────────────

    // Recorder injected via constructor
    
    // Database and Repository injected via constructor
    
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

    // ──────────────────────────────────────────────────────────────
    // NEW: USB trigger state
    // ──────────────────────────────────────────────────────────────

    /** UsbManager for probing available drivers. */
    private val usbManager: UsbManager =
        application.getSystemService(UsbManager::class.java)

    /**
     * Current USB connection state, forwarded from [UsbUartManager].
     * Compose UI can observe this to show a USB status indicator.
     */
    val usbConnectionState: StateFlow<UsbConnectionState> =
        usbUartManager.connectionState

    /**
     * Trigger mode for the current/last measurement.
     * USB_UART if trigger was sent via serial, MANUAL otherwise.
     */
    private val _triggerMode = MutableStateFlow(TriggerMode.MANUAL)
    val triggerMode: StateFlow<TriggerMode> = _triggerMode.asStateFlow()

    /**
     * Result of the last hardware handshake attempt.
     * Observed by [MeasurementScreen] to display trigger status.
     */
    private val _lastHandshakeResult = MutableStateFlow(HandshakeResult())
    val lastHandshakeResult: StateFlow<HandshakeResult> = _lastHandshakeResult.asStateFlow()

    /**
     * Input source of the last measurement (for UI display).
     */
    private val _lastInputSource = MutableStateFlow(InputSource.MICROPHONE)
    val lastInputSource: StateFlow<String> = _lastInputSource.asStateFlow()

    // ──────────────────────────────────────────────────────────────
    // EXISTING: Init
    // ──────────────────────────────────────────────────────────────

    init {
        // Initialize Python bridge
        // Note: This should ideally be done in Application.onCreate()
        // but we do it here as a fallback
        if (!PythonDspBridge.isReady()) {
            PythonDspBridge.initialize(application.applicationContext)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // MODIFIED: startRecording() — hardware handshake protocol
    // ──────────────────────────────────────────────────────────────

    /**
     * Starts recording audio from the microphone and optionally
     * executes the full START/DONE handshake with the ESP32.
     *
     * ## Flow:
     * 1. Determine trigger mode (check USB availability + permission)
     * 2. Launch recording immediately
     * 3. If USB connected:
     *    a. Delay 2000ms (ESP32 stabilization)
     *    b. Send "START\n"
     *    c. Wait for "DONE" (5s timeout)
     *    d. Log latency result
     * 4. Wait for recording to complete (full 10s regardless)
     * 5. Save to database with correct input source
     * 6. Run DSP analysis
     *
     * ## Graceful Degradation:
     * If USB is not available, permission is denied, connection
     * fails, or the handshake times out, the mode silently falls
     * back to MANUAL. Recording and DSP analysis are NEVER
     * interrupted by USB issues.
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
        _triggerMode.value = TriggerMode.MANUAL
        _lastHandshakeResult.value = HandshakeResult()
        _lastInputSource.value = InputSource.MICROPHONE
        
        viewModelScope.launch {
            // ── Step 0: Resolve trigger mode before recording ──
            val resolvedTriggerMode = resolveUsbTriggerMode()
            _triggerMode.value = resolvedTriggerMode
            Log.d(TAG, "Trigger mode: $resolvedTriggerMode")

            // ── Step 1: Start recording immediately (parallel) ──
            var completedWavFile: File? = null

            val recordingJob = launch {
                microphoneRecorder.startRecording().collect { state ->
                    _recordingState.value = state
                    if (state is RecordingState.Completed) {
                        completedWavFile = state.wavFile
                    }
                }
            }

            // ── Step 2: Execute hardware handshake (non-blocking) ──
            var handshakeResult = HandshakeResult()
            if (resolvedTriggerMode == TriggerMode.USB_UART) {
                handshakeResult = executeHandshakeSafely()
            }
            _lastHandshakeResult.value = handshakeResult

            // ── Step 3: Wait for recording to complete ──
            recordingJob.join()

            // ── Step 4: Process completed recording ──
            val wavFile = completedWavFile
            if (wavFile != null) {
                lastRecordedFile = wavFile

                // Save to DB with correct input source
                val inputSource = when (resolvedTriggerMode) {
                    TriggerMode.USB_UART -> InputSource.USB
                    TriggerMode.MANUAL -> InputSource.MICROPHONE
                }
                _lastInputSource.value = inputSource
                saveMeasurementToDatabase(
                    wavFile = wavFile,
                    inputSource = inputSource,
                    triggerCompleted = handshakeResult.completed,
                    triggerLatencyMs = handshakeResult.latencyMs
                )

                // Run DSP analysis
                runDspAnalysis(wavFile)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // NEW: USB trigger helpers
    // ──────────────────────────────────────────────────────────────

    /**
     * Determines whether USB-UART trigger is available.
     *
     * Checks (in order):
     * 1. Is a USB-serial device physically connected?
     * 2. Does the app have USB permission?
     * 3. Can the serial port be opened?
     *
     * If any step fails, returns [TriggerMode.MANUAL] silently.
     * This runs BEFORE recording starts to avoid delaying audio capture.
     *
     * @return [TriggerMode.USB_UART] if ready, [TriggerMode.MANUAL] otherwise
     */
    private suspend fun resolveUsbTriggerMode(): TriggerMode {
        try {
            // Already connected from a previous session?
            if (usbUartManager.connectionState.value.isConnected) {
                Log.d(TAG, "USB already connected — using USB_UART mode")
                return TriggerMode.USB_UART
            }

            // Check for available USB-serial drivers
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (drivers.isEmpty()) {
                Log.d(TAG, "No USB-serial devices found — falling back to MANUAL")
                return TriggerMode.MANUAL
            }

            val device = drivers.first().device

            // Request permission if not already granted
            if (!usbManager.hasPermission(device)) {
                Log.d(TAG, "Requesting USB permission for ${device.deviceName}")
                val granted = usbPermissionHandler.requestPermission(device)
                if (!granted) {
                    Log.d(TAG, "USB permission denied — falling back to MANUAL")
                    return TriggerMode.MANUAL
                }
            }

            // Try to connect
            val result = usbUartManager.connect()
            if (result.isFailure) {
                Log.e(TAG, "USB connection failed: ${result.exceptionOrNull()?.message}")
                return TriggerMode.MANUAL
            }

            Log.d(TAG, "USB connected — using USB_UART mode")
            return TriggerMode.USB_UART

        } catch (e: Exception) {
            Log.e(TAG, "USB setup failed — falling back to MANUAL", e)
            return TriggerMode.MANUAL
        }
    }

    /**
     * Executes the full START/DONE handshake with the ESP32.
     *
     * Sequence:
     * 1. Wait [HANDSHAKE_STABILIZATION_DELAY_MS] for ESP32 to stabilize
     * 2. Send "START\n" command
     * 3. Wait for "DONE" response (up to [HANDSHAKE_TIMEOUT_MS])
     * 4. Log latency on success
     *
     * If any step fails, the trigger mode is downgraded to MANUAL.
     * Recording is NEVER interrupted by handshake failures.
     *
     * @return [HandshakeResult] with success status and latency
     */
    private suspend fun executeHandshakeSafely(): HandshakeResult {
        try {
            // Step 1: Stabilization delay for ESP32
            Log.d(TAG, "Waiting ${HANDSHAKE_STABILIZATION_DELAY_MS}ms for ESP32 stabilization...")
            delay(HANDSHAKE_STABILIZATION_DELAY_MS)

            // Step 2: Send START command
            val startResult = usbUartManager.sendStartCommand()
            if (startResult.isFailure) {
                Log.e(TAG, "Failed to send START: ${startResult.exceptionOrNull()?.message}")
                _triggerMode.value = TriggerMode.MANUAL
                return HandshakeResult()
            }
            Log.d(TAG, "START command sent — waiting for DONE...")

            // Step 3: Wait for DONE response
            val doneResult = usbUartManager.waitForDone(HANDSHAKE_TIMEOUT_MS)
            if (doneResult.isSuccess) {
                val latencyMs = doneResult.getOrNull()
                Log.d(TAG, "Handshake complete — trigger latency: ${latencyMs}ms")
                return HandshakeResult(completed = true, latencyMs = latencyMs)
            } else {
                Log.e(
                    TAG,
                    "Handshake failed: ${doneResult.exceptionOrNull()?.message}"
                )
                _triggerMode.value = TriggerMode.MANUAL
                return HandshakeResult()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Handshake error — falling back to MANUAL", e)
            _triggerMode.value = TriggerMode.MANUAL
            return HandshakeResult()
        }
    }

    /**
     * Result of the hardware handshake protocol.
     *
     * @property completed True if ESP32 responded with "DONE"
     * @property latencyMs Time in ms between START and DONE (null if failed)
     */
    data class HandshakeResult(
        val completed: Boolean = false,
        val latencyMs: Long? = null
    )

    // ──────────────────────────────────────────────────────────────
    // NEW: USB connection management
    // ──────────────────────────────────────────────────────────────

    /**
     * Manually connects to USB-serial device.
     *
     * Handles permission request + serial port opening.
     * Call this from the UI when user taps a "Connect USB" button.
     */
    fun connectUsb() {
        viewModelScope.launch {
            resolveUsbTriggerMode()
        }
    }

    /**
     * Manually disconnects from USB-serial device.
     */
    fun disconnectUsb() {
        viewModelScope.launch {
            usbUartManager.disconnect()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // EXISTING: Database & DSP (unchanged except inputSource param)
    // ──────────────────────────────────────────────────────────────

    /**
     * Creates a measurement record in Room after WAV file is saved.
     *
     * @param wavFile The saved WAV file
     * @param inputSource Input source: MICROPHONE (manual) or USB (triggered)
     * @param triggerCompleted True if hardware handshake completed successfully
     * @param triggerLatencyMs Latency in ms between START and DONE
     */
    private suspend fun saveMeasurementToDatabase(
        wavFile: File,
        inputSource: String,
        triggerCompleted: Boolean = false,
        triggerLatencyMs: Long? = null
    ) {
        currentMeasurementId = repository.createMeasurement(
            wavFilePath = wavFile.absolutePath,
            inputSource = inputSource,
            triggerCompleted = triggerCompleted,
            triggerLatencyMs = triggerLatencyMs
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

    // ──────────────────────────────────────────────────────────────
    // EXISTING: Public helpers (unchanged)
    // ──────────────────────────────────────────────────────────────

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
        _triggerMode.value = TriggerMode.MANUAL
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
