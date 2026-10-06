package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.brain.AskAiChatMessage
import com.example.data.brain.OnDeviceBrainModelStatus
import com.example.data.brain.OnDeviceBrainModelUiState
import com.example.data.brain.AttachedAiFile
import com.example.data.brain.BrainTopicFile
import com.example.data.brain.RagAnswer
import com.example.data.local.AiProviderConfigEntity
import com.example.data.brain.BrainTopicEntity
import com.example.data.brain.BrainEdgeEntity
import com.example.data.brain.BrainNodeEntity
import com.example.ui.theme.ColorDocuments
import com.example.ui.theme.ColorImages
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

enum class BrainScreenTab(val label: String, val icon: ImageVector) {
    ASK_AI("Chat", Icons.Default.AutoAwesome),
    CANVAS("Graph", Icons.Default.Hub)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainScreen(
    nodes: List<BrainNodeEntity>,
    edges: List<BrainEdgeEntity>,
    nodeCount: Int = nodes.size,
    edgeCount: Int = edges.size,
    aiConfig: AiProviderConfigEntity?,
    isIndexing: Boolean,
    indexingProgress: Float,
    indexingStatus: String,
    embeddingReady: Boolean = false,
    ragAnswer: RagAnswer?,
    isRagQuerying: Boolean,
    onDeviceBrainModel: OnDeviceBrainModelUiState = OnDeviceBrainModelUiState(),
    smartSuggestions: List<String> = emptyList(),
    askAiMessages: List<AskAiChatMessage> = emptyList(),
    attachedAiFile: AttachedAiFile? = null,
    onAttachFile: (File) -> Unit = {},
    onDetachFile: () -> Unit = {},
    onClearChat: () -> Unit = {},
    apiConfigured: Boolean = false,
    onCancelRag: () -> Unit = {},
    onQueryRag: (String) -> Unit,
    onIndexAllFiles: () -> Unit,
    onPauseIndexing: () -> Unit = {},
    onResumeIndexing: () -> Unit = {},
    onOpenAiSettings: () -> Unit,
    onAskAiForFile: (BrainNodeEntity) -> Unit,
    brainTopics: List<BrainTopicEntity> = emptyList(),
    selectedBrainTopic: BrainTopicEntity? = null,
    brainTopicRelevantFiles: List<BrainTopicFile> = emptyList(),
    isBrainTopicLoading: Boolean = false,
    brainTopicStatus: String = "",
    onSelectBrainTopic: (String) -> Unit = {},
    onSaveBrainTopic: (String, String, String?) -> Unit = { _, _, _ -> },
    onDeleteBrainTopic: (String) -> Unit = {},
    onOpenFile: (File) -> Unit,
    onOpenImage: (File) -> Unit,
    modifier: Modifier = Modifier
) {    var selectedTab by rememberSaveable { mutableStateOf(BrainScreenTab.ASK_AI) }
    var selectedNode by remember { mutableStateOf<BrainNodeEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (selectedTab == BrainScreenTab.ASK_AI) Icons.Default.AutoAwesome else Icons.Default.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (selectedTab == BrainScreenTab.ASK_AI) "Chat" else "Knowledge Graph",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedTab == BrainScreenTab.ASK_AI) {
                            val chatProvider = aiConfig?.let {
                                com.example.data.ai.ProviderType.fromString(it.providerType).displayName
                            } ?: "Not configured"
                            val chatModel = aiConfig?.chatModel?.ifBlank { "No model selected" } ?: "No model selected"
                            Text(
                                text = "$chatProvider  •  $chatModel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "$nodeCount nodes  •  $edgeCount links",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenAiSettings,
                        modifier = Modifier.testTag("brain_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "AI model settings")
                    }
                }

                if (selectedTab == BrainScreenTab.ASK_AI && aiConfig != null) {
                    val embeddingProvider = com.example.data.ai.EmbeddingProviderType.fromString(aiConfig.embeddingProviderType)
                    val embeddingModel = if (embeddingProvider == com.example.data.ai.EmbeddingProviderType.OFFLINE) {
                        onDeviceBrainModel.displayName
                    } else {
                        aiConfig.textEmbeddingModel.ifBlank { aiConfig.embeddingModel }.ifBlank { "No model selected" }
                    }
                    Text(
                        text = "Embeddings: ${embeddingProvider.displayName}  •  $embeddingModel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            BrainScreenTab.entries.forEach { tab ->
                                val selected = selectedTab == tab
                                Surface(
                                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { selectedTab = tab },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Text(tab.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                BrainScreenTab.ASK_AI -> DeclutteredAskAiView(
                    messages = askAiMessages,
                    attachedFile = attachedAiFile,
                    onAttachFile = onAttachFile,
                    onDetachFile = onDetachFile,
                    onClearChat = onClearChat,
                    isQuerying = isRagQuerying,
                    smartSuggestions = smartSuggestions,
                    availableNodes = nodes,
                    onQuery = onQueryRag,
                    onCancel = onCancelRag,
                    onOpenFile = onOpenFile,
                    cloudAiReady = apiConfigured,
                    localBrainReady = embeddingReady,
                    onOpenImage = onOpenImage
                )
                BrainScreenTab.CANVAS -> DeclutteredCanvasView(
                    nodes = nodes,
                    edges = edges,
                    localBrainReady = embeddingReady,
                    onNodeClick = { selectedNode = it },
                    onOpenFile = onOpenFile,
                    onOpenImage = onOpenImage,
                    onIndexFiles = onIndexAllFiles,
                    onPauseIndexing = onPauseIndexing,
                    onResumeIndexing = onResumeIndexing,
                    isIndexing = isIndexing,
                    onAskAiForFile = { node ->
                        selectedTab = BrainScreenTab.ASK_AI
                        onAskAiForFile(node)
                    }
                )
            }
        }
    }

    selectedNode?.let { node ->
        ModalBottomSheet(
            onDismissRequest = { selectedNode = null },
            sheetState = sheetState
        ) {
            DeclutteredNodeSheet(
                node = node,
                allEdges = edges,
                allNodes = nodes,
                onOpenFile = onOpenFile,
                onOpenImage = onOpenImage,
                onSelectRelatedNode = { selectedNode = it },
                onClose = { selectedNode = null }
            )
        }
    }
}

// -------------------------------------------------------------
// TAB 1: REIMAGINED CONVERSATIONAL ASK AI WITH FILE ATTACHMENT
// -------------------------------------------------------------

@Composable
fun DeclutteredAskAiView(
    messages: List<AskAiChatMessage>,
    attachedFile: AttachedAiFile?,
    onAttachFile: (File) -> Unit,
    onDetachFile: () -> Unit,
    onClearChat: () -> Unit,
    isQuerying: Boolean,
    smartSuggestions: List<String>,
    availableNodes: List<BrainNodeEntity>,
    onQuery: (String) -> Unit,
    onCancel: () -> Unit = {},
    cloudAiReady: Boolean = false,
    localBrainReady: Boolean = false,
    onOpenFile: (File) -> Unit,
    onOpenImage: (File) -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { copyUriToTempFile(context, it)?.let(onAttachFile) }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { copyUriToTempFile(context, it)?.let(onAttachFile) }
    }

    LaunchedEffect(messages.size, isQuerying) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("What can I help you find?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ask about the files in your Brain index.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                color = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.widthIn(max = 340.dp)
                            ) {
                                Text(
                                    message.text,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                    if (isQuerying) {
                        item {
                            Text(
                                "Thinking…",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        attachedFile?.let { file ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(file.file.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDetachFile) {
                        Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(26.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = {
                        documentPickerLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
                }
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Attach image")
                }
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (!isQuerying && queryText.isNotBlank()) {
                            onQuery(queryText.trim())
                            queryText = ""
                        }
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                if (isQuerying) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Stop")
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (queryText.isNotBlank()) {
                                onQuery(queryText.trim())
                                queryText = ""
                            }
                        },
                        enabled = queryText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

private fun copyUriToTempFile(context: Context, uri: Uri): File? {
    return try {
        val resolver = context.contentResolver
        var displayName = "attached_file"
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                displayName = cursor.getString(nameIndex) ?: displayName
            }
        }
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(context.cacheDir, "ai_${System.currentTimeMillis()}_$safeName")
        resolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (destFile.exists() && destFile.length() > 0) destFile else null
    } catch (_: Exception) {
        null
    }
}

// -------------------------------------------------------------
// TAB 2: DECLUTTERED FULL-BLEED NETWORK GRAPH VIEW
// -------------------------------------------------------------

@Composable
fun DeclutteredCanvasView(
    nodes: List<BrainNodeEntity>,
    edges: List<BrainEdgeEntity>,
    localBrainReady: Boolean = false,
    onNodeClick: (BrainNodeEntity) -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenImage: (File) -> Unit,
    onIndexFiles: () -> Unit,
    onPauseIndexing: () -> Unit,
    onResumeIndexing: () -> Unit,
    isIndexing: Boolean,
    onAskAiForFile: (BrainNodeEntity) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var activeNode by remember { mutableStateOf<BrainNodeEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var showNodeList by rememberSaveable { mutableStateOf(false) }
    var layoutSeed by remember { mutableStateOf(42L) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val filteredNodes = remember(nodes, selectedFilter) {
        when (selectedFilter) {
            "ALL" -> nodes
            "DOCS" -> nodes.filter { it.nodeType == "DOCUMENT" }
            "PHOTOS" -> nodes.filter { it.nodeType == "IMAGE" }
            "PLACES" -> nodes.filter { it.nodeType == "LOCATION" }
            "DEVICES" -> nodes.filter { it.nodeType == "DEVICE" }
            "TOPICS" -> nodes.filter { it.nodeType !in setOf("DOCUMENT", "IMAGE", "LOCATION", "DEVICE") }
            else -> nodes
        }
    }
    val filteredIds = remember(filteredNodes) { filteredNodes.map { it.id }.toSet() }
    val filteredEdges = remember(edges, filteredIds) {
        edges.filter { it.sourceNodeId in filteredIds && it.targetNodeId in filteredIds }
    }

    val degreeMap = remember(filteredNodes, filteredEdges) {
        val map = mutableMapOf<String, Int>()
        filteredEdges.forEach { edge ->
            map[edge.sourceNodeId] = (map[edge.sourceNodeId] ?: 0) + 1
            map[edge.targetNodeId] = (map[edge.targetNodeId] ?: 0) + 1
        }
        map
    }

    val activeNeighbors = remember(activeNode, filteredEdges) {
        if (activeNode == null) emptySet<String>()
        else {
            val set = mutableSetOf<String>()
            filteredEdges.forEach { edge ->
                if (edge.sourceNodeId == activeNode?.id) set.add(edge.targetNodeId)
                if (edge.targetNodeId == activeNode?.id) set.add(edge.sourceNodeId)
            }
            set
        }
    }

    val nodePositions = remember(filteredNodes, filteredEdges, layoutSeed) {
        val map = mutableStateMapOf<String, Offset>()
        val computed = computeOrganicGraphLayout(filteredNodes, filteredEdges, layoutSeed)
        map.putAll(computed)
        map
    }

    LaunchedEffect(filteredNodes.size, layoutSeed) {
        if (nodePositions.isNotEmpty()) {
            val fit = calculateFitScaleAndOffset(nodePositions.values)
            scale = fit.first
            offset = fit.second
        }
    }

    val searchMatches = remember(searchQuery, filteredNodes) {
        if (searchQuery.isBlank()) emptySet<String>()
        else {
            val q = searchQuery.trim().lowercase()
            filteredNodes.filter { it.label.lowercase().contains(q) || it.summary.lowercase().contains(q) || it.nodeType.lowercase().contains(q) }
                .map { it.id }.toSet()
        }
    }

    val animateGraph = activeNode != null || searchMatches.isNotEmpty()
    val pulsePhase = if (animateGraph) {
        val transition = rememberInfiniteTransition(label = "graph_energy")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse_phase"
        ).value
    } else 0f
    val haloPulse = if (animateGraph) {
        val transition = rememberInfiniteTransition(label = "graph_halo")
        transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "halo_pulse"
        ).value
    } else 1f

    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Brain index", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isIndexing) indexingStatus else if (nodes.isEmpty()) "Nothing indexed yet" else "${nodes.size} entities · ${edges.size} links",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isIndexing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isIndexing) {
                        LinearProgressIndicator(
                            progress = { indexingProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                        )
                    }
                }
                if (isIndexing) {
                    TextButton(onClick = onPauseIndexing) { Text("Pause") }
                } else if (indexingStatus.contains("paused", ignoreCase = true)) {
                    Button(onClick = onResumeIndexing, enabled = localBrainReady, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        Text("Resume")
                    }
                } else {
                    Button(onClick = onIndexFiles, enabled = localBrainReady, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        Text("Start indexing")
                    }
                }
            }
        }
        // Minimal horizontal category filters
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearchOpen) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter graph nodes…", fontSize = 12.sp) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (searchQuery.isNotEmpty()) searchQuery = "" else isSearchOpen = false
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val filters = listOf(
                            "ALL" to ("All (" + nodes.size + ")") to primaryColor,
                            "PHOTOS" to ("Photos (" + nodes.count { it.nodeType == "IMAGE" } + ")") to Color(0xFF00BFA5),
                            "DOCS" to ("Docs (" + nodes.count { it.nodeType == "DOCUMENT" } + ")") to Color(0xFF2979FF),
                            "PLACES" to ("Places (" + nodes.count { it.nodeType == "LOCATION" } + ")") to Color(0xFFFF9100),
                            "DEVICES" to ("Devices (" + nodes.count { it.nodeType == "DEVICE" } + ")") to Color(0xFFAB47BC)
                        )
                        items(filters) { item ->
                            val key = item.first.first
                            val label = item.first.second
                            val dotColor = item.second
                            FilterChip(
                                selected = selectedFilter == key,
                                onClick = {
                                    selectedFilter = key
                                    activeNode = null
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                },
                                label = { Text(label, fontSize = 11.sp, fontWeight = if (selectedFilter == key) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { showNodeList = !showNodeList },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.List, contentDescription = if (showNodeList) "Hide accessible node list" else "Show accessible node list", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { isSearchOpen = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search nodes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (showNodeList) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .semantics { contentDescription = "Accessible Brain node list" }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Text(
                            "Accessible node list",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(filteredNodes.take(80), key = { it.id }) { node ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNodeClick(node) }
                                .semantics {
                                    contentDescription = "${node.label}, ${node.nodeType}, ${degreeMap[node.id] ?: 0} connections"
                                    role = Role.Button
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    getNodeIcon(node.nodeType),
                                    contentDescription = null,
                                    tint = getNodeColor(node.nodeType, primaryColor),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(node.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${node.nodeType} • ${degreeMap[node.id] ?: 0} connections",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (filteredNodes.size > 80) {
                        item {
                            Text(
                                "Showing the first 80 nodes. Use Search to narrow the list.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Dedicated Full-Bleed Graph Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(filteredNodes, scale, offset) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val centerOffset = Offset(size.width / 2f, size.height / 2f)
                            var closestNode: BrainNodeEntity? = null
                            var closestDist = Float.MAX_VALUE
                            val minTouchTargetPx = 36f * density

                            for (node in filteredNodes) {
                                val pos = nodePositions[node.id] ?: continue
                                val screenPos = centerOffset + offset + (pos * scale)
                                val deg = degreeMap[node.id] ?: 0
                                val baseRadius = 14f + deg.coerceAtMost(8) * 1.8f
                                val nodeRadiusPx = (baseRadius * scale).coerceIn(8f, 48f)
                                val touchRadiusPx = maxOf(nodeRadiusPx + 16f * density, minTouchTargetPx)
                                val dist = (tapOffset - screenPos).getDistance()
                                if (dist <= touchRadiusPx && dist < closestDist) {
                                    closestDist = dist
                                    closestNode = node
                                }
                            }
                            activeNode = closestNode
                        },
                        onDoubleTap = { tapOffset ->
                            val centerOffset = Offset(size.width / 2f, size.height / 2f)
                            var closestNode: BrainNodeEntity? = null
                            var closestDist = Float.MAX_VALUE
                            val minTouchTargetPx = 36f * density

                            for (node in filteredNodes) {
                                val pos = nodePositions[node.id] ?: continue
                                val screenPos = centerOffset + offset + (pos * scale)
                                val deg = degreeMap[node.id] ?: 0
                                val baseRadius = 14f + deg.coerceAtMost(8) * 1.8f
                                val nodeRadiusPx = (baseRadius * scale).coerceIn(8f, 48f)
                                val touchRadiusPx = maxOf(nodeRadiusPx + 16f * density, minTouchTargetPx)
                                val dist = (tapOffset - screenPos).getDistance()
                                if (dist <= touchRadiusPx && dist < closestDist) {
                                    closestDist = dist
                                    closestNode = node
                                }
                            }
                            if (closestNode != null) {
                                onNodeClick(closestNode)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.35f, 3.5f)
                        val centerOffset = Offset(size.width / 2f, size.height / 2f)
                        val focus = centroid - centerOffset - offset
                        offset = offset + pan - focus * (newScale / scale - 1f)
                        scale = newScale
                    }
                }
                .testTag("interactive_graph_canvas")
        ) {
            if (filteredNodes.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                             if (!localBrainReady) "Configure an embedding model" else "No indexed entities found",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                             if (!localBrainReady) "Choose an embedding provider and model in Brain Settings before indexing." else "Brain is ready. Start indexing to build the semantic graph.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onIndexFiles,
                             enabled = !isIndexing && localBrainReady
                        ) {
                             Text(if (isIndexing) "Indexing…" else if (localBrainReady) "Start indexing" else "Open Brain Settings")
                        }
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                // 1. Subtle spatial dot grid
                val gridSpacing = 44f * scale
                val startX = (offset.x + centerOffset.x) % gridSpacing
                val startY = (offset.y + centerOffset.y) % gridSpacing
                val dotColor = outlineColor.copy(alpha = 0.12f)
                var gx = startX - gridSpacing
                while (gx < size.width + gridSpacing) {
                    var gy = startY - gridSpacing
                    while (gy < size.height + gridSpacing) {
                        drawCircle(color = dotColor, radius = 1.2f, center = Offset(gx, gy))
                        gy += gridSpacing
                    }
                    gx += gridSpacing
                }

                // 2. Render Edges
                filteredEdges.forEachIndexed { index, edge ->
                    val p1 = nodePositions[edge.sourceNodeId] ?: return@forEachIndexed
                    val p2 = nodePositions[edge.targetNodeId] ?: return@forEachIndexed

                    val startScreen = centerOffset + offset + (p1 * scale)
                    val endScreen = centerOffset + offset + (p2 * scale)

                    val isConnectedToActive = activeNode != null && (edge.sourceNodeId == activeNode?.id || edge.targetNodeId == activeNode?.id)
                    val isDimmed = activeNode != null && !isConnectedToActive

                    val edgeColor = if (isConnectedToActive) {
                        primaryColor
                    } else if (isDimmed) {
                        outlineColor.copy(alpha = 0.08f)
                    } else {
                        outlineColor.copy(alpha = 0.28f)
                    }

                    val strokeWidth = if (isConnectedToActive) 2.6f * scale else 1.0f * scale

                    drawLine(
                        color = edgeColor,
                        start = startScreen,
                        end = endScreen,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )

                    // Animated travelling energy particles on active links
                    if (isConnectedToActive) {
                        val travel = (pulsePhase + (index * 0.22f)) % 1f
                        val particlePos = startScreen + (endScreen - startScreen) * travel
                        drawCircle(
                            color = primaryColor,
                            radius = 3.2f * scale,
                            center = particlePos
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.6f * scale,
                            center = particlePos
                        )
                    }
                }

                // 3. Render Nodes
                for (node in filteredNodes) {
                    val pos = nodePositions[node.id] ?: continue
                    val screenPos = centerOffset + offset + (pos * scale)

                    val isSelected = activeNode?.id == node.id
                    val isNeighbor = activeNeighbors.contains(node.id)
                    val isSearchMatch = searchMatches.contains(node.id)

                    val isHighlighted = activeNode == null || isSelected || isNeighbor
                    val isDimmed = !isHighlighted

                    val deg = degreeMap[node.id] ?: 0
                    val baseRadius = 14f + deg.coerceAtMost(8) * 1.8f
                    val radius = (baseRadius * scale).coerceIn(8f, 48f)

                    val nodeColor = getNodeColor(node.nodeType, primaryColor)

                    // Search match pulsing double ring
                    if (isSearchMatch) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.35f * haloPulse),
                            radius = radius + (14f * scale * haloPulse),
                            center = screenPos
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = radius + (6f * scale),
                            center = screenPos,
                            style = Stroke(width = 2f * scale)
                        )
                    }

                    // Active Node Breathing Halo
                    if (isSelected) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.28f * haloPulse),
                            radius = radius + (10f * scale * haloPulse),
                            center = screenPos
                        )
                    } else if (isNeighbor) {
                        drawCircle(
                            color = nodeColor.copy(alpha = 0.16f),
                            radius = radius + (5f * scale),
                            center = screenPos
                        )
                    }

                    // Node Body Fill
                    val bodyColor = if (isDimmed) nodeColor.copy(alpha = 0.20f) else nodeColor
                    drawCircle(
                        color = bodyColor,
                        radius = radius,
                        center = screenPos
                    )

                    // Crisp Contrast Border
                    val borderColor = if (isSelected) {
                        primaryColor
                    } else if (isDimmed) {
                        surfaceColor.copy(alpha = 0.25f)
                    } else {
                        surfaceColor
                    }
                    drawCircle(
                        color = borderColor,
                        radius = radius,
                        center = screenPos,
                        style = Stroke(width = if (isSelected) 2.4f * scale else 1.2f * scale)
                    )

                    // Node Label - Only show for selected, neighbor, or search matches to avoid visual clutter
                    val shouldShowLabel = isSelected || isNeighbor || isSearchMatch || (scale >= 1.0f && deg >= 2)
                    if (shouldShowLabel) {
                        val maxChars = if (isSelected) 22 else 12
                        val displayText = if (node.label.length > maxChars) node.label.take(maxChars) + "…" else node.label
                        val labelSizeSp = (10 * scale).coerceIn(8f, 12f).sp
                        val textLayout = textMeasurer.measure(
                            text = displayText,
                            style = TextStyle(
                                fontSize = labelSizeSp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDimmed) onSurfaceColor.copy(alpha = 0.35f) else onSurfaceColor
                            )
                        )

                        val padH = 5f * scale
                        val padV = 2f * scale
                        val pillWidth = textLayout.size.width + padH * 2
                        val pillHeight = textLayout.size.height + padV * 2
                        val pillTopLeft = Offset(screenPos.x - (pillWidth / 2f), screenPos.y + radius + 4f * scale)

                        drawRoundRect(
                            color = surfaceColor.copy(alpha = if (isDimmed) 0.4f else 0.90f),
                            topLeft = pillTopLeft,
                            size = Size(pillWidth, pillHeight),
                            cornerRadius = CornerRadius(4f * scale, 4f * scale)
                        )

                        if (isSelected) {
                            drawRoundRect(
                                color = primaryColor,
                                topLeft = pillTopLeft,
                                size = Size(pillWidth, pillHeight),
                                cornerRadius = CornerRadius(4f * scale, 4f * scale),
                                style = Stroke(width = 1.0f * scale)
                            )
                        }

                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(pillTopLeft.x + padH, pillTopLeft.y + padV)
                        )
                    }
                }
            }

            // Top-Start Discreet Stats Pill
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${filteredNodes.size} Nodes • ${filteredEdges.size} Links",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Decluttered Bottom-End Floating Action Island
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = if (activeNode != null) 160.dp else 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { scale = (scale * 1.25f).coerceAtMost(3.5f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { scale = (scale * 0.8f).coerceAtLeast(0.35f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            val fit = calculateFitScaleAndOffset(nodePositions.values)
                            scale = fit.first
                            offset = fit.second
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "Recenter", modifier = Modifier.size(16.dp))
                    }
                    if (activeNode != null) {
                        IconButton(
                            onClick = {
                                val p = nodePositions[activeNode?.id]
                                if (p != null) {
                                    offset = -p * scale
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.FilterCenterFocus,
                                contentDescription = "Focus",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Sleek Bottom Floating Node Quick Info Card
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = activeNode != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    activeNode?.let { node ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val nColor = getNodeColor(node.nodeType, primaryColor)
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(nColor.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getNodeIcon(node.nodeType),
                                                contentDescription = null,
                                                tint = nColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = nColor.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = node.nodeType,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = nColor,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val connCount = degreeMap[node.id] ?: 0
                                                Text(
                                                    text = "$connCount link${if (connCount == 1) "" else "s"}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = node.label,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { activeNode = null },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                                    }
                                }

                                if (node.summary.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = node.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    node.sourceFilePath?.let { path ->
                                        val f = File(path)
                                        FilledTonalButton(
                                            onClick = {
                                                if (node.nodeType == "IMAGE") onOpenImage(f) else onOpenFile(f)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Open", fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    FilledTonalButton(
                                        onClick = { onAskAiForFile(node) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ask AI", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))

                                    Button(
                                        onClick = { onNodeClick(node) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Inspect", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: DEDICATED DECLUTTERED TOPICS VIEW
// -------------------------------------------------------------

@Composable
fun DeclutteredTopicsView(
    topics: List<BrainTopicEntity>,
    selectedTopic: BrainTopicEntity?,
    relevantFiles: List<BrainTopicFile>,
    isLoading: Boolean,
    status: String,
    onSelectTopic: (String) -> Unit,
    onSaveTopic: (String, String, String?) -> Unit,
    onDeleteTopic: (String) -> Unit,
    onAskAiForFile: (BrainNodeEntity) -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenImage: (File) -> Unit
) {
    var isDialogOpen by remember { mutableStateOf(false) }
    var editingTopicId by remember { mutableStateOf<String?>(null) }
    var heading by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Topics Header & Add Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Smart Topics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Saved semantic filters that surface relevant files; Ask AI is for answering questions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = {
                    editingTopicId = null
                    heading = ""
                    description = ""
                    isDialogOpen = true
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Topic", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (topics.isEmpty()) {
            // Friendly Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        "No topics created yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Create topics like 'Work Receipts', 'Travel Hawaii', or 'Tax Records' to let Brain automatically group relevant files semantically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Button(
                        onClick = {
                            editingTopicId = null
                            heading = ""
                            description = ""
                            isDialogOpen = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create First Topic")
                    }
                }
            }
        } else {
            // Horizontal topic selectors
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(topics, key = { it.id }) { topic ->
                    FilterChip(
                        selected = selectedTopic?.id == topic.id,
                        onClick = { onSelectTopic(topic.id) },
                        label = {
                            Text(
                                topic.heading,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTopic?.id == topic.id) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.height(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Active Topic Details
            selectedTopic?.let { topic ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    Icons.Default.Hub,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    topic.heading,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        editingTopicId = topic.id
                                        heading = topic.heading
                                        description = topic.description
                                        isDialogOpen = true
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit topic", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { onDeleteTopic(topic.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete topic",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (topic.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                topic.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (status.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Matched Files Section
                Text(
                    text = "Matched Files (${relevantFiles.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                } else if (relevantFiles.isEmpty()) {
                    Text(
                        "No semantically matched files yet. Brain will link files as they are scanned.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(relevantFiles, key = { it.node.id }) { match ->
                            val node = match.node
                            val file = node.sourceFilePath?.let(::File)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = if (node.nodeType == "IMAGE") Icons.Default.Image else Icons.Default.Description
                                    val tint = if (node.nodeType == "IMAGE") ColorImages else ColorDocuments

                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(tint.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(node.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        if (node.summary.isNotBlank()) {
                                            Text(node.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (file != null) {
                                        IconButton(
                                            onClick = {
                                                if (node.nodeType == "IMAGE") onOpenImage(file) else onOpenFile(file)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = "Open",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onAskAiForFile(node) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = "Ask AI",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Clean Modal Dialog for Creating/Editing Topics
    if (isDialogOpen) {
        AlertDialog(
            onDismissRequest = { isDialogOpen = false },
            title = {
                Text(if (editingTopicId == null) "New Smart Topic" else "Edit Topic")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = heading,
                        onValueChange = { heading = it },
                        singleLine = true,
                        label = { Text("Topic Name") },
                        placeholder = { Text("e.g. Travel receipts, Medical") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        minLines = 2,
                        maxLines = 4,
                        label = { Text("Semantic Criteria") },
                        placeholder = { Text("What should Brain look for in files?") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (heading.isNotBlank()) {
                            onSaveTopic(heading.trim(), description.trim(), editingTopicId)
                            isDialogOpen = false
                        }
                    },
                    enabled = heading.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { isDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 4: DECLUTTERED CONNECTIONS VIEW
// -------------------------------------------------------------

@Composable
fun DeclutteredConnectionsView(
    nodes: List<BrainNodeEntity>,
    edges: List<BrainEdgeEntity>,
    onNodeClick: (BrainNodeEntity) -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenImage: (File) -> Unit,
    onIndexFiles: () -> Unit
) {
    if (edges.isEmpty()) {
        DeclutteredEmptyState(
            title = "No Connections Discovered",
            message = "Scan your device storage to link camera photos with documents and discover shared locations.",
            actionLabel = "Scan Storage",
            onAction = onIndexFiles
        )
        return
    }

    val nodeMap = remember(nodes) { nodes.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = edges, key = { edge -> "${edge.sourceNodeId}|${edge.relation}|${edge.targetNodeId}|${edge.evidenceSource.orEmpty()}" }) { edge ->
            val source = nodeMap[edge.sourceNodeId]
            val target = nodeMap[edge.targetNodeId]

            if (source != null && target != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Source Node Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        source.sourceFilePath?.let {
                                            val f = File(it)
                                            if (source.nodeType == "IMAGE") onOpenImage(f) else onOpenFile(f)
                                        } ?: onNodeClick(source)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = getNodeIcon(source.nodeType)
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(source.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }

                            // Clean Relation Arrow
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = edge.relation.lowercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            // Target Node Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        target.sourceFilePath?.let {
                                            val f = File(it)
                                            if (target.nodeType == "IMAGE") onOpenImage(f) else onOpenFile(f)
                                        } ?: onNodeClick(target)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = getNodeIcon(target.nodeType)
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(target.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }

                        if (edge.evidenceSnippet.isNotBlank()) {
                            Text(
                                text = edge.evidenceSnippet,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// NODE DETAIL BOTTOM SHEET
// -------------------------------------------------------------

@Composable
fun DeclutteredNodeSheet(
    node: BrainNodeEntity,
    allEdges: List<BrainEdgeEntity>,
    allNodes: List<BrainNodeEntity>,
    onOpenFile: (File) -> Unit,
    onOpenImage: (File) -> Unit,
    onSelectRelatedNode: (BrainNodeEntity) -> Unit,
    onClose: () -> Unit
) {
    val nodeMap = remember(allNodes) { allNodes.associateBy { it.id } }
    val connectedEdges = remember(node, allEdges) {
        allEdges.filter { it.sourceNodeId == node.id || it.targetNodeId == node.id }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Node Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = node.nodeType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(node.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            node.sourceFilePath?.let { path ->
                val f = File(path)
                Button(
                    onClick = {
                        onClose()
                        if (node.nodeType == "IMAGE") onOpenImage(f) else onOpenFile(f)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open File", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (node.summary.isNotBlank()) {
            Text(node.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Direct Connections
        if (connectedEdges.isNotEmpty()) {
            Text(
                text = "Connected Entities (${connectedEdges.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items = connectedEdges, key = { edge -> "${edge.sourceNodeId}|${edge.relation}|${edge.targetNodeId}|${edge.evidenceSource.orEmpty()}" }) { edge ->
                    val otherId = if (edge.sourceNodeId == node.id) edge.targetNodeId else edge.sourceNodeId
                    val otherNode = nodeMap[otherId]
                    if (otherNode != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectRelatedNode(otherNode) }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = edge.relation.lowercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(80.dp)
                                )
                                Text(
                                    text = otherNode.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// -------------------------------------------------------------
// MINIMAL EMPTY & SETUP STATES
// -------------------------------------------------------------

@Composable
fun DeclutteredEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

// -------------------------------------------------------------
// FORCE-DIRECTED LAYOUT & VISUAL HELPERS
// -------------------------------------------------------------

fun getNodeColor(nodeType: String, primaryColor: Color): Color {
    return when (nodeType) {
        "IMAGE" -> Color(0xFF00BFA5)       // Mint / Teal
        "DOCUMENT" -> Color(0xFF2979FF)    // Vivid Royal Blue
        "LOCATION" -> Color(0xFFFF9100)    // Sunset Amber
        "DEVICE" -> Color(0xFFAB47BC)      // Deep Lavender
        "FOLDER" -> Color(0xFFFF4081)      // Rose / Coral
        "TOPIC" -> Color(0xFFFFD600)       // Gold
        else -> primaryColor
    }
}

fun getNodeIcon(nodeType: String) = when (nodeType) {
    "IMAGE" -> Icons.Default.Image
    "DOCUMENT" -> Icons.Default.Description
    "LOCATION" -> Icons.Default.LocationOn
    "DEVICE" -> Icons.Default.PhoneAndroid
    "FOLDER" -> Icons.Default.Folder
    else -> Icons.Default.Hub
}

fun calculateFitScaleAndOffset(positions: Collection<Offset>): Pair<Float, Offset> {
    if (positions.isEmpty()) return 1f to Offset.Zero
    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (p in positions) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    val spanX = (maxX - minX).coerceAtLeast(100f) + 120f
    val spanY = (maxY - minY).coerceAtLeast(100f) + 120f
    val fitScale = minOf(650f / spanX, 850f / spanY).coerceIn(0.45f, 1.8f)
    val midX = (minX + maxX) / 2f
    val midY = (minY + maxY) / 2f
    val fitOffset = Offset(-midX * fitScale, -midY * fitScale)
    return fitScale to fitOffset
}

fun computeOrganicGraphLayout(
    nodes: List<BrainNodeEntity>,
    edges: List<BrainEdgeEntity>,
    seed: Long = 42L
): Map<String, Offset> {
    if (nodes.isEmpty()) return emptyMap()
    val count = nodes.size
    val random = java.util.Random(seed)

    val degreeMap = mutableMapOf<String, Int>()
    edges.forEach { edge ->
        degreeMap[edge.sourceNodeId] = (degreeMap[edge.sourceNodeId] ?: 0) + 1
        degreeMap[edge.targetNodeId] = (degreeMap[edge.targetNodeId] ?: 0) + 1
    }

    val adjacency = mutableMapOf<String, MutableSet<String>>()
    nodes.forEach { adjacency[it.id] = mutableSetOf() }
    edges.forEach { edge ->
        adjacency[edge.sourceNodeId]?.add(edge.targetNodeId)
        adjacency[edge.targetNodeId]?.add(edge.sourceNodeId)
    }

    val positions = mutableMapOf<String, Offset>()
    val sortedNodes = nodes.sortedByDescending { degreeMap[it.id] ?: 0 }
    val initialRadius = (count * 24f).coerceIn(150f, 440f)

    sortedNodes.forEachIndexed { i, node ->
        val deg = degreeMap[node.id] ?: 0
        if (deg >= 2) {
            val angle = (2 * Math.PI * i / sortedNodes.size).toFloat()
            val r = initialRadius * 0.42f + (random.nextFloat() - 0.5f) * 30f
            positions[node.id] = Offset((r * cos(angle.toDouble())).toFloat(), (r * sin(angle.toDouble())).toFloat())
        } else {
            val placedNeighbor = adjacency[node.id]?.firstOrNull { positions.containsKey(it) }
            if (placedNeighbor != null) {
                val nPos = positions[placedNeighbor]!!
                val angle = random.nextFloat() * 2 * Math.PI.toFloat()
                val dist = 60f + random.nextFloat() * 40f
                positions[node.id] = Offset(nPos.x + (dist * cos(angle.toDouble())).toFloat(), nPos.y + (dist * sin(angle.toDouble())).toFloat())
            } else {
                val angle = (2 * Math.PI * i / sortedNodes.size).toFloat()
                val r = initialRadius * 0.85f + (random.nextFloat() - 0.5f) * 40f
                positions[node.id] = Offset((r * cos(angle.toDouble())).toFloat(), (r * sin(angle.toDouble())).toFloat())
            }
        }
    }

    val k = 110f
    val kSq = k * k
    val tempMax = 20f
    val iterations = when {
        count <= 80 -> 30
        count <= 160 -> 20
        else -> 12
    }

    val currentX = positions.mapValues { it.value.x }.toMutableMap()
    val currentY = positions.mapValues { it.value.y }.toMutableMap()

    for (step in 0 until iterations) {
        val temp = tempMax * (1f - step.toFloat() / iterations)
        val dispX = mutableMapOf<String, Float>()
        val dispY = mutableMapOf<String, Float>()
        nodes.forEach {
            dispX[it.id] = 0f
            dispY[it.id] = 0f
        }

        // 1. Repulsion between node pairs
        for (i in 0 until count) {
            val u = nodes[i].id
            val ux = currentX[u] ?: continue
            val uy = currentY[u] ?: continue
            for (j in i + 1 until count) {
                val v = nodes[j].id
                val vx = currentX[v] ?: continue
                val vy = currentY[v] ?: continue
                var dx = ux - vx
                var dy = uy - vy
                var dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist < 1f) {
                    dx = (random.nextFloat() - 0.5f) * 2f
                    dy = (random.nextFloat() - 0.5f) * 2f
                    dist = 1f
                }
                val repForce = (kSq / dist).coerceAtMost(250f)
                val fx = (dx / dist) * repForce
                val fy = (dy / dist) * repForce
                dispX[u] = dispX[u]!! + fx
                dispY[u] = dispY[u]!! + fy
                dispX[v] = dispX[v]!! - fx
                dispY[v] = dispY[v]!! - fy
            }
        }

        // 2. Attraction along edges
        for (edge in edges) {
            val sx = currentX[edge.sourceNodeId] ?: continue
            val sy = currentY[edge.sourceNodeId] ?: continue
            val tx = currentX[edge.targetNodeId] ?: continue
            val ty = currentY[edge.targetNodeId] ?: continue
            val dx = sx - tx
            val dy = sy - ty
            val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            val attForce = (dist * dist / k).coerceAtMost(200f)
            val fx = (dx / dist) * attForce
            val fy = (dy / dist) * attForce
            dispX[edge.sourceNodeId] = dispX[edge.sourceNodeId]!! - fx
            dispY[edge.sourceNodeId] = dispY[edge.sourceNodeId]!! - fy
            dispX[edge.targetNodeId] = dispX[edge.targetNodeId]!! + fx
            dispY[edge.targetNodeId] = dispY[edge.targetNodeId]!! + fy
        }

        // 3. Center gravity
        for (node in nodes) {
            val px = currentX[node.id] ?: continue
            val py = currentY[node.id] ?: continue
            val distToCenter = Math.hypot(px.toDouble(), py.toDouble()).toFloat()
            val grav = 0.04f * distToCenter
            dispX[node.id] = dispX[node.id]!! - (px / (distToCenter + 1f)) * grav
            dispY[node.id] = dispY[node.id]!! - (py / (distToCenter + 1f)) * grav
        }

        // 4. Displace with temperature cap
        for (node in nodes) {
            val px = currentX[node.id] ?: continue
            val py = currentY[node.id] ?: continue
            val dx = dispX[node.id] ?: 0f
            val dy = dispY[node.id] ?: 0f
            val dispDist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.01f)
            val move = minOf(dispDist, temp)
            currentX[node.id] = px + (dx / dispDist) * move
            currentY[node.id] = py + (dy / dispDist) * move
        }
    }

    return nodes.associate { it.id to Offset(currentX[it.id] ?: 0f, currentY[it.id] ?: 0f) }
}
