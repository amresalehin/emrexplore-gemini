package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.OperationStatus
import com.example.data.operations.FileOperationManager
import com.example.data.repository.FileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FileRepositoryDeleteTest {
    private lateinit var repository: FileRepository
    private lateinit var db: AppDatabase
    private lateinit var testRoot: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)
        repository = FileRepository(context)
        testRoot = File(repository.baseWorkingDir, "delete-test-\${System.nanoTime()}").apply { mkdirs() }
        runBlocking {
            db.trashDao().clearAllTrash()
            db.fileIndexDao().deleteByPathTree(testRoot.absolutePath, testRoot.absolutePath)
        }
    }

    @After
    fun tearDown() {
        testRoot.deleteRecursively()
        repository.operationManager.shutdown()
    }

    @Test
    fun deleteToTrashKeepsSourceOutOfPlaceAndCreatesTrashRow() = runBlocking {
        val source = File(testRoot, "recoverable.txt").apply { writeText("recover me") }
        db.fileIndexDao().insertOrUpdate(
            com.example.data.local.IndexedFileEntity(
                path = source.absolutePath,
                name = source.name,
                parentPath = testRoot.absolutePath,
                size = source.length(),
                lastModified = source.lastModified(),
                isDirectory = false,
                mimeType = "text/plain",
                extension = "txt"
            )
        )

        assertTrue(repository.deleteFile(source.absolutePath, toTrash = true))

        assertFalse(source.exists())
        val row = db.trashDao().getAllTrash().first().single { it.originalPath == source.absolutePath }
        assertNotEquals(source.absolutePath, row.trashPath)
        assertTrue(File(row.trashPath).exists())
        assertTrue(db.fileIndexDao().getByPath(source.absolutePath) == null)
    }


    @Test
    fun operationManagerDelegatesTrashFlagWithoutDeletingSourceItself() = runBlocking {
        val source = File(testRoot, "manager.txt").apply { writeText("keep me") }
        var requestedToTrash: Boolean? = null
        val manager = FileOperationManager(
            onFilesMutated = {},
            deleteFile = { path, toTrash ->
                requestedToTrash = toTrash
                assertTrue(File(path).exists())
                true
            }
        )

        manager.startDelete(listOf(source.absolutePath), toTrash = true)
        withTimeout(5_000) {
            manager.progress.filter { it.status == OperationStatus.COMPLETED }.first()
        }

        assertTrue(source.exists())
        assertTrue(requestedToTrash == true)
        manager.shutdown()
    }

    @Test
    fun permanentDeleteStillDeletesSourceWithoutTrashRow() = runBlocking {
        val source = File(testRoot, "permanent.txt").apply { writeText("delete me") }

        assertTrue(repository.deleteFile(source.absolutePath, toTrash = false))

        assertFalse(source.exists())
        assertTrue(db.trashDao().getAllTrash().first().none { it.originalPath == source.absolutePath })
    }
}
