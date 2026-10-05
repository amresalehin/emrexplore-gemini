package com.example.data.brain

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrainDocumentDao {
    @Query("SELECT * FROM brain_documents WHERE path = :path LIMIT 1")
    suspend fun get(path: String): BrainDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BrainDocumentEntity)

    @Query("SELECT path FROM brain_documents")
    suspend fun getAllPaths(): List<String>

    @Query("SELECT path FROM brain_documents WHERE path = :path OR path LIKE :prefix || '/%'")
    suspend fun getPathsUnder(path: String, prefix: String): List<String>

    @Query("DELETE FROM brain_documents WHERE path = :path")
    suspend fun delete(path: String)

    @Query("DELETE FROM brain_documents")
    suspend fun clearAll()
}

@Dao
interface BrainChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BrainChunkEntity>)

    @Query("SELECT * FROM brain_chunks WHERE filePath = :filePath ORDER BY chunkIndex ASC")
    suspend fun getForFile(filePath: String): List<BrainChunkEntity>

    @Query("""
        SELECT * FROM brain_chunks
        WHERE embeddingModel = :embeddingModel
          AND embeddingJson != ''
        ORDER BY indexedAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getEmbeddedPage(
        embeddingModel: String,
        limit: Int,
        offset: Int
    ): List<BrainChunkEntity>

    @Query("""
        SELECT * FROM brain_chunks
        WHERE offlineEmbeddingJson != ''
        ORDER BY indexedAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getOfflinePage(limit: Int, offset: Int): List<BrainChunkEntity>

    @Query("SELECT * FROM brain_chunks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<BrainChunkEntity>

    @Query("SELECT * FROM brain_chunks WHERE content LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun lexical(query: String, limit: Int = 40): List<BrainChunkEntity>

    @Query("SELECT COUNT(*) FROM brain_chunks")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM brain_chunks WHERE filePath = :filePath")
    suspend fun deleteForFile(filePath: String)

    @Query("DELETE FROM brain_chunks")
    suspend fun clearAll()
}

@Dao
interface BrainNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BrainNodeEntity>)

    @Query("SELECT * FROM brain_nodes ORDER BY degree DESC, updatedAt DESC LIMIT 240")
    fun observePreview(): Flow<List<BrainNodeEntity>>

    @Query("""
        SELECT e.*
        FROM brain_edges e
        INNER JOIN brain_nodes s ON s.id = e.sourceNodeId
        INNER JOIN brain_nodes t ON t.id = e.targetNodeId
        ORDER BY e.weight DESC
        LIMIT 500
    """)
    fun observeEdges(): Flow<List<BrainEdgeEntity>>

    @Query("SELECT * FROM brain_nodes WHERE id = :id LIMIT 1")
    suspend fun get(id: String): BrainNodeEntity?

    @Query("SELECT * FROM brain_nodes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<BrainNodeEntity>

    @Query("SELECT * FROM brain_nodes WHERE sourceFilePath IN (:paths)")
    suspend fun getByFilePaths(paths: List<String>): List<BrainNodeEntity>

    @Query("SELECT * FROM brain_nodes WHERE sourceFilePath = :path LIMIT 1")
    suspend fun getByFilePath(path: String): BrainNodeEntity?

    @Query("SELECT * FROM brain_nodes WHERE label LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<BrainNodeEntity>

    @Query("SELECT * FROM brain_nodes WHERE nodeType = :nodeType ORDER BY degree DESC LIMIT :limit")
    suspend fun byType(nodeType: String, limit: Int = 20): List<BrainNodeEntity>

    @Query("SELECT * FROM brain_nodes WHERE nodeType IN ('DOCUMENT', 'IMAGE') ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recentFiles(limit: Int = 10): List<BrainNodeEntity>

    @Query("SELECT COUNT(*) FROM brain_nodes")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM brain_nodes WHERE sourceFilePath = :path")
    suspend fun deleteFileNode(path: String)

    @Query("DELETE FROM brain_nodes WHERE nodeType NOT IN ('DOCUMENT', 'IMAGE') AND id NOT IN (SELECT sourceNodeId FROM brain_edges UNION SELECT targetNodeId FROM brain_edges)")
    suspend fun deleteOrphans()

    @Query("UPDATE brain_nodes SET degree = (SELECT COUNT(*) FROM brain_edges WHERE sourceNodeId = brain_nodes.id OR targetNodeId = brain_nodes.id)")
    suspend fun recomputeDegrees()

    @Query("DELETE FROM brain_nodes")
    suspend fun clearAll()
}

@Dao
interface BrainEdgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BrainEdgeEntity>)

    @Query("SELECT COUNT(*) FROM brain_edges")
    fun countFlow(): Flow<Int>

    @Query("SELECT * FROM brain_edges WHERE sourceNodeId = :nodeId OR targetNodeId = :nodeId")
    suspend fun forNode(nodeId: String): List<BrainEdgeEntity>

    @Query("SELECT * FROM brain_edges WHERE sourceNodeId IN (:nodeIds) OR targetNodeId IN (:nodeIds)")
    suspend fun forNodes(nodeIds: List<String>): List<BrainEdgeEntity>

    @Query("""
        DELETE FROM brain_edges
        WHERE sourceNodeId = :fileNodeId
           OR targetNodeId = :fileNodeId
    """)
    suspend fun deleteForFileNode(fileNodeId: String)

    @Query("DELETE FROM brain_edges")
    suspend fun clearAll()
}

@Dao
interface BrainEdgeEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BrainEdgeEvidenceEntity>)

    @Query("DELETE FROM brain_edge_evidence WHERE evidenceSource = :filePath")
    suspend fun deleteForFile(filePath: String)

    @Query("""
        UPDATE brain_edges
        SET
            evidenceSource = (
                SELECT ev.evidenceSource
                FROM brain_edge_evidence ev
                WHERE ev.sourceNodeId = brain_edges.sourceNodeId
                  AND ev.targetNodeId = brain_edges.targetNodeId
                  AND ev.relation = brain_edges.relation
                ORDER BY ev.createdAt DESC, ev.evidenceSource ASC
                LIMIT 1
            ),
            evidenceSnippet = (
                SELECT ev.evidenceSnippet
                FROM brain_edge_evidence ev
                WHERE ev.sourceNodeId = brain_edges.sourceNodeId
                  AND ev.targetNodeId = brain_edges.targetNodeId
                  AND ev.relation = brain_edges.relation
                ORDER BY ev.createdAt DESC, ev.evidenceSource ASC
                LIMIT 1
            )
    """)
    suspend fun refreshRepresentatives()

    @Query("""
        DELETE FROM brain_edges
        WHERE evidenceSource IS NULL
           OR NOT EXISTS (
                SELECT 1
                FROM brain_edge_evidence ev
                WHERE ev.sourceNodeId = brain_edges.sourceNodeId
                  AND ev.targetNodeId = brain_edges.targetNodeId
                  AND ev.relation = brain_edges.relation
           )
    """)
    suspend fun deleteEdgesWithoutEvidence()

    @Query("DELETE FROM brain_edge_evidence")
    suspend fun clearAll()
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
interface BrainRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: BrainRunEntity)

    @Query("SELECT * FROM brain_runs WHERE filePath = :path ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getForFile(path: String, limit: Int = 20): List<BrainRunEntity>

    @Query("DELETE FROM brain_runs")
    suspend fun clearAll()
}
