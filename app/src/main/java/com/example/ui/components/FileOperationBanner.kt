package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConflictResolution
import com.example.data.model.FileConflict
import com.example.data.model.FileOperationProgress
import com.example.data.model.OperationStatus
import com.example.data.model.OperationType

@Composable
fun FileOperationBanner(
    progress: FileOperationProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onResolveConflict: (ConflictResolution) -> Unit,
    modifier: Modifier = Modifier
) {
    // Conflict dialog if operation paused due to conflict
    if (progress.status == OperationStatus.CONFLICT && progress.conflict != null) {
        ConflictResolutionDialog(
            conflict = progress.conflict,
            onResolve = onResolveConflict,
            onCancel = onCancel
        )
    }

    AnimatedVisibility(
        visible = progress.status != OperationStatus.IDLE,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("file_operation_banner"),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (progress.status) {
                    OperationStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                    OperationStatus.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top header row: Icon, Title, and Action Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (progress.type) {
                        OperationType.COPY -> Icons.Default.FileCopy
                        OperationType.MOVE -> Icons.Default.DriveFileMove
                        else -> Icons.Default.InsertDriveFile
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (progress.status) {
                                OperationStatus.ERROR -> Icons.Default.Error
                                OperationStatus.COMPLETED -> Icons.Default.Check
                                else -> icon
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = progress.type.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (progress.status == OperationStatus.RUNNING || progress.status == OperationStatus.PAUSED) {
                                Text(
                                    text = "${(progress.percentOverall * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Subtitle: files completed & transfer speed
                        val subtitleText = when (progress.status) {
                            OperationStatus.RUNNING -> "${progress.filesProcessed}/${progress.totalFiles} files • ${progress.formattedSpeed}"
                            OperationStatus.PAUSED -> "Paused (${progress.filesProcessed}/${progress.totalFiles} files)"
                            OperationStatus.COMPLETED -> "${progress.totalFiles} files processed successfully"
                            OperationStatus.CANCELLED -> "Cancelled at ${progress.filesProcessed}/${progress.totalFiles} files"
                            OperationStatus.ERROR -> progress.errorMessage ?: "Operation error"
                            OperationStatus.CONFLICT -> "Conflict encountered"
                            OperationStatus.IDLE -> ""
                        }

                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Action Controls: Pause / Resume / Cancel / Dismiss / Retry
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (progress.status) {
                            OperationStatus.RUNNING -> {
                                IconButton(
                                    onClick = onPause,
                                    modifier = Modifier.size(36.dp).testTag("pause_operation_button")
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = onCancel,
                                    modifier = Modifier.size(36.dp).testTag("cancel_operation_button")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(20.dp))
                                }
                            }
                            OperationStatus.PAUSED -> {
                                IconButton(
                                    onClick = onResume,
                                    modifier = Modifier.size(36.dp).testTag("resume_operation_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = onCancel,
                                    modifier = Modifier.size(36.dp).testTag("cancel_operation_button")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(20.dp))
                                }
                            }
                            OperationStatus.ERROR -> {
                                IconButton(
                                    onClick = onRetry,
                                    modifier = Modifier.size(36.dp).testTag("retry_operation_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(36.dp).testTag("dismiss_operation_button")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(20.dp))
                                }
                            }
                            OperationStatus.COMPLETED, OperationStatus.CANCELLED -> {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(36.dp).testTag("dismiss_operation_button")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(20.dp))
                                }
                            }
                            else -> {}
                        }
                    }
                }

                // Progress Bar for Active Operations
                if (progress.status == OperationStatus.RUNNING || progress.status == OperationStatus.PAUSED) {
                    LinearProgressIndicator(
                        progress = { progress.percentOverall },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    // Current file details row with ETA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (progress.currentFileName.isNotBlank()) progress.currentFileName else "Processing...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = progress.formattedEta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConflictResolutionDialog(
    conflict: FileConflict,
    onResolve: (ConflictResolution) -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("File Already Exists")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "A file named '${conflict.fileName}' already exists in the destination folder.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Existing file: ${formatFileSize(conflict.existingSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "New file: ${formatFileSize(conflict.newSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "Choose how you want to handle this conflict:",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onResolve(ConflictResolution.SKIP) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Skip")
                }
                FilledTonalButton(
                    onClick = { onResolve(ConflictResolution.KEEP_BOTH) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Keep Both")
                }
                Button(
                    onClick = { onResolve(ConflictResolution.OVERWRITE) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Replace")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel Operation")
            }
        }
    )
}
