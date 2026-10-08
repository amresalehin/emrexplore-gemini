package com.example.data.brain

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.ai.VectorDatabaseType
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class BrainVectorSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val outbox = db.brainVectorSyncDao()
        outbox.resetInFlight(System.currentTimeMillis())

        val repository = BrainRepository(applicationContext)
        val config = repository.getAiConfig()
        if (VectorDatabaseType.fromString(config.vectorDatabaseType) != VectorDatabaseType.QDRANT) {
            return Result.success()
        }

        val store = QdrantBrainVectorStore()
        val chunkDao = db.brainChunkDao()

        while (true) {
            currentCoroutineContext().ensureActive()
            val operations = outbox.getPending(BATCH_SIZE)
            if (operations.isEmpty()) break

            for (operation in operations) {
                currentCoroutineContext().ensureActive()
                outbox.markInFlight(operation.id, System.currentTimeMillis())
                try {
                    when (operation.operation) {
                        BrainVectorSyncOperations.UPSERT -> {
                            val chunkId = operation.chunkId ?: error("UPSERT operation has no chunkId")
                            val chunk = chunkDao.getByIds(listOf(chunkId)).firstOrNull()
                            if (chunk != null) {
                                store.upsert(listOf(chunk), config)
                            }
                        }
                        BrainVectorSyncOperations.DELETE -> {
                            val chunkId = operation.chunkId ?: error("DELETE operation has no chunkId")
                            if (!outbox.hasNewerUpsert(chunkId, operation.createdAt)) {
                                store.delete(listOf(chunkId), config)
                            }
                        }
                        BrainVectorSyncOperations.CLEAR -> store.clear(config)
                        else -> error("Unknown vector sync operation: ${operation.operation}")
                    }
                    outbox.markCompleted(operation.id, System.currentTimeMillis())
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    outbox.markFailed(
                        operation.id,
                        (error.message ?: "Vector synchronization failed").take(500),
                        System.currentTimeMillis()
                    )
                    return Result.retry()
                }
            }
        }

        outbox.deleteCompleted()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "emrexplore-brain-vector-sync"
        private const val BATCH_SIZE = 64

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<BrainVectorSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
