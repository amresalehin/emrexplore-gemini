package com.example.ui.screens

import android.content.Intent
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.brain.BrainNodeEntity
import com.example.data.model.FileItem
import com.example.data.model.MediaItem
import com.example.ui.components.formatDate
import com.example.ui.components.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMediaViewer(
    mediaList: List<MediaItem>,
    currentIndex: Int,
    windowStartIndex: Int = 0,
    totalCount: Int = mediaList.size,
    onClose: () -> Unit,
    onIndexChange: (Int) -> Unit,
    onToggleFavorite: (FileItem) -> Unit,
    onTogglePin: (MediaItem) -> Unit = {},
    isPinned: (MediaItem) -> Boolean = { false },
    onInspectMetadata: (MediaItem) -> Unit = {},
    onLoadBrainNode: suspend (MediaItem) -> BrainNodeEntity? = { null },
    onReindexWithBrain: (MediaItem) -> Unit = {},
    onAskAiAboutFile: (MediaItem) -> Unit = {},
    onEdit: (MediaItem) -> Unit = {},
    onDelete: (MediaItem) -> Unit = {}
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val localIndex = (currentIndex - windowStartIndex)
        .coerceIn(0, (mediaList.size - 1).coerceAtLeast(0))
    val currentItem = mediaList.getOrNull(localIndex)

    // A clean viewer is the default. Tap the media once to reveal transient controls.
    var showControls by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSummarySheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var currentItemPinned by remember(currentItem?.path) { mutableStateOf(currentItem?.let(isPinned) == true) }
    var brainNode by remember { mutableStateOf<BrainNodeEntity?>(null) }
    var isLoadingBrainNode by remember { mutableStateOf(false) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Don't do Brain work while the user is merely browsing media.
    LaunchedEffect(currentItem?.path) {
        brainNode = null
        isLoadingBrainNode = false
        currentItemPinned = currentItem?.let(isPinned) == true
    }

    LaunchedEffect(showSummarySheet, currentItem?.path) {
        if (!showSummarySheet || currentItem == null) return@LaunchedEffect
        isLoadingBrainNode = true
        brainNode = try {
            onLoadBrainNode(currentItem)
        } catch (_: Exception) {
            null
        }
        isLoadingBrainNode = false
    }

    LaunchedEffect(currentIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        rotationDegrees = 0f
    }

    if (currentItem == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close viewer",
                    tint = Color.White
                )
            }
        }
        return
    }

    fun toggleFavorite() {
        onToggleFavorite(
            FileItem(
                name = currentItem.name,
                path = currentItem.path,
                size = currentItem.size,
                lastModified = currentItem.dateAdded,
                isDirectory = false,
                mimeType = currentItem.mimeType,
                isFavorite = currentItem.isFavorite
            )
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentItem.path, currentIndex) {
                    var horizontalSwipe = 0f
                    var verticalSwipe = 0f
                    var swipeTriggered = false

                    // Preserve pinch-to-zoom while giving single-finger horizontal swipes
                    // the expected gallery navigation behavior.
                    detectTransformGestures { _, pan, zoom, _ ->
                        val previousScale = scale
                        scale = (scale * zoom).coerceIn(1f, 5f)

                        val atBaseScale = previousScale <= 1.05f && zoom in 0.995f..1.005f
                        if (atBaseScale && !swipeTriggered) {
                            horizontalSwipe += pan.x
                            verticalSwipe += pan.y

                            if (
                                kotlin.math.abs(horizontalSwipe) > 110f &&
                                kotlin.math.abs(horizontalSwipe) > kotlin.math.abs(verticalSwipe) * 1.15f
                            ) {
                                swipeTriggered = true
                                when {
                                    horizontalSwipe < 0f && currentIndex < totalCount - 1 ->
                                        onIndexChange(currentIndex + 1)
                                    horizontalSwipe > 0f && currentIndex > 0 ->
                                        onIndexChange(currentIndex - 1)
                                }
                                horizontalSwipe = 0f
                                verticalSwipe = 0f
                            }
                        } else if (!atBaseScale) {
                            horizontalSwipe = 0f
                            verticalSwipe = 0f
                        }

                        if (scale > 1f) {
                            val maxOffsetX = (size.width * (scale - 1f)) / 2f
                            val maxOffsetY = (size.height * (scale - 1f)) / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .clickable { showControls = !showControls },
            contentAlignment = Alignment.Center
        ) {
            if (currentItem.isVideo) {
                var videoError by remember(currentItem.uri) { mutableStateOf<String?>(null) }

                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            tag = currentItem.uri.toString()
                            setVideoURI(currentItem.uri)
                            setMediaController(MediaController(context))
                            setOnPreparedListener { player ->
                                player.isLooping = false
                                videoError = null
                                start()
                            }
                            setOnErrorListener { _, _, _ ->
                                videoError = "Unable to play this video."
                                true
                            }
                        }
                    },
                    update = { videoView ->
                        if (videoView.tag != currentItem.uri.toString()) {
                            videoView.tag = currentItem.uri.toString()
                            videoError = null
                            videoView.setVideoURI(currentItem.uri)
                            videoView.start()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                videoError?.let { message ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = message,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            } else {
                AsyncImage(
                    model = currentItem.uri,
                    contentDescription = currentItem.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                            rotationZ = rotationDegrees
                        }
                )
            }
        }

        if (showControls) {
            Surface(
                color = Color.Black.copy(alpha = 0.68f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = currentItem.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${currentIndex + 1} of $totalCount",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    IconButton(
                        onClick = { onAskAiAboutFile(currentItem) },
                        enabled = currentItem.path.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Ask AI about this file",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = ::toggleFavorite) {
                        Icon(
                            imageVector = if (currentItem.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (currentItem.isFavorite) "Remove favorite" else "Favorite",
                            tint = if (currentItem.isFavorite) Color(0xFFFBBF24) else Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            onTogglePin(currentItem)
                            currentItemPinned = !currentItemPinned
                        }
                    ) {
                        Icon(
                            imageVector = if (currentItemPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (currentItemPinned) "Remove from Pinboard" else "Pin to Pinboard",
                            tint = if (currentItemPinned) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = currentItem.mimeType
                                putExtra(Intent.EXTRA_STREAM, currentItem.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(shareIntent, "Share media"))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }

                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Details") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showSummarySheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Full metadata") },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onInspectMetadata(currentItem)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rotate 90°") },
                                leadingIcon = { Icon(Icons.Default.RotateRight, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    rotationDegrees = (rotationDegrees + 90f) % 360f
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onEdit(currentItem)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }


        }
    }

    if (showSummarySheet) {
        ModalBottomSheet(
            onDismissRequest = { showSummarySheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Summary",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentItem.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                SummarySectionCard(
                    title = "AI analysis",
                    icon = Icons.Default.AutoAwesome,
                    accent = MaterialTheme.colorScheme.primary
                ) {
                    when {
                        isLoadingBrainNode -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Loading saved AI analysis…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        brainNode?.summary?.isNotBlank() == true -> {
                            Text(
                                text = brainNode?.summary.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            TextButton(onClick = { onReindexWithBrain(currentItem) }) {
                                Text("Re-index")
                            }
                        }

                        else -> {
                            Text(
                                text = if (currentItem.isVideo) {
                                    "No Gallery AI profile is available for this media yet."
                                } else {
                                    "Gallery AI has not analyzed this media yet."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!currentItem.isVideo) {
                                TextButton(onClick = { onReindexWithBrain(currentItem) }) {
                                    Text("Run Gallery AI")
                                }
                            }
                        }
                    }
                }

                SummarySectionCard(
                    title = "Media",
                    icon = Icons.Default.Info,
                    accent = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    MediaInfoRow("File name", currentItem.name)
                    MediaInfoRow("Size", formatFileSize(currentItem.size))
                    if (currentItem.width > 0 && currentItem.height > 0) {
                        MediaInfoRow(
                            "Resolution",
                            "${currentItem.width} × ${currentItem.height}"
                        )
                    }
                    MediaInfoRow("MIME type", currentItem.mimeType)
                    MediaInfoRow("Date", formatDate(currentItem.dateAdded))
                    if (currentItem.duration > 0) {
                        val totalSeconds = currentItem.duration / 1000
                        val minutes = totalSeconds / 60
                        val seconds = totalSeconds % 60
                        MediaInfoRow("Duration", String.format("%02d:%02d", minutes, seconds))
                    }
                    MediaInfoRow("Path", currentItem.path)
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Move to trash?") },
            text = {
                Text(
                    "“${currentItem.name}” will be moved to the app trash so it can be restored later."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(currentItem)
                    }
                ) {
                    Text("Move to trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SummarySectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun MediaInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 5.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}
