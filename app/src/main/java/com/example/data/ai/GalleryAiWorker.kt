package com.example.data.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

class GalleryAiWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        const val UNIQUE_NAME = "emrexplore-gallery-ai"
    }

    override suspend fun doWork(): Result {
        val store = GalleryAiOperationStore(applicationContext)

        // Migrate any work queued by an older app build into the durable checkpoint store.
        val legacyPaths = inputData.getStringArray("paths").orEmpty().toList()
        if (legacyPaths.isNotEmpty() && !store.hasPendingWork()) {
            store.addPaths(legacyPaths)
        }

        if (store.isPaused()) {
            return Result.success(
                workDataOf(
                    "processed" to store.completedCount(),
                    "total" to store.totalCount(),
                    "paused" to true
                )
            )
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val rawConfig = db.aiProviderConfigDao().getConfig()
            ?: return Result.failure(workDataOf("error" to "AI provider is not configured"))
        val config = rawConfig.copy(
            apiKey = com.example.data.security.ApiKeyProtector.decrypt(rawConfig.apiKey)
        )
        if (!config.isEnabled || (!isKeylessAiConfig(config) && config.apiKey.isBlank())) {
            return Result.failure(workDataOf("error" to "Configure and save an AI provider first"))
        }

        val repository = KnowledgeGraphRepository(applicationContext)
        var failedThisAttempt = 0

        while (true) {
            currentCoroutineContext().ensureActive()
            if (store.isPaused()) {
                return Result.success(
                    workDataOf(
                        "processed" to store.completedCount(),
                        "total" to store.totalCount(),
                        "paused" to true
                    )
                )
            }

            val paths = store.pendingPaths()
            if (paths.isEmpty()) {
                val processed = store.completedCount()
                val total = store.totalCount()
                store.clear()
                return Result.success(
                    workDataOf(
                        "processed" to processed,
                        "total" to total,
                        "failed" to 0
                    )
                )
            }

            val total = store.totalCount()
            for (path in paths) {
                currentCoroutineContext().ensureActive()

                if (store.isPaused()) {
                    return Result.success(
                        workDataOf(
                            "processed" to store.completedCount(),
                            "total" to store.totalCount(),
                            "paused" to true
                        )
                    )
                }

                val completed = store.completedCount()
                setProgress(
                    workDataOf(
                        "current" to completed,
                        "total" to total,
                        "path" to path
                    )
                )

                val file = File(path)
                if (!file.exists() || !file.isFile || !file.canRead()) {
                    store.markCompleted(path)
                    continue
                }

                try {
                    val uri = android.net.Uri.fromFile(file)
                    val force = store.isForce(path)
                    val enriched = repository.enrichGalleryImage(file, uri, config, force = force)
                    val indexed = enriched && repository.indexFile(file, uri, config)
                    if (indexed) {
                        store.markCompleted(path)
                        setProgress(
                            workDataOf(
                                "current" to store.completedCount(),
                                "total" to total,
                                "path" to path
                            )
                        )
                    } else {
                        failedThisAttempt++
                    }
                } catch (error: Exception) {
                    failedThisAttempt++
                }
            }

            if (!store.hasPendingWork()) {
                val completed = store.completedCount()
                val completedTotal = store.totalCount()
                store.clear()
                return Result.success(
                    workDataOf(
                        "processed" to completed,
                        "total" to completedTotal,
                        "failed" to failedThisAttempt
                    )
                )
            }

            if (failedThisAttempt > 0) {
                return if (runAttemptCount < 2) {
                    Result.retry()
                } else {
                    Result.failure(
                        workDataOf(
                            "processed" to store.completedCount(),
                            "total" to store.totalCount(),
                            "failed" to store.pendingPaths().size
                        )
                    )
                }
            }
        }
    }
}
