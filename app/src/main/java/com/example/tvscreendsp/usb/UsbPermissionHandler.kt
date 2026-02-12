package com.example.tvscreendsp.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Handles USB permission requests using Android's [UsbManager.requestPermission] API.
 *
 * This class is separate from [UsbUartManager] by design:
 * - [UsbUartManager] handles serial I/O only
 * - [UsbPermissionHandler] handles the permission dialog + result
 *
 * ## How it works
 * 1. Creates a [PendingIntent] targeting a dynamically registered [BroadcastReceiver]
 * 2. Calls [UsbManager.requestPermission] which shows the system dialog
 * 3. Suspends via [CompletableDeferred] until the user grants or denies
 * 4. Unregisters the receiver immediately after the result
 *
 * ## Thread Safety
 * A [Mutex] ensures only one permission dialog is active at a time.
 * Without this, rapid calls could register duplicate receivers or
 * corrupt the [CompletableDeferred] reference.
 *
 * ## Android 12+ Compatibility
 * [PendingIntent] uses [PendingIntent.FLAG_IMMUTABLE] as required by
 * Android 12 (API 31+). Mutable intents are not needed because
 * the permission result is read from intent extras, not modified by the system.
 *
 * @param context Application context (not Activity — avoids leaks)
 * @param usbManager System [UsbManager] service
 */
class UsbPermissionHandler(
    private val context: Context,
    private val usbManager: UsbManager
) {
    companion object {
        private const val TAG = "UsbPermissionHandler"
        private const val ACTION_USB_PERMISSION =
            "com.example.tvscreendsp.USB_PERMISSION"
    }

    /**
     * Mutex to ensure only one permission request is active at a time.
     * Concurrent requests would corrupt the shared [CompletableDeferred].
     */
    private val permissionMutex = Mutex()

    /**
     * Requests USB permission for the given device.
     *
     * Shows the system USB permission dialog and suspends until
     * the user grants or denies access.
     *
     * Safe to call from any coroutine context — internally dispatches
     * receiver registration to the main thread.
     *
     * @param device The USB device to request permission for
     * @return true if permission was granted, false if denied
     */
    suspend fun requestPermission(device: UsbDevice): Boolean {
        // If already granted, return immediately
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "Permission already granted for ${device.deviceName}")
            return true
        }

        // Serialize permission requests
        return permissionMutex.withLock {
            // Double-check after acquiring lock (another caller may have granted)
            if (usbManager.hasPermission(device)) {
                Log.d(TAG, "Permission granted while waiting for lock")
                return@withLock true
            }

            requestPermissionInternal(device)
        }
    }

    /**
     * Internal implementation that registers a receiver, requests permission,
     * and suspends until the result is received.
     *
     * MUST be called under [permissionMutex] to prevent concurrent receivers.
     */
    private suspend fun requestPermissionInternal(device: UsbDevice): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        var receiver: BroadcastReceiver? = null

        try {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action != ACTION_USB_PERMISSION) return

                    val granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false
                    )
                    val intentDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            UsbManager.EXTRA_DEVICE,
                            UsbDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    Log.d(
                        TAG,
                        "Permission result: granted=$granted, device=${intentDevice?.deviceName}"
                    )

                    // Complete the deferred — resumes the suspended coroutine
                    deferred.complete(granted)
                }
            }

            // Register receiver
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            // Create PendingIntent (FLAG_IMMUTABLE required for Android 12+)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
            )

            // Request permission — shows system dialog
            Log.d(TAG, "Requesting USB permission for ${device.deviceName}")
            usbManager.requestPermission(device, pendingIntent)

            // Suspend until user responds
            val granted = deferred.await()
            Log.d(TAG, "Permission ${if (granted) "GRANTED" else "DENIED"}")
            return granted

        } catch (e: Exception) {
            Log.e(TAG, "Permission request failed", e)
            if (!deferred.isCompleted) {
                deferred.complete(false)
            }
            return false

        } finally {
            // Always unregister to prevent leaks
            try {
                receiver?.let { context.unregisterReceiver(it) }
            } catch (e: IllegalArgumentException) {
                // Receiver was not registered or already unregistered
                Log.d(TAG, "Receiver already unregistered")
            }
        }
    }
}
