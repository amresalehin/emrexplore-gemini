package com.example.data.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.brain.BrainContentReader
import com.example.data.brain.BrainRepository
import com.example.data.brain.DefaultBrainAiGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/**
 * Gallery AI owns image/VLM analysis and both image/text embedding stages. Brain only persists completed data/vectors.
 */
class GalleryVlmWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val paths = inputData.getStringArray("paths").orEmpty().map(String::trim).filter(String::isNotBlank).distinct()
        if (paths.isEmpty()) return Result.success()

        val repository = BrainRepository(applicationContext)
        val client = AiProviderClient()
        val gateway = DefaultBrainAiGateway(client)
        val contentReader = BrainContentReader(applicationContext)
        val embeddings = AiEmbeddingService(applicationContext)
        val config = repository.getAiConfig()
        var failed = 0
        val store = BrainTargetedOperationStore(applicationContext)

        paths.forEachIndexed { index, path ->
            currentCoroutineContext().ensureActive()
            val file = File(path)
            try {
                if (!file.exists() || !file.isFile || !file.canRead()) {
                    throw IllegalStateException("File is not readable")
                }
                val input = contentReader.read(file, config)
                if (!input.isImage) throw IllegalStateException("Gallery AI only processes images")
                val analysis = gateway.analyzeImage(
                    base64Jpeg = input.imageBase64,
                    ocrText = input.ocrText,
                    metadataSummary = input.metadataSummary,
                    fileName = file.name,
                    config = config
                )
                val (imageVector, imageModel) = embeddings.embedImage(
                    input.imageBase64 ?: throw IllegalStateException("Image preview unavailable"),
                    config
                )
                repository.saveGalleryAi(file, input, analysis, imageModel)
                val texts = repository.buildEmbeddingTextsForAi(file, config)
                val (textVectors, textModel) = embeddings.embedText(texts, config)
                if (!repository.indexFile(
                        file = file,
                        config = config,
                        force = true,
                        precomputedTextEmbeddings = textVectors,
                        precomputedTextEmbeddingModel = textModel,
                        precomputedImageEmbedding = imageVector
                    )
                ) {
                    throw IllegalStateException("Embedding failed")
                }
                store.markCompleted(path)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed++
                setProgress(workDataOf(
                    "current" to index + 1,
                    "total" to paths.size,
                    "path" to path,
                    "stage" to "failed",
                    "error" to (error.message ?: "Gallery AI failed")
                ))
                return@forEachIndexed
            }

            setProgress(workDataOf(
                "current" to index + 1,
                "total" to paths.size,
                "path" to path,
                "stage" to "embedded",
                "error" to ""
            ))
        }

        return if (failed == 0) Result.success() else Result.failure(
            workDataOf("failed" to failed, "total" to paths.size)
        )
    }

    companion object {
        const val UNIQUE_NAME = "emrexplore-gallery-ai"
    }
}
