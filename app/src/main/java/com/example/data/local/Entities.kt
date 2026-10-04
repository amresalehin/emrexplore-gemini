package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val path: String,
    val name: String,
    val isDirectory: Boolean,
    val mimeType: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "trash")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val mimeType: String = "",
    val deletedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "recents")
data class RecentEntity(
    @PrimaryKey val path: String,
    val name: String,
    val mimeType: String = "",
    val size: Long = 0L,
    val lastOpenedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val path: String,
    val name: String,
    val iconName: String = "folder"
)

@Entity(
    tableName = "indexed_files",
    indices = [
        Index(value = ["name"]),
        Index(value = ["parentPath"]),
        Index(value = ["category"]),
        Index(value = ["extension"]),
        Index(value = ["lastModified"]),
        Index(value = ["isDirectory"])
    ]
)
data class IndexedFileEntity(
    @PrimaryKey val path: String,
    val name: String,
    val parentPath: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val mimeType: String = "",
    val extension: String = "",
    val category: String = "OTHER",
    val childCount: Int = 0,
    val indexedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "explorer_preferences")
data class ExplorerPreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val viewMode: String = "DETAILED_LIST",
    val sortOption: String = "NAME_ASC",
    val showHidden: Boolean = false,
    val defaultStartupPath: String = "",
    val rememberLastDirectory: Boolean = true,
    val lastDirectoryPath: String = "",
    val galleryColumns: Int = 3,
    val enableFastRoomSearch: Boolean = true,
    val autoIndexOnStart: Boolean = true,
    val themeMode: String = "SYSTEM",
    val confirmDelete: Boolean = true
)

@Entity(tableName = "index_status")
data class IndexStatusEntity(
    @PrimaryKey val id: Int = 1,
    val isIndexing: Boolean = false,
    val lastIndexedTimestamp: Long = 0L,
    val totalIndexedCount: Int = 0,
    val statusMessage: String = "Ready"
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
data class MediaMetadataEntity(
    @PrimaryKey val uri: String,
    val path: String = "",
    val size: Long = 0L,
    val dateAdded: Long = 0L,
    val make: String? = null,
    val model: String? = null,
    val lens: String? = null,
    val iso: Int? = null,
    val aperture: Double? = null,
    val focalLength: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val hasGps: Boolean = false,
    val capturedAt: Long? = null,
    val searchableText: String = "",
    val indexedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "place_search_cache")
data class PlaceSearchCacheEntity(
    @PrimaryKey val query: String,
    val latitude: Double,
    val longitude: Double,
    val label: String = "",
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_provider_config")
data class AiProviderConfigEntity(
    @PrimaryKey val id: Int = 1,
    val providerType: String = "GEMINI",
    val apiKey: String = "",
    val baseUrl: String = "https://generativelanguage.googleapis.com/",
    val chatModel: String = "gemini-3.8-flash",
    val visionModel: String = "gemini-3.8-flash",
    val embeddingModel: String = "gemini-embedding-2",
    val customHeadersJson: String = "{}",
    val temperature: Float = 0.2f,
    val isEnabled: Boolean = false,
    val autoSync: Boolean = true,
    val lastSyncTimestamp: Long = 0L
)

@Entity(
    tableName = "kg_nodes",
    indices = [
        Index(value = ["label"]),
        Index(value = ["nodeType"]),
        Index(value = ["sourceFilePath"])
    ]
)
data class KgNodeEntity(
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
    tableName = "kg_edges",
    primaryKeys = ["sourceNodeId", "targetNodeId", "relation"],
    indices = [
        Index(value = ["sourceNodeId"]),
        Index(value = ["targetNodeId"]),
        Index(value = ["relation"])
    ]
)
data class KgEdgeEntity(
    val sourceNodeId: String,
    val targetNodeId: String,
    val relation: String,
    val weight: Float = 1.0f,
    val evidenceSnippet: String = "",
    val evidenceSource: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "rag_chunks",
    indices = [
        Index(value = ["filePath"]),
        Index(value = ["fileType"]),
        Index(value = ["indexedTimestamp"])
    ]
)
data class RagChunkEntity(
    @PrimaryKey val chunkId: String,
    val filePath: String,
    val fileType: String,
    val chunkIndex: Int,
    val content: String,
    val tagsJson: String = "[]",
    val embeddingJson: String? = null,
    val embeddingModel: String? = null,
    val contentHash: String = "",
    val sectionPath: String = "",
    val pageNumber: Int? = null,
    val indexedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "memory_facts",
    indices = [
        Index(value = ["normalizedSubject"]),
        Index(value = ["predicate"]),
        Index(value = ["validFrom"]),
        Index(value = ["validTo"]),
        Index(value = ["confidence"])
    ]
)
data class MemoryFactEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val normalizedSubject: String,
    val predicate: String,
    val objectValue: String,
    val normalizedObject: String,
    val confidence: Float = 0.5f,
    val sourceType: String = "AI",
    val evidence: String = "",
    val sourceFilePath: String? = null,
    val validFrom: Long? = null,
    val validTo: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "entity_mentions",
    primaryKeys = ["entityId", "sourceFilePath", "chunkId"],
    indices = [
        Index(value = ["entityId"]),
        Index(value = ["sourceFilePath"]),
        Index(value = ["chunkId"])
    ]
)
data class EntityMentionEntity(
    val entityId: String,
    val sourceFilePath: String,
    val chunkId: String,
    val mentionText: String,
    val entityType: String,
    val confidence: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "index_fingerprints",
    indices = [Index(value = ["filePath"]), Index(value = ["contentHash"]), Index(value = ["modelVersion"])]
)
data class IndexFingerprintEntity(
    @PrimaryKey val filePath: String,
    val size: Long,
    val lastModified: Long,
    val contentHash: String,
    val modelVersion: String,
    val embeddingModel: String,
    val indexedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "model_runs",
    indices = [Index(value = ["filePath"]), Index(value = ["startedAt"])]
)
data class ModelRunEntity(
    @PrimaryKey val id: String,
    val filePath: String?,
    val operation: String,
    val model: String,
    val success: Boolean,
    val error: String? = null,
    val startedAt: Long,
    val finishedAt: Long = System.currentTimeMillis()
)