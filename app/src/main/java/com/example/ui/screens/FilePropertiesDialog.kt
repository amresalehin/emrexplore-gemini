package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FileItem
import com.example.ui.components.formatDate
import com.example.ui.components.formatFileSize

@Composable
fun FilePropertiesDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onInspectMetadata: ((FileItem) -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Properties",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PropertyRow("Name", item.name)
                PropertyRow("Type", if (item.isDirectory) "Directory / Folder" else item.mimeType.ifEmpty { "File" })
                PropertyRow("Location", item.path)
                if (!item.isDirectory) {
                    PropertyRow("Size", "${formatFileSize(item.size)} (${item.size} bytes)")
                } else {
                    PropertyRow("Items", "${item.childCount} files & folders")
                }
                PropertyRow("Modified", formatDate(item.lastModified))

                if (item.isImage && onInspectMetadata != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onInspectMetadata(item)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("Inspect EXIF, IPTC & XMP")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}
