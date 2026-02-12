package com.example.tvscreendsp.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeoutException

/**
 * Manages USB-UART serial communication for external trigger devices.
 *
 * This class is responsible ONLY for serial I/O:
 * - Opening/closing the serial port
 * - Sending commands ("TAP\n", "START\n")
 * - Reading serial responses (e.g., "DONE" handshake)
 * - Reporting connection state via [StateFlow]
 *
 * It does NOT handle:
 * - USB permission requests (handled by [UsbPermissionHandler])
 * - USB attach/detach events (handled by [UsbDeviceMonitor])
 * - Recording or DSP logic (handled by [MeasurementViewModel])
 *
 * ## Why [Mutex]?
 * The serial port is a shared hardware resource. Even though we expect
 * sequential writes, a [Mutex] guarantees that rapid button taps or
 * retry logic cannot produce concurrent writes to the same port.
 * This mirrors the pattern used in [PythonDspBridge.analysisMutex].
 *
 * ## Why [Dispatchers.IO]?
 * Serial port operations (`open`, `write`, `close`) are blocking I/O.
 * Running them on [Dispatchers.IO] prevents ANR on the main thread
 * and keeps them off the CPU-bound [Dispatchers.Default] pool.
 *
 * ## Why [Result] wrapper?
 * USB hardware is inherently unreliable — cables can be unplugged at
 * any moment. [Result] lets the caller (ViewModel) decide how to handle
 * failures without try-catch boilerplate, and supports graceful degradation
 * to microphone-only mode.
 *
 * @param context Application context (not Activity — avoids leaks)
 * @param usbManager System [UsbManager] service
 */
