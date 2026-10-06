package com.example.data.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.brain.BrainRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/**
 * File AI owns document enrichment. Brain is only the final embedding/storage consumer.
 */
class FileAiWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val paths = inputData.getStringArray("paths").orEmpty().map(String::trim).filter(String::isNotBlank).distinct()
        if (paths.isEmpty()) return Result.success()

        val repository = BrainRepository(applicationContext)
        var failed = 0
        paths.forEachIndexed { index, path ->
            currentCoroutineContext().ensureActive()
            val file = File(path)
            val outcome = repository.runFileAi(file, force = true)
            if (!outcome.success) failed++
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
        const val UNIQUE_NAME = "emrexplore-file-ai"
    }
}
