package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TrashEntity
import com.example.data.model.CategoryType
import com.example.data.model.FileItem
import com.example.ui.components.FileTypeIconBadge
import com.example.ui.components.formatDate
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ColorApks
import com.example.ui.theme.ColorArchives
import com.example.ui.theme.ColorAudio
import com.example.ui.theme.ColorDocuments
import com.example.ui.theme.ColorDownloads
import com.example.ui.theme.ColorImages
import com.example.ui.theme.ColorVideos
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UnifiedViewModel
import java.io.File

@Composable
fun HomeScreen(
    uiState: UiState,
    viewModel: UnifiedViewModel,
    modifier: Modifier = Modifier
) {
    // Back handler: if Home search query is active, clear search first
    BackHandler(enabled = uiState.homeSearchQuery.isNotEmpty()) {
        viewModel.clearHomeSearch()
    }

    // Back handler when inspecting a category
    BackHandler(enabled = uiState.selectedCategory != null && uiState.homeSearchQuery.isEmpty()) {
        viewModel.selectCategory(null)
    }

    var showEmptyTrashConfirm by remember { mutableStateOf(false) }
    var categorySearchQuery by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {

        // Top App Bar & Search Section
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                if (uiState.selectedCategory != null) {
                    // Category Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.selectCategory(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                        }
                        Text(
                            text = uiState.selectedCategory.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${uiState.categoryFiles.size} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Category Drilldown Search / Filter Bar
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("category_search_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextField(
                                value = categorySearchQuery,
                                onValueChange = { categorySearchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "Filter in ${uiState.selectedCategory.displayName}...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("category_search_input")
                            )
                            if (categorySearchQuery.isNotEmpty()) {
                                IconButton(onClick = { categorySearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    // Home Search Bar
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("home_search_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextField(
                                value = uiState.homeSearchQuery,
                                onValueChange = { viewModel.setHomeSearchQuery(it) },
                                placeholder = {
                                    Text(
                                        text = "Search files, media & documents...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_search_input")
                            )
                            if (uiState.homeSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearHomeSearch() },
                                    modifier = Modifier.testTag("home_search_clear")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Content
        if (uiState.selectedCategory != null) {
            // Category drilldown view
            val filteredCategoryFiles = if (categorySearchQuery.isBlank()) {
                uiState.categoryFiles
            } else {
                uiState.categoryFiles.filter { it.name.contains(categorySearchQuery, ignoreCase = true) }
            }

            if (filteredCategoryFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (categorySearchQuery.isNotBlank()) "No files match \"$categorySearchQuery\"" else "No ${uiState.selectedCategory.displayName} found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val categoryListState = rememberLazyListState()

                LazyColumn(
                    state = categoryListState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredCategoryFiles,
                        key = { it.path },
                        contentType = { if (it.isDirectory) "folder" else "file" }
                    ) { item ->
                        CategoryFileCard(
                            item = item,
                            onClick = { viewModel.openFile(item) }
                        )
                    }
                }
            }
        } else if (uiState.homeSearchQuery.isNotBlank()) {
            // Live Search Results View
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.isHomeSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (uiState.homeSearchResults.isEmpty() && !uiState.isHomeSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No files found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No files match \"${uiState.homeSearchQuery}\".\nTry checking the spelling or selecting 'All' categories.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val searchListState = rememberLazyListState()

                    LazyColumn(
                        state = searchListState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "search_results_header", contentType = "header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Search Results",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${uiState.homeSearchResults.size} found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(
                            items = uiState.homeSearchResults,
                            key = { it.path },
                            contentType = { if (it.isDirectory) "folder" else "file" }
                        ) { item ->
                            SearchResultCard(
                                item = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        viewModel.jumpToFolder(item.path)
                                    } else {
                                        viewModel.openFile(item)
                                    }
                                },
                                onOpenLocation = {
                                    val parent = File(item.path).parentFile?.absolutePath ?: item.path
                                    viewModel.jumpToFolder(parent)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(item) }
                            )
                        }
                    }
                }
            }
        } else {
            // Minimal Home Dashboard: Search Bar + Recent Items + Quick Tiles
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Recent Items
                if (uiState.recentsList.isNotEmpty()) {
                    item(key = "home_recent_items") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Recent Items",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_recent_items")
                        ) {
                            items(
                                items = uiState.recentsList,
                                key = { it.path }
                            ) { recent ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier
                                        .width(180.dp)
                                        .clickable {
                                            val fileItem = FileItem(
                                                name = recent.name,
                                                path = recent.path,
                                                size = recent.size,
                                                lastModified = recent.lastOpenedTimestamp,
                                                isDirectory = false,
                                                mimeType = recent.mimeType
                                            )
                                            viewModel.openFile(fileItem)
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = recent.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = formatDate(recent.lastOpenedTimestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Tiles
                item(key = "home_quick_tiles") {
                    Text(
                        text = "Quick Tiles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val quickTiles = listOf(
                        CategoryType.IMAGES to (Icons.Default.Image to ColorImages),
                        CategoryType.VIDEOS to (Icons.Default.Movie to ColorVideos),
                        CategoryType.AUDIO to (Icons.Default.AudioFile to ColorAudio),
                        CategoryType.DOCUMENTS to (Icons.Default.Description to ColorDocuments),
                        CategoryType.ARCHIVES to (Icons.Default.Archive to ColorArchives),
                        CategoryType.APKS to (Icons.Default.VideogameAsset to ColorApks),
                        CategoryType.DOWNLOADS to (Icons.Default.Download to ColorDownloads)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        quickTiles.chunked(2).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { (cat, visual) ->
                                    val count = uiState.categoryCounts[cat] ?: 0
                                    Box(modifier = Modifier.weight(1f)) {
                                        CategoryCard(
                                            title = cat.displayName,
                                            count = count,
                                            icon = visual.first,
                                            tint = visual.second,
                                            onClick = { viewModel.selectCategory(cat) }
                                        )
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = { Text("Empty Recycle Bin") },
            text = { Text("Are you sure you want to permanently delete all items in the Recycle Bin? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.emptyTrash()
                        showEmptyTrashConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Explorer Preferences (Persistent in Room)
    if (uiState.showPreferencesDialog) {
        ExplorerPreferencesDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { viewModel.setShowPreferencesDialog(false) }
        )
    }
}

@Composable
private fun BreakdownRow(label: String, bytes: Long, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = formatFileSize(bytes),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun TrashItemRow(
    item: TrashEntity,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatFileSize(item.size)} • Deleted ${formatDate(item.deletedTimestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDeleteForever) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Delete Forever",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    title: String,
    count: Int,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = "$count files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryFileCard(
    item: FileItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileTypeIconBadge(item = item)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = formatFileSize(item.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formatDate(item.lastModified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: FileItem,
    onClick: () -> Unit,
    onOpenLocation: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("search_result_item_${item.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileTypeIconBadge(item = item)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val parentName = File(item.path).parentFile?.name ?: "Storage"
                Text(
                    text = "📁 $parentName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (item.isDirectory) "${item.childCount} items" else formatFileSize(item.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formatDate(item.lastModified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Location button
            IconButton(
                onClick = onOpenLocation,
                modifier = Modifier.testTag("search_result_location_${item.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Open file location",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Favorite button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("search_result_fav_${item.name}")
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (item.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (item.isFavorite) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
