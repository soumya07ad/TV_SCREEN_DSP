package com.example.tvscreendsp.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Monitors USB device attach/detach events via BroadcastReceiver.
 *
 * This class is separate from [UsbUartManager] by design:
 * - [UsbUartManager] handles serial I/O only
 * - [UsbDeviceMonitor] handles hardware plug/unplug events
 *
 * On attach, automatically requests permission (if needed) and connects
 * the serial port via [UsbUartManager]. On detach, cleans up resources.
 *
 * ## Lifecycle Contract
 * - Call [register] in `Activity.onCreate()` or equivalent
 * - Call [unregister] in `Activity.onDestroy()` or equivalent
 * - **Never auto-registers in the constructor** — caller controls lifecycle
 * - Safe to call [unregister] multiple times (idempotent)
 *
 * @param context Application context (not Activity — avoids leaks)
 * @param usbUartManager The serial manager for connect/disconnect
 * @param usbPermissionHandler Handles suspend-based USB permission dialog
 */
class UsbDeviceMonitor(
    private val context: Context,
    private val usbUartManager: UsbUartManager,
    private val usbPermissionHandler: UsbPermissionHandler
) {
    companion object {
        private const val TAG = "UsbDeviceMonitor"
    }

    /** System UsbManager for permission checks. */
    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var isRegistered = false

    /**
     * Coroutine scope for async work (permission requests, connect).
     * Created in [register], cancelled in [unregister] to prevent leaks.
     */
    private var monitorScope: CoroutineScope? = null

    /**
     * Optional callback invoked when a USB device is attached.
     * Called *before* the auto-connect flow starts.
     * If null, attach events are only logged + auto-connected.
     */
    var onDeviceAttached: ((UsbDevice) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> handleAttach(intent)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> handleDetach(intent)
            }
        }
    }

    /**
     * Registers the BroadcastReceiver for USB attach/detach events.
     *
     * Call this in `Activity.onCreate()` or `onStart()`.
     * Safe to call multiple times — duplicate registrations are prevented.
     */
    fun register() {
        if (isRegistered) {
            Log.d(TAG, "Already registered — skipping duplicate registration")
            return
        }

        monitorScope = MainScope()

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        isRegistered = true
        Log.d(TAG, "Registered for USB attach/detach events")
    }

    /**
     * Unregisters the BroadcastReceiver and cancels any pending coroutines.
     *
     * Call this in `Activity.onDestroy()` or `onStop()`.
     * Safe to call multiple times — idempotent.
     */
    fun unregister() {
        if (!isRegistered) return

        // Cancel any in-flight permission requests or connect attempts
        monitorScope?.cancel()
        monitorScope = null

        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Receiver was not registered")
        }

        isRegistered = false
        Log.d(TAG, "Unregistered USB monitor")
    }

    /**
     * Handles USB device attachment.
     *
     * Logs the event, invokes the optional callback, then launches
     * the auto-permission + auto-connect flow in [monitorScope].
     */
    private fun handleAttach(intent: Intent) {
        val device = extractDevice(intent)
        if (device != null) {
            Log.d(
                TAG,
                "USB ATTACHED: ${device.productName ?: "Unknown"} " +
                    "(VID=0x${device.vendorId.toString(16)}, " +
                    "PID=0x${device.productId.toString(16)}, " +
                    "path=${device.deviceName})"
            )

            // Notify external listener (if any) before auto-connect
            onDeviceAttached?.invoke(device)

            // Launch auto-permission + auto-connect flow
            monitorScope?.launch {
                autoConnectDevice(device)
            } ?: Log.e(TAG, "Monitor scope is null — was register() called?")
        } else {
            Log.d(TAG, "USB ATTACHED: device info unavailable")
        }
    }

    /**
     * Handles USB device detachment.
     *
     * Notifies [UsbUartManager] to clean up serial port resources.
     */
    private fun handleDetach(intent: Intent) {
        val device = extractDevice(intent)
        Log.d(
            TAG,
            "USB DETACHED: ${device?.productName ?: device?.deviceName ?: "Unknown device"}"
        )

        // Notify UsbUartManager to clean up serial port resources
        usbUartManager.onDeviceDetached()
        Log.d(TAG, "Device detached — connection state reset to Disconnected")
    }

    /**
     * Auto-permission + auto-connect flow.
     *
     * Steps:
     * 1. Check if permission is already granted
     * 2. If not, request permission (suspends until dialog result)
     * 3. If granted, connect the serial port
     *
     * All failures are logged but never crash the app.
     */
    private suspend fun autoConnectDevice(device: UsbDevice) {
        try {
            // Step 1: Check permission
            if (!usbManager.hasPermission(device)) {
                Log.d(TAG, "Permission not granted — requesting for ${device.deviceName}")
                val granted = usbPermissionHandler.requestPermission(device)

                if (!granted) {
                    Log.d(TAG, "Permission DENIED by user for ${device.deviceName}")
                    return
                }
                Log.d(TAG, "Permission GRANTED for ${device.deviceName}")
            } else {
                Log.d(TAG, "Permission already granted for ${device.deviceName}")
            }

            // Step 2: Connect serial port
            Log.d(TAG, "Connecting to ${device.deviceName}...")
            val result = usbUartManager.connect()

            if (result.isSuccess) {
                Log.d(TAG, "Connect SUCCESS for ${device.deviceName}")
            } else {
                Log.e(
                    TAG,
                    "Connect FAILED for ${device.deviceName}: " +
                        "${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-connect failed for ${device.deviceName}", e)
        }
    }

    private fun extractDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }
}
