package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.FileItem
import com.example.ui.theme.ColorApks
import com.example.ui.theme.ColorArchives
import com.example.ui.theme.ColorAudio
import com.example.ui.theme.ColorDocuments
import com.example.ui.theme.ColorFolders
import com.example.ui.theme.ColorImages
import com.example.ui.theme.ColorVideos
import com.example.ui.viewmodel.ClipboardAction
import com.example.ui.viewmodel.ClipboardState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroups.coerceIn(0, units.size - 1)])
}

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val sdf = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun BreadcrumbsRow(
    currentPath: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Break current path into readable steps
    val parts = currentPath.split("/").filter { it.isNotEmpty() }
    val steps = mutableListOf<Pair<String, String>>()

    var accumulated = ""
    for (part in parts) {
        accumulated += "/$part"
        val label = when (part) {
            "emulated" -> null
            "0" -> "Internal Storage"
            "storage" -> null
            else -> part
        }
        if (label != null) {
            steps.add(label to accumulated)
        }
    }

    if (steps.isEmpty()) {
        steps.add("Home" to currentPath)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (name, path) ->
            if (index > 0) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(12.dp)
                        .padding(horizontal = 2.dp)
                )
            }

            val isLast = index == steps.lastIndex
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isLast) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.clickable { onNavigate(path) }
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    ),
                    color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun PasteActionBar(
    clipboard: ClipboardState,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (clipboard.action == ClipboardAction.COPY) "Copy items" else "Move items",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${clipboard.sourcePaths.size} item(s) selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onPaste,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Paste Here")
                }
            }
        }
    }
}

@Composable
fun AudioMiniPlayer(
    activeAudio: FileItem?,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = activeAudio != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        if (activeAudio == null) return@AnimatedVisibility

        Card(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ColorAudio.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = ColorAudio,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeAudio.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatFileSize(activeAudio.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close player",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileTypeIconBadge(
    item: FileItem,
    modifier: Modifier = Modifier
) {
    if (item.isImage && item.uri != null) {
        val context = LocalContext.current
        val imageRequest = remember(item.uri) {
            ImageRequest.Builder(context)
                .data(item.uri)
                .size(128, 128)
                .crossfade(false)
                .allowHardware(true)
                .build()
        }
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ColorImages.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        val (icon, tint) = when {
            item.isDirectory -> Icons.Default.Folder to ColorFolders
            item.isImage -> Icons.Default.Image to ColorImages
            item.isVideo -> Icons.Default.Movie to ColorVideos
            item.isAudio -> Icons.Default.AudioFile to ColorAudio
            item.isArchive -> Icons.Default.Archive to ColorArchives
            item.isApk -> Icons.Default.VideogameAsset to ColorApks
            item.isDocument -> Icons.Default.Description to ColorDocuments
            else -> Icons.Default.Description to MaterialTheme.colorScheme.primary
        }

        Box(
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
