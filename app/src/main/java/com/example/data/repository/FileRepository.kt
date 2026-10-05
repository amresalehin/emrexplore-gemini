package com.example.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.example.data.local.AppDatabase
import com.example.data.local.BookmarkEntity
import com.example.data.local.ExplorerPreferencesEntity
import com.example.data.local.FavoriteEntity
import com.example.data.local.IndexStatusEntity
import com.example.data.local.IndexedFileEntity
import com.example.data.local.RecentEntity
import com.example.data.local.TrashEntity
import com.example.data.model.CategoryType
import com.example.data.model.FileItem
import com.example.data.model.MediaAlbum
import com.example.data.model.MediaItem
import com.example.data.model.SortOption
import com.example.data.model.StorageStats
import com.example.data.model.ViewMode
import com.example.data.model.ExplorerFilterType
import com.example.data.model.ExplorerDateFilter
import com.example.data.model.ExplorerSizeFilter
import com.example.data.model.ExplorerSearchScope
import com.example.data.model.sortFiles
import com.example.data.performance.IoPriorityCoordinator
import com.example.data.performance.PerformanceMonitor
import com.example.data.operations.FileOperationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class PagedDirectoryResult(
    val items: List<FileItem>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean
)

class FileRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val favoriteDao = db.favoriteDao()
    private val trashDao = db.trashDao()
    private val recentDao = db.recentDao()
    private val bookmarkDao = db.bookmarkDao()
    private val fileIndexDao = db.fileIndexDao()
    private val preferencesDao = db.preferencesDao()
    private val indexStatusDao = db.indexStatusDao()
    private val indexingMutex = Mutex()
    private val folderCache = java.util.concurrent.ConcurrentHashMap<String, List<FileItem>>()

    data class CachedStat(
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean,
        val mimeType: String,
        val extension: String,
        val childCount: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val statCache = java.util.concurrent.ConcurrentHashMap<String, CachedStat>()

    val operationManager = FileOperationManager { affectedPaths ->
        for (dir in affectedPaths) {
            invalidateFolderCache(dir)
        }
    }

    fun invalidateFolderCache(dirPath: String? = null) {
        if (dirPath == null) {
            folderCache.clear()
            statCache.clear()
        } else {
            folderCache.remove(dirPath)
            statCache.keys.removeIf { it.startsWith(dirPath) }
        }
    }

    val favoritesFlow: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()
    val trashFlow: Flow<List<TrashEntity>> = trashDao.getAllTrash()
    val recentsFlow: Flow<List<RecentEntity>> = recentDao.getRecentItems()
    val bookmarksFlow: Flow<List<BookmarkEntity>> = bookmarkDao.getBookmarks()
    val preferencesFlow: Flow<ExplorerPreferencesEntity> = preferencesDao.getPreferencesFlow().map {
        it ?: ExplorerPreferencesEntity()
    }
    val indexStatusFlow: Flow<IndexStatusEntity> = indexStatusDao.getStatusFlow().map {
        it ?: IndexStatusEntity()
    }
    val totalIndexedCountFlow: Flow<Int> = fileIndexDao.getTotalCountFlow()

    suspend fun getPreferences(): ExplorerPreferencesEntity = withContext(Dispatchers.IO) {
        val existing = preferencesDao.getPreferences()
        if (existing == null) {
            val defaultPrefs = ExplorerPreferencesEntity()
            preferencesDao.savePreferences(defaultPrefs)
            defaultPrefs
        } else {
            existing
        }
    }

    suspend fun savePreferences(prefs: ExplorerPreferencesEntity) = withContext(Dispatchers.IO) {
        preferencesDao.savePreferences(prefs)
    }

    suspend fun updateViewMode(viewMode: ViewMode) = withContext(Dispatchers.IO) {
        ensurePreferencesInitialized()
        preferencesDao.updateViewMode(viewMode.name)
    }

    suspend fun updateSortOption(sortOption: SortOption) = withContext(Dispatchers.IO) {
        ensurePreferencesInitialized()
        preferencesDao.updateSortOption(sortOption.name)
    }

    suspend fun updateShowHidden(showHidden: Boolean) = withContext(Dispatchers.IO) {
        ensurePreferencesInitialized()
        preferencesDao.updateShowHidden(showHidden)
    }

    suspend fun updateLastPath(path: String) = withContext(Dispatchers.IO) {
        ensurePreferencesInitialized()
        preferencesDao.updateLastPath(path)
    }

    suspend fun updateGalleryColumns(cols: Int) = withContext(Dispatchers.IO) {
        ensurePreferencesInitialized()
        preferencesDao.updateGalleryColumns(cols.coerceIn(2, 4))
    }

    suspend fun updateFastSearch(enabled: Boolean) = withContext(Dispatchers.IO) {
        ensurePreferencesInitialized()
        preferencesDao.updateFastSearch(enabled)
    }

    suspend fun updateRememberLastDir(remember: Boolean) = withContext(Dispatchers.IO) {
        ensurePreferencesInitialized()
        preferencesDao.updateRememberLastDir(remember)
    }

    suspend fun resetPreferences(): ExplorerPreferencesEntity = withContext(Dispatchers.IO) {
        val defaultPrefs = ExplorerPreferencesEntity()
        preferencesDao.savePreferences(defaultPrefs)
        defaultPrefs
    }

    private suspend fun ensurePreferencesInitialized() {
        if (preferencesDao.getPreferences() == null) {
            preferencesDao.savePreferences(ExplorerPreferencesEntity())
        }
    }

    val rootPath: String
        get() {
            val external = Environment.getExternalStorageDirectory()
            return if (external != null && external.canRead()) {
                external.absolutePath
            } else {
                context.filesDir.absolutePath
            }
        }

    val baseWorkingDir: File
        get() {
            val externalDir = context.getExternalFilesDir(null)
            return externalDir ?: context.filesDir
        }

    suspend fun initializeStorageDefaults() = withContext(Dispatchers.IO) {
        // emrexplore used to populate app-private storage with demo files. Remove those
        // legacy artifacts once, and never create synthetic files in a real user library.
        removeLegacySampleData()
        ensurePreferencesInitialized()

        if (bookmarkDao.getBookmarks().firstOrNull().isNullOrEmpty()) {
            bookmarkDao.addBookmark(BookmarkEntity(rootPath, "Internal Storage", "storage"))
            val dcim = File(rootPath, "DCIM")
            if (dcim.exists()) bookmarkDao.addBookmark(BookmarkEntity(dcim.absolutePath, "DCIM / Photos", "camera"))
            val downloads = File(rootPath, "Download")
            if (downloads.exists()) bookmarkDao.addBookmark(BookmarkEntity(downloads.absolutePath, "Downloads", "download"))
        }
    }

    private suspend fun removeLegacySampleData() {
        val legacyFiles = listOf(
            File(baseWorkingDir, "Camera/Sunset_Horizon_2026.jpg"),
            File(baseWorkingDir, "Camera/Mountain_Peak_Spring.jpg"),
            File(baseWorkingDir, "Camera/Forest_Mist_Morning.jpg"),
            File(baseWorkingDir, "Camera/Ocean_Breeze_Shore.jpg"),
            File(baseWorkingDir, "Screenshots/Screenshot_emrexplore_UI.png"),
            File(baseWorkingDir, "Documents/emrexplore_Overview.txt"),
            File(baseWorkingDir, "Documents/Project_Roadmap.md"),
            File(baseWorkingDir, "Documents/app_config.json"),
            File(baseWorkingDir, "Downloads/Sample_Archive.zip"),
            File(baseWorkingDir, "Music/Chime_Notification.wav"),
            File(baseWorkingDir, ".emrexplore_seeded")
        )
        legacyFiles.forEach { file ->
            try {
                if (file.isFile) file.delete()
                // Remove legacy artifacts from every derived store as well.
                fileIndexDao.deleteByPath(file.absolutePath)
                recentDao.removeRecent(file.absolutePath)
            } catch (_: Exception) { }
        }
        listOf(
            File(baseWorkingDir, "Camera"),
            File(baseWorkingDir, "Screenshots"),
            File(baseWorkingDir, "Documents"),
            File(baseWorkingDir, "Downloads"),
            File(baseWorkingDir, "Music")
        ).forEach { dir ->
            try {
                if (dir.isDirectory && (dir.list()?.isEmpty() == true)) dir.delete()
            } catch (_: Exception) { }
        }
    }

    suspend fun totalIndexedCount(): Int = withContext(Dispatchers.IO) {
        fileIndexDao.getTotalCount()
    }

    suspend fun getAllNonDirectoryFiles(limit: Int = 300): List<IndexedFileEntity> = withContext(Dispatchers.IO) {
        fileIndexDao.getAllNonDirectoryFiles(limit)
    }

    suspend fun indexStorage(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        indexingMutex.withLock {
            val currentStatus = indexStatusDao.getStatus()
            val currentCount = fileIndexDao.getTotalCount()
            if (!force && currentStatus?.isIndexing == true) {
                return@withContext currentCount
            }
            if (!force && currentCount > 0 && currentStatus != null && (System.currentTimeMillis() - currentStatus.lastIndexedTimestamp) < 300_000) {
                return@withContext currentCount
            }

            indexStatusDao.updateStatus(
                IndexStatusEntity(
                    id = 1,
                    isIndexing = true,
                    lastIndexedTimestamp = currentStatus?.lastIndexedTimestamp ?: 0L,
                    totalIndexedCount = currentCount,
                    statusMessage = "Indexing storage..."
                )
            )

            if (force) {
                fileIndexDao.clearIndex()
            }

            val targets = listOf(
                File(rootPath),
                baseWorkingDir
            ).distinctBy { it.absolutePath }

            val batch = mutableListOf<IndexedFileEntity>()
            var indexedTotal = 0

            for (target in targets) {
                scanDirForIndexing(target, batch, maxDepth = Int.MAX_VALUE, currentDepth = 0, onBatchFlushed = { count ->
                    indexedTotal += count
                    indexStatusDao.updateStatus(
                        IndexStatusEntity(
                            id = 1,
                            isIndexing = true,
                            lastIndexedTimestamp = 0L,
                            totalIndexedCount = indexedTotal,
                            statusMessage = "Indexing files ($indexedTotal)..."
                        )
                    )
                })
            }

            if (batch.isNotEmpty()) {
                fileIndexDao.insertAll(batch)
                indexedTotal += batch.size
                batch.clear()
            }

            val finalCount = fileIndexDao.getTotalCount()
            indexStatusDao.updateStatus(
                IndexStatusEntity(
                    id = 1,
                    isIndexing = false,
                    lastIndexedTimestamp = System.currentTimeMillis(),
                    totalIndexedCount = finalCount,
                    statusMessage = "Indexed $finalCount files & folders"
                )
            )

            finalCount
        }
    }

    private suspend fun scanDirForIndexing(
        dir: File,
        batch: MutableList<IndexedFileEntity>,
        maxDepth: Int,
        currentDepth: Int,
        onBatchFlushed: suspend (Int) -> Unit,
        visitedDirectories: MutableSet<String> = mutableSetOf()
    ) {
        if (!dir.exists() || !dir.isDirectory || currentDepth > maxDepth) return
        val canonical = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
        if (!visitedDirectories.add(canonical)) return
        IoPriorityCoordinator.yieldIfInteractive()
        val children = dir.listFiles() ?: return

        for (file in children) {
            val name = file.name
            if (name.startsWith(".") && name != ".trash") continue
            if (name == "Android" || name == "cache") continue

            val isDir = file.isDirectory
            val ext = if (isDir) "" else file.extension.lowercase()
            val mime = if (isDir) "inode/directory" else (MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: inferMime(ext))
            val category = determineCategory(isDir, ext, file.parentFile?.name)
            val childCount = if (isDir) fastChildCount(file) else 0

            batch.add(
                IndexedFileEntity(
                    path = file.absolutePath,
                    name = file.name,
                    parentPath = file.parent ?: "",
                    size = if (isDir) 0L else file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = isDir,
                    mimeType = mime,
                    extension = ext,
                    category = category.name,
                    childCount = childCount,
                    indexedTimestamp = System.currentTimeMillis()
                )
            )

            if (batch.size >= 100) {
                fileIndexDao.insertAll(batch)
                val flushedSize = batch.size
                batch.clear()
                onBatchFlushed(flushedSize)
                IoPriorityCoordinator.yieldIfInteractive()
            }

            if (isDir) {
                scanDirForIndexing(file, batch, maxDepth, currentDepth + 1, onBatchFlushed, visitedDirectories)
            }
        }
    }

    fun determineCategory(isDirectory: Boolean, ext: String, parentName: String?): CategoryType {
        if (isDirectory) return CategoryType.DOCUMENTS
        val lowerExt = ext.lowercase()
        return when {
            lowerExt in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic") -> CategoryType.IMAGES
            lowerExt in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp") -> CategoryType.VIDEOS
            lowerExt in listOf("mp3", "m4a", "wav", "ogg", "flac", "aac") -> CategoryType.AUDIO
            lowerExt in listOf("zip", "rar", "7z", "tar", "gz", "bz2") -> CategoryType.ARCHIVES
            lowerExt in listOf("apk", "xapk", "apks") -> CategoryType.APKS
            parentName?.equals("Download", ignoreCase = true) == true || parentName?.equals("Downloads", ignoreCase = true) == true -> CategoryType.DOWNLOADS
            lowerExt in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "json", "xml", "html", "kt", "java", "py", "log") -> CategoryType.DOCUMENTS
            else -> CategoryType.DOCUMENTS
        }
    }

    suspend fun indexFileOrDir(file: File) = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        if (file.isDirectory) reconcileIndexedDirectory(file) else indexSingleFileOrDirectory(file)
    }

    /**
     * Reconciles a filesystem subtree into the canonical FileRepository index.
     * Brain consumes this index; it never performs its own storage scan.
     */
    private suspend fun reconcileIndexedDirectory(root: File) {
        if (!root.exists() || !root.isDirectory) return

        val visited = mutableSetOf<String>()
        val stack = ArrayDeque<File>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val dir = stack.removeFirst()
            val canonical = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
            if (!visited.add(canonical)) continue

            indexSingleFileOrDirectory(dir)
            val children = dir.listFiles() ?: continue
            val actualPaths = children.asSequence()
                .filterNot { it.name.startsWith(".") }
                .map { it.absolutePath }
                .toSet()

            for (indexed in fileIndexDao.getFilesByParent(dir.absolutePath)) {
                if (indexed.path !in actualPaths) {
                    fileIndexDao.deleteByPathTree(indexed.path, indexed.path)
                }
            }

            for (child in children) {
                if (child.name.startsWith(".")) continue
                indexSingleFileOrDirectory(child)
                if (child.isDirectory) stack.add(child)
            }
        }
    }

    private suspend fun indexSingleFileOrDirectory(file: File) {
        if (!file.exists()) return
        val isDir = file.isDirectory
        val ext = if (isDir) "" else file.extension.lowercase()
        val mime = if (isDir) "inode/directory" else (MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: inferMime(ext))
        val category = determineCategory(isDir, ext, file.parentFile?.name)
        val childCount = if (isDir) fastChildCount(file) else 0

        fileIndexDao.insertOrUpdate(
            IndexedFileEntity(
                path = file.absolutePath,
                name = file.name,
                parentPath = file.parent ?: "",
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                isDirectory = isDir,
                mimeType = mime,
                extension = ext,
                category = category.name,
                childCount = childCount,
                indexedTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeIndexedPath(path: String, isDirectory: Boolean) = withContext(Dispatchers.IO) {
        if (isDirectory) {
            fileIndexDao.deleteByPathTree(path, path)
        } else {
            fileIndexDao.deleteByPath(path)
        }
    }


    suspend fun getCachedFiles(dirPath: String, showHidden: Boolean): List<FileItem>? = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) {
            folderCache.remove(dirPath)
            return@withContext null
        }

        val inMemory = folderCache[dirPath]
        if (inMemory != null) {
            // The filesystem is the source of truth. A cache can become stale when
            // files are created, deleted, renamed, or moved outside the app.
            // Reconcile the cached path set with the directory before serving it.
            val actualPaths = dir.listFiles()
                ?.asSequence()
                ?.filter { showHidden || !it.name.startsWith(".") }
                ?.map { it.absolutePath }
                ?.toSet()
            val cachedPaths = inMemory.map { it.path }.toSet()

            if (actualPaths == null || actualPaths != cachedPaths) {
                folderCache.remove(dirPath)
            } else {
                val valid = inMemory.filter { item ->
                    val file = File(item.path)
                    file.exists() && item.isDirectory == file.isDirectory
                }
                if (valid.size != inMemory.size) {
                    folderCache[dirPath] = valid
                }
                return@withContext if (showHidden) valid else valid.filter { !it.name.startsWith(".") }
            }
        }

        try {
            val entities = fileIndexDao.getFilesByParent(dirPath)
            if (entities.isNotEmpty()) {
                // Room is only a cache. Serve it only when its path set and item types
                // exactly match the live directory; otherwise fall through to disk.
                val actualFiles = dir.listFiles()?.toList() ?: return@withContext null
                val actualPaths = actualFiles
                    .filter { showHidden || !it.name.startsWith(".") }
                    .map { it.absolutePath }
                    .toSet()
                val indexedPaths = entities
                    .filter { showHidden || !it.name.startsWith(".") }
                    .map { it.path }
                    .toSet()
                val indexedTypesMatch = entities
                    .filter { showHidden || !it.name.startsWith(".") }
                    .all { entity -> File(entity.path).let { it.exists() && entity.isDirectory == it.isDirectory } }

                if (actualPaths != indexedPaths || !indexedTypesMatch) {
                    return@withContext null
                }

                val favSet = try { favoriteDao.getAllFavoritePathsSync().toHashSet() } catch (e: Exception) { emptySet() }
                val validEntities = entities.filter { showHidden || !it.name.startsWith(".") }
                if (validEntities.isEmpty()) return@withContext null
                val items = validEntities.map { entity ->
                    FileItem(
                        name = entity.name,
                        path = entity.path,
                        size = entity.size,
                        lastModified = entity.lastModified,
                        isDirectory = entity.isDirectory,
                        mimeType = entity.mimeType,
                        extension = entity.extension,
                        isFavorite = favSet.contains(entity.path),
                        childCount = if (entity.isDirectory) fastChildCount(File(entity.path)) else 0,
                        uri = Uri.fromFile(File(entity.path))
                    )
                }
                folderCache[dirPath] = items
                return@withContext if (showHidden) items else items.filter { !it.name.startsWith(".") }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun getFilesPaged(
        dirPath: String,
        page: Int,
        pageSize: Int = 120,
        sortOption: SortOption = SortOption.NAME_ASC,
        showHidden: Boolean = false
    ): PagedDirectoryResult = IoPriorityCoordinator.withInteractivePriority {
        withContext(Dispatchers.IO) {
            val startTimeMs = System.currentTimeMillis()
            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext PagedDirectoryResult(emptyList(), 0, page, pageSize, false)
            }

            // 1. Check in-memory cache only when it still exactly matches the
            // live directory. Never let stale cached folders/files leak into the UI.
            val cached = folderCache[dirPath]
            val cacheMatchesDisk = if (cached != null) {
                val actualPaths = dir.listFiles()
                    ?.asSequence()
                    ?.filter { showHidden || !it.name.startsWith(".") }
                    ?.map { it.absolutePath }
                    ?.toSet()
                actualPaths != null && actualPaths == cached.map { it.path }.toSet()
            } else {
                false
            }
            if (cached != null && cacheMatchesDisk) {
                PerformanceMonitor.recordFolderCacheHit()
                val filtered = if (showHidden) cached else cached.filter { !it.name.startsWith(".") }
                val refreshed = filtered.map { item ->
                    if (item.isDirectory) item.copy(childCount = fastChildCount(File(item.path))) else item
                }
                val sorted = sortFileList(refreshed, sortOption)
                val pagedItems: List<FileItem>
                val hasMore: Boolean
                val offset = page * pageSize
                pagedItems = if (offset >= sorted.size) emptyList() else sorted.subList(offset, minOf(offset + pageSize, sorted.size))
                hasMore = (offset + pageSize) < sorted.size
                val elapsed = System.currentTimeMillis() - startTimeMs
                if (page == 0) PerformanceMonitor.recordFolderOpen(elapsed)
                PerformanceMonitor.recordPagedLoad(elapsed)
                return@withContext PagedDirectoryResult(
                    items = pagedItems,
                    totalCount = sorted.size,
                    page = page,
                    pageSize = pageSize,
                    hasMore = hasMore
                )
            }

            // Room is an index for search/category work, not live-directory pagination.
            // Pagination must operate on the filesystem so hidden filtering and every sort mode are global.

            // Direct filesystem paging
            val rawFiles = dir.listFiles()?.asSequence()
                ?.filter { showHidden || !it.name.startsWith(".") }
                ?.toList()
                ?: return@withContext PagedDirectoryResult(emptyList(), 0, page, pageSize, false)

            val favSet = try { favoriteDao.getAllFavoritePathsSync().toHashSet() } catch (_: Exception) { emptySet() }
            val allItems = ArrayList<FileItem>(rawFiles.size)
            for (file in rawFiles) {
                if (!file.exists()) continue
                val isDir = file.isDirectory
                val ext = if (isDir) "" else file.extension.lowercase()
                val mime = if (isDir) "inode/directory"
                    else (MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: inferMime(ext))
                allItems += FileItem(
                    name = file.name,
                    path = file.absolutePath,
                    size = if (isDir) 0L else file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = isDir,
                    mimeType = mime,
                    extension = ext,
                    isFavorite = favSet.contains(file.absolutePath),
                    childCount = if (isDir) fastChildCount(file) else 0,
                    uri = Uri.fromFile(file)
                )
            }

            val sorted = sortFileList(allItems, sortOption)
            val totalCount = sorted.size
            val offset = page * pageSize
            if (offset >= totalCount) {
                return@withContext PagedDirectoryResult(emptyList(), totalCount, page, pageSize, false)
            }
            val pagedItems = sorted.subList(offset, minOf(offset + pageSize, totalCount))
            val elapsed = System.currentTimeMillis() - startTimeMs
            if (page == 0) PerformanceMonitor.recordFolderOpen(elapsed)
            PerformanceMonitor.recordPagedLoad(elapsed)
            return@withContext PagedDirectoryResult(
                items = pagedItems,
                totalCount = totalCount,
                page = page,
                pageSize = pageSize,
                hasMore = offset + pageSize < totalCount
            )
        }
    }

    private fun sortFileList(items: List<FileItem>, sortOption: SortOption): List<FileItem> {
        val dirs = items.filter { it.isDirectory }
        val nonDirs = items.filter { !it.isDirectory }

        val sortComparator: Comparator<FileItem> = when (sortOption) {
            SortOption.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortOption.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortOption.SIZE_ASC -> compareBy { it.size }
            SortOption.SIZE_DESC -> compareByDescending { it.size }
            SortOption.DATE_ASC -> compareBy { it.lastModified }
            SortOption.DATE_DESC -> compareByDescending { it.lastModified }
            SortOption.TYPE -> compareBy<FileItem> { it.extension }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }

        return dirs.sortedWith(sortComparator) + nonDirs.sortedWith(sortComparator)
    }

    suspend fun getFiles(dirPath: String, showHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        val files = dir.listFiles() ?: return@withContext emptyList()
        val favSet = try { favoriteDao.getAllFavoritePathsSync().toHashSet() } catch (e: Exception) { emptySet() }

        val allItems = ArrayList<FileItem>(files.size)
        val entitiesToBatch = ArrayList<IndexedFileEntity>(files.size)

        for (file in files) {
            val isDir = file.isDirectory
            val name = file.name
            val ext = if (isDir) "" else file.extension.lowercase()
            val mime = if (isDir) {
                "inode/directory"
            } else {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: inferMime(ext)
            }
            val childCount = if (isDir) fastChildCount(file) else 0
            val isFav = favSet.contains(file.absolutePath)
            val size = if (isDir) 0L else file.length()
            val lastModified = file.lastModified()

            val item = FileItem(
                name = name,
                path = file.absolutePath,
                size = size,
                lastModified = lastModified,
                isDirectory = isDir,
                mimeType = mime,
                extension = ext,
                isFavorite = isFav,
                childCount = childCount,
                uri = Uri.fromFile(file)
            )
            allItems.add(item)

            entitiesToBatch.add(
                IndexedFileEntity(
                    path = file.absolutePath,
                    name = name,
                    parentPath = dirPath,
                    size = size,
                    lastModified = lastModified,
                    isDirectory = isDir,
                    mimeType = mime,
                    extension = ext,
                    category = determineCategory(isDir, ext, dir.name).name,
                    childCount = childCount,
                    indexedTimestamp = System.currentTimeMillis()
                )
            )
        }

        folderCache[dirPath] = allItems

        if (entitiesToBatch.isNotEmpty()) {
            try {
                fileIndexDao.insertAll(entitiesToBatch)
            } catch (e: Exception) {
                // ignore
            }
        }

        if (showHidden) allItems else allItems.filter { !it.name.startsWith(".") }
    }

    private fun fastChildCount(dir: File): Int {
        val name = dir.name
        if (name.equals("Android", ignoreCase = true) || name.equals("data", ignoreCase = true) || name.equals("obb", ignoreCase = true)) {
            return -1
        }
        return try {
            dir.list()?.size ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    private suspend fun toFileItem(file: File, favSet: Set<String>? = null): FileItem {
        val isDir = file.isDirectory
        val ext = if (isDir) "" else file.extension.lowercase()
        val mime = if (isDir) {
            "inode/directory"
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: inferMime(ext)
        }

        val childCount = if (isDir) fastChildCount(file) else 0

        val isFav = favSet?.contains(file.absolutePath) ?: favoriteDao.isFavoriteSync(file.absolutePath)

        return FileItem(
            name = file.name,
            path = file.absolutePath,
            size = if (isDir) 0L else file.length(),
            lastModified = file.lastModified(),
            isDirectory = isDir,
            mimeType = mime,
            extension = ext,
            isFavorite = isFav,
            childCount = childCount,
            uri = Uri.fromFile(file)
        )
    }

    private fun inferMime(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "txt", "md", "log" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    // MediaStore & App Directory Gallery Items
    private fun getMediaCategoryFiles(
        mediaType: Int,
        favSet: Set<String>,
        limit: Int = 300
    ): List<FileItem> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
        val args = arrayOf(mediaType.toString())
        val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC, " +
            MediaStore.Files.FileColumns._ID + " DESC"
        val uri = MediaStore.Files.getContentUri("external")

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val queryArgs = android.os.Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, 0)
            }
            context.contentResolver.query(uri, projection, queryArgs, null)
        } else {
            context.contentResolver.query(
                uri, projection, selection, args, "$sortOrder LIMIT $limit"
            )
        }

        val result = mutableListOf<FileItem>()
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

            while (it.moveToNext()) {
                val rowId = it.getLong(idCol)
                val path = if (dataCol >= 0) it.getString(dataCol) ?: "" else ""
                val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val mediaUri = if (isVideo) {
                    ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId
                    )
                } else {
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rowId
                    )
                }
                result += FileItem(
                    name = it.getString(nameCol) ?: "Media_$rowId",
                    path = path,
                    size = it.getLong(sizeCol),
                    lastModified = it.getLong(dateCol) * 1000L,
                    isDirectory = false,
                    mimeType = it.getString(mimeCol)
                        ?: if (isVideo) "video/*" else "image/*",
                    extension = File(path).extension.lowercase(),
                    isFavorite = favSet.contains(path),
                    uri = mediaUri
                )
            }
        }
        return result
    }

    suspend fun getFilesByCategory(category: CategoryType): List<FileItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FileItem>()
        val favSet = try { favoriteDao.getAllFavoritePathsSync().toHashSet() } catch (e: Exception) { emptySet() }

        when (category) {
            CategoryType.IMAGES -> {
                result.addAll(
                    getMediaCategoryFiles(
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE,
                        favSet
                    )
                )
                val imgDirs = listOf(
                    File(rootPath, "DCIM"),
                    File(rootPath, "Pictures"),
                    File(baseWorkingDir, "Camera"),
                    File(baseWorkingDir, "Screenshots"),
                    File(baseWorkingDir, "Pictures")
                ).filter { it.exists() }
                for (dir in imgDirs) {
                    scanFilesRecursively(dir, CategoryType.IMAGES, result, maxDepth = 3, currentDepth = 0, favSet = favSet)
                }
            }
            CategoryType.VIDEOS -> {
                result.addAll(
                    getMediaCategoryFiles(
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                        favSet
                    )
                )
                val vidDirs = listOf(
                    File(rootPath, "DCIM"),
                    File(rootPath, "Movies"),
                    File(rootPath, "Video"),
                    File(rootPath, "Videos"),
                    File(baseWorkingDir, "Movies"),
                    File(baseWorkingDir, "Videos")
                ).filter { it.exists() }
                for (dir in vidDirs) {
                    scanFilesRecursively(dir, CategoryType.VIDEOS, result, maxDepth = 3, currentDepth = 0, favSet = favSet)
                }
            }
            CategoryType.AUDIO -> {
                try {
                    val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    val proj = arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.DATE_ADDED,
                        MediaStore.Audio.Media.MIME_TYPE
                    )
                    context.contentResolver.query(audioUri, proj, null, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
                        val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                        val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                        val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                        val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                        val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                        val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val path = cursor.getString(dataCol) ?: ""
                            val name = cursor.getString(nameCol) ?: "Audio_$id"
                            result.add(
                                FileItem(
                                    name = name,
                                    path = path,
                                    size = cursor.getLong(sizeCol),
                                    lastModified = cursor.getLong(dateCol) * 1000,
                                    isDirectory = false,
                                    mimeType = cursor.getString(mimeCol) ?: "audio/mpeg",
                                    extension = File(path).extension.lowercase(),
                                    isFavorite = favSet.contains(path),
                                    uri = ContentUris.withAppendedId(audioUri, id)
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val audioDirs = listOf(
                    File(rootPath, "Music"),
                    File(rootPath, "Audio"),
                    File(rootPath, "Podcasts"),
                    File(rootPath, "Ringtones"),
                    File(baseWorkingDir, "Music"),
                    File(baseWorkingDir, "Audio")
                ).filter { it.exists() }
                for (dir in audioDirs) {
                    scanFilesRecursively(dir, CategoryType.AUDIO, result, maxDepth = 3, currentDepth = 0, favSet = favSet)
                }
            }
            CategoryType.DOWNLOADS -> {
                val dlDirs = listOf(
                    File(rootPath, "Download"),
                    File(rootPath, "Downloads"),
                    File(baseWorkingDir, "Download"),
                    File(baseWorkingDir, "Downloads")
                ).filter { it.exists() }
                for (dir in dlDirs) {
                    dir.listFiles()?.filter { !it.name.startsWith(".") && it.isFile }?.forEach { f ->
                        result.add(toFileItem(f, favSet))
                    }
                }
            }
            CategoryType.DOCUMENTS -> {
                val docDirs = listOf(
                    File(rootPath, "Documents"),
                    File(rootPath, "Download"),
                    File(rootPath, "Downloads"),
                    File(baseWorkingDir, "Documents"),
                    File(baseWorkingDir, "Download"),
                    File(baseWorkingDir, "Downloads"),
                    baseWorkingDir
                ).filter { it.exists() }
                for (dir in docDirs) {
                    scanFilesRecursively(dir, CategoryType.DOCUMENTS, result, maxDepth = 3, currentDepth = 0, favSet = favSet)
                }
            }
            CategoryType.ARCHIVES -> {
                val archDirs = listOf(
                    File(rootPath, "Download"),
                    File(rootPath, "Downloads"),
                    File(rootPath, "Documents"),
                    File(baseWorkingDir, "Download"),
                    File(baseWorkingDir, "Downloads"),
                    baseWorkingDir
                ).filter { it.exists() }
                for (dir in archDirs) {
                    scanFilesRecursively(dir, CategoryType.ARCHIVES, result, maxDepth = 3, currentDepth = 0, favSet = favSet)
                }
            }
            CategoryType.APKS -> {
                val apkDirs = listOf(
                    File(rootPath, "Download"),
                    File(rootPath, "Downloads"),
                    File(rootPath, "Documents"),
                    File(baseWorkingDir, "Download"),
                    File(baseWorkingDir, "Downloads"),
                    baseWorkingDir
                ).filter { it.exists() }
                for (dir in apkDirs) {
                    scanFilesRecursively(dir, CategoryType.APKS, result, maxDepth = 3, currentDepth = 0, favSet = favSet)
                }
            }
        }

        // Clean & Deduplicate: only keep existing non-directory files
        result.filter { item ->
            !item.isDirectory && !item.name.startsWith(".") && !item.path.contains("/.trash/") &&
                    (item.uri != null || (item.path.isNotBlank() && File(item.path).exists()))
        }.distinctBy {
            if (it.path.isNotBlank()) it.path else it.uri?.toString() ?: it.name
        }.sortedByDescending { it.lastModified }
    }

    suspend fun getCategoryCounts(): Map<CategoryType, Int> = withContext(Dispatchers.IO) {
        val counts = mutableMapOf<CategoryType, Int>()
        for (cat in CategoryType.entries) {
            counts[cat] = getFilesByCategory(cat).size
        }
        counts
    }

    suspend fun getCategorySizes(): Map<CategoryType, Long> = withContext(Dispatchers.IO) {
        val sizes = mutableMapOf<CategoryType, Long>()
        for (cat in CategoryType.entries) {
            sizes[cat] = getFilesByCategory(cat).sumOf { it.size }
        }
        sizes
    }

    suspend fun searchFiles(query: String, category: CategoryType? = null): List<FileItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val prefs = getPreferences()
        if (prefs.enableFastRoomSearch) {
            val indexed = searchIndexedFiles(query, category)
            if (indexed.isNotEmpty()) {
                return@withContext indexed
            }
        }

        val q = query.trim().lowercase()
        val result = mutableListOf<FileItem>()
        val favSet = try { favoriteDao.getAllFavoritePathsSync().toHashSet() } catch (e: Exception) { emptySet() }
        val rootsToScan = listOf(
            File(rootPath),
            baseWorkingDir
        ).distinctBy { it.absolutePath }

        for (root in rootsToScan) {
            scanFilesForSearch(root, q, category, result, maxDepth = 3, currentDepth = 0, favSet = favSet)
            if (result.size >= 100) break
        }

        // Prioritize exact/prefix matches first, then contains, sorted by recent date
        result.distinctBy { it.path }.sortedWith(
            compareByDescending<FileItem> { it.name.lowercase().startsWith(q) }
                .thenByDescending { it.lastModified }
        ).take(100)
    }

    suspend fun searchIndexedFiles(query: String, category: CategoryType? = null): List<FileItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim()
        val favSet = try { favoriteDao.getAllFavoritePathsSync().toHashSet() } catch (e: Exception) { emptySet() }
        val entities = if (category != null) {
            fileIndexDao.searchFilesByCategory(q, category.name, limit = 150)
        } else {
            fileIndexDao.searchFiles(q, limit = 150)
        }

        entities.mapNotNull { entity ->
            val file = File(entity.path)
            if (!file.exists() || file.isDirectory != entity.isDirectory) return@mapNotNull null
            FileItem(
                name = entity.name,
                path = entity.path,
                size = entity.size,
                lastModified = entity.lastModified,
                isDirectory = entity.isDirectory,
                mimeType = entity.mimeType,
                extension = entity.extension,
                isFavorite = favSet.contains(entity.path),
                childCount = entity.childCount,
                uri = Uri.fromFile(file)
            )
        }
    }

    private fun escapeSqlLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    suspend fun searchExplorer(
        dirPath: String,
        query: String,
        scope: ExplorerSearchScope,
        filterType: ExplorerFilterType,
        dateFilter: ExplorerDateFilter,
        sizeFilter: ExplorerSizeFilter,
        sortOption: SortOption,
        showHidden: Boolean
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        val favSet = try { favoriteDao.getAllFavoritePathsSync().toHashSet() } catch (e: Exception) { emptySet() }

        // Date boundaries
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -7)
        val sevenDaysAgo = cal.timeInMillis

        cal.timeInMillis = now
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        cal.set(Calendar.DAY_OF_YEAR, 1)
        val startOfYear = cal.timeInMillis

        val filterPredicate: (FileItem) -> Boolean = { item ->
            val passesHidden = showHidden || (!item.name.startsWith(".") && item.name != ".trash")
            val passesQuery = q.isEmpty() || item.name.lowercase().contains(q)
            val passesType = when (filterType) {
                ExplorerFilterType.ALL -> true
                ExplorerFilterType.FOLDERS -> item.isDirectory
                ExplorerFilterType.DOCUMENTS -> !item.isDirectory && item.isDocument
                ExplorerFilterType.IMAGES -> !item.isDirectory && item.isImage
                ExplorerFilterType.VIDEOS -> !item.isDirectory && item.isVideo
                ExplorerFilterType.AUDIO -> !item.isDirectory && item.isAudio
                ExplorerFilterType.ARCHIVES -> !item.isDirectory && item.isArchive
                ExplorerFilterType.APKS -> !item.isDirectory && item.isApk
            }
            val passesDate = when (dateFilter) {
                ExplorerDateFilter.ALL -> true
                ExplorerDateFilter.TODAY -> item.lastModified >= startOfToday
                ExplorerDateFilter.LAST_7_DAYS -> item.lastModified >= sevenDaysAgo
                ExplorerDateFilter.THIS_MONTH -> item.lastModified >= startOfMonth
                ExplorerDateFilter.THIS_YEAR -> item.lastModified >= startOfYear
            }
            val passesSize = if (item.isDirectory) {
                true
            } else {
                when (sizeFilter) {
                    ExplorerSizeFilter.ALL -> true
                    ExplorerSizeFilter.SMALL -> item.size < 1_048_576L
                    ExplorerSizeFilter.MEDIUM -> item.size in 1_048_576L..52_428_800L
                    ExplorerSizeFilter.LARGE -> item.size > 52_428_800L
                }
            }
            passesHidden && passesQuery && passesType && passesDate && passesSize
        }

        val rawItems = mutableListOf<FileItem>()
        val targetDir = if (dirPath.isBlank() || dirPath == "/") File(rootPath) else File(dirPath)
        val effectiveDir = if (targetDir.exists() && targetDir.isDirectory) {
            targetDir
        } else if (File(rootPath).exists()) {
            File(rootPath)
        } else {
            baseWorkingDir
        }

        when (scope) {
            ExplorerSearchScope.CURRENT_FOLDER -> {
                val files = effectiveDir.listFiles() ?: emptyArray()
                for (file in files) {
                    val item = toFileItem(file, favSet)
                    if (filterPredicate(item)) {
                        rawItems.add(item)
                    }
                }
                // If effectiveDir is root and has no matching direct items, also check baseWorkingDir direct children
                if (effectiveDir.absolutePath == rootPath && rawItems.isEmpty() && baseWorkingDir.exists()) {
                    val baseFiles = baseWorkingDir.listFiles() ?: emptyArray()
                    for (file in baseFiles) {
                        val item = toFileItem(file, favSet)
                        if (filterPredicate(item)) {
                            rawItems.add(item)
                        }
                    }
                }
            }

            ExplorerSearchScope.SUBFOLDERS -> {
                val prefs = getPreferences()
                var loadedFromRoom = false
                val escapedPrefix = escapeSqlLike(effectiveDir.absolutePath + File.separator)
                val escapedQuery = escapeSqlLike(q)

                

            ExplorerSearchScope.ALL_STORAGE -> {
                val prefs = getPreferences()
                var loadedFromRoom = false
                if (prefs.enableFastRoomSearch) {
                    val catString = when (filterType) {
                        ExplorerFilterType.IMAGES -> "IMAGES"
                        ExplorerFilterType.VIDEOS -> "VIDEOS"
                        ExplorerFilterType.AUDIO -> "AUDIO"
                        ExplorerFilterType.DOCUMENTS -> "DOCUMENTS"
                        ExplorerFilterType.ARCHIVES -> "ARCHIVES"
                        ExplorerFilterType.APKS -> "APKS"
                        else -> null
                    }
                    val entities = if (catString != null) {
                        fileIndexDao.searchFilesByCategory(q, catString, limit = 250)
                    } else if (q.isNotEmpty()) {
                        fileIndexDao.searchFiles(q, limit = 250)
                    } else {
                        emptyList()
                    }
                    if (entities.isNotEmpty()) {
                        entities.forEach { entity ->
                            val item = FileItem(
                                name = entity.name,
                                path = entity.path,
                                size = entity.size,
                                lastModified = entity.lastModified,
                                isDirectory = entity.isDirectory,
                                mimeType = entity.mimeType,
                                extension = entity.extension,
                                isFavorite = favSet.contains(entity.path),
                                childCount = entity.childCount,
                                uri = Uri.fromFile(File(entity.path))
                            )
                            if (filterPredicate(item)) {
                                rawItems.add(item)
                            }
                        }
                        if (rawItems.isNotEmpty()) {
                            loadedFromRoom = true
                        }
                    }
                }

                if (!loadedFromRoom) {
                    val roots = listOf(File(rootPath), baseWorkingDir).distinctBy { it.absolutePath }
                    for (root in roots) {
                        scanDirectoryRecursive(root, filterPredicate, favSet, rawItems, maxDepth = Int.MAX_VALUE, currentDepth = 0)
                        if (rawItems.size >= 250) break
                    }
                }
            }
        }

        val distinctItems = rawItems.distinctBy { it.path }
        sortFiles(distinctItems, sortOption)
    }

    private suspend fun scanDirectoryRecursive(
        dir: File,
        predicate: (FileItem) -> Boolean,
        favSet: Set<String>,
        outList: MutableList<FileItem>,
        maxDepth: Int,
        currentDepth: Int
    ) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory || outList.size >= 250) return
        val list = dir.listFiles() ?: return
        for (file in list) {
            if (outList.size >= 250) return
            val name = file.name
            if (name.startsWith(".") && name != ".trash") continue
            val item = toFileItem(file, favSet)
            if (file.isDirectory) {
                if (name == ".trash" || name == "cache") continue
                if (name == "Android") {
                    if (baseWorkingDir.absolutePath.startsWith(file.absolutePath)) {
                        scanDirectoryRecursive(baseWorkingDir, predicate, favSet, outList, maxDepth, currentDepth + 1)
                    }
                    continue
                }
                if (predicate(item)) {
                    outList.add(item)
                }
                scanDirectoryRecursive(file, predicate, favSet, outList, maxDepth, currentDepth + 1)
            } else {
                if (predicate(item)) {
                    outList.add(item)
                }
            }
        }
    }


    private suspend fun scanFilesForSearch(
        dir: File,
        query: String,
        category: CategoryType?,
        outList: MutableList<FileItem>,
        maxDepth: Int,
        currentDepth: Int,
        favSet: Set<String>
    ) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory || outList.size >= 100) return
        val list = dir.listFiles() ?: return

        for (file in list) {
            if (outList.size >= 100) return
            val name = file.name
            if (name.startsWith(".") && name != ".trash") {
                continue
            }
            if (file.isDirectory) {
                if (name != "Android" && name != ".trash" && name != "cache") {
                    if (category == null && name.lowercase().contains(query)) {
                        outList.add(toFileItem(file, favSet))
                    }
                    scanFilesForSearch(file, query, category, outList, maxDepth, currentDepth + 1, favSet)
                }
            } else {
                if (name.lowercase().contains(query)) {
                    val item = toFileItem(file, favSet)
                    val matchesCategory = if (category == null) {
                        true
                    } else {
                        when (category) {
                            CategoryType.IMAGES -> item.isImage
                            CategoryType.VIDEOS -> item.isVideo
                            CategoryType.AUDIO -> item.isAudio
                            CategoryType.DOCUMENTS -> item.isDocument
                            CategoryType.ARCHIVES -> item.isArchive
                            CategoryType.APKS -> item.isApk
                            CategoryType.DOWNLOADS -> file.parentFile?.name.equals("Download", ignoreCase = true) ||
                                                      file.parentFile?.name.equals("Downloads", ignoreCase = true)
                        }
                    }
                    if (matchesCategory) {
                        outList.add(item)
                    }
                }
            }
        }
    }

    private suspend fun scanFilesRecursively(
        dir: File,
        category: CategoryType,
        outList: MutableList<FileItem>,
        maxDepth: Int,
        currentDepth: Int,
        favSet: Set<String>? = null
    ) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory) return
        val list = dir.listFiles() ?: return

        for (file in list) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".") && file.name != "Android") {
                    scanFilesRecursively(file, category, outList, maxDepth, currentDepth + 1, favSet)
                }
            } else {
                val item = toFileItem(file, favSet)
                val matches = when (category) {
                    CategoryType.IMAGES -> item.isImage
                    CategoryType.VIDEOS -> item.isVideo
                    CategoryType.AUDIO -> item.isAudio
                    CategoryType.DOCUMENTS -> item.isDocument
                    CategoryType.ARCHIVES -> item.isArchive
                    CategoryType.APKS -> item.isApk
                    CategoryType.DOWNLOADS -> file.parentFile?.name.equals("Download", ignoreCase = true) ||
                                              file.parentFile?.name.equals("Downloads", ignoreCase = true)
                }
                if (matches) {
                    outList.add(item)
                }
            }
        }
    }

    // Storage Statistics (Accurate device and category size calculation)
    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val external = Environment.getExternalStorageDirectory()
        val targetPath = if (external != null && external.canRead()) external.path else context.filesDir.path
        val stat = try { StatFs(targetPath) } catch (e: Exception) { StatFs(Environment.getDataDirectory().path) }
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val total = totalBlocks * blockSize
        val free = availableBlocks * blockSize
        val used = (total - free).coerceAtLeast(0L)

        val imgBytes = getFilesByCategory(CategoryType.IMAGES).sumOf { it.size }
        val vidBytes = getFilesByCategory(CategoryType.VIDEOS).sumOf { it.size }
        val audBytes = getFilesByCategory(CategoryType.AUDIO).sumOf { it.size }
        val docBytes = getFilesByCategory(CategoryType.DOCUMENTS).sumOf { it.size }
        val archBytes = getFilesByCategory(CategoryType.ARCHIVES).sumOf { it.size }
        val apkBytes = getFilesByCategory(CategoryType.APKS).sumOf { it.size }

        val categorizedSum = imgBytes + vidBytes + audBytes + docBytes + archBytes + apkBytes
        val other = (used - categorizedSum).coerceAtLeast(0L)

        StorageStats(
            totalBytes = total,
            freeBytes = free,
            usedBytes = used,
            imagesBytes = imgBytes,
            videosBytes = vidBytes,
            audioBytes = audBytes,
            documentsBytes = docBytes,
            archivesBytes = archBytes,
            apksBytes = apkBytes,
            otherBytes = other
        )
    }

    private fun queryMediaStoreCount(uri: Uri): Int {
        return try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use {
                it.count
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun queryMediaStoreSumSize(uri: Uri): Long {
        var sum = 0L
        try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (sizeCol != -1) {
                    while (cursor.moveToNext()) {
                        sum += cursor.getLong(sizeCol)
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return sum
    }

    private fun countFilesWithExtensions(dir: File, exts: Set<String>): Int {
        if (!dir.exists()) return 0
        var count = 0
        dir.walkTopDown().maxDepth(Int.MAX_VALUE).forEach { f ->
            if (f.isFile && f.extension.lowercase() in exts) count++
        }
        return count
    }

    private fun sumFileSizeInDir(dir: File, exts: Set<String>?): Long {
        if (!dir.exists()) return 0L
        var sum = 0L
        dir.walkTopDown().maxDepth(3).forEach { f ->
            if (f.isFile && (exts == null || f.extension.lowercase() in exts)) {
                sum += f.length()
            }
        }
        return sum
    }

    // CRUD & File Operations
    suspend fun createFolder(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidChildName(name)) return@withContext false
        val dir = File(parentPath, name)
        val created = if (!dir.exists()) dir.mkdirs() else false
        if (created) {
            invalidateFolderCache(parentPath)
            indexFileOrDir(dir)
        }
        created
    }

    suspend fun createTextFile(parentPath: String, name: String, content: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (!isValidChildName(name)) return@withContext false
        val file = File(parentPath, name)
        if (!file.exists()) {
            val created = file.createNewFile()
            if (!created) return@withContext false
            if (content.isNotEmpty()) {
                file.writeText(content)
            }
            invalidateFolderCache(parentPath)
            indexFileOrDir(file)
            true
        } else false
    }

    suspend fun renameFile(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidChildName(newName)) return@withContext false
        val oldFile = File(oldPath)
        if (!oldFile.exists()) return@withContext false
        val parent = oldFile.parent ?: ""
        val newFile = File(oldFile.parentFile, newName)
        if (oldFile.canonicalFile == newFile.canonicalFile) return@withContext true
        if (newFile.exists()) return@withContext false
        val isDir = oldFile.isDirectory
        val renamed = oldFile.renameTo(newFile)
        if (renamed) {
            invalidateFolderCache(parent)
            removeIndexedPath(oldPath, isDir)
            indexFileOrDir(newFile)
        }
        renamed
    }

    private fun isUnsafeDestination(source: File, destination: File): Boolean {
        return runCatching {
            val canonicalSource = source.canonicalFile
            val canonicalDestination = destination.canonicalFile
            canonicalSource == canonicalDestination ||
                (source.isDirectory && canonicalDestination.absolutePath.startsWith(canonicalSource.absolutePath + File.separator))
        }.getOrDefault(true)
    }

    private fun isValidChildName(name: String): Boolean {
        return name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            !File(name).isAbsolute &&
            '/' !in name &&
            '\\' !in name
    }

    private fun moveAcrossFilesystemsSafely(source: File, destination: File): Boolean {
        if (!source.exists()) return false
        destination.parentFile?.mkdirs()
        if (source.renameTo(destination)) return true
        return try {
            val sourceSize = if (source.isFile) source.length() else -1L
            val copied = if (source.isDirectory) {
                source.copyRecursively(destination, overwrite = false)
            } else {
                source.copyTo(destination, overwrite = false)
                true
            }
            if (!copied || !destination.exists()) return false
            if (source.isFile && (destination.length() != sourceSize)) {
                try { destination.delete() } catch (_: Exception) { }
                return false
            }
            val deleted = if (source.isDirectory) source.deleteRecursively() else source.delete()
            if (!deleted) {
                if (destination.isDirectory) destination.deleteRecursively() else destination.delete()
                false
            } else true
        } catch (_: Exception) {
            if (destination.exists()) {
                try { if (destination.isDirectory) destination.deleteRecursively() else destination.delete() } catch (_: Exception) { }
            }
            false
        }
    }
    suspend fun deleteFile(path: String, toTrash: Boolean): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext false
        val parent = file.parent ?: ""
        val isDir = file.isDirectory

        if (toTrash) {
            val trashDir = File(baseWorkingDir, ".trash").apply { mkdirs() }
            val targetTrashFile = File(trashDir, "${System.currentTimeMillis()}_${file.name}")
            val success = moveAcrossFilesystemsSafely(file, targetTrashFile)
            if (success) {
                invalidateFolderCache(parent)
                removeIndexedPath(path, isDir)
                trashDao.insertTrash(
                    TrashEntity(
                        originalPath = path,
                        trashPath = targetTrashFile.absolutePath,
                        name = file.name,
                        isDirectory = isDir,
                        size = targetTrashFile.length(),
                        mimeType = inferMime(file.extension)
                    )
                )
            }
            success
        } else {
            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (deleted) {
                invalidateFolderCache(parent)
                removeIndexedPath(path, isDir)
            }
            deleted
        }
    }

    suspend fun restoreTrashItem(trashEntity: TrashEntity): Boolean = withContext(Dispatchers.IO) {
        val trashFile = File(trashEntity.trashPath)
        val origFile = File(trashEntity.originalPath)
        origFile.parentFile?.mkdirs()

        val success = if (trashFile.exists()) {
            moveAcrossFilesystemsSafely(trashFile, origFile)
        } else false

        if (success) {
            trashDao.deleteTrashById(trashEntity.id)
            origFile.parent?.let { invalidateFolderCache(it) }
            indexFileOrDir(origFile)
        }
        success
    }

    suspend fun permanentlyDeleteTrash(trashEntity: TrashEntity): Boolean = withContext(Dispatchers.IO) {
        val trashFile = File(trashEntity.trashPath)
        val deleted = !trashFile.exists() || trashFile.deleteRecursively()
        if (!deleted) return@withContext false

        trashDao.deleteTrashById(trashEntity.id)
        true
    }

    suspend fun clearTrash(): Boolean = withContext(Dispatchers.IO) {
        val trashDir = File(baseWorkingDir, ".trash")
        val deleted = !trashDir.exists() || trashDir.deleteRecursively()
        if (!deleted) return@withContext false

        trashDao.clearAllTrash()
        true
    }

    suspend fun copyFile(sourcePath: String, targetDir: String): Boolean = withContext(Dispatchers.IO) {
        val src = File(sourcePath)
        val dest = File(targetDir, src.name)
        if (!src.exists()) return@withContext false
        if (isUnsafeDestination(src, dest)) return@withContext false

        try {
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = true)
            } else {
                src.copyTo(dest, overwrite = true)
            }
            invalidateFolderCache(targetDir)
            indexFileOrDir(dest)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun moveFile(sourcePath: String, targetDir: String): Boolean = withContext(Dispatchers.IO) {
        val src = File(sourcePath)
        val dest = File(targetDir, src.name)
        if (!src.exists()) return@withContext false
        if (isUnsafeDestination(src, dest)) return@withContext false
        val parent = src.parent ?: ""
        val isDir = src.isDirectory

        try {
            val moved = moveAcrossFilesystemsSafely(src, dest)
            if (!moved) return@withContext false

            invalidateFolderCache(parent)
            invalidateFolderCache(targetDir)
            removeIndexedPath(sourcePath, isDir)
            indexFileOrDir(dest)
            if (dest.isDirectory) {
                val batch = mutableListOf<IndexedFileEntity>()
                scanDirForIndexing(dest, batch, Int.MAX_VALUE, 0, onBatchFlushed = {})
                if (batch.isNotEmpty()) fileIndexDao.insertAll(batch)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    suspend fun zipFiles(sourcePaths: List<String>, targetZipPath: String): Boolean = withContext(Dispatchers.IO) {
        val zipFile = File(targetZipPath)
        val canonicalZip = runCatching { zipFile.canonicalFile }.getOrNull() ?: return@withContext false
        val selfTargeted = sourcePaths.any { sourcePath ->
            val source = File(sourcePath)
            val canonicalSource = runCatching { source.canonicalFile }.getOrNull() ?: return@any true
            canonicalSource == canonicalZip ||
                (source.isDirectory && canonicalZip.absolutePath.startsWith(canonicalSource.absolutePath + File.separator))
        }
        if (selfTargeted) return@withContext false
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for (path in sourcePaths) {
                    val file = File(path)
                    addToZip(file, file.name, zos)
                }
            }
            indexFileOrDir(zipFile)
            zipFile.parent?.let { invalidateFolderCache(it) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun addToZip(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                addToZip(child, "$entryName/${child.name}", zos)
            }
        } else {
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            FileInputStream(file).use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    suspend fun listZipEntries(zipPath: String): List<String> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<String>()
        try {
            ZipFile(File(zipPath)).use { zip ->
                val en = zip.entries()
                while (en.hasMoreElements()) {
                    val entry = en.nextElement()
                    val sizeStr = if (entry.isDirectory) "Folder" else "${entry.size / 1024} KB"
                    entries.add("${entry.name} ($sizeStr)")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        entries
    }

    suspend fun extractZip(zipPath: String, destDir: String): Boolean = withContext(Dispatchers.IO) {
        val zipFile = File(zipPath)
        val target = File(destDir).apply { mkdirs() }
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val targetCanonical = target.canonicalFile
                while (entry != null) {
                    val outFile = File(target, entry.name)
                    val outCanonical = try { outFile.canonicalFile } catch (_: IOException) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    val rootPath = targetCanonical.path + File.separator
                    if (outCanonical.path != targetCanonical.path && !outCanonical.path.startsWith(rootPath)) {
                        throw IOException("Unsafe ZIP entry path: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val batch = mutableListOf<IndexedFileEntity>()
            scanDirForIndexing(target, batch, Int.MAX_VALUE, 0, onBatchFlushed = {})
            if (batch.isNotEmpty()) fileIndexDao.insertAll(batch)
            invalidateFolderCache(destDir)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists() && file.isFile) {
            try {
                file.readText()
            } catch (e: Exception) {
                "Unable to read file: ${e.message}"
            }
        } else ""
    }

    suspend fun writeText(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        try {
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun toggleFavorite(fileItem: FileItem): Boolean = withContext(Dispatchers.IO) {
        val isFav = favoriteDao.isFavoriteSync(fileItem.path)
        if (isFav) {
            favoriteDao.removeFavorite(fileItem.path)
            false
        } else {
            favoriteDao.addFavorite(
                FavoriteEntity(
                    path = fileItem.path,
                    name = fileItem.name,
                    isDirectory = fileItem.isDirectory,
                    mimeType = fileItem.mimeType
                )
            )
            true
        }
    }

    suspend fun recordRecent(fileItem: FileItem) = withContext(Dispatchers.IO) {
        if (!fileItem.isDirectory) {
            recentDao.addRecent(
                RecentEntity(
                    path = fileItem.path,
                    name = fileItem.name,
                    mimeType = fileItem.mimeType,
                    size = fileItem.size
                )
            )
        }
    }
}
