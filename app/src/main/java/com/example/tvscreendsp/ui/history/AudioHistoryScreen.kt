package com.example.tvscreendsp.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
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
    val showRenameDialog by viewModel.showRenameDialog.collectAsStateWithLifecycle()
    val showDeleteConfirm by viewModel.showDeleteConfirm.collectAsStateWithLifecycle()
    
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                        onPlayToggle = { viewModel.togglePlayback(measurement) },
                        onEdit = { viewModel.showRenameDialog(measurement.id) },
                        onDelete = { viewModel.showDeleteConfirm(measurement.id) }
                    )
                }
            }
        }
    }
    
    // Rename Dialog
    showRenameDialog?.let { measurementId ->
        val measurement = measurements.find { it.id == measurementId }
        measurement?.let {
            RenameDialog(
                currentName = it.customName ?: "",
                onDismiss = { viewModel.hideRenameDialog() },
                onConfirm = { newName ->
                    viewModel.renameMeasurement(measurementId, newName)
                }
            )
        }
    }
    
    // Delete Confirmation Dialog
    showDeleteConfirm?.let { measurementId ->
        val measurement = measurements.find { it.id == measurementId }
        measurement?.let {
            DeleteConfirmDialog(
                onDismiss = { viewModel.hideDeleteConfirm() },
                onConfirm = {
                    viewModel.deleteMeasurement(it)
                }
            )
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
    onPlayToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
                // Display custom name or fallback to date/time
                Text(
                    text = measurement.customName ?: formatDateTime(measurement.recordedAt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Show date if custom name exists
                if (measurement.customName != null) {
                    Text(
                        text = formatDateTime(measurement.recordedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
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
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit button
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                
                // Play/Pause button
                IconButton(onClick = onPlayToggle) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Clear else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
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

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var error by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Recording") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = when {
                            it.length > 50 -> "Name too long (max 50 characters)"
                            else -> null
                        }
                    },
                    label = { Text("Custom Name") },
                    placeholder = { Text("Enter name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = error == null && name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete Recording?") },
        text = {
            Text("This will permanently delete the audio file and cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
