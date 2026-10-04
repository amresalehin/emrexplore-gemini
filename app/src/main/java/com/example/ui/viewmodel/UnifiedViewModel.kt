package com.example.ui.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExplorerPreferencesEntity
import com.example.data.local.FavoriteEntity
import com.example.data.local.IndexStatusEntity
import com.example.data.local.RecentEntity
import com.example.data.local.TrashEntity
import com.example.data.model.CategoryType
import com.example.data.model.FileItem
import com.example.data.model.MediaAlbum
import com.example.data.model.MediaItem
import com.example.data.model.SortOption
import com.example.data.model.StorageStats
import com.example.data.model.ViewMode
import com.example.data.model.ExplorerFilterType
import com.example.data.model.ExplorerDateFilter
import com.example.data.model.ExplorerSizeFilter
import com.example.data.model.ExplorerSearchScope
import com.example.data.model.sortFiles
import com.example.data.media.MediaFilter
import com.example.data.media.MediaRepository
import com.example.data.media.FullscreenMediaSource
import com.example.data.media.MediaViewerWindow
import com.example.data.ai.KnowledgeGraphRepository
import com.example.data.ai.AvailableAiModel
import com.example.data.ai.ConnectedDotsItem
import com.example.data.ai.ConnectionTestResult
import com.example.data.ai.RagAnswer
import com.example.data.local.AiProviderConfigEntity
import com.example.data.local.KgEdgeEntity
import com.example.data.local.KgNodeEntity
import com.example.data.model.ConflictResolution
import com.example.data.model.FileOperationProgress
import com.example.data.model.OperationStatus
import com.example.data.performance.PerformanceMetrics
import com.example.data.performance.PerformanceMonitor
import com.example.data.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class MainTab {
    HOME,
    FILES,
    GALLERY,
    BRAIN
}

enum class GallerySubTab {
    TIMELINE,
    ALBUMS
}

enum class GalleryDateFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year")
}

enum class GalleryLocationFilter(val label: String) {
    ALL("All Locations"),
    WITH_GPS("With GPS"),
    NO_GPS("No GPS")
}

enum class GallerySortOption(val title: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first")
}

enum class ClipboardAction {
    COPY,
    CUT
}

data class ClipboardState(
    val action: ClipboardAction,
    val sourcePaths: List<String>
)

data class ExplorerTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Storage",
    val path: String = "/storage/emulated/0"
)

data class UiState(
    val currentTab: MainTab = MainTab.HOME,
    // File Explorer
    val currentPath: String = "",
    val explorerTabs: List<ExplorerTab> = listOf(
        ExplorerTab(id = "default_tab", title = "Storage", path = "/storage/emulated/0")
    ),
    val activeExplorerTabId: String = "default_tab",
    val files: List<FileItem> = emptyList(),
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.NAME_ASC,
    val viewMode: ViewMode = ViewMode.DETAILED_LIST,
    val showHidden: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val clipboard: ClipboardState? = null,
    val isLoadingFiles: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val hasMorePages: Boolean = false,
    val currentPage: Int = 0,
    val totalFilesInFolder: Int = 0,

    // File Explorer Search & Filtering
    val explorerSearchActive: Boolean = false,
    val explorerSearchScope: ExplorerSearchScope = ExplorerSearchScope.SUBFOLDERS,
    val explorerFilterType: ExplorerFilterType = ExplorerFilterType.ALL,
    val explorerDateFilter: ExplorerDateFilter = ExplorerDateFilter.ALL,
    val explorerSizeFilter: ExplorerSizeFilter = ExplorerSizeFilter.ALL,
    val explorerSearchResults: List<FileItem> = emptyList(),
    val isExplorerSearching: Boolean = false,
    val explorerFilterBarVisible: Boolean = false,

    // Explorer Preferences & Room Indexing
    val explorerPreferences: ExplorerPreferencesEntity = ExplorerPreferencesEntity(),
    val isIndexing: Boolean = false,
    val indexedCount: Int = 0,
    val lastIndexedTimestamp: Long = 0L,
    val indexStatusMessage: String = "Ready",
    val isFastSearchRoomPowered: Boolean = true,
    val showPreferencesDialog: Boolean = false,

    // Gallery
    val gallerySubTab: GallerySubTab = GallerySubTab.TIMELINE,
    val galleryFilter: String = "ALL", // ALL, PHOTOS, VIDEOS, FAVORITES
    val galleryDateFilter: GalleryDateFilter = GalleryDateFilter.ALL,
    val galleryLocationFilter: GalleryLocationFilter = GalleryLocationFilter.ALL,
    val gallerySearchQuery: String = "",
    val gallerySearchSubmittedQuery: String = "",
    val gallerySearchActive: Boolean = false,
    val galleryRecentSearches: List<String> = emptyList(),
    val allMediaItems: List<MediaItem> = emptyList(),
    val mediaItems: List<MediaItem> = emptyList(),
    val mediaAlbums: List<MediaAlbum> = emptyList(),
    val selectedAlbum: MediaAlbum? = null,
    val galleryColumns: Int = 3,
    val gallerySortOption: GallerySortOption = GallerySortOption.DATE_DESC,
    val isLoadingMedia: Boolean = false,
    val gallerySelection: List<MediaItem> = emptyList(),

    // Browse / Categories
    val selectedCategory: CategoryType? = null,
    val categoryFiles: List<FileItem> = emptyList(),
    val categoryCounts: Map<CategoryType, Int> = emptyMap(),
    val categorySizes: Map<CategoryType, Long> = emptyMap(),

    // Viewers & Modals
    val fullscreenMediaIndex: Int? = null,
    val fullscreenMediaList: List<MediaItem> = emptyList(),
    val fullscreenWindowStartIndex: Int = 0,
    val fullscreenTotalCount: Int = 0,
    val fullscreenSource: FullscreenMediaSource? = null,
    val fullscreenAlbumId: String? = null,
    val fullscreenSearchQuery: String = "",
    val fullscreenSearchFavoriteOnly: Boolean = false,
    val fullscreenLoading: Boolean = false,
    val activeTextFile: FileItem? = null,
    val textFileContent: String = "",
    val isEditingText: Boolean = false,
    val activeZipFile: FileItem? = null,
    val zipEntries: List<String> = emptyList(),
    val isExtractingZip: Boolean = false,
    val activeDetailItem: FileItem? = null,

    // Audio Mini-Player
    val activeAudioFile: FileItem? = null,
    val isAudioPlaying: Boolean = false,
    val audioDurationMs: Int = 0,
    val audioPositionMs: Int = 0,

    // Storage Tools & Trash
    val storageStats: StorageStats = StorageStats(),
    val trashList: List<TrashEntity> = emptyList(),
    val isRecycleBinOpen: Boolean = false,
    val favoritesList: List<FavoriteEntity> = emptyList(),
    val recentsList: List<RecentEntity> = emptyList(),

    // Home Tab Search
    val homeSearchQuery: String = "",
    val homeSearchResults: List<FileItem> = emptyList(),
    val homeSearchCategoryFilter: CategoryType? = null,
    val isHomeSearching: Boolean = false,

    // Background File Operation
    val fileOperationProgress: FileOperationProgress = FileOperationProgress(),
    val performanceMetrics: PerformanceMetrics = PerformanceMetrics(),

    // Knowledge Graph & RAG State
    val kgNodes: List<KgNodeEntity> = emptyList(),
    val kgEdges: List<KgEdgeEntity> = emptyList(),
    val kgNodeCount: Int = 0,
    val kgEdgeCount: Int = 0,
    val kgChunkCount: Int = 0,
    val aiConfig: AiProviderConfigEntity = AiProviderConfigEntity(),
    val isTestingAiConnection: Boolean = false,
    val aiTestResult: ConnectionTestResult? = null,
    val showAiSettingsDialog: Boolean = false,
    val isAiSettingsScreenOpen: Boolean = false,
    val isKgIndexing: Boolean = false,
    val kgIndexingProgress: Float = 0f,
    val kgIndexingStatus: String = "Ready",
    val ragAnswer: RagAnswer? = null,
    val isRagQuerying: Boolean = false,
    val activeFileConnectedDots: List<ConnectedDotsItem> = emptyList(),
    val kgSmartSuggestions: List<String> = emptyList(),
    val aiModels: List<AvailableAiModel> = emptyList(),
    val aiVisionModels: List<AvailableAiModel> = emptyList(),
    val aiEmbeddingModels: List<AvailableAiModel> = emptyList(),
    val isFetchingAiModels: Boolean = false,
    val aiModelFetchError: String? = null,

    // User Feedback
    val userMessage: String? = null
)

class UnifiedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository(application)
    private val mediaRepository = MediaRepository(application)
    private val kgRepository = KnowledgeGraphRepository(application)
    private val galleryFilterFlow = MutableStateFlow<MediaFilter?>(MediaFilter.ALL)
    private val galleryDateFilterFlow = MutableStateFlow(GalleryDateFilter.ALL)
    private val galleryLocationFilterFlow = MutableStateFlow(GalleryLocationFilter.ALL)
    private val galleryRefreshFlow = MutableStateFlow(0L)
    private val gallerySearchFlow = MutableStateFlow("")
    private val gallerySortFlow = MutableStateFlow(GallerySortOption.DATE_DESC)

    private fun todayDateString(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private fun thisMonthString(): String =
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())

    private fun thisYearString(): String =
        java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date())

    fun buildEffectiveSearchQuery(
        rawQuery: String,
        dateFilter: GalleryDateFilter,
        locationFilter: GalleryLocationFilter
    ): String {
        val parts = mutableListOf<String>()
        if (rawQuery.isNotBlank()) {
            parts.add(rawQuery.trim())
        }
        when (dateFilter) {
            GalleryDateFilter.TODAY -> parts.add("date:${todayDateString()}")
            GalleryDateFilter.THIS_MONTH -> parts.add("month:${thisMonthString()}")
            GalleryDateFilter.THIS_YEAR -> parts.add("year:${thisYearString()}")
            GalleryDateFilter.ALL -> Unit
        }
        when (locationFilter) {
            GalleryLocationFilter.WITH_GPS -> parts.add("gps:true")
            GalleryLocationFilter.NO_GPS -> parts.add("gps:false")
            GalleryLocationFilter.ALL -> Unit
        }
        return parts.joinToString(" ")
    }

    /**
     * Primary timeline data source. Only the currently loaded Paging window is kept
     * in memory; the Gallery no longer needs the complete MediaStore library.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val galleryPagingFlow: Flow<PagingData<MediaItem>> =
        combine(
            combine(galleryFilterFlow, galleryDateFilterFlow, galleryLocationFilterFlow) { f, d, l ->
                Triple(f, d, l)
            },
            gallerySearchFlow.debounce(200).distinctUntilChanged(),
            gallerySortFlow,
            galleryRefreshFlow
        ) { (filter, dateFilter, locationFilter), query, sort, _ ->
            val effectiveQuery = buildEffectiveSearchQuery(query, dateFilter, locationFilter)
            Triple(filter, effectiveQuery, sort)
        }
        .flatMapLatest { (filter, effectiveQuery, sort) ->
            if (effectiveQuery.isNotBlank()) {
                mediaRepository.searchPager(
                    query = effectiveQuery,
                    filter = filter ?: MediaFilter.ALL,
                    favoritesOnly = filter == null,
                    sort = sort
                )
            } else if (filter == null) {
                mediaRepository.favoritesPager()
            } else {
                mediaRepository.pager(filter, sort)
            }
        }.cachedIn(viewModelScope)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var audioProgressJob: Job? = null
    private var homeSearchJob: Job? = null
    private var loadFilesJob: Job? = null
    private var fullscreenLoadJob: Job? = null
    private var explorerSearchJob: Job? = null

    init {
        // Collect decoupled file operations progress
        viewModelScope.launch {
            repository.operationManager.progress.collect { progress ->
                _uiState.update { it.copy(fileOperationProgress = progress) }
                if (progress.status == OperationStatus.COMPLETED) {
                    loadFiles()
                    loadStorageStats()
                }
            }
        }

        // Collect performance instrumentation metrics
        viewModelScope.launch {
            PerformanceMonitor.metrics.collect { metrics ->
                _uiState.update { it.copy(performanceMetrics = metrics) }
            }
        }
        viewModelScope.launch {
            val prefs = repository.getPreferences()
            val initialPath = if (prefs.rememberLastDirectory && prefs.lastDirectoryPath.isNotBlank() && File(prefs.lastDirectoryPath).exists()) {
                prefs.lastDirectoryPath
            } else if (prefs.defaultStartupPath.isNotBlank() && File(prefs.defaultStartupPath).exists()) {
                prefs.defaultStartupPath
            } else {
                repository.rootPath
            }
            val initialTabTitle = resolveTabTitle(initialPath)
            _uiState.update {
                it.copy(
                    currentPath = initialPath,
                    explorerTabs = listOf(ExplorerTab(id = "default_tab", title = initialTabTitle, path = initialPath)),
                    activeExplorerTabId = "default_tab"
                )
            }
            // Fast loading of initial folder
            loadFiles(initialPath)
            loadStorageStats()
            calculateCategoryCounts()

            // Asynchronous sample seeding and indexing in background
            launch(Dispatchers.IO) {
                repository.initializeSampleDataIfNeeded()
                if (prefs.autoIndexOnStart && repository.totalIndexedCount() == 0) {
                    repository.indexStorage(force = false)
                }
                try {
                    val currentNodes = kgRepository.nodeCountFlow.firstOrNull() ?: 0
                    if (currentNodes == 0) {
                        indexAllFilesForKnowledgeGraph()
                    }
                } catch (e: Exception) {
                    Log.w("UnifiedViewModel", "Initial KG index check: ${e.message}")
                }
            }
        }

        // Collect Room Database Flows
        viewModelScope.launch {
            repository.preferencesFlow.collectLatest { prefs ->
                _uiState.update { current ->
                    val viewModeEnum = try { ViewMode.valueOf(prefs.viewMode) } catch (e: Exception) { ViewMode.DETAILED_LIST }
                    val sortOptionEnum = try { SortOption.valueOf(prefs.sortOption) } catch (e: Exception) { SortOption.NAME_ASC }
                    current.copy(
                        explorerPreferences = prefs,
                        viewMode = viewModeEnum,
                        sortOption = sortOptionEnum,
                        showHidden = prefs.showHidden,
                        galleryColumns = prefs.galleryColumns,
                        isFastSearchRoomPowered = prefs.enableFastRoomSearch
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.indexStatusFlow.collectLatest { status ->
                _uiState.update { current ->
                    current.copy(
                        isIndexing = status.isIndexing,
                        lastIndexedTimestamp = status.lastIndexedTimestamp,
                        indexedCount = status.totalIndexedCount,
                        indexStatusMessage = status.statusMessage
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.favoritesFlow.collectLatest { favs ->
                _uiState.update { it.copy(favoritesList = favs) }
            }
        }
        viewModelScope.launch {
            repository.trashFlow.collectLatest { trash ->
                _uiState.update { it.copy(trashList = trash) }
            }
        }
        viewModelScope.launch {
            repository.recentsFlow.collectLatest { recents ->
                _uiState.update { it.copy(recentsList = recents) }
            }
        }

        // Collect Knowledge Graph and AI config flows
        viewModelScope.launch {
            kgRepository.allNodesFlow.collectLatest { nodes ->
                val suggestions = try { kgRepository.getSmartSuggestions() } catch (_: Exception) { emptyList() }
                _uiState.update { it.copy(kgNodes = nodes, kgNodeCount = nodes.size, kgSmartSuggestions = suggestions) }
            }
        }
        viewModelScope.launch {
            kgRepository.allEdgesFlow.collectLatest { edges ->
                _uiState.update { it.copy(kgEdges = edges, kgEdgeCount = edges.size) }
            }
        }
        viewModelScope.launch {
            kgRepository.aiConfigFlow.collectLatest { config ->
                config?.let { c ->
                    _uiState.update { it.copy(aiConfig = c) }
                }
            }
        }
        viewModelScope.launch {
            kgRepository.chunkCountFlow.collectLatest { count ->
                _uiState.update { it.copy(kgChunkCount = count) }
            }
        }
    }


    fun setTab(tab: MainTab) {
        _uiState.update { it.copy(currentTab = tab) }
        when (tab) {
            MainTab.HOME -> {
                loadStorageStats()
                calculateCategoryCounts()
            }
            MainTab.FILES -> {
                if (_uiState.value.files.isEmpty()) {
                    loadFiles(_uiState.value.currentPath)
                }
            }
            MainTab.GALLERY -> {
                // Timeline is backed by Paging and starts loading only when the UI
                // collects galleryPagingFlow. Albums/favorites retain their legacy
                // path temporarily and are loaded only when explicitly needed.
            }
            MainTab.BRAIN -> {
                // Brain Knowledge Graph & RAG view - auto-connect if empty
                if (_uiState.value.kgNodes.isEmpty() && !_uiState.value.isKgIndexing) {
                    indexAllFilesForKnowledgeGraph()
                }
            }
        }
    }

    // --- File Explorer Actions ---

    fun resolveTabTitle(path: String): String {
        return if (path.isBlank() || path == "/" || path == "/storage/emulated/0" || path == repository.rootPath) {
            "Storage"
        } else {
            val file = File(path)
            file.name.ifEmpty { "Folder" }
        }
    }

    fun openNewExplorerTab(path: String = repository.rootPath) {
        val targetPath = if (path.isNotBlank() && File(path).exists()) path else repository.rootPath
        val title = resolveTabTitle(targetPath)
        val newTab = ExplorerTab(
            id = UUID.randomUUID().toString(),
            title = title,
            path = targetPath
        )
        _uiState.update { current ->
            current.copy(
                explorerTabs = current.explorerTabs + newTab,
                activeExplorerTabId = newTab.id,
                currentPath = targetPath,
                searchQuery = "",
                isSelectionMode = false,
                selectedPaths = emptySet()
            )
        }
        loadFiles(targetPath)
        showMessage("Opened new tab: $title")
    }

    fun switchExplorerTab(tabId: String) {
        val targetTab = _uiState.value.explorerTabs.find { it.id == tabId } ?: return
        if (targetTab.id == _uiState.value.activeExplorerTabId) return
        _uiState.update { current ->
            current.copy(
                activeExplorerTabId = tabId,
                currentPath = targetTab.path,
                searchQuery = "",
                isSelectionMode = false,
                selectedPaths = emptySet()
            )
        }
        loadFiles(targetTab.path)
    }

    fun closeExplorerTab(tabId: String) {
        val currentTabs = _uiState.value.explorerTabs
        if (currentTabs.size <= 1) return
        val newTabs = currentTabs.filter { it.id != tabId }
        val newActiveTab = if (_uiState.value.activeExplorerTabId == tabId) {
            newTabs.last()
        } else {
            newTabs.find { it.id == _uiState.value.activeExplorerTabId } ?: newTabs.last()
        }
        _uiState.update { current ->
            current.copy(
                explorerTabs = newTabs,
                activeExplorerTabId = newActiveTab.id,
                currentPath = newActiveTab.path,
                searchQuery = "",
                isSelectionMode = false,
                selectedPaths = emptySet()
            )
        }
        loadFiles(newActiveTab.path)
    }

    fun navigateToDirectory(path: String) {
        if (path == _uiState.value.currentPath && _uiState.value.files.isNotEmpty()) return

        loadFilesJob?.cancel()

        val tabTitle = resolveTabTitle(path)
        _uiState.update { current ->
            val updatedTabs = current.explorerTabs.map { tab ->
                if (tab.id == current.activeExplorerTabId) {
                    tab.copy(path = path, title = tabTitle)
                } else tab
            }
            current.copy(
                explorerTabs = updatedTabs,
                currentPath = path,
                searchQuery = "",
                isSelectionMode = false,
                selectedPaths = emptySet(),
                currentPage = 0,
                hasMorePages = false,
                totalFilesInFolder = 0,
                isLoadingFiles = true,
                isLoadingNextPage = false
            )
        }

        loadFilesJob = viewModelScope.launch {
            // STEP 1: Quick check if cached items exist in memory or Room DB
            val cached = repository.getCachedFiles(path, _uiState.value.showHidden)
            if (!cached.isNullOrEmpty()) {
                val sorted = sortFiles(cached, _uiState.value.sortOption)
                val initialItems = if (sorted.size <= 300) sorted else sorted.take(120)
                val hasMore = sorted.size > initialItems.size
                _uiState.update {
                    it.copy(
                        files = initialItems,
                        currentPage = 0,
                        totalFilesInFolder = sorted.size,
                        hasMorePages = hasMore,
                        isLoadingFiles = false
                    )
                }
                if (!hasMore) {
                    if (_uiState.value.explorerPreferences.rememberLastDirectory) {
                        repository.updateLastPath(path)
                    }
                    return@launch
                }
            }

            // STEP 2: Fetch Page 0 lazily via repository.getFilesPaged (pageSize = 120)
            val pagedResult = repository.getFilesPaged(
                dirPath = path,
                page = 0,
                pageSize = 120,
                sortOption = _uiState.value.sortOption,
                showHidden = _uiState.value.showHidden
            )

            _uiState.update {
                it.copy(
                    files = pagedResult.items,
                    currentPage = 0,
                    totalFilesInFolder = pagedResult.totalCount,
                    hasMorePages = pagedResult.hasMore,
                    isLoadingFiles = false
                )
            }

            if (_uiState.value.explorerPreferences.rememberLastDirectory) {
                repository.updateLastPath(path)
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoadingNextPage || !state.hasMorePages || state.isLoadingFiles) return

        val nextPage = state.currentPage + 1
        _uiState.update { it.copy(isLoadingNextPage = true) }

        viewModelScope.launch {
            val result = repository.getFilesPaged(
                dirPath = state.currentPath,
                page = nextPage,
                pageSize = 120,
                sortOption = state.sortOption,
                showHidden = state.showHidden
            )

            _uiState.update { current ->
                if (current.currentPath == state.currentPath) {
                    val combined = (current.files + result.items).distinctBy { it.path }
                    current.copy(
                        files = combined,
                        currentPage = nextPage,
                        totalFilesInFolder = result.totalCount,
                        hasMorePages = result.hasMore,
                        isLoadingNextPage = false
                    )
                } else {
                    current.copy(isLoadingNextPage = false)
                }
            }
        }
    }

    fun navigateUp(): Boolean {
        val current = File(_uiState.value.currentPath)
        val parent = current.parentFile
        return if (parent != null && parent.canRead() && current.absolutePath != "/") {
            navigateToDirectory(parent.absolutePath)
            true
        } else false
    }

    fun onPermissionsGranted() {
        val root = repository.rootPath
        val current = _uiState.value.currentPath
        val filesDir = getApplication<Application>().filesDir.absolutePath
        val shouldNavigateToRoot = (current == filesDir || !File(current).exists() || !File(current).canRead()) && root != filesDir
        if (shouldNavigateToRoot) {
            navigateToDirectory(root)
        } else {
            loadFiles()
        }
        loadStorageStats()
        calculateCategoryCounts()
        if (_uiState.value.kgNodes.isEmpty() && !_uiState.value.isKgIndexing) {
            indexAllFilesForKnowledgeGraph()
        }
    }

    fun loadFiles(path: String = _uiState.value.currentPath) {
        loadFilesJob?.cancel()
        loadFilesJob = viewModelScope.launch {
            if (_uiState.value.files.isEmpty()) {
                _uiState.update { it.copy(isLoadingFiles = true) }
            }

            val result = repository.getFilesPaged(
                dirPath = path,
                page = 0,
                pageSize = 120,
                sortOption = _uiState.value.sortOption,
                showHidden = _uiState.value.showHidden
            )

            _uiState.update {
                it.copy(
                    files = result.items,
                    currentPage = 0,
                    totalFilesInFolder = result.totalCount,
                    hasMorePages = result.hasMore,
                    isLoadingFiles = false
                )
            }
        }
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { state ->
            val sorted = sortFiles(state.files, option)
            val sortedSearch = if (state.explorerSearchResults.isNotEmpty()) {
                sortFiles(state.explorerSearchResults, option)
            } else emptyList()
            state.copy(sortOption = option, files = sorted, explorerSearchResults = sortedSearch)
        }
        viewModelScope.launch {
            repository.updateSortOption(option)
            loadFiles()
        }
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
        viewModelScope.launch {
            repository.updateViewMode(mode)
        }
    }

    fun toggleShowHidden() {
        val newVal = !_uiState.value.showHidden
        _uiState.update { it.copy(showHidden = newVal) }
        viewModelScope.launch {
            repository.updateShowHidden(newVal)
        }
        loadFiles()
        triggerExplorerSearch()
    }

    fun setShowPreferencesDialog(show: Boolean) {
        _uiState.update { it.copy(showPreferencesDialog = show) }
    }

    fun saveExplorerPreferences(prefs: ExplorerPreferencesEntity) {
        viewModelScope.launch {
            repository.savePreferences(prefs)
            showMessage("Preferences saved to Room database")
        }
    }

    fun toggleFastSearch() {
        val nextVal = !_uiState.value.isFastSearchRoomPowered
        viewModelScope.launch {
            repository.updateFastSearch(nextVal)
            showMessage(if (nextVal) "Room database search enabled" else "Live disk search enabled")
        }
    }

    fun toggleRememberLastDirectory() {
        val nextVal = !_uiState.value.explorerPreferences.rememberLastDirectory
        viewModelScope.launch {
            repository.updateRememberLastDir(nextVal)
        }
    }

    fun reindexStorage(force: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isIndexing = true, indexStatusMessage = "Indexing storage...") }
            val count = repository.indexStorage(force)
            _uiState.update {
                it.copy(
                    isIndexing = false,
                    indexedCount = count,
                    lastIndexedTimestamp = System.currentTimeMillis(),
                    indexStatusMessage = "Indexed $count items"
                )
            }
            showMessage("Room indexed $count files and folders")
            calculateCategoryCounts()
        }
    }

    fun resetPreferences() {
        viewModelScope.launch {
            repository.resetPreferences()
            showMessage("Preferences reset to defaults")
            loadFiles()
        }
    }

    fun setExplorerSearchActive(active: Boolean) {
        _uiState.update { it.copy(explorerSearchActive = active) }
        if (!active && _uiState.value.searchQuery.isNotEmpty()) {
            clearExplorerSearch()
        }
    }

    fun setExplorerSearchQuery(query: String) {
        _uiState.update { current ->
            val q = query.trim().lowercase()
            val instantMatches = if (q.isNotEmpty()) {
                current.files.filter { file ->
                    val passesQuery = file.name.lowercase().contains(q)
                    val passesType = when (current.explorerFilterType) {
                        ExplorerFilterType.ALL -> true
                        ExplorerFilterType.FOLDERS -> file.isDirectory
                        ExplorerFilterType.DOCUMENTS -> !file.isDirectory && file.isDocument
                        ExplorerFilterType.IMAGES -> !file.isDirectory && file.isImage
                        ExplorerFilterType.VIDEOS -> !file.isDirectory && file.isVideo
                        ExplorerFilterType.AUDIO -> !file.isDirectory && file.isAudio
                        ExplorerFilterType.ARCHIVES -> !file.isDirectory && file.isArchive
                        ExplorerFilterType.APKS -> !file.isDirectory && file.isApk
                    }
                    passesQuery && passesType
                }
            } else if (current.explorerFilterType != ExplorerFilterType.ALL) {
                current.files.filter { file ->
                    when (current.explorerFilterType) {
                        ExplorerFilterType.ALL -> true
                        ExplorerFilterType.FOLDERS -> file.isDirectory
                        ExplorerFilterType.DOCUMENTS -> !file.isDirectory && file.isDocument
                        ExplorerFilterType.IMAGES -> !file.isDirectory && file.isImage
                        ExplorerFilterType.VIDEOS -> !file.isDirectory && file.isVideo
                        ExplorerFilterType.AUDIO -> !file.isDirectory && file.isAudio
                        ExplorerFilterType.ARCHIVES -> !file.isDirectory && file.isArchive
                        ExplorerFilterType.APKS -> !file.isDirectory && file.isApk
                    }
                }
            } else {
                emptyList()
            }
            current.copy(
                searchQuery = query,
                explorerSearchResults = if (query.isNotBlank() || current.explorerFilterType != ExplorerFilterType.ALL) instantMatches else emptyList()
            )
        }
        triggerExplorerSearch()
    }

    fun setSearchQuery(query: String) {
        setExplorerSearchQuery(query)
    }

    fun setExplorerSearchScope(scope: ExplorerSearchScope) {
        _uiState.update { it.copy(explorerSearchScope = scope) }
        triggerExplorerSearch()
    }

    fun setExplorerFilterType(filterType: ExplorerFilterType) {
        _uiState.update { current ->
            val instantMatches = if (filterType != ExplorerFilterType.ALL) {
                current.files.filter { file ->
                    val matchesType = when (filterType) {
                        ExplorerFilterType.ALL -> true
                        ExplorerFilterType.FOLDERS -> file.isDirectory
                        ExplorerFilterType.DOCUMENTS -> !file.isDirectory && file.isDocument
                        ExplorerFilterType.IMAGES -> !file.isDirectory && file.isImage
                        ExplorerFilterType.VIDEOS -> !file.isDirectory && file.isVideo
                        ExplorerFilterType.AUDIO -> !file.isDirectory && file.isAudio
                        ExplorerFilterType.ARCHIVES -> !file.isDirectory && file.isArchive
                        ExplorerFilterType.APKS -> !file.isDirectory && file.isApk
                    }
                    val matchesQuery = current.searchQuery.isBlank() || file.name.lowercase().contains(current.searchQuery.trim().lowercase())
                    matchesType && matchesQuery
                }
            } else if (current.searchQuery.isNotBlank()) {
                val q = current.searchQuery.trim().lowercase()
                current.files.filter { it.name.lowercase().contains(q) }
            } else {
                emptyList()
            }
            current.copy(
                explorerFilterType = filterType,
                explorerSearchResults = if (filterType != ExplorerFilterType.ALL || current.searchQuery.isNotBlank()) instantMatches else emptyList()
            )
        }
        triggerExplorerSearch()
    }

    fun setExplorerDateFilter(dateFilter: ExplorerDateFilter) {
        _uiState.update { it.copy(explorerDateFilter = dateFilter) }
        triggerExplorerSearch()
    }

    fun setExplorerSizeFilter(sizeFilter: ExplorerSizeFilter) {
        _uiState.update { it.copy(explorerSizeFilter = sizeFilter) }
        triggerExplorerSearch()
    }

    fun toggleExplorerFilterBar(visible: Boolean? = null) {
        _uiState.update { it.copy(explorerFilterBarVisible = visible ?: !it.explorerFilterBarVisible) }
    }

    fun clearExplorerSearch() {
        explorerSearchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                explorerSearchResults = emptyList(),
                isExplorerSearching = false
            )
        }
    }

    fun clearExplorerFilters() {
        _uiState.update {
            it.copy(
                explorerFilterType = ExplorerFilterType.ALL,
                explorerDateFilter = ExplorerDateFilter.ALL,
                explorerSizeFilter = ExplorerSizeFilter.ALL,
                explorerFilterBarVisible = false
            )
        }
        triggerExplorerSearch()
    }

    fun resetExplorerSearchAndFilters() {
        explorerSearchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                explorerSearchActive = false,
                explorerSearchScope = ExplorerSearchScope.SUBFOLDERS,
                explorerFilterType = ExplorerFilterType.ALL,
                explorerDateFilter = ExplorerDateFilter.ALL,
                explorerSizeFilter = ExplorerSizeFilter.ALL,
                explorerSearchResults = emptyList(),
                isExplorerSearching = false,
                explorerFilterBarVisible = false
            )
        }
    }

    fun triggerExplorerSearch() {
        val state = _uiState.value
        val hasQuery = state.searchQuery.isNotBlank()
        val hasFilters = state.explorerFilterType != ExplorerFilterType.ALL ||
                         state.explorerDateFilter != ExplorerDateFilter.ALL ||
                         state.explorerSizeFilter != ExplorerSizeFilter.ALL

        if (!hasQuery && !hasFilters) {
            explorerSearchJob?.cancel()
            _uiState.update {
                it.copy(
                    explorerSearchResults = emptyList(),
                    isExplorerSearching = false
                )
            }
            return
        }

        explorerSearchJob?.cancel()
        explorerSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isExplorerSearching = true) }
            if (hasQuery) delay(120)
            val currentState = _uiState.value
            val results = repository.searchExplorer(
                dirPath = currentState.currentPath,
                query = currentState.searchQuery,
                scope = currentState.explorerSearchScope,
                filterType = currentState.explorerFilterType,
                dateFilter = currentState.explorerDateFilter,
                sizeFilter = currentState.explorerSizeFilter,
                sortOption = currentState.sortOption,
                showHidden = currentState.showHidden
            )
            _uiState.update {
                it.copy(
                    explorerSearchResults = results,
                    isExplorerSearching = false
                )
            }
        }
    }

    // --- Home Tab Search Actions ---

    fun setHomeSearchQuery(query: String) {
        _uiState.update { it.copy(homeSearchQuery = query) }
        homeSearchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(homeSearchResults = emptyList(), isHomeSearching = false) }
            return
        }
        homeSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isHomeSearching = true) }
            delay(150)
            val results = repository.searchFiles(query, _uiState.value.homeSearchCategoryFilter)
            _uiState.update {
                it.copy(
                    homeSearchResults = results,
                    isHomeSearching = false
                )
            }
        }
    }

    fun setHomeSearchCategoryFilter(category: CategoryType?) {
        _uiState.update { it.copy(homeSearchCategoryFilter = category) }
        val currentQuery = _uiState.value.homeSearchQuery
        if (currentQuery.isNotBlank()) {
            setHomeSearchQuery(currentQuery)
        }
    }

    fun clearHomeSearch() {
        homeSearchJob?.cancel()
        _uiState.update {
            it.copy(
                homeSearchQuery = "",
                homeSearchResults = emptyList(),
                homeSearchCategoryFilter = null,
                isHomeSearching = false
            )
        }
    }

    fun jumpToFolder(folderPath: String) {
        setTab(MainTab.FILES)
        navigateToDirectory(folderPath)
    }

    private fun sortFiles(list: List<FileItem>, option: SortOption): List<FileItem> {
        val (dirs, files) = list.partition { it.isDirectory }
        val sortedDirs = when (option) {
            SortOption.NAME_ASC -> dirs.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> dirs.sortedByDescending { it.name.lowercase() }
            SortOption.DATE_DESC -> dirs.sortedByDescending { it.lastModified }
            SortOption.DATE_ASC -> dirs.sortedBy { it.lastModified }
            SortOption.SIZE_DESC -> dirs.sortedByDescending { it.childCount }
            SortOption.SIZE_ASC -> dirs.sortedBy { it.childCount }
            SortOption.TYPE -> dirs.sortedBy { it.name.lowercase() }
        }

        val sortedFiles = when (option) {
            SortOption.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            SortOption.DATE_DESC -> files.sortedByDescending { it.lastModified }
            SortOption.DATE_ASC -> files.sortedBy { it.lastModified }
            SortOption.SIZE_DESC -> files.sortedByDescending { it.size }
            SortOption.SIZE_ASC -> files.sortedBy { it.size }
            SortOption.TYPE -> files.sortedBy { it.extension }
        }

        return sortedDirs + sortedFiles
    }

    // Selection & Batch Operations
    fun toggleSelectionMode(enable: Boolean? = null) {
        val newMode = enable ?: !_uiState.value.isSelectionMode
        _uiState.update {
            it.copy(
                isSelectionMode = newMode,
                selectedPaths = if (newMode) it.selectedPaths else emptySet()
            )
        }
    }

    fun toggleItemSelection(path: String) {
        _uiState.update { state ->
            val set = state.selectedPaths.toMutableSet()
            if (set.contains(path)) set.remove(path) else set.add(path)
            state.copy(
                selectedPaths = set,
                isSelectionMode = set.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        val allPaths = _uiState.value.files.map { it.path }.toSet()
        _uiState.update {
            it.copy(
                selectedPaths = allPaths,
                isSelectionMode = allPaths.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedPaths = emptySet(),
                isSelectionMode = false
            )
        }
    }

    // Clipboard (Copy / Cut / Paste)
    fun copySelected() {
        val selected = _uiState.value.selectedPaths.toList()
        if (selected.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    clipboard = ClipboardState(ClipboardAction.COPY, selected),
                    isSelectionMode = false,
                    selectedPaths = emptySet(),
                    userMessage = "${selected.size} items copied to clipboard"
                )
            }
        }
    }

    fun cutSelected() {
        val selected = _uiState.value.selectedPaths.toList()
        if (selected.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    clipboard = ClipboardState(ClipboardAction.CUT, selected),
                    isSelectionMode = false,
                    selectedPaths = emptySet(),
                    userMessage = "${selected.size} items cut to clipboard"
                )
            }
        }
    }

    fun cancelClipboard() {
        _uiState.update { it.copy(clipboard = null, userMessage = "Clipboard cleared") }
    }

    fun pasteClipboard() {
        val clip = _uiState.value.clipboard ?: return
        val targetDir = _uiState.value.currentPath
        val actionName = if (clip.action == ClipboardAction.COPY) "Copy" else "Move"

        if (clip.action == ClipboardAction.COPY) {
            repository.operationManager.startCopy(clip.sourcePaths, targetDir)
        } else {
            repository.operationManager.startMove(clip.sourcePaths, targetDir)
        }

        _uiState.update {
            it.copy(
                clipboard = null,
                userMessage = "$actionName operation started in background"
            )
        }
    }

    // File Operation Controls
    fun pauseFileOperation() {
        repository.operationManager.pause()
    }

    fun resumeFileOperation() {
        repository.operationManager.resume()
    }

    fun cancelFileOperation() {
        repository.operationManager.cancel()
    }

    fun retryFileOperation() {
        repository.operationManager.retry()
    }

    fun resolveFileConflict(resolution: ConflictResolution) {
        repository.operationManager.resolveConflict(resolution)
    }

    fun dismissFileOperation() {
        repository.operationManager.dismiss()
    }

    // CRUD
    fun createFolder(name: String) {
        viewModelScope.launch {
            val success = repository.createFolder(_uiState.value.currentPath, name.trim())
            if (success) {
                showMessage("Folder '$name' created")
                loadFiles()
            } else {
                showMessage("Could not create folder '$name'")
            }
        }
    }

    fun createTextFile(name: String, initialContent: String = "") {
        viewModelScope.launch {
            val validName = if (!name.contains(".")) "$name.txt" else name
            val success = repository.createTextFile(_uiState.value.currentPath, validName.trim(), initialContent)
            if (success) {
                showMessage("File '$validName' created")
                loadFiles()
            } else {
                showMessage("File '$validName' already exists or failed")
            }
        }
    }

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch {
            val ok = repository.renameFile(oldPath, newName.trim())
            if (ok) {
                showMessage("Renamed to '$newName'")
                loadFiles()
                refreshGallery()
            } else {
                showMessage("Failed to rename file")
            }
        }
    }

    fun deleteFile(path: String, toTrash: Boolean = true) {
        viewModelScope.launch {
            val ok = repository.deleteFile(path, toTrash)
            if (ok) {
                showMessage(if (toTrash) "Moved to Recycle Bin" else "Permanently deleted")
                loadFiles()
                refreshGallery()
                loadStorageStats()
            } else {
                showMessage("Delete failed")
            }
        }
    }

    fun deleteSelected(toTrash: Boolean = true) {
        val selected = _uiState.value.selectedPaths.toList()
        viewModelScope.launch {
            var count = 0
            for (p in selected) {
                if (repository.deleteFile(p, toTrash)) count++
            }
            clearSelection()
            showMessage(if (toTrash) "Moved $count items to Recycle Bin" else "Deleted $count items")
            loadFiles()
            refreshGallery()
            loadStorageStats()
        }
    }

    fun zipSelected(zipName: String) {
        val selected = _uiState.value.selectedPaths.toList()
        if (selected.isEmpty()) return

        val finalName = if (zipName.endsWith(".zip")) zipName else "$zipName.zip"
        val targetZip = File(_uiState.value.currentPath, finalName).absolutePath

        viewModelScope.launch {
            val ok = repository.zipFiles(selected, targetZip)
            clearSelection()
            if (ok) {
                showMessage("Created archive $finalName")
                loadFiles()
            } else {
                showMessage("Failed to create zip archive")
            }
        }
    }

    // --- Gallery Actions ---

    fun setGallerySubTab(subTab: GallerySubTab) {
        _uiState.update { it.copy(gallerySubTab = subTab) }
    }

    fun setGallerySearchActive(active: Boolean) {
        _uiState.update { it.copy(gallerySearchActive = active) }
    }

    fun setGallerySearchQuery(query: String) {
        _uiState.update { it.copy(gallerySearchQuery = query) }
        gallerySearchFlow.value = query.trim()
    }

    fun submitGallerySearch(customQuery: String? = null) {
        val query = (customQuery ?: _uiState.value.gallerySearchQuery).trim()
        val recents = if (query.isBlank()) {
            _uiState.value.galleryRecentSearches
        } else {
            listOf(query) + _uiState.value.galleryRecentSearches.filterNot { it.equals(query, ignoreCase = true) }
        }.take(10)
        _uiState.update {
            it.copy(
                gallerySearchQuery = query,
                gallerySearchSubmittedQuery = query,
                gallerySearchActive = false,
                galleryRecentSearches = recents
            )
        }
        gallerySearchFlow.value = query
    }

    fun useRecentGallerySearch(query: String) {
        setGallerySearchQuery(query)
        submitGallerySearch(query)
    }

    fun removeRecentGallerySearch(query: String) {
        _uiState.update {
            it.copy(galleryRecentSearches = it.galleryRecentSearches.filterNot { item -> item.equals(query, ignoreCase = true) })
        }
    }

    fun clearGalleryRecentSearches() {
        _uiState.update { it.copy(galleryRecentSearches = emptyList()) }
    }

    fun clearGallerySearch() {
        _uiState.update {
            it.copy(
                gallerySearchQuery = "",
                gallerySearchSubmittedQuery = "",
                gallerySearchActive = false
            )
        }
        gallerySearchFlow.value = ""
    }

    fun setGalleryFilter(filter: String) {
        _uiState.update { it.copy(galleryFilter = filter) }

        when (filter) {
            "ALL" -> galleryFilterFlow.value = MediaFilter.ALL
            "PHOTOS" -> galleryFilterFlow.value = MediaFilter.PHOTOS
            "VIDEOS" -> galleryFilterFlow.value = MediaFilter.VIDEOS
            "FAVORITES" -> galleryFilterFlow.value = null
        }
    }

    fun setGalleryDateFilter(dateFilter: GalleryDateFilter) {
        _uiState.update { it.copy(galleryDateFilter = dateFilter) }
        galleryDateFilterFlow.value = dateFilter
    }

    fun setGalleryLocationFilter(locationFilter: GalleryLocationFilter) {
        _uiState.update { it.copy(galleryLocationFilter = locationFilter) }
        galleryLocationFilterFlow.value = locationFilter
    }

    fun clearGalleryFilters() {
        setGalleryFilter("ALL")
        setGalleryDateFilter(GalleryDateFilter.ALL)
        setGalleryLocationFilter(GalleryLocationFilter.ALL)
    }

    fun selectAlbum(album: MediaAlbum?) {
        _uiState.update { it.copy(selectedAlbum = album) }
    }

    fun setGallerySortOption(option: GallerySortOption) {
        _uiState.update { it.copy(gallerySortOption = option) }
        gallerySortFlow.value = option
    }

    fun setGalleryColumns(cols: Int) {
        val clamped = cols.coerceIn(2, 4)
        _uiState.update { it.copy(galleryColumns = clamped) }
        viewModelScope.launch {
            repository.updateGalleryColumns(clamped)
        }
    }

    fun refreshGallery() {
        galleryRefreshFlow.value = System.currentTimeMillis()
    }

    // Gallery selection is intentionally bounded to items the user explicitly selects.
    // It never materializes the complete MediaStore library.
    fun toggleGallerySelection(item: MediaItem) {
        _uiState.update { state ->
            val selected = state.gallerySelection.toMutableList()
            val existing = selected.indexOfFirst { it.path == item.path }
            if (existing >= 0) selected.removeAt(existing) else selected.add(item)
            state.copy(gallerySelection = selected)
        }
    }

    fun clearGallerySelection() {
        _uiState.update { it.copy(gallerySelection = emptyList()) }
    }

    fun favoriteGallerySelection() {
        val selected = _uiState.value.gallerySelection
        if (selected.isEmpty()) return
        viewModelScope.launch {
            selected.forEach { media -> repository.toggleFavorite(media.toFileItem()) }
            clearGallerySelection()
            refreshGallery()
            showMessage("Updated favorites for " + selected.size + " items")
        }
    }

    fun deleteGallerySelection(toTrash: Boolean = true) {
        val selected = _uiState.value.gallerySelection
        if (selected.isEmpty()) return
        viewModelScope.launch {
            var count = 0
            selected.forEach { media -> if (repository.deleteFile(media.path, toTrash)) count++ }
            clearGallerySelection()
            refreshGallery()
            loadStorageStats()
            showMessage(if (toTrash) "Moved $count items to Recycle Bin" else "Deleted $count items")
        }
    }

    fun openFullscreenMedia(
        item: MediaItem,
        list: List<MediaItem>,
        source: FullscreenMediaSource? = null,
        albumId: String? = null
    ) {
        val stateBeforeOpen = _uiState.value
        val fallbackIndex = list.indexOfFirst { it.path == item.path }.takeIf { it >= 0 } ?: 0
        val searchFavoriteOnly = stateBeforeOpen.galleryFilter == "FAVORITES"
        val effectiveQuery = buildEffectiveSearchQuery(
            stateBeforeOpen.gallerySearchSubmittedQuery.ifBlank { stateBeforeOpen.gallerySearchQuery },
            stateBeforeOpen.galleryDateFilter,
            stateBeforeOpen.galleryLocationFilter
        )
        val effectiveSource = if (effectiveQuery.isNotBlank() && source != FullscreenMediaSource.ALBUM) {
            FullscreenMediaSource.SEARCH
        } else {
            source
        }

        _uiState.update {
            it.copy(
                fullscreenMediaIndex = fallbackIndex,
                fullscreenMediaList = listOf(item),
                fullscreenWindowStartIndex = fallbackIndex,
                fullscreenTotalCount = list.size,
                fullscreenSource = effectiveSource,
                fullscreenAlbumId = albumId,
                fullscreenSearchQuery = if (effectiveSource == FullscreenMediaSource.SEARCH) effectiveQuery else "",
                fullscreenSearchFavoriteOnly = if (effectiveSource == FullscreenMediaSource.SEARCH) searchFavoriteOnly else false,
                fullscreenLoading = effectiveSource != null
            )
        }
        if (effectiveSource == null) return
        fullscreenLoadJob?.cancel()
        fullscreenLoadJob = viewModelScope.launch {
            try {
                if (effectiveSource == FullscreenMediaSource.SEARCH) {
                    val filter = when (stateBeforeOpen.galleryFilter) {
                        "PHOTOS" -> MediaFilter.PHOTOS
                        "VIDEOS" -> MediaFilter.VIDEOS
                        else -> MediaFilter.ALL
                    }
                    val window = mediaRepository.loadSearchViewerWindow(
                        item = item,
                        query = effectiveQuery,
                        filter = filter,
                        favoritesOnly = searchFavoriteOnly,
                        radius = 2
                    )
                    _uiState.update {
                        it.copy(
                            fullscreenMediaIndex = window.startIndex,
                            fullscreenMediaList = window.items,
                            fullscreenWindowStartIndex = window.startIndex,
                            fullscreenTotalCount = window.totalCount,
                            fullscreenLoading = false
                        )
                    }
                    return@launch
                }

                val resolvedSource = effectiveSource ?: FullscreenMediaSource.ALL
                val absoluteIndex = mediaRepository.viewerPosition(item, resolvedSource, albumId)
                val window = mediaRepository.loadViewerWindow(
                    resolvedSource,
                    absoluteIndex,
                    radius = 2,
                    albumId = albumId
                )
                _uiState.update {
                    it.copy(
                        fullscreenMediaIndex = absoluteIndex,
                        fullscreenMediaList = window.items,
                        fullscreenWindowStartIndex = window.startIndex,
                        fullscreenTotalCount = window.totalCount,
                        fullscreenLoading = false
                    )
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _uiState.update { it.copy(fullscreenLoading = false) }
            }
        }
    }

    fun openStandaloneFullscreenMedia(item: MediaItem) {
        fullscreenLoadJob?.cancel()
        _uiState.update {
            it.copy(
                fullscreenMediaIndex = 0,
                fullscreenMediaList = listOf(item),
                fullscreenWindowStartIndex = 0,
                fullscreenTotalCount = 1,
                fullscreenSource = null,
                fullscreenAlbumId = null,
                fullscreenSearchQuery = "",
                fullscreenSearchFavoriteOnly = false,
                fullscreenLoading = false
            )
        }
    }

    fun closeFullscreenMedia() {
        fullscreenLoadJob?.cancel()
        fullscreenLoadJob = null
        _uiState.update {
            it.copy(
                fullscreenMediaIndex = null,
                fullscreenMediaList = emptyList(),
                fullscreenWindowStartIndex = 0,
                fullscreenTotalCount = 0,
                fullscreenSource = null,
                fullscreenAlbumId = null,
                fullscreenLoading = false
            )
        }
    }

    fun moveFullscreenMedia(targetIndex: Int) {
        val state = _uiState.value
        val source = state.fullscreenSource ?: return
        val total = state.fullscreenTotalCount
        if (targetIndex !in 0 until total || state.fullscreenLoading) return
        if (targetIndex in state.fullscreenWindowStartIndex until (state.fullscreenWindowStartIndex + state.fullscreenMediaList.size)) {
            _uiState.update { it.copy(fullscreenMediaIndex = targetIndex) }
            return
        }
        fullscreenLoadJob?.cancel()
        fullscreenLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(fullscreenLoading = true) }
            try {
                if (source == FullscreenMediaSource.SEARCH) {
                    val filter = when (state.galleryFilter) {
                        "PHOTOS" -> MediaFilter.PHOTOS
                        "VIDEOS" -> MediaFilter.VIDEOS
                        else -> MediaFilter.ALL
                    }
                    val anchor = if (targetIndex < state.fullscreenWindowStartIndex) {
                        state.fullscreenMediaList.firstOrNull()
                    } else {
                        state.fullscreenMediaList.lastOrNull()
                    } ?: run {
                        _uiState.update { it.copy(fullscreenLoading = false) }
                        return@launch
                    }

                    val window = mediaRepository.loadSearchViewerWindow(
                        item = anchor,
                        query = state.fullscreenSearchQuery,
                        filter = filter,
                        favoritesOnly = state.fullscreenSearchFavoriteOnly,
                        radius = 2
                    )
                    _uiState.update {
                        it.copy(
                            fullscreenMediaList = window.items,
                            fullscreenWindowStartIndex = window.startIndex,
                            fullscreenTotalCount = window.totalCount,
                            fullscreenMediaIndex = targetIndex,
                            fullscreenLoading = false
                        )
                    }
                    return@launch
                }

                val window = mediaRepository.loadViewerWindow(
                    source,
                    targetIndex,
                    radius = 2,
                    albumId = state.fullscreenAlbumId
                )
                _uiState.update {
                    it.copy(
                        fullscreenMediaList = window.items,
                        fullscreenWindowStartIndex = window.startIndex,
                        fullscreenTotalCount = window.totalCount,
                        fullscreenMediaIndex = targetIndex,
                        fullscreenLoading = false
                    )
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _uiState.update { it.copy(fullscreenLoading = false) }
            }
        }
    }

    fun nextMedia() {
        val curr = _uiState.value.fullscreenMediaIndex ?: return
        moveFullscreenMedia(curr + 1)
    }

    fun previousMedia() {
        val curr = _uiState.value.fullscreenMediaIndex ?: return
        moveFullscreenMedia(curr - 1)
    }

    // --- Browse / Categories Actions ---

    fun selectCategory(category: CategoryType?) {
        _uiState.update { it.copy(selectedCategory = category, isRecycleBinOpen = false) }
        if (category != null) {
            viewModelScope.launch {
                val list = repository.getFilesByCategory(category)
                _uiState.update { it.copy(categoryFiles = list) }
            }
        } else {
            calculateCategoryCounts()
            loadStorageStats()
        }
    }

    private fun calculateCategoryCounts() {
        viewModelScope.launch {
            val counts = repository.getCategoryCounts()
            val sizes = repository.getCategorySizes()
            _uiState.update { it.copy(categoryCounts = counts, categorySizes = sizes) }
        }
    }

    val rootPath: String get() = repository.rootPath

    fun refreshHomeScreen() {
        viewModelScope.launch {
            loadStorageStats()
            calculateCategoryCounts()
            showMessage("Counts & storage refreshed")
        }
    }

    // --- In-App File Viewers ---

    fun openFile(fileItem: FileItem) {
        viewModelScope.launch {
            repository.recordRecent(fileItem)

            when {
                fileItem.isImage || fileItem.isVideo -> {
                    // Open in Fullscreen Media Viewer
                    val mediaItem = MediaItem(
                        id = fileItem.path.hashCode().toLong(),
                        uri = fileItem.uri ?: android.net.Uri.fromFile(File(fileItem.path)),
                        name = fileItem.name,
                        path = fileItem.path,
                        size = fileItem.size,
                        dateAdded = fileItem.lastModified,
                        mimeType = fileItem.mimeType,
                        isVideo = fileItem.isVideo,
                        isFavorite = fileItem.isFavorite
                    )
                    openStandaloneFullscreenMedia(mediaItem)
                }
                fileItem.isAudio -> {
                    playAudio(fileItem)
                }
                fileItem.isArchive -> {
                    openZip(fileItem)
                }
                fileItem.isTextEditable -> {
                    openTextEditor(fileItem)
                }
                else -> {
                    // Show Details
                    openProperties(fileItem)
                }
            }
        }
    }

    fun openTextEditor(fileItem: FileItem) {
        viewModelScope.launch {
            val text = repository.readText(fileItem.path)
            _uiState.update {
                it.copy(
                    activeTextFile = fileItem,
                    textFileContent = text,
                    isEditingText = false
                )
            }
        }
    }

    fun closeTextEditor() {
        _uiState.update {
            it.copy(
                activeTextFile = null,
                textFileContent = "",
                isEditingText = false
            )
        }
    }

    fun toggleTextEditing(editing: Boolean) {
        _uiState.update { it.copy(isEditingText = editing) }
    }

    fun updateTextContent(newContent: String) {
        _uiState.update { it.copy(textFileContent = newContent) }
    }

    fun saveTextFile() {
        val file = _uiState.value.activeTextFile ?: return
        val content = _uiState.value.textFileContent
        viewModelScope.launch {
            val ok = repository.writeText(file.path, content)
            if (ok) {
                showMessage("Saved changes to ${file.name}")
                _uiState.update { it.copy(isEditingText = false) }
                loadFiles()
            } else {
                showMessage("Failed to save ${file.name}")
            }
        }
    }

    fun openZip(fileItem: FileItem) {
        viewModelScope.launch {
            val entries = repository.listZipEntries(fileItem.path)
            _uiState.update {
                it.copy(
                    activeZipFile = fileItem,
                    zipEntries = entries
                )
            }
        }
    }

    fun closeZip() {
        _uiState.update {
            it.copy(
                activeZipFile = null,
                zipEntries = emptyList(),
                isExtractingZip = false
            )
        }
    }

    fun extractCurrentZip() {
        val zip = _uiState.value.activeZipFile ?: return
        val dest = File(zip.path).parentFile?.absolutePath ?: _uiState.value.currentPath
        val extractFolder = File(dest, zip.name.substringBeforeLast(".")).absolutePath

        viewModelScope.launch {
            _uiState.update { it.copy(isExtractingZip = true) }
            val ok = repository.extractZip(zip.path, extractFolder)
            _uiState.update { it.copy(isExtractingZip = false) }
            if (ok) {
                showMessage("Extracted to ${File(extractFolder).name}")
                closeZip()
                loadFiles()
            } else {
                showMessage("Extraction failed")
            }
        }
    }

    fun openProperties(fileItem: FileItem) {
        _uiState.update { it.copy(activeDetailItem = fileItem, activeFileConnectedDots = emptyList()) }
        loadConnectedDotsForFile(fileItem.path)
    }

    fun closeProperties() {
        _uiState.update { it.copy(activeDetailItem = null) }
    }

    // Audio Playback
    fun playAudio(fileItem: FileItem) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(fileItem.path)
                prepare()
                start()
                setOnCompletionListener {
                    _uiState.update { it.copy(isAudioPlaying = false, audioPositionMs = 0) }
                }
            }
            val dur = mediaPlayer?.duration ?: 0
            _uiState.update {
                it.copy(
                    activeAudioFile = fileItem,
                    isAudioPlaying = true,
                    audioDurationMs = dur,
                    audioPositionMs = 0
                )
            }
            startAudioTracking()
        } catch (e: Exception) {
            e.printStackTrace()
            showMessage("Could not play audio: ${e.message}")
        }
    }

    fun toggleAudioPlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _uiState.update { it.copy(isAudioPlaying = false) }
        } else {
            mp.start()
            _uiState.update { it.copy(isAudioPlaying = true) }
            startAudioTracking()
        }
    }

    fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        audioProgressJob?.cancel()
        _uiState.update {
            it.copy(
                activeAudioFile = null,
                isAudioPlaying = false,
                audioDurationMs = 0,
                audioPositionMs = 0
            )
        }
    }

    private fun startAudioTracking() {
        audioProgressJob?.cancel()
        audioProgressJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive && mediaPlayer != null && _uiState.value.isAudioPlaying) {
                val pos = mediaPlayer?.currentPosition ?: 0
                _uiState.update { it.copy(audioPositionMs = pos) }
                delay(300)
            }
        }
    }

    // Favorites & Recents
    fun toggleFavorite(fileItem: FileItem) {
        viewModelScope.launch {
            val isNowFav = repository.toggleFavorite(fileItem)
            showMessage(if (isNowFav) "Added to Favorites" else "Removed from Favorites")

            _uiState.update { state ->
                state.copy(fullscreenMediaList = state.fullscreenMediaList.map { media ->
                    if (media.path == fileItem.path) media.copy(isFavorite = isNowFav) else media
                })
            }
            loadFiles()
            refreshGallery()
        }
    }

    // Recycle Bin / Trash
    fun restoreTrashItem(trashEntity: TrashEntity) {
        viewModelScope.launch {
            val ok = repository.restoreTrashItem(trashEntity)
            if (ok) {
                showMessage("Restored ${trashEntity.name}")
                loadFiles()
                loadStorageStats()
            } else {
                showMessage("Could not restore ${trashEntity.name}")
            }
        }
    }

    fun permanentlyDeleteTrash(trashEntity: TrashEntity) {
        viewModelScope.launch {
            val ok = repository.permanentlyDeleteTrash(trashEntity)
            if (ok) {
                showMessage("Permanently deleted ${trashEntity.name}")
                loadStorageStats()
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.clearTrash()
            showMessage("Recycle Bin emptied")
            loadStorageStats()
        }
    }

    fun openRecycleBin() {
        _uiState.update { it.copy(isRecycleBinOpen = true, selectedCategory = null, currentTab = MainTab.HOME) }
    }

    fun closeRecycleBin() {
        _uiState.update { it.copy(isRecycleBinOpen = false) }
    }

    // --- Knowledge Graph & RAG Actions ---

    fun setShowAiSettings(show: Boolean) {
        _uiState.update { it.copy(isAiSettingsScreenOpen = show, showAiSettingsDialog = show) }
    }

    fun saveAiConfig(config: AiProviderConfigEntity) {
        viewModelScope.launch {
            kgRepository.saveAiConfig(config)
            _uiState.update { it.copy(aiConfig = config) }
            showMessage("AI settings saved")
        }
    }

    fun fetchAiModels(config: AiProviderConfigEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingAiModels = true, aiModelFetchError = null) }
            try {
                val all = kgRepository.listAiModels(config)
                _uiState.update {
                    it.copy(
                        aiModels = all.filter { model -> !model.supportsEmbedding },
                        aiVisionModels = all.filter { model -> model.supportsVision },
                        aiEmbeddingModels = all.filter { model -> model.supportsEmbedding },
                        isFetchingAiModels = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isFetchingAiModels = false, aiModelFetchError = e.message ?: "Could not fetch models") }
            }
        }
    }

    fun testAiConnection(config: AiProviderConfigEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingAiConnection = true, aiTestResult = null) }
            val result = kgRepository.testConnection(config)
            _uiState.update { it.copy(isTestingAiConnection = false, aiTestResult = result) }
        }
    }

    fun clearKnowledgeGraph() {
        viewModelScope.launch {
            kgRepository.clearGraph()
            _uiState.update { it.copy(ragAnswer = null, activeFileConnectedDots = emptyList()) }
            showMessage("Knowledge Graph cleared")
        }
    }

    fun queryRag(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRagQuerying = true) }
            val answer = kgRepository.queryRag(question)
            _uiState.update { it.copy(isRagQuerying = false, ragAnswer = answer) }
        }
    }

    fun loadConnectedDotsForFile(filePath: String) {
        viewModelScope.launch {
            val dots = kgRepository.getConnectedDotsForFile(filePath)
            _uiState.update { it.copy(activeFileConnectedDots = dots) }
        }
    }

    fun indexAllFilesForKnowledgeGraph() {
        if (_uiState.value.isKgIndexing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isKgIndexing = true,
                    kgIndexingProgress = 0.05f,
                    kgIndexingStatus = "Connecting storage files & photos..."
                )
            }
            try {
                val config = kgRepository.getAiConfig()
                data class FileCandidate(val file: File, val uri: Uri?)
                val candidateMap = mutableMapOf<String, FileCandidate>()

                val supportedBrainExtensions = setOf(
                    "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp",
                    "txt", "md", "json", "csv", "xml", "html", "htm", "log", "kt", "java", "py", "js", "ts",
                    "c", "cpp", "properties", "sql", "yaml", "yml", "pdf", "conf", "ini", "tsv", "gradle", "kts", "env"
                )
                val scanRoots = listOf(File(repository.rootPath), repository.baseWorkingDir).distinctBy { it.absolutePath }
                for (root in scanRoots) {
                    if (!root.exists() || !root.isDirectory) continue
                    root.walkTopDown()
                        .onEnter { dir -> dir.name !in setOf("Android", ".trash", "cache", ".thumbnails") }
                        .filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in supportedBrainExtensions }
                        .forEach { file -> candidateMap[file.absolutePath] = FileCandidate(file, Uri.fromFile(file)) }
                }

                // Keep explicitly selected/recent/favorite files even when outside the standard roots.
                for (item in _uiState.value.files) if (!item.isDirectory) candidateMap[item.path] = FileCandidate(File(item.path), item.uri)
                for (recent in _uiState.value.recentsList) candidateMap[recent.path] = FileCandidate(File(recent.path), Uri.fromFile(recent.path))
                for (fav in _uiState.value.favoritesList) candidateMap[fav.path] = FileCandidate(File(fav.path), Uri.fromFile(fav.path))

                val distinctCandidates = candidateMap.values.toList()
                val total = distinctCandidates.size.coerceAtLeast(1)

                distinctCandidates.forEachIndexed { index, candidate ->
                    val progress = ((index + 1).toFloat() / total.toFloat()).coerceIn(0.1f, 0.95f)
                    _uiState.update {
                        it.copy(
                            kgIndexingProgress = progress,
                            kgIndexingStatus = "Connecting dots: ${candidate.file.name} (${index + 1}/$total)"
                        )
                    }
                    kgRepository.indexFile(candidate.file, candidate.uri, config)
                }

                val suggestions = try { kgRepository.getSmartSuggestions() } catch (_: Exception) { emptyList() }
                _uiState.update {
                    it.copy(
                        isKgIndexing = false,
                        kgIndexingProgress = 1f,
                        kgIndexingStatus = "Connected ${distinctCandidates.size} files in Brain",
                        kgSmartSuggestions = suggestions
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isKgIndexing = false,
                        kgIndexingStatus = "Indexing error: ${e.message}"
                    )
                }
            }
        }
    }

    fun loadStorageStats() {
        viewModelScope.launch {
            val stats = repository.getStorageStats()
            _uiState.update { it.copy(storageStats = stats) }
        }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(userMessage = msg) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        audioProgressJob?.cancel()
    }
}

private fun MediaItem.toFileItem(): FileItem = FileItem(
    name = name,
    path = path,
    size = size,
    lastModified = dateAdded,
    isDirectory = false,
    mimeType = mimeType,
    isFavorite = isFavorite
)
