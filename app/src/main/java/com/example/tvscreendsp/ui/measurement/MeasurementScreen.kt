package com.example.tvscreendsp.ui.measurement

import android.util.Log
import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tvscreendsp.audio.RecordingState
import com.example.tvscreendsp.data.local.InputSource
import com.example.tvscreendsp.usb.UsbConnectionState
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
    val usbState by viewModel.usbConnectionState.collectAsStateWithLifecycle()
    val handshakeResult by viewModel.lastHandshakeResult.collectAsStateWithLifecycle()
    val lastInputSource by viewModel.lastInputSource.collectAsStateWithLifecycle()
    
    // Permission handling
    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TV Screen DSP") },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Default.List,
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
        ) {
            // USB status banner at the top of the screen
            UsbStatusIndicator(state = usbState)

            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                            handshakeResult = handshakeResult,
                            inputSource = lastInputSource,
                            onStartRecording = { viewModel.startRecording() },
                            onStopRecording = { viewModel.stopRecording() },
                            onReset = { viewModel.resetState() }
                        )
                    }
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
    handshakeResult: MeasurementViewModel.HandshakeResult,
    inputSource: String,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onReset: () -> Unit
) {
    when (recordingState) {
        is RecordingState.Idle -> {
            IdleUI(
                onStartRecording = onStartRecording,
                analysisState = analysisState,
                handshakeResult = handshakeResult,
                inputSource = inputSource,
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
            AnalyzingUI(
                analysisState = analysisState,
                handshakeResult = handshakeResult,
                inputSource = inputSource,
                onReset = onReset
            )
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
    handshakeResult: MeasurementViewModel.HandshakeResult,
    inputSource: String,
    onReset: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Show previous result if exists
        if (analysisState is AnalysisState.Completed) {
            TriggerStatusCard(
                triggerCompleted = handshakeResult.completed,
                triggerLatencyMs = handshakeResult.latencyMs,
                inputSource = inputSource
            )
            Spacer(modifier = Modifier.height(8.dp))
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
            progress = { progress / 100f },
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
    handshakeResult: MeasurementViewModel.HandshakeResult,
    inputSource: String,
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
                TriggerStatusCard(
                    triggerCompleted = handshakeResult.completed,
                    triggerLatencyMs = handshakeResult.latencyMs,
                    inputSource = inputSource
                )
                Spacer(modifier = Modifier.height(8.dp))
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
            
            HorizontalDivider()
            
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

// ──────────────────────────────────────────────────────────────────
// Trigger Status Card
// ──────────────────────────────────────────────────────────────────

@Composable
fun TriggerStatusCard(
    triggerCompleted: Boolean,
    triggerLatencyMs: Long?,
    inputSource: String
) {
    val (containerColor, contentColor, icon, title, subtitle) = remember(
        triggerCompleted, triggerLatencyMs, inputSource
    ) {
        when {
            triggerCompleted && triggerLatencyMs != null -> {
                Log.d("TriggerStatusCard", "USB trigger success — latency: ${triggerLatencyMs}ms")
                TriggerCardInfo(
                    containerColor = Color(0xFF1B5E20),
                    contentColor = Color(0xFFC8E6C9),
                    icon = Icons.Filled.CheckCircle,
                    title = "Hardware Trigger Successful",
                    subtitle = "Latency: ${triggerLatencyMs} ms"
                )
            }
            inputSource == InputSource.USB -> {
                Log.d("TriggerStatusCard", "USB trigger failed — no DONE received")
                TriggerCardInfo(
                    containerColor = Color(0xFFB71C1C),
                    contentColor = Color(0xFFFFCDD2),
                    icon = Icons.Filled.Warning,
                    title = "Hardware Trigger Failed",
                    subtitle = "No DONE response received"
                )
            }
            else -> {
                Log.d("TriggerStatusCard", "Manual mode — no hardware trigger")
                TriggerCardInfo(
                    containerColor = Color(0xFF424242),
                    contentColor = Color(0xFFBDBDBD),
                    icon = Icons.Filled.Info,
                    title = "Manual Mode",
                    subtitle = "No hardware trigger used"
                )
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private data class TriggerCardInfo(
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

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

// ──────────────────────────────────────────────────────────────────
// USB Status Indicator
// ──────────────────────────────────────────────────────────────────

/** Container color for the connected state (Material 3 green tone). */
private val ConnectedContainerColor = Color(0xFF1B5E20)
/** Content color for the connected state. */
private val ConnectedContentColor = Color(0xFFC8E6C9)

/** Container color for the connecting state (Material 3 amber tone). */
private val ConnectingContainerColor = Color(0xFFF57F17)
/** Content color for the connecting state. */
private val ConnectingContentColor = Color(0xFFFFF8E1)

/** Container color for the disconnected state. */
private val DisconnectedContainerColor = Color(0xFF424242)
/** Content color for the disconnected state. */
private val DisconnectedContentColor = Color(0xFFBDBDBD)

/** Container color for error/permission-denied states. */
private val ErrorContainerColor = Color(0xFFB71C1C)
/** Content color for error/permission-denied states. */
private val ErrorContentColor = Color(0xFFFFCDD2)

/**
 * Displays a full-width status banner showing the current USB connection state.
 *
 * Color mapping:
 * - **Connected**: Green banner with device name
 * - **Connecting**: Yellow/amber banner
 * - **Disconnected**: Gray banner
 * - **PermissionDenied / Error**: Red banner with message
 *
 * Uses [AnimatedVisibility] for smooth expand/collapse transitions.
 */
@Composable
fun UsbStatusIndicator(state: UsbConnectionState) {
    val (containerColor, contentColor, icon, text) = remember(state) {
        when (state) {
            is UsbConnectionState.Connected -> StatusInfo(
                containerColor = ConnectedContainerColor,
                contentColor = ConnectedContentColor,
                icon = "🔌",
                text = "USB Device Connected: ${state.deviceName}"
            )
            is UsbConnectionState.Connecting -> StatusInfo(
                containerColor = ConnectingContainerColor,
                contentColor = ConnectingContentColor,
                icon = "⏳",
                text = "Connecting..."
            )
            is UsbConnectionState.Disconnected -> StatusInfo(
                containerColor = DisconnectedContainerColor,
                contentColor = DisconnectedContentColor,
                icon = "⚡",
                text = "USB Disconnected"
            )
            is UsbConnectionState.PermissionDenied -> StatusInfo(
                containerColor = ErrorContainerColor,
                contentColor = ErrorContentColor,
                icon = "🚫",
                text = "USB Permission Denied"
            )
            is UsbConnectionState.Error -> StatusInfo(
                containerColor = ErrorContainerColor,
                contentColor = ErrorContentColor,
                icon = "❌",
                text = "Error: ${state.message}"
            )
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = containerColor,
            contentColor = contentColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Data holder for USB status indicator properties.
 */
private data class StatusInfo(
    val containerColor: Color,
    val contentColor: Color,
    val icon: String,
    val text: String
)
