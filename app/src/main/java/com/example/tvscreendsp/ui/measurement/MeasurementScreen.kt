package com.example.tvscreendsp.ui.measurement

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tvscreendsp.audio.RecordingState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Main measurement screen with recording controls and analysis display.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MeasurementScreen(
    viewModel: MeasurementViewModel = viewModel(),
    onNavigateToHistory: () -> Unit = {}
) {
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()
    
    // Permission handling
    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TV Screen DSP") },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.History,
                            contentDescription = "Audio History"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                !audioPermission.status.isGranted -> {
                    PermissionRequestUI(
                        rationaleText = if (audioPermission.status.shouldShowRationale) {
                            "Microphone permission is required to record audio for analysis."
                        } else {
                            "This app needs microphone access to analyze TV screen audio."
                        },
                        onRequestPermission = { audioPermission.launchPermissionRequest() }
                    )
                }
                else -> {
                    MainContent(
                        recordingState = recordingState,
                        analysisState = analysisState,
                        onStartRecording = { viewModel.startRecording() },
                        onStopRecording = { viewModel.stopRecording() },
                        onReset = { viewModel.resetState() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestUI(
    rationaleText: String,
    onRequestPermission: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = "🎤",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = rationaleText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
private fun MainContent(
    recordingState: RecordingState,
    analysisState: AnalysisState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onReset: () -> Unit
) {
    when (recordingState) {
        is RecordingState.Idle -> {
            IdleUI(
                onStartRecording = onStartRecording,
                analysisState = analysisState,
                onReset = onReset
            )
        }
        is RecordingState.Recording -> {
            RecordingUI(
                progress = recordingState.progressPercent,
                onStop = onStopRecording
            )
        }
        is RecordingState.Completed -> {
            AnalyzingUI(analysisState = analysisState, onReset = onReset)
        }
        is RecordingState.Error -> {
            ErrorUI(
                message = recordingState.message,
                onRetry = onReset
            )
        }
    }
}

@Composable
private fun IdleUI(
    onStartRecording: () -> Unit,
    analysisState: AnalysisState,
    onReset: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Show previous result if exists
        if (analysisState is AnalysisState.Completed) {
            ResultCard(result = analysisState.result)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Text(
            text = "🎙️",
            style = MaterialTheme.typography.displayLarge
        )
        
        Text(
            text = "Tap to start recording",
            style = MaterialTheme.typography.titleLarge
        )
        
        Button(
            onClick = onStartRecording,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Measure Noise")
        }
    }
}

@Composable
private fun RecordingUI(
    progress: Int,
    onStop: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "🔴 Recording",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        
        Text(
            text = "${progress}%",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        
        LinearProgressIndicator(
            progress = progress / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
        
        OutlinedButton(onClick = onStop) {
            Text("Cancel")
        }
    }
}

@Composable
private fun AnalyzingUI(
    analysisState: AnalysisState,
    onReset: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        when (analysisState) {
            is AnalysisState.Analyzing -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Text(
                    text = "Analyzing audio...",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            is AnalysisState.Completed -> {
                ResultCard(result = analysisState.result)
                Button(onClick = onReset) {
                    Text("Measure Again")
                }
            }
            is AnalysisState.Error -> {
                ErrorUI(message = analysisState.message, onRetry = onReset)
            }
            else -> {
                Text("Processing...")
            }
        }
    }
}

@Composable
private fun ResultCard(result: com.example.tvscreendsp.data.model.DspResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result.noiseStatus) {
                "CRACK" -> MaterialTheme.colorScheme.errorContainer
                "NORMAL" -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status:",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${result.noiseStatus} ${getStatusEmoji(result.noiseStatus)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Confidence:")
                Text(
                    text = "${(result.confidence * 100).toInt()}%",
                    fontWeight = FontWeight.Bold
                )
            }
            
            Divider()
            
            // Frequency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Frequency:")
                Text("${String.format("%.1f", result.frequency)} Hz")
            }
            
            // Power
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Power:")
                Text("${String.format("%.1f", result.power)} dB")
            }
            
            // Surface Tension
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Surface Tension:")
                Text(String.format("%.1f", result.surfaceTension))
            }
        }
    }
}

@Composable
private fun ErrorUI(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "❌",
            style = MaterialTheme.typography.displayLarge
        )
        
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
        
        Button(onClick = onRetry) {
            Text("Try Again")
        }
    }
}

private fun getStatusEmoji(status: String): String {
    return when (status) {
        "CRACK" -> "⚠️"
        "NORMAL" -> "✅"
        "NOISE" -> "🔇"
        else -> "❓"
    }
}
