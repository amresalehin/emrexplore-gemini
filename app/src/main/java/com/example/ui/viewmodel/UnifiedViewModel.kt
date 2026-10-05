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
import com.example.data.media.MediaMetadataRepository
import com.example.data.ai.BrainTargetedOperationStore
import com.example.data.brain.BrainRepository
import com.example.data.ai.AvailableAiModel
import com.example.data.brain.ConnectedDotsItem
import com.example.data.ai.ConnectionTestResult
import com.example.data.brain.RagAnswer
import com.example.data.brain.AttachedAiFile
import com.example.data.brain.AskAiChatMessage
import com.example.data.local.AiProviderConfigEntity
import com.example.data.brain.BrainTopicEntity
import com.example.data.brain.BrainEdgeEntity
import com.example.data.brain.BrainNodeEntity
import com.example.data.local.MediaMetadataEntity
import com.example.data.metadata.MetadataExtractor
import com.example.data.metadata.MetadataReport
import com.example.data.brain.BrainTopicFile
import com.example.data.brain.BrainModelDownloadWorker
import com.example.data.brain.OnDeviceBrainModelStatus
import com.example.data.brain.OnDeviceBrainModelUiState
import com.example.data.brain.OnDeviceBrainModelSpec
import com.example.data.model.ConflictResolution
import com.example.data.model.FileOperationProgress
import com.example.data.model.OperationStatus
import com.example.data.model.OperationType
import com.example.data.performance.PerformanceMetrics
import com.example.data.performance.PerformanceMonitor
import com.example.data.repository.FileRepository
import kotlinx.coroutines.CancellationException
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
import androidx.paging.map
import androidx.paging.cachedIn
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

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
    val explorerTabs: List<ExplorerTab> = emptyList(),
    val activeExplorerTabId: String = "",
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
    val galleryGroupBy: String = "Month",
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
    val savedTextFileContent: String = "",
    val isEditingText: Boolean = false,
    val activeZipFile: FileItem? = null,
    val zipEntries: List<String> = emptyList(),
    val isExtractingZip: Boolean = false,
    val activeDetailItem: FileItem? = null,
    val activeMetadataReport: MetadataReport? = null,

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
    val brainNodes: List<BrainNodeEntity> = emptyList(),
    val brainEdges: List<BrainEdgeEntity> = emptyList(),
    val brainNodeCount: Int = 0,
    val brainEdgeCount: Int = 0,
    val brainChunkCount: Int = 0,
    val aiConfig: AiProviderConfigEntity = AiProviderConfigEntity(),
    val isTestingAiConnection: Boolean = false,
    val aiTestResult: ConnectionTestResult? = null,
    val isAiSettingsScreenOpen: Boolean = false,
    val aiConfigLoaded: Boolean = false,
    val isBrainIndexing: Boolean = false,
    val isGalleryAiProcessing: Boolean = false,
    val isGalleryAiPaused: Boolean = false,
    val galleryAiProgress: Float = 0f,
    val galleryAiStatus: String = "Ready",
    val brainIndexingProgress: Float = 0f,
    val brainIndexingStatus: String = "Ready",
    val ragAnswer: RagAnswer? = null,
    val isRagQuerying: Boolean = false,
    val askAiMessages: List<AskAiChatMessage> = emptyList(),
    val attachedAiFile: AttachedAiFile? = null,
    val activeFileConnectedDots: List<ConnectedDotsItem> = emptyList(),
    val brainSmartSuggestions: List<String> = emptyList(),
    val brainTopics: List<BrainTopicEntity> = emptyList(),
    val selectedBrainTopicId: String? = null,
    val brainTopicRelevantFiles: List<BrainTopicFile> = emptyList(),
    val isBrainTopicLoading: Boolean = false,
    val brainTopicStatus: String = "",
    val aiModels: List<AvailableAiModel> = emptyList(),
    val aiVisionModels: List<AvailableAiModel> = emptyList(),
    val aiEmbeddingModels: List<AvailableAiModel> = emptyList(),
    val aiMultimodalEmbeddingModels: List<AvailableAiModel> = emptyList(),
    val offlineBrainModels: List<OnDeviceBrainModelSpec> = emptyList(),
    val isFetchingAiModels: Boolean = false,
    val aiModelFetchError: String? = null,
    val onDeviceBrainModel: OnDeviceBrainModelUiState = OnDeviceBrainModelUiState(),

    // User Feedback
    val userMessage: String? = null
)

class UnifiedViewModel(application: Application) : AndroidViewModel(application) {

    private val brainRepository = BrainRepository(application)
    private val repository = FileRepository(application) { relocatedPaths, removedPaths ->
        brainRepository.reconcileMutation(relocatedPaths, removedPaths)
    }
    private val metadataExtractor = MetadataExtractor(application.applicationContext)
    private val mediaRepository = MediaRepository(application)
    private val mediaMetadataRepository = MediaMetadataRepository(application.applicationContext)
    private val galleryAiStore = BrainTargetedOperationStore(application.applicationContext)
    private val galleryFilterFlow = MutableStateFlow<MediaFilter?>(MediaFilter.ALL)
    private val galleryDateFilterFlow = MutableStateFlow(GalleryDateFilter.ALL)
    private val galleryLocationFilterFlow = MutableStateFlow(GalleryLocationFilter.ALL)
    private val galleryRefreshFlow = MutableStateFlow(0L)
    private val gallerySearchFlow = MutableStateFlow("")
    private val gallerySortFlow = MutableStateFlow(GallerySortOption.DATE_DESC)

