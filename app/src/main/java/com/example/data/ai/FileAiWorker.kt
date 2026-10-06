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
 * File AI owns document analysis and text embedding. Brain only persists the completed data/vectors.
 */
class FileAiWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
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

        paths.forEachIndexed { index, path ->
            currentCoroutineContext().ensureActive()
            val file = File(path)
            try {
                if (!file.exists() || !file.isFile || !file.canRead()) {
                    throw IllegalStateException("File is not readable")
                }
                val input = contentReader.read(file, config)
                if (input.isImage) throw IllegalStateException("Images are owned by Gallery AI")
                val analysis = gateway.analyzeDocument(input.text.take(12_000), file.name, config)
                repository.saveDocumentAi(file, analysis, config.chatModel.ifBlank { config.providerType })
                val texts = repository.buildEmbeddingTextsForAi(file, config)
                val (vectors, model) = embeddings.embedText(texts, config)
                if (!repository.indexFile(
                        file = file,
                        config = config,
                        force = true,
                        precomputedTextEmbeddings = vectors,
                        precomputedTextEmbeddingModel = model
                    )
                ) {
                    throw IllegalStateException("Embedding failed")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed++
                setProgress(workDataOf(
                    "current" to index + 1,
                    "total" to paths.size,
                    "path" to path,
                    "stage" to "failed",
                    "error" to (error.message ?: "File AI failed")
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
        const val UNIQUE_NAME = "emrexplore-file-ai"
    }
}
