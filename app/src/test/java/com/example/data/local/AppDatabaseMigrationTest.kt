package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Entity(tableName = "brain_chunks")
data class LegacyBrainChunkV13(
    @PrimaryKey val id: String,
    val filePath: String,
    val chunkIndex: Int,
    val content: String,
    val embeddingJson: String,
    val embeddingModel: String,
    val offlineEmbeddingJson: String,
    val locator: String,
    val pageNumber: Int?,
    val indexedAt: Long
)

@Entity(tableName = "media_metadata")
data class LegacyMediaMetadataV13(
    @PrimaryKey val uri: String,
    val path: String,
    val size: Long,
    val dateAdded: Long,
    val make: String?,
    val model: String?,
    val lens: String?,
    val iso: Int?,
    val aperture: Double?,
    val focalLength: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val hasGps: Boolean,
    val capturedAt: Long?,
    val searchableText: String,
    val indexedAt: Long
)

@Database(
    entities = [LegacyBrainChunkV13::class, LegacyMediaMetadataV13::class],
    version = 13,
    exportSchema = false
)
abstract class LegacyDatabaseV13 : androidx.room.RoomDatabase()

@Entity(
    tableName = "brain_chunks",
    indices = [
        Index(value = ["filePath"]),
        Index(value = ["embeddingModel"]),
        Index(value = ["indexedAt"])
    ]
)
data class MigrationBrainChunkV15(
    @PrimaryKey val id: String,
    val filePath: String,
    val chunkIndex: Int,
    val content: String,
    val embeddingJson: String,
    val embeddingModel: String,
    val locator: String,
    val pageNumber: Int?,
    val indexedAt: Long
)

@Entity(
    tableName = "media_metadata",
    indices = [
        Index(value = ["path"]),
        Index(value = ["make"]),
        Index(value = ["model"]),
        Index(value = ["hasGps"]),
        Index(value = ["capturedAt"])
    ]
)
data class MigrationMediaMetadataV15(
    @PrimaryKey val uri: String,
    val path: String,
    val size: Long,
    val dateAdded: Long,
    val make: String?,
    val model: String?,
    val lens: String?,
    val iso: Int?,
    val aperture: Double?,
    val focalLength: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val hasGps: Boolean,
    val capturedAt: Long?,
    val searchableText: String,
    val indexedAt: Long
)

@Database(
    entities = [MigrationBrainChunkV15::class, MigrationMediaMetadataV15::class],
    version = 15,
    exportSchema = false
)
abstract class MigrationTargetDatabaseV15 : androidx.room.RoomDatabase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "migration-13-15-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrate13To15PreservesDualEmbeddingsAndMediaRows() {
        val legacy = Room.databaseBuilder(
            context,
            LegacyDatabaseV13::class.java,
            databaseName
        ).allowMainThreadQueries().build()

        legacy.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO brain_chunks (
                id, filePath, chunkIndex, content,
                embeddingJson, embeddingModel, offlineEmbeddingJson,
                locator, pageNumber, indexedAt
            ) VALUES (
                'chunk-1', '/storage/emulated/0/note.txt', 0, 'hello',
                '[0.1,0.2]', 'text-v1', '[0.9,0.8]',
                'page:1', 1, 1700000000000
            )
            """.trimIndent()
        )
        legacy.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO media_metadata (
                uri, path, size, dateAdded, make, model, lens, iso,
                aperture, focalLength, latitude, longitude, hasGps,
                capturedAt, searchableText, indexedAt
            ) VALUES (
                'content://media/1', '/storage/emulated/0/photo.jpg', 1234, 1700000000,
                'Acme', 'Camera X', 'Lens Y', 100,
                1.8, 50.0, 12.3, 45.6, 1,
                1700000000000, 'camera acme', 1700000000000
            )
            """.trimIndent()
        )
        legacy.close()

        val migrated = Room.databaseBuilder(
            context,
            MigrationTargetDatabaseV15::class.java,
            databaseName
        )
            .allowMainThreadQueries()
            .addMigrations(
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15
            )
            .build()

        val brainCursor = migrated.openHelper.writableDatabase.query(
            "SELECT embeddingJson, embeddingModel, locator, pageNumber, indexedAt FROM brain_chunks WHERE id = 'chunk-1'"
        )
        assertTrue(brainCursor.moveToFirst())
        assertEquals("[0.1,0.2]", brainCursor.getString(0))
        assertEquals("text-v1", brainCursor.getString(1))
        assertEquals("page:1", brainCursor.getString(2))
        assertEquals(1, brainCursor.getInt(3))
        assertEquals(1700000000000L, brainCursor.getLong(4))
        brainCursor.close()

        val brainColumns = migrated.openHelper.writableDatabase.query("PRAGMA table_info(brain_chunks)")
        var hasOfflineEmbedding = false
        while (brainColumns.moveToNext()) {
            if (brainColumns.getString(1) == "offlineEmbeddingJson") {
                hasOfflineEmbedding = true
                break
            }
        }
        brainColumns.close()
        assertFalse(hasOfflineEmbedding)

        val mediaCursor = migrated.openHelper.writableDatabase.query(
            "SELECT path, make, model, searchableText FROM media_metadata WHERE uri = 'content://media/1'"
        )
        assertTrue(mediaCursor.moveToFirst())
        assertEquals("/storage/emulated/0/photo.jpg", mediaCursor.getString(0))
        assertEquals("Acme", mediaCursor.getString(1))
        assertEquals("Camera X", mediaCursor.getString(2))
        assertEquals("camera acme", mediaCursor.getString(3))
        mediaCursor.close()

        migrated.close()
    }
}
