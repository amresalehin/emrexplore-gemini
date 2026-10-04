package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.RagAnswer
import com.example.data.local.AiProviderConfigEntity
import com.example.data.local.BrainTopicEntity
import com.example.data.local.KgEdgeEntity
import com.example.data.local.KgNodeEntity
import com.example.data.ai.BrainTopicFile
import com.example.ui.theme.ColorDocuments
import com.example.ui.theme.ColorImages
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

enum class GraphScreenTab(val label: String) {
    ASK_AI("Ask AI"),
    CANVAS("Network"),
    DOTS("Connections")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeGraphScreen(
    nodes: List<KgNodeEntity>,
    edges: List<KgEdgeEntity>,
    nodeCount: Int = nodes.size,
    edgeCount: Int = edges.size,
    aiConfig: AiProviderConfigEntity?,
    isIndexing: Boolean,
    indexingProgress: Float,
    indexingStatus: String,
    ragAnswer: RagAnswer?,
    isRagQuerying: Boolean,
    smartSuggestions: List<String> = emptyList(),
    apiConfigured: Boolean = false,
    onQueryRag: (String) -> Unit,
    onIndexAllFiles: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onAskAiForFile: (KgNodeEntity) -> Unit,
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
) {
    // Brain opens as an AI chat/search surface so the primary interaction is immediately useful.
    var selectedTab by remember { mutableStateOf(GraphScreenTab.ASK_AI) }
    var selectedNode by remember { mutableStateOf<KgNodeEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val syncRotation = if (isIndexing) {
        val transition = rememberInfiniteTransition(label = "sync_anim")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sync_rot"
        ).value
    } else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- 1. Decluttered Top Header ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // App title & live status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Brain",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (nodes.isEmpty()) {
                                    if (isIndexing) "Connecting storage..." else "Not connected to storage"
                                } else {
                                    "${nodeCount} nodes • ${edgeCount} connections"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Action buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onIndexAllFiles,
                            enabled = !isIndexing,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("kg_sync_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync",
                                tint = if (isIndexing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = if (isIndexing) Modifier.rotate(syncRotation) else Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenAiSettings,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("kg_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Seamless indexing progress bar
                AnimatedVisibility(visible = isIndexing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { indexingProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                        Text(
                            text = indexingStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        )
                    }
                }

                // Minimal M3 Tab Row
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    GraphScreenTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = tab.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }

        // --- 2. Clean Tab Content ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                GraphScreenTab.CANVAS -> {
                    DeclutteredCanvasView(
                        nodes = nodes,
                        edges = edges,
                        onNodeClick = { selectedNode = it },
                        onOpenFile = onOpenFile,
                        onOpenImage = onOpenImage,
                        onIndexFiles = onIndexAllFiles,
                        isIndexing = isIndexing,
                        brainTopics = brainTopics,
                        selectedBrainTopic = selectedBrainTopic,
                        brainTopicRelevantFiles = brainTopicRelevantFiles,
                        isBrainTopicLoading = isBrainTopicLoading,
                        brainTopicStatus = brainTopicStatus,
                        onSelectBrainTopic = onSelectBrainTopic,
                        onSaveBrainTopic = onSaveBrainTopic,
                        onDeleteBrainTopic = onDeleteBrainTopic,
                        onAskAiForFile = { node ->
                            selectedTab = GraphScreenTab.ASK_AI
                            onAskAiForFile(node)
                        }
                    )
                }
                GraphScreenTab.ASK_AI -> {
                    DeclutteredAskAiView(
                        ragAnswer = ragAnswer,
                        isQuerying = isRagQuerying,
                        smartSuggestions = smartSuggestions,
                        apiConfigured = apiConfigured,
                        onOpenAiSettings = onOpenAiSettings,
                        onQuery = onQueryRag,
                        onOpenFile = onOpenFile,
                        onOpenImage = onOpenImage
                    )
                }
                GraphScreenTab.DOTS -> {
                    DeclutteredConnectionsView(
                        nodes = nodes,
                        edges = edges,
                        onNodeClick = { selectedNode = it },
                        onOpenFile = onOpenFile,
                        onOpenImage = onOpenImage,
                        onIndexFiles = onIndexAllFiles
                    )
                }
            }
        }
    }

    // Node Detail Inspection Sheet
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

