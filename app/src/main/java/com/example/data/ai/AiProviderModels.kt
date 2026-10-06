package com.example.data.ai


enum class ProviderType(
    val displayName: String,
    val description: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val defaultVisionModel: String,
    val defaultTextEmbeddingModel: String,
    val defaultMultimodalEmbeddingModel: String,
    val keyHint: String
) {
    GEMINI(
        displayName = "Google Gemini",
        description = "Direct REST with Gemini 3.8 Flash and native multimodal vision",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/",
        defaultModel = "gemini-3.8-flash",
        defaultVisionModel = "gemini-3.8-flash",
        defaultTextEmbeddingModel = "gemini-embedding-2",
        defaultMultimodalEmbeddingModel = "",
        keyHint = "AIzaSy..."
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI-compatible",
        description = "NVIDIA NIM, OpenAI, Groq, or any compatible endpoint",
        defaultBaseUrl = "https://integrate.api.nvidia.com/v1",
        defaultModel = "nvidia/nemotron-3-super-120b",
        defaultVisionModel = "meta/llama-3.2-11b-vision-instruct",
        defaultTextEmbeddingModel = "nvidia/nv-embedqa-e5-v5",
        defaultMultimodalEmbeddingModel = "nvidia/llama-nemotron-embed-vl-1b-v2",
        keyHint = "API key"
    ),
    OLLAMA(
        displayName = "Ollama (Local AI)",
        description = "Completely private on-device or local LAN network, zero cloud leaks",
        defaultBaseUrl = "http://10.0.2.2:11434/v1/",
        defaultModel = "llama3.2:latest",
        defaultVisionModel = "llama3.2-vision:latest",
        defaultTextEmbeddingModel = "nomic-embed-text:latest",
        defaultMultimodalEmbeddingModel = "",
        keyHint = "Optional (not required for local Ollama)"
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        description = "Universal router to Claude 3.5, Llama 3, DeepSeek, and Gemini",
        defaultBaseUrl = "https://openrouter.ai/api/v1/",
        defaultModel = "meta-llama/llama-3.2-11b-vision-instruct",
        defaultVisionModel = "meta-llama/llama-3.2-11b-vision-instruct",
        defaultTextEmbeddingModel = "openai/text-embedding-3-small",
        defaultMultimodalEmbeddingModel = "",
        keyHint = "sk-or-v1-..."
    ),
    CUSTOM(
        displayName = "Custom OpenAI-Compatible",
        description = "Bring your own base URL, API key, and model IDs for an OpenAI-compatible endpoint",
        defaultBaseUrl = "",
        defaultModel = "",
        defaultVisionModel = "",
        defaultTextEmbeddingModel = "",
        defaultMultimodalEmbeddingModel = "",
        keyHint = "Optional for local endpoints"
    ),
    GROQ(
        displayName = "Groq Cloud",
        description = "Ultra-fast LPUs for instant document extraction & indexing",
        defaultBaseUrl = "https://api.groq.com/openai/v1/",
        defaultModel = "qwen/qwen3.8-27b",
        defaultVisionModel = "qwen/qwen3.8-27b",
        defaultTextEmbeddingModel = "",
        defaultMultimodalEmbeddingModel = "",
        keyHint = "gsk_..."
    );

    companion object {
        fun fromString(value: String): ProviderType {
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: GEMINI
        }
    }
}

enum class EmbeddingProviderType(
    val displayName: String,
    val description: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val keyHint: String
) {
    OFFLINE(
        "On-device Brain",
        "Private local MiniLM embeddings. Nothing leaves the device.",
        "",
        "all-MiniLM-L6-v2-int8",
        "No key required"
    ),
    OLLAMA(
        "Ollama",
        "Local or LAN embeddings such as nomic-embed-text.",
        "http://10.0.2.2:11434/v1/",
        "nomic-embed-text:latest",
        "Optional"
    ),
    OPENAI_COMPATIBLE(
        "OpenAI-compatible",
        "OpenAI, NVIDIA NIM, LocalAI, or another compatible embedding endpoint.",
        "https://api.openai.com/v1",
        "text-embedding-3-small",
        "API key"
    ),
    GEMINI(
        "Google Gemini",
        "Google's hosted embedding API.",
        "https://generativelanguage.googleapis.com/",
        "gemini-embedding-2",
        "AIzaSy..."
    ),
    OPENROUTER(
        "OpenRouter",
        "Hosted OpenAI-compatible embedding models.",
        "https://openrouter.ai/api/v1/",
        "openai/text-embedding-3-small",
        "sk-or-v1-..."
    ),
    CUSTOM(
        "Custom embedding endpoint",
        "Bring your own OpenAI-compatible embedding server.",
        "",
        "",
        "Optional for local endpoints"
    );

    companion object {
        fun fromString(value: String): EmbeddingProviderType =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: OFFLINE
    }
}

data class ExtractedEntity(
    val name: String,
    val type: String, // "LOCATION", "PERSON", "TOPIC", "ORGANIZATION", "DATE", "EVENT"
    val confidence: Float = 1.0f
)

data class ExtractedRelation(
    val source: String,
    val relation: String, // "MENTIONS", "DEPICTS", "LOCATED_AT", "REFERENCES", "ASSOCIATED_WITH"
    val target: String,
    val evidence: String = ""
)

data class AnalysisResult(
    val summary: String,
    val entities: List<ExtractedEntity> = emptyList(),
    val relations: List<ExtractedRelation> = emptyList(),
    val tags: List<String> = emptyList()
)

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val responseTimeMs: Long = 0L
)

fun isKeylessAiConfig(config: com.example.data.local.AiProviderConfigEntity): Boolean {
    val provider = ProviderType.fromString(config.providerType)
    if (provider == ProviderType.OLLAMA) return true
    if (provider != ProviderType.OPENAI_COMPATIBLE && provider != ProviderType.CUSTOM) return false
    val url = config.baseUrl.trim().lowercase()
    return url.contains("localhost") || url.contains("127.0.0.1") || url.contains("10.0.2.2")
}

data class AvailableAiModel(
    val id: String,
    val supportsChat: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsEmbedding: Boolean = false,
    val supportsMultimodalEmbedding: Boolean = false,
    val isFree: Boolean = false,
    val priceKnown: Boolean = false
)

enum class VectorDatabaseType(
    val displayName: String,
    val description: String,
    val isLocal: Boolean,
    val requiresEndpoint: Boolean
) {
    ROOM("Room — Local", "Built into the app. Vectors stay on this device.", true, false),
    QDRANT("Qdrant", "Open-source vector database. Self-hosted or cloud.", false, true);

    companion object {
        fun fromString(value: String): VectorDatabaseType =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: ROOM
    }
}

data class BrainModelChoice(
    val id: String,
    val displayName: String,
    val provider: ProviderType,
    val isLocal: Boolean,
    val description: String
)

