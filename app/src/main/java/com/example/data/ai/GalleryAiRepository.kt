package com.example.data.ai

import com.example.data.ai.AiProviderClient
import com.example.data.ai.AnalysisResult
import com.example.data.ai.isKeylessAiConfig
import com.example.data.ai.ProviderType
import com.example.data.local.AiProviderConfigEntity
import com.example.data.media.MediaMetadataRepository
import com.example.data.model.MediaItem
import com.example.data.metadata.MetadataWriter
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GalleryAiRepository(private val context: android.content.Context) {
    private val appContext = context.applicationContext
    private val client = AiProviderClient()
    private val contentReader = BrainContentReader(appContext)
    private val mediaMetadataRepository = MediaMetadataRepository(appContext)

    private fun normalizeAiConfig(config: AiProviderConfigEntity): AiProviderConfigEntity {
        val provider = ProviderType.fromString(config.providerType)
        val explicitTextEmbedding = config.textEmbeddingModel.trim()
        val legacyEmbedding = config.embeddingModel.trim()
        return config.copy(
            providerType = provider.name,
            textEmbeddingModel = explicitTextEmbedding.ifBlank { legacyEmbedding },
            embeddingModel = legacyEmbedding
        )
    }

    suspend fun enrichGalleryImage(
        file: File,
        uri: android.net.Uri?,
        config: AiProviderConfigEntity,
        force: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {

        if (!file.exists() || !file.isFile || !BrainContentReader.IMAGE_EXTENSIONS.contains(file.extension.lowercase(Locale.US))) {
            return@withContext false
        }

        val normalized = normalizeAiConfig(config)
        val input = runCatching { contentReader.read(file, normalized) }.getOrNull() ?: return@withContext false
        val analysis = if (
            normalized.isEnabled &&
            (isKeylessAiConfig(normalized) || normalized.apiKey.isNotBlank())
        ) {
            client.analyzeImage(input.imageBase64, input.metadataSummary, file.name, normalized)
        } else {
            AnalysisResult(
                summary = input.metadataSummary.ifBlank { "Image " + file.name },
                tags = input.metadata?.summary?.keywords.orEmpty().take(8)
            )
        }

        val item = MediaItem(
            id = file.absolutePath.hashCode().toLong(),
            uri = uri ?: android.net.Uri.fromFile(file),
            name = file.name,
            path = file.absolutePath,
            size = file.length(),
            dateAdded = file.lastModified(),
            mimeType = input.mimeType,
            isVideo = false
        )
        val tagsJson = JSONArray(analysis.tags).toString()
        val entitiesJson = JSONArray().apply {
            analysis.entities.forEach {
                put(JSONObject().apply {
                    put("name", it.name.trim())
                    put("type", it.type.trim().uppercase(Locale.US))
                    put("confidence", it.confidence)
                })
            }
        }.toString()
        val relationsJson = JSONArray().apply {
            analysis.relations.forEach {
                put(JSONObject().apply {
                    put("source", it.source.trim())
                    put("relation", it.relation.trim())
                    put("target", it.target.trim())
                    put("evidence", it.evidence.trim())
                })
            }
        }.toString()

        MetadataWriter.writeAiMetadata(appContext, file, analysis.summary.trim(), analysis.tags)
        mediaMetadataRepository.getOrRead(item, requireOriginalLocation = false)
        true
    }
}
