package com.example.ui.screens
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import coil.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaAlbum
import com.example.data.model.MediaItem
import com.example.data.media.MediaAlbumRepository
import com.example.data.media.MediaRepository
import com.example.data.media.FullscreenMediaSource
import com.example.ui.viewmodel.GallerySubTab
import com.example.ui.viewmodel.GallerySortOption
import com.example.ui.viewmodel.GalleryDateFilter
import com.example.ui.viewmodel.GalleryLocationFilter
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UnifiedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    uiState: UiState,
    viewModel: UnifiedViewModel,
    onRequestMediaLocationPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val selectedAlbumId = uiState.selectedAlbum?.id
    val galleryGridState = rememberLazyGridState()
    var chromeVisible by remember { mutableStateOf(true) }
    var searchDropdownVisible by remember { mutableStateOf(false) }
    var filterMenuVisible by remember { mutableStateOf(false) }
    var sortMenuVisible by remember { mutableStateOf(false) }
    var groupMenuVisible by remember { mutableStateOf(false) }
    var groupBy by remember { mutableStateOf("Month") }

    val groupedPagingFlow: Flow<PagingData<GalleryGridItem>> = remember(groupBy) {
        viewModel.galleryPagingFlow.map { pagingData: PagingData<MediaItem> ->
            val mediaData: PagingData<GalleryGridItem> = pagingData.map { media -> GalleryGridItem.Media(media) }
            if (groupBy == "None") {
                mediaData
            } else {
                mediaData.insertSeparators { before, after ->
                    val current = after as? GalleryGridItem.Media ?: return@insertSeparators null
                    val currentKey = galleryGroupKey(current.item.dateAdded, groupBy)
                    val previousKey = (before as? GalleryGridItem.Media)?.let {
                        galleryGroupKey(it.item.dateAdded, groupBy)
                    }
                    if (currentKey != previousKey) GalleryGridItem.Header(currentKey) else null
                }
            }
        }
    }
    val pagedMedia = groupedPagingFlow.collectAsLazyPagingItems()

    LaunchedEffect(galleryGridState) {
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow {
            galleryGridState.firstVisibleItemIndex to galleryGridState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (index == 0 && offset < 12) {
                chromeVisible = true
            } else if (index != lastIndex || abs(offset - lastOffset) > 8) {
                val scrollingUp = index < lastIndex || (index == lastIndex && offset < lastOffset)
                chromeVisible = scrollingUp
            }
            lastIndex = index
            lastOffset = offset
        }
    }

    LaunchedEffect(uiState.gallerySearchActive, uiState.gallerySearchSubmittedQuery) {
        val query = uiState.gallerySearchSubmittedQuery.lowercase(Locale.US)
        if (listOf("gps:", "near:", "location:").any { query.contains(it) }) {
            onRequestMediaLocationPermission()
        }
    }
    var discoveredAlbums by remember { mutableStateOf(uiState.mediaAlbums) }

    LaunchedEffect(uiState.gallerySubTab) {
        if (uiState.gallerySubTab == GallerySubTab.ALBUMS && discoveredAlbums.isEmpty()) {
            discoveredAlbums = MediaAlbumRepository(context).getAlbums()
        }
    }

    val albumPagedMedia = if (selectedAlbumId != null) {
        val albumFlow: Flow<PagingData<GalleryGridItem>> = remember(selectedAlbumId) {
            MediaRepository(context).albumPager(selectedAlbumId).map { pagingData: PagingData<MediaItem> ->
                pagingData.map { media -> GalleryGridItem.Media(media) as GalleryGridItem }
            }
        }
        albumFlow.collectAsLazyPagingItems()
    } else null

    // If inside an album, handle back button
    BackHandler(enabled = uiState.selectedAlbum != null) {
        viewModel.selectAlbum(null)
    }

    Column(modifier = modifier.fillMaxSize()) {

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (uiState.selectedAlbum != null) {
                        // Album detail header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.selectAlbum(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Albums")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.selectedAlbum.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${uiState.selectedAlbum.itemCount} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                val nextCols = if (uiState.galleryColumns >= 4) 2 else uiState.galleryColumns + 1
                                viewModel.setGalleryColumns(nextCols)
                            }) {
                                Icon(Icons.Default.GridView, contentDescription = "${uiState.galleryColumns} columns")
                            }
                        }
                    } else {
                        // Main gallery header
                        if (uiState.gallerySearchActive) {
                            // Expanded Rich Search Bar Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(onClick = {
                                    viewModel.setGallerySearchActive(false)
                                    keyboardController?.hide()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                                }
                                TextField(
                                    value = uiState.gallerySearchQuery,
                                    onValueChange = { viewModel.setGallerySearchQuery(it) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp),
                                    placeholder = {
                                        Text(
                                            if (uiState.gallerySubTab == GallerySubTab.ALBUMS) "Search albums..." else "Search photos, camera, date, GPS, tags...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (uiState.gallerySearchQuery.isNotBlank()) {
                                            IconButton(onClick = { viewModel.clearGallerySearch() }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Clear search",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            viewModel.submitGallerySearch()
                                            keyboardController?.hide()
                                        }
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            }
                        } else {
                            // Standard polished Gallery Top Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Modern Segmented Pill Switcher (Photos / Albums)
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val isTimeline = uiState.gallerySubTab == GallerySubTab.TIMELINE
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (isTimeline) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable { viewModel.setGallerySubTab(GallerySubTab.TIMELINE) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.PhotoLibrary,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isTimeline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "Photos",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isTimeline) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isTimeline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (!isTimeline) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable { viewModel.setGallerySubTab(GallerySubTab.ALBUMS) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (!isTimeline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "Albums",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (!isTimeline) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (!isTimeline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Search button
                                val hasSearchQuery = uiState.gallerySearchQuery.isNotBlank()
                                IconButton(onClick = { viewModel.setGallerySearchActive(true) }) {
                                    BadgedBox(
                                        badge = {
                                            if (hasSearchQuery) {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(8.dp)
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Search gallery",
                                            tint = if (hasSearchQuery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Grid Columns Toggle
                                IconButton(onClick = {
                                    val nextCols = if (uiState.galleryColumns >= 4) 2 else uiState.galleryColumns + 1
                                    viewModel.setGalleryColumns(nextCols)
                                }) {
                                    Icon(Icons.Default.GridView, contentDescription = "${uiState.galleryColumns} columns")
                                }

                                // Sort Menu
                                Box {
                                    IconButton(onClick = { sortMenuVisible = true }) {
                                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                                    }
                                    DropdownMenu(
                                        expanded = sortMenuVisible,
                                        onDismissRequest = { sortMenuVisible = false }
                                    ) {
                                        GallerySortOption.values().forEach { option ->
                                            val isSelected = uiState.gallerySortOption == option
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        option.title,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                leadingIcon = {
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setGallerySortOption(option)
                                                    sortMenuVisible = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Group Menu (Photos timeline only)
                                if (uiState.gallerySubTab == GallerySubTab.TIMELINE) {
                                    Box {
                                        IconButton(onClick = { groupMenuVisible = true }) {
                                            Icon(Icons.Default.ViewColumn, contentDescription = "Group by")
                                        }
                                        DropdownMenu(
                                            expanded = groupMenuVisible,
                                            onDismissRequest = { groupMenuVisible = false }
                                        ) {
                                            listOf("Year", "Month", "Day", "None").forEach { option ->
                                                val isSelected = groupBy == option
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            "Group by: $option",
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        if (isSelected) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                        }
                                                    },
                                                    onClick = {
                                                        groupBy = option
                                                        groupMenuVisible = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Rich Search Suggestions & Tokens Panel (when search is open)
                        AnimatedVisibility(
                            visible = uiState.gallerySearchActive,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            RichGallerySearchPanel(
                                uiState = uiState,
                                viewModel = viewModel,
                                onSearchSubmitted = {
                                    viewModel.submitGallerySearch()
                                    keyboardController?.hide()
                                },
                                onDismiss = {
                                    viewModel.setGallerySearchActive(false)
                                    keyboardController?.hide()
                                }
                            )
                        }

                        // Dedicated, Decoupled Filter Bar (when on Timeline tab)
                        if (uiState.gallerySubTab == GallerySubTab.TIMELINE) {
                            GalleryDedicatedFilterBar(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }

                        // Active Search Results Banner (when search query is present & search panel is closed)
                        if (uiState.gallerySearchQuery.isNotBlank() && !uiState.gallerySearchActive) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Search results: \"${uiState.gallerySearchQuery}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = { viewModel.clearGallerySearch() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isGalleryAiProcessing || uiState.isGalleryAiPaused) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                LinearProgressIndicator(
                    progress = { uiState.galleryAiProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        uiState.galleryAiStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (uiState.isGalleryAiPaused) {
                                viewModel.resumeGalleryAi()
                            } else {
                                viewModel.pauseGalleryAi()
                            }
                        }
                    ) {
                        Text(if (uiState.isGalleryAiPaused) "Resume" else "Pause")
                    }
                }
            }
        }

        // Aves-style contextual selection bar. Selection is limited to explicitly selected items.
        if (uiState.gallerySelection.isNotEmpty()) {
            GallerySelectionBar(
                count = uiState.gallerySelection.size,
                onClear = { viewModel.clearGallerySelection() },
                onFavorite = { viewModel.favoriteGallerySelection() },
                onAiProcess = { viewModel.processGalleryAiSelection() },
                onShare = {
                    val uris = ArrayList(uiState.gallerySelection.map { it.uri })
                    try {
                        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share ${uiState.gallerySelection.size} items"))
                    } catch (_: ActivityNotFoundException) {
                        viewModel.showMessage("No app available to share these items")
                    }
                },
                onDelete = { viewModel.deleteGallerySelection() }
            )
        }

        // Body
        if (uiState.isLoadingMedia) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.selectedAlbum != null) {
            // Display media inside selected album
            val albumItems = albumPagedMedia
            if (albumItems == null) {
                EmptyGalleryMessage("No album selected")
            } else {
                PagedMediaGrid(
                    items = albumItems,
                    gridState = galleryGridState,
                    columns = uiState.galleryColumns,
                    onItemClick = { item ->
                        if (uiState.gallerySelection.isNotEmpty()) viewModel.toggleGallerySelection(item)
                        else {
                            val loaded = albumItems.itemSnapshotList.items.filterIsInstance<GalleryGridItem.Media>().map { it.item }
                            viewModel.openFullscreenMedia(item, loaded, FullscreenMediaSource.ALBUM, selectedAlbumId)
                        }
                    },
                    onItemLongClick = { item -> viewModel.toggleGallerySelection(item) },
                    selectedPaths = uiState.gallerySelection.map { it.path }.toSet()
                )
            }
        } else {
            when (uiState.gallerySubTab) {
                GallerySubTab.TIMELINE -> {
                    PagedMediaGrid(
                        items = pagedMedia,
                        gridState = galleryGridState,
                        columns = uiState.galleryColumns,
                        onItemClick = { item ->
                            if (uiState.gallerySelection.isNotEmpty()) viewModel.toggleGallerySelection(item)
                            else {
                            val loaded = pagedMedia.itemSnapshotList.items.filterIsInstance<GalleryGridItem.Media>().map { it.item }
                            val isSearchOrFiltered = uiState.gallerySearchQuery.isNotBlank() ||
                                uiState.galleryDateFilter != GalleryDateFilter.ALL ||
                                uiState.galleryLocationFilter != GalleryLocationFilter.ALL
                            viewModel.openFullscreenMedia(
                                item,
                                loaded,
                                if (isSearchOrFiltered) {
                                    FullscreenMediaSource.SEARCH
                                } else {
                                    when (uiState.galleryFilter) {
                                        "PHOTOS" -> FullscreenMediaSource.PHOTOS
                                        "VIDEOS" -> FullscreenMediaSource.VIDEOS
                                        "FAVORITES" -> FullscreenMediaSource.FAVORITES
                                        else -> FullscreenMediaSource.ALL
                                    }
                                }
                            )
                            }
                        },
                        onItemLongClick = { item -> viewModel.toggleGallerySelection(item) },
                        selectedPaths = uiState.gallerySelection.map { it.path }.toSet()
                    )
                }
                GallerySubTab.ALBUMS -> {
                    if (discoveredAlbums.isEmpty()) {
                        EmptyGalleryMessage("No albums detected")
                    } else {
                        AlbumsGrid(
                            albums = discoveredAlbums,
                            onAlbumClick = { album -> viewModel.selectAlbum(album) }
                        )
                    }
                }
            }
        }
    }
}


private sealed interface GalleryGridItem {
    data class Media(val item: MediaItem) : GalleryGridItem
    data class Header(val title: String) : GalleryGridItem
}

private fun galleryGroupKey(timestamp: Long, groupBy: String): String {
    val pattern = when (groupBy) {
        "Year" -> "yyyy"
        "Month" -> "MMMM yyyy"
        "Day" -> "dd MMMM yyyy"
        else -> ""
    }
    if (pattern.isBlank()) return ""
    return java.text.SimpleDateFormat(pattern, Locale.US).format(java.util.Date(timestamp))
}

@Composable
private fun PagedMediaGrid(
    items: androidx.paging.compose.LazyPagingItems<GalleryGridItem>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    columns: Int,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit = {},
    selectedPaths: Set<String> = emptySet()
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            count = items.itemCount,
            key = { index ->
                when (val model = items.peek(index)) {
                    is GalleryGridItem.Header -> "header:" + model.title + ":" + index
                    is GalleryGridItem.Media -> "media:" + model.item.uri
                    null -> "placeholder:" + index
                }
            },
            span = { index ->
                if (items.peek(index) is GalleryGridItem.Header) {
                    androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan)
                } else {
                    androidx.compose.foundation.lazy.grid.GridItemSpan(1)
                }
            },
            contentType = { index ->
                when (items.peek(index)) {
                    is GalleryGridItem.Header -> "header"
                    is GalleryGridItem.Media -> "media"
                    null -> "placeholder"
                }
            }
        ) { index ->
            when (val model = items[index]) {
                is GalleryGridItem.Header -> {
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 10.dp)
                    )
                }
                is GalleryGridItem.Media -> {
                    val item = model.item
                    MediaGridThumbnail(
                        item = item,
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) },
                        selected = item.path in selectedPaths
                    )
                }
                null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }
            }
        }

        if (items.loadState.append is LoadState.Loading) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (items.itemCount == 0 && items.loadState.refresh is LoadState.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }

    if (items.itemCount == 0 && items.loadState.refresh is LoadState.Error) {
        EmptyGalleryMessage("Could not load media. Pull to refresh.")
    }
}

@Composable
private fun GalleryDedicatedFilterBar(
    uiState: UiState,
    viewModel: UnifiedViewModel
) {
    var dateMenuVisible by remember { mutableStateOf(false) }
    var locationMenuVisible by remember { mutableStateOf(false) }
    val hasActiveFilters = uiState.galleryFilter != "ALL" ||
        uiState.galleryDateFilter != GalleryDateFilter.ALL ||
        uiState.galleryLocationFilter != GalleryLocationFilter.ALL

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Media Type Chips
        FilterChip(
            selected = uiState.galleryFilter == "ALL",
            onClick = { viewModel.setGalleryFilter("ALL") },
            label = { Text("All") }
        )
        FilterChip(
            selected = uiState.galleryFilter == "PHOTOS",
            onClick = { viewModel.setGalleryFilter(if (uiState.galleryFilter == "PHOTOS") "ALL" else "PHOTOS") },
            label = { Text("Photos") },
            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        FilterChip(
            selected = uiState.galleryFilter == "VIDEOS",
            onClick = { viewModel.setGalleryFilter(if (uiState.galleryFilter == "VIDEOS") "ALL" else "VIDEOS") },
            label = { Text("Videos") },
            leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        FilterChip(
            selected = uiState.galleryFilter == "FAVORITES",
            onClick = { viewModel.setGalleryFilter(if (uiState.galleryFilter == "FAVORITES") "ALL" else "FAVORITES") },
            label = { Text("Favorites") },
            leadingIcon = {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (uiState.galleryFilter == "FAVORITES") Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        VerticalDivider(
            modifier = Modifier
                .height(20.dp)
                .padding(horizontal = 2.dp)
        )

        // Date Filter Dropdown
        Box {
            FilterChip(
                selected = uiState.galleryDateFilter != GalleryDateFilter.ALL,
                onClick = { dateMenuVisible = true },
                label = { Text(uiState.galleryDateFilter.label) },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenu(
                expanded = dateMenuVisible,
                onDismissRequest = { dateMenuVisible = false }
            ) {
                GalleryDateFilter.values().forEach { option ->
                    val isSelected = uiState.galleryDateFilter == option
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = {
                            viewModel.setGalleryDateFilter(option)
                            dateMenuVisible = false
                        }
                    )
                }
            }
        }

        // Location Filter Dropdown
        Box {
            FilterChip(
                selected = uiState.galleryLocationFilter != GalleryLocationFilter.ALL,
                onClick = { locationMenuVisible = true },
                label = { Text(uiState.galleryLocationFilter.label) },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenu(
                expanded = locationMenuVisible,
                onDismissRequest = { locationMenuVisible = false }
            ) {
                GalleryLocationFilter.values().forEach { option ->
                    val isSelected = uiState.galleryLocationFilter == option
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = {
                            viewModel.setGalleryLocationFilter(option)
                            locationMenuVisible = false
                        }
                    )
                }
            }
        }

        // Reset Filters Button
        if (hasActiveFilters) {
            AssistChip(
                onClick = { viewModel.clearGalleryFilters() },
                label = { Text("Reset filters") },
                leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    labelColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    }
}

@Composable
private fun RichGallerySearchPanel(
    uiState: UiState,
    viewModel: UnifiedViewModel,
    onSearchSubmitted: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Recent Searches
            if (uiState.galleryRecentSearches.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent searches",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearGalleryRecentSearches() }) {
                        Text("Clear all", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.galleryRecentSearches.take(8).forEach { recent ->
                        InputChip(
                            selected = false,
                            onClick = {
                                viewModel.useRecentGallerySearch(recent)
                            },
                            label = { Text(recent, maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.removeRecentGallerySearch(recent) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(12.dp))
                                }
                            }
                        )
                    }
                }
            }

            // Quick Suggestions & EXIF Tokens
            Text(
                "Quick search suggestions & operators",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Media categories
                listOf("Screenshots", "Camera", "Wallpapers", "GIFs").forEach { term ->
                    SuggestionTokenChip(term, uiState.gallerySearchQuery) {
                        viewModel.setGallerySearchQuery(toggleExactSearchToken(uiState.gallerySearchQuery, term))
                    }
                }

                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                // Camera & EXIF tokens
                listOf("camera:Pixel", "camera:Samsung", "camera:Sony", "make:Apple", "iso:100", "iso:800", "f/1.8").forEach { term ->
                    SuggestionTokenChip(term, uiState.gallerySearchQuery) {
                        viewModel.setGallerySearchQuery(toggleExactSearchToken(uiState.gallerySearchQuery, term))
                    }
                }

                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                // Location & GPS tokens
                listOf("gps:true", "gps:false", "near:", "location:").forEach { term ->
                    SuggestionTokenChip(term, uiState.gallerySearchQuery) {
                        viewModel.setGallerySearchQuery(appendOrToggleToken(uiState.gallerySearchQuery, term))
                    }
                }

                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                // Date tokens
                listOf("year:" + yearToken(), "year:" + (yearToken().toIntOrNull()?.minus(1) ?: 2025), "month:" + monthToken()).forEach { term ->
                    SuggestionTokenChip(term, uiState.gallerySearchQuery) {
                        viewModel.setGallerySearchQuery(toggleExactSearchToken(uiState.gallerySearchQuery, term))
                    }
                }

                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                // Tag tokens
                listOf("tag:portrait", "tag:nature", "tag:travel").forEach { term ->
                    SuggestionTokenChip(term, uiState.gallerySearchQuery) {
                        viewModel.setGallerySearchQuery(appendOrToggleToken(uiState.gallerySearchQuery, term))
                    }
                }
            }

            // Search Tips Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Rich Search Operators",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "• camera:<name> (e.g. camera:Pixel) to filter by device\n• iso:<number> (e.g. iso:400) or f:<aperture> for EXIF\n• gps:true or location:<city> for places\n• year:2026 or date:YYYY-MM-DD for precise dates\n• \"exact phrase\" in quotes for literal matching",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSearchSubmitted,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Search")
                }
            }
        }
    }
}

@Composable
private fun SuggestionTokenChip(
    token: String,
    currentQuery: String,
    onClick: () -> Unit
) {
    val isSelected = hasExactSearchToken(currentQuery, token)
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(token, style = MaterialTheme.typography.labelSmall) }
    )
}

private fun appendOrToggleToken(query: String, token: String): String {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return token
    if (trimmed.contains(token, ignoreCase = true)) {
        return trimmed.replace(token, "").replace(Regex("""\s+"""), " ").trim()
    }
    return "$trimmed $token"
}

private fun hasExactSearchToken(query: String, token: String): Boolean =
    query.split(Regex("""\s+""")).any { it.equals(token, ignoreCase = true) }

private fun toggleExactSearchToken(query: String, token: String): String {
    val parts = query.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    return if (parts.any { it.equals(token, ignoreCase = true) }) {
        parts.filterNot { it.equals(token, ignoreCase = true) }.joinToString(" ")
    } else {
        (parts + token).joinToString(" ")
    }
}

private fun todayToken(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())

private fun monthToken(): String =
    java.text.SimpleDateFormat("yyyy-MM", Locale.US).format(java.util.Date())

private fun yearToken(): String =
    java.text.SimpleDateFormat("yyyy", Locale.US).format(java.util.Date())

@Composable
private fun GalleryScrollbar(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    columns: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val layout = gridState.layoutInfo
    val total = layout.totalItemsCount
    val visible = layout.visibleItemsInfo.size
    if (total <= visible || total <= 0) return

    val maxFirst = (total - visible).coerceAtLeast(1)
    val progress = (gridState.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
    val thumbFraction = (visible.toFloat() / total).coerceIn(0.08f, 1f)

    Box(
        modifier = modifier
            .width(14.dp)
            .fillMaxHeight()
            .pointerInput(total, columns) {
                detectVerticalDragGestures { change, dragAmount ->
                    scope.launch {
                        val viewport = gridState.layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
                        val itemRange = (total - visible).coerceAtLeast(1)
                        val deltaItems = (dragAmount / viewport * itemRange).roundToInt()
                        val target = (gridState.firstVisibleItemIndex + deltaItems).coerceIn(0, maxFirst)
                        gridState.scrollToItem(target)
                    }
                }
            }
    ) {
        val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = 0.16f
        )
        val thumbColor = MaterialTheme.colorScheme.primary.copy(
            alpha = 0.62f
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = 3.dp.toPx()
            val thumbWidth = 5.dp.toPx()
            val trackX = (size.width - trackWidth) / 2f
            val thumbX = (size.width - thumbWidth) / 2f
            val thumbHeight = (size.height * thumbFraction).coerceAtLeast(24.dp.toPx())
            val thumbTop = (size.height - thumbHeight).coerceAtLeast(0f) * progress
            drawRoundRect(
                color = trackColor,
                topLeft = androidx.compose.ui.geometry.Offset(trackX, 0f),
                size = androidx.compose.ui.geometry.Size(trackWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth, trackWidth)
            )
            drawRoundRect(
                color = thumbColor,
                topLeft = androidx.compose.ui.geometry.Offset(thumbX, thumbTop),
                size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth, thumbWidth)
            )
        }
    }
}

@Composable
private fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    onItemClick: (MediaItem) -> Unit
) {
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = items,
            key = { it.uri.toString() },
            contentType = { if (it.isVideo) "video" else "photo" }
        ) { item ->
            MediaGridThumbnail(
                item = item,
                onClick = { onItemClick(item) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridThumbnail(
    item: MediaItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    selected: Boolean = false
) {
    val context = LocalContext.current
    val imageRequest = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(280, 280)
            .crossfade(false)
            .allowHardware(true)
            .memoryCacheKey(item.uri.toString())
            .diskCacheKey(item.uri.toString())
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video Duration badge overlay
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            startY = 100f
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                if (item.duration > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val sec = (item.duration / 1000) % 60
                    val min = (item.duration / 1000) / 60
                    Text(
                        text = String.format("%02d:%02d", min, sec),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }

        // Favorite star indicator
        if (item.isFavorite && !selected) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorite",
                tint = Color(0xFFFBBF24),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
            )
        }
    }
}

@Composable
private fun GallerySelectionBar(
    count: Int,
    onClear: () -> Unit,
    onFavorite: () -> Unit,
    onAiProcess: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Clear selection") }
            Text("$count selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onFavorite) { Icon(Icons.Default.Star, contentDescription = "Favorite selected") }
            IconButton(onClick = onAiProcess) { Icon(Icons.Default.AutoAwesome, contentDescription = "AI process selected") }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share selected") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete selected") }
        }
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<MediaAlbum>,
    onAlbumClick: (MediaAlbum) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = albums,
            key = { it.id },
            contentType = { "album" }
        ) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
private fun AlbumCard(
    album: MediaAlbum,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverUri != null) {
                    val context = LocalContext.current
                    val coverRequest = remember(album.coverUri) {
                        ImageRequest.Builder(context)
                            .data(album.coverUri)
                            .size(320, 320)
                            .crossfade(false)
                            .allowHardware(true)
                            .build()
                    }
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.itemCount} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyGalleryMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
