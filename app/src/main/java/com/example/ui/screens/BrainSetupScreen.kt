package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.ai.AvailableAiModel
import com.example.data.ai.EmbeddingProviderType
import com.example.data.ai.ProviderType
import com.example.data.ai.VectorDatabaseType
import com.example.data.brain.OnDeviceBrainModelSpec
import com.example.data.local.AiProviderConfigEntity

private data class SetupChatChoice(val provider: ProviderType, val model: String, val title: String, val subtitle: String, val local: Boolean)
private data class SetupEmbeddingChoice(val provider: EmbeddingProviderType, val model: String, val title: String, val subtitle: String, val local: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainSetupScreen(
    currentConfig: AiProviderConfigEntity,
    offlineModels: List<OnDeviceBrainModelSpec>,
    onSelectOfflineModel: (String) -> Unit,
    availableModels: List<AvailableAiModel> = emptyList(),
    availableVisionModels: List<AvailableAiModel> = emptyList(),
    availableEmbeddingModels: List<AvailableAiModel> = emptyList(),
    availableMultimodalEmbeddingModels: List<AvailableAiModel> = emptyList(),
    isFetchingModels: Boolean = false,
    modelFetchError: String? = null,
    onFetchModels: (AiProviderConfigEntity) -> Unit = {},
    onFetchEmbeddingModels: (AiProviderConfigEntity) -> Unit = {},
    onDownloadOllamaModel: (AiProviderConfigEntity, String) -> Unit = { _, _ -> },
    isDownloadingOllamaModel: Boolean = false,
    ollamaDownloadProgress: Float = 0f,
    ollamaDownloadStatus: String = "",
    onSaveConfig: (AiProviderConfigEntity) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }
    var step by remember { mutableIntStateOf(0) }
    var selectedProvider by remember { mutableStateOf(ProviderType.fromString(currentConfig.providerType)) }
    var selectedEmbeddingProvider by remember { mutableStateOf(EmbeddingProviderType.fromString(currentConfig.embeddingProviderType)) }
    var endpoint by remember { mutableStateOf(currentConfig.baseUrl) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var embeddingEndpoint by remember { mutableStateOf(currentConfig.embeddingBaseUrl) }
    var embeddingApiKey by remember { mutableStateOf(currentConfig.embeddingApiKey) }
    var chatModel by remember { mutableStateOf(currentConfig.chatModel) }
    var visionModel by remember { mutableStateOf(currentConfig.visionModel) }
    var embeddingModel by remember { mutableStateOf(currentConfig.textEmbeddingModel.ifBlank { currentConfig.embeddingModel }) }
    var imageEmbeddingModel by remember { mutableStateOf(currentConfig.multimodalEmbeddingModel) }
    var vectorChoice by remember { mutableStateOf(VectorDatabaseType.fromString(currentConfig.vectorDatabaseType)) }
    var vectorUrl by remember { mutableStateOf(currentConfig.vectorDatabaseBaseUrl) }
    var vectorKey by remember { mutableStateOf(currentConfig.vectorDatabaseApiKey) }
    var collection by remember { mutableStateOf(currentConfig.vectorDatabaseCollection.ifBlank { "emrexplore_brain" }) }
    var search by remember { mutableStateOf("") }
    var embeddingSearch by remember { mutableStateOf("") }
    var ollamaDownloadModel by remember { mutableStateOf("") }

    val selectedChatProvider = selectedProvider
    val selectedEmbedding = selectedEmbeddingProvider
    val filteredChatModels = availableModels.filter { search.isBlank() || it.id.contains(search, true) || it.displayName.contains(search, true) }
    val filteredVisionModels = availableVisionModels.filter { search.isBlank() || it.id.contains(search, true) || it.displayName.contains(search, true) }
    val filteredEmbeddingModels = availableEmbeddingModels.filter { embeddingSearch.isBlank() || it.id.contains(embeddingSearch, true) || it.displayName.contains(embeddingSearch, true) }
    val filteredMultimodalEmbeddingModels = availableMultimodalEmbeddingModels.filter { embeddingSearch.isBlank() || it.id.contains(embeddingSearch, true) || it.displayName.contains(embeddingSearch, true) }

    LaunchedEffect(selectedChatProvider) {
        if (selectedChatProvider == ProviderType.OLLAMA || apiKey.isNotBlank()) {
            onFetchModels(chatDraft())
        }
    }

    LaunchedEffect(selectedEmbedding) {
        if (selectedEmbedding != EmbeddingProviderType.OFFLINE &&
            (selectedEmbedding == EmbeddingProviderType.OLLAMA || embeddingApiKey.isNotBlank())
        ) {
            onFetchEmbeddingModels(embeddingDraft())
        }
    }

    fun chatDraft(): AiProviderConfigEntity = currentConfig.copy(
        providerType = selectedChatProvider.name,
        baseUrl = endpoint.trim(),
        apiKey = apiKey.trim(),
        chatModel = chatModel.trim(),
        visionModel = visionModel.trim()
    )

    fun embeddingDraft(): AiProviderConfigEntity = currentConfig.copy(
        providerType = selectedChatProvider.name,
        baseUrl = endpoint.trim(),
        apiKey = apiKey.trim(),
        embeddingProviderType = selectedEmbedding.name,
        embeddingBaseUrl = embeddingEndpoint.trim(),
        embeddingApiKey = embeddingApiKey.trim(),
        textEmbeddingModel = embeddingModel.trim(),
        embeddingModel = embeddingModel.trim(),
        multimodalEmbeddingModel = imageEmbeddingModel.trim()
    )

    fun finish() {
        val selectedChatModel = chatModel.trim()
        val selectedEmbeddingModel = embeddingModel.trim()
        if (selectedChatModel.isBlank()) return
        val config = currentConfig.copy(
            providerType = selectedChatProvider.name,
            baseUrl = endpoint.trim().ifBlank { selectedChatProvider.defaultBaseUrl },
            apiKey = apiKey.trim(),
            chatModel = selectedChatModel,
            visionModel = visionModel.trim(),
            multimodalEmbeddingModel = imageEmbeddingModel.trim(),
            embeddingProviderType = selectedEmbedding.name,
            embeddingBaseUrl = if (selectedEmbedding == EmbeddingProviderType.OFFLINE) "" else embeddingEndpoint.trim().ifBlank { selectedEmbedding.defaultBaseUrl },
            embeddingApiKey = embeddingApiKey.trim(),
            embeddingModel = selectedEmbeddingModel,
            textEmbeddingModel = selectedEmbeddingModel,
            vectorDatabaseType = vectorChoice.name,
            vectorDatabaseBaseUrl = vectorUrl.trim(),
            vectorDatabaseApiKey = vectorKey.trim(),
            vectorDatabaseCollection = collection.trim().ifBlank { "emrexplore_brain" },
            brainSetupCompleted = true,
            isEnabled = true
        )
        if (selectedEmbedding == EmbeddingProviderType.OFFLINE && selectedEmbeddingModel.isNotBlank()) {
            onSelectOfflineModel(selectedEmbeddingModel)
        }
        onSaveConfig(config)
        onNavigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Set up Brain", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Choose how Brain works", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Chat, embeddings, and vector storage are independent. Mix local and cloud choices freely.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("AI roles", "Text embedding", "Image embedding", "Storage").forEachIndexed { index, label ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = if (step == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                            Text("${index + 1}. $label", Modifier.padding(vertical = 9.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, fontWeight = if (step == index) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            when (step) {
                0 -> {
                    Column(Modifier.fillMaxSize()) {
                        SetupHeading("Chat & vision models", "Choose the provider and endpoint first. Then fetch the live model catalog from that endpoint.")
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                Text("Provider", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                ProviderType.entries.forEach { provider ->
                                    SetupChoiceCard(provider.displayName, provider.description, provider == selectedChatProvider, if (provider == ProviderType.OLLAMA) Icons.Default.PhoneAndroid else Icons.Default.Cloud) {
                                        selectedProvider = provider
                                        endpoint = provider.defaultBaseUrl
                                        if (provider == ProviderType.OLLAMA) apiKey = ""
                                        chatModel = ""
                                        visionModel = ""
                                        search = ""
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                            item {
                                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API endpoint") }, placeholder = { Text("https://your-server.example.com/v1") }, singleLine = true)
                            }
                            item {
                                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API key") }, placeholder = { Text(selectedChatProvider.keyHint) }, singleLine = true)
                            }
                            item {
                                Button(onClick = { onFetchModels(chatDraft()) }, enabled = !isFetchingModels && endpoint.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                    if (isFetchingModels) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isFetchingModels) "Fetching all models…" else "Fetch all models from endpoint")
                                }
                            }
                            modelFetchError?.let { error ->
                                item {
                                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("Model discovery failed", fontWeight = FontWeight.Bold)
                                            Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
                                        }
                                    }
                                }
                            }
                            if (selectedChatProvider == ProviderType.OLLAMA) {
                                item {
                                    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Download an LLM or VLM", fontWeight = FontWeight.SemiBold)
                                            Text("Downloads the model into the connected Ollama server. Enter any Ollama model ID.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            OutlinedTextField(value = ollamaDownloadModel, onValueChange = { ollamaDownloadModel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ollama model ID") }, placeholder = { Text("gemma3:latest") }, singleLine = true)
                                            Button(onClick = { onDownloadOllamaModel(chatDraft(), ollamaDownloadModel) }, enabled = !isDownloadingOllamaModel && ollamaDownloadModel.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                                if (isDownloadingOllamaModel) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                                else Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(if (isDownloadingOllamaModel) "Downloading…" else "Download model")
                                            }
                                            if (isDownloadingOllamaModel || ollamaDownloadProgress > 0f) {
                                                LinearProgressIndicator(progress = { ollamaDownloadProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                            }
                                            if (ollamaDownloadStatus.isNotBlank()) Text(ollamaDownloadStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            item {
                                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search fetched models") })
                            }
                            item {
                                Text(
                                    if (filteredChatModels.isEmpty()) "No live chat models fetched yet. Fetch the endpoint above, or enter a model ID manually below."
                                    else filteredChatModels.size.toString() + " chat-capable models available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(filteredChatModels, key = { "chat:" + it.id }) { model ->
                                SetupModelCard(model, model.id == chatModel) { chatModel = model.id }
                            }
                            item {
                                OutlinedTextField(value = chatModel, onValueChange = { chatModel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Chat model ID") }, placeholder = { Text("Pick one above or enter any supported model ID") }, singleLine = true)
                            }
                            item { Text("Vision model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            items(filteredVisionModels, key = { "vision:" + it.id }) { model ->
                                SetupModelCard(model, model.id == visionModel) { visionModel = model.id }
                            }
                            item {
                                OutlinedTextField(value = visionModel, onValueChange = { visionModel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Vision model ID (optional)") }, placeholder = { Text("Leave blank if your chat model also accepts images") }, singleLine = true)
                            }
                        }
                        SetupButtons(onBack = {}, onNext = { step = 1 }, backEnabled = false)
                    }
                }
                1 -> {
                    Column(Modifier.fillMaxSize()) {
                        SetupHeading("Text & image embeddings", "Embedding is independent from Chat. Use on-device, Ollama, or any compatible endpoint.")
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                Text("Embedding provider", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                EmbeddingProviderType.entries.forEach { provider ->
                                    SetupChoiceCard(provider.displayName, provider.description, provider == selectedEmbedding, if (provider == EmbeddingProviderType.OFFLINE) Icons.Default.PhoneAndroid else Icons.Default.Cloud) {
                                        selectedEmbeddingProvider = provider
                                        embeddingEndpoint = if (provider == EmbeddingProviderType.OFFLINE) "" else provider.defaultBaseUrl
                                        if (provider == EmbeddingProviderType.OFFLINE) embeddingApiKey = ""
                                        embeddingModel = if (provider == EmbeddingProviderType.OFFLINE) offlineModels.firstOrNull()?.id.orEmpty() else provider.defaultModel
                                        imageEmbeddingModel = provider.defaultMultimodalEmbeddingModel
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                            if (selectedEmbedding != EmbeddingProviderType.OFFLINE) {
                                item { OutlinedTextField(value = embeddingEndpoint, onValueChange = { embeddingEndpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Embedding API endpoint") }, singleLine = true) }
                                item { OutlinedTextField(value = embeddingApiKey, onValueChange = { embeddingApiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Embedding API key") }, singleLine = true) }
                                item {
                                    Button(onClick = { onFetchEmbeddingModels(embeddingDraft()) }, enabled = !isFetchingModels && embeddingEndpoint.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                        if (isFetchingModels) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        else Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Fetch embedding models")
                                    }
                                }
                                modelFetchError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
                                item { OutlinedTextField(value = embeddingSearch, onValueChange = { embeddingSearch = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search embedding models") }) }
                                items(filteredEmbeddingModels, key = { "embed:" + it.id }) { model -> SetupModelCard(model, model.id == embeddingModel) { embeddingModel = model.id } }
                                items(filteredMultimodalEmbeddingModels, key = { "mmembed:" + it.id }) { model -> SetupModelCard(model, model.id == imageEmbeddingModel) { imageEmbeddingModel = model.id } }
                            } else {
                                offlineModels.forEach { spec ->
                                    SetupChoiceCard(spec.displayName, spec.sizeLabel + " · " + spec.embeddingDimension + "-D", spec.id == embeddingModel, Icons.Default.PhoneAndroid) {
                                        embeddingModel = spec.id
                                        onSelectOfflineModel(spec.id)
                                    }
                                }
                            }
                            item {
                                OutlinedTextField(value = embeddingModel, onValueChange = { embeddingModel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Text embedding model ID") }, placeholder = { Text("Pick a fetched model or enter a model ID") }, singleLine = true)
                            }
                            item {
                                OutlinedTextField(value = imageEmbeddingModel, onValueChange = { imageEmbeddingModel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Image/multimodal embedding model ID (optional)") }, singleLine = true)
                            }
                        }
                        SetupButtons(onBack = { step = 0 }, onNext = { step = 2 })
                    }
                }
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        SetupHeading("Vector database", "This is where Brain stores and searches the vectors produced by your embedding model.")
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                SetupChoiceCard("Room — Local", "Built into the app · vectors stay on this device", vectorChoice == VectorDatabaseType.ROOM, Icons.Default.PhoneAndroid) { vectorChoice = VectorDatabaseType.ROOM }
                            }
                            item {
                                SetupChoiceCard("Qdrant", "Remote or self-hosted vector database", vectorChoice == VectorDatabaseType.QDRANT, Icons.Default.Storage) { vectorChoice = VectorDatabaseType.QDRANT }
                            }
                            if (vectorChoice == VectorDatabaseType.QDRANT) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(value = vectorUrl, onValueChange = { vectorUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Qdrant URL") }, placeholder = { Text("https://your-qdrant.example.com") }, singleLine = true)
                                        OutlinedTextField(value = vectorKey, onValueChange = { vectorKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API key (optional)") }, singleLine = true)
                                        OutlinedTextField(value = collection, onValueChange = { collection = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Collection") }, singleLine = true)
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(8.dp))
                                Text("Your setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Chat · ${chatCustom.ifBlank { chatChoice.model }}", style = MaterialTheme.typography.bodyMedium)
                                Text("Embedding · ${embeddingCustom.ifBlank { embeddingChoice.model }}", style = MaterialTheme.typography.bodyMedium)
                                Text("Vector DB · ${vectorChoice.displayName}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        SetupButtons(onBack = { step = 1 }, onNext = ::finish, nextLabel = "Finish setup")
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupModelCard(model: AvailableAiModel, selected: Boolean, onClick: () -> Unit) {
    val badges = buildList {
        if (model.supportsChat) add("Chat")
        if (model.supportsVision) add("Vision")
        if (model.supportsEmbedding) add("Embed")
        if (model.supportsMultimodalEmbedding) add("Image embed")
        if (model.supportsTools) add("Tools")
        if (model.isFree) add("Free")
    }
    SetupChoiceCard(
        model.displayName.ifBlank { model.id },
        buildString {
            append(model.id)
            model.contextWindow?.let { append(" · ").append(it / 1000).append("K ctx") }
            if (badges.isNotEmpty()) append(" · ").append(badges.joinToString(" · "))
            append(" · ").append(model.availabilityMessage)
        },
        selected,
        if (model.supportsVision) Icons.Default.Cloud else Icons.Default.Check
    ) { onClick() }
}

@Composable
private fun SetupHeading(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetupChoiceCard(title: String, subtitle: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SetupButtons(backEnabled: Boolean = true, onBack: () -> Unit, onNext: () -> Unit, nextLabel: String = "Continue") {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (backEnabled) TextButton(onClick = onBack) { Text("Back") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onNext) { Text(nextLabel) }
    }
}
