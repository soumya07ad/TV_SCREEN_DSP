package com.example.tvscreendsp.usb

/**
 * Sealed class representing the USB-UART connection state machine.
 *
 * State transitions:
 * ```
 * Disconnected → Connecting → Connected
 *                    ↓             ↓
 *                  Error      Disconnected
 *                    ↓
 *              Disconnected
 * ```
 */
sealed class UsbConnectionState {

    /** No USB device is connected or port is closed. */
    data object Disconnected : UsbConnectionState()

    /** Port is being opened and configured. */
    data object Connecting : UsbConnectionState()

    /** Serial port is open and ready to send commands. */
    data class Connected(val deviceName: String) : UsbConnectionState()

    /** USB permission was denied by the user. */
    data object PermissionDenied : UsbConnectionState()

    /** An error occurred during connection or communication. */
    data class Error(val message: String) : UsbConnectionState()

    /** Returns true if the port is ready for I/O. */
    val isConnected: Boolean
        get() = this is Connected
}
