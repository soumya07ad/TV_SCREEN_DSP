package com.example.tvscreendsp.dsp

/**
 * # Chaquopy Bridge Pitfalls - Developer Reference
 * 
 * This file documents critical issues when using the Python bridge.
 * Read this before modifying bridge code.
 * 
 * ## 1. INITIALIZATION THREAD
 * 
 * **Pitfall**: Calling Python.start() from a background thread
 * **Symptom**: Native crash or AndroidPlatform initialization failure
 * **Solution**: Initialize on main thread (Application.onCreate() or Activity.onCreate())
 * 
 * ```kotlin
 * // ❌ WRONG - may crash
 * viewModelScope.launch(Dispatchers.IO) {
 *     PythonDspBridge.initialize(context)
 * }
 * 
 * // ✅ CORRECT - main thread initialization
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         PythonDspBridge.initialize(this)
 *     }
 * }
 * ```
 * 
 * ---
 * 
 * ## 2. PYTHON GIL (GLOBAL INTERPRETER LOCK)
 * 
 * **Pitfall**: Calling Python from multiple threads simultaneously
 * **Symptom**: Race conditions, data corruption, crashes
 * **Solution**: Use Mutex to serialize Python calls
 * 
 * ```kotlin
 * // ✅ CORRECT - Mutex serialization
 * private val analysisMutex = Mutex()
 * 
 * suspend fun analyzeAudio(wavPath: String) = analysisMutex.withLock {
 *     // Only one thread can execute Python at a time
 *     dspModule.callAttr("analyze_audio", wavPath)
 * }
 * ```
 * 
 * **Why**: Python's GIL prevents true parallel execution, but Chaquopy
 * can still have race conditions during native/Kotlin interop.
 * 
 * ---
 * 
 * ## 3. UI THREAD BLOCKING
 * 
 * **Pitfall**: Running DSP analysis on main thread
 * **Symptom**: ANR (Application Not Responding)
 * **Solution**: Use Dispatchers.Default for CPU-intensive work
 * 
 * ```kotlin
 * // ❌ WRONG - blocks UI
 * fun analyzeAudio(wavPath: String): DspResult {
 *     return dspModule.callAttr("analyze_audio", wavPath)
 * }
 * 
 * // ✅ CORRECT - background thread
 * suspend fun analyzeAudio(wavPath: String) = withContext(Dispatchers.Default) {
 *     dspModule.callAttr("analyze_audio", wavPath)
 * }
 * ```
 * 
 * **Note**: Use Dispatchers.Default (not IO) for CPU-bound work like DSP.
 * 
 * ---
 * 
 * ## 4. PYOBJECT TYPE CONVERSION
 * 
 * **Pitfall**: Assuming PyObject types match Kotlin types
 * **Symptom**: ClassCastException, NumberFormatException
 * **Solution**: Use PyObject conversion methods carefully
 * 
 * ```kotlin
 * // ❌ WRONG - may fail if Python returns different type
 * val freq = pyDict.get("frequency") as Double
 * 
 * // ✅ CORRECT - safe conversion with null check
 * val freq = pyDict.get("frequency")?.toDouble()
 *     ?: throw DspException("Missing 'frequency'")
 * ```
 * 
 * **PyObject methods**:
 * - `.toInt()`, `.toLong()`, `.toDouble()` for numbers
 * - `.toString()` for strings
 * - `.asList()` for lists
 * - `.asMap()` for dicts
 * 
 * ---
 * 
 * ## 5. PYTHON EXCEPTIONS
 * 
 * **Pitfall**: Not catching Python exceptions
 * **Symptom**: Unhandled exception crashes app
 * **Solution**: Wrap callAttr in try-catch
 * 
 * ```kotlin
 * try {
 *     val result = dspModule.callAttr("analyze_audio", wavPath)
 * } catch (e: PyException) {
 *     // Python exception - e.message contains Python traceback
 *     Log.e("DSP", "Python error: ${e.message}")
 * }
 * ```
 * 
 * ---
 * 
 * ## 6. MODULE LOADING
 * 
 * **Pitfall**: Module not found because of incorrect path
 * **Symptom**: `ModuleNotFoundError: No module named 'dsp_analyzer'`
 * **Solution**: Ensure Python file is in src/main/python/
 * 
 * ```
 * app/
 *   src/
 *     main/
 *       java/      # Kotlin code
 *       python/    # Python code ← dsp_analyzer.py goes here
 * ```
 * 
 * ---
 * 
 * ## 7. NUMPY/SCIPY COMPATIBILITY
 * 
 * **Pitfall**: Using scipy (too large, slow to build)
 * **Symptom**: Build times of 10+ minutes, APK size explosion
 * **Solution**: Use numpy only for DSP operations
 * 
 * NumPy-only alternatives:
 * - FFT: `numpy.fft.rfft()` instead of `scipy.fft`
 * - Power: `numpy.sqrt(numpy.mean(samples**2))`
 * - Spectral flatness: Manual implementation
 * 
 * ---
 * 
 * ## 8. APK SIZE MANAGEMENT
 * 
 * **Pitfall**: Including all ABIs
 * **Symptom**: APK size 50+ MB
 * **Solution**: Filter to needed ABIs
 * 
 * ```kotlin
 * // In build.gradle.kts
 * ndk {
 *     abiFilters += listOf("arm64-v8a", "armeabi-v7a")
 *     // Remove x86 variants if not needed for emulator
 * }
 * ```
 * 
 * ---
 * 
 * ## 9. MEMORY LEAKS
 * 
 * **Pitfall**: Holding PyObject references too long
 * **Symptom**: Memory usage grows over time
 * **Solution**: Let PyObjects go out of scope after use
 * 
 * ```kotlin
 * // ✅ CORRECT - pyResult goes out of scope after function
 * suspend fun analyzeAudio(wavPath: String): DspResult {
 *     val pyResult = dspModule.callAttr("analyze_audio", wavPath)
 *     return convertPyObjectToResult(pyResult)  // pyResult released after return
 * }
 * ```
 * 
 * ---
 * 
 * ## QUICK REFERENCE: Bridge Checklist
 * 
 * Before implementing:
 * ☐ Initialize Python on main thread?
 * ☐ Mutex for serialized Python calls?
 * ☐ DSP runs on Dispatchers.Default?
 * 
 * During implementation:
 * ☐ Safe PyObject type conversions?
 * ☐ Python exceptions caught?
 * ☐ Error field checked in result dict?
 * 
 * Before release:
 * ☐ ABI filters configured?
 * ☐ Unnecessary scipy dependencies removed?
 * ☐ Memory usage tested?
 */
@Suppress("unused")
object ChaquopyPitfalls {
    // This file is documentation only - no runtime code
}
