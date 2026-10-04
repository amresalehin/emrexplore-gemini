package com.example.ui.screens

import android.content.Intent
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.example.ui.viewmodel.ExplorerTab
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import coil.request.ImageRequest
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.os.Build
import androidx.compose.material3.Button
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.components.isAllFilesAccessGranted
import com.example.ui.components.openAllFilesAccessSettings
import com.example.data.model.FileItem
import com.example.data.model.SortOption
import com.example.data.model.ViewMode
import com.example.data.model.ExplorerFilterType
import com.example.data.model.ExplorerDateFilter
import com.example.data.model.ExplorerSizeFilter
import com.example.data.model.ExplorerSearchScope
import com.example.ui.components.BreadcrumbsRow
import com.example.ui.components.FileTypeIconBadge
import com.example.ui.components.PasteActionBar
import com.example.ui.components.formatDate
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ColorFolders
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UnifiedViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorerScreen(
    uiState: UiState,
    viewModel: UnifiedViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Dialog states
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTargetItem by remember { mutableStateOf<FileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    var showZipDialog by remember { mutableStateOf(false) }
    var zipArchiveName by remember { mutableStateOf("") }

    var showSortMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showDateFilterMenu by remember { mutableStateOf(false) }
    var showSizeFilterMenu by remember { mutableStateOf(false) }
    var activeMenuItem by remember { mutableStateOf<FileItem?>(null) }

    val hasActiveFilters = uiState.explorerFilterType != ExplorerFilterType.ALL ||
        uiState.explorerDateFilter != ExplorerDateFilter.ALL ||
        uiState.explorerSizeFilter != ExplorerSizeFilter.ALL

    val isSearchOrFilterActive = uiState.searchQuery.isNotBlank() || hasActiveFilters

    val displayFiles = if (isSearchOrFilterActive) {
        uiState.explorerSearchResults
    } else {
        uiState.files
    }

    // Intercept back button when search or filters are active
    BackHandler(enabled = (uiState.explorerSearchActive || uiState.searchQuery.isNotEmpty() || hasActiveFilters) && !uiState.isSelectionMode) {
        viewModel.resetExplorerSearchAndFilters()
    }

    // Intercept hardware back button when inside a subfolder
    BackHandler(enabled = viewModel.canNavigateUp() && !uiState.isSelectionMode && !uiState.explorerSearchActive && uiState.searchQuery.isEmpty() && !hasActiveFilters) {
        val navigated = viewModel.navigateUp()
        if (!navigated) {
            // Let default handler run
        }
    }

    // Intercept back button when selection mode is active
    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Header & Search Bar
            if (uiState.isSelectionMode) {
                // Multi-selection bar
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Selection")
                        }
                        Text(
                            text = "${uiState.selectedPaths.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { viewModel.copySelected() }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = { viewModel.cutSelected() }) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Cut")
                        }
                        IconButton(onClick = {
                            zipArchiveName = "Archive"
                            showZipDialog = true
                        }) {
                            Icon(Icons.Default.Archive, contentDescription = "Compress")
                        }
                        IconButton(onClick = { viewModel.deleteSelected(toTrash = true) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                // Modern Material 3 Header & Integrated Search/Filters
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        if (uiState.explorerSearchActive || uiState.searchQuery.isNotEmpty()) {
                            // --- Modern Material 3 Search Bar Header ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    tonalElevation = 3.dp,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = {
                                            viewModel.resetExplorerSearchAndFilters()
                                        }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Exit search",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        TextField(
                                            value = uiState.searchQuery,
                                            onValueChange = { viewModel.setExplorerSearchQuery(it) },
                                            placeholder = {
                                                val placeholder = when (uiState.explorerSearchScope) {
                                                    ExplorerSearchScope.CURRENT_FOLDER -> "Search in this folder..."
                                                    ExplorerSearchScope.SUBFOLDERS -> "Search this folder & subfolders..."
                                                    ExplorerSearchScope.ALL_STORAGE -> "Search all files & storage..."
                                                }
                                                Text(
                                                    text = placeholder,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                            )
                                        )

                                        if (uiState.searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.clearExplorerSearch() }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear search query",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // --- Search Scope Segmented Pills ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ExplorerSearchScope.entries.forEach { scopeOption ->
                                    val isSelected = uiState.explorerSearchScope == scopeOption
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { viewModel.setExplorerSearchScope(scopeOption) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val scopeIcon = when (scopeOption) {
                                                ExplorerSearchScope.CURRENT_FOLDER -> Icons.Default.Folder
                                                ExplorerSearchScope.SUBFOLDERS -> Icons.Default.FolderOpen
                                                ExplorerSearchScope.ALL_STORAGE -> Icons.Default.Storage
                                            }
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.Check else scopeIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = scopeOption.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // --- Standard Modern Material 3 Header ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val canNavigateUp = uiState.currentPath.isNotBlank() && uiState.currentPath != "/" && uiState.currentPath != "/storage/emulated/0"
                                if (canNavigateUp) {
                                    IconButton(
                                        onClick = { viewModel.navigateUp() },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Navigate up",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    ExplorerTabSegmentedSwitcher(
                                        tabs = uiState.explorerTabs,
                                        activeTabId = uiState.activeExplorerTabId,
                                        onSelectTab = { tabId -> viewModel.switchExplorerTab(tabId) },
                                        onCloseTab = { tabId -> viewModel.closeExplorerTab(tabId) },
                                        onNewTab = { viewModel.openNewExplorerTab() }
                                    )
                                }

                                // Search Button
                                IconButton(onClick = { viewModel.setExplorerSearchActive(true) }) {
                                    BadgedBox(
                                        badge = {
                                            if (uiState.searchQuery.isNotEmpty()) {
                                                Badge(containerColor = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Search files",
                                            tint = if (uiState.searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // Filter Bar Toggle
                                IconButton(onClick = { viewModel.toggleExplorerFilterBar() }) {
                                    BadgedBox(
                                        badge = {
                                            if (hasActiveFilters) {
                                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                                    Text("•")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.FilterList,
                                            contentDescription = "Toggle filters",
                                            tint = if (hasActiveFilters || uiState.explorerFilterBarVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // Sort Menu
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        SortOption.entries.forEach { option ->
                                            val isCurrent = uiState.sortOption == option
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = option.title,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                leadingIcon = {
                                                    if (isCurrent) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setSortOption(option)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Overflow Menu
                                Box {
                                    IconButton(onClick = { showOptionsMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                                    }
                                    DropdownMenu(
                                        expanded = showOptionsMenu,
                                        onDismissRequest = { showOptionsMenu = false }
                                    ) {
                                        // Grid / List View Toggle
                                        DropdownMenuItem(
                                            text = {
                                                Text(if (uiState.viewMode == ViewMode.GRID) "List view" else "Grid view")
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (uiState.viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                val nextMode = if (uiState.viewMode == ViewMode.GRID) ViewMode.DETAILED_LIST else ViewMode.GRID
                                                viewModel.setViewMode(nextMode)
                                                showOptionsMenu = false
                                            }
                                        )

                                        // New tab
                                        DropdownMenuItem(
                                            text = { Text("New tab") },
                                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                            onClick = {
                                                viewModel.openNewExplorerTab()
                                                showOptionsMenu = false
                                            }
                                        )

                                        // Show/Hide hidden files (functional)
                                        DropdownMenuItem(
                                            text = { Text(if (uiState.showHidden) "Hide hidden files" else "Show hidden files") },
                                            leadingIcon = {
                                                Icon(
                                                    if (uiState.showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                viewModel.toggleShowHidden()
                                                showOptionsMenu = false
                                            }
                                        )

                                        // Remember Last Folder Preference
                                        DropdownMenuItem(
                                            text = {
                                                Text(if (uiState.explorerPreferences.rememberLastDirectory) "Remember folder (On)" else "Remember folder (Off)")
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.History, contentDescription = null)
                                            },
                                            onClick = {
                                                viewModel.toggleRememberLastDirectory()
                                                showOptionsMenu = false
                                            }
                                        )

                                        // Fast SQLite Room Search Preference
                                        DropdownMenuItem(
                                            text = {
                                                Text(if (uiState.isFastSearchRoomPowered) "Fast search: Room (On)" else "Fast search: Disk (Off)")
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Bolt, contentDescription = null)
                                            },
                                            onClick = {
                                                viewModel.toggleFastSearch()
                                                showOptionsMenu = false
                                            }
                                        )

                                        // Re-index Local Storage
                                        DropdownMenuItem(
                                            text = { Text(if (uiState.isIndexing) "Indexing storage..." else "Re-index storage") },
                                            leadingIcon = {
                                                if (uiState.isIndexing) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Default.Storage, contentDescription = null)
                                                }
                                            },
                                            enabled = !uiState.isIndexing,
                                            onClick = {
                                                viewModel.reindexStorage(force = true)
                                                showOptionsMenu = false
                                            }
                                        )

                                        HorizontalDivider()

                                        DropdownMenuItem(
                                            text = { Text("New folder") },
                                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                            onClick = {
                                                newFolderName = ""
                                                showNewFolderDialog = true
                                                showOptionsMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("New file") },
                                            leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
                                            onClick = {
                                                newFileName = ""
                                                showNewFileDialog = true
                                                showOptionsMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Select items") },
                                            leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                                            onClick = {
                                                viewModel.toggleSelectionMode(true)
                                                showOptionsMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Refresh") },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                            onClick = {
                                                viewModel.loadFiles()
                                                showOptionsMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Recycle Bin (${uiState.trashList.size})") },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                viewModel.openRecycleBin()
                                                showOptionsMenu = false
                                            }
                                        )

                                        val isAllFilesGranted = isAllFilesAccessGranted()
                                        if (!isAllFilesGranted) {
                                            DropdownMenuItem(
                                                text = { Text("Grant storage permission") },
                                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                                                onClick = {
                                                    openAllFilesAccessSettings(context)
                                                    showOptionsMenu = false
                                                }
                                            )
                                        }

                                        HorizontalDivider()

                                        DropdownMenuItem(
                                            text = { Text("Reset preferences") },
                                            leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                                            onClick = {
                                                viewModel.resetPreferences()
                                                showOptionsMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Breadcrumb Navigation Row (always visible except in all-storage deep search)
                        if (uiState.explorerSearchScope != ExplorerSearchScope.ALL_STORAGE || uiState.searchQuery.isEmpty()) {
                            BreadcrumbsRow(
                                currentPath = uiState.currentPath,
                                onNavigate = { path -> viewModel.navigateToDirectory(path) }
                            )
                        }

                        // --- Quick Filter Chips Bar ---
                        val filterBarShouldShow = uiState.explorerFilterBarVisible
                        AnimatedVisibility(visible = filterBarShouldShow) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Category filter chips
                                items(ExplorerFilterType.entries) { filterType ->
                                    val isSelected = uiState.explorerFilterType == filterType
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.setExplorerFilterType(filterType)
                                            if (filterType == ExplorerFilterType.ALL && uiState.explorerDateFilter == ExplorerDateFilter.ALL && uiState.explorerSizeFilter == ExplorerSizeFilter.ALL) {
                                                viewModel.toggleExplorerFilterBar(false)
                                            }
                                        },
                                        label = { Text(filterType.label) },
                                        leadingIcon = {
                                            val icon = when (filterType) {
                                                ExplorerFilterType.ALL -> Icons.Default.Clear
                                                ExplorerFilterType.FOLDERS -> Icons.Default.Folder
                                                ExplorerFilterType.DOCUMENTS -> Icons.Default.Description
                                                ExplorerFilterType.IMAGES -> Icons.Default.Image
                                                ExplorerFilterType.VIDEOS -> Icons.Default.Movie
                                                ExplorerFilterType.AUDIO -> Icons.Default.AudioFile
                                                ExplorerFilterType.ARCHIVES -> Icons.Default.Archive
                                                ExplorerFilterType.APKS -> Icons.Default.VideogameAsset
                                            }
                                            if (filterType != ExplorerFilterType.ALL || isSelected) {
                                                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }

                                // Date Filter Dropdown Chip
                                item {
                                    Box {
                                        FilterChip(
                                            selected = uiState.explorerDateFilter != ExplorerDateFilter.ALL,
                                            onClick = { showDateFilterMenu = true },
                                            label = {
                                                Text(if (uiState.explorerDateFilter == ExplorerDateFilter.ALL) "Date" else uiState.explorerDateFilter.label)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                        DropdownMenu(
                                            expanded = showDateFilterMenu,
                                            onDismissRequest = { showDateFilterMenu = false }
                                        ) {
                                            ExplorerDateFilter.entries.forEach { option ->
                                                val isCurrent = uiState.explorerDateFilter == option
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            option.label,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        if (isCurrent) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                        }
                                                    },
                                                    onClick = {
                                                        viewModel.setExplorerDateFilter(option)
                                                        showDateFilterMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Size Filter Dropdown Chip
                                item {
                                    Box {
                                        FilterChip(
                                            selected = uiState.explorerSizeFilter != ExplorerSizeFilter.ALL,
                                            onClick = { showSizeFilterMenu = true },
                                            label = {
                                                Text(if (uiState.explorerSizeFilter == ExplorerSizeFilter.ALL) "Size" else uiState.explorerSizeFilter.label)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.DataUsage, contentDescription = null, modifier = Modifier.size(16.dp))
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                        DropdownMenu(
                                            expanded = showSizeFilterMenu,
                                            onDismissRequest = { showSizeFilterMenu = false }
                                        ) {
                                            ExplorerSizeFilter.entries.forEach { option ->
                                                val isCurrent = uiState.explorerSizeFilter == option
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            option.label,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        if (isCurrent) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                        }
                                                    },
                                                    onClick = {
                                                        viewModel.setExplorerSizeFilter(option)
                                                        showSizeFilterMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Reset filters chip
                                if (hasActiveFilters) {
                                    item {
                                        FilterChip(
                                            selected = false,
                                            onClick = { viewModel.clearExplorerFilters() },
                                            label = { Text("Reset Filters", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // --- Status / Search Results Info Bar ---
                        if (isSearchOrFilterActive) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (uiState.isExplorerSearching) "Searching files..." else "${displayFiles.size} items found",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (uiState.isFastSearchRoomPowered && uiState.explorerSearchScope != ExplorerSearchScope.CURRENT_FOLDER) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = "Indexed",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                if (hasActiveFilters || uiState.searchQuery.isNotEmpty()) {
                                    Text(
                                        text = "Clear all",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { viewModel.resetExplorerSearchAndFilters() }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (uiState.isExplorerSearching) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        } else if (uiState.hasMorePages) {
                            // Paging status banner in normal folder view
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Loaded ${uiState.files.size} of ${uiState.totalFilesInFolder} items",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Scroll for more",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isLoadingFiles && displayFiles.isEmpty() && !isSearchOrFilterActive) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.isExplorerSearching && displayFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                        Text(
                            text = "Searching files...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (displayFiles.isEmpty()) {
                val needsAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isAllFilesAccessGranted()
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
                            imageVector = if (needsAllFiles) Icons.Default.Storage else if (isSearchOrFilterActive) Icons.Default.SearchOff else Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (needsAllFiles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (needsAllFiles) {
                                "All Files Access Required"
                            } else if (isSearchOrFilterActive) {
                                if (uiState.searchQuery.isNotBlank()) "No files match '${uiState.searchQuery}'" else "No files match active filters"
                            } else {
                                "Folder is empty"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (needsAllFiles) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (needsAllFiles) {
                                "Grant All Files Access permission to browse and manage files across your device storage."
                            } else if (isSearchOrFilterActive) {
                                "Try adjusting your search query, changing filters, or selecting 'Subfolders' or 'All Storage'."
                            } else {
                                "Tap + to create a new folder or file"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        if (needsAllFiles) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { openAllFilesAccessSettings(context) },
                                modifier = Modifier.testTag("empty_state_grant_all_files_button")
                            ) {
                                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grant All Files Access")
                            }
                        } else if (isSearchOrFilterActive) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { viewModel.resetExplorerSearchAndFilters() }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clear search & filters")
                            }
                        }
                    }
                }
            } else {
                if (uiState.isLoadingFiles) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // Content based on ViewMode
                if (uiState.viewMode == ViewMode.GRID) {
                    val gridState = rememberLazyGridState()

                    LaunchedEffect(gridState, displayFiles.size, uiState.hasMorePages) {
                        snapshotFlow {
                            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val total = gridState.layoutInfo.totalItemsCount
                            last >= total - 18
                        }.collect { nearEnd ->
                            if (nearEnd && uiState.hasMorePages && !uiState.isLoadingNextPage && uiState.searchQuery.isBlank()) {
                                viewModel.loadNextPage()
                            }
                        }
                    }

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = displayFiles,
                            key = { it.path },
                            contentType = { if (it.isDirectory) "folder" else "file" }
                        ) { item ->
                            val isSelected = uiState.selectedPaths.contains(item.path)
                            FileGridCard(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = uiState.isSelectionMode,
                                isSearchMode = isSearchOrFilterActive,
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleItemSelection(item.path)
                                    } else {
                                        if (item.isDirectory) {
                                            viewModel.navigateToDirectory(item.path)
                                        } else {
                                            viewModel.openFile(item)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!uiState.isSelectionMode) {
                                        viewModel.toggleSelectionMode(true)
                                    }
                                    viewModel.toggleItemSelection(item.path)
                                }
                            )
                        }

                        if (uiState.isLoadingNextPage) {
                            item(span = { GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val listState = rememberLazyListState()

                    LaunchedEffect(listState, displayFiles.size, uiState.hasMorePages) {
                        snapshotFlow {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val total = listState.layoutInfo.totalItemsCount
                            last >= total - 15
                        }.collect { nearEnd ->
                            if (nearEnd && uiState.hasMorePages && !uiState.isLoadingNextPage && uiState.searchQuery.isBlank()) {
                                viewModel.loadNextPage()
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = displayFiles,
                            key = { it.path },
                            contentType = { if (it.isDirectory) "folder" else "file" }
                        ) { item ->
                            val isSelected = uiState.selectedPaths.contains(item.path)

                            FileListItem(
                                item = item,
                                isCompact = uiState.viewMode == ViewMode.COMPACT_LIST,
                                isSelected = isSelected,
                                isSelectionMode = uiState.isSelectionMode,
                                isSearchMode = isSearchOrFilterActive,
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleItemSelection(item.path)
                                    } else {
                                        if (item.isDirectory) {
                                            viewModel.navigateToDirectory(item.path)
                                        } else {
                                            viewModel.openFile(item)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!uiState.isSelectionMode) {
                                        viewModel.toggleSelectionMode(true)
                                    }
                                    viewModel.toggleItemSelection(item.path)
                                },
                                onMoreClick = { activeMenuItem = item }
                            )
                        }

                        if (uiState.isLoadingNextPage) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Hoisted single context dropdown menu for items
                    DropdownMenu(
                        expanded = activeMenuItem != null,
                        onDismissRequest = { activeMenuItem = null }
                    ) {
                        activeMenuItem?.let { item ->
                            DropdownMenuItem(
                                text = { Text("Open") },
                                onClick = {
                                    activeMenuItem = null
                                    if (item.isDirectory) {
                                        viewModel.navigateToDirectory(item.path)
                                    } else {
                                        viewModel.openFile(item)
                                    }
                                }
                            )
                            if (item.isDirectory) {
                                DropdownMenuItem(
                                    text = { Text("Open in new tab") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                    },
                                    onClick = {
                                        activeMenuItem = null
                                        viewModel.openNewExplorerTab(item.path)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (item.isFavorite) "Remove Favorite" else "Add to Favorites") },
                                leadingIcon = {
                                    Icon(
                                        if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    viewModel.toggleFavorite(item)
                                    activeMenuItem = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                onClick = {
                                    renameTargetItem = item
                                    renameNewName = item.name
                                    showRenameDialog = true
                                    activeMenuItem = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    viewModel.toggleItemSelection(item.path)
                                    viewModel.copySelected()
                                    activeMenuItem = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cut / Move") },
                                leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                                onClick = {
                                    viewModel.toggleItemSelection(item.path)
                                    viewModel.cutSelected()
                                    activeMenuItem = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Properties") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    viewModel.openProperties(item)
                                    activeMenuItem = null
                                }
                            )
                            if (!item.isDirectory) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                                 onClick = {
                                                 try {
                                                 val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                     type = item.mimeType
                                                     val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
                                                     val shareCopy = File(shareDir, item.name).also {
                                                         File(item.path).copyTo(it, overwrite = true)
                                                     }
                                                     val shareUri = androidx.core.content.FileProvider.getUriForFile(
                                                         context,
                                                         context.packageName + ".fileprovider",
                                                         shareCopy
                                                     )
                                                     putExtra(Intent.EXTRA_STREAM, shareUri)
                                                     addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                 }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share File"))
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        activeMenuItem = null
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    viewModel.deleteFile(item.path, toTrash = true)
                                    activeMenuItem = null
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Paste Action Bar (when clipboard has items)
        if (uiState.clipboard != null) {
            PasteActionBar(
                clipboard = uiState.clipboard,
                onPaste = { viewModel.pasteClipboard() },
                onCancel = { viewModel.cancelClipboard() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }

        // Floating Action Button
        var showFabMenu by remember { mutableStateOf(false) }
        FloatingActionButton(
            onClick = { showFabMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Actions")
        }

        DropdownMenu(
            expanded = showFabMenu,
            onDismissRequest = { showFabMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("New Folder") },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = ColorFolders) },
                onClick = {
                    showFabMenu = false
                    newFolderName = ""
                    showNewFolderDialog = true
                }
            )
            DropdownMenuItem(
                text = { Text("New Text File") },
                leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    showFabMenu = false
                    newFileName = ""
                    showNewFileDialog = true
                }
            )
        }
    }

    // Dialog: Create Folder
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName)
                            showNewFolderDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Create File
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New Text File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("File Name (e.g. notes.txt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.createTextFile(newFileName)
                            showNewFileDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Rename
    if (showRenameDialog && renameTargetItem != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val item = renameTargetItem
                        if (item != null && renameNewName.isNotBlank()) {
                            viewModel.renameFile(item.path, renameNewName)
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Create Zip
    if (showZipDialog) {
        AlertDialog(
            onDismissRequest = { showZipDialog = false },
            title = { Text("Create ZIP Archive") },
            text = {
                OutlinedTextField(
                    value = zipArchiveName,
                    onValueChange = { zipArchiveName = it },
                    label = { Text("Archive Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (zipArchiveName.isNotBlank()) {
                            viewModel.zipSelected(zipArchiveName)
                            showZipDialog = false
                        }
                    }
                ) {
                    Text("Compress")
                }
            },
            dismissButton = {
                TextButton(onClick = { showZipDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    item: FileItem,
    isCompact: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isSearchMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isCompact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator or Icon
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            FileTypeIconBadge(item = item, modifier = Modifier.size(if (isCompact) 36.dp else 44.dp))

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                val parentFolderName = File(item.path).parentFile?.name
                if (isSearchMode && !parentFolderName.isNullOrBlank() && parentFolderName != "0") {
                    Text(
                        text = "in $parentFolderName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isCompact) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val subText = if (item.isDirectory) {
                            if (item.childCount >= 0) "${item.childCount} items" else "Items unavailable"
                        } else {
                            formatFileSize(item.size)
                        }
                        Text(
                            text = subText,
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

            if (!isSelectionMode) {
                IconButton(onClick = onMoreClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCard(
    item: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isSearchMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                if (item.isImage && item.uri != null) {
                    val context = LocalContext.current
                    val thumbRequest = remember(item.uri) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(200, 200)
                            .crossfade(false)
                            .allowHardware(true)
                            .build()
                    }
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    FileTypeIconBadge(item = item, modifier = Modifier.size(56.dp))
                }

                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val parentFolderName = File(item.path).parentFile?.name
            if (isSearchMode && !parentFolderName.isNullOrBlank() && parentFolderName != "0") {
                Text(
                    text = "in $parentFolderName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = if (item.isDirectory) {
                        if (item.childCount >= 0) "${item.childCount} items" else "Items unavailable"
                    } else formatFileSize(item.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ExplorerTabSegmentedSwitcher(
    tabs: List<ExplorerTab>,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Modern Material 3 Segmented Pill Switcher
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { tab ->
                    val isActive = tab.id == activeTabId
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        tonalElevation = if (isActive) 2.dp else 0.dp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectTab(tab.id) }
                            .testTag("explorer_tab_${tab.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = 10.dp,
                                end = if (tabs.size > 1) 6.dp else 10.dp,
                                top = 6.dp,
                                bottom = 6.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isActive) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = if (tabs.size > 1) 85.dp else 110.dp)
                            )

                            if (tabs.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .clickable { onCloseTab(tab.id) }
                                        .testTag("close_tab_${tab.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        tint = if (isActive) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        },
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Plus button: ALWAYS present next to tabs ("keep a plus button if no other tab is open")
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { onNewTab() }
                .testTag("add_tab_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New tab",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
