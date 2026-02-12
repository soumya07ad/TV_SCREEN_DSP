package com.example.tvscreendsp.dsp

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.tvscreendsp.data.model.DspResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bridge between Kotlin and Python DSP module via Chaquopy.
 * 
 * ## Thread Safety
 * - Python initialization MUST happen on the main thread (Android requirement)
 * - DSP analysis runs on Dispatchers.Default (CPU-intensive work)
 * - Mutex ensures only one analysis runs at a time
 * 
 * ## Usage
 * ```kotlin
 * // In Application.onCreate() or Activity.onCreate()
 * PythonDspBridge.initialize(context)
 * 
 * // In ViewModel (suspend function)
 * val result = PythonDspBridge.analyzeAudio(wavFilePath)
 * ```
 */
object PythonDspBridge {
    
    private var isInitialized = false
    private var dspModule: PyObject? = null
    
    // Mutex to prevent concurrent Python calls (Python GIL + safety)
    private val analysisMutex = Mutex()
    
    /**
     * Initialize Python runtime. MUST be called on main thread before any analysis.
     * 
     * Typically called in:
     * - Application.onCreate()
     * - MainActivity.onCreate()
     * 
     * @param context Android context for Chaquopy initialization
     */
    fun initialize(context: android.content.Context) {
        if (isInitialized) return
        
        synchronized(this) {
            if (isInitialized) return
            
            // Start Python if not already running
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            
            // Load the DSP analyzer module
            val python = Python.getInstance()
            dspModule = python.getModule("dsp_analyzer")
            
            isInitialized = true
        }
    }
    
    /**
     * Check if bridge is ready for analysis.
     */
    fun isReady(): Boolean = isInitialized && dspModule != null
    
    /**
     * Analyze a WAV file and return DSP results.
     * 
     * MUST be called from a coroutine context.
     * Runs on Dispatchers.Default (CPU thread pool) to avoid blocking UI.
     * 
     * @param wavPath Absolute path to WAV file
     * @return DspResult on success, null on failure
     * @throws DspException if Python throws an exception
     */
    suspend fun analyzeAudio(wavPath: String): DspResult? = withContext(Dispatchers.Default) {
        if (!isReady()) {
            throw DspException("PythonDspBridge not initialized. Call initialize() first.")
        }
        
        // Ensure only one analysis at a time
        analysisMutex.withLock {
            try {
                val module = dspModule!!
                
                // Call Python function: analyze_audio(wav_path) -> dict
                val pyResult: PyObject = module.callAttr("analyze_audio", wavPath)
                
                // Convert Python dict to Kotlin DspResult
                convertPyObjectToResult(pyResult)
                
            } catch (e: Exception) {
                throw DspException("DSP analysis failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Convert Python dictionary to Kotlin DspResult.
     * 
     * IMPORTANT: PyObject.get(String) calls Python getattr(), NOT dict[key].
     * Must use asMap() to properly access Python dict entries.
     * 
     * Python returns:
     * {
     *     "frequency": float,
     *     "power": float,
     *     "surface_tension": float,
     *     "noise_status": str,
     *     "confidence": float
     * }
     */
    private fun convertPyObjectToResult(pyDict: PyObject): DspResult? {
        try {
            // Convert Python dict → Java Map for reliable key access
            val map = pyDict.asMap()
            
            var frequency = 0.0
            var power = -100.0
            var surfaceTension = 0.0
            var noiseStatus = "NOISE"
            var confidence = 0.0
            
            for ((key, value) in map) {
                when (key.toString()) {
                    "error" -> {
                        val errorMsg = value.toString()
                        if (errorMsg.isNotEmpty() && errorMsg != "None") {
                            throw DspException("Python analysis error: $errorMsg")
                        }
                    }
                    "frequency" -> frequency = value.toDouble()
                    "power" -> power = value.toDouble()
                    "surface_tension" -> surfaceTension = value.toDouble()
                    "noise_status" -> noiseStatus = value.toString()
                    "confidence" -> confidence = value.toDouble()
                }
            }
            
            return DspResult(
                frequency = frequency,
                power = power,
                surfaceTension = surfaceTension,
                noiseStatus = noiseStatus,
                confidence = confidence
            )
        } catch (e: DspException) {
            throw e
        } catch (e: Exception) {
            throw DspException("Failed to parse DSP result: ${e.message}", e)
        }
    }
}

/**
 * Exception thrown when DSP analysis fails.
 */
class DspException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
