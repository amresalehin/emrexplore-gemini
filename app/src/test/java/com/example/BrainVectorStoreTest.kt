package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.brain.BrainChunkEntity
import com.example.data.brain.BrainVectorKind
import com.example.data.brain.BrainVectorSyncOperationEntity
import com.example.data.brain.BrainVectorSyncOperations
import com.example.data.brain.RoomBrainVectorStore
import com.example.data.local.AiProviderConfigEntity
import com.example.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrainVectorStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    @After
    fun closeDatabase() {
        db.close()
    }


    @Test
    fun vectorSyncOutbox_preservesOrderingAndNewerUpsertProtection() = runBlocking {
        val dao = db.brainVectorSyncDao()
        val delete = BrainVectorSyncOperationEntity(
            id = "delete",
            operation = BrainVectorSyncOperations.DELETE,
            chunkId = "chunk-1",
            createdAt = 10L,
            updatedAt = 10L
        )
        val upsert = BrainVectorSyncOperationEntity(
            id = "upsert",
            operation = BrainVectorSyncOperations.UPSERT,
            chunkId = "chunk-1",
            createdAt = 20L,
            updatedAt = 20L
        )
        dao.insertAll(listOf(delete, upsert))

        assertTrue(dao.hasNewerUpsert("chunk-1", 10L))
        assertEquals("delete", dao.getPending(1).single().id)

        dao.markInFlight("delete", 30L)
        dao.markFailed("delete", "temporary", 31L)
        assertEquals("delete", dao.getPending(1).single().id)

        dao.markCompleted("delete", 32L)
        assertEquals("upsert", dao.getPending(1).single().id)
    }

    @Test
    fun roomSearch_scansPastFormerTwentyThousandRowCeiling() = runBlocking {
        val model = "test-model"
        val now = System.currentTimeMillis()
        val chunks = ArrayList<BrainChunkEntity>(20_001)
        repeat(20_000) { index ->
            chunks += BrainChunkEntity(
                id = "chunk-%05d".format(index),
                filePath = "/storage/test/file-$index.txt",
                chunkIndex = 0,
                content = "irrelevant",
                embeddingJson = "[1.0,0.0]",
                embeddingModel = model,
                indexedAt = now
            )
        }
        chunks += BrainChunkEntity(
            id = "chunk-target",
            filePath = "/storage/test/target.txt",
            chunkIndex = 0,
            content = "target",
            embeddingJson = "[0.0,1.0]",
            embeddingModel = model,
            indexedAt = now
        )
        db.brainChunkDao().insertAll(chunks)

        val results = RoomBrainVectorStore(db.brainChunkDao()).search(
            vector = floatArrayOf(0f, 1f),
            model = model,
            limit = 1,
            config = AiProviderConfigEntity(),
            kind = BrainVectorKind.TEXT
        )

        assertEquals(1, results.size)
        assertEquals("chunk-target", results.single().id)
        assertTrue(results.single().score > 0.99f)
    }
}
