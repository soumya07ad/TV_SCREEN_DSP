package com.example.tvscreendsp.audio

/**
 * # Common AudioRecord Pitfalls - Developer Reference
 * 
 * This file documents critical issues when using Android's AudioRecord API.
 * Read this before modifying any audio recording code.
 * 
 * ## 1. BUFFER SIZE ISSUES
 * 
 * **Pitfall**: Using buffer smaller than `getMinBufferSize()` return value
 * **Symptom**: Recording fails silently or produces corrupted audio
 * **Solution**: Always use `getMinBufferSize()` as minimum:
 * ```kotlin
 * val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
 * val bufferSize = maxOf(minBuffer * 2, 4096) // Use 2x minimum for safety
 * ```
 * 
 * **Pitfall**: Not checking for ERROR or ERROR_BAD_VALUE from getMinBufferSize()
 * **Symptom**: Crash when constructing AudioRecord
 * **Solution**: Validate return value before proceeding
 * 
 * ---
 * 
 * ## 2. PERMISSION ISSUES
 * 
 * **Pitfall**: Missing runtime permission request on Android 6.0+
 * **Symptom**: SecurityException when starting recording
 * **Solution**:
 * 1. Declare in AndroidManifest: `<uses-permission android:name="android.permission.RECORD_AUDIO"/>`
 * 2. Request at runtime before recording:
 * ```kotlin
 * if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
 *     != PackageManager.PERMISSION_GRANTED) {
 *     // Request permission using ActivityResultLauncher
 * }
 * ```
 * 
 * **Pitfall**: Not handling permission denial gracefully
 * **Symptom**: App crashes or shows blank state
 * **Solution**: Show informative message, provide settings shortcut
 * 
 * ---
 * 
 * ## 3. THREADING MISTAKES
 * 
 * **Pitfall**: Calling AudioRecord.read() on Main/UI thread
 * **Symptom**: ANR (Application Not Responding), janky UI
 * **Solution**: Use Dispatchers.IO for all audio operations:
 * ```kotlin
 * flow { ... }.flowOn(Dispatchers.IO)
 * ```
 * 
 * **Pitfall**: Not using @Volatile for isRecording flag
 * **Symptom**: Recording doesn't stop when requested
 * **Solution**: Mark with `@Volatile` or use AtomicBoolean
 * 
 * **Pitfall**: Modifying UI state from background thread
 * **Symptom**: Crash with "CalledFromWrongThreadException"
 * **Solution**: Use StateFlow/LiveData which handle thread safety
 * 
 * ---
 * 
 * ## 4. AUDIORECORD STATE ISSUES
 * 
 * **Pitfall**: Not checking STATE_INITIALIZED after construction
 * **Symptom**: Recording fails silently
 * **Solution**: 
 * ```kotlin
 * if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
 *     throw IllegalStateException("AudioRecord initialization failed")
 * }
 * ```
 * 
 * **Pitfall**: Not releasing AudioRecord when done
 * **Symptom**: Resource leak, microphone stays busy
 * **Solution**: Always call release() in finally block
 * 
 * **Pitfall**: Calling stop() without checking if recording started
 * **Symptom**: IllegalStateException
 * **Solution**: Wrap in try-catch or track state explicitly
 * 
 * ---
 * 
 * ## 5. AUDIO FORMAT ISSUES
 * 
 * **Pitfall**: Using ENCODING_PCM_8BIT for DSP analysis
 * **Symptom**: Poor dynamic range, noisy analysis results
 * **Solution**: Always use ENCODING_PCM_16BIT for DSP
 * 
 * **Pitfall**: Assuming stereo when device only supports mono
 * **Symptom**: AudioRecord initialization failure
 * **Solution**: Use CHANNEL_IN_MONO for maximum compatibility
 * 
 * ---
 * 
 * ## 6. WAV FILE ISSUES
 * 
 * **Pitfall**: Wrong byte order in WAV header
 * **Symptom**: Playback fails or sounds corrupted
 * **Solution**: Use ByteOrder.LITTLE_ENDIAN explicitly
 * 
 * **Pitfall**: Incorrect chunk size calculations
 * **Symptom**: WAV players report file corruption
 * **Solution**: Follow exact RIFF specification:
 * - ChunkSize = file size - 8
 * - Subchunk2Size = actual PCM data length
 * 
 * **Pitfall**: Not creating parent directories before file write
 * **Symptom**: FileNotFoundException
 * **Solution**: Call `file.parentFile?.mkdirs()` before writing
 * 
 * ---
 * 
 * ## 7. RECORDING DURATION ISSUES
 * 
 * **Pitfall**: Using System.currentTimeMillis() for timing
 * **Symptom**: Drift, inconsistent recording length
 * **Solution**: Calculate bytes needed: `sampleRate * seconds * bytesPerSample * channels`
 * 
 * **Pitfall**: Not handling partial reads from AudioRecord.read()
 * **Symptom**: Incomplete audio, buffer underruns
 * **Solution**: Accumulate bytes in loop until target reached
 * 
 * ---
 * 
 * ## QUICK REFERENCE: MicrophoneRecorder Checklist
 * 
 * Before starting recording:
 * ☐ Permission granted?
 * ☐ Minimum buffer size obtained and valid?
 * ☐ AudioRecord STATE_INITIALIZED?
 * ☐ Running on background thread?
 * 
 * During recording:
 * ☐ Loop until exact byte count reached?
 * ☐ Handling all AudioRecord.read() error codes?
 * ☐ Emitting progress updates?
 * 
 * After recording:
 * ☐ Stopped AudioRecord?
 * ☐ Released AudioRecord resources?
 * ☐ WAV header written with correct byte order?
 * ☐ State updated for UI?
 */
@Suppress("unused")
object AudioRecordPitfalls {
    // This file is documentation only - no runtime code
}
