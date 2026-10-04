package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC, path ASC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    fun isFavorite(path: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    suspend fun isFavoriteSync(path: String): Boolean

    @Query("SELECT path FROM favorites")
    suspend fun getAllFavoritePathsSync(): List<String>

    @Query("SELECT path FROM favorites ORDER BY timestamp DESC, path ASC LIMIT :limit OFFSET :offset")
    suspend fun getFavoritePathsPage(limit: Int, offset: Int): List<String>

    @Query("SELECT timestamp FROM favorites WHERE path = :path LIMIT 1")
    suspend fun getFavoriteTimestamp(path: String): Long?

    @Query("SELECT COUNT(*) FROM favorites WHERE timestamp > :timestamp OR (timestamp = :timestamp AND path < :path)")
    suspend fun countFavoritesBefore(timestamp: Long, path: String): Int

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoriteCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun removeFavorite(path: String)
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deletedTimestamp DESC")
    fun getAllTrash(): Flow<List<TrashEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrash(item: TrashEntity): Long

    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun deleteTrashById(id: Long)

    @Query("DELETE FROM trash")
    suspend fun clearAllTrash()
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recents ORDER BY lastOpenedTimestamp DESC LIMIT 25")
    fun getRecentItems(): Flow<List<RecentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRecent(recent: RecentEntity)

    @Query("DELETE FROM recents WHERE path = :path")
    suspend fun removeRecent(path: String)

    @Query("DELETE FROM recents")
    suspend fun clearRecents()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks")
    fun getBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE path = :path")
    suspend fun removeBookmark(path: String)
}

data class CategoryStatTuple(
    val category: String,
    val count: Int,
    val totalSize: Long?
)

@Dao
interface FileIndexDao {
    @Query("SELECT * FROM indexed_files ORDER BY lastModified DESC LIMIT 100")
    fun getAllIndexedFiles(): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM indexed_files WHERE name LIKE '%' || :query || '%' ORDER BY isDirectory DESC, name ASC LIMIT :limit")
    suspend fun searchFiles(query: String, limit: Int = 100): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE category = :category AND name LIKE '%' || :query || '%' ORDER BY isDirectory DESC, name ASC LIMIT :limit")
    suspend fun searchFilesByCategory(query: String, category: String, limit: Int = 100): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE (path LIKE :parentPath || '/%' OR parentPath = :parentPath) AND name LIKE '%' || :query || '%' ORDER BY isDirectory DESC, name ASC LIMIT :limit")
    suspend fun searchFilesUnderPath(parentPath: String, query: String, limit: Int = 150): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE (path LIKE :parentPath || '/%' OR parentPath = :parentPath) AND category = :category AND name LIKE '%' || :query || '%' ORDER BY isDirectory DESC, name ASC LIMIT :limit")
    suspend fun searchFilesUnderPathByCategory(parentPath: String, query: String, category: String, limit: Int = 150): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE category = :category ORDER BY lastModified DESC LIMIT :limit")
    suspend fun getFilesByCategory(category: String, limit: Int = 300): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE category = :category ORDER BY lastModified DESC")
    fun getFilesByCategoryFlow(category: String): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath ORDER BY isDirectory DESC, name ASC")
    suspend fun getFilesByParent(parentPath: String): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath ORDER BY isDirectory DESC, name ASC LIMIT :limit OFFSET :offset")
    suspend fun getFilesByParentPaged(parentPath: String, limit: Int, offset: Int): List<IndexedFileEntity>

    @Query("SELECT COUNT(*) FROM indexed_files WHERE parentPath = :parentPath")
    suspend fun getCountByParent(parentPath: String): Int

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath ORDER BY isDirectory DESC, name ASC")
    fun getFilesByParentFlow(parentPath: String): Flow<List<IndexedFileEntity>>

    @Query("SELECT COUNT(*) FROM indexed_files")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM indexed_files")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM indexed_files WHERE category = :category")
    suspend fun getCountByCategory(category: String): Int

    @Query("SELECT category, COUNT(*) as count, SUM(size) as totalSize FROM indexed_files WHERE isDirectory = 0 GROUP BY category")
    fun getCategoryStatsFlow(): Flow<List<CategoryStatTuple>>

    @Query("SELECT category, COUNT(*) as count, SUM(size) as totalSize FROM indexed_files WHERE isDirectory = 0 GROUP BY category")
    suspend fun getCategoryStats(): List<CategoryStatTuple>

    @Query("SELECT * FROM indexed_files WHERE isDirectory = 0 ORDER BY lastModified DESC LIMIT :limit")
    suspend fun getAllNonDirectoryFiles(limit: Int = 300): List<IndexedFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(file: IndexedFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<IndexedFileEntity>)

    @Query("DELETE FROM indexed_files WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM indexed_files WHERE path = :path OR path LIKE :pathPrefix || '/%'")
    suspend fun deleteByPathTree(path: String, pathPrefix: String)

    @Query("DELETE FROM indexed_files")
    suspend fun clearIndex()
}

@Dao
interface PreferencesDao {
    @Query("SELECT * FROM explorer_preferences WHERE id = 1")
    fun getPreferencesFlow(): Flow<ExplorerPreferencesEntity?>

    @Query("SELECT * FROM explorer_preferences WHERE id = 1")
    suspend fun getPreferences(): ExplorerPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreferences(prefs: ExplorerPreferencesEntity)

    @Query("UPDATE explorer_preferences SET viewMode = :viewMode WHERE id = 1")
    suspend fun updateViewMode(viewMode: String)

