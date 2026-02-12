package com.example.tvscreendsp.usb

/**
 * # USB-UART Integration Strategy
 *
 * ## Where to Create & Manage Components
 *
 * ### MainActivity.kt — Lifecycle owner
 * ```
 * class MainActivity : ComponentActivity() {
 *     private lateinit var usbUartManager: UsbUartManager
 *     private lateinit var usbPermissionHandler: UsbPermissionHandler
 *     private lateinit var usbDeviceMonitor: UsbDeviceMonitor
 *
 *     override fun onCreate() {
 *         val usbManager = getSystemService(USB_SERVICE) as UsbManager
 *         val appContext = applicationContext
 *
 *         usbUartManager = UsbUartManager(appContext, usbManager)
 *         usbPermissionHandler = UsbPermissionHandler(appContext, usbManager)
 *         usbDeviceMonitor = UsbDeviceMonitor(appContext, usbUartManager)
 *
 *         // Register monitor for plug/unplug events
 *         usbDeviceMonitor.register()
 *
 *         // Optional: auto-connect callback when device is plugged in
 *         usbDeviceMonitor.onDeviceAttached = { device ->
 *             // Notify ViewModel or trigger connection
 *         }
 *
 *         // Pass to ViewModel via factory or manual injection
 *         // viewModel.setUsbComponents(usbUartManager, usbPermissionHandler)
 *     }
 *
 *     override fun onDestroy() {
 *         usbDeviceMonitor.unregister()     // Prevents leaked receiver
 *         runBlocking { usbUartManager.disconnect() }  // Releases serial port
 *         super.onDestroy()
 *     }
 * }
 * ```
 *
 * ### MeasurementViewModel — Orchestrator
 * ```
 * // Permission + connect flow:
 * viewModelScope.launch {
 *     val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
 *     val device = drivers.firstOrNull()?.device ?: return@launch
 *
 *     // Step 1: Request permission (suspends until dialog result)
 *     val granted = usbPermissionHandler.requestPermission(device)
 *     if (!granted) return@launch
 *
 *     // Step 2: Connect serial port
 *     usbUartManager.connect()
 * }
 *
 * // Trigger during recording:
 * viewModelScope.launch {
 *     val recordingJob = launch { collectRecordingFlow() }
 *     delay(300)
 *     if (usbUartManager.connectionState.value.isConnected) {
 *         usbUartManager.sendTrigger()   // Graceful: no-op if not connected
 *     }
 *     recordingJob.join()
 *     // Continue DSP pipeline...
 * }
 * ```
 *
 * ### Compose UI — Observe state
 * ```
 * val usbState by usbUartManager.connectionState.collectAsStateWithLifecycle()
 *
 * when (usbState) {
 *     Disconnected -> Icon(usb_off)
 *     Connecting   -> CircularProgressIndicator()
 *     Connected    -> Icon(usb, tint = Green)
 *     Error        -> Icon(usb, tint = Red)
 * }
 * ```
 *
 * ## Memory Leak Prevention
 *
 * | Concern                    | Guard                                          |
 * |----------------------------|-------------------------------------------------|
 * | BroadcastReceiver leak     | unregister() in onDestroy()                    |
 * | PermissionHandler receiver | Unregistered in finally block after each call   |
 * | Serial port file desc.     | closePortSafely() on disconnect + onDestroy     |
 * | Coroutine leak             | viewModelScope auto-cancels on ViewModel clear  |
 * | Activity reference leak    | All USB classes use applicationContext           |
 *
 * ## Duplicate Registration Prevention
 *
 * - UsbDeviceMonitor.register() checks `isRegistered` flag
 * - UsbPermissionHandler uses Mutex — only one dialog at a time
 * - UsbUartManager.connect() overwrites previous port cleanly
 *
 * ## Activity Recreation Safety
 *
 * - All USB objects use applicationContext (survives recreation)
 * - MainActivity uses launchMode="singleTop" (prevents duplicate instances)
 * - ViewModel survives config changes (holds USB state references)
 * - BroadcastReceiver re-registered in onCreate (safe after recreation)
 */
object UsbIntegrationStrategy
