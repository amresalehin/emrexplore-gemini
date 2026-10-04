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

data class RagEmbeddingRow(
    val chunkId: String,
    val embeddingJson: String?
)

@Dao
interface FileIndexDao {
    @Query("SELECT * FROM indexed_files ORDER BY lastModified DESC LIMIT 100")
    fun getAllIndexedFiles(): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM indexed_files WHERE isDirectory = 0")
    suspend fun getAllIndexedFilesForBrain(): List<IndexedFileEntity>

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

    @Query("SELECT path FROM indexed_files WHERE parentPath = :parentPath")
    suspend fun getPathsByParent(parentPath: String): List<String>

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

    @Query("SELECT * FROM media_metadata WHERE path = :path ORDER BY indexedAt DESC LIMIT 1")
    suspend fun getByPath(path: String): MediaMetadataEntity?

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
interface BrainTopicDao {
    @Query("SELECT * FROM brain_topics ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<BrainTopicEntity>>

    @Query("SELECT * FROM brain_topics WHERE id = :id LIMIT 1")
    suspend fun get(id: String): BrainTopicEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(topic: BrainTopicEntity)

    @Query("DELETE FROM brain_topics WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface KgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<KgNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdges(edges: List<KgEdgeEntity>)

    // The Brain canvas is a preview, not a database dump. Keep large libraries from
    // materializing thousands of nodes/edges into Compose state on every change.
    @Query("SELECT * FROM kg_nodes ORDER BY degree DESC, updatedAt DESC LIMIT 240")
    fun getAllNodesFlow(): Flow<List<KgNodeEntity>>

    @Query("""
        SELECT e.* FROM kg_edges e
        INNER JOIN kg_nodes s ON s.id = e.sourceNodeId
        INNER JOIN kg_nodes t ON t.id = e.targetNodeId
        ORDER BY e.weight DESC
        LIMIT 500
    """)
    fun getAllEdgesFlow(): Flow<List<KgEdgeEntity>>

    @Query("SELECT * FROM kg_nodes WHERE id = :id LIMIT 1")
    suspend fun getNode(id: String): KgNodeEntity?

    @Query("SELECT * FROM kg_nodes WHERE sourceFilePath = :path LIMIT 1")
    suspend fun getNodeByFilePath(path: String): KgNodeEntity?

    @Query("SELECT * FROM kg_nodes WHERE id IN (:ids)")
    suspend fun getNodes(ids: List<String>): List<KgNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdgeEvidence(items: List<KgEdgeEvidenceEntity>)

    @Query("DELETE FROM kg_edge_evidence WHERE evidenceSource = :sourceFilePath")
    suspend fun deleteEdgeEvidenceBySource(sourceFilePath: String)

    @Query("""
        DELETE FROM kg_edges
        WHERE evidenceSource IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM kg_edge_evidence e
              WHERE e.sourceNodeId = kg_edges.sourceNodeId
                AND e.targetNodeId = kg_edges.targetNodeId
                AND e.relation = kg_edges.relation
          )
    """)
    suspend fun deleteSourcedEdgesWithoutEvidence()

    @Query("DELETE FROM kg_nodes WHERE nodeType NOT IN ('DOCUMENT', 'IMAGE') AND id NOT IN (SELECT sourceNodeId FROM kg_edges UNION SELECT targetNodeId FROM kg_edges)")
    suspend fun deleteOrphanedNonFileNodes()

    @Query("UPDATE kg_nodes SET degree = (SELECT COUNT(*) FROM kg_edges WHERE sourceNodeId = kg_nodes.id OR targetNodeId = kg_nodes.id)")
    suspend fun recomputeDegrees()

    @Query("SELECT * FROM kg_edges WHERE sourceNodeId = :nodeId OR targetNodeId = :nodeId")
    suspend fun getEdgesForNode(nodeId: String): List<KgEdgeEntity>

    @Query("SELECT * FROM kg_edges WHERE sourceNodeId IN (:nodeIds) OR targetNodeId IN (:nodeIds)")
    suspend fun getEdgesForNodes(nodeIds: List<String>): List<KgEdgeEntity>

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

    @Query("DELETE FROM kg_nodes WHERE id IN (SELECT sourceNodeId FROM kg_edges WHERE targetNodeId IN (SELECT id FROM kg_nodes WHERE sourceFilePath = :filePath)) OR id IN (SELECT targetNodeId FROM kg_edges WHERE sourceNodeId IN (SELECT id FROM kg_nodes WHERE sourceFilePath = :filePath))")
    suspend fun deleteOrphanedRelatedNodesForFile(filePath: String)

    @Query("DELETE FROM kg_edges WHERE sourceNodeId = :nodeId OR targetNodeId = :nodeId")
    suspend fun deleteEdgesForNode(nodeId: String)

    @Query("SELECT COUNT(*) FROM kg_edges WHERE sourceNodeId = :nodeId OR targetNodeId = :nodeId")
    suspend fun getDegree(nodeId: String): Int

    @Query("DELETE FROM kg_nodes")
    suspend fun clearAllNodes()

    @Query("DELETE FROM kg_edges")
    suspend fun clearAllEdges()

    @Query("DELETE FROM kg_edge_evidence")
    suspend fun clearAllEdgeEvidence()
}

@Dao
interface RagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<RagChunkEntity>)

