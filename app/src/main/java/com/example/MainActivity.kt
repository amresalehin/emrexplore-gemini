package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AllFilesAccessDialog
import com.example.ui.components.AudioMiniPlayer
import com.example.ui.components.FileOperationBanner
import com.example.ui.components.StoragePermissionBanner
import com.example.ui.components.getRequiredStoragePermissions
import com.example.ui.components.isAllFilesAccessGranted
import com.example.ui.components.launchAllFilesAccessSettings
import com.example.ui.screens.AiSettingsScreen
import com.example.ui.screens.BrainSetupScreen
import com.example.ui.screens.BrainScreen
import com.example.ui.screens.FileExplorerScreen
import com.example.ui.screens.FilePropertiesDialog
import com.example.ui.screens.FullscreenMediaViewer
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MetadataInspectorSheet
import com.example.ui.screens.TextEditorScreen
import com.example.ui.screens.ZipViewerDialog
import com.example.ui.theme.EmrExploreTheme
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.LocalMainTabVisible
import com.example.data.ai.isKeylessAiConfig
import com.example.ui.viewmodel.UnifiedViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: UnifiedViewModel by viewModels()

    companion object {
        private var coilConfigured = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure the process-wide Coil loader only once. Use applicationContext so it
        // never retains an Activity across rotation or process recreation.
        if (!coilConfigured) {
            val appContext = applicationContext
            val imageLoader = coil.ImageLoader.Builder(appContext)
                .components {
                    add(coil.decode.VideoFrameDecoder.Factory())
                }
                .memoryCache {
                    coil.memory.MemoryCache.Builder(appContext)
                        .maxSizePercent(0.30)
                        .build()
                }
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(appContext.cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.05)
                        .build()
                }
                .crossfade(false)
                .allowHardware(true)
                .respectCacheHeaders(false)
                .build()
            coil.Coil.setImageLoader(imageLoader)
            coilConfigured = true
        }

        setContent {
            EmrExploreTheme {
                MainAppRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainAppRoot(viewModel: UnifiedViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aiSettingsVisible = uiState.isAiSettingsScreenOpen
    val activityContext = LocalContext.current

    DisposableEffect(aiSettingsVisible) {
        val activity = activityContext as? android.app.Activity
        if (aiSettingsVisible) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    var allFilesAccessGranted by remember { mutableStateOf(isAllFilesAccessGranted()) }
    var showAllFilesDialog by rememberSaveable {
        mutableStateOf(!isAllFilesAccessGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    }

    var mediaLocationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_MEDIA_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val mediaLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        mediaLocationGranted = granted
    }

    var lastAllFilesAccessGranted by remember { mutableStateOf(allFilesAccessGranted) }

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        allFilesAccessGranted = isAllFilesAccessGranted()
        showAllFilesDialog = false
    }

    // Refresh storage only when All Files Access actually changes to granted.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = isAllFilesAccessGranted()
        val changedToGranted = granted && !lastAllFilesAccessGranted
        allFilesAccessGranted = granted
        lastAllFilesAccessGranted = granted
        if (changedToGranted) viewModel.onPermissionsGranted()
    }

    // Accompanist Permissions setup for reading and writing files to external storage
    val storagePermissions = remember { getRequiredStoragePermissions() }
    val storagePermissionsState = rememberMultiplePermissionsState(
        permissions = storagePermissions
    )

    var lastRuntimePermissionsGranted by remember {
        mutableStateOf(storagePermissionsState.allPermissionsGranted)
    }

    // First-run surfaces are sequenced: finish the All Files Access prompt before the
    // runtime media permission sheet. This prevents overlapping permission surfaces.
    LaunchedEffect(showAllFilesDialog, allFilesAccessGranted, storagePermissionsState.allPermissionsGranted) {
        val runtimeGranted = storagePermissionsState.allPermissionsGranted
        if (!showAllFilesDialog &&
            (allFilesAccessGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) &&
            !runtimeGranted
        ) {
            storagePermissionsState.launchMultiplePermissionRequest()
        }
        val changedToGranted = runtimeGranted && !lastRuntimePermissionsGranted
        if (changedToGranted) viewModel.onPermissionsGranted()
        lastRuntimePermissionsGranted = runtimeGranted
    }

    // At a tab root, Back returns to Home. Content-level handlers (folders/viewers/editors)
    // are registered later and therefore take precedence when they can consume Back.
    BackHandler(
        enabled = uiState.currentTab != MainTab.HOME &&
            uiState.activeTextFile == null &&
            uiState.activeZipFile == null &&
            uiState.activeDetailItem == null &&
            uiState.fullscreenMediaIndex == null &&
            !uiState.isAiSettingsScreenOpen &&
            !uiState.isRecycleBinOpen
    ) {
        viewModel.setTab(MainTab.HOME)
    }

    // Display user messages via Snackbar
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Decoupled Background File Operation Progress Banner & Controls
                // Keep transient chrome bounded: an active operation gets the priority slot;
                // the permission prompt returns as soon as the operation is idle.
                if (uiState.fileOperationProgress.status != com.example.data.model.OperationStatus.IDLE) {
                    FileOperationBanner(
                        progress = uiState.fileOperationProgress,
                        onPause = { viewModel.pauseFileOperation() },
                        onResume = { viewModel.resumeFileOperation() },
                        onCancel = { viewModel.cancelFileOperation() },
                        onRetry = { viewModel.retryFileOperation() },
                        onDismiss = { viewModel.dismissFileOperation() },
                        onResolveConflict = { resolution -> viewModel.resolveFileConflict(resolution) }
                    )
                }

                // Audio mini-player bar above navigation
                AudioMiniPlayer(
                    activeAudio = uiState.activeAudioFile,
                    isPlaying = uiState.isAudioPlaying,
                    positionMs = uiState.audioPositionMs,
                    durationMs = uiState.audioDurationMs,
                    onPlayPause = { viewModel.toggleAudioPlayPause() },
                    onClose = { viewModel.stopAudio() }
                )

                // Main Navigation Bar
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("main_bottom_nav"),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.HOME,
                        onClick = { viewModel.setTab(MainTab.HOME) },
                        icon = { Icon(Icons.Default.Home, contentDescription = stringResource(com.example.R.string.nav_home_cd)) },
                        label = { Text(stringResource(com.example.R.string.nav_home)) },
                        modifier = Modifier.testTag("nav_item_home")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.FILES,
                        onClick = { viewModel.setTab(MainTab.FILES) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = stringResource(com.example.R.string.nav_files_cd)) },
                        label = { Text(stringResource(com.example.R.string.nav_files)) },
                        modifier = Modifier.testTag("nav_item_files")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.GALLERY,
                        onClick = { viewModel.setTab(MainTab.GALLERY) },
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = stringResource(com.example.R.string.nav_gallery_cd)) },
                        label = { Text(stringResource(com.example.R.string.nav_gallery)) },
                        modifier = Modifier.testTag("nav_item_gallery")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.BRAIN,
                        onClick = { viewModel.setTab(MainTab.BRAIN) },
                        icon = { Icon(Icons.Default.Psychology, contentDescription = stringResource(com.example.R.string.nav_brain_cd)) },
                        label = { Text(stringResource(com.example.R.string.nav_brain)) },
                        modifier = Modifier.testTag("nav_item_brain")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.fileOperationProgress.status == com.example.data.model.OperationStatus.IDLE && !showAllFilesDialog) {
                StoragePermissionBanner(
                    permissionsState = storagePermissionsState,
                    allFilesAccessGranted = allFilesAccessGranted,
                    onGrantAllFilesAccess = { launchAllFilesAccessSettings(context, allFilesLauncher) }
                )
            }
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                PersistentTabHost(
                    uiState = uiState,
                    viewModel = viewModel,
                    mediaLocationGranted = mediaLocationGranted,
                    mediaLocationLauncher = mediaLocationLauncher
                )
            }
        }
    }

    // --- Overlay In-App Viewers & Modals ---

    // 1. Fullscreen Media Viewer
    if (uiState.fullscreenMediaIndex != null) {
        FullscreenMediaViewer(
            mediaList = uiState.fullscreenMediaList,
            currentIndex = uiState.fullscreenMediaIndex ?: 0,
            windowStartIndex = uiState.fullscreenWindowStartIndex,
            totalCount = uiState.fullscreenTotalCount.coerceAtLeast(uiState.fullscreenMediaList.size),
            onClose = { viewModel.closeFullscreenMedia() },
            onIndexChange = { newIdx -> viewModel.moveFullscreenMedia(newIdx) },
            onToggleFavorite = { fileItem -> viewModel.toggleFavorite(fileItem) },
            onInspectMetadata = { mediaItem -> viewModel.inspectMetadata(mediaItem) },
            onLoadBrainNode = { mediaItem -> viewModel.getBrainNode(mediaItem) },
            onReindexWithBrain = { mediaItem -> viewModel.reAnalyzeGalleryImage(mediaItem) },
            onAskAiAboutFile = { mediaItem ->
                viewModel.closeFullscreenMedia()
                if (mediaItem.path.isNotBlank()) {
                    viewModel.askAiAboutFile(File(mediaItem.path))
                }
            },
            onEdit = { mediaItem ->
                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(
                        mediaItem.uri,
                        mediaItem.mimeType.ifBlank { if (mediaItem.isVideo) "video/*" else "image/*" }
                    )
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    clipData = android.content.ClipData.newRawUri("media", mediaItem.uri)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(editIntent, "Edit " + mediaItem.name))
                }.onFailure {
                    viewModel.showMessage("No compatible editor is installed for this file")
                }
            },
            onDelete = { mediaItem ->
                viewModel.deleteFile(mediaItem.path, toTrash = true)
                viewModel.closeFullscreenMedia()
            }
        )
    }

    // 2. In-App Text File Editor
    if (uiState.activeTextFile != null) {
        TextEditorScreen(
            fileItem = uiState.activeTextFile!!,
            content = uiState.textFileContent,
            isEditing = uiState.isEditingText,
            onContentChange = { viewModel.updateTextContent(it) },
            onToggleEdit = { viewModel.toggleTextEditing(it) },
            onSave = { viewModel.saveTextFile() },
            onSaveAndClose = { viewModel.saveTextFile(closeWhenDone = true) },
            onDiscard = { viewModel.discardTextChanges() },
            onClose = { viewModel.closeTextEditor() },
            isDirty = uiState.textFileContent != uiState.savedTextFileContent
        )
    }

    // 3. Zip Archive Viewer
    if (uiState.activeZipFile != null) {
        ZipViewerDialog(
            item = uiState.activeZipFile!!,
            entries = uiState.zipEntries,
            isExtracting = uiState.isExtractingZip,
            onExtract = { viewModel.extractCurrentZip() },
            onDismiss = { viewModel.closeZip() }
        )
    }

    // 4. File Properties Dialog
    if (uiState.activeDetailItem != null) {
        FilePropertiesDialog(
            item = uiState.activeDetailItem!!,
            connectedDots = uiState.activeFileConnectedDots,
            onDismiss = { viewModel.closeProperties() },
            onInspectMetadata = { item -> viewModel.inspectMetadata(com.example.data.model.MediaItem(
                id = item.path.hashCode().toLong(),
                uri = item.uri ?: android.net.Uri.fromFile(File(item.path)),
                name = item.name,
                path = item.path,
                size = item.size,
                dateAdded = item.lastModified,
                mimeType = item.mimeType,
                isVideo = false
            )) },
            onOpenFile = { file -> viewModel.openFile(com.example.data.model.FileItem(name = file.name, path = file.absolutePath, size = file.length(), lastModified = file.lastModified(), isDirectory = false)) },
            onOpenImage = { file -> viewModel.openFile(com.example.data.model.FileItem(name = file.name, path = file.absolutePath, size = file.length(), lastModified = file.lastModified(), isDirectory = false, mimeType = "image/jpeg")) },
            onAskAiAboutFile = { file ->
                viewModel.closeProperties()
                viewModel.askAiAboutFile(file)
            }
        )
    }

    // Metadata Inspector
    uiState.activeMetadataReport?.let { report ->
        MetadataInspectorSheet(
            report = report,
            onDismiss = { viewModel.closeMetadataInspector() },
            onAskAiAboutFile = { file ->
                viewModel.closeMetadataInspector()
                viewModel.askAiAboutFile(file)
            }
        )
    }

    // 5. Initial All Files Access Prompt Dialog
    if (showAllFilesDialog && !allFilesAccessGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        AllFilesAccessDialog(
            onConfirm = {
                showAllFilesDialog = false
                launchAllFilesAccessSettings(context, allFilesLauncher)
            },
            onDismiss = {
                showAllFilesDialog = false
            }
        )
    }

    // 6. Dedicated AI & BYOK Provider Settings Screen
    if (uiState.isAiSettingsScreenOpen) {
        if (!uiState.aiConfig.brainSetupCompleted) {
            BrainSetupScreen(
                currentConfig = uiState.aiConfig,
                offlineModels = uiState.offlineBrainModels,
                availableModels = uiState.aiModels,
                availableVisionModels = uiState.aiVisionModels,
                availableEmbeddingModels = uiState.aiEmbeddingModels,
                availableMultimodalEmbeddingModels = uiState.aiMultimodalEmbeddingModels,
                isFetchingModels = uiState.isFetchingAiModels,
                modelFetchError = uiState.aiModelFetchError,
                onFetchModels = { viewModel.fetchAiModels(it) },
                onFetchEmbeddingModels = { viewModel.fetchEmbeddingModels(it) },
                onDownloadOllamaModel = { config, model -> viewModel.downloadOllamaModel(config, model) },
                isDownloadingOllamaModel = uiState.isDownloadingOllamaModel,
                ollamaDownloadProgress = uiState.ollamaDownloadProgress,
                ollamaDownloadStatus = uiState.ollamaDownloadStatus,
                onSelectOfflineModel = { viewModel.selectOnDeviceBrainModel(it) },
                onSaveConfig = { viewModel.saveAiConfig(it) },
                onNavigateBack = { viewModel.setShowAiSettings(false) }
            )
        } else {
            AiSettingsScreen(
                currentConfig = uiState.aiConfig,
                isTestingConnection = uiState.isTestingAiConnection,
                testResult = uiState.aiTestResult,
                onSaveConfig = { viewModel.saveAiConfig(it) },
                onTestConnection = { viewModel.testAiConnection(it) },
                availableModels = uiState.aiModels,
                availableVisionModels = uiState.aiVisionModels,
                availableEmbeddingModels = uiState.aiEmbeddingModels,
                availableMultimodalEmbeddingModels = uiState.aiMultimodalEmbeddingModels,
                offlineBrainModels = uiState.offlineBrainModels,
                selectedOfflineBrainModelId = uiState.onDeviceBrainModel.modelId,
                onSelectOfflineBrainModel = { viewModel.selectOnDeviceBrainModel(it) },
                isFetchingModels = uiState.isFetchingAiModels,
                modelFetchError = uiState.aiModelFetchError,
                onFetchModels = { viewModel.fetchAiModels(it) },
                onFetchEmbeddingModels = { viewModel.fetchEmbeddingModels(it) },
                onDownloadOllamaModel = { config, model -> viewModel.downloadOllamaModel(config, model) },
                isDownloadingOllamaModel = uiState.isDownloadingOllamaModel,
                ollamaDownloadProgress = uiState.ollamaDownloadProgress,
                ollamaDownloadStatus = uiState.ollamaDownloadStatus,
                onTestEmbeddingConnection = { viewModel.testEmbeddingConnection(it) },
                embeddingTestResult = uiState.embeddingTestResult,
                onDeviceBrainModel = uiState.onDeviceBrainModel,
                onDownloadOnDeviceBrainModel = { viewModel.downloadOnDeviceBrainModel() },
                onDeleteOnDeviceBrainModel = { viewModel.deleteOnDeviceBrainModel() },
                onNavigateBack = { viewModel.setShowAiSettings(false) }
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PersistentTabHost(
    uiState: com.example.ui.viewmodel.UiState,
    viewModel: UnifiedViewModel,
    mediaLocationGranted: Boolean,
    mediaLocationLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    Box(Modifier.fillMaxSize()) {
        TabHostPage(uiState.currentTab == MainTab.HOME) { HomeScreen(uiState, viewModel) }
        TabHostPage(uiState.currentTab == MainTab.FILES) { FileExplorerScreen(uiState, viewModel) }
        TabHostPage(uiState.currentTab == MainTab.GALLERY) {
            GalleryScreen(
                uiState = uiState,
                viewModel = viewModel,
                onOpenBrainSettings = { viewModel.setTab(MainTab.BRAIN); viewModel.setShowAiSettings(true) },
                onRequestMediaLocationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !mediaLocationGranted) {
                        mediaLocationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                    }
                }
            )
        }
        TabHostPage(uiState.currentTab == MainTab.BRAIN) {
            BrainScreen(
                nodes = uiState.brainNodes,
                edges = uiState.brainEdges,
                nodeCount = uiState.brainNodeCount,
                edgeCount = uiState.brainEdgeCount,
                aiConfig = uiState.aiConfig,
                apiConfigured = uiState.aiConfigLoaded && uiState.aiConfig.isEnabled &&
                    (isKeylessAiConfig(uiState.aiConfig) || uiState.aiConfig.apiKey.isNotBlank()),
                isIndexing = uiState.isBrainIndexing,
                embeddingReady = run {
                    val embeddingProvider = com.example.data.ai.EmbeddingProviderType.fromString(uiState.aiConfig.embeddingProviderType)
                    if (embeddingProvider == com.example.data.ai.EmbeddingProviderType.OFFLINE) {
                        uiState.onDeviceBrainModel.status == com.example.data.brain.OnDeviceBrainModelStatus.READY
                    } else {
                        uiState.aiConfig.textEmbeddingModel.ifBlank { uiState.aiConfig.embeddingModel }.isNotBlank() &&
                            uiState.aiConfig.embeddingBaseUrl.isNotBlank() &&
                            (
                                embeddingProvider == com.example.data.ai.EmbeddingProviderType.OLLAMA ||
                                (
                                    embeddingProvider == com.example.data.ai.EmbeddingProviderType.CUSTOM &&
                                    uiState.aiConfig.embeddingBaseUrl.trim().lowercase().let {
                                        it.contains("localhost") || it.contains("127.0.0.1") || it.contains("10.0.2.2")
                                    }
                                ) ||
                                uiState.aiConfig.embeddingApiKey.isNotBlank()
                            )
                    }
                },
                indexingProgress = uiState.brainIndexingProgress,
                indexingStatus = uiState.brainIndexingStatus,
                ragAnswer = uiState.ragAnswer,
                isRagQuerying = uiState.isRagQuerying,
                onDeviceBrainModel = uiState.onDeviceBrainModel,
                smartSuggestions = uiState.brainSmartSuggestions,
                askAiMessages = uiState.askAiMessages,
                attachedAiFile = uiState.attachedAiFile,
                onAttachFile = viewModel::attachAiFile,
                onDetachFile = viewModel::detachAiFile,
                onClearChat = viewModel::clearAskAiChat,
                onQueryRag = viewModel::queryRag,
                onCancelRag = viewModel::cancelRagQuery,
                onIndexAllFiles = viewModel::indexAllFilesForBrain,
                onPauseIndexing = viewModel::pauseBrainIndexing,
                onResumeIndexing = viewModel::resumeBrainIndexing,
                onAskAiForFile = { node ->
                    node.sourceFilePath?.let { path ->
                        viewModel.askAiAboutFile(File(path))
                    }
                },
                brainTopics = uiState.brainTopics,
                selectedBrainTopic = uiState.brainTopics.firstOrNull { it.id == uiState.selectedBrainTopicId },
                brainTopicRelevantFiles = uiState.brainTopicRelevantFiles,
                isBrainTopicLoading = uiState.isBrainTopicLoading,
                brainTopicStatus = uiState.brainTopicStatus,
                onSelectBrainTopic = viewModel::selectBrainTopic,
                onSaveBrainTopic = viewModel::saveBrainTopic,
                onDeleteBrainTopic = viewModel::deleteBrainTopic,
                onOpenAiSettings = { viewModel.setShowAiSettings(true) },
                onOpenFile = { file -> viewModel.openFile(com.example.data.model.FileItem(name = file.name, path = file.absolutePath, size = file.length(), lastModified = file.lastModified(), isDirectory = false)) },
                onOpenImage = { file -> viewModel.openFile(com.example.data.model.FileItem(name = file.name, path = file.absolutePath, size = file.length(), lastModified = file.lastModified(), isDirectory = false, mimeType = "image/jpeg")) }
            )
        }
    }
}

@Composable
private fun TabHostPage(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalMainTabVisible provides visible) {
    Box(
        modifier = modifier
            .alpha(if (visible) 1f else 0f)
            .zIndex(if (visible) 1f else 0f)
            .pointerInput(visible) {
                if (!visible) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent()
                    }
                }
            }
    ) { content() }
    }
}
