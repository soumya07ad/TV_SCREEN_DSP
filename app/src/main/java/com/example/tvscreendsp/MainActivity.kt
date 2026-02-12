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
import com.example.tvscreendsp.auth.AuthManager
import com.example.tvscreendsp.dsp.PythonDspBridge
import com.example.tvscreendsp.ui.auth.AuthViewModel
import com.example.tvscreendsp.ui.auth.OtpScreen
import com.example.tvscreendsp.ui.auth.SignupScreen
import com.example.tvscreendsp.ui.history.AudioHistoryScreen
import com.example.tvscreendsp.ui.measurement.MeasurementScreen
import com.example.tvscreendsp.ui.splash.SplashScreen
import com.example.tvscreendsp.ui.theme.TVScreenDSPTheme

/**
 * Main activity for TV Screen DSP app.
 * 
 * Initializes Python bridge and sets up navigation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Python bridge (must be on main thread)
        PythonDspBridge.initialize(this)
        
        enableEdgeToEdge()
        setContent {
            TVScreenDSPTheme {
                TVScreenDSPApp()
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
fun TVScreenDSPApp() {
    val navController = rememberNavController()
    
    // Shared AuthViewModel across auth screens
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
        
        // Home (Measurement Screen) - existing
        composable(Routes.HOME) {
            MeasurementScreen(
                onNavigateToHistory = {
                    navController.navigate(Routes.HISTORY)
                }
            )
        }
        
        // Audio History - existing
        composable(Routes.HISTORY) {
            AudioHistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}