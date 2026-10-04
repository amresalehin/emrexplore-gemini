package com.example.data.ai

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

class GalleryAiWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val paths = inputData.getStringArray("paths")?.toList().orEmpty()
        if (paths.isEmpty()) return Result.success(workDataOf("processed" to 0))

        val db = AppDatabase.getDatabase(applicationContext)
        val rawConfig = db.aiProviderConfigDao().getConfig()
            ?: return Result.failure(workDataOf("error" to "AI provider is not configured"))
        val config = rawConfig.copy(apiKey = com.example.data.security.ApiKeyProtector.decrypt(rawConfig.apiKey))
        val provider = ProviderType.fromString(config.providerType)
        val keyless = provider in setOf(ProviderType.OLLAMA, ProviderType.OPENAI_COMPATIBLE, ProviderType.CUSTOM)
        if (!config.isEnabled || (!keyless && config.apiKey.isBlank())) {
            return Result.failure(workDataOf("error" to "Configure and save an AI provider first"))
        }

        val repository = KnowledgeGraphRepository(applicationContext)
        var processed = 0
        var failed = 0
        for ((index, path) in paths.withIndex()) {
            currentCoroutineContext().ensureActive()
            val file = File(path)
            setProgress(workDataOf("current" to index + 1, "total" to paths.size, "path" to path))
            if (!file.exists() || !file.isFile || !file.canRead()) {
                failed++
                continue
            }
            try {
                val uri = Uri.fromFile(file)
                if (repository.enrichGalleryImage(file, uri, config) &&
                    repository.indexFile(file, uri, config)
                ) {
                    processed++
                } else {
                    failed++
                }
            } catch (_: Exception) {
                failed++
            }
        }
        return if (failed == 0) {
            Result.success(workDataOf("processed" to processed, "failed" to 0))
        } else if (runAttemptCount < 2) {
            Result.retry()
        } else {
            Result.failure(workDataOf("processed" to processed, "failed" to failed))
        }
    }
}
