package com.example.data.brain

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object BrainIndexStates {
    const val READY = "READY"
    const val FAILED = "FAILED"
}

@Entity(
    tableName = "brain_documents",
    indices = [
        Index(value = ["lastModified"]),
        Index(value = ["state"]),
        Index(value = ["modelSignature"])
    ]
)
data class BrainDocumentEntity(
    @PrimaryKey val path: String,
    val name: String,
    val mimeType: String = "",
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val contentHash: String = "",
    val modelSignature: String = "",
    val state: String = BrainIndexStates.READY,
    val error: String? = null,
    val indexedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "brain_chunks",
    indices = [
        Index(value = ["filePath"]),
        Index(value = ["embeddingModel"]),
        Index(value = ["indexedAt"])
    ]
)
data class BrainChunkEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val chunkIndex: Int,
    val content: String,
    val embeddingJson: String,
    val embeddingModel: String,
    val offlineEmbeddingJson: String,
    val locator: String = "",
    val pageNumber: Int? = null,
    val indexedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "brain_nodes",
    indices = [
        Index(value = ["label"]),
        Index(value = ["nodeType"]),
        Index(value = ["sourceFilePath"])
    ]
)
data class BrainNodeEntity(
    @PrimaryKey val id: String,
    val label: String,
    val nodeType: String,
    val sourceFilePath: String? = null,
    val thumbnailUri: String? = null,
    val summary: String = "",
    val degree: Int = 0,
    val confidence: Float = 1.0f,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "brain_edges",
    primaryKeys = ["sourceNodeId", "targetNodeId", "relation"],
    indices = [
        Index(value = ["sourceNodeId"]),
        Index(value = ["targetNodeId"]),
        Index(value = ["relation"]),
        Index(value = ["evidenceSource"])
    ]
)
data class BrainEdgeEntity(
    val sourceNodeId: String,
    val targetNodeId: String,
    val relation: String,
    val weight: Float = 1.0f,
    val evidenceSnippet: String = "",
    val evidenceSource: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "brain_topics",
    indices = [Index(value = ["updatedAt"])]
)
data class BrainTopicEntity(
    @PrimaryKey val id: String,
    val heading: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "brain_runs",
    indices = [
        Index(value = ["filePath"]),
        Index(value = ["startedAt"])
    ]
)
data class BrainRunEntity(
    @PrimaryKey val id: String,
    val filePath: String? = null,
    val operation: String,
    val success: Boolean,
    val error: String? = null,
    val startedAt: Long,
    val finishedAt: Long = System.currentTimeMillis()
)
