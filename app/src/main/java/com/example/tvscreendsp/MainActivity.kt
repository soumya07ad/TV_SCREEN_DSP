package com.example.tvscreendsp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.tvscreendsp.dsp.PythonDspBridge
import com.example.tvscreendsp.ui.auth.AuthViewModel
import com.example.tvscreendsp.ui.auth.OtpScreen
import com.example.tvscreendsp.ui.auth.SignupScreen
import com.example.tvscreendsp.ui.history.AudioHistoryScreen
import com.example.tvscreendsp.ui.measurement.MeasurementScreen
import com.example.tvscreendsp.ui.measurement.MeasurementViewModel
import com.example.tvscreendsp.ui.measurement.MeasurementViewModelFactory
import com.example.tvscreendsp.ui.splash.SplashScreen
import com.example.tvscreendsp.ui.theme.TVScreenDSPTheme
import com.example.tvscreendsp.usb.UsbDeviceMonitor
import kotlinx.coroutines.runBlocking

/**
 * Main activity for TV Screen DSP app.
 * 
 * Responsible for:
 * 1. Initializing core components (Python, USB)
 * 2. Managing USB lifecycle (monitor registration/unregistration)
 * 3. Wiring up dependencies via [MeasurementViewModelFactory]
 * 4. Hosting the navigation graph
 */
class MainActivity : ComponentActivity() {

    // Helper for creating MeasurementViewModel with USB dependencies
    private lateinit var measurementViewModelFactory: MeasurementViewModelFactory
    
    // Monitors USB attach/detach events
    private lateinit var usbDeviceMonitor: UsbDeviceMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Python bridge (must be on main thread)
        PythonDspBridge.initialize(this)
        
        // Initialize Factory with Application context
        measurementViewModelFactory = MeasurementViewModelFactory(application)
        
        // Initialize USB Monitor with the manager and permission handler from the factory
        usbDeviceMonitor = UsbDeviceMonitor(
            context = applicationContext,
            usbUartManager = measurementViewModelFactory.usbUartManager,
            usbPermissionHandler = measurementViewModelFactory.usbPermissionHandler
        )
        
        // Register for USB events — auto-connect is handled by the monitor
        usbDeviceMonitor.register()
        
        enableEdgeToEdge()
        setContent {
            TVScreenDSPTheme {
                TVScreenDSPApp(measurementViewModelFactory)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Prevent receiver leaks
        if (::usbDeviceMonitor.isInitialized) {
            usbDeviceMonitor.unregister()
        }
        
        // Clean up serial port connection
        if (::measurementViewModelFactory.isInitialized) {
            runBlocking {
                measurementViewModelFactory.usbUartManager.disconnect()
            }
        }
    }
}

/**
 * Navigation routes for the app.
 */
object Routes {
    const val SPLASH = "splash"
    const val SIGNUP = "signup"
    const val OTP = "otp"
    const val HOME = "home"
    const val HISTORY = "history"
}

@Composable
fun TVScreenDSPApp(
    measurementFactory: MeasurementViewModelFactory
) {
    val navController = rememberNavController()
    
    // Shared AuthViewModel across auth screens (default factory is fine here)
    val authViewModel: AuthViewModel = viewModel()
    
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        // Splash Screen
        composable(Routes.SPLASH) {
            val isAuthenticated = remember {
                authViewModel.isAuthenticated()
            }
            
            SplashScreen(
                isAuthenticated = isAuthenticated,
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToSignup = {
                    navController.navigate(Routes.SIGNUP) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        
        // Signup Screen
        composable(Routes.SIGNUP) {
            SignupScreen(
                viewModel = authViewModel,
                onNavigateToOtp = {
                    navController.navigate(Routes.OTP)
                }
            )
        }
        
        // OTP Verification Screen
        composable(Routes.OTP) {
            OtpScreen(
                viewModel = authViewModel,
                onVerificationSuccess = {
                    navController.navigate(Routes.HOME) {
                        // Clear entire auth flow from backstack
                        popUpTo(Routes.SIGNUP) { inclusive = true }
                    }
                }
            )
        }
        
        // Home (Measurement Screen)
        composable(Routes.HOME) {
            // Use the factory to create/retrieve the ViewModel with USB dependencies
            val measurementViewModel: MeasurementViewModel = viewModel(
                factory = measurementFactory
            )
            
            MeasurementScreen(
                viewModel = measurementViewModel,
                onNavigateToHistory = {
                    navController.navigate(Routes.HISTORY)
                }
            )
        }
        
        // Audio History
        composable(Routes.HISTORY) {
            AudioHistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}