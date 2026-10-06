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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.data.ai.EmbeddingProviderType
import com.example.data.ai.isKeylessAiConfig
import com.example.data.brain.OnDeviceBrainModelSpec
import com.example.data.brain.OnDeviceBrainModelStatus
import com.example.data.brain.OnDeviceBrainModelUiState
import com.example.data.local.AiProviderConfigEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    currentConfig: AiProviderConfigEntity,
    isTestingConnection: Boolean,
    testResult: ConnectionTestResult?,
    onSaveConfig: (AiProviderConfigEntity) -> Unit,
    onTestConnection: (AiProviderConfigEntity) -> Unit,
    availableModels: List<AvailableAiModel> = emptyList(),
    availableVisionModels: List<AvailableAiModel> = emptyList(),
    availableEmbeddingModels: List<AvailableAiModel> = emptyList(),
    availableMultimodalEmbeddingModels: List<AvailableAiModel> = emptyList(),
    offlineBrainModels: List<OnDeviceBrainModelSpec> = emptyList(),
    selectedOfflineBrainModelId: String = "",
    onSelectOfflineBrainModel: (String) -> Unit = {},
    isFetchingModels: Boolean = false,
    modelFetchError: String? = null,
    onFetchModels: (AiProviderConfigEntity) -> Unit = {},
    onFetchEmbeddingModels: (AiProviderConfigEntity) -> Unit = {},
    onTestEmbeddingConnection: (AiProviderConfigEntity) -> Unit = {},
    embeddingTestResult: ConnectionTestResult? = null,
    onDeviceBrainModel: OnDeviceBrainModelUiState = OnDeviceBrainModelUiState(),
    onDownloadOnDeviceBrainModel: () -> Unit = {},
    onDeleteOnDeviceBrainModel: () -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var selectedProvider by remember { mutableStateOf(normalizeProvider(currentConfig.providerType)) }
    var selectedEmbeddingProvider by remember { mutableStateOf(EmbeddingProviderType.fromString(currentConfig.embeddingProviderType)) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(currentConfig.baseUrl) }
    var embeddingApiKey by remember { mutableStateOf(currentConfig.embeddingApiKey) }
    var embeddingBaseUrl by remember { mutableStateOf(currentConfig.embeddingBaseUrl) }
    var chatModel by remember { mutableStateOf(currentConfig.chatModel) }
    var visionModel by remember { mutableStateOf(currentConfig.visionModel) }
    var embeddingModel by remember { mutableStateOf(currentConfig.textEmbeddingModel.ifBlank { currentConfig.embeddingModel }) }
    var multimodalEmbeddingModel by remember { mutableStateOf(currentConfig.multimodalEmbeddingModel) }
    var isEnabled by remember { mutableStateOf(currentConfig.isEnabled) }
    var autoSync by remember { mutableStateOf(currentConfig.autoSync) }
    var freeOnly by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var showProviderPicker by remember { mutableStateOf(false) }
    var showEmbeddingProviderPicker by remember { mutableStateOf(false) }
    var showChatPicker by remember { mutableStateOf(false) }
    var showVisionPicker by remember { mutableStateOf(false) }
    var showEmbeddingPicker by remember { mutableStateOf(false) }
    var showMultimodalEmbeddingPicker by remember { mutableStateOf(false) }
    var showOfflineBrainPicker by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    fun draftConfig(): AiProviderConfigEntity = currentConfig.copy(
        providerType = selectedProvider.name,
        apiKey = apiKey.trim(),
        baseUrl = baseUrl.trim(),
        embeddingProviderType = selectedEmbeddingProvider.name,
        embeddingApiKey = embeddingApiKey.trim(),
        embeddingBaseUrl = embeddingBaseUrl.trim(),
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

    fun selectEmbeddingProvider(provider: EmbeddingProviderType) {
        selectedEmbeddingProvider = provider
        if (EmbeddingProviderType.fromString(currentConfig.embeddingProviderType) == provider) {
            embeddingApiKey = currentConfig.embeddingApiKey
            embeddingBaseUrl = currentConfig.embeddingBaseUrl
        } else {
            embeddingApiKey = ""
            embeddingBaseUrl = provider.defaultBaseUrl
            if (provider == EmbeddingProviderType.OFFLINE) {
                embeddingApiKey = ""
                embeddingBaseUrl = ""
            }
        }
        showEmbeddingProviderPicker = false
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
                            if (isEnabled) "Cloud AI enabled" else "Cloud AI optional",
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
            SectionTitle("LOCAL BRAIN — REQUIRED")
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Enable Brain locally", fontWeight = FontWeight.Bold)
                            Text("The local model powers semantic search, indexing, Topics and Graph.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Brain retrieval is local-first. Download the model below; cloud AI is optional and only supplies generated answers or optional enrichment.", style = MaterialTheme.typography.bodySmall)
                    when (onDeviceBrainModel.status) {
                        OnDeviceBrainModelStatus.NOT_INSTALLED -> {
                            Button(onClick = onDownloadOnDeviceBrainModel, modifier = Modifier.fillMaxWidth().testTag("on_device_brain_download_btn"), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Download local Brain model")
                            }
                        }
                        OnDeviceBrainModelStatus.DOWNLOADING -> {
                            LinearProgressIndicator(progress = { onDeviceBrainModel.progress }, modifier = Modifier.fillMaxWidth())
                            Text("Downloading ${(onDeviceBrainModel.progress * 100).toInt()}%…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        OnDeviceBrainModelStatus.READY -> {
                            Text("Ready — semantic indexing and search are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            OutlinedButton(onClick = onDeleteOnDeviceBrainModel, modifier = Modifier.fillMaxWidth().testTag("on_device_brain_delete_btn"), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Remove local model")
                            }
                        }
                        OnDeviceBrainModelStatus.ERROR -> {
                            Text(onDeviceBrainModel.error ?: "The local model could not be downloaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Button(onClick = onDownloadOnDeviceBrainModel, modifier = Modifier.fillMaxWidth().testTag("on_device_brain_retry_btn"), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Retry download")
                            }
                        }
                    }
                }
            }

            SectionTitle("CLOUD AI — OPTIONAL")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Enable cloud AI answers", fontWeight = FontWeight.Bold)
                        Text("Optional: only prompts, attached files, or context needed for cloud-generated answers are sent to the configured provider.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it }, modifier = Modifier.testTag("ai_enabled_switch"))
                }
            }

            Text("The local Brain model is the canonical semantic-search engine. Cloud AI is an optional answer generator.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                    CompactInfo("Ollama is local-first. Same device → 127.0.0.1:11434. Android Emulator → 10.0.2.2:11434. Another computer on your LAN → use its private IP, e.g. 192.168.1.20:11434.")
                } else {
                    CompactInfo("Server: ${baseUrl.removeSuffix("/")}")
                }

                SectionTitle("EMBEDDING PROVIDER — INDEPENDENT FROM CHAT")

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { showEmbeddingProviderPicker = true },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedEmbeddingProvider.displayName, fontWeight = FontWeight.Bold)
                            Text(selectedEmbeddingProvider.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Change", color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (selectedEmbeddingProvider != EmbeddingProviderType.OFFLINE) {
                    if (selectedEmbeddingProvider != EmbeddingProviderType.OLLAMA) {
                        OutlinedTextField(
                            value = embeddingApiKey,
                            onValueChange = { embeddingApiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Embedding API key") },
                            placeholder = { Text(selectedEmbeddingProvider.keyHint) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                    OutlinedTextField(
                        value = embeddingBaseUrl,
                        onValueChange = { embeddingBaseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Embedding base URL") },
                        placeholder = { Text(selectedEmbeddingProvider.defaultBaseUrl.ifBlank { "https://your-server/v1" }) },
                        singleLine = true
                    )
                    CompactInfo("This provider is used only for Brain embeddings. Chat/vision can use a different provider. Nothing about the LLM choice changes this setting.")
                } else {
                    CompactInfo("Embeddings stay on this device. Chat can still use Gemini, OpenAI-compatible, Ollama, or another provider at the same time.")
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Embedding models", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (selectedEmbeddingProvider == EmbeddingProviderType.OFFLINE) "Use the downloaded local model below."
                            else "Discover models from the embedding provider.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectedEmbeddingProvider != EmbeddingProviderType.OFFLINE) {
                        IconButton(
                            onClick = { onFetchEmbeddingModels(draftConfig()) },
                            enabled = !isFetchingModels
                        ) {
                            if (isFetchingModels) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = "Fetch embedding models")
                        }
                    }
                }

                if (selectedEmbeddingProvider != EmbeddingProviderType.OFFLINE) {
                    ModelPicker("Text embedding / semantic search", firstUsable(embeddings, embeddingModel), { showEmbeddingPicker = true }, "ai_embedding_model_field", embeddings)
                    ModelPicker("Multimodal embedding / image retrieval", firstUsable(multimodalEmbeddings, multimodalEmbeddingModel), { showMultimodalEmbeddingPicker = true }, "ai_multimodal_embedding_model_field", multimodalEmbeddings)
                    Button(
                        onClick = { onTestEmbeddingConnection(draftConfig()) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Test embedding connection")
                    }
                    embeddingTestResult?.let { result ->
                        Surface(
                            color = if (result.success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(if (result.success) "Embedding connected" else "Embedding unavailable", fontWeight = FontWeight.Bold)
                                Text(result.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }
                }

                SectionTitle("OFFLINE EMBEDDING MODEL")
                if (selectedEmbeddingProvider == EmbeddingProviderType.OFFLINE) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable { showOfflineBrainPicker = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("On-device embedding model", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val selectedOffline = offlineBrainModels.firstOrNull { it.id == selectedOfflineBrainModelId }
                                Text(
                                    selectedOffline?.displayName ?: onDeviceBrainModel.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (selectedOffline != null) {
                                    Text(selectedOffline.sizeLabel + " · " + selectedOffline.embeddingDimension + "-D", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text("Choose", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    CompactInfo("Offline models remain available as a separate choice. Switch Embedding Provider to On-device Brain whenever you want fully local embeddings.")
                }

                val cloudReady = isEnabled && (isKeylessAiConfig(draftConfig()) || apiKey.trim().isNotBlank())
                CompactInfo(if (cloudReady) "Cloud AI is configured. It is used only for generated answers and optional enrichment." else "Cloud AI is not configured. You can still use the local Brain for semantic search; generated answers require a configured provider.")

                SectionTitle("PROVIDER MODELS")
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
                modelFetchError?.let { error ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Selected model needs attention", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { onFetchModels(draftConfig()) }, enabled = !isFetchingModels, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (isFetchingModels) "Refreshing models…" else "Refresh available models")
                            }
                        }
                    }
                }

                ModelPicker("General files / chat", firstUsable(chats, chatModel), { showChatPicker = true }, "ai_chat_model_field", chats)
                ModelPicker("Vision / image understanding + caption", firstUsable(visions, visionModel), { showVisionPicker = true }, "ai_vision_model_field", visions)
undefined                SectionTitle("CHECK")
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
                SectionTitle("AI DATA & PRIVACY")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Local Brain", fontWeight = FontWeight.SemiBold)
                        Text("Embeddings, Graph data and Topics stay on this device.", style = MaterialTheme.typography.bodySmall)
                        Text("Cloud providers", fontWeight = FontWeight.SemiBold)
                        Text("When enabled, only data needed for the requested cloud answer or enrichment is sent to the configured provider.", style = MaterialTheme.typography.bodySmall)
                        Text("Ollama", fontWeight = FontWeight.SemiBold)
                        Text("Requests go only to the endpoint you configure: same device, emulator host, or a private LAN server.", style = MaterialTheme.typography.bodySmall)
                        Text("Location & .env files", fontWeight = FontWeight.SemiBold)
                        Text("Precise GPS is omitted from metadata sent to cloud providers. .env files are indexed as text; keep API keys and other secrets out of files Brain can ingest.", style = MaterialTheme.typography.bodySmall)
                    }
                }

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
                         "Privacy: the local model stays on this device. If you enable a cloud provider, only data required for that AI operation is sent to that provider. Ollama/local endpoints can keep processing on your device or LAN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showProviderPicker) ProviderPickerDialog(selectedProvider, ::selectProvider) { showProviderPicker = false }
    if (showEmbeddingProviderPicker) EmbeddingProviderPickerDialog(selectedEmbeddingProvider, ::selectEmbeddingProvider) { showEmbeddingProviderPicker = false }
    if (showChatPicker) ModelPickerDialog("Choose chat model", chats, chatModel, { chatModel = it; showChatPicker = false }) { showChatPicker = false }
    if (showVisionPicker) ModelPickerDialog("Choose vision model", visions, visionModel, { visionModel = it; showVisionPicker = false }) { showVisionPicker = false }
    if (showEmbeddingPicker) ModelPickerDialog("Choose text embedding model", embeddings, embeddingModel, { embeddingModel = it; showEmbeddingPicker = false }) { showEmbeddingPicker = false }
    if (showMultimodalEmbeddingPicker) ModelPickerDialog("Choose multimodal embedding model", multimodalEmbeddings, multimodalEmbeddingModel, { multimodalEmbeddingModel = it; showMultimodalEmbeddingPicker = false }) { showMultimodalEmbeddingPicker = false }
    if (showOfflineBrainPicker) OfflineBrainModelPickerDialog(
        models = offlineBrainModels,
        selectedId = selectedOfflineBrainModelId,
        onSelect = {
            onSelectOfflineBrainModel(it)
            showOfflineBrainPicker = false
        },
        onDismiss = { showOfflineBrainPicker = false }
    )
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
private fun EmbeddingProviderPickerDialog(
    selected: EmbeddingProviderType,
    onSelect: (EmbeddingProviderType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose embedding provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EmbeddingProviderType.entries.forEach { provider ->
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
private fun OfflineBrainModelPickerDialog(
    models: List<OnDeviceBrainModelSpec>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose offline embedding model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                models.forEach { model ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(model.id) },
                        color = if (model.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(11.dp)) {
                            Text(model.displayName, fontWeight = FontWeight.SemiBold)
                            Text(model.sizeLabel + " · " + model.embeddingDimension + "-D · max " + model.maxTokens + " tokens", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
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