package com.example.data.ai

import android.content.Context
import com.example.data.brain.OnDeviceEmbeddingEngine
import com.example.data.local.AiProviderConfigEntity

class AiEmbeddingService(context: Context) {
    private val client = AiProviderClient()
    private val onDevice = OnDeviceEmbeddingEngine(context.applicationContext)

    suspend fun embedText(
        texts: List<String>,
        config: AiProviderConfigEntity
    ): Pair<List<FloatArray>, String> {
        if (texts.isEmpty()) return emptyList<FloatArray>() to ""
        return when (EmbeddingProviderType.fromString(config.embeddingProviderType)) {
            EmbeddingProviderType.OFFLINE -> {
                if (!onDevice.isReady()) {
                    throw IllegalStateException("Download the selected on-device embedding model before running AI")
                }
                val vectors = onDevice.embedTextPassages(texts)
                    ?: throw IllegalStateException("On-device embedding failed")
                val model = onDevice.modelIdIfReady()
                    ?: throw IllegalStateException("Selected on-device embedding model is unavailable")
                vectors to model
            }
            else -> {
                val model = config.textEmbeddingModel.ifBlank { config.embeddingModel }.trim()
                if (model.isBlank()) throw IllegalStateException("Choose a text embedding model before running AI")
                client.embedTextPassages(texts, config) to model
            }
        }.also { (vectors, _) ->
            if (vectors.size != texts.size || vectors.any { it.isEmpty() }) {
                throw IllegalStateException("Embedding provider returned an incomplete embedding batch")
            }
        }
    }

    suspend fun embedImage(
        imageBase64: String,
        config: AiProviderConfigEntity
    ): Pair<FloatArray, String> {
        val model = config.multimodalEmbeddingModel.trim()
        if (model.isBlank()) throw IllegalStateException("Choose a Gallery AI image embedding model before running Gallery AI")
        val vector = client.embedMultimodalDocument(imageBase64, config)
            ?: throw IllegalStateException("Image embedding provider returned no vector")
        if (vector.isEmpty()) throw IllegalStateException("Image embedding provider returned an empty vector")
        return vector to model
    }
}
