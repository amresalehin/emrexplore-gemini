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
import androidx.compose.material.icons.filled.Database
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.ai.EmbeddingProviderType
import com.example.data.ai.ProviderType
import com.example.data.ai.VectorDatabaseType
import com.example.data.brain.OnDeviceBrainModelSpec
import com.example.data.local.AiProviderConfigEntity

private data class SetupChatChoice(val provider: ProviderType, val model: String, val title: String, val subtitle: String, val local: Boolean)
private data class SetupEmbeddingChoice(val provider: EmbeddingProviderType, val model: String, val title: String, val subtitle: String, val local: Boolean)

@Composable
fun BrainSetupScreen(
    currentConfig: AiProviderConfigEntity,
    offlineModels: List<OnDeviceBrainModelSpec>,
    onSelectOfflineModel: (String) -> Unit,
    onSaveConfig: (AiProviderConfigEntity) -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }
    var step by remember { mutableIntStateOf(0) }
    var chatChoice by remember {
        mutableStateOf(
            SetupChatChoice(
                ProviderType.fromString(currentConfig.providerType),
                currentConfig.chatModel.ifBlank { "gemini-3.8-flash" },
                currentConfig.chatModel.ifBlank { "Gemini" },
                ProviderType.fromString(currentConfig.providerType).displayName,
                ProviderType.fromString(currentConfig.providerType) == ProviderType.OLLAMA
            )
        )
    }
    var embeddingChoice by remember {
        mutableStateOf(
            SetupEmbeddingChoice(
                EmbeddingProviderType.fromString(currentConfig.embeddingProviderType),
                currentConfig.textEmbeddingModel.ifBlank { currentConfig.embeddingModel.ifBlank { "all-MiniLM-L6-v2-int8" } },
                currentConfig.textEmbeddingModel.ifBlank { "Embedding model" },
                EmbeddingProviderType.fromString(currentConfig.embeddingProviderType).displayName,
                EmbeddingProviderType.fromString(currentConfig.embeddingProviderType) == EmbeddingProviderType.OFFLINE
            )
        )
    }
    var vectorChoice by remember { mutableStateOf(VectorDatabaseType.fromString(currentConfig.vectorDatabaseType)) }
    var chatCustom by remember { mutableStateOf("") }
    var embeddingCustom by remember { mutableStateOf("") }
    var vectorUrl by remember { mutableStateOf(currentConfig.vectorDatabaseBaseUrl) }
    var vectorKey by remember { mutableStateOf(currentConfig.vectorDatabaseApiKey) }
    var collection by remember { mutableStateOf(currentConfig.vectorDatabaseCollection.ifBlank { "emrexplore_brain" }) }
    var search by remember { mutableStateOf("") }

    val chatChoices = listOf(
        SetupChatChoice(ProviderType.GEMINI, "gemini-3.8-flash", "Gemini 3.8 Flash", "Cloud · Google", false),
        SetupChatChoice(ProviderType.OPENAI_COMPATIBLE, "gpt-5", "GPT-5", "Cloud · OpenAI-compatible", false),
        SetupChatChoice(ProviderType.OPENROUTER, "openai/gpt-5", "OpenRouter model", "Cloud · OpenRouter", false),
        SetupChatChoice(ProviderType.GROQ, "llama-3.3-70b-versatile", "Llama 3.3 70B", "Cloud · Groq", false),
        SetupChatChoice(ProviderType.OLLAMA, "qwen3:8b", "Qwen 3 8B", "Local · Ollama", true),
        SetupChatChoice(ProviderType.OLLAMA, "llama3.2:latest", "Llama 3.2", "Local · Ollama", true)
    ).filter { search.isBlank() || it.title.contains(search, true) || it.subtitle.contains(search, true) }

    val embeddingChoices = buildList {
        offlineModels.forEach { spec ->
            add(SetupEmbeddingChoice(EmbeddingProviderType.OFFLINE, spec.id, spec.displayName, "Local · On-device · ${spec.sizeLabel}", true))
        }
        add(SetupEmbeddingChoice(EmbeddingProviderType.OLLAMA, "nomic-embed-text:latest", "Nomic Embed Text", "Local · Ollama", true))
        add(SetupEmbeddingChoice(EmbeddingProviderType.OPENAI_COMPATIBLE, "text-embedding-3-small", "text-embedding-3-small", "Cloud · OpenAI", false))
        add(SetupEmbeddingChoice(EmbeddingProviderType.GEMINI, "gemini-embedding-2", "Gemini Embedding 2", "Cloud · Google", false))
        add(SetupEmbeddingChoice(EmbeddingProviderType.OPENROUTER, "openai/text-embedding-3-small", "OpenRouter embedding", "Cloud · OpenRouter", false))
    }

    fun finish() {
        val chatProvider = chatChoice.provider
        val embeddingProvider = embeddingChoice.provider
        val selectedChatModel = chatCustom.trim().ifBlank { chatChoice.model }
        val selectedEmbeddingModel = embeddingCustom.trim().ifBlank { embeddingChoice.model }
        val config = currentConfig.copy(
            providerType = chatProvider.name,
            baseUrl = chatProvider.defaultBaseUrl,
            chatModel = selectedChatModel,
            embeddingProviderType = embeddingProvider.name,
            embeddingBaseUrl = embeddingProvider.defaultBaseUrl,
            embeddingModel = selectedEmbeddingModel,
            textEmbeddingModel = selectedEmbeddingModel,
            vectorDatabaseType = vectorChoice.name,
            vectorDatabaseBaseUrl = vectorUrl.trim(),
            vectorDatabaseApiKey = vectorKey.trim(),
            vectorDatabaseCollection = collection.trim().ifBlank { "emrexplore_brain" },
            brainSetupCompleted = true,
            isEnabled = true
        )
        if (embeddingProvider == EmbeddingProviderType.OFFLINE) onSelectOfflineModel(selectedEmbeddingModel)
        onSaveConfig(config)
        onNavigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Set up Brain", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = onOpenAdvancedSettings) { Text("Advanced") } }
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
                    listOf("Chat", "Embedding", "Storage").forEachIndexed { index, label ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = if (step == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                            Text("${index + 1}. $label", Modifier.padding(vertical = 9.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, fontWeight = if (step == index) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            when (step) {
                0 -> {
                    Column(Modifier.fillMaxSize()) {
                        SetupHeading("Chat model", "Choose the model that answers your questions. Local and cloud models are shown together.")
                        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search models") })
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(chatChoices) { choice ->
                                SetupChoiceCard(choice.title, choice.subtitle, choice.provider == chatChoice.provider && choice.model == chatChoice.model, if (choice.local) Icons.Default.PhoneAndroid else Icons.Default.Cloud) { chatChoice = choice; chatCustom = "" }
                            }
                            item {
                                OutlinedTextField(value = chatCustom, onValueChange = { chatCustom = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Use another model ID") }, placeholder = { Text("Any model supported by the selected endpoint") }, singleLine = true)
                            }
                        }
                        SetupButtons(onBack = {}, onNext = { step = 1 }, backEnabled = false)
                    }
                }
                1 -> {
                    Column(Modifier.fillMaxSize()) {
                        SetupHeading("Embedding model", "This model turns your files into vectors. It is completely independent from Chat.")
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(embeddingChoices) { choice ->
                                SetupChoiceCard(choice.title, choice.subtitle, choice.provider == embeddingChoice.provider && choice.model == embeddingChoice.model, if (choice.local) Icons.Default.PhoneAndroid else Icons.Default.Cloud) { embeddingChoice = choice; embeddingCustom = "" }
                            }
                            item {
                                OutlinedTextField(value = embeddingCustom, onValueChange = { embeddingCustom = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Use another embedding model ID") }, placeholder = { Text("Any embedding model supported by the selected endpoint") }, singleLine = true)
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
                                SetupChoiceCard("Qdrant", "Remote or self-hosted vector database", vectorChoice == VectorDatabaseType.QDRANT, Icons.Default.Database) { vectorChoice = VectorDatabaseType.QDRANT }
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
