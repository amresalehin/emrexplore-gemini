package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
    onInspectMetadata: (MediaItem) -> Unit = {}
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val localIndex = (currentIndex - windowStartIndex).coerceIn(0, (mediaList.size - 1).coerceAtLeast(0))
    val currentItem = mediaList.getOrNull(localIndex) ?: return
    var showControls by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Reset zoom when index changes
    LaunchedEffect(currentIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        rotationDegrees = 0f
    }

    val filmstripState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (localIndex in mediaList.indices) {
            filmstripState.animateScrollToItem(localIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main Image / Video View with Pinch-to-zoom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentIndex) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            val maxOffsetX = (size.width * (scale - 1)) / 2
                            val maxOffsetY = (size.height * (scale - 1)) / 2
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

            // Video overlay indicator if video
            if (currentItem.isVideo) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }
            }
        }

        // Left / Right touch navigation arrows
        if (showControls) {
            if (currentIndex > 0) {
                IconButton(
                    onClick = { onIndexChange(currentIndex - 1) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Previous",
                        tint = Color.White
                    )
                }
            }

            if (currentIndex < totalCount - 1) {
                IconButton(
                    onClick = { onIndexChange(currentIndex + 1) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Next",
                        tint = Color.White
                    )
                }
            }
        }

        // Top App Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Rotate
                    IconButton(onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f }) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate",
                            tint = Color.White
                        )
                    }

                    // Favorite
                    IconButton(onClick = {
                        val fileItem = FileItem(
                            name = currentItem.name,
                            path = currentItem.path,
                            size = currentItem.size,
                            lastModified = currentItem.dateAdded,
                            isDirectory = false,
                            mimeType = currentItem.mimeType,
                            isFavorite = currentItem.isFavorite
                        )
                        onToggleFavorite(fileItem)
                    }) {
                        Icon(
                            imageVector = if (currentItem.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (currentItem.isFavorite) Color(0xFFFBBF24) else Color.White
                        )
                    }

                    // Share
                    IconButton(onClick = {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = currentItem.mimeType
                                putExtra(Intent.EXTRA_STREAM, currentItem.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }

                    // Info / EXIF Quick Sheet
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Media Info",
                            tint = Color.White
                        )
                    }

                    // Full Metadata Inspector (EXIF, IPTC, XMP)
                    IconButton(onClick = { onInspectMetadata(currentItem) }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Inspect EXIF, IPTC & XMP Metadata",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Bottom Filmstrip
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .navigationBarsPadding()
                    .padding(vertical = 10.dp)
            ) {
                LazyRow(
                    state = filmstripState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(mediaList) { idx, item ->
                        val isSelected = idx == currentIndex
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onIndexChange(windowStartIndex + idx) }
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    // Media Details / EXIF Info Bottom Sheet
    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                MediaInfoRow("File Name", currentItem.name)
                MediaInfoRow("Path", currentItem.path)
                MediaInfoRow("Size", formatFileSize(currentItem.size))
                if (currentItem.width > 0 && currentItem.height > 0) {
                    MediaInfoRow("Resolution", "${currentItem.width} x ${currentItem.height}")
                }
                MediaInfoRow("MIME Type", currentItem.mimeType)
                MediaInfoRow("Date", formatDate(currentItem.dateAdded))
                if (currentItem.duration > 0) {
                    val sec = (currentItem.duration / 1000) % 60
                    val min = (currentItem.duration / 1000) / 60
                    MediaInfoRow("Duration", String.format("%02d:%02d", min, sec))
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        showInfoSheet = false
                        onInspectMetadata(currentItem)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inspect Full EXIF, IPTC & XMP Tree")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MediaInfoRow(label: String, value: String) {
    Column {
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
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    }
}
