package com.example.tvscreendsp.ui.measurement

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tvscreendsp.audio.MicrophoneRecorder
import com.example.tvscreendsp.data.local.AppDatabase
import com.example.tvscreendsp.data.repository.MeasurementRepository
import com.example.tvscreendsp.dsp.PythonDspBridge
import com.example.tvscreendsp.usb.UsbPermissionHandler
import com.example.tvscreendsp.usb.UsbUartManager

/**
 * Factory for creating [MeasurementViewModel] with its dependencies.
 *
 * This factory is responsible for:
 * 1. Instantiating USB components ([UsbUartManager], [UsbPermissionHandler])
 *    using the application context to avoid leaks.
 * 2. Exposing the [usbUartManager] instance so [MainActivity] can share it
 *    with [UsbDeviceMonitor].
 * 3. Wiring up the Repository, Database, Recorder, and DSP Bridge.
 *
 * This replaces simpler `viewModel()` calls and enables clean dependency injection
 * without using a DI framework like Hilt.
 *
 * @param application The Application instance (used for Context).
 */
class MeasurementViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    /**
     * Shared UsbUartManager instance.
     * Exposed so MainActivity can pass it to UsbDeviceMonitor.
     * Lifecycle: Scoped to this Factory (which is scoped to MainActivity).
     */
    val usbUartManager: UsbUartManager
    
    val usbPermissionHandler: UsbPermissionHandler

    init {
        // Use Application context for USB components to prevent Activity leaks
        val context = application.applicationContext
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        usbUartManager = UsbUartManager(usbManager)
        usbPermissionHandler = UsbPermissionHandler(context, usbManager)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MeasurementViewModel::class.java)) {
            // Create other dependencies on demand
            val context = application.applicationContext
            
            // Database & Repository
            val database = AppDatabase.getInstance(context)
            val repository = MeasurementRepository(database.measurementDao())
            
            // Audio Recorder
            val recorder = MicrophoneRecorder(context)
            
            // DSP Bridge (Singleton object)
            val dspBridge = PythonDspBridge

            return MeasurementViewModel(
                application = application,
                usbUartManager = usbUartManager,
                usbPermissionHandler = usbPermissionHandler,
                repository = repository,
                pythonDspBridge = dspBridge,
                microphoneRecorder = recorder
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
