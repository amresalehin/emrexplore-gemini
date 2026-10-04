package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.ai.AvailableAiModel
import com.example.data.ai.ConnectionTestResult
import com.example.data.ai.ProviderType
import com.example.data.local.AiProviderConfigEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    currentConfig: AiProviderConfigEntity,
    nodeCount: Int,
    edgeCount: Int,
    chunkCount: Int,
    isTestingConnection: Boolean,
    testResult: ConnectionTestResult?,
    onSaveConfig: (AiProviderConfigEntity) -> Unit,
    onTestConnection: (AiProviderConfigEntity) -> Unit,
    availableModels: List<AvailableAiModel> = emptyList(),
    availableVisionModels: List<AvailableAiModel> = emptyList(),
    availableEmbeddingModels: List<AvailableAiModel> = emptyList(),
    availableMultimodalEmbeddingModels: List<AvailableAiModel> = emptyList(),
    isFetchingModels: Boolean = false,
    modelFetchError: String? = null,
    onFetchModels: (AiProviderConfigEntity) -> Unit = {},
    onReindexAll: () -> Unit,
    onClearGraph: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var selectedProvider by remember { mutableStateOf(normalizeProvider(currentConfig.providerType)) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(currentConfig.baseUrl) }
    var chatModel by remember { mutableStateOf(currentConfig.chatModel) }
    var visionModel by remember { mutableStateOf(currentConfig.visionModel) }
    var embeddingModel by remember { mutableStateOf(currentConfig.textEmbeddingModel.ifBlank { currentConfig.embeddingModel }) }
    var multimodalEmbeddingModel by remember { mutableStateOf(currentConfig.multimodalEmbeddingModel) }
    var isEnabled by remember { mutableStateOf(currentConfig.isEnabled) }
    var autoSync by remember { mutableStateOf(currentConfig.autoSync) }
    var freeOnly by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var showProviderPicker by remember { mutableStateOf(false) }
    var showChatPicker by remember { mutableStateOf(false) }
    var showVisionPicker by remember { mutableStateOf(false) }
    var showEmbeddingPicker by remember { mutableStateOf(false) }
    var showMultimodalEmbeddingPicker by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    fun draftConfig(): AiProviderConfigEntity = currentConfig.copy(
        providerType = selectedProvider.name,
        apiKey = apiKey.trim(),
        baseUrl = baseUrl.trim(),
        chatModel = chatModel.trim(),
        visionModel = visionModel.trim(),
        embeddingModel = embeddingModel.trim(),
        textEmbeddingModel = embeddingModel.trim(),
        multimodalEmbeddingModel = multimodalEmbeddingModel.trim(),
        isEnabled = isEnabled,
        autoSync = autoSync
    )

    fun selectProvider(provider: ProviderType) {
        selectedProvider = provider
        if (normalizeProvider(currentConfig.providerType) == provider) {
            baseUrl = currentConfig.baseUrl
            chatModel = currentConfig.chatModel
            visionModel = currentConfig.visionModel
            embeddingModel = currentConfig.textEmbeddingModel.ifBlank { currentConfig.embeddingModel }
            multimodalEmbeddingModel = currentConfig.multimodalEmbeddingModel
            apiKey = currentConfig.apiKey
        } else {
            baseUrl = provider.defaultBaseUrl
            chatModel = ""
            visionModel = ""
            embeddingModel = provider.defaultTextEmbeddingModel
            multimodalEmbeddingModel = provider.defaultMultimodalEmbeddingModel
            apiKey = ""
        }
        showProviderPicker = false
    }

    fun usableModels(models: List<AvailableAiModel>): List<AvailableAiModel> =
        models.filter { !freeOnly || it.isFree }.distinctBy { it.id }

    fun firstUsable(models: List<AvailableAiModel>, current: String): String =
        usableModels(models).firstOrNull { it.id == current }?.id
            ?: usableModels(models).firstOrNull()?.id
            ?: current

    val chats = usableModels(availableModels)
    val visions = usableModels(availableVisionModels)
    val embeddings = usableModels(availableEmbeddingModels)
    val multimodalEmbeddings = usableModels(availableMultimodalEmbeddingModels)

    LaunchedEffect(chats, visions, embeddings, multimodalEmbeddings) {
        if (chats.isNotEmpty()) chatModel = firstUsable(chats, chatModel)
        if (visions.isNotEmpty()) visionModel = firstUsable(visions, visionModel)
        if (embeddings.isNotEmpty()) embeddingModel = firstUsable(embeddings, embeddingModel)
        if (multimodalEmbeddings.isNotEmpty()) multimodalEmbeddingModel = firstUsable(multimodalEmbeddings, multimodalEmbeddingModel)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Setup", fontWeight = FontWeight.Bold)
                        Text(
                            if (isEnabled) "Brain enabled" else "Brain is off",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("ai_settings_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { onSaveConfig(draftConfig()); onNavigateBack() },
                        modifier = Modifier.padding(end = 8.dp).testTag("ai_settings_save_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Enable Brain", fontWeight = FontWeight.Bold)
                        Text(
                            "Semantic search, file understanding and image analysis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        modifier = Modifier.testTag("ai_enabled_switch")
                    )
                }
            }

            if (!isEnabled) {
                Text("Nothing is sent to an AI provider while Brain is off.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SectionTitle("PROVIDER")
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { showProviderPicker = true },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedProvider.displayName, fontWeight = FontWeight.Bold)
                            Text(
                                when (selectedProvider) {
                                    ProviderType.GEMINI -> "Recommended · simplest cloud setup"
                                    ProviderType.OLLAMA -> "Local / private"
                                    ProviderType.CUSTOM -> "Your OpenAI-compatible endpoint"
                                    else -> "Cloud API"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Change", color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (selectedProvider != ProviderType.OLLAMA) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth().testTag("ai_custom_api_key_field"),
                        label = { Text("API key") },
                        placeholder = { Text(selectedProvider.keyHint) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val value = clip.getItemAt(0).text?.toString()?.trim().orEmpty()
                                        if (value.isNotBlank()) apiKey = value
                                    }
                                }) { Icon(Icons.Default.ContentPaste, contentDescription = "Paste API key") }
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                                    )
                                }
                            }
                        }
                    )
                }

                if (selectedProvider == ProviderType.OPENAI_COMPATIBLE) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth().testTag("ai_endpoint_url_field"),
                        label = { Text("Base URL") },
                        placeholder = { Text("https://your-server/v1") },
                        singleLine = true
                    )
                    CompactInfo("Works with OpenAI-compatible endpoints such as OpenAI, NVIDIA NIM, Groq, or your own server. Fetch models after changing it.")
                } else if (selectedProvider == ProviderType.OLLAMA) {
                    CompactInfo("No API key is required for Ollama. Use localhost or 10.0.2.2 for local connections.")
                } else {
                    CompactInfo("Server: ${baseUrl.removeSuffix("/")}")
                }

                SectionTitle("MODELS")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Fetch from provider", fontWeight = FontWeight.SemiBold)
                        Text("Only models compatible with Brain are shown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onFetchModels(draftConfig()) },
                        enabled = !isFetchingModels,
                        modifier = Modifier.testTag("ai_fetch_models_btn")
                    ) {
                        if (isFetchingModels) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Fetch models")
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Free models only", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text("No cost metadata is assumed; only explicitly free models pass this filter.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Switch(checked = freeOnly, onCheckedChange = { freeOnly = it })
                }
                modelFetchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                ModelPicker("General files / chat", firstUsable(chats, chatModel), { showChatPicker = true }, "ai_chat_model_field", chats)
                ModelPicker("Vision / image understanding + caption", firstUsable(visions, visionModel), { showVisionPicker = true }, "ai_vision_model_field", visions)
                ModelPicker("Text embeddings", firstUsable(embeddings, embeddingModel), { showEmbeddingPicker = true }, "ai_text_embedding_model_field", embeddings)
                ModelPicker("Image + text semantic search", firstUsable(multimodalEmbeddings, multimodalEmbeddingModel), { showMultimodalEmbeddingPicker = true }, "ai_multimodal_embedding_model_field", multimodalEmbeddings)

                // Offline Fallback Information Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Offline Embedding Fallback: Active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "On-device 256-D semantic embedding model acts as automatic fallback when offline or if provider embeddings are not configured.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SectionTitle("CHECK")
                Button(
                    onClick = { onTestConnection(draftConfig()) },
                    enabled = !isTestingConnection,
                    modifier = Modifier.fillMaxWidth().testTag("ai_screen_test_connection_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isTestingConnection) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isTestingConnection) "Checking…" else "Test connection")
                }

                testResult?.let { result ->
                    Surface(
                        color = if (result.success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (result.success) "Connected" else "Could not connect", fontWeight = FontWeight.Bold)
                            Text(result.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }

                HorizontalDivider()
                TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAdvanced) "Hide advanced options" else "Advanced options")
                }
                if (showAdvanced) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-index new files", fontWeight = FontWeight.SemiBold)
                            Text("Add newly indexed files to Brain automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoSync, onCheckedChange = { autoSync = it })
                    }
                    Text(
                        "API keys stay in private app storage. Cloud providers receive data required for the AI operation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showProviderPicker) ProviderPickerDialog(selectedProvider, ::selectProvider) { showProviderPicker = false }
    if (showChatPicker) ModelPickerDialog("Choose chat model", chats, chatModel, { chatModel = it; showChatPicker = false }) { showChatPicker = false }
    if (showVisionPicker) ModelPickerDialog("Choose vision model", visions, visionModel, { visionModel = it; showVisionPicker = false }) { showVisionPicker = false }
    if (showEmbeddingPicker) ModelPickerDialog("Choose text embedding model", embeddings, embeddingModel, { embeddingModel = it; showEmbeddingPicker = false }) { showEmbeddingPicker = false }
    if (showMultimodalEmbeddingPicker) ModelPickerDialog("Choose image + text embedding model", multimodalEmbeddings, multimodalEmbeddingModel, { multimodalEmbeddingModel = it; showMultimodalEmbeddingPicker = false }) { showMultimodalEmbeddingPicker = false }
}

private fun normalizeProvider(value: String): ProviderType = when (ProviderType.fromString(value)) {
    ProviderType.GROQ, ProviderType.CUSTOM -> ProviderType.OPENAI_COMPATIBLE
    else -> ProviderType.fromString(value)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun CompactInfo(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 2.dp))
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}

@Composable
private fun ModelPicker(
    label: String,
    value: String,
    onClick: () -> Unit,
    testTag: String,
    models: List<AvailableAiModel>
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(testTag),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value.ifBlank { "Tap Fetch models" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (models.isNotEmpty()) Text("${models.size} available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("Choose", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ProviderPickerDialog(selected: ProviderType, onSelect: (ProviderType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    ProviderType.GEMINI,
                    ProviderType.OPENAI_COMPATIBLE,
                    ProviderType.OLLAMA
                ).forEach { provider ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(provider) },
                        color = if (provider == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(11.dp)) {
                            Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                            Text(provider.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ModelPickerDialog(title: String, models: List<AvailableAiModel>, current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().height(420.dp)) {
                items(models.take(60), key = { it.id }) { model ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(model.id) },
                        color = if (model.id == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(model.id, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}