package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.TrashEntity
import com.example.data.model.CategoryType
import com.example.data.model.ExplorerDateFilter
import com.example.data.model.ExplorerSizeFilter
import com.example.data.model.FileItem
import com.example.data.model.GroupByOption
import com.example.data.model.SortOption
import com.example.data.model.ViewMode
import com.example.data.model.sortFiles
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

fun getCategoryColor(type: CategoryType): Color = when (type) {
    CategoryType.IMAGES -> ColorImages
    CategoryType.VIDEOS -> ColorVideos
    CategoryType.AUDIO -> ColorAudio
    CategoryType.DOCUMENTS -> ColorDocuments
    CategoryType.ARCHIVES -> ColorArchives
    CategoryType.APKS -> ColorApks
    CategoryType.DOWNLOADS -> ColorDownloads
}

fun getCategoryIcon(type: CategoryType): ImageVector = when (type) {
    CategoryType.IMAGES -> Icons.Default.Image
    CategoryType.VIDEOS -> Icons.Default.Movie
    CategoryType.AUDIO -> Icons.Default.AudioFile
    CategoryType.DOCUMENTS -> Icons.Default.Description
    CategoryType.ARCHIVES -> Icons.Default.Archive
    CategoryType.APKS -> Icons.Default.VideogameAsset
    CategoryType.DOWNLOADS -> Icons.Default.Download
}

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

    // Back handler when inspecting Recycle Bin
    BackHandler(enabled = uiState.isRecycleBinOpen && uiState.homeSearchQuery.isEmpty()) {
        viewModel.closeRecycleBin()
    }

    // Back handler when inspecting a category
    BackHandler(enabled = uiState.selectedCategory != null && uiState.homeSearchQuery.isEmpty() && !uiState.isRecycleBinOpen) {
        viewModel.selectCategory(null)
    }

    // Home Page Quick Tiles & Dashboard State
    var homeTileSortOption by remember { mutableStateOf(TileSortOption.DEFAULT) }
    var homeTileGroupBy by remember { mutableStateOf(TileGroupByOption.NONE) }
    var homeTileFilter by remember { mutableStateOf(TileFilterOption.ALL) }
    var homeTileViewMode by remember { mutableStateOf(ViewMode.GRID) }
    var homeTileFilterVisible by remember { mutableStateOf(false) }

    // Category Drilldown Page State
    var categorySearchQuery by remember { mutableStateOf("") }
    var categorySearchExpanded by remember { mutableStateOf(false) }
    var categorySortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var categoryViewMode by remember { mutableStateOf(ViewMode.DETAILED_LIST) }
    var categoryGroupBy by remember { mutableStateOf(GroupByOption.NONE) }
    var categoryFilterVisible by remember { mutableStateOf(false) }
    var categoryDateFilter by remember { mutableStateOf(ExplorerDateFilter.ALL) }
    var categorySizeFilter by remember { mutableStateOf(ExplorerSizeFilter.ALL) }
    var categorySubtypeFilter by remember { mutableStateOf("ALL") }
    var categoryFavoriteOnly by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.selectedCategory) {
        categorySearchQuery = ""
        categorySubtypeFilter = "ALL"
        categorySearchExpanded = false
    }

    // Recycle Bin Page State
    var recycleBinSearchQuery by remember { mutableStateOf("") }
    var recycleBinSearchExpanded by remember { mutableStateOf(false) }
    var recycleBinSortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var recycleBinViewMode by remember { mutableStateOf(ViewMode.DETAILED_LIST) }
    var recycleBinGroupBy by remember { mutableStateOf(GroupByOption.NONE) }
    var recycleBinFilterVisible by remember { mutableStateOf(false) }
    var recycleBinDateFilter by remember { mutableStateOf(ExplorerDateFilter.ALL) }
    var recycleBinSizeFilter by remember { mutableStateOf(ExplorerSizeFilter.ALL) }
    var recycleBinTypeFilter by remember { mutableStateOf("ALL") }
    var showEmptyConfirmDialog by remember { mutableStateOf(false) }
    var itemToDeleteForever by remember { mutableStateOf<TrashEntity?>(null) }

    LaunchedEffect(uiState.isRecycleBinOpen) {
        recycleBinSearchQuery = ""
        recycleBinSearchExpanded = false
    }

    // Home Search Results State
    var homeSearchSortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var homeSearchViewMode by remember { mutableStateOf(ViewMode.DETAILED_LIST) }
    var homeSearchGroupBy by remember { mutableStateOf(GroupByOption.NONE) }
    var homeSearchFilterVisible by remember { mutableStateOf(false) }
    var homeSearchDateFilter by remember { mutableStateOf(ExplorerDateFilter.ALL) }
    var homeSearchSizeFilter by remember { mutableStateOf(ExplorerSizeFilter.ALL) }

    // Home Page Recents State
    var recentsSortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var recentsViewMode by remember { mutableStateOf(ViewMode.COMPACT_LIST) }
    var recentsGroupBy by remember { mutableStateOf(GroupByOption.NONE) }

    Column(modifier = modifier.fillMaxSize()) {

        // Top App Bar & Search Section
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                if (uiState.isRecycleBinOpen) {
                    val filteredTrash = remember(
                        uiState.trashList,
                        recycleBinSearchQuery,
                        recycleBinDateFilter,
                        recycleBinSizeFilter,
                        recycleBinTypeFilter,
                        recycleBinSortOption
                    ) {
                        val filtered = filterTrash(
                            trash = uiState.trashList,
                            query = recycleBinSearchQuery,
                            dateFilter = recycleBinDateFilter,
                            sizeFilter = recycleBinSizeFilter,
                            typeFilter = recycleBinTypeFilter
                        )
                        sortTrash(filtered, recycleBinSortOption)
                    }

                    // Recycle Bin Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.closeRecycleBin() },
                            modifier = Modifier.testTag("recycle_bin_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recycle Bin",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val totalTrashBytes = uiState.trashList.sumOf { it.size }
                            Text(
                                text = "${filteredTrash.size} items • ${formatFileSize(totalTrashBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    recycleBinSearchExpanded = !recycleBinSearchExpanded
                                    if (!recycleBinSearchExpanded) recycleBinSearchQuery = ""
                                },
                                modifier = Modifier.testTag("recycle_bin_search_toggle")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search in Recycle Bin",
                                    tint = if (recycleBinSearchExpanded || recycleBinSearchQuery.isNotEmpty())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ViewModeToggleButton(
                                currentViewMode = recycleBinViewMode,
                                onViewModeChanged = { recycleBinViewMode = it }
                            )
                            if (uiState.trashList.isNotEmpty()) {
                                IconButton(
                                    onClick = { showEmptyConfirmDialog = true },
                                    modifier = Modifier.testTag("recycle_bin_empty_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = "Empty Recycle Bin",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // Recycle Bin Search Bar (expands on search tap or when query present)
                    AnimatedVisibility(visible = recycleBinSearchExpanded || recycleBinSearchQuery.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .testTag("recycle_bin_search_bar")
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
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextField(
                                    value = recycleBinSearchQuery,
                                    onValueChange = { recycleBinSearchQuery = it },
                                    placeholder = {
                                        Text(
                                            text = "Filter in Recycle Bin...",
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
                                        .testTag("recycle_bin_search_input")
                                )
                                if (recycleBinSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { recycleBinSearchQuery = "" }) {
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

                    // Modern Recycle Bin Controls & Filter Chips
                    RecycleBinFilterChipsBar(
                        sortOption = recycleBinSortOption,
                        onSortSelected = { recycleBinSortOption = it },
                        groupBy = recycleBinGroupBy,
                        onGroupBySelected = { recycleBinGroupBy = it },
                        typeFilter = recycleBinTypeFilter,
                        onTypeFilterChanged = { recycleBinTypeFilter = it },
                        dateFilter = recycleBinDateFilter,
                        onDateFilterChanged = { recycleBinDateFilter = it },
                        sizeFilter = recycleBinSizeFilter,
                        onSizeFilterChanged = { recycleBinSizeFilter = it },
                        onResetFilters = {
                            recycleBinTypeFilter = "ALL"
                            recycleBinDateFilter = ExplorerDateFilter.ALL
                            recycleBinSizeFilter = ExplorerSizeFilter.ALL
                            recycleBinSortOption = SortOption.DATE_DESC
                            recycleBinGroupBy = GroupByOption.NONE
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                } else if (uiState.selectedCategory != null) {
                    val filteredCategoryFiles = remember(
                        uiState.categoryFiles,
                        categorySearchQuery,
                        categoryDateFilter,
                        categorySizeFilter,
                        categorySubtypeFilter,
                        categoryFavoriteOnly,
                        categorySortOption
                    ) {
                        val filtered = filterFiles(
                            files = uiState.categoryFiles,
                            query = categorySearchQuery,
                            dateFilter = categoryDateFilter,
                            sizeFilter = categorySizeFilter,
                            subtypeFilter = categorySubtypeFilter,
                            favoritesOnly = categoryFavoriteOnly,
                            categoryType = uiState.selectedCategory
                        )
                        sortFiles(filtered, categorySortOption)
                    }

                    val categoryColor = getCategoryColor(uiState.selectedCategory)

                    // Category Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.selectCategory(null) },
                            modifier = Modifier.testTag("category_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(categoryColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = uiState.selectedCategory.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val totalCatBytes = remember(filteredCategoryFiles) { filteredCategoryFiles.sumOf { it.size } }
                            Text(
                                text = if (filteredCategoryFiles.size == uiState.categoryFiles.size)
                                    "${filteredCategoryFiles.size} items • ${formatFileSize(totalCatBytes)}"
                                else
                                    "${filteredCategoryFiles.size} of ${uiState.categoryFiles.size} items • ${formatFileSize(totalCatBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    categorySearchExpanded = !categorySearchExpanded
                                    if (!categorySearchExpanded) categorySearchQuery = ""
                                },
                                modifier = Modifier.testTag("category_search_toggle")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (categorySearchExpanded || categorySearchQuery.isNotEmpty())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ViewModeToggleButton(
                                currentViewMode = categoryViewMode,
                                onViewModeChanged = { categoryViewMode = it }
                            )
                        }
                    }

                    // Category Drilldown Search Bar (expands on search tap or when query present)
                    AnimatedVisibility(visible = categorySearchExpanded || categorySearchQuery.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
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
                                    tint = categoryColor,
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
                    }

                    // Modern Category Filter & Sort Control Strip
                    CategoryFilterChipsBar(
                        category = uiState.selectedCategory,
                        sortOption = categorySortOption,
                        onSortSelected = { categorySortOption = it },
                        groupBy = categoryGroupBy,
                        onGroupBySelected = { categoryGroupBy = it },
                        subtypeFilter = categorySubtypeFilter,
                        onSubtypeFilterChanged = { categorySubtypeFilter = it },
                        dateFilter = categoryDateFilter,
                        onDateFilterChanged = { categoryDateFilter = it },
                        sizeFilter = categorySizeFilter,
                        onSizeFilterChanged = { categorySizeFilter = it },
                        favoriteOnly = categoryFavoriteOnly,
                        onFavoriteOnlyToggle = { categoryFavoriteOnly = !categoryFavoriteOnly },
                        onResetFilters = {
                            categorySubtypeFilter = "ALL"
                            categoryDateFilter = ExplorerDateFilter.ALL
                            categorySizeFilter = ExplorerSizeFilter.ALL
                            categoryFavoriteOnly = false
                            categorySortOption = SortOption.DATE_DESC
                            categoryGroupBy = GroupByOption.NONE
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                } else {
                    // Home Top Bar: Compact search bar + three-dot preferences menu beside it
                    var showHomeMenu by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pill-shaped search bar
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_search_bar")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                TextField(
                                    value = uiState.homeSearchQuery,
                                    onValueChange = { viewModel.setHomeSearchQuery(it) },
                                    placeholder = {
                                        Text(
                                            text = "Search files, videos, documents...",
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
                                        modifier = Modifier.size(28.dp).testTag("home_search_clear")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Three-dot menu button beside search bar
                        Box {
                            IconButton(
                                onClick = { showHomeMenu = true },
                                modifier = Modifier.testTag("home_settings_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showHomeMenu,
                                onDismissRequest = { showHomeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Preferences") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showHomeMenu = false
                                        viewModel.setShowPreferencesDialog(true)
                                    },
                                    modifier = Modifier.testTag("menu_preferences")
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }

        // Content
        if (uiState.isRecycleBinOpen) {
            // Recycle Bin Drilldown View (Full Page)
            val filteredTrash = remember(
                uiState.trashList,
                recycleBinSearchQuery,
                recycleBinDateFilter,
                recycleBinSizeFilter,
                recycleBinTypeFilter,
                recycleBinSortOption
            ) {
                val filtered = filterTrash(
                    trash = uiState.trashList,
                    query = recycleBinSearchQuery,
                    dateFilter = recycleBinDateFilter,
                    sizeFilter = recycleBinSizeFilter,
                    typeFilter = recycleBinTypeFilter
                )
                sortTrash(filtered, recycleBinSortOption)
            }

            if (uiState.trashList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Recycle Bin is Empty",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Deleted files and folders will appear here safely.\nYou can restore or permanently delete them at any time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (filteredTrash.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "No Items Match Filters",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Try clearing your search query or resetting filters to view deleted items.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        FilledTonalButton(
                            onClick = {
                                recycleBinSearchQuery = ""
                                recycleBinDateFilter = ExplorerDateFilter.ALL
                                recycleBinSizeFilter = ExplorerSizeFilter.ALL
                                recycleBinTypeFilter = "ALL"
                                recycleBinSortOption = SortOption.DATE_DESC
                                recycleBinGroupBy = GroupByOption.NONE
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset All Filters")
                        }
                    }
                }
            } else {
                RecycleBinContent(
                    trashItems = filteredTrash,
                    viewMode = recycleBinViewMode,
                    groupBy = recycleBinGroupBy,
                    onRestore = { viewModel.restoreTrashItem(it) },
                    onDeleteForever = { itemToDeleteForever = it }
                )
            }
        } else if (uiState.selectedCategory != null) {
            // Category drilldown view
            val filteredCategoryFiles = remember(
                uiState.categoryFiles,
                categorySearchQuery,
                categoryDateFilter,
                categorySizeFilter,
                categorySubtypeFilter,
                categoryFavoriteOnly,
                categorySortOption
            ) {
                val filtered = filterFiles(
                    files = uiState.categoryFiles,
                    query = categorySearchQuery,
                    dateFilter = categoryDateFilter,
                    sizeFilter = categorySizeFilter,
                    subtypeFilter = categorySubtypeFilter,
                    favoritesOnly = categoryFavoriteOnly,
                    categoryType = uiState.selectedCategory
                )
                sortFiles(filtered, categorySortOption)
            }

            if (filteredCategoryFiles.isEmpty()) {
                val categoryColor = getCategoryColor(uiState.selectedCategory)
                val categoryIcon = getCategoryIcon(uiState.selectedCategory)
                val isFiltered = categorySearchQuery.isNotBlank() ||
                    categoryDateFilter != ExplorerDateFilter.ALL ||
                    categorySizeFilter != ExplorerSizeFilter.ALL ||
                    categorySubtypeFilter != "ALL" ||
                    categoryFavoriteOnly

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(categoryColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFiltered) Icons.Default.FilterList else categoryIcon,
                                contentDescription = null,
                                tint = categoryColor,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (isFiltered) "No Matching Files" else "No ${uiState.selectedCategory.displayName} Found",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isFiltered)
                                "No files match '${categorySearchQuery.ifEmpty { "active filters" }}'. Try resetting your filters."
                            else
                                "Any ${uiState.selectedCategory.displayName.lowercase()} saved in storage will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (isFiltered) {
                            Spacer(modifier = Modifier.height(20.dp))
                            FilledTonalButton(
                                onClick = {
                                    categorySearchQuery = ""
                                    categorySubtypeFilter = "ALL"
                                    categoryDateFilter = ExplorerDateFilter.ALL
                                    categorySizeFilter = ExplorerSizeFilter.ALL
                                    categoryFavoriteOnly = false
                                    categorySortOption = SortOption.DATE_DESC
                                    categoryGroupBy = GroupByOption.NONE
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reset All Filters")
                            }
                        }
                    }
                }
            } else {
                CategoryFilesContent(
                    files = filteredCategoryFiles,
                    viewMode = categoryViewMode,
                    groupBy = categoryGroupBy,
                    categoryType = uiState.selectedCategory,
                    onFileClick = { viewModel.openFile(it) }
                )
            }
        } else if (uiState.homeSearchQuery.isNotBlank()) {
            // Live Search Results View
            val processedSearchResults = remember(
                uiState.homeSearchResults,
                homeSearchDateFilter,
                homeSearchSizeFilter,
                homeSearchSortOption
            ) {
                val filtered = filterFiles(
                    files = uiState.homeSearchResults,
                    query = "",
                    dateFilter = homeSearchDateFilter,
                    sizeFilter = homeSearchSizeFilter
                )
                sortFiles(filtered, homeSearchSortOption)
            }

            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.isHomeSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (processedSearchResults.isEmpty() && !uiState.isHomeSearching) {
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
                                text = "No files match \"${uiState.homeSearchQuery}\".\nTry checking the spelling or resetting filters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Search Results",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${processedSearchResults.size} found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SortMenuButton(
                                    currentSort = homeSearchSortOption,
                                    onSortSelected = { homeSearchSortOption = it }
                                )
                                GroupByMenuButton(
                                    currentGroupBy = homeSearchGroupBy,
                                    onGroupBySelected = { homeSearchGroupBy = it }
                                )
                                FilterToggleButton(
                                    isFilterBarVisible = homeSearchFilterVisible,
                                    hasActiveFilters = homeSearchDateFilter != ExplorerDateFilter.ALL || homeSearchSizeFilter != ExplorerSizeFilter.ALL,
                                    onToggle = { homeSearchFilterVisible = !homeSearchFilterVisible }
                                )
                                ViewModeToggleButton(
                                    currentViewMode = homeSearchViewMode,
                                    onViewModeChanged = { homeSearchViewMode = it }
                                )
                            }
                        }

                        if (homeSearchFilterVisible || homeSearchDateFilter != ExplorerDateFilter.ALL || homeSearchSizeFilter != ExplorerSizeFilter.ALL) {
                            SearchFilterChipsBar(
                                dateFilter = homeSearchDateFilter,
                                onDateFilterChanged = { homeSearchDateFilter = it },
                                sizeFilter = homeSearchSizeFilter,
                                onSizeFilterChanged = { homeSearchSizeFilter = it },
                                onResetFilters = {
                                    homeSearchDateFilter = ExplorerDateFilter.ALL
                                    homeSearchSizeFilter = ExplorerSizeFilter.ALL
                                }
                            )
                        }

                        CategoryFilesContent(
                            files = processedSearchResults,
                            viewMode = homeSearchViewMode,
                            groupBy = homeSearchGroupBy,
                            onFileClick = {
                                if (it.isDirectory) {
                                    viewModel.jumpToFolder(it.path)
                                } else {
                                    viewModel.openFile(it)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Modern Home Dashboard: Recent Items + Quick Tiles
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Recent Items
                if (uiState.recentsList.isNotEmpty()) {
                    item(key = "home_recent_items") {
                        val recentFileItems = remember(uiState.recentsList, recentsSortOption) {
                            val mapped = uiState.recentsList.map { recent ->
                                FileItem(
                                    name = recent.name,
                                    path = recent.path,
                                    size = recent.size,
                                    lastModified = recent.lastOpenedTimestamp,
                                    isDirectory = false,
                                    mimeType = recent.mimeType
                                )
                            }
                            sortFiles(mapped, recentsSortOption)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SortMenuButton(
                                    currentSort = recentsSortOption,
                                    onSortSelected = { recentsSortOption = it }
                                )
                                GroupByMenuButton(
                                    currentGroupBy = recentsGroupBy,
                                    onGroupBySelected = { recentsGroupBy = it }
                                )
                                ViewModeToggleButton(
                                    currentViewMode = recentsViewMode,
                                    onViewModeChanged = { recentsViewMode = it }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (recentsViewMode == ViewMode.GRID) {
                            val groupedRecents = remember(recentFileItems, recentsGroupBy) {
                                groupFiles(recentFileItems, recentsGroupBy)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                groupedRecents.forEach { (header, itemsInGroup) ->
                                    if (header.isNotBlank()) {
                                        Text(
                                            text = "$header (${itemsInGroup.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    itemsInGroup.chunked(2).forEach { rowItems ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            rowItems.forEach { recentItem ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    CategoryFileGridCard(
                                                        item = recentItem,
                                                        onClick = { viewModel.openFile(recentItem) }
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
                        } else if (recentsViewMode == ViewMode.DETAILED_LIST) {
                            val groupedRecents = remember(recentFileItems, recentsGroupBy) {
                                groupFiles(recentFileItems, recentsGroupBy)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                groupedRecents.forEach { (header, itemsInGroup) ->
                                    if (header.isNotBlank()) {
                                        Text(
                                            text = "$header (${itemsInGroup.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    itemsInGroup.forEach { recentItem ->
                                        CategoryFileCard(
                                            item = recentItem,
                                            onClick = { viewModel.openFile(recentItem) }
                                        )
                                    }
                                }
                            }
                        } else {
                            // Compact horizontal row
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_recent_items")
                            ) {
                                items(
                                    items = recentFileItems,
                                    key = { it.path }
                                ) { recent ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        modifier = Modifier
                                            .width(180.dp)
                                            .clickable { viewModel.openFile(recent) }
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
                                                text = formatDate(recent.lastModified),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Tiles
                item(key = "home_quick_tiles") {
                    val recycleBinColor = MaterialTheme.colorScheme.error
                    val quickTiles = remember(uiState.categoryCounts, uiState.trashList.size, recycleBinColor) {
                        listOf(
                            QuickTileEntry(
                                title = CategoryType.IMAGES.displayName,
                                count = uiState.categoryCounts[CategoryType.IMAGES] ?: 0,
                                icon = Icons.Default.Image,
                                tint = ColorImages,
                                description = "Photos, screenshots & illustrations",
                                onClick = { viewModel.selectCategory(CategoryType.IMAGES) }
                            ),
                            QuickTileEntry(
                                title = CategoryType.VIDEOS.displayName,
                                count = uiState.categoryCounts[CategoryType.VIDEOS] ?: 0,
                                icon = Icons.Default.Movie,
                                tint = ColorVideos,
                                description = "Movies, camera recordings & clips",
                                onClick = { viewModel.selectCategory(CategoryType.VIDEOS) }
                            ),
                            QuickTileEntry(
                                title = CategoryType.AUDIO.displayName,
                                count = uiState.categoryCounts[CategoryType.AUDIO] ?: 0,
                                icon = Icons.Default.AudioFile,
                                tint = ColorAudio,
                                description = "Music, voice recordings & podcasts",
                                onClick = { viewModel.selectCategory(CategoryType.AUDIO) }
                            ),
                            QuickTileEntry(
                                title = CategoryType.DOCUMENTS.displayName,
                                count = uiState.categoryCounts[CategoryType.DOCUMENTS] ?: 0,
                                icon = Icons.Default.Description,
                                tint = ColorDocuments,
                                description = "PDF, Word, Excel, text & ebooks",
                                onClick = { viewModel.selectCategory(CategoryType.DOCUMENTS) }
                            ),
                            QuickTileEntry(
                                title = CategoryType.ARCHIVES.displayName,
                                count = uiState.categoryCounts[CategoryType.ARCHIVES] ?: 0,
                                icon = Icons.Default.Archive,
                                tint = ColorArchives,
                                description = "ZIP, RAR, 7Z & tarball files",
                                onClick = { viewModel.selectCategory(CategoryType.ARCHIVES) }
                            ),
                            QuickTileEntry(
                                title = CategoryType.APKS.displayName,
                                count = uiState.categoryCounts[CategoryType.APKS] ?: 0,
                                icon = Icons.Default.VideogameAsset,
                                tint = ColorApks,
                                description = "Android app installer packages",
                                onClick = { viewModel.selectCategory(CategoryType.APKS) }
                            ),
                            QuickTileEntry(
                                title = CategoryType.DOWNLOADS.displayName,
                                count = uiState.categoryCounts[CategoryType.DOWNLOADS] ?: 0,
                                icon = Icons.Default.Download,
                                tint = ColorDownloads,
                                description = "Downloaded files & browser items",
                                onClick = { viewModel.selectCategory(CategoryType.DOWNLOADS) }
                            ),
                            QuickTileEntry(
                                title = "Recycle Bin",
                                count = uiState.trashList.size,
                                icon = Icons.Default.Delete,
                                tint = recycleBinColor,
                                description = "Safely recoverable deleted files",
                                onClick = { viewModel.openRecycleBin() }
                            )
                        )
                    }

                    val filteredTiles = remember(quickTiles, homeTileFilter) {
                        when (homeTileFilter) {
                            TileFilterOption.ALL -> quickTiles
                            TileFilterOption.HAS_FILES -> quickTiles.filter { it.count > 0 }
                            TileFilterOption.MEDIA -> quickTiles.filter { it.title in listOf("Images", "Videos", "Audio") }
                            TileFilterOption.DOCUMENTS -> quickTiles.filter { it.title in listOf("Documents", "Archives", "APKs") }
                            TileFilterOption.SYSTEM -> quickTiles.filter { it.title in listOf("Downloads", "Recycle Bin") }
                        }
                    }

                    val sortedTiles = remember(filteredTiles, homeTileSortOption) {
                        when (homeTileSortOption) {
                            TileSortOption.DEFAULT -> filteredTiles
                            TileSortOption.NAME_ASC -> filteredTiles.sortedBy { it.title }
                            TileSortOption.NAME_DESC -> filteredTiles.sortedByDescending { it.title }
                            TileSortOption.COUNT_DESC -> filteredTiles.sortedByDescending { it.count }
                            TileSortOption.COUNT_ASC -> filteredTiles.sortedBy { it.count }
                        }
                    }

                    val groupedTiles = remember(sortedTiles, homeTileGroupBy) {
                        when (homeTileGroupBy) {
                            TileGroupByOption.NONE -> mapOf("" to sortedTiles)
                            TileGroupByOption.CATEGORY -> sortedTiles.groupBy { tile ->
                                when (tile.title) {
                                    "Images", "Videos", "Audio" -> "Media"
                                    "Documents", "Archives", "APKs" -> "Documents & Archives"
                                    "Downloads", "Recycle Bin" -> "System & Storage"
                                    else -> "Other"
                                }
                            }
                            TileGroupByOption.HAS_FILES -> sortedTiles.groupBy { tile ->
                                if (tile.count > 0) "Active Categories" else "Empty Categories"
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Categories",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${filteredTiles.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            TileSortMenuButton(
                                currentSort = homeTileSortOption,
                                onSortSelected = { homeTileSortOption = it }
                            )
                            TileGroupByMenuButton(
                                currentGroupBy = homeTileGroupBy,
                                onGroupBySelected = { homeTileGroupBy = it }
                            )
                            FilterToggleButton(
                                isFilterBarVisible = homeTileFilterVisible,
                                hasActiveFilters = homeTileFilter != TileFilterOption.ALL,
                                onToggle = { homeTileFilterVisible = !homeTileFilterVisible }
                            )
                            ViewModeToggleButton(
                                currentViewMode = homeTileViewMode,
                                onViewModeChanged = { homeTileViewMode = it }
                            )
                        }
                    }

                    if (homeTileFilterVisible || homeTileFilter != TileFilterOption.ALL) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HomeFilterChipsBar(
                            currentFilter = homeTileFilter,
                            onFilterChanged = { homeTileFilter = it },
                            onReset = { homeTileFilter = TileFilterOption.ALL }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredTiles.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No tiles match current filter",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { homeTileFilter = TileFilterOption.ALL }) {
                                    Text("Show All Tiles")
                                }
                            }
                        }
                    } else if (homeTileViewMode == ViewMode.GRID) {
                        // 2-Column Grid
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            groupedTiles.forEach { (header, tilesInGroup) ->
                                if (header.isNotBlank()) {
                                    Text(
                                        text = "$header (${tilesInGroup.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                tilesInGroup.chunked(2).forEach { rowItems ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        rowItems.forEach { tile ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                CategoryCard(
                                                    title = tile.title,
                                                    count = tile.count,
                                                    icon = tile.icon,
                                                    tint = tile.tint,
                                                    onClick = tile.onClick
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
                    } else if (homeTileViewMode == ViewMode.COMPACT_LIST) {
                        // 4-Column Compact Grid
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            groupedTiles.forEach { (header, tilesInGroup) ->
                                if (header.isNotBlank()) {
                                    Text(
                                        text = "$header (${tilesInGroup.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                tilesInGroup.chunked(4).forEach { rowItems ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        rowItems.forEach { tile ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                CategoryCompactCard(
                                                    title = tile.title,
                                                    count = tile.count,
                                                    icon = tile.icon,
                                                    tint = tile.tint,
                                                    onClick = tile.onClick
                                                )
                                            }
                                        }
                                        repeat(4 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Detailed List
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            groupedTiles.forEach { (header, tilesInGroup) ->
                                if (header.isNotBlank()) {
                                    Text(
                                        text = "$header (${tilesInGroup.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                tilesInGroup.forEach { tile ->
                                    CategoryDetailedListCard(
                                        title = tile.title,
                                        count = tile.count,
                                        icon = tile.icon,
                                        tint = tile.tint,
                                        description = tile.description,
                                        onClick = tile.onClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirmDialog = false },
            title = { Text("Empty Recycle Bin") },
            text = { Text("Are you sure you want to permanently delete all ${uiState.trashList.size} items in the Recycle Bin? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.emptyTrash()
                        showEmptyConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (itemToDeleteForever != null) {
        val target = itemToDeleteForever!!
        AlertDialog(
            onDismissRequest = { itemToDeleteForever = null },
            title = { Text("Delete Permanently?") },
            text = { Text("Are you sure you want to permanently delete \"${target.name}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.permanentlyDeleteTrash(target)
                        itemToDeleteForever = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeleteForever = null }) {
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
private fun StorageOverviewCard(
    storageStats: com.example.data.model.StorageStats,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val total = if (storageStats.totalBytes > 0) storageStats.totalBytes else 1L
    val used = storageStats.usedBytes.coerceAtLeast(0L)
    val usedFraction = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val usedPercent = (usedFraction * 100).toInt()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Internal Storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${formatFileSize(storageStats.freeBytes)} free of ${formatFileSize(storageStats.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "$usedPercent% used",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-segment progress bar or smooth indicator
            val imgFraction = (storageStats.imagesBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val vidFraction = (storageStats.videosBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val audFraction = (storageStats.audioBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val docFraction = (storageStats.documentsBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val sumFraction = imgFraction + vidFraction + audFraction + docFraction

            if (sumFraction > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (imgFraction > 0.005f) {
                            Box(modifier = Modifier.weight(imgFraction).fillMaxHeight().background(ColorImages))
                        }
                        if (vidFraction > 0.005f) {
                            Box(modifier = Modifier.weight(vidFraction).fillMaxHeight().background(ColorVideos))
                        }
                        if (audFraction > 0.005f) {
                            Box(modifier = Modifier.weight(audFraction).fillMaxHeight().background(ColorAudio))
                        }
                        if (docFraction > 0.005f) {
                            Box(modifier = Modifier.weight(docFraction).fillMaxHeight().background(ColorDocuments))
                        }
                        val remainingUsed = (usedFraction - sumFraction).coerceAtLeast(0f)
                        if (remainingUsed > 0.005f) {
                            Box(modifier = Modifier.weight(remainingUsed).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                        }
                        val freeFraction = (1f - usedFraction).coerceAtLeast(0f)
                        if (freeFraction > 0.005f) {
                            Box(modifier = Modifier.weight(freeFraction).fillMaxHeight().background(Color.Transparent))
                        }
                    }
                }
            } else {
                LinearProgressIndicator(
                    progress = { usedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Breakdown Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StorageCategoryPill("Images", storageStats.imagesBytes, ColorImages)
                StorageCategoryPill("Videos", storageStats.videosBytes, ColorVideos)
                StorageCategoryPill("Audio", storageStats.audioBytes, ColorAudio)
                StorageCategoryPill("Docs", storageStats.documentsBytes, ColorDocuments)
            }
        }
    }
}

@Composable
private fun StorageCategoryPill(label: String, bytes: Long, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label: ${formatFileSize(bytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class QuickTileEntry(
    val title: String,
    val count: Int,
    val icon: ImageVector,
    val tint: Color,
    val description: String = "",
    val onClick: () -> Unit
)

enum class TileSortOption(val title: String) {
    DEFAULT("Default Order"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    COUNT_DESC("Most Items"),
    COUNT_ASC("Fewest Items")
}

enum class TileGroupByOption(val label: String) {
    NONE("None"),
    CATEGORY("By Section"),
    HAS_FILES("Has Items vs Empty")
}

enum class TileFilterOption(val label: String) {
    ALL("All"),
    HAS_FILES("With Items"),
    MEDIA("Media"),
    DOCUMENTS("Documents"),
    SYSTEM("System & Bin")
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("category_card_$title")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (title == "Recycle Bin") "$count items" else "$count files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CategoryCompactCard(
    title: String,
    count: Int,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("category_compact_$title")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CategoryDetailedListCard(
    title: String,
    count: Int,
    icon: ImageVector,
    tint: Color,
    description: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("category_detailed_$title")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = tint.copy(alpha = 0.12f),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = if (title == "Recycle Bin") "$count items" else "$count files",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = tint,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CategoryFileCard(
    item: FileItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("category_file_item_${item.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if ((item.isImage || item.isVideo) && item.uri != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val context = LocalContext.current
                    val thumbRequest = remember(item.uri) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(160, 160)
                            .crossfade(true)
                            .allowHardware(true)
                            .build()
                    }
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (item.isVideo) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            } else {
                FileTypeIconBadge(item = item, modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatFileSize(item.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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

            if (item.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryFileCompactRow(
    item: FileItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("category_compact_row_${item.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if ((item.isImage || item.isVideo) && item.uri != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val context = LocalContext.current
                    val thumbRequest = remember(item.uri) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(100, 100)
                            .crossfade(true)
                            .allowHardware(true)
                            .build()
                    }
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                FileTypeIconBadge(item = item, modifier = Modifier.size(34.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatFileSize(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "•",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatDate(item.lastModified),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
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

@Composable
private fun RecycleBinPageCard(
    item: TrashEntity,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recycle_bin_item_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (item.isDirectory) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        text = "Deleted ${formatDate(item.deletedTimestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.originalPath.isNotBlank()) {
                    Text(
                        text = "Original: ${item.originalPath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onRestore,
                modifier = Modifier.testTag("restore_btn_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onDeleteForever,
                modifier = Modifier.testTag("delete_forever_btn_${item.id}")
            ) {
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
private fun RecycleBinCompactRow(
    item: TrashEntity,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (item.isDirectory) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatFileSize(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onRestore, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDeleteForever, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Delete Forever",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TileSortMenuButton(
    currentSort: TileSortOption,
    onSortSelected: (TileSortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("home_tile_sort_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort quick tiles",
                tint = if (currentSort != TileSortOption.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TileSortOption.entries.forEach { option ->
                val isSelected = currentSort == option
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.title,
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
                        onSortSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TileGroupByMenuButton(
    currentGroupBy: TileGroupByOption,
    onGroupBySelected: (TileGroupByOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("home_tile_group_by_button")
        ) {
            Icon(
                imageVector = Icons.Default.ViewColumn,
                contentDescription = "Group quick tiles",
                tint = if (currentGroupBy != TileGroupByOption.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TileGroupByOption.entries.forEach { option ->
                val isSelected = currentGroupBy == option
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Group: ${option.label}",
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
                        onGroupBySelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchFilterChipsBar(
    dateFilter: ExplorerDateFilter,
    onDateFilterChanged: (ExplorerDateFilter) -> Unit,
    sizeFilter: ExplorerSizeFilter,
    onSizeFilterChanged: (ExplorerSizeFilter) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dateMenuExpanded by remember { mutableStateOf(false) }
    var sizeMenuExpanded by remember { mutableStateOf(false) }
    val hasActiveFilters = dateFilter != ExplorerDateFilter.ALL || sizeFilter != ExplorerSizeFilter.ALL

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Box {
                FilterChip(
                    selected = dateFilter != ExplorerDateFilter.ALL,
                    onClick = { dateMenuExpanded = true },
                    label = { Text(if (dateFilter == ExplorerDateFilter.ALL) "Date: Any" else "Date: ${dateFilter.label}") }
                )
                DropdownMenu(
                    expanded = dateMenuExpanded,
                    onDismissRequest = { dateMenuExpanded = false }
                ) {
                    ExplorerDateFilter.entries.forEach { option ->
                        val isSelected = dateFilter == option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
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
                                onDateFilterChanged(option)
                                dateMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        item {
            Box {
                FilterChip(
                    selected = sizeFilter != ExplorerSizeFilter.ALL,
                    onClick = { sizeMenuExpanded = true },
                    label = { Text(if (sizeFilter == ExplorerSizeFilter.ALL) "Size: Any" else "Size: ${sizeFilter.label}") }
                )
                DropdownMenu(
                    expanded = sizeMenuExpanded,
                    onDismissRequest = { sizeMenuExpanded = false }
                ) {
                    ExplorerSizeFilter.entries.forEach { option ->
                        val isSelected = sizeFilter == option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
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
                                onSizeFilterChanged(option)
                                sizeMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        if (hasActiveFilters) {
            item {
                FilterChip(
                    selected = false,
                    onClick = onResetFilters,
                    label = { Text("Reset Filters") },
                    leadingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun HomeFilterChipsBar(
    currentFilter: TileFilterOption,
    onFilterChanged: (TileFilterOption) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(TileFilterOption.entries) { option ->
            FilterChip(
                selected = currentFilter == option,
                onClick = { onFilterChanged(option) },
                label = { Text(option.label) }
            )
        }
        if (currentFilter != TileFilterOption.ALL) {
            item {
                FilterChip(
                    selected = false,
                    onClick = onReset,
                    label = { Text("Reset") },
                    leadingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun SortMenuButton(
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("sort_menu_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SortOption.entries.forEach { option ->
                val isSelected = currentSort == option
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.title,
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
                        onSortSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun GroupByMenuButton(
    currentGroupBy: GroupByOption,
    onGroupBySelected: (GroupByOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("group_by_menu_button")
        ) {
            Icon(
                imageVector = Icons.Default.ViewColumn,
                contentDescription = "Group by",
                tint = if (currentGroupBy != GroupByOption.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            GroupByOption.entries.forEach { option ->
                val isSelected = currentGroupBy == option
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Group by: ${option.label}",
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
                        onGroupBySelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FilterToggleButton(
    isFilterBarVisible: Boolean,
    hasActiveFilters: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.testTag("filter_toggle_button")
    ) {
        Box {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filter options",
                tint = if (hasActiveFilters || isFilterBarVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasActiveFilters) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}

@Composable
private fun ViewModeToggleButton(
    currentViewMode: ViewMode,
    onViewModeChanged: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("view_mode_toggle_button")
        ) {
            Icon(
                imageVector = when (currentViewMode) {
                    ViewMode.GRID -> Icons.Default.GridView
                    ViewMode.DETAILED_LIST -> Icons.Default.ViewList
                    ViewMode.COMPACT_LIST -> Icons.Default.ViewColumn
                },
                contentDescription = "Switch view mode (${currentViewMode.name})",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val modes = listOf(
                Triple(ViewMode.GRID, "Grid View", Icons.Default.GridView),
                Triple(ViewMode.DETAILED_LIST, "Detailed List", Icons.Default.ViewList),
                Triple(ViewMode.COMPACT_LIST, "Compact List", Icons.Default.ViewColumn)
            )
            modes.forEach { (mode, label, icon) ->
                val isSelected = currentViewMode == mode
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        Icon(icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        onViewModeChanged(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterChipsBar(
    category: CategoryType,
    sortOption: SortOption,
    onSortSelected: (SortOption) -> Unit,
    groupBy: GroupByOption,
    onGroupBySelected: (GroupByOption) -> Unit,
    subtypeFilter: String,
    onSubtypeFilterChanged: (String) -> Unit,
    dateFilter: ExplorerDateFilter,
    onDateFilterChanged: (ExplorerDateFilter) -> Unit,
    sizeFilter: ExplorerSizeFilter,
    onSizeFilterChanged: (ExplorerSizeFilter) -> Unit,
    favoriteOnly: Boolean,
    onFavoriteOnlyToggle: () -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var dateMenuExpanded by remember { mutableStateOf(false) }
    var sizeMenuExpanded by remember { mutableStateOf(false) }
    val hasActiveFilters = subtypeFilter != "ALL" ||
        dateFilter != ExplorerDateFilter.ALL ||
        sizeFilter != ExplorerSizeFilter.ALL ||
        favoriteOnly ||
        sortOption != SortOption.DATE_DESC ||
        groupBy != GroupByOption.NONE

    val subtypeOptions = remember(category) {
        when (category) {
            CategoryType.IMAGES -> listOf("ALL", "JPG", "PNG", "WEBP", "GIF", "SVG / RAW")
            CategoryType.VIDEOS -> listOf("ALL", "MP4", "MKV", "AVI", "MOV", "3GP / WebM")
            CategoryType.AUDIO -> listOf("ALL", "MP3", "WAV", "AAC", "FLAC", "M4A / OGG")
            CategoryType.DOCUMENTS -> listOf("ALL", "PDF", "DOC/DOCX", "XLS/XLSX", "PPT/PPTX", "TXT", "EPUB")
            CategoryType.ARCHIVES -> listOf("ALL", "ZIP", "RAR", "7Z", "TAR / GZ")
            CategoryType.APKS -> listOf("ALL", "APK", "XAPK", "APKS")
            CategoryType.DOWNLOADS -> listOf("ALL", "Images", "Videos", "Audio", "Documents", "Archives", "APKs")
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Sort Chip
        item {
            Box {
                FilterChip(
                    selected = sortOption != SortOption.DATE_DESC,
                    onClick = { sortMenuExpanded = true },
                    label = { Text("Sort: ${sortOption.title}") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    SortOption.entries.forEach { option ->
                        val isSelected = sortOption == option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.title,
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
                                onSortSelected(option)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 2. Group By Chip
        item {
            Box {
                FilterChip(
                    selected = groupBy != GroupByOption.NONE,
                    onClick = { groupMenuExpanded = true },
                    label = { Text("Group: ${groupBy.label}") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                DropdownMenu(
                    expanded = groupMenuExpanded,
                    onDismissRequest = { groupMenuExpanded = false }
                ) {
                    GroupByOption.entries.forEach { option ->
                        val isSelected = groupBy == option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
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
                                onGroupBySelected(option)
                                groupMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 3. Subtype chips
        items(subtypeOptions) { opt ->
            FilterChip(
                selected = subtypeFilter == opt,
                onClick = { onSubtypeFilterChanged(opt) },
                label = { Text(if (opt == "ALL") "All Types" else opt) }
            )
        }

        // 4. Date Filter Chip
        item {
            Box {
                FilterChip(
                    selected = dateFilter != ExplorerDateFilter.ALL,
                    onClick = { dateMenuExpanded = true },
                    label = {
                        Text(if (dateFilter == ExplorerDateFilter.ALL) "Date" else "Date: ${dateFilter.label}")
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                DropdownMenu(
                    expanded = dateMenuExpanded,
                    onDismissRequest = { dateMenuExpanded = false }
                ) {
                    ExplorerDateFilter.entries.forEach { option ->
                        val isSelected = dateFilter == option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
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
                                onDateFilterChanged(option)
                                dateMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 5. Size Filter Chip
        item {
            Box {
                FilterChip(
                    selected = sizeFilter != ExplorerSizeFilter.ALL,
                    onClick = { sizeMenuExpanded = true },
                    label = {
                        Text(if (sizeFilter == ExplorerSizeFilter.ALL) "Size" else "Size: ${sizeFilter.label}")
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                DropdownMenu(
                    expanded = sizeMenuExpanded,
                    onDismissRequest = { sizeMenuExpanded = false }
                ) {
                    ExplorerSizeFilter.entries.forEach { option ->
                        val isSelected = sizeFilter == option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
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
                                onSizeFilterChanged(option)
                                sizeMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 6. Starred / Favorites Chip
        item {
            FilterChip(
                selected = favoriteOnly,
                onClick = onFavoriteOnlyToggle,
                label = { Text("★ Starred") }
            )
        }

        // 7. Clear Filters Chip
        if (hasActiveFilters) {
            item {
                FilterChip(
                    selected = false,
                    onClick = onResetFilters,
                    label = { Text("Reset Filters") },
                    leadingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun RecycleBinFilterChipsBar(
    sortOption: SortOption,
    onSortSelected: (SortOption) -> Unit,
    groupBy: GroupByOption,
    onGroupBySelected: (GroupByOption) -> Unit,
    typeFilter: String,
    onTypeFilterChanged: (String) -> Unit,
    dateFilter: ExplorerDateFilter,
    onDateFilterChanged: (ExplorerDateFilter) -> Unit,
    sizeFilter: ExplorerSizeFilter,
    onSizeFilterChanged: (ExplorerSizeFilter) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var dateMenuExpanded by remember { mutableStateOf(false) }
    var sizeMenuExpanded by remember { mutableStateOf(false) }
    val hasActiveFilters = typeFilter != "ALL" ||
        dateFilter != ExplorerDateFilter.ALL ||
        sizeFilter != ExplorerSizeFilter.ALL ||
        sortOption != SortOption.DATE_DESC ||
        groupBy != GroupByOption.NONE

    val typeOptions = listOf("ALL", "Folders", "Files", "Large (>10MB)")

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Sort Chip
        item {
            Box {
                FilterChip(
                    selected = sortOption != SortOption.DATE_DESC,
                    onClick = { sortMenuExpanded = true },
                    label = { Text("Sort: ${sortOption.title}") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    SortOption.entries.forEach { option ->
                        val isSelected = sortOption == option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.title,
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
                                onSortSelected(option)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 2. Group By Chip
        item {
            Box {
                FilterChip(
                    selected = groupBy != GroupByOption.NONE,
                    onClick = { groupMenuExpanded = true },
                    label = { Text("Group: ${groupBy.label}") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                DropdownMenu(
                    expanded = groupMenuExpanded,
                    onDismissRequest = { groupMenuExpanded = false }
                ) {
                    GroupByOption.entries.forEach { option ->
                        val isSelected = groupBy == option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
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
                                onGroupBySelected(option)
                                groupMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 3. Type Chips
        items(typeOptions) { opt ->
            FilterChip(
                selected = typeFilter == opt,
                onClick = { onTypeFilterChanged(opt) },
                label = { Text(if (opt == "ALL") "All Types" else opt) }
            )
        }

        // 4. Date Deleted Chip
        item {
            Box {
                FilterChip(
                    selected = dateFilter != ExplorerDateFilter.ALL,
                    onClick = { dateMenuExpanded = true },
                    label = {
                        Text(if (dateFilter == ExplorerDateFilter.ALL) "Deleted: Any time" else "Deleted: ${dateFilter.label}")
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                DropdownMenu(
                    expanded = dateMenuExpanded,
                    onDismissRequest = { dateMenuExpanded = false }
                ) {
                    ExplorerDateFilter.entries.forEach { option ->
                        val isSelected = dateFilter == option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
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
                                onDateFilterChanged(option)
                                dateMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 5. Size Filter Chip
        item {
            Box {
                FilterChip(
                    selected = sizeFilter != ExplorerSizeFilter.ALL,
                    onClick = { sizeMenuExpanded = true },
                    label = {
                        Text(if (sizeFilter == ExplorerSizeFilter.ALL) "Size: Any" else "Size: ${sizeFilter.label}")
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                DropdownMenu(
                    expanded = sizeMenuExpanded,
                    onDismissRequest = { sizeMenuExpanded = false }
                ) {
                    ExplorerSizeFilter.entries.forEach { option ->
                        val isSelected = sizeFilter == option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
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
                                onSizeFilterChanged(option)
                                sizeMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 6. Reset Filters Chip
        if (hasActiveFilters) {
            item {
                FilterChip(
                    selected = false,
                    onClick = onResetFilters,
                    label = { Text("Reset Filters") },
                    leadingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun CategoryFileGridCard(
    item: FileItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("category_grid_item_${item.name}")
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if ((item.isImage || item.isVideo) && item.uri != null) {
                    val context = LocalContext.current
                    val thumbRequest = remember(item.uri) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(280, 280)
                            .crossfade(true)
                            .allowHardware(true)
                            .build()
                    }
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (item.isVideo) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Video",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else {
                    FileTypeIconBadge(item = item, modifier = Modifier.size(48.dp))
                }

                // Extension format badge in top-right
                val ext = item.name.substringAfterLast('.', "").uppercase()
                if (ext.isNotBlank() && ext.length <= 5) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = ext,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Star badge in top-left
                if (item.isFavorite) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Starred",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier
                                .padding(4.dp)
                                .size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatFileSize(item.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecycleBinGridCard(
    item: TrashEntity,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recycle_bin_grid_item_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isDirectory) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = formatFileSize(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onRestore, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Restore",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDeleteForever, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Delete Forever",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilesContent(
    files: List<FileItem>,
    viewMode: ViewMode,
    groupBy: GroupByOption,
    categoryType: CategoryType? = null,
    onFileClick: (FileItem) -> Unit
) {
    val grouped = remember(files, groupBy, categoryType) {
        groupFiles(files, groupBy, categoryType)
    }

    if (viewMode == ViewMode.GRID) {
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            state = gridState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            grouped.forEach { (header, itemsInGroup) ->
                if (header.isNotBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header_$header") {
                        Text(
                            text = "$header (${itemsInGroup.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                items(
                    items = itemsInGroup,
                    key = { it.path }
                ) { item ->
                    CategoryFileGridCard(
                        item = item,
                        onClick = { onFileClick(item) }
                    )
                }
            }
        }
    } else if (viewMode == ViewMode.DETAILED_LIST) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            grouped.forEach { (header, itemsInGroup) ->
                if (header.isNotBlank()) {
                    item(key = "header_$header") {
                        Text(
                            text = "$header (${itemsInGroup.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                items(
                    items = itemsInGroup,
                    key = { it.path }
                ) { item ->
                    CategoryFileCard(
                        item = item,
                        onClick = { onFileClick(item) }
                    )
                }
            }
        }
    } else {
        // Compact List View
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            grouped.forEach { (header, itemsInGroup) ->
                if (header.isNotBlank()) {
                    item(key = "header_$header") {
                        Text(
                            text = "$header (${itemsInGroup.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                items(
                    items = itemsInGroup,
                    key = { it.path }
                ) { item ->
                    CategoryFileCompactRow(
                        item = item,
                        onClick = { onFileClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecycleBinContent(
    trashItems: List<TrashEntity>,
    viewMode: ViewMode,
    groupBy: GroupByOption,
    onRestore: (TrashEntity) -> Unit,
    onDeleteForever: (TrashEntity) -> Unit
) {
    val grouped = remember(trashItems, groupBy) {
        groupTrash(trashItems, groupBy)
    }

    if (viewMode == ViewMode.GRID) {
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            state = gridState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            grouped.forEach { (header, itemsInGroup) ->
                if (header.isNotBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "trash_header_$header") {
                        Text(
                            text = "$header (${itemsInGroup.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                items(
                    items = itemsInGroup,
                    key = { it.id }
                ) { item ->
                    RecycleBinGridCard(
                        item = item,
                        onRestore = { onRestore(item) },
                        onDeleteForever = { onDeleteForever(item) }
                    )
                }
            }
        }
    } else if (viewMode == ViewMode.DETAILED_LIST) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            grouped.forEach { (header, itemsInGroup) ->
                if (header.isNotBlank()) {
                    item(key = "trash_header_$header") {
                        Text(
                            text = "$header (${itemsInGroup.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                items(
                    items = itemsInGroup,
                    key = { it.id }
                ) { item ->
                    RecycleBinPageCard(
                        item = item,
                        onRestore = { onRestore(item) },
                        onDeleteForever = { onDeleteForever(item) }
                    )
                }
            }
        }
    } else {
        // Compact List View for Trash
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            grouped.forEach { (header, itemsInGroup) ->
                if (header.isNotBlank()) {
                    item(key = "trash_header_$header") {
                        Text(
                            text = "$header (${itemsInGroup.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                items(
                    items = itemsInGroup,
                    key = { it.id }
                ) { item ->
                    RecycleBinCompactRow(
                        item = item,
                        onRestore = { onRestore(item) },
                        onDeleteForever = { onDeleteForever(item) }
                    )
                }
            }
        }
    }
}

private fun filterFiles(
    files: List<FileItem>,
    query: String,
    dateFilter: ExplorerDateFilter,
    sizeFilter: ExplorerSizeFilter,
    subtypeFilter: String = "ALL",
    favoritesOnly: Boolean = false,
    categoryType: CategoryType? = null
): List<FileItem> {
    val now = System.currentTimeMillis()
    return files.filter { item ->
        if (query.isNotBlank() && !item.name.contains(query, ignoreCase = true)) {
            return@filter false
        }
        if (favoritesOnly && !item.isFavorite) {
            return@filter false
        }
        val matchesDate = when (dateFilter) {
            ExplorerDateFilter.ALL -> true
            ExplorerDateFilter.TODAY -> (now - item.lastModified) <= 86_400_000L
            ExplorerDateFilter.LAST_7_DAYS -> (now - item.lastModified) <= 7 * 86_400_000L
            ExplorerDateFilter.THIS_MONTH -> (now - item.lastModified) <= 30 * 86_400_000L
            ExplorerDateFilter.THIS_YEAR -> (now - item.lastModified) <= 365 * 86_400_000L
        }
        if (!matchesDate) return@filter false
        val matchesSize = when (sizeFilter) {
            ExplorerSizeFilter.ALL -> true
            ExplorerSizeFilter.SMALL -> item.size < 1_048_576L
            ExplorerSizeFilter.MEDIUM -> item.size in 1_048_576L..52_428_800L
            ExplorerSizeFilter.LARGE -> item.size > 52_428_800L
        }
        if (!matchesSize) return@filter false

        if (subtypeFilter != "ALL") {
            val ext = item.extension.lowercase()
            val matchesSubtype = when (subtypeFilter) {
                // Images
                "JPG" -> ext in listOf("jpg", "jpeg")
                "PNG" -> ext == "png"
                "WEBP" -> ext == "webp"
                "GIF" -> ext == "gif"
                "SVG / RAW" -> ext in listOf("svg", "dng", "cr2", "nef", "arw", "heic", "heif")
                // Videos
                "MP4" -> ext == "mp4"
                "MKV" -> ext == "mkv"
                "AVI" -> ext == "avi"
                "MOV" -> ext == "mov"
                "3GP / WebM" -> ext in listOf("3gp", "webm", "flv", "ts")
                // Audio
                "MP3" -> ext == "mp3"
                "WAV" -> ext == "wav"
                "AAC" -> ext == "aac"
                "FLAC" -> ext == "flac"
                "M4A / OGG" -> ext in listOf("m4a", "ogg", "opus", "wma")
                // Documents
                "PDF" -> ext == "pdf"
                "DOC/DOCX" -> ext in listOf("doc", "docx", "odt", "rtf")
                "XLS/XLSX" -> ext in listOf("xls", "xlsx", "ods", "csv")
                "PPT/PPTX" -> ext in listOf("ppt", "pptx", "odp")
                "TXT" -> ext in listOf("txt", "md", "log", "json", "xml", "html")
                "EPUB" -> ext in listOf("epub", "mobi", "azw3")
                // Archives
                "ZIP" -> ext == "zip"
                "RAR" -> ext == "rar"
                "7Z" -> ext == "7z"
                "TAR / GZ" -> ext in listOf("tar", "gz", "bz2", "xz", "tgz")
                // APKs
                "APK" -> ext == "apk"
                "XAPK" -> ext == "xapk"
                "APKS" -> ext == "apks"
                // Downloads categories
                "Images" -> item.isImage
                "Videos" -> item.isVideo
                "Audio" -> item.isAudio
                "Documents" -> item.isDocument
                "Archives" -> item.isArchive
                "APKs" -> item.isApk
                else -> ext.equals(subtypeFilter, ignoreCase = true)
            }
            if (!matchesSubtype) return@filter false
        }
        true
    }
}

private fun groupFiles(
    files: List<FileItem>,
    groupBy: GroupByOption,
    categoryType: CategoryType? = null
): Map<String, List<FileItem>> {
    val now = System.currentTimeMillis()
    return when (groupBy) {
        GroupByOption.NONE -> mapOf("" to files)
        GroupByOption.DATE -> files.groupBy { item ->
            val diff = now - item.lastModified
            when {
                diff <= 86_400_000L -> "Today"
                diff <= 2 * 86_400_000L -> "Yesterday"
                diff <= 7 * 86_400_000L -> "Last 7 Days"
                diff <= 30 * 86_400_000L -> "This Month"
                diff <= 365 * 86_400_000L -> "Earlier this Year"
                else -> "Older"
            }
        }
        GroupByOption.TYPE -> {
            if (categoryType != null && categoryType != CategoryType.DOWNLOADS) {
                // Inside specific category, group by format extension!
                files.groupBy { item ->
                    val ext = item.extension.uppercase()
                    if (ext.isNotBlank()) ".$ext Format" else "Other Format"
                }
            } else {
                files.groupBy { item ->
                    when {
                        item.isDirectory -> "Folders"
                        item.isImage -> "Images"
                        item.isVideo -> "Videos"
                        item.isAudio -> "Audio"
                        item.isDocument -> "Documents"
                        item.isArchive -> "Archives"
                        item.isApk -> "APKs"
                        else -> "Other Files"
                    }
                }
            }
        }
        GroupByOption.SIZE -> files.groupBy { item ->
            when {
                item.isDirectory -> "Folders"
                item.size > 100 * 1024 * 1024L -> "Large (> 100 MB)"
                item.size > 10 * 1024 * 1024L -> "Medium (10 MB – 100 MB)"
                item.size > 1024 * 1024L -> "Small (1 MB – 10 MB)"
                else -> "Tiny (< 1 MB)"
            }
        }
    }
}

private fun filterTrash(
    trash: List<TrashEntity>,
    query: String,
    dateFilter: ExplorerDateFilter,
    sizeFilter: ExplorerSizeFilter,
    typeFilter: String = "ALL"
): List<TrashEntity> {
    val now = System.currentTimeMillis()
    return trash.filter { item ->
        if (query.isNotBlank() && !item.name.contains(query, ignoreCase = true)) {
            return@filter false
        }
        val matchesDate = when (dateFilter) {
            ExplorerDateFilter.ALL -> true
            ExplorerDateFilter.TODAY -> (now - item.deletedTimestamp) <= 86_400_000L
            ExplorerDateFilter.LAST_7_DAYS -> (now - item.deletedTimestamp) <= 7 * 86_400_000L
            ExplorerDateFilter.THIS_MONTH -> (now - item.deletedTimestamp) <= 30 * 86_400_000L
            ExplorerDateFilter.THIS_YEAR -> (now - item.deletedTimestamp) <= 365 * 86_400_000L
        }
        if (!matchesDate) return@filter false
        val matchesSize = when (sizeFilter) {
            ExplorerSizeFilter.ALL -> true
            ExplorerSizeFilter.SMALL -> item.size < 1_048_576L
            ExplorerSizeFilter.MEDIUM -> item.size in 1_048_576L..52_428_800L
            ExplorerSizeFilter.LARGE -> item.size > 52_428_800L
        }
        if (!matchesSize) return@filter false

        val matchesType = when (typeFilter) {
            "ALL" -> true
            "Folders" -> item.isDirectory
            "Files" -> !item.isDirectory
            "Large (>10MB)" -> item.size > 10 * 1024 * 1024L
            else -> true
        }
        matchesType
    }
}

private fun sortTrash(trash: List<TrashEntity>, option: SortOption): List<TrashEntity> {
    return when (option) {
        SortOption.NAME_ASC -> trash.sortedBy { it.name.lowercase() }
        SortOption.NAME_DESC -> trash.sortedByDescending { it.name.lowercase() }
        SortOption.DATE_DESC -> trash.sortedByDescending { it.deletedTimestamp }
        SortOption.DATE_ASC -> trash.sortedBy { it.deletedTimestamp }
        SortOption.SIZE_DESC -> trash.sortedByDescending { it.size }
        SortOption.SIZE_ASC -> trash.sortedBy { it.size }
        SortOption.TYPE -> trash.sortedBy { if (it.isDirectory) "0" else "1" + it.name.substringAfterLast(".", "") }
    }
}

private fun groupTrash(trash: List<TrashEntity>, groupBy: GroupByOption): Map<String, List<TrashEntity>> {
    val now = System.currentTimeMillis()
    return when (groupBy) {
        GroupByOption.NONE -> mapOf("" to trash)
        GroupByOption.DATE -> trash.groupBy { item ->
            val diff = now - item.deletedTimestamp
            when {
                diff <= 86_400_000L -> "Today"
                diff <= 2 * 86_400_000L -> "Yesterday"
                diff <= 7 * 86_400_000L -> "Last 7 Days"
                diff <= 30 * 86_400_000L -> "This Month"
                diff <= 365 * 86_400_000L -> "Earlier this Year"
                else -> "Older"
            }
        }
        GroupByOption.TYPE -> trash.groupBy { item ->
            if (item.isDirectory) "Folders" else "Files"
        }
        GroupByOption.SIZE -> trash.groupBy { item ->
            when {
                item.isDirectory -> "Folders"
                item.size > 100 * 1024 * 1024L -> "Large (> 100 MB)"
                item.size > 10 * 1024 * 1024L -> "Medium (10 MB – 100 MB)"
                item.size > 1024 * 1024L -> "Small (1 MB – 10 MB)"
                else -> "Tiny (< 1 MB)"
            }
        }
    }
}