class UsbUartManager(
    private val usbManager: UsbManager
) {
    companion object {
        private const val TAG = "UsbUartManager"

        // Serial port configuration
        private const val BAUD_RATE = 115200
        private const val DATA_BITS = 8
        private const val STOP_BITS = UsbSerialPort.STOPBITS_1
        private const val PARITY = UsbSerialPort.PARITY_NONE

        // Trigger command sent to the external tapping device
        private const val TRIGGER_COMMAND = "TAP\n"

        // Handshake command sent to initiate measurement
        private const val START_COMMAND = "START\n"

        // Expected response from ESP32 after START
        private const val DONE_RESPONSE = "DONE"

        // Timeout for serial write operations (milliseconds)
        private const val WRITE_TIMEOUT_MS = 1000

        // Buffer size for serial reads (bytes)
        private const val READ_BUFFER_SIZE = 64

        // Per-iteration read timeout (milliseconds)
        // Short enough to check overall timeout frequently
        private const val READ_POLL_TIMEOUT_MS = 100
    }

    // --- Internal state ---

    private var serialPort: UsbSerialPort? = null
    private var deviceConnection: UsbDeviceConnection? = null

    /** Mutex to serialize all serial write operations. */
    private val writeMutex = Mutex()

    private val _connectionState = MutableStateFlow<UsbConnectionState>(
        UsbConnectionState.Disconnected
    )

    /** Observable connection state for UI and ViewModel consumption. */
    val connectionState: StateFlow<UsbConnectionState> = _connectionState.asStateFlow()

    // --- Public API ---

    /**
     * Connects to the first available USB-serial device.
     *
     * Probes for supported drivers, opens the device, and configures
     * the port to 115200 baud, 8N1. All blocking operations run on
     * [Dispatchers.IO].
     *
     * @return [Result.success] if connected, [Result.failure] with cause otherwise
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = UsbConnectionState.Connecting
            Log.d(TAG, "Scanning for USB-serial devices...")

            // Step 1: Find available drivers
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            if (availableDrivers.isEmpty()) {
                Log.d(TAG, "No USB-serial devices found")
                _connectionState.value = UsbConnectionState.Disconnected
                return@withContext Result.failure(
                    NoSuchElementException("No USB-serial device found. Connect a device via OTG.")
                )
            }

            val driver = availableDrivers.first()
            val device: UsbDevice = driver.device
            Log.d(TAG, "Found device: ${device.deviceName} (VID=${device.vendorId}, PID=${device.productId})")

            // Step 2: Check USB permission
            if (!usbManager.hasPermission(device)) {
                Log.e(TAG, "USB permission not granted for ${device.deviceName}")
                _connectionState.value = UsbConnectionState.PermissionDenied
                return@withContext Result.failure(
                    SecurityException("USB permission not granted. Request permission first.")
                )
            }

            // Step 3: Open device connection
            val connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "Failed to open USB device connection")
                _connectionState.value = UsbConnectionState.Error("Failed to open device connection")
                return@withContext Result.failure(
                    IOException("Failed to open USB device connection")
                )
            }
            deviceConnection = connection

            // Step 4: Open and configure serial port
            val port = driver.ports.firstOrNull()
            if (port == null) {
                Log.e(TAG, "No serial ports available on device")
                connection.close()
                deviceConnection = null
                _connectionState.value = UsbConnectionState.Error("No serial ports on device")
                return@withContext Result.failure(
                    IOException("Device has no serial ports")
                )
            }

            port.open(connection)
            port.setParameters(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY)
            serialPort = port

            val deviceName = "${device.productName ?: "USB-Serial"} (${device.deviceName})"
            _connectionState.value = UsbConnectionState.Connected(deviceName)
            Log.d(TAG, "Connected: $deviceName @ ${BAUD_RATE} baud, 8N1")

            Result.success(Unit)

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during connect", e)
            _connectionState.value = UsbConnectionState.PermissionDenied
            closePortSafely()
            Result.failure(e)

        } catch (e: IOException) {
            Log.e(TAG, "IO exception during connect", e)
            _connectionState.value = UsbConnectionState.Error("Connection failed: ${e.message}")
            closePortSafely()
            Result.failure(e)

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during connect", e)
            _connectionState.value = UsbConnectionState.Error("Unexpected: ${e.message}")
            closePortSafely()
            Result.failure(e)
        }
    }

    /**
     * Sends the trigger command ("TAP\n") to the connected serial device.
     *
     * The write is serialized via [Mutex] to prevent concurrent port access.
     * Runs on [Dispatchers.IO] since serial write is blocking.
     *
     * @return [Result.success] if sent, [Result.failure] if not connected or write fails
     */
    suspend fun sendTrigger(): Result<Unit> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val port = serialPort
            if (port == null || _connectionState.value !is UsbConnectionState.Connected) {
                Log.e(TAG, "Cannot send trigger: not connected (state=${_connectionState.value})")
                return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
            }

            try {
                val data = TRIGGER_COMMAND.toByteArray(Charsets.US_ASCII)
                port.write(data, WRITE_TIMEOUT_MS)
                Log.d(TAG, "Trigger sent: ${TRIGGER_COMMAND.trim()} (${data.size} bytes)")
                Result.success(Unit)

            } catch (e: IOException) {
                // Device likely disconnected mid-write
                Log.e(TAG, "Write failed — device may have been disconnected", e)
                _connectionState.value = UsbConnectionState.Error("Write failed: ${e.message}")
                closePortSafely()
                Result.failure(e)

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during trigger send", e)
                _connectionState.value = UsbConnectionState.Error("Send error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // --- Handshake Protocol ---

    /**
     * Sends the start command ("START\n") to begin the handshake.
     *
     * Write is serialized via [Mutex] to prevent concurrent port access.
     * Runs on [Dispatchers.IO] since serial write is blocking.
     *
     * @return [Result.success] if sent, [Result.failure] if not connected or write fails
     */
    suspend fun sendStartCommand(): Result<Unit> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val port = serialPort
            if (port == null || _connectionState.value !is UsbConnectionState.Connected) {
                Log.e(TAG, "Cannot send START: not connected (state=${_connectionState.value})")
                return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
            }

            try {
                val data = START_COMMAND.toByteArray(Charsets.US_ASCII)
                port.write(data, WRITE_TIMEOUT_MS)
                Log.d(TAG, "START command sent (${data.size} bytes)")
                Result.success(Unit)

            } catch (e: IOException) {
                Log.e(TAG, "START write failed — device may have been disconnected", e)
                _connectionState.value = UsbConnectionState.Error("Write failed: ${e.message}")
                closePortSafely()
                Result.failure(e)

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error sending START", e)
                _connectionState.value = UsbConnectionState.Error("Send error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Waits for the ESP32 to respond with "DONE" after a START command.
     *
     * Continuously reads from the serial port, accumulating partial data
     * into a buffer until the substring "DONE" is detected or the timeout
     * is exceeded.
     *
     * **Thread safety**: This method does NOT acquire [writeMutex].
     * Reads and writes can safely happen on separate threads since
     * USB serial ports support full-duplex I/O.
     *
     * @param timeoutMs Maximum time to wait for "DONE" (default 5000ms)
     * @return [Result.success] with latency in milliseconds, or
     *         [Result.failure] with [TimeoutException] or [IOException]
     */
    suspend fun waitForDone(timeoutMs: Long = 5000L): Result<Long> = withContext(Dispatchers.IO) {
        val port = serialPort
        if (port == null || _connectionState.value !is UsbConnectionState.Connected) {
            Log.e(TAG, "Cannot read: not connected (state=${_connectionState.value})")
            return@withContext Result.failure<Long>(
                IllegalStateException("Not connected. Call connect() first.")
            )
        }

        val buffer = ByteArray(READ_BUFFER_SIZE)
        val accumulated = StringBuilder()
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "Waiting for DONE response (timeout=${timeoutMs}ms)...")

        try {
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= timeoutMs) {
                    Log.e(TAG, "Timeout: DONE not received within ${timeoutMs}ms")
                    return@withContext Result.failure<Long>(
                        TimeoutException(
                            "DONE not received within ${timeoutMs}ms. " +
                                "Buffer contents: '${accumulated}'"
                        )
                    )
                }

                val bytesRead = port.read(buffer, READ_POLL_TIMEOUT_MS)

                if (bytesRead > 0) {
                    val chunk = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                    accumulated.append(chunk)
                    Log.d(TAG, "Serial RX: '$chunk' (${bytesRead} bytes, total=${accumulated.length})")

                    if (accumulated.contains(DONE_RESPONSE)) {
                        val latency = System.currentTimeMillis() - startTime
                        Log.d(TAG, "DONE detected — latency=${latency}ms")
                        accumulated.clear()
                        return@withContext Result.success(latency)
                    }
                }
                // bytesRead == 0 means no data yet — loop continues
            }
        } catch (e: IOException) {
            Log.e(TAG, "Read failed — device may have been disconnected", e)
            _connectionState.value = UsbConnectionState.Error("Read failed: ${e.message}")
            closePortSafely()
            return@withContext Result.failure<Long>(e)

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during serial read", e)
            _connectionState.value = UsbConnectionState.Error("Read error: ${e.message}")
            return@withContext Result.failure<Long>(e)
        }
        
        // Should be unreachable due to infinite loop + returns above
        return@withContext Result.failure<Long>(IllegalStateException("Unreachable"))
    }

    /**
     * Disconnects from the USB-serial device and releases all resources.
     *
     * Safe to call multiple times — idempotent.
     * Runs on [Dispatchers.IO] since close operations may block.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Disconnecting...")
        closePortSafely()
        _connectionState.value = UsbConnectionState.Disconnected
        Log.d(TAG, "Disconnected")
    }

    /**
     * Notifies the manager that a USB device was physically detached.
     *
     * Called by [UsbDeviceMonitor] when it receives a detach broadcast.
     * Cleans up resources without blocking.
     */
    fun onDeviceDetached() {
        Log.d(TAG, "Device detached — cleaning up")
        closePortSafely()
        _connectionState.value = UsbConnectionState.Disconnected
    }

    // --- Internal helpers ---

    /**
     * Safely closes the serial port and device connection.
     *
     * Catches and logs all exceptions to ensure cleanup never crashes.
     * Nulls out references to prevent use-after-close.
     */
    private fun closePortSafely() {
        try {
            serialPort?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing serial port", e)
        }
        serialPort = null

        try {
            deviceConnection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing device connection", e)
        }
        deviceConnection = null
    }
}
