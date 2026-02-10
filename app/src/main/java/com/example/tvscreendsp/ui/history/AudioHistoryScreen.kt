package com.example.tvscreendsp.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tvscreendsp.data.local.MeasurementEntity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Audio History Screen - displays list of recorded measurements with playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioHistoryScreen(
    viewModel: AudioHistoryViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val measurements by viewModel.measurements.collectAsStateWithLifecycle()
    val playingId by viewModel.playingId.collectAsStateWithLifecycle()
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPlayback()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (measurements.isEmpty()) {
            EmptyState(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(measurements, key = { it.id }) { measurement ->
                    MeasurementListItem(
                        measurement = measurement,
                        isPlaying = playingId == measurement.id,
                        onPlayToggle = { viewModel.togglePlayback(measurement) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📭",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "No recordings yet",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Record audio to see history",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MeasurementListItem(
    measurement: MeasurementEntity,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (measurement.noiseStatus) {
                "CRACK" -> MaterialTheme.colorScheme.errorContainer
                "NORMAL" -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Date/Time
                Text(
                    text = formatDateTime(measurement.recordedAt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Source
                Text(
                    text = "Source: ${measurement.inputSource}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Status (if analyzed)
                measurement.noiseStatus?.let { status ->
                    Text(
                        text = "Status: $status ${getStatusEmoji(status)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Analysis details (if available)
                if (measurement.isAnalyzed()) {
                    Text(
                        text = "${String.format("%.0f", measurement.frequency ?: 0.0)} Hz • " +
                              "${String.format("%.1f", measurement.power ?: 0.0)} dB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Play/Stop button
            IconButton(onClick = onPlayToggle) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getStatusEmoji(status: String): String {
    return when (status) {
        "CRACK" -> "⚠️"
        "NORMAL" -> "✅"
        "NOISE" -> "🔇"
        else -> "❓"
    }
}
