package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ai.AvailableAiModel
import com.example.data.ai.EmbeddingProviderType
import com.example.data.ai.ProviderType
import com.example.data.brain.OnDeviceBrainModelSpec
import com.example.data.local.AiProviderConfigEntity

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
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    var provider by remember { mutableStateOf(ProviderType.fromString(currentConfig.providerType)) }
    var apiKey by rememberSaveable { mutableStateOf(currentConfig.apiKey) }
    var endpoint by rememberSaveable { mutableStateOf(currentConfig.baseUrl.ifBlank { provider.defaultBaseUrl }) }
    var chatModel by rememberSaveable { mutableStateOf(currentConfig.chatModel) }
    var visionModel by rememberSaveable { mutableStateOf(currentConfig.visionModel) }
    var embeddingProvider by rememberSaveable { mutableStateOf(EmbeddingProviderType.fromString(currentConfig.embeddingProviderType)) }
    var embeddingApiKey by rememberSaveable { mutableStateOf(currentConfig.embeddingApiKey) }
    var embeddingEndpoint by rememberSaveable { mutableStateOf(currentConfig.embeddingBaseUrl) }
    var textEmbeddingModel by rememberSaveable { mutableStateOf(currentConfig.textEmbeddingModel.ifBlank { currentConfig.embeddingModel }) }
    var imageEmbeddingModel by rememberSaveable { mutableStateOf(currentConfig.multimodalEmbeddingModel) }
    var selectedEmbeddingModel by rememberSaveable {
        mutableStateOf(
            offlineModels.firstOrNull { it.id == currentConfig.textEmbeddingModel }?.id
                ?: offlineModels.firstOrNull()?.id.orEmpty()
        )
    }
    var showFreeOnly by rememberSaveable { mutableStateOf(false) }

    val chatModels = availableModels.filter { it.supportsChat && (!showFreeOnly || it.isFree) }
    val visionModels = (if (availableVisionModels.isNotEmpty()) availableVisionModels else availableModels)
        .filter { it.supportsVision && (!showFreeOnly || it.isFree) }

    fun draft(): AiProviderConfigEntity = currentConfig.copy(
        providerType = provider.name,
        apiKey = apiKey.trim(),
        baseUrl = endpoint.trim().ifBlank { provider.defaultBaseUrl },
        chatModel = chatModel.trim(),
        visionModel = visionModel.trim(),
        embeddingProviderType = embeddingProvider.name,
        embeddingApiKey = if (embeddingProvider == EmbeddingProviderType.OFFLINE) "" else embeddingApiKey.trim(),
        embeddingBaseUrl = if (embeddingProvider == EmbeddingProviderType.OFFLINE) "" else embeddingEndpoint.trim(),
        embeddingModel = if (embeddingProvider == EmbeddingProviderType.OFFLINE) selectedEmbeddingModel else textEmbeddingModel.trim(),
        textEmbeddingModel = if (embeddingProvider == EmbeddingProviderType.OFFLINE) selectedEmbeddingModel else textEmbeddingModel.trim(),
        multimodalEmbeddingModel = if (embeddingProvider == EmbeddingProviderType.OFFLINE) "" else imageEmbeddingModel.trim(),
        brainSetupCompleted = true
    )

    LaunchedEffect(provider) {
        if (endpoint.isBlank()) endpoint = provider.defaultBaseUrl
        if (endpoint.isNotBlank()) onFetchModels(draft())
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("AI setup", fontWeight = FontWeight.Bold)
                    Text("LLM + Gallery VLM + configurable embeddings", style = MaterialTheme.typography.labelSmall)
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.Settings, contentDescription = "Back")
                }
            },
            actions = {
                Button(
                    onClick = { onSaveConfig(draft()); onNavigateBack() },
                    enabled = chatModel.isNotBlank() && selectedEmbeddingModel.isNotBlank(),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Save")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Three separate jobs", fontWeight = FontWeight.Bold)
                        Text("LLM answers Brain questions. Gallery AI uses the VLM to create and save captions/tags. Offline embeddings run last and are stored with each file.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item { SectionHeading("1. LLM / answer provider", Icons.Default.AutoAwesome) }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Provider", fontWeight = FontWeight.SemiBold)
                        ProviderType.entries.forEach { candidate ->
                            SetupChoice(
                                title = candidate.displayName,
                                subtitle = when (candidate) {
                                    ProviderType.OLLAMA -> "Local / LAN endpoint. Install the model on that server yourself."
                                    ProviderType.GEMINI -> "Google Gemini API."
                                    ProviderType.CUSTOM -> "Any compatible OpenAI-style endpoint."
                                    else -> candidate.description
                                },
                                selected = provider == candidate,
                                icon = if (candidate == ProviderType.OLLAMA) Icons.Default.PhoneAndroid else Icons.Default.Cloud,
                                onClick = {
                                    provider = candidate
                                    endpoint = candidate.defaultBaseUrl
                                    if (candidate == ProviderType.OLLAMA) apiKey = ""
                                    chatModel = ""
                                    visionModel = ""
                                }
                            )
                        }
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API endpoint") },
                            singleLine = true
                        )
                        if (provider != ProviderType.OLLAMA) {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("API key") },
                                singleLine = true
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Model catalog", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            OutlinedButton(
                                onClick = { onFetchModels(draft()) },
                                enabled = !isFetchingModels && endpoint.isNotBlank()
                            ) {
                                if (isFetchingModels) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.size(5.dp))
                                Text("Fetch")
                            }
                        }
                        if (chatModels.isNotEmpty()) {
                            Text(chatModels.size.toString() + " chat-capable models found", style = MaterialTheme.typography.labelSmall)
                            chatModels.take(30).forEach { model ->
                                SetupModel(model, model.id == chatModel) { chatModel = model.id }
                            }
                        }
                        OutlinedTextField(
                            value = chatModel,
                            onValueChange = { chatModel = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("LLM model ID") },
                            singleLine = true
                        )
                        modelFetchError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item { SectionHeading("2. Gallery AI VLM", Icons.Default.AutoAwesome) }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Used only by Gallery AI to understand images.", style = MaterialTheme.typography.bodySmall)
                        if (visionModels.isNotEmpty()) {
                            Text(visionModels.size.toString() + " vision-capable models found", style = MaterialTheme.typography.labelSmall)
                            visionModels.take(30).forEach { model ->
                                SetupModel(model, model.id == visionModel) { visionModel = model.id }
                            }
                        }
                        OutlinedTextField(
                            value = visionModel,
                            onValueChange = { visionModel = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("VLM model ID") },
                            singleLine = true
                        )
                        Text("For Ollama, enter a vision-capable model already available on the configured server. There is no in-app LLM/VLM model download.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { SectionHeading("3. Embedding engine", Icons.Default.Download) }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Embeddings belong to File AI and Gallery AI — Brain only consumes them.", fontWeight = FontWeight.SemiBold)
                        EmbeddingProviderType.entries.forEach { candidate ->
                            SetupChoice(
                                title = candidate.displayName,
                                subtitle = candidate.description,
                                selected = embeddingProvider == candidate,
                                icon = if (candidate == EmbeddingProviderType.OFFLINE) Icons.Default.PhoneAndroid else Icons.Default.Cloud,
                                onClick = {
                                    embeddingProvider = candidate
                                    if (candidate == EmbeddingProviderType.OFFLINE) {
                                        embeddingEndpoint = ""
                                        embeddingApiKey = ""
                                    } else {
                                        embeddingEndpoint = candidate.defaultBaseUrl
                                        textEmbeddingModel = candidate.defaultModel
                                        imageEmbeddingModel = candidate.defaultMultimodalEmbeddingModel
                                        if (candidate == EmbeddingProviderType.OLLAMA) embeddingApiKey = ""
                                    }
                                }
                            )
                        }
                        if (embeddingProvider != EmbeddingProviderType.OFFLINE) {
                            if (embeddingProvider != EmbeddingProviderType.OLLAMA) {
                                OutlinedTextField(
                                    value = embeddingApiKey,
                                    onValueChange = { embeddingApiKey = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Embedding API key (optional for local endpoints)") },
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = embeddingEndpoint,
                                onValueChange = { embeddingEndpoint = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Embedding endpoint") },
                                singleLine = true
                            )
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Fetch embedding models", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                OutlinedButton(
                                    onClick = { onFetchEmbeddingModels(draft()) },
                                    enabled = !isFetchingModels
                                ) {
                                    if (isFetchingModels) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.size(5.dp))
                                    Text("Fetch")
                                }
                            }
                            val remoteEmbeddings = availableEmbeddingModels.filter { !showFreeOnly || it.isFree }
                            val remoteImageEmbeddings = availableMultimodalEmbeddingModels.filter { it.supportsMultimodalEmbedding && (!showFreeOnly || it.isFree) }
                            remoteEmbeddings.take(30).forEach { model ->
                                SetupModel(model, model.id == textEmbeddingModel) { textEmbeddingModel = model.id }
                            }
                            OutlinedTextField(
                                value = textEmbeddingModel,
                                onValueChange = { textEmbeddingModel = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Text embedding model ID") },
                                singleLine = true
                            )
                            remoteImageEmbeddings.take(30).forEach { model ->
                                SetupModel(model, model.id == imageEmbeddingModel) { imageEmbeddingModel = model.id }
                            }
                            OutlinedTextField(
                                value = imageEmbeddingModel,
                                onValueChange = { imageEmbeddingModel = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Image embedding model ID — Gallery AI only") },
                                singleLine = true
                            )
                            Text("The image embedding model is called by Gallery AI. Brain never generates image embeddings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { SectionHeading("4. Offline text embedding", Icons.Default.Download) }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("On-device text embedding is one available embedding engine.", fontWeight = FontWeight.SemiBold)
                        Text("Download/select an on-device text model when you want private local text embeddings. File AI and Gallery AI always run enrichment first and embedding last.", style = MaterialTheme.typography.bodySmall)
                        offlineModels.forEach { spec ->
                            SetupChoice(
                                title = spec.displayName,
                                subtitle = spec.sizeLabel + " · " + spec.embeddingDimension + "-D",
                                selected = spec.id == selectedEmbeddingModel,
                                icon = Icons.Default.PhoneAndroid,
                                onClick = {
                                    selectedEmbeddingModel = spec.id
                                    onSelectOfflineModel(spec.id)
                                }
                            )
                        }
                        if (offlineModels.isEmpty()) {
                            Text("No offline embedding model is available.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show free models only", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = showFreeOnly, onCheckedChange = { showFreeOnly = it })
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("What happens after setup", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("• Documents: File AI → save summary/tags/entities → text embedding → Brain consumes it.", style = MaterialTheme.typography.bodySmall)
                        Text("• Images: Gallery AI VLM → image embedding → save caption/tags/entities/vectors → Brain consumes the saved data.", style = MaterialTheme.typography.bodySmall)
                        Text("• Changing the LLM or VLM does not re-embed existing files.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SetupChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SetupModel(model: AvailableAiModel, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (model.supportsVision) Icons.Default.AutoAwesome else Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(model.displayName.ifBlank { model.id }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (model.priceKnown) Text(if (model.isFree) "FREE" else "Paid / metered", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
