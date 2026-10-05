package com.example.data.brain

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
            Result.success(
                workDataOf(
                    "modelId" to manager.defaultSpec().id,
                    "displayName" to manager.defaultSpec().displayName
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