    @Query("UPDATE explorer_preferences SET sortOption = :sortOption WHERE id = 1")
    suspend fun updateSortOption(sortOption: String)

    @Query("UPDATE explorer_preferences SET showHidden = :showHidden WHERE id = 1")
    suspend fun updateShowHidden(showHidden: Boolean)

    @Query("UPDATE explorer_preferences SET lastDirectoryPath = :path WHERE id = 1")
    suspend fun updateLastPath(path: String)

    @Query("UPDATE explorer_preferences SET galleryColumns = :cols WHERE id = 1")
    suspend fun updateGalleryColumns(cols: Int)

    @Query("UPDATE explorer_preferences SET enableFastRoomSearch = :enable WHERE id = 1")
    suspend fun updateFastSearch(enable: Boolean)

    @Query("UPDATE explorer_preferences SET rememberLastDirectory = :remember WHERE id = 1")
    suspend fun updateRememberLastDir(remember: Boolean)
}

@Dao
interface IndexStatusDao {
    @Query("SELECT * FROM index_status WHERE id = 1")
    fun getStatusFlow(): Flow<IndexStatusEntity?>

    @Query("SELECT * FROM index_status WHERE id = 1")
    suspend fun getStatus(): IndexStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateStatus(status: IndexStatusEntity)
}

@Dao
interface MediaMetadataDao {
    @Query("SELECT * FROM media_metadata WHERE uri = :uri LIMIT 1")
    suspend fun get(uri: String): MediaMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: MediaMetadataEntity)

    @Query("DELETE FROM media_metadata WHERE uri = :uri")
    suspend fun delete(uri: String)
}

@Dao
interface PlaceSearchCacheDao {
    @Query("SELECT * FROM place_search_cache WHERE query = :query LIMIT 1")
    suspend fun get(query: String): PlaceSearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: PlaceSearchCacheEntity)
}

@Dao
interface AiProviderConfigDao {
    @Query("SELECT * FROM ai_provider_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<AiProviderConfigEntity?>

    @Query("SELECT * FROM ai_provider_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): AiProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: AiProviderConfigEntity)
}

@Dao
interface KgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<KgNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdges(edges: List<KgEdgeEntity>)

    @Query("SELECT * FROM kg_nodes ORDER BY degree DESC, updatedAt DESC")
    fun getAllNodesFlow(): Flow<List<KgNodeEntity>>

    @Query("SELECT * FROM kg_edges ORDER BY weight DESC")
    fun getAllEdgesFlow(): Flow<List<KgEdgeEntity>>

    @Query("SELECT * FROM kg_nodes WHERE id = :id LIMIT 1")
    suspend fun getNode(id: String): KgNodeEntity?

    @Query("SELECT * FROM kg_nodes WHERE sourceFilePath = :path LIMIT 1")
    suspend fun getNodeByFilePath(path: String): KgNodeEntity?

    @Query("SELECT * FROM kg_edges WHERE sourceNodeId = :nodeId OR targetNodeId = :nodeId")
    suspend fun getEdgesForNode(nodeId: String): List<KgEdgeEntity>

    @Query("SELECT * FROM kg_nodes WHERE label LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%'")
    suspend fun searchNodes(query: String): List<KgNodeEntity>

    @Query("SELECT * FROM kg_nodes WHERE nodeType = :nodeType ORDER BY degree DESC LIMIT :limit")
    suspend fun getNodesByType(nodeType: String, limit: Int = 20): List<KgNodeEntity>

    @Query("SELECT * FROM kg_nodes ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentNodes(limit: Int = 20): List<KgNodeEntity>

    @Query("SELECT * FROM kg_nodes WHERE nodeType IN ('DOCUMENT', 'IMAGE') ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentFileNodes(limit: Int = 10): List<KgNodeEntity>

    @Query("SELECT DISTINCT nodeType FROM kg_nodes")
    suspend fun getDistinctNodeTypes(): List<String>

    @Query("SELECT COUNT(*) FROM kg_nodes")
    fun getNodeCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM kg_edges")
    fun getEdgeCountFlow(): Flow<Int>

    @Query("DELETE FROM kg_nodes WHERE sourceFilePath = :filePath")
    suspend fun deleteNodeByFilePath(filePath: String)

    @Query("DELETE FROM kg_edges WHERE sourceNodeId = :nodeId OR targetNodeId = :nodeId")
    suspend fun deleteEdgesForNode(nodeId: String)

    @Query("DELETE FROM kg_nodes")
    suspend fun clearAllNodes()

    @Query("DELETE FROM kg_edges")
    suspend fun clearAllEdges()
}

@Dao
interface RagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<RagChunkEntity>)

    @Query("SELECT * FROM rag_chunks WHERE filePath = :filePath ORDER BY chunkIndex ASC")
    suspend fun getChunksForFile(filePath: String): List<RagChunkEntity>

    @Query("SELECT * FROM rag_chunks WHERE content LIKE '%' || :query || '%' OR tagsJson LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchChunks(query: String, limit: Int = 20): List<RagChunkEntity>

    @Query("SELECT COUNT(*) FROM rag_chunks")
    fun getChunkCountFlow(): Flow<Int>

    @Query("DELETE FROM rag_chunks WHERE filePath = :filePath")
    suspend fun deleteChunksForFile(filePath: String)

    @Query("DELETE FROM rag_chunks")
    suspend fun clearAllChunks()
}