    fun setGalleryGroupBy(groupBy: String) {
        _uiState.update { it.copy(galleryGroupBy = groupBy) }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun albumPagingFlow(albumId: String): Flow<PagingData<MediaItem>> =
        mediaRepository.albumPager(albumId, _uiState.value.gallerySortOption)

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
                mediaRepository.favoritesPager(sort)
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
    private var ragQueryJob: Job? = null
    private var ragRequestId: Long = 0L
    private var permissionRefreshJob: Job? = null
    private var permissionsInitialized = false

    init {
        // Collect decoupled file operations progress
        viewModelScope.launch {
            repository.operationManager.progress.collect { progress ->
                _uiState.update { it.copy(fileOperationProgress = progress) }
                if (progress.status == OperationStatus.COMPLETED) {
                    if (progress.type == OperationType.COPY || progress.type == OperationType.MOVE) {
                        _uiState.update { state ->
                            if (state.clipboard != null) state.copy(clipboard = null) else state
                        }
                    }
                    loadFiles()
                    refreshGallery()
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
            // Remove any legacy demo files before the first real storage/count read.
            repository.initializeStorageDefaults()
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

            // File indexing may continue, but Brain/AI indexing is strictly opt-in.
            launch(Dispatchers.IO) {
                if (prefs.autoIndexOnStart && repository.totalIndexedCount() == 0) {
                    repository.indexStorage(force = false)
                }
            }

        }
        // Collect Room Database Flows
        // Suggestions are stable during a sync; refresh once at startup and again after indexing completes.
        viewModelScope.launch(Dispatchers.IO) {
            val suggestions = try { brainRepository.getSmartSuggestions() } catch (_: Exception) { emptyList() }
            _uiState.update { it.copy(brainSmartSuggestions = suggestions) }
        }
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
        // Gallery Brain processing is WorkManager-backed and its queue/checkpoint is durable outside
        // the ViewModel. Reattaching here keeps the UI accurate after process death.
        _uiState.update { it.copy(isGalleryAiPaused = galleryAiStore.isPaused()) }
        viewModelScope.launch {
            WorkManager.getInstance(getApplication<Application>())
                .getWorkInfosForUniqueWorkFlow(com.example.data.ai.BrainIndexWorker.TARGETED_UNIQUE_NAME)
                .collectLatest { works ->
                    val work = works.firstOrNull() ?: run {
                        if (galleryAiStore.isPaused()) {
                            val total = galleryAiStore.totalCount()
                            val completed = galleryAiStore.completedCount()
                            _uiState.update {
                                it.copy(
                                    isGalleryAiProcessing = false,
                                    isGalleryAiPaused = true,
                                    galleryAiProgress = if (total > 0) completed.toFloat() / total else 0f,
                                    galleryAiStatus = "Brain processing paused"
                                )
                            }
                        }
                        return@collectLatest
                    }
                    val progress = work.progress
                    val storeTotal = galleryAiStore.totalCount()
                    val storeCompleted = galleryAiStore.completedCount()
                    val total = progress.getInt("total", storeTotal)
                    val current = progress.getInt("current", storeCompleted)
                    val path = progress.getString("path").orEmpty()
                    when (work.state) {
                        androidx.work.WorkInfo.State.RUNNING,
                        androidx.work.WorkInfo.State.ENQUEUED -> _uiState.update {
                            it.copy(
                                isGalleryAiProcessing = true,
                                isGalleryAiPaused = false,
                                galleryAiProgress = if (total > 0) current.toFloat() / total else 0f,
                                galleryAiStatus = if (path.isBlank()) "Processing with Brain..." else "Brain: " + File(path).name + " ($current/$total)"
                            )
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            val processed = work.outputData.getInt("indexed", current)
                            galleryAiStore.clear()
                            _uiState.update {
                                it.copy(
                                    isGalleryAiProcessing = false,
                                    isGalleryAiPaused = false,
                                    galleryAiProgress = 1f,
                                    galleryAiStatus = "Brain processing complete ($processed processed)"
                                )
                            }
                            refreshGallery()
                            refreshBrainTopicFiles(_uiState.value.selectedBrainTopicId)
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            val hasPending = galleryAiStore.hasPendingWork()
                            val error = work.outputData.getString("error")
                            _uiState.update {
                                it.copy(
                                    isGalleryAiProcessing = false,
                                    isGalleryAiPaused = hasPending,
                                    galleryAiStatus = if (hasPending) {
                                        "Brain stopped — Resume to retry" + (error?.let { ": $it" } ?: "")
                                    } else {
                                        error ?: "Brain processing failed"
                                    }
                                )
                            }
                        }
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            val paused = galleryAiStore.isPaused() && galleryAiStore.hasPendingWork()
                            val total = galleryAiStore.totalCount()
                            val completed = galleryAiStore.completedCount()
                            _uiState.update {
                                it.copy(
                                    isGalleryAiProcessing = false,
                                    isGalleryAiPaused = paused,
                                    galleryAiProgress = if (total > 0) completed.toFloat() / total else 0f,
                                    galleryAiStatus = if (paused) "Brain processing paused" else "Brain processing cancelled"
                                )
                            }
                        }
                        else -> Unit
                    }
                }
        }


        // Brain indexing is WorkManager-backed. Reattach the UI to the durable
        // unique work after process recreation instead of relying on ViewModel state.
        viewModelScope.launch {
            WorkManager.getInstance(getApplication<Application>())
                .getWorkInfosForUniqueWorkFlow(com.example.data.ai.BrainIndexWorker.UNIQUE_NAME)
                .collectLatest { works ->
                    val work = works.firstOrNull() ?: run {
                        val ready = brainRepository.getOnDeviceBrainModelState()
                        _uiState.update { state ->
                            state.copy(
                                isBrainIndexing = false,
                                onDeviceBrainModel = if (state.onDeviceBrainModel.status == OnDeviceBrainModelStatus.DOWNLOADING) state.onDeviceBrainModel else ready
                            )
                        }
                        return@collectLatest
                    }
                    val progress = work.progress
                    val total = progress.getInt("total", 0)
                    val current = progress.getInt("current", 0)
                    val path = progress.getString("path").orEmpty()
                    when (work.state) {
                        androidx.work.WorkInfo.State.RUNNING -> _uiState.update {
                            it.copy(
                                isBrainIndexing = true,
                                brainIndexingProgress = if (total > 0) current.toFloat() / total else 0f,
                                brainIndexingStatus = if (path.isBlank()) "Indexing Brain..." else "Indexing " + File(path).name + " ($current/$total)"
                            )
                        }
                        androidx.work.WorkInfo.State.ENQUEUED -> _uiState.update {
                            it.copy(isBrainIndexing = true, brainIndexingStatus = "Brain indexing queued...")
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            _uiState.update {
                                it.copy(
                                    isBrainIndexing = false,
                                    brainIndexingProgress = 1f
                                )
                            }
                            refreshBrainTopicFiles(_uiState.value.selectedBrainTopicId)
                        }
                        else -> _uiState.update {
                            it.copy(
                                isBrainIndexing = false,
                                brainIndexingProgress = it.brainIndexingProgress
                            )
                        }
                    }
                }
        }
        // On-device semantic model download is durable and independent of Brain indexing.
        viewModelScope.launch(Dispatchers.IO) {
            val specs = brainRepository.getOnDeviceBrainModelSpecs()
            _uiState.update {
                it.copy(
                    offlineBrainModels = specs,
                    onDeviceBrainModel = brainRepository.getOnDeviceBrainModelState()
                )
            }
        }
        viewModelScope.launch {
            WorkManager.getInstance(getApplication<Application>())
                .getWorkInfosForUniqueWorkFlow(BrainModelDownloadWorker.UNIQUE_NAME)
                .collectLatest { works ->
                    val work = works.firstOrNull() ?: return@collectLatest
                    val progress = work.progress
                    when (work.state) {
                        androidx.work.WorkInfo.State.ENQUEUED,
                        androidx.work.WorkInfo.State.RUNNING -> {
                            val p = progress.getFloat("progress", 0f).coerceIn(0f, 1f)
                            val downloaded = progress.getLong("downloadedBytes", 0L)
                            val total = progress.getLong("totalBytes", 0L)
                            val spec = brainRepository.getOnDeviceBrainModelSpec()
                            _uiState.update {
                                it.copy(
                                    onDeviceBrainModel = OnDeviceBrainModelUiState(
                                        status = OnDeviceBrainModelStatus.DOWNLOADING,
                                        modelId = spec.id,
                                        displayName = spec.displayName,
                                        sizeLabel = spec.sizeLabel,
                                        progress = p,
                                        downloadedBytes = downloaded,
                                        totalBytes = total
                                    )
                                )
                            }
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            val ready = brainRepository.getOnDeviceBrainModelState()
                            _uiState.update { it.copy(onDeviceBrainModel = ready) }
                            // BrainModelDownloadWorker enqueues the unique Brain index work
                            // after the model is installed. Do not start a second indexing job
                            // from the ViewModel; WorkManager is the single owner of that handoff.
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            val error = work.outputData.getString("error") ?: "Model download failed"
                            _uiState.update {
                                it.copy(onDeviceBrainModel = brainRepository.getOnDeviceBrainModelState(error))
                            }
                        }
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            // Cancellation is a terminal unavailable state, not a download error.
                            _uiState.update {
                                it.copy(onDeviceBrainModel = brainRepository.getOnDeviceBrainModelState())
                            }
                        }
                        else -> Unit
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
            brainRepository.allNodesFlow.collectLatest { nodes ->
                _uiState.update { it.copy(brainNodes = nodes) }
            }
        }
        viewModelScope.launch {
            brainRepository.allEdgesFlow.collectLatest { edges ->
                _uiState.update { it.copy(brainEdges = edges) }
            }
        }
        viewModelScope.launch {
            brainRepository.brainTopicsFlow.collectLatest { topics ->
                val previousId = _uiState.value.selectedBrainTopicId
                val selectedId = previousId?.takeIf { id -> topics.any { it.id == id } }
                    ?: topics.firstOrNull()?.id
                _uiState.update {
                    it.copy(
                        brainTopics = topics,
                        selectedBrainTopicId = selectedId
                    )
                }
                if (selectedId != previousId) {
                    refreshBrainTopicFiles(selectedId)
                }
            }
        }

        viewModelScope.launch {
            brainRepository.aiConfigFlow.collectLatest { config ->
                val selectedTopicId = _uiState.value.selectedBrainTopicId
                _uiState.update {
                    it.copy(
                        aiConfigLoaded = true,
                        aiConfig = config ?: it.aiConfig
                    )
                }
                if (selectedTopicId != null) {
                    refreshBrainTopicFiles(selectedTopicId)
                }
            }
        }
        viewModelScope.launch {
            brainRepository.chunkCountFlow.collectLatest { count ->
                _uiState.update { it.copy(brainChunkCount = count) }
            }
        }
        viewModelScope.launch {
            brainRepository.nodeCountFlow.collectLatest { count ->
                _uiState.update { it.copy(brainNodeCount = count) }
            }
        }
        viewModelScope.launch {
            brainRepository.edgeCountFlow.collectLatest { count ->
                _uiState.update { it.copy(brainEdgeCount = count) }
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
                // Brain work is explicitly started by the user after AI setup.
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

    fun openNewExplorerTab(path: String = _uiState.value.currentPath.ifBlank { repository.rootPath }) {
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
                explorerSearchActive = false,
                explorerSearchResults = emptyList(),
                explorerFilterType = ExplorerFilterType.ALL,
                explorerDateFilter = ExplorerDateFilter.ALL,
                explorerSizeFilter = ExplorerSizeFilter.ALL,
                explorerFilterBarVisible = false,
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
                val initialItems = sorted.take(120)
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

    fun canNavigateUp(): Boolean {
        val current = File(_uiState.value.currentPath).absoluteFile
        val root = File(repository.rootPath).absoluteFile
        return current != root && current.parentFile?.canRead() == true
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
        if (permissionsInitialized || permissionRefreshJob?.isActive == true) return
        permissionsInitialized = true
        permissionRefreshJob = viewModelScope.launch {
            try {
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
                // Storage permission alone must never start Brain/AI indexing.
            } finally {
                permissionRefreshJob = null
            }
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
        val state = _uiState.value
        val queryOrFilterActive =
            state.searchQuery.isNotBlank() ||
                state.explorerSearchActive ||
                state.explorerFilterType != ExplorerFilterType.ALL ||
                state.explorerDateFilter != ExplorerDateFilter.ALL ||
                state.explorerSizeFilter != ExplorerSizeFilter.ALL

        if (!queryOrFilterActive) {
            viewModelScope.launch {
                val all = repository.getFiles(state.currentPath, state.showHidden)
                _uiState.update { current ->
                    current.copy(
                        selectedPaths = all.map { it.path }.toSet(),
                        isSelectionMode = all.isNotEmpty()
                    )
                }
            }
            return
        }

        val source = if (queryOrFilterActive) state.explorerSearchResults else state.files
        val allPaths = source.map { it.path }.toSet()
        _uiState.update {
            it.copy(selectedPaths = allPaths, isSelectionMode = allPaths.isNotEmpty())
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

        _uiState.update { it.copy(userMessage = "$actionName operation started in background") }
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
        repository.operationManager.startDelete(listOf(path), toTrash)
        _uiState.update {
            it.copy(userMessage = if (toTrash) "Delete operation started" else "Permanent delete started")
        }
    }

    fun deletePathsAfterConfirmation(paths: List<String>, toTrash: Boolean = true) {
        val selected = paths.distinct().filter { it.isNotBlank() }
        if (selected.isEmpty()) return
        repository.operationManager.startDelete(selected, toTrash)
        clearSelection()
        _uiState.update {
            it.copy(userMessage = if (toTrash) "Delete operation started" else "Permanent delete started")
        }
    }

    fun deleteSelected(toTrash: Boolean = true) {
        deletePathsAfterConfirmation(_uiState.value.selectedPaths.toList(), toTrash)
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

    fun processGalleryAiSelection() {
        val selected = _uiState.value.gallerySelection.filter { !it.isVideo }
        if (selected.isEmpty()) return
        if (!brainRepository.isOnDeviceBrainModelReady()) {
            showMessage("Download the on-device Brain model before processing gallery images")
            return
        }

        val paths = selected.mapNotNull { it.path.takeIf { p -> p.isNotBlank() } }.distinct()
        if (paths.isEmpty()) {
            showMessage("Selected gallery images do not expose readable file paths")
            return
        }
        if (paths.size > 80) {
            showMessage("Brain processing is limited to 80 selected images per action")
            return
        }

        _uiState.update { it.copy(gallerySelection = emptyList()) }
        startGalleryAiProcessing(paths)
    }

    private fun startGalleryAiProcessing(paths: Collection<String>, force: Boolean = false) {
        val cleanPaths = paths.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanPaths.isEmpty()) return

        galleryAiStore.addPaths(cleanPaths, force = force)
        galleryAiStore.setPaused(false)
        val total = galleryAiStore.totalCount()
        val completed = galleryAiStore.completedCount()
        _uiState.update {
            it.copy(
                isGalleryAiProcessing = true,
                isGalleryAiPaused = false,
                galleryAiProgress = if (total > 0) completed.toFloat() / total else 0f,
                galleryAiStatus = "Brain processing queued..."
            )
        }

        enqueueGalleryBrainWorker()
    }

    private fun enqueueGalleryBrainWorker() {
        val paths = galleryAiStore.pendingPaths()
        if (paths.isEmpty()) return
        val forcePaths = paths.filter { galleryAiStore.isForce(it) }
        val request = OneTimeWorkRequestBuilder<com.example.data.ai.BrainIndexWorker>()
            .setInputData(
                androidx.work.workDataOf(
                    "paths" to paths.toTypedArray(),
                    "forcePaths" to forcePaths.toTypedArray()
                )
            )
            .build()
        WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(
            com.example.data.ai.BrainIndexWorker.TARGETED_UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun pauseGalleryAi() {
        if (!galleryAiStore.hasPendingWork()) {
            showMessage("No gallery Brain processing is active")
            return
        }
        galleryAiStore.setPaused(true)
        _uiState.update {
            it.copy(
                isGalleryAiProcessing = false,
                isGalleryAiPaused = true,
                galleryAiStatus = "Pausing Brain processing..."
            )
        }
        WorkManager.getInstance(getApplication<Application>())
            .cancelUniqueWork(com.example.data.ai.BrainIndexWorker.TARGETED_UNIQUE_NAME)
    }

    fun cancelGalleryAi() {
        val hadWork = galleryAiStore.hasPendingWork() || galleryAiStore.isPaused()
        WorkManager.getInstance(getApplication<Application>())
            .cancelUniqueWork(com.example.data.ai.BrainIndexWorker.TARGETED_UNIQUE_NAME)
        galleryAiStore.clear()
        _uiState.update {
            it.copy(
                isGalleryAiProcessing = false,
                isGalleryAiPaused = false,
                galleryAiProgress = 0f,
                galleryAiStatus = if (hadWork) "Brain processing cancelled" else "Ready"
            )
        }
    }

    fun resumeGalleryAi() {
        if (!galleryAiStore.hasPendingWork()) {
            galleryAiStore.clear()
            _uiState.update {
                it.copy(
                    isGalleryAiProcessing = false,
                    isGalleryAiPaused = false,
                    galleryAiStatus = "Ready",
                    galleryAiProgress = 0f
                )
            }
            showMessage("No pending gallery AI work")
            return
        }
        if (!brainRepository.isOnDeviceBrainModelReady()) {
            showMessage("Download the on-device Brain model before resuming gallery processing")
            return
        }
        galleryAiStore.setPaused(false)
        _uiState.update {
            it.copy(
                isGalleryAiProcessing = true,
                isGalleryAiPaused = false,
                galleryAiStatus = "Resuming Brain processing..."
            )
        }
        enqueueGalleryBrainWorker()
    }

    fun reAnalyzeGalleryImage(item: MediaItem) {
        if (item.isVideo || item.path.isBlank()) return
        if (!brainRepository.isOnDeviceBrainModelReady()) {
            showMessage("Download the on-device Brain model before re-analyzing")
            return
        }
        startGalleryAiProcessing(listOf(item.path), force = true)
        showMessage("Re-analysis queued for ${item.name}")
    }

    suspend fun getBrainNode(item: MediaItem): BrainNodeEntity? =
        brainRepository.getBrainNode(item.path)

    fun deleteGallerySelection(toTrash: Boolean = true) {
        val selected = _uiState.value.gallerySelection
        if (selected.isEmpty()) return
        viewModelScope.launch {
            var count = 0
            selected.forEach { media ->
                if (repository.deleteFile(media.path, toTrash)) {
                    brainRepository.removeIndexedSource(media.path)
                    count++
                }
            }
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
                        radius = 2,
                        sort = stateBeforeOpen.gallerySortOption
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
                val absoluteIndex = mediaRepository.viewerPosition(
                    item,
                    resolvedSource,
                    albumId,
                    stateBeforeOpen.gallerySortOption
                )
                val window = mediaRepository.loadViewerWindow(
                    resolvedSource,
                    absoluteIndex,
                    radius = 2,
                    albumId = albumId,
                    sort = stateBeforeOpen.gallerySortOption
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
                        radius = 2,
                        sort = state.gallerySortOption
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
                    albumId = state.fullscreenAlbumId,
                    sort = state.gallerySortOption
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
                    savedTextFileContent = text,
                    isEditingText = false
                )
            }
        }
    }

    fun closeTextEditor() {
        val state = _uiState.value
        if (state.activeTextFile != null && state.textFileContent != state.savedTextFileContent) {
            showMessage("Unsaved changes — save or discard them before closing.")
            return
        }
        _uiState.update {
            it.copy(
                activeTextFile = null,
                textFileContent = "",
                savedTextFileContent = "",
                isEditingText = false
            )
        }
    }

    fun discardTextChanges() {
        _uiState.update {
            it.copy(
                activeTextFile = null,
                textFileContent = "",
                savedTextFileContent = "",
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

    fun saveTextFile(closeWhenDone: Boolean = false) {
        val file = _uiState.value.activeTextFile ?: return
        val content = _uiState.value.textFileContent
        viewModelScope.launch {
            val ok = repository.writeText(file.path, content)
            if (ok) {
                val brainReady = brainRepository.isOnDeviceBrainModelReady()
                val reindexed = if (brainReady) {
                    brainRepository.indexFile(
                        File(file.path),
                        config = brainRepository.getAiConfig(),
                        force = true
                    )
                } else true
                showMessage(
                    when {
                        !brainReady -> "Saved changes to " + file.name
                        reindexed -> "Saved changes to " + file.name
                        else -> "Saved changes to " + file.name + "; Brain will refresh later"
                    }
                )
                _uiState.update {
                    it.copy(isEditingText = false, savedTextFileContent = content)
                }
                loadFiles()
                if (closeWhenDone) closeTextEditor()
            } else {
                showMessage("Failed to save " + file.name)
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

    fun inspectMetadata(mediaItem: MediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val report = if (mediaItem.uri != null) {
                    metadataExtractor.extractFromUri(mediaItem.uri, mediaItem.name, mediaItem.size, mediaItem.path)
                } else {
                    metadataExtractor.extract(File(mediaItem.path))
                }
                _uiState.update { it.copy(activeMetadataReport = report) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showMessage("Could not inspect metadata")
            }
        }
    }

    fun closeMetadataInspector() {
        _uiState.update { it.copy(activeMetadataReport = null) }
    }

    // Audio Playback
    fun playAudio(fileItem: FileItem) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                if (fileItem.uri != null) {
                    setDataSource(getApplication<Application>(), fileItem.uri!!)
                } else {
                    setDataSource(fileItem.path)
                }
                setOnPreparedListener { mp ->
                    mp.start()
                    _uiState.update {
                        it.copy(
                            activeAudioFile = fileItem,
                            isAudioPlaying = true,
                            audioDurationMs = mp.duration,
                            audioPositionMs = 0
                        )
                    }
                    startAudioTracking()
                }
                setOnCompletionListener {
                    _uiState.update { it.copy(isAudioPlaying = false, audioPositionMs = 0) }
                }
                prepareAsync()
            }
            _uiState.update {
                it.copy(
                    activeAudioFile = fileItem,
                    isAudioPlaying = false,
                    audioDurationMs = 0,
                    audioPositionMs = 0
                )
            }
        } catch (e: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            e.printStackTrace()
            showMessage("Could not play audio: " + (e.message ?: "unknown error"))
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
                refreshGallery()
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
        _uiState.update { it.copy(isAiSettingsScreenOpen = show) }
    }

    fun saveAiConfig(config: AiProviderConfigEntity) {
        viewModelScope.launch {
            val saved = config.copy(
                apiKey = config.apiKey.trim(),
                baseUrl = config.baseUrl.trim()
            )
            brainRepository.saveAiConfig(saved)
            _uiState.update { it.copy(aiConfig = saved, aiConfigLoaded = true) }
            showMessage("AI settings saved")
        }
    }

    fun fetchAiModels(config: AiProviderConfigEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingAiModels = true, aiModelFetchError = null) }
            try {
                val all = brainRepository.listAiModels(config)
                _uiState.update {
                    it.copy(
                        aiModels = all.filter { model -> model.supportsChat },
                        aiVisionModels = all.filter { model -> model.supportsChat && model.supportsVision },
                        aiEmbeddingModels = all.filter { model -> model.supportsEmbedding && !model.supportsMultimodalEmbedding },
                        aiMultimodalEmbeddingModels = all.filter { model -> model.supportsMultimodalEmbedding },
                        isFetchingAiModels = false
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isFetchingAiModels = false, aiModelFetchError = e.message ?: "Could not fetch models") }
            }
        }
    }

    fun testAiConnection(config: AiProviderConfigEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingAiConnection = true, aiTestResult = null) }
            val result = brainRepository.testConnection(config)
            _uiState.update { it.copy(isTestingAiConnection = false, aiTestResult = result) }
        }
    }

    fun clearBrainIndex() {
        viewModelScope.launch {
            brainRepository.clearGraph()
            _uiState.update { it.copy(ragAnswer = null, activeFileConnectedDots = emptyList()) }
            showMessage("Knowledge Graph cleared")
        }
    }

    fun attachAiFile(file: File): AttachedAiFile {
        val ext = file.extension.lowercase()
        val isImg = ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
        val mime = if (isImg) "image/" + if (ext == "jpg") "jpeg" else ext else if (ext == "pdf") "application/pdf" else "application/octet-stream"
        val attached = AttachedAiFile(
            file = file,
            name = file.name,
            path = file.absolutePath,
            mimeType = mime,
            size = file.length(),
            isImage = isImg
        )
        _uiState.update { it.copy(attachedAiFile = attached) }
        return attached
    }

    fun askAiAboutFile(file: File) {
        if (!file.exists() || !file.isFile || !file.canRead()) {
            showMessage("This file is no longer readable.")
            return
        }
        val attached = attachAiFile(file)
        setTab(MainTab.BRAIN)
        queryRag("Tell me about this file: \${file.name}", attachedOverride = attached)
    }

    fun detachAiFile() {
        _uiState.update { it.copy(attachedAiFile = null) }
    }

    fun clearAskAiChat() {
        _uiState.update { it.copy(askAiMessages = emptyList(), ragAnswer = null) }
    }

    fun cancelRagQuery() {
        ragQueryJob?.cancel()
        ragQueryJob = null
        _uiState.update { it.copy(isRagQuerying = false) }
    }

    fun queryRag(question: String, attachedOverride: AttachedAiFile? = null) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) {
            _uiState.update { it.copy(ragAnswer = RagAnswer("Please enter a question.", isSuccessful = false)) }
            showMessage("Please enter a question")
            return
        }
        if (cleanQuestion.length > 4000) {
            val message = "Question is too long. Please keep it under 4,000 characters."
            _uiState.update { it.copy(ragAnswer = RagAnswer(message, isSuccessful = false)) }
            showMessage(message)
            return
        }

        ragQueryJob?.cancel()
        val requestId = ++ragRequestId
        val attached = attachedOverride ?: _uiState.value.attachedAiFile
        val priorHistory = _uiState.value.askAiMessages
            .filter { !it.isError }
            .filter { message ->
                if (attached != null) message.attachedFile?.path == attached.path
                else message.attachedFile == null
            }
            .takeLast(8)
            .map { if (it.isUser) "User" to it.text else "AI" to it.text }

        val userMsg = AskAiChatMessage(isUser = true, text = cleanQuestion, attachedFile = attached)
        _uiState.update {
            it.copy(
                isRagQuerying = true,
                askAiMessages = it.askAiMessages + userMsg
            )
        }

        ragQueryJob = viewModelScope.launch {
            val answer = try {
                if (attached != null) {
                    brainRepository.queryFileSpecifically(attached.file, cleanQuestion, priorHistory)
                } else {
                    brainRepository.queryRag(cleanQuestion, priorHistory)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                RagAnswer(
                    answer = "Brain query failed: " + (error.message ?: "unknown error"),
                    isSuccessful = false
                )
            }

            if (!isActive) return@launch

            val aiMsg = AskAiChatMessage(
                isUser = false,
                text = answer.answer,
                attachedFile = attached,
                referencedNodes = answer.connectedNodes,
                isError = !answer.isSuccessful
            )
            _uiState.update {
                it.copy(
                    isRagQuerying = false,
                    ragAnswer = answer,
                    askAiMessages = it.askAiMessages + aiMsg
                )
            }
            if (ragRequestId == requestId) {
                ragQueryJob = null
            }
        }
    }

    fun selectBrainTopic(topicId: String) {
        val exists = _uiState.value.brainTopics.any { it.id == topicId }
        if (!exists) return
        _uiState.update {
            it.copy(
                selectedBrainTopicId = topicId,
                brainTopicRelevantFiles = emptyList(),
                isBrainTopicLoading = true,
                brainTopicStatus = "Finding relevant files..."
            )
        }
        refreshBrainTopicFiles(topicId)
    }

    fun saveBrainTopic(heading: String, description: String, existingId: String?) {
        viewModelScope.launch {
            try {
                val saved = brainRepository.saveBrainTopic(existingId, heading, description)
                _uiState.update {
                    it.copy(
                        selectedBrainTopicId = saved.id,
                        brainTopicStatus = "Topic saved"
                    )
                }
                refreshBrainTopicFiles(saved.id, saved)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(brainTopicStatus = error.message ?: "Could not save topic") }
            }
        }
    }

    fun deleteBrainTopic(topicId: String) {
        viewModelScope.launch {
            brainRepository.deleteBrainTopic(topicId)
            val remaining = _uiState.value.brainTopics.filterNot { it.id == topicId }
            val nextId = remaining.firstOrNull()?.id
            _uiState.update {
                it.copy(
                    selectedBrainTopicId = nextId,
                    brainTopicRelevantFiles = emptyList(),
                    brainTopicStatus = if (nextId == null) "" else "Finding relevant files...",
                    isBrainTopicLoading = nextId != null
                )
            }
            refreshBrainTopicFiles(nextId)
        }
    }

    private fun refreshBrainTopicFiles(topicId: String?, topicOverride: BrainTopicEntity? = null) {
        if (topicId == null) {
            _uiState.update {
                it.copy(
                    brainTopicRelevantFiles = emptyList(),
                    isBrainTopicLoading = false,
                    brainTopicStatus = ""
                )
            }
            return
        }
        viewModelScope.launch {
            val topic = topicOverride ?: _uiState.value.brainTopics.firstOrNull { it.id == topicId }
            if (topic == null) {
                _uiState.update { it.copy(brainTopicRelevantFiles = emptyList(), isBrainTopicLoading = false) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    selectedBrainTopicId = topicId,
                    isBrainTopicLoading = true,
                    brainTopicStatus = "Finding relevant files..."
                )
            }
            try {
                val files = brainRepository.getRelevantFilesForBrainTopic(topic, _uiState.value.aiConfig, 12)
                val config = _uiState.value.aiConfig
                _uiState.update {
                    it.copy(
                        brainTopicRelevantFiles = files,
                        isBrainTopicLoading = false,
                        brainTopicStatus = if (files.isNotEmpty()) {
                            "${files.size} relevant files found"
                        } else {
                            "No Brain matches found"
                        }
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        brainTopicRelevantFiles = emptyList(),
                        isBrainTopicLoading = false,
                        brainTopicStatus = "Brain topic search failed: ${error.message ?: "unknown error"}"
                    )
                }
            }
        }
    }
    fun loadConnectedDotsForFile(filePath: String) {
        viewModelScope.launch {
            val dots = brainRepository.getConnectedDotsForFile(filePath)
            _uiState.update { it.copy(activeFileConnectedDots = dots) }
        }
    }

    fun selectOnDeviceBrainModel(modelId: String) {
        if (_uiState.value.onDeviceBrainModel.status == OnDeviceBrainModelStatus.DOWNLOADING) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                brainRepository.selectOnDeviceBrainModel(modelId)
                brainRepository.getOnDeviceBrainModelState()
            }.onSuccess { state ->
                _uiState.update { it.copy(onDeviceBrainModel = state) }
                showMessage("Offline Brain model selected")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(onDeviceBrainModel = brainRepository.getOnDeviceBrainModelState(error.message))
                }
                showMessage(error.message ?: "Could not select offline Brain model")
            }
        }
    }

    fun downloadOnDeviceBrainModel() {
        val state = _uiState.value.onDeviceBrainModel
        if (state.status == OnDeviceBrainModelStatus.DOWNLOADING) return

        val request = OneTimeWorkRequestBuilder<BrainModelDownloadWorker>()
            .setInputData(
                androidx.work.workDataOf(
                    BrainModelDownloadWorker.KEY_MODEL_ID to state.modelId
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        _uiState.update {
            it.copy(
                onDeviceBrainModel = it.onDeviceBrainModel.copy(
                    status = OnDeviceBrainModelStatus.DOWNLOADING,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    error = null
                )
            )
        }

        WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(
            BrainModelDownloadWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun deleteOnDeviceBrainModel() {
        val workManager = WorkManager.getInstance(getApplication<Application>())
        workManager.cancelUniqueWork(BrainModelDownloadWorker.UNIQUE_NAME)
        workManager.cancelUniqueWork(com.example.data.ai.BrainIndexWorker.UNIQUE_NAME)
        workManager.cancelUniqueWork(com.example.data.ai.BrainIndexWorker.TARGETED_UNIQUE_NAME)

        // Reflect the capability loss immediately; disk cleanup happens off the main thread.
        val spec = brainRepository.getOnDeviceBrainModelSpec()
        val unavailable = OnDeviceBrainModelUiState(
            status = OnDeviceBrainModelStatus.NOT_INSTALLED,
            modelId = spec.id,
            displayName = spec.displayName,
            sizeLabel = spec.sizeLabel
        )
        _uiState.update {
            it.copy(
                onDeviceBrainModel = unavailable,
                isBrainIndexing = false,
                brainIndexingProgress = 0f,
                brainIndexingStatus = "Brain model unavailable"
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                brainRepository.deleteOnDeviceBrainModel()
                _uiState.update {
                    it.copy(
                        onDeviceBrainModel = brainRepository.getOnDeviceBrainModelState(),
                        isBrainIndexing = false,
                        brainIndexingProgress = 0f,
                        brainIndexingStatus = "Brain model unavailable"
                    )
                }
                showMessage("On-device Brain model removed")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        onDeviceBrainModel = brainRepository.getOnDeviceBrainModelState(error.message),
                        isBrainIndexing = false
                    )
                }
            }
        }
    }

    fun indexAllFilesForBrain() {
        if (_uiState.value.isBrainIndexing) return
        if (!brainRepository.isOnDeviceBrainModelReady()) {
            showMessage("Download the on-device Brain model before indexing.")
            return
        }
        _uiState.update {
            it.copy(
                isBrainIndexing = true,
                brainIndexingProgress = 0f,
                brainIndexingStatus = "Brain indexing queued..."
            )
        }
        val request = OneTimeWorkRequestBuilder<com.example.data.ai.BrainIndexWorker>()
            .setInputData(
                androidx.work.workDataOf(
                    "force" to true,
                    "refreshStorageIndex" to true
                )
            )
            .build()
        WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(
            com.example.data.ai.BrainIndexWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        viewModelScope.launch {
            WorkManager.getInstance(getApplication<Application>())
                .getWorkInfoByIdFlow(request.id)
                .collectLatest { info ->
                    if (info == null) return@collectLatest
                    val progress = info.progress
                    val total = progress.getInt("total", 0)
                    val current = progress.getInt("current", 0)
                    val path = progress.getString("path").orEmpty()
                    when (info.state) {
                        androidx.work.WorkInfo.State.RUNNING -> _uiState.update { state ->
                            state.copy(
                                isBrainIndexing = true,
                                brainIndexingProgress = if (total > 0) current.toFloat() / total else 0f,
                                brainIndexingStatus = if (path.isBlank()) "Indexing Brain..." else "Indexing: " + File(path).name + " (" + current + "/" + total + ")"
                            )
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            val indexed = info.outputData.getInt("indexed", 0)
                            val skipped = info.outputData.getInt("skipped", 0)
                            val failed = info.outputData.getInt("failed", 0)
                            val suggestions = try { brainRepository.getSmartSuggestions() } catch (_: Exception) { emptyList() }
                            _uiState.update { state ->
                                state.copy(
                                    isBrainIndexing = false,
                                    brainIndexingProgress = 1f,
                                    brainIndexingStatus = "Brain ready — " + indexed + " indexed, " + skipped + " skipped, " + failed + " failed",
                                    brainSmartSuggestions = suggestions
                                )
                            }
                            return@collectLatest
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            val error = info.outputData.getString("error") ?: "Brain indexing failed"
                            _uiState.update { state -> state.copy(isBrainIndexing = false, brainIndexingStatus = error) }
                            return@collectLatest
                        }
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            _uiState.update { state -> state.copy(isBrainIndexing = false, brainIndexingStatus = "Brain indexing cancelled") }
                            return@collectLatest
                        }
                        else -> Unit
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
        repository.operationManager.shutdown()
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
