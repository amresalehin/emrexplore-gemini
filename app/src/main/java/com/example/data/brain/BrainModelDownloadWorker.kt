package com.example.data.brain

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.ai.BrainIndexWorker
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class BrainModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        currentCoroutineContext().ensureActive()
        val manager = OnDeviceEmbeddingModelManager(applicationContext)

        return try {
            manager.downloadDefaultModel { progress, downloaded, total ->
                setProgress(
                    workDataOf(
                        "progress" to progress,
                        "downloadedBytes" to downloaded,
                        "totalBytes" to total,
                        "modelId" to manager.defaultSpec().id
                    )
                )
            }

            currentCoroutineContext().ensureActive()
            val indexRequest = OneTimeWorkRequestBuilder<BrainIndexWorker>().build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                BrainIndexWorker.UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                indexRequest
            )

            Result.success(
                workDataOf(
                    "modelId" to manager.defaultSpec().id,
                    "displayName" to manager.defaultSpec().displayName,
                    "indexWorkEnqueued" to true
                )
            )
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        "error" to (error.message ?: "On-device Brain model download failed")
                    )
                )
            }
        }
    }

    companion object {
        const val UNIQUE_NAME = "emrexplore-brain-model-download"
    }
}
