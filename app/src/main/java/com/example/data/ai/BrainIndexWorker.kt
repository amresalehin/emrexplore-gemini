package com.example.data.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import com.example.data.repository.FileRepository
import kotlinx.coroutines.ensureActive
import java.io.File

class BrainIndexWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val config = db.aiProviderConfigDao().getConfig()?.let {
            it.copy(apiKey = com.example.data.security.ApiKeyProtector.decrypt(it.apiKey))
        } ?: return Result.failure(workDataOf("error" to "AI provider is not configured"))

        val provider = ProviderType.fromString(config.providerType)
        val keyless = provider in setOf(ProviderType.OLLAMA, ProviderType.OPENAI_COMPATIBLE, ProviderType.CUSTOM)
        if (!config.isEnabled || (!keyless && config.apiKey.isBlank())) {
            return Result.failure(workDataOf("error" to "Configure and save an AI provider first"))
        }

        val repository = KnowledgeGraphRepository(applicationContext)
        val candidates = repository.getBrainCandidates().filter {
            it.extension.lowercase() in SUPPORTED_EXTENSIONS && !it.name.startsWith(".")
        }.map { File(it.path) }.filter { it.isFile && it.canRead() }

        if (candidates.isEmpty()) {
            repository.recomputeGraphDegrees()
            return Result.success(workDataOf("indexed" to 0))
        }

        for ((index, file) in candidates.withIndex()) {
            ensureActive()
            setProgress(workDataOf("current" to index + 1, "total" to candidates.size, "path" to file.absolutePath))
            repository.indexFile(file, android.net.Uri.fromFile(file), config)
        }
        repository.recomputeGraphDegrees()
        return Result.success(workDataOf("indexed" to candidates.size))
    }

    companion object {
        const val UNIQUE_NAME = "emrexplore-brain-index"
        private val SUPPORTED_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp",
            "txt", "md", "json", "csv", "xml", "html", "htm", "log", "kt", "java", "py", "js", "ts",
            "c", "cpp", "properties", "sql", "yaml", "yml", "pdf", "conf", "ini", "tsv", "gradle", "kts", "env"
        )
    }
}