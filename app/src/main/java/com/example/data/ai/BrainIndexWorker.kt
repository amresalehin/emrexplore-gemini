package com.example.data.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.brain.BrainRepository
import com.example.data.repository.FileRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Single Brain indexing pipeline.
 *
 * FileRepository owns the filesystem scan and persistent file index. Brain only
 * consumes that index; it never performs a second recursive storage scan.
 */
class BrainIndexWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        currentCoroutineContext().ensureActive()

        val brainRepository = BrainRepository(applicationContext)
        if (!brainRepository.isOnDeviceBrainModelReady()) {
            return Result.failure(
                workDataOf("error" to "Download the on-device Brain model before indexing")
            )
        }

        return try {
            // Refresh the canonical filesystem index once. BrainRepository.syncAll()
            // consumes this index and must never trigger another filesystem walk.
            FileRepository(applicationContext).indexStorage(
                force = inputData.getBoolean("refreshStorageIndex", false)
            )

            val result = brainRepository.syncAll(
                force = inputData.getBoolean("force", false)
            ) { current, total, path, outcome ->
                currentCoroutineContext().ensureActive()
                setProgress(
                    workDataOf(
                        "current" to current,
                        "total" to total,
                        "path" to path,
                        "success" to outcome.success,
                        "skipped" to outcome.skipped
                    )
                )
            }

            when {
                result.failed == 0 -> Result.success(
                    workDataOf(
                        "indexed" to result.indexed,
                        "skipped" to result.skipped,
                        "failed" to 0,
                        "total" to result.total
                    )
                )
                runAttemptCount < 2 -> Result.retry()
                else -> Result.failure(
                    workDataOf(
                        "indexed" to result.indexed,
                        "skipped" to result.skipped,
                        "failed" to result.failed,
                        "total" to result.total
                    )
                )
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Result.failure(workDataOf("error" to (error.message ?: "Brain sync failed")))
        }
    }

    companion object {
        const val UNIQUE_NAME = "emrexplore-brain-index"
    }
}
