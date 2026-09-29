package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem

@Composable
fun ZipViewerDialog(
    item: FileItem,
    entries: List<String>,
    isExtracting: Boolean,
    onExtract: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isExtracting) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Archive Contents (${entries.size} entries):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    if (entries.isEmpty()) {
                        Text(
                            text = "No files or empty archive.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn {
                            items(entries) { entry ->
                                Text(
                                    text = entry,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExtract,
                enabled = !isExtracting
            ) {
                if (isExtracting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Extract Here")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExtracting
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
