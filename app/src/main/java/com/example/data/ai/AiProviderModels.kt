package com.example.data.ai

import com.example.data.local.KgNodeEntity
import com.example.data.local.RagChunkEntity

enum class ProviderType(
    val displayName: String,
    val description: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val defaultVisionModel: String,
    val defaultEmbeddingModel: String,
    val keyHint: String
) {
    GEMINI(
        displayName = "Google Gemini",
        description = "Direct REST with Gemini 3.5 Flash & native multimodal vision",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/",
        defaultModel = "gemini-3.8-flash",
        defaultVisionModel = "gemini-3.8-flash",
        defaultEmbeddingModel = "gemini-embedding-2",
        keyHint = "AIzaSy..."
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI",
        description = "GPT-4o & GPT-4o-mini with structured entity extraction",
        defaultBaseUrl = "https://api.openai.com/v1/",
        defaultModel = "gpt-4o-mini",
        defaultVisionModel = "gpt-4o-mini",
        defaultEmbeddingModel = "text-embedding-3-small",
        keyHint = "sk-proj-..."
    ),
    OLLAMA(
        displayName = "Ollama (Local AI)",
        description = "Completely private on-device or local LAN network, zero cloud leaks",
        defaultBaseUrl = "http://10.0.2.2:11434/v1/",
        defaultModel = "llama3.2:latest",
        defaultVisionModel = "llama3.2-vision:latest",
        defaultEmbeddingModel = "nomic-embed-text:latest",
        keyHint = "Optional (not required for local Ollama)"
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        description = "Universal router to Claude 3.5, Llama 3, DeepSeek, and Gemini",
        defaultBaseUrl = "https://openrouter.ai/api/v1/",
        defaultModel = "meta-llama/llama-3.2-11b-vision-instruct",
        defaultVisionModel = "meta-llama/llama-3.2-11b-vision-instruct",
        defaultEmbeddingModel = "openai/text-embedding-3-small",
        keyHint = "sk-or-v1-..."
    ),
    GROQ(
        displayName = "Groq Cloud",
        description = "Ultra-fast LPUs for instant document extraction & indexing",
        defaultBaseUrl = "https://api.groq.com/openai/v1/",
        defaultModel = "openai/gpt-oss-120b",
        defaultVisionModel = "openai/gpt-oss-120b",
        defaultEmbeddingModel = "",
        keyHint = "gsk_..."
    );

    companion object {
        fun fromString(value: String): ProviderType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GEMINI
        }
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

data class RagAnswer(
    val answer: String,
    val sourceChunks: List<RagChunkEntity> = emptyList(),
    val connectedNodes: List<KgNodeEntity> = emptyList(),
    val isSuccessful: Boolean = true,
    val latencyMs: Long = 0L
)

data class ConnectedDotsItem(
    val fileNode: KgNodeEntity,
    val relationship: String,
    val targetNode: KgNodeEntity,
    val snippet: String = ""
)


data class AvailableAiModel(
    val id: String,
    val supportsVision: Boolean = false,
    val supportsEmbedding: Boolean = false
)
