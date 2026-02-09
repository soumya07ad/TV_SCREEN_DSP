package com.example.tvscreendsp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tvscreendsp.dsp.PythonDspBridge
import com.example.tvscreendsp.ui.measurement.MeasurementScreen
import com.example.tvscreendsp.ui.measurement.MeasurementViewModel
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
                MeasurementScreen()
            }
        }
    }
}