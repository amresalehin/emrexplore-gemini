package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.brain.BrainChunkEntity
import com.example.data.brain.BrainVectorKind
import com.example.data.brain.RoomBrainVectorStore
import com.example.data.local.AiProviderConfigEntity
import com.example.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainVectorStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    @After
    fun closeDatabase() {
        db.close()
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
