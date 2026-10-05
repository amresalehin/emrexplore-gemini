package com.example.data.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.brain.BrainRepository
import kotlinx.coroutines.ensureActive
import com.example.data.repository.FileRepository
import kotlinx.coroutines.currentCoroutineContext

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
            val paths = inputData.getStringArray("paths").orEmpty()
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
            val forcePaths = inputData.getStringArray("forcePaths").orEmpty().toSet()

            if (paths.isNotEmpty()) {
                val fileRepository = FileRepository(applicationContext)
                val targetedStore = BrainTargetedOperationStore(applicationContext)
                val config = brainRepository.getAiConfig()
                var indexed = 0
                var failed = 0

                paths.forEachIndexed { index, path ->
                    currentCoroutineContext().ensureActive()
                    val file = java.io.File(path)
                    val success = if (file.exists() && file.isFile && file.canRead()) {
                        // Update one canonical file-index entry. Never recurse from a targeted request.
                        fileRepository.indexFileOrDir(file)
                        brainRepository.indexFile(file, config, force = path in forcePaths)
                    } else {
                        false
                    }

                    if (success) {
                        indexed++
                        targetedStore.markCompleted(path)
                    } else {
                        failed++
                    }
                    currentCoroutineContext().ensureActive()
                    setProgress(
                        workDataOf(
                            "current" to index + 1,
                            "total" to paths.size,
                            "path" to path,
                            "success" to success
                        )
                    )
                }

                brainRepository.recomputeGraphDegrees()
                when {
                    failed == 0 -> Result.success(
                        workDataOf("indexed" to indexed, "skipped" to 0, "failed" to 0, "total" to paths.size)
                    )
                    runAttemptCount < 2 -> Result.retry()
                    else -> Result.failure(
                        workDataOf("indexed" to indexed, "skipped" to 0, "failed" to failed, "total" to paths.size)
                    )
                }
            } else {
                // Full Brain rebuild: FileRepository owns the one recursive storage scan.
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
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Result.failure(workDataOf("error" to (error.message ?: "Brain sync failed")))
        }
    }

    companion object {
        const val UNIQUE_NAME = "emrexplore-brain-index"
        const val TARGETED_UNIQUE_NAME = "emrexplore-brain-targeted-index"
    }
}
