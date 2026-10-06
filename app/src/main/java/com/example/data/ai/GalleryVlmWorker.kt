package com.example.data.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.brain.BrainContentReader
import com.example.data.brain.BrainRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/**
 * Gallery AI owns image/VLM enrichment and image embedding. Brain only persists the completed vectors.
 */
class GalleryVlmWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val paths = inputData.getStringArray("paths").orEmpty().map(String::trim).filter(String::isNotBlank).distinct()
        if (paths.isEmpty()) return Result.success()

        val repository = BrainRepository(applicationContext)
        val client = AiProviderClient()
        val contentReader = BrainContentReader(applicationContext)
        val config = repository.getAiConfig()
        if (config.multimodalEmbeddingModel.isBlank()) {
            return Result.failure(workDataOf("error" to "Choose a Gallery AI image embedding model before running Gallery AI"))
        }
        val store = BrainTargetedOperationStore(applicationContext)
        var failed = 0
        paths.forEachIndexed { index, path ->
            currentCoroutineContext().ensureActive()
            val file = File(path)
            val input = runCatching { contentReader.read(file, config) }.getOrElse { error ->
                failed++
                setProgress(workDataOf("current" to index + 1, "total" to paths.size, "path" to path, "stage" to "failed", "error" to (error.message ?: "Could not read image")))
                return@forEachIndexed
            }
            val imageEmbedding = runCatching {
                client.embedMultimodalDocument(input.imageBase64 ?: error("Image preview unavailable"), config)
            }.getOrElse { error ->
                failed++
                setProgress(workDataOf("current" to index + 1, "total" to paths.size, "path" to path, "stage" to "failed", "error" to (error.message ?: "Image embedding failed")))
                return@forEachIndexed
            }
            val outcome = repository.runGalleryAi(file, force = true, precomputedImageEmbedding = imageEmbedding)
            if (outcome.success) store.markCompleted(path) else failed++
            setProgress(workDataOf(
                "current" to index + 1,
                "total" to paths.size,
                "path" to path,
                "stage" to if (outcome.success) "embedded" else "failed",
                "error" to (outcome.error ?: "")
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