    @Query("SELECT * FROM rag_chunks WHERE filePath = :filePath ORDER BY chunkIndex ASC")
    suspend fun getChunksForFile(filePath: String): List<RagChunkEntity>

    @Query("SELECT COUNT(*) FROM rag_chunks WHERE filePath = :filePath")
    suspend fun getChunkCountForFile(filePath: String): Int

    @Query("""
        SELECT COUNT(*) FROM rag_chunks
        WHERE filePath = :filePath
          AND (embeddingJson IS NULL OR embeddingModel != :embeddingModel)
    """)
    suspend fun getChunksMissingEmbeddings(filePath: String, embeddingModel: String): Int

    @Query("""
        SELECT chunkId, embeddingJson FROM rag_chunks
        WHERE embeddingModel = :embeddingModel AND embeddingJson IS NOT NULL
        ORDER BY indexedTimestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getEmbeddedChunksPage(embeddingModel: String, limit: Int, offset: Int): List<RagEmbeddingRow>
    @Query("SELECT * FROM rag_chunks WHERE chunkId IN (:ids)")
    suspend fun getChunksByIds(ids: List<String>): List<RagChunkEntity>

    @Query("SELECT * FROM rag_chunks WHERE content LIKE '%' || :query || '%' OR tagsJson LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchChunks(query: String, limit: Int = 20): List<RagChunkEntity>

    @Query("SELECT COUNT(*) FROM rag_chunks")
    fun getChunkCountFlow(): Flow<Int>

    @Query("DELETE FROM rag_chunks WHERE filePath = :filePath")
    suspend fun deleteChunksForFile(filePath: String)

    @Query("DELETE FROM rag_chunks")
    suspend fun clearAllChunks()
}



@Dao
interface MemoryFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fact: MemoryFactEntity)

    @Query("SELECT * FROM memory_facts WHERE normalizedSubject = :subject OR normalizedObject = :subject ORDER BY confidence DESC LIMIT :limit")
    suspend fun findByEntity(subject: String, limit: Int = 50): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts ORDER BY confidence DESC, updatedAt DESC LIMIT :limit")
    suspend fun getTopFacts(limit: Int = 100): List<MemoryFactEntity>

    @Query("DELETE FROM memory_facts WHERE sourceFilePath = :path")
    suspend fun deleteForFile(path: String)

    @Query("DELETE FROM memory_facts")
    suspend fun clearAll()
}

@Dao
interface EntityMentionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EntityMentionEntity>)

    @Query("DELETE FROM entity_mentions WHERE sourceFilePath = :path")
    suspend fun deleteForFile(path: String)

    @Query("SELECT * FROM entity_mentions WHERE entityId = :entityId ORDER BY confidence DESC")
    suspend fun getForEntity(entityId: String): List<EntityMentionEntity>

    @Query("SELECT DISTINCT sourceFilePath FROM entity_mentions WHERE entityId = :entityId")
    suspend fun getSourceFiles(entityId: String): List<String>

    @Query("DELETE FROM entity_mentions")
    suspend fun clearAll()
}

@Dao
interface IndexFingerprintDao {
    @Query("SELECT * FROM index_fingerprints WHERE filePath = :path LIMIT 1")
    suspend fun get(path: String): IndexFingerprintEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: IndexFingerprintEntity)

    @Query("DELETE FROM index_fingerprints WHERE filePath = :path")
    suspend fun delete(path: String)

    @Query("DELETE FROM index_fingerprints")
    suspend fun clearAll()
}

@Dao
interface ModelRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ModelRunEntity)

    @Query("SELECT * FROM model_runs WHERE filePath = :path ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getForFile(path: String, limit: Int = 20): List<ModelRunEntity>

    @Query("DELETE FROM model_runs")
    suspend fun clearAll()
}
