package com.example

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.getRequiredStoragePermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("emrexplore", appName)
    assertEquals("Home", context.getString(R.string.nav_home))
    assertEquals("Files", context.getString(R.string.nav_files))
    assertEquals("Gallery", context.getString(R.string.nav_gallery))
  }

  @Test
  fun `verify required storage permissions list`() {
    val permissions = getRequiredStoragePermissions()
    assertTrue(permissions.isNotEmpty())
    // On API 36 (Tiramisu+), media permissions are requested
    assertTrue(permissions.contains(Manifest.permission.READ_MEDIA_IMAGES))
  }

  @Test
  fun `verify home search initial state and query updates`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.viewmodel.UnifiedViewModel(application)
    assertEquals("", viewModel.uiState.value.homeSearchQuery)
    assertEquals(emptyList<com.example.data.model.FileItem>(), viewModel.uiState.value.homeSearchResults)

    viewModel.setHomeSearchQuery("test")
    assertEquals("test", viewModel.uiState.value.homeSearchQuery)

    viewModel.clearHomeSearch()
    assertEquals("", viewModel.uiState.value.homeSearchQuery)
    assertEquals(emptyList<com.example.data.model.FileItem>(), viewModel.uiState.value.homeSearchResults)
  }

  @Test
  fun `verify room database file index operations`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.AppDatabase.getDatabase(context)
    val indexDao = db.fileIndexDao()

    indexDao.clearIndex()
    assertEquals(0, indexDao.getTotalCount())

    val testFiles = listOf(
      com.example.data.local.IndexedFileEntity(
        path = "/storage/emulated/0/Documents/report_2026.pdf",
        name = "report_2026.pdf",
        parentPath = "/storage/emulated/0/Documents",
        size = 102400L,
        lastModified = 1700000000000L,
        isDirectory = false,
        mimeType = "application/pdf",
        extension = "pdf",
        category = "DOCUMENTS"
      ),
      com.example.data.local.IndexedFileEntity(
        path = "/storage/emulated/0/Pictures/vacation.jpg",
        name = "vacation.jpg",
        parentPath = "/storage/emulated/0/Pictures",
        size = 2048000L,
        lastModified = 1710000000000L,
        isDirectory = false,
        mimeType = "image/jpeg",
        extension = "jpg",
        category = "IMAGES"
      )
    )

    indexDao.insertAll(testFiles)
    assertEquals(2, indexDao.getTotalCount())

    val searchResults = indexDao.searchFiles("report")
    assertEquals(1, searchResults.size)
    assertEquals("report_2026.pdf", searchResults[0].name)
    assertEquals("DOCUMENTS", searchResults[0].category)

    val categoryResults = indexDao.getFilesByCategory("IMAGES")
    assertEquals(1, categoryResults.size)
    assertEquals("vacation.jpg", categoryResults[0].name)

    val stats = indexDao.getCategoryStats()
    assertTrue(stats.any { it.category == "DOCUMENTS" && it.count == 1 })
    assertTrue(stats.any { it.category == "IMAGES" && it.count == 1 })

    indexDao.deleteByPath("/storage/emulated/0/Pictures/vacation.jpg")
    assertEquals(1, indexDao.getTotalCount())
  }

  @Test
  fun `verify room database explorer preferences persistence`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.AppDatabase.getDatabase(context)
    val prefsDao = db.preferencesDao()

    val testPrefs = com.example.data.local.ExplorerPreferencesEntity(
      id = 1,
      viewMode = "GRID",
      sortOption = "SIZE_DESC",
      showHidden = true,
      defaultStartupPath = "/storage/emulated/0/Download",
      rememberLastDirectory = true,
      lastDirectoryPath = "/storage/emulated/0/DCIM",
      galleryColumns = 4,
      enableFastRoomSearch = true
    )

    prefsDao.savePreferences(testPrefs)
    val loaded = prefsDao.getPreferences()
    org.junit.Assert.assertNotNull(loaded)
    assertEquals("GRID", loaded?.viewMode)
    assertEquals("SIZE_DESC", loaded?.sortOption)
    assertTrue(loaded?.showHidden == true)
    assertEquals("/storage/emulated/0/DCIM", loaded?.lastDirectoryPath)
    assertEquals(4, loaded?.galleryColumns)
    assertTrue(loaded?.enableFastRoomSearch == true)

    prefsDao.updateViewMode("COMPACT_LIST")
    val updated = prefsDao.getPreferences()
    assertEquals("COMPACT_LIST", updated?.viewMode)
  }

  @Test
  fun `verify room database paged folder queries`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.AppDatabase.getDatabase(context)
    val indexDao = db.fileIndexDao()
    val testFolder = "/storage/emulated/0/TestFolder"

    indexDao.deleteByPath(testFolder)
    val files = (1..15).map { i ->
      com.example.data.local.IndexedFileEntity(
        path = "$testFolder/file_$i.txt",
        name = "file_$i.txt",
        parentPath = testFolder,
        size = 100L * i,
        lastModified = 1700000000000L + i,
        isDirectory = false,
        mimeType = "text/plain",
        extension = "txt",
        category = "DOCUMENTS"
      )
    }
    indexDao.insertAll(files)

    val count = indexDao.getCountByParent(testFolder)
    assertEquals(15, count)

    val page1 = indexDao.getFilesByParentPaged(testFolder, limit = 5, offset = 0)
    assertEquals(5, page1.size)

    val page2 = indexDao.getFilesByParentPaged(testFolder, limit = 5, offset = 5)
    assertEquals(5, page2.size)

    val page3 = indexDao.getFilesByParentPaged(testFolder, limit = 5, offset = 10)
    assertEquals(5, page3.size)

    val page4 = indexDao.getFilesByParentPaged(testFolder, limit = 5, offset = 15)
    assertEquals(0, page4.size)
  }

  @Test
  fun `verify repository paged directory loading with lazy fetching`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.repository.FileRepository(context)

    // Create a temporary directory with 25 files
    val testDir = java.io.File(context.cacheDir, "paged_test_dir").apply {
      deleteRecursively()
      mkdirs()
    }
    for (i in 1..25) {
      java.io.File(testDir, "item_$i.txt").writeText("content $i")
    }

    // Page 0 with pageSize = 10
    val page0 = repo.getFilesPaged(
      dirPath = testDir.absolutePath,
      page = 0,
      pageSize = 10,
      sortOption = com.example.data.model.SortOption.NAME_ASC,
      showHidden = false
    )
    assertEquals(10, page0.items.size)
    assertEquals(25, page0.totalCount)
    assertEquals(0, page0.page)
    assertTrue(page0.hasMore)

    // Page 1 with pageSize = 10
    val page1 = repo.getFilesPaged(
      dirPath = testDir.absolutePath,
      page = 1,
      pageSize = 10,
      sortOption = com.example.data.model.SortOption.NAME_ASC,
      showHidden = false
    )
    assertEquals(10, page1.items.size)
    assertTrue(page1.hasMore)

    // Page 2 with pageSize = 10 (remaining 5)
    val page2 = repo.getFilesPaged(
      dirPath = testDir.absolutePath,
      page = 2,
      pageSize = 10,
      sortOption = com.example.data.model.SortOption.NAME_ASC,
      showHidden = false
    )
    assertEquals(5, page2.items.size)
    org.junit.Assert.assertFalse(page2.hasMore)

    testDir.deleteRecursively()
  }

  @Test
  fun `verify all files access intent creation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = com.example.ui.components.createAllFilesAccessIntent(context)
    org.junit.Assert.assertNotNull(intent)
    org.junit.Assert.assertNotNull(intent.action)
  }

  @Test
  fun `verify all files access check executes without exception`() {
    val isGranted = com.example.ui.components.isAllFilesAccessGranted()
    // Returns boolean without crash
    assertTrue(isGranted == true || isGranted == false)
  }

  @Test
  fun `verify IO priority coordinator interactive flag and yield`() {
    kotlinx.coroutines.runBlocking {
      com.example.data.performance.IoPriorityCoordinator.reset()
      org.junit.Assert.assertFalse(com.example.data.performance.IoPriorityCoordinator.isInteractiveActive())

      com.example.data.performance.IoPriorityCoordinator.notifyInteractiveActivity()
      assertTrue(com.example.data.performance.IoPriorityCoordinator.isInteractiveActive())

      // Running inside interactive block
      val result = com.example.data.performance.IoPriorityCoordinator.withInteractivePriority {
        assertTrue(com.example.data.performance.IoPriorityCoordinator.isInteractiveActive())
        42
      }
      assertEquals(42, result)
    }
  }

  @Test
  fun `verify file operation manager copy with progress and completion`() {
    kotlinx.coroutines.runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val testSrcDir = java.io.File(context.cacheDir, "op_src_test_${System.currentTimeMillis()}").apply { mkdirs() }
      val testDestDir = java.io.File(context.cacheDir, "op_dest_test_${System.currentTimeMillis()}").apply { mkdirs() }

      val file1 = java.io.File(testSrcDir, "sample1.txt").apply { writeText("Hello World Performance") }
      val file2 = java.io.File(testSrcDir, "sample2.txt").apply { writeText("Second file test") }

      var mutatedPaths = emptyList<String>()
      val manager = com.example.data.operations.FileOperationManager { affected ->
        mutatedPaths = affected
      }

      manager.startCopy(listOf(file1.absolutePath, file2.absolutePath), testDestDir.absolutePath)

      // Wait for operation completion
      var loops = 0
      while (manager.progress.value.status != com.example.data.model.OperationStatus.COMPLETED && loops < 50) {
        kotlinx.coroutines.delay(100)
        loops++
      }

      assertEquals(com.example.data.model.OperationStatus.COMPLETED, manager.progress.value.status)
      assertEquals(2, manager.progress.value.filesProcessed)
      assertTrue(java.io.File(testDestDir, "sample1.txt").exists())
      assertTrue(java.io.File(testDestDir, "sample2.txt").exists())

      testSrcDir.deleteRecursively()
      testDestDir.deleteRecursively()
    }
  }

  @Test
  fun `verify large directory lazy paged loading with 1000 files`() {
    kotlinx.coroutines.runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val repo = com.example.data.repository.FileRepository(context)
      val massiveDir = java.io.File(context.cacheDir, "massive_1000_${System.currentTimeMillis()}").apply { mkdirs() }

      // Create 1,000 files
      for (i in 1..1000) {
        java.io.File(massiveDir, "file_%04d.dat".format(i)).writeBytes(ByteArray(16))
      }

      val startMs = System.currentTimeMillis()
      val page0 = repo.getFilesPaged(
        dirPath = massiveDir.absolutePath,
        page = 0,
        pageSize = 40,
        sortOption = com.example.data.model.SortOption.NAME_ASC,
        showHidden = false
      )
      val latencyMs = System.currentTimeMillis() - startMs

      // Only 40 items in page slice
      assertEquals(40, page0.items.size)
      assertEquals(1000, page0.totalCount)
      assertTrue(page0.hasMore)
      assertEquals("file_0001.dat", page0.items.first().name)
      assertEquals("file_0040.dat", page0.items.last().name)

      // Page 1
      val page1 = repo.getFilesPaged(
        dirPath = massiveDir.absolutePath,
        page = 1,
        pageSize = 40,
        sortOption = com.example.data.model.SortOption.NAME_ASC,
        showHidden = false
      )
      assertEquals(40, page1.items.size)
      assertEquals("file_0041.dat", page1.items.first().name)

      massiveDir.deleteRecursively()
    }
  }

  @Test
  fun `verify performance monitor metrics update`() {
    com.example.data.performance.PerformanceMonitor.recordFolderOpen(12L)
    com.example.data.performance.PerformanceMonitor.recordPagedLoad(8L)
    com.example.data.performance.PerformanceMonitor.recordStatCacheHit(5)

    val metrics = com.example.data.performance.PerformanceMonitor.metrics.value
    assertEquals(12L, metrics.lastFolderOpenLatencyMs)
    assertEquals(8L, metrics.lastPagedLoadLatencyMs)
    assertTrue(metrics.diskReadsAvoided >= 5)
  }

  @Test
  fun `verify gallery search and filter decoupling`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.viewmodel.UnifiedViewModel(application)

    // Initially ALL and empty search
    assertEquals("ALL", viewModel.uiState.value.galleryFilter)
    assertEquals(com.example.ui.viewmodel.GalleryDateFilter.ALL, viewModel.uiState.value.galleryDateFilter)
    assertEquals(com.example.ui.viewmodel.GalleryLocationFilter.ALL, viewModel.uiState.value.galleryLocationFilter)
    assertEquals("", viewModel.uiState.value.gallerySearchQuery)

    // Set search query without mutating filters
    viewModel.setGallerySearchQuery("beach")
    assertEquals("beach", viewModel.uiState.value.gallerySearchQuery)
    assertEquals("ALL", viewModel.uiState.value.galleryFilter)

    // Set filters independently
    viewModel.setGalleryFilter("PHOTOS")
    viewModel.setGalleryDateFilter(com.example.ui.viewmodel.GalleryDateFilter.TODAY)
    viewModel.setGalleryLocationFilter(com.example.ui.viewmodel.GalleryLocationFilter.WITH_GPS)

    assertEquals("beach", viewModel.uiState.value.gallerySearchQuery)
    assertEquals("PHOTOS", viewModel.uiState.value.galleryFilter)
    assertEquals(com.example.ui.viewmodel.GalleryDateFilter.TODAY, viewModel.uiState.value.galleryDateFilter)
    assertEquals(com.example.ui.viewmodel.GalleryLocationFilter.WITH_GPS, viewModel.uiState.value.galleryLocationFilter)

    // Clear search leaves filters intact
    viewModel.clearGallerySearch()
    assertEquals("", viewModel.uiState.value.gallerySearchQuery)
    assertEquals("PHOTOS", viewModel.uiState.value.galleryFilter)
    assertEquals(com.example.ui.viewmodel.GalleryDateFilter.TODAY, viewModel.uiState.value.galleryDateFilter)

    // Clear filters leaves search intact
    viewModel.setGallerySearchQuery("sunset")
    viewModel.clearGalleryFilters()
    assertEquals("sunset", viewModel.uiState.value.gallerySearchQuery)
    assertEquals("ALL", viewModel.uiState.value.galleryFilter)
    assertEquals(com.example.ui.viewmodel.GalleryDateFilter.ALL, viewModel.uiState.value.galleryDateFilter)
    assertEquals(com.example.ui.viewmodel.GalleryLocationFilter.ALL, viewModel.uiState.value.galleryLocationFilter)
  }

}
