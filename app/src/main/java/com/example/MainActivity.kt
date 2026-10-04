package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.ui.screens.AiSettingsDialog
import com.example.ui.screens.AiSettingsScreen
import com.example.ui.screens.FileExplorerScreen
import com.example.ui.screens.FilePropertiesDialog
import com.example.ui.screens.FullscreenMediaViewer
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.KnowledgeGraphScreen
import com.example.ui.screens.TextEditorScreen
import com.example.ui.screens.ZipViewerDialog
import com.example.ui.theme.EmrExploreTheme
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.UnifiedViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: UnifiedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure Coil ImageLoader for high-performance lazy loading without scroll stutter
        val imageLoader = coil.ImageLoader.Builder(this)
            .components {
                add(coil.decode.VideoFrameDecoder.Factory())
            }
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(false)
            .allowHardware(true)
            .respectCacheHeaders(false)
            .build()
        coil.Coil.setImageLoader(imageLoader)

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

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = isAllFilesAccessGranted()
        allFilesAccessGranted = granted
        if (granted) {
            viewModel.onPermissionsGranted()
        }
    }

    // Automatically check and refresh when the activity resumes (e.g. returning from system settings)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = isAllFilesAccessGranted()
        allFilesAccessGranted = granted
        if (granted) {
            viewModel.onPermissionsGranted()
        }
    }

    // Accompanist Permissions setup for reading and writing files to external storage
    val storagePermissions = remember { getRequiredStoragePermissions() }
    val storagePermissionsState = rememberMultiplePermissionsState(
        permissions = storagePermissions
    ) { permissionsResultMap ->
        val anyGranted = permissionsResultMap.values.any { it }
        if (anyGranted) {
            viewModel.onPermissionsGranted()
        }
    }

    // Auto-prompt permissions on initial start if not granted
    LaunchedEffect(Unit) {
        if (!storagePermissionsState.allPermissionsGranted) {
            storagePermissionsState.launchMultiplePermissionRequest()
        }
    }

    // Reactively refresh data when permissions are newly granted
    LaunchedEffect(storagePermissionsState.allPermissionsGranted) {
        if (storagePermissionsState.allPermissionsGranted && uiState.files.isEmpty()) {
            viewModel.onPermissionsGranted()
        }
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
                FileOperationBanner(
                    progress = uiState.fileOperationProgress,
                    onPause = { viewModel.pauseFileOperation() },
                    onResume = { viewModel.resumeFileOperation() },
                    onCancel = { viewModel.cancelFileOperation() },
                    onRetry = { viewModel.retryFileOperation() },
                    onDismiss = { viewModel.dismissFileOperation() },
                    onResolveConflict = { resolution -> viewModel.resolveFileConflict(resolution) }
                )

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
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("nav_item_home")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.FILES,
                        onClick = { viewModel.setTab(MainTab.FILES) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
                        label = { Text("Files") },
                        modifier = Modifier.testTag("nav_item_files")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.GALLERY,
                        onClick = { viewModel.setTab(MainTab.GALLERY) },
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery") },
                        label = { Text("Gallery") },
                        modifier = Modifier.testTag("nav_item_gallery")
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == MainTab.BRAIN,
                        onClick = { viewModel.setTab(MainTab.BRAIN) },
                        icon = { Icon(Icons.Default.Psychology, contentDescription = "Brain & Graph") },
                        label = { Text("Brain") },
                        modifier = Modifier.testTag("nav_item_brain")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permissions Banner for external storage & All Files Access
            StoragePermissionBanner(
                permissionsState = storagePermissionsState,
                allFilesAccessGranted = allFilesAccessGranted,
                onGrantAllFilesAccess = {
                    launchAllFilesAccessSettings(context, allFilesLauncher)
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                AnimatedContent(
                    targetState = uiState.currentTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TabContent"
                ) { targetTab ->
                    key(targetTab) {
                        when (targetTab) {
                        MainTab.HOME -> HomeScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        MainTab.FILES -> FileExplorerScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        MainTab.GALLERY -> GalleryScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onRequestMediaLocationPermission = {
                                if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    !mediaLocationGranted
                                ) {
                                    mediaLocationLauncher.launch(
                                        Manifest.permission.ACCESS_MEDIA_LOCATION
                                    )
                                }
                            }
                        )
                        MainTab.BRAIN -> KnowledgeGraphScreen(
                            nodes = uiState.kgNodes,
                            edges = uiState.kgEdges,
                            aiConfig = uiState.aiConfig,
                            apiConfigured = uiState.aiConfig.isEnabled &&
                                (com.example.data.ai.ProviderType.fromString(uiState.aiConfig.providerType) in setOf(
                                    com.example.data.ai.ProviderType.OLLAMA,
                                    com.example.data.ai.ProviderType.OPENAI_COMPATIBLE,
                                    com.example.data.ai.ProviderType.CUSTOM
                                ) || uiState.aiConfig.apiKey.isNotBlank()),
                            isIndexing = uiState.isKgIndexing,
                            indexingProgress = uiState.kgIndexingProgress,
                            indexingStatus = uiState.kgIndexingStatus,
                            ragAnswer = uiState.ragAnswer,
                            isRagQuerying = uiState.isRagQuerying,
                            smartSuggestions = uiState.kgSmartSuggestions,
                            onQueryRag = { viewModel.queryRag(it) },
                            onIndexAllFiles = { viewModel.indexAllFilesForKnowledgeGraph() },
                            onOpenAiSettings = { viewModel.setShowAiSettings(true) },
                            onOpenFile = { file -> viewModel.openFile(com.example.data.model.FileItem(name = file.name, path = file.absolutePath, size = file.length(), lastModified = file.lastModified(), isDirectory = false)) },
                            onOpenImage = { file -> viewModel.openFile(com.example.data.model.FileItem(name = file.name, path = file.absolutePath, size = file.length(), lastModified = file.lastModified(), isDirectory = false, mimeType = "image/jpeg")) }
                        )
                    }
                    }
                }
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
            onToggleFavorite = { fileItem -> viewModel.toggleFavorite(fileItem) }
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
            onClose = { viewModel.closeTextEditor() }
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
            onOpenFile = { file -> viewModel.openFile(com.example.data.model.FileItem(name = file.name, path = file.absolutePath, size = file.length(), lastModified = file.lastModified(), isDirectory = false)) },
            onOpenImage = { file -> viewModel.openFile(com.example.data.model.FileItem(name = file.name, path = file.absolutePath, size = file.length(), lastModified = file.lastModified(), isDirectory = false, mimeType = "image/jpeg")) }
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
        AiSettingsScreen(
            currentConfig = uiState.aiConfig,
            nodeCount = uiState.kgNodeCount,
            edgeCount = uiState.kgEdgeCount,
            chunkCount = uiState.kgChunkCount,
            isTestingConnection = uiState.isTestingAiConnection,
            testResult = uiState.aiTestResult,
            onSaveConfig = { viewModel.saveAiConfig(it) },
            onTestConnection = { viewModel.testAiConnection(it) },
            availableModels = uiState.aiModels,
            availableVisionModels = uiState.aiVisionModels,
            availableEmbeddingModels = uiState.aiEmbeddingModels,
            isFetchingModels = uiState.isFetchingAiModels,
            modelFetchError = uiState.aiModelFetchError,
            onFetchModels = { viewModel.fetchAiModels(it) },
            onReindexAll = { viewModel.indexAllFilesForKnowledgeGraph() },
            onClearGraph = { viewModel.clearKnowledgeGraph() },
            onNavigateBack = { viewModel.setShowAiSettings(false) }
        )
    } else if (uiState.showAiSettingsDialog) {
        AiSettingsDialog(
            currentConfig = uiState.aiConfig,
            isTestingConnection = uiState.isTestingAiConnection,
            testResult = uiState.aiTestResult,
            onSaveConfig = { viewModel.saveAiConfig(it) },
            onTestConnection = { viewModel.testAiConnection(it) },
            onClearGraph = { viewModel.clearKnowledgeGraph() },
            onDismiss = { viewModel.setShowAiSettings(false) }
        )
    }
}