@Composable
private fun AiSetupRequiredState(
    onOpenAiSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "Set up AI to use Brain",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "AI chat needs an enabled provider. Your persisted Brain graph remains available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onOpenAiSettings,
                    modifier = Modifier.testTag("brain_setup_ai_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open AI Settings")
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: POLISHED FORCE-DIRECTED NETWORK VIEW
// -------------------------------------------------------------

@Composable
fun DeclutteredCanvasView(
    nodes: List<KgNodeEntity>,
    edges: List<KgEdgeEntity>,
    onNodeClick: (KgNodeEntity) -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenImage: (File) -> Unit,
    onIndexFiles: () -> Unit,
    isIndexing: Boolean,
    brainTopics: List<BrainTopicEntity>,
    selectedBrainTopic: BrainTopicEntity?,
    brainTopicRelevantFiles: List<BrainTopicFile>,
    isBrainTopicLoading: Boolean,
    brainTopicStatus: String,
    onSelectBrainTopic: (String) -> Unit,
    onSaveBrainTopic: (String, String, String?) -> Unit,
    onDeleteBrainTopic: (String) -> Unit,
    onAskAiForFile: (KgNodeEntity) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var activeNode by remember { mutableStateOf<KgNodeEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
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

    // Node connection degree map
    val degreeMap = remember(filteredNodes, filteredEdges) {
        val map = mutableMapOf<String, Int>()
        filteredEdges.forEach { edge ->
            map[edge.sourceNodeId] = (map[edge.sourceNodeId] ?: 0) + 1
            map[edge.targetNodeId] = (map[edge.targetNodeId] ?: 0) + 1
        }
        map
    }

    // Set of neighbors connected to the active node
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

    // Organic Force-Directed Layout Simulation
    val nodePositions = remember(filteredNodes, filteredEdges, layoutSeed) {
        val map = mutableStateMapOf<String, Offset>()
        val computed = computeOrganicGraphLayout(filteredNodes, filteredEdges, layoutSeed)
        map.putAll(computed)
        map
    }

    // Search matches
    val searchMatches = remember(searchQuery, filteredNodes) {
        if (searchQuery.isBlank()) emptySet<String>()
        else {
            val q = searchQuery.trim().lowercase()
            filteredNodes.filter { it.label.lowercase().contains(q) || it.summary.lowercase().contains(q) || it.nodeType.lowercase().contains(q) }
                .map { it.id }.toSet()
        }
    }

    // Avoid a frame-driven animation loop while the graph is idle.
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
        // Integrated top control & filter bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
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
                            placeholder = { Text("Filter network nodes...", fontSize = 12.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (searchQuery.isNotEmpty()) {
                                            searchQuery = ""
                                        } else {
                                            isSearchOpen = false
                                        }
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
                                "DEVICES" to ("Devices (" + nodes.count { it.nodeType == "DEVICE" } + ")") to Color(0xFFAB47BC),
                                "TOPICS" to "Topics" to Color(0xFFFFD600)
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
                                                .size(8.dp)
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
        }

        BrainTopicWorkspace(
            topics = brainTopics,
            selectedTopic = selectedBrainTopic,
            relevantFiles = brainTopicRelevantFiles,
            isLoading = isBrainTopicLoading,
            status = brainTopicStatus,
            onSelectTopic = onSelectBrainTopic,
            onSaveTopic = onSaveBrainTopic,
            onDeleteTopic = onDeleteBrainTopic,
            onAskAiForFile = onAskAiForFile,
            onOpenFile = onOpenFile,
            onOpenImage = onOpenImage
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Interactive Graph Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.35f, 3.5f)
                        val centerOffset = Offset(size.width / 2f, size.height / 2f)
                        val focus = centroid - centerOffset - offset
                        offset = offset + pan - focus * (newScale / scale - 1f)
                        scale = newScale
                    }
                }
                .pointerInput(filteredNodes, scale, offset) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val centerOffset = Offset(size.width / 2f, size.height / 2f)
                            val worldTap = (tapOffset - offset - centerOffset) / scale
                            var hit: KgNodeEntity? = null
                            for (node in filteredNodes) {
                                val pos = nodePositions[node.id] ?: continue
                                val deg = degreeMap[node.id] ?: 0
                                val hitRadius = 16f + deg.coerceAtMost(8) * 2f
                                if ((worldTap - pos).getDistance() <= (hitRadius + 18f)) {
                                    hit = node
                                    break
                                }
                            }
                            activeNode = hit
                        },
                        onDoubleTap = { tapOffset ->
                            val centerOffset = Offset(size.width / 2f, size.height / 2f)
                            val worldTap = (tapOffset - offset - centerOffset) / scale
                            for (node in filteredNodes) {
                                val pos = nodePositions[node.id] ?: continue
                                val deg = degreeMap[node.id] ?: 0
                                val hitRadius = 16f + deg.coerceAtMost(8) * 2f
                                if ((worldTap - pos).getDistance() <= (hitRadius + 18f)) {
                                    onNodeClick(node)
                                    break
                                }
                            }
                        }
                    )
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
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            "No indexed files yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Sync Brain to populate the network. Your topics can still be created above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onIndexFiles,
                            enabled = !isIndexing
                        ) {
                            Text(if (isIndexing) "Indexing…" else "Sync Brain")
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
                val dotColor = outlineColor.copy(alpha = 0.16f)
                var gx = startX - gridSpacing
                while (gx < size.width + gridSpacing) {
                    var gy = startY - gridSpacing
                    while (gy < size.height + gridSpacing) {
                        drawCircle(color = dotColor, radius = 1.3f, center = Offset(gx, gy))
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
                        outlineColor.copy(alpha = 0.10f)
                    } else {
                        outlineColor.copy(alpha = 0.35f)
                    }

                    val strokeWidth = if (isConnectedToActive) 2.8f * scale else 1.2f * scale

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
                            radius = 3.5f * scale,
                            center = particlePos
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.8f * scale,
                            center = particlePos
                        )

                        // Relation label at midpoint when zoomed in
                        if (scale >= 0.75f && edge.relation.isNotBlank()) {
                            val mid = (startScreen + endScreen) / 2f
                            val relText = edge.relation.lowercase()
                            val textLayout = textMeasurer.measure(
                                text = relText,
                                style = TextStyle(
                                    fontSize = (9 * scale).coerceIn(8f, 11f).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryColor
                                )
                            )
                            val padH = 4f * scale
                            val padV = 2f * scale
                            val bgWidth = textLayout.size.width + padH * 2
                            val bgHeight = textLayout.size.height + padV * 2
                            val bgTopLeft = Offset(mid.x - bgWidth / 2f, mid.y - bgHeight / 2f)

                            drawRoundRect(
                                color = surfaceColor.copy(alpha = 0.92f),
                                topLeft = bgTopLeft,
                                size = Size(bgWidth, bgHeight),
                                cornerRadius = CornerRadius(4f * scale, 4f * scale)
                            )
                            drawRoundRect(
                                color = primaryColor.copy(alpha = 0.5f),
                                topLeft = bgTopLeft,
                                size = Size(bgWidth, bgHeight),
                                cornerRadius = CornerRadius(4f * scale, 4f * scale),
                                style = Stroke(width = 0.8f * scale)
                            )
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(bgTopLeft.x + padH, bgTopLeft.y + padV)
                            )
                        }
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
                    val bodyColor = if (isDimmed) nodeColor.copy(alpha = 0.25f) else nodeColor
                    drawCircle(
                        color = bodyColor,
                        radius = radius,
                        center = screenPos
                    )

                    // Crisp Contrast Border
                    val borderColor = if (isSelected) {
                        primaryColor
                    } else if (isDimmed) {
                        surfaceColor.copy(alpha = 0.3f)
                    } else {
                        surfaceColor
                    }
                    drawCircle(
                        color = borderColor,
                        radius = radius,
                        center = screenPos,
                        style = Stroke(width = if (isSelected) 2.5f * scale else 1.4f * scale)
                    )

                    // Center Glyph / Icon representation for larger nodes
                    if (radius >= 11f && isHighlighted) {
                        val glyphColor = Color.White
                        val glyphSize = radius * 0.55f
                        when (node.nodeType) {
                            "IMAGE" -> {
                                // Sun dot + mountain ridge
                                drawCircle(color = glyphColor, radius = glyphSize * 0.25f, center = Offset(screenPos.x, screenPos.y - glyphSize * 0.35f))
                                drawLine(
                                    color = glyphColor,
                                    start = Offset(screenPos.x - glyphSize * 0.6f, screenPos.y + glyphSize * 0.35f),
                                    end = Offset(screenPos.x, screenPos.y),
                                    strokeWidth = 1.3f * scale,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = glyphColor,
                                    start = Offset(screenPos.x, screenPos.y),
                                    end = Offset(screenPos.x + glyphSize * 0.6f, screenPos.y + glyphSize * 0.35f),
                                    strokeWidth = 1.3f * scale,
                                    cap = StrokeCap.Round
                                )
                            }
                            "DOCUMENT" -> {
                                // Clean document rectangle
                                drawRoundRect(
                                    color = glyphColor,
                                    topLeft = Offset(screenPos.x - glyphSize * 0.35f, screenPos.y - glyphSize * 0.45f),
                                    size = Size(glyphSize * 0.7f, glyphSize * 0.9f),
                                    cornerRadius = CornerRadius(1.5f * scale, 1.5f * scale),
                                    style = Stroke(width = 1.2f * scale)
                                )
                            }
                            "LOCATION" -> {
                                // Pin ring + dot
                                drawCircle(
                                    color = glyphColor,
                                    radius = glyphSize * 0.45f,
                                    center = screenPos,
                                    style = Stroke(width = 1.2f * scale)
                                )
                                drawCircle(
                                    color = glyphColor,
                                    radius = glyphSize * 0.18f,
                                    center = screenPos
                                )
                            }
                            "DEVICE" -> {
                                // Phone outline
                                drawRoundRect(
                                    color = glyphColor,
                                    topLeft = Offset(screenPos.x - glyphSize * 0.3f, screenPos.y - glyphSize * 0.5f),
                                    size = Size(glyphSize * 0.6f, glyphSize),
                                    cornerRadius = CornerRadius(2f * scale, 2f * scale),
                                    style = Stroke(width = 1.2f * scale)
                                )
                            }
                            else -> {
                                // Center neural dot
                                drawCircle(
                                    color = glyphColor,
                                    radius = glyphSize * 0.35f,
                                    center = screenPos
                                )
                            }
                        }
                    }

                    // Node Label with Crisp Pill Container
                    val shouldShowLabel = isSelected || isNeighbor || isSearchMatch || (scale >= 0.65f && (!isDimmed || deg >= 2))
                    if (shouldShowLabel) {
                        val maxChars = if (isSelected) 22 else 14
                        val displayText = if (node.label.length > maxChars) node.label.take(maxChars) + "…" else node.label
                        val labelSizeSp = (10 * scale).coerceIn(8f, 13f).sp
                        val textLayout = textMeasurer.measure(
                            text = displayText,
                            style = TextStyle(
                                fontSize = labelSizeSp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDimmed) onSurfaceColor.copy(alpha = 0.4f) else onSurfaceColor
                            )
                        )

                        val padH = 6f * scale
                        val padV = 2.5f * scale
                        val pillWidth = textLayout.size.width + padH * 2
                        val pillHeight = textLayout.size.height + padV * 2
                        val pillTopLeft = Offset(screenPos.x - (pillWidth / 2f), screenPos.y + radius + 4f * scale)

                        // Pill background
                        drawRoundRect(
                            color = surfaceColor.copy(alpha = if (isDimmed) 0.5f else 0.90f),
                            topLeft = pillTopLeft,
                            size = Size(pillWidth, pillHeight),
                            cornerRadius = CornerRadius(5f * scale, 5f * scale)
                        )

                        // Pill border
                        if (isSelected) {
                            drawRoundRect(
                                color = primaryColor,
                                topLeft = pillTopLeft,
                                size = Size(pillWidth, pillHeight),
                                cornerRadius = CornerRadius(5f * scale, 5f * scale),
                                style = Stroke(width = 1.2f * scale)
                            )
                        } else if (!isDimmed) {
                            drawRoundRect(
                                color = outlineColor.copy(alpha = 0.35f),
                                topLeft = pillTopLeft,
                                size = Size(pillWidth, pillHeight),
                                cornerRadius = CornerRadius(5f * scale, 5f * scale),
                                style = Stroke(width = 0.6f * scale)
                            )
                        }

                        // Pill text
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(pillTopLeft.x + padH, pillTopLeft.y + padV)
                        )
                    }
                }
            }

            // Top-Start Stats Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${filteredNodes.size} Nodes • ${filteredEdges.size} Links",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (searchMatches.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${searchMatches.size} found",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Bottom-End Floating Action HUD
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = if (activeNode != null) 160.dp else 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { scale = (scale * 1.25f).coerceAtMost(3.5f) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { scale = (scale * 0.8f).coerceAtLeast(0.35f) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            val fit = calculateFitScaleAndOffset(nodePositions.values)
                            scale = fit.first
                            offset = fit.second
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "Recenter", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            layoutSeed = System.currentTimeMillis()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Settle Physics",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Bottom Floating Node Quick Info Card (Shows without obscuring full graph)
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
                            shape = RoundedCornerShape(18.dp),
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
                                    Surface(
                                        shape = CircleShape,
                                        color = nColor.copy(alpha = 0.16f),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = getNodeIcon(node.nodeType),
                                                contentDescription = null,
                                                tint = nColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
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
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val connCount = degreeMap[node.id] ?: 0
                                            Text(
                                                text = "$connCount connection${if (connCount == 1) "" else "s"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
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
                                Spacer(modifier = Modifier.height(6.dp))
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
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Button(
                                    onClick = { onNodeClick(node) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Explore Connections", fontSize = 12.sp)
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

@Composable
private fun BrainTopicWorkspace(
    topics: List<BrainTopicEntity>,
    selectedTopic: BrainTopicEntity?,
    relevantFiles: List<BrainTopicFile>,
    isLoading: Boolean,
    status: String,
    onSelectTopic: (String) -> Unit,
    onSaveTopic: (String, String, String?) -> Unit,
    onDeleteTopic: (String) -> Unit,
    onAskAiForFile: (KgNodeEntity) -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenImage: (File) -> Unit
) {
    var isEditorOpen by remember { mutableStateOf(false) }
    var editingTopicId by remember { mutableStateOf<String?>(null) }
    var heading by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Topics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Define a context and let Brain find related files semantically.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = {
                        editingTopicId = null
                        heading = ""
                        description = ""
                        isEditorOpen = true
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add topic", fontSize = 12.sp)
                }
            }

            if (topics.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(topics, key = { it.id }) { topic ->
                        FilterChip(
                            selected = selectedTopic?.id == topic.id,
                            onClick = { onSelectTopic(topic.id) },
                            label = {
                                Text(
                                    topic.heading,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            if (isEditorOpen) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (editingTopicId == null) "New topic" else "Edit topic",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = heading,
                            onValueChange = { heading = it },
                            singleLine = true,
                            label = { Text("Heading") },
                            placeholder = { Text("e.g. Medical studies") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            minLines = 2,
                            maxLines = 4,
                            label = { Text("Description") },
                            placeholder = { Text("What should Brain look for in this topic?") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (editingTopicId != null) {
                                IconButton(
                                    onClick = {
                                        onDeleteTopic(editingTopicId!!)
                                        isEditorOpen = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete topic",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            FilledTonalButton(onClick = { isEditorOpen = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    if (heading.isNotBlank()) {
                                        onSaveTopic(heading.trim(), description.trim(), editingTopicId)
                                        isEditorOpen = false
                                    }
                                },
                                enabled = heading.isNotBlank()
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }

            selectedTopic?.let { topic ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                topic.heading,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    editingTopicId = topic.id
                                    heading = topic.heading
                                    description = topic.description
                                    isEditorOpen = true
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit topic", modifier = Modifier.size(16.dp))
                            }
                        }
                        if (topic.description.isNotBlank()) {
                            Text(
                                topic.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Relevant files",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                            )
                        } else if (relevantFiles.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(relevantFiles, key = { it.node.id }) { match ->
                                    val node = match.node
                                    val file = node.sourceFilePath?.let(::File)
                                    Card(
                                        modifier = Modifier.width(230.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    if (node.nodeType == "IMAGE") Icons.Default.Image else Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = if (node.nodeType == "IMAGE") ColorImages else ColorDocuments,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    node.label,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (node.summary.isNotBlank()) {
                                                Text(
                                                    node.summary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (file != null) {
                                                    FilledTonalButton(
                                                        onClick = {
                                                            if (node.nodeType == "IMAGE") onOpenImage(file) else onOpenFile(file)
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                                        modifier = Modifier
                                                            .height(30.dp)
                                                            .weight(1f)
                                                    ) {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.OpenInNew,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("Open", fontSize = 11.sp)
                                                    }
                                                }
                                                Button(
                                                    onClick = { onAskAiForFile(node) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                                    modifier = Modifier
                                                        .height(30.dp)
                                                        .weight(1f)
                                                ) {
                                                    Icon(
                                                        Icons.Default.AutoAwesome,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Ask AI", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (status.isNotBlank()) {
                            Text(
                                "Brain has not found semantically relevant files yet.",
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
    var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE
    var maxY = Float.MIN_VALUE
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
    nodes: List<KgNodeEntity>,
    edges: List<KgEdgeEntity>,
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

    // Force-directed simulation iterations
    val k = 110f
    val kSq = k * k
    val tempMax = 20f
    // Layout cost grows quadratically with node count. Fewer iterations keep
    // larger real-world libraries responsive while preserving a readable layout.
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

        // 1. Repulsion between all node pairs
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

// -------------------------------------------------------------
// TAB 2: DECLUTTERED ASK AI & SEARCH VIEW (CONNECTED TO REAL DATA)
// -------------------------------------------------------------

@Composable
fun DeclutteredAskAiView(
    ragAnswer: RagAnswer?,
    isQuerying: Boolean,
    smartSuggestions: List<String>,
    apiConfigured: Boolean,
    onOpenAiSettings: () -> Unit,
    onQuery: (String) -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenImage: (File) -> Unit
) {
    if (!apiConfigured) {
        AiSetupRequiredState(onOpenAiSettings = onOpenAiSettings)
        return
    }

    var queryText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Query Input Field
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    placeholder = { Text("Search files, places, or notes...", fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (queryText.isNotBlank() && !isQuerying) onQuery(queryText.trim())
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("rag_query_input")
                )
                if (queryText.isNotEmpty()) {
                    IconButton(onClick = { queryText = "" }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(
                    onClick = { if (queryText.isNotBlank()) onQuery(queryText.trim()) },
                    enabled = queryText.isNotBlank() && !isQuerying,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("rag_submit_query_btn")
                ) {
                    if (isQuerying) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (queryText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dynamic, honest suggestions based on real files
        val suggestionsToShow = if (smartSuggestions.isNotEmpty()) {
            smartSuggestions
        } else {
            listOf("Search documents", "Find camera photos", "Show locations")
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(suggestionsToShow) { prompt ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.clickable {
                        queryText = prompt
                        onQuery(prompt)
                    }
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (ragAnswer != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Answer Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Results", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Results", ragAnswer.answer))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(15.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ragAnswer.answer,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Sources list
                if (ragAnswer.connectedNodes.isNotEmpty()) {
                    item {
                        Text(
                            text = "Matching Files & Entities (${ragAnswer.connectedNodes.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    items(ragAnswer.connectedNodes) { node ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    node.sourceFilePath?.let { path ->
                                        val f = File(path)
                                        if (node.nodeType == "IMAGE") onOpenImage(f) else onOpenFile(f)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when (node.nodeType) {
                                    "IMAGE" -> Icons.Default.Image
                                    "DOCUMENT" -> Icons.Default.Description
                                    "LOCATION" -> Icons.Default.LocationOn
                                    "DEVICE" -> Icons.Default.PhoneAndroid
                                    "FOLDER" -> Icons.Default.Folder
                                    else -> Icons.Default.Hub
                                }
                                val tint = when (node.nodeType) {
                                    "IMAGE" -> ColorImages
                                    "DOCUMENT" -> ColorDocuments
                                    "LOCATION" -> Color(0xFFE65100)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(node.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(node.summary.take(80), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                if (node.sourceFilePath != null) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
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
// TAB 3: DECLUTTERED CONNECTIONS VIEW
// -------------------------------------------------------------

@Composable
fun DeclutteredConnectionsView(
    nodes: List<KgNodeEntity>,
    edges: List<KgEdgeEntity>,
    onNodeClick: (KgNodeEntity) -> Unit,
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
        items(edges) { edge ->
            val source = nodeMap[edge.sourceNodeId]
            val target = nodeMap[edge.targetNodeId]

            if (source != null && target != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Source Node
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
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
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = if (source.nodeType == "IMAGE") Icons.Default.Image else Icons.Default.Description
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(source.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }

                            // Clean Relation Arrow
                            Text(
                                text = " → ${edge.relation.lowercase()} → ",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            // Target Node
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
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
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = when (target.nodeType) {
                                        "IMAGE" -> Icons.Default.Image
                                        "DOCUMENT" -> Icons.Default.Description
                                        "LOCATION" -> Icons.Default.LocationOn
                                        "DEVICE" -> Icons.Default.PhoneAndroid
                                        "FOLDER" -> Icons.Default.Folder
                                        else -> Icons.Default.Hub
                                    }
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(target.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
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
    node: KgNodeEntity,
    allEdges: List<KgEdgeEntity>,
    allNodes: List<KgNodeEntity>,
    onOpenFile: (File) -> Unit,
    onOpenImage: (File) -> Unit,
    onSelectRelatedNode: (KgNodeEntity) -> Unit,
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
                text = "Connected (${connectedEdges.size})",
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
                items(connectedEdges) { edge ->
                    val otherId = if (edge.sourceNodeId == node.id) edge.targetNodeId else edge.sourceNodeId
                    val otherNode = nodeMap[otherId]
                    if (otherNode != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
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
// MINIMAL DECLUTTERED EMPTY STATE
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
