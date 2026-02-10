package com.example.tvscreendsp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.tvscreendsp.dsp.PythonDspBridge
import com.example.tvscreendsp.ui.theme.TVScreenDSPTheme

/**
 * Main activity for TV Screen DSP app.
 * 
 * Initializes Python bridge and displays the measurement screen.
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

@Composable
fun TVScreenDSPApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Measurement) }
    
    when (currentScreen) {
        Screen.Measurement -> {
            com.example.tvscreendsp.ui.measurement.MeasurementScreen(
                onNavigateToHistory = { currentScreen = Screen.History }
            )
        }
        Screen.History -> {
            com.example.tvscreendsp.ui.history.AudioHistoryScreen(
                onNavigateBack = { currentScreen = Screen.Measurement }
            )
        }
    }
}

sealed class Screen {
    object Measurement : Screen()
    object History : Screen()
}