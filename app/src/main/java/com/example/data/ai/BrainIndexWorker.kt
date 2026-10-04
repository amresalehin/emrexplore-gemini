package com.example.data.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.example.data.local.AppDatabase
import java.io.File

class BrainIndexWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val config = db.aiProviderConfigDao().getConfig()?.let {
            it.copy(apiKey = com.example.data.security.ApiKeyProtector.decrypt(it.apiKey))
        } ?: return Result.failure(workDataOf("error" to "AI provider is not configured"))

        val manual = inputData.getBoolean("manual", false)
        val isOnlineReady = config.isEnabled && (isKeylessAiConfig(config) || config.apiKey.isNotBlank())
        if (!isOnlineReady && !manual) {
            return Result.failure(workDataOf("error" to "Brain auto-sync requires an enabled AI provider"))
        }

        val repository = KnowledgeGraphRepository(applicationContext)
        val candidates = repository.getBrainCandidates().filter {
            it.extension.lowercase() in SUPPORTED_EXTENSIONS &&
                !it.name.startsWith(".") &&
                !it.path.split(File.separatorChar).any { segment -> segment == ".trash" }
        }.map { File(it.path) }.filter { it.isFile && it.canRead() }

        if (candidates.isEmpty()) {
            repository.recomputeGraphDegrees()
            return Result.success(workDataOf("indexed" to 0))
        }

        var failures = 0
        for ((index, file) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            setProgress(workDataOf("current" to index + 1, "total" to candidates.size, "path" to file.absolutePath))
            if (!repository.indexFile(file, android.net.Uri.fromFile(file), config)) {
                failures++
            }
        }
        repository.recomputeGraphDegrees()
        return when {
            failures == 0 -> Result.success(workDataOf("indexed" to candidates.size, "failed" to 0))
            runAttemptCount < 2 -> Result.retry()
            else -> Result.failure(workDataOf("indexed" to candidates.size - failures, "failed" to failures))
        }
    }

    companion object {
        const val UNIQUE_NAME = "emrexplore-brain-index"
        private val SUPPORTED_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp",
            "txt", "md", "json", "csv", "xml", "html", "htm", "log", "kt", "java", "py", "js", "ts",
            "c", "cpp", "sql", "yaml", "yml", "pdf", "tsv", "gradle", "kts"
        )
    }
}