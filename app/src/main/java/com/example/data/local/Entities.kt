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
    val providerType: String = "GEMINI", // "GEMINI", "OPENAI_COMPATIBLE", "OLLAMA"
    val apiKey: String = "",
    val baseUrl: String = "https://generativelanguage.googleapis.com/",
    val chatModel: String = "gemini-3.5-flash",
    val visionModel: String = "gemini-3.5-flash",
    val embeddingModel: String = "gemini-embedding-2-preview",
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
    @PrimaryKey val id: String, // e.g. "doc:/path/file.txt", "img:/path/photo.jpg", "ent:Tokyo"
    val label: String,
    val nodeType: String, // "DOCUMENT", "IMAGE", "ENTITY", "LOCATION", "TOPIC", "PERSON"
    val sourceFilePath: String? = null,
    val thumbnailUri: String? = null,
    val summary: String = "",
    val degree: Int = 0,
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
    val relation: String, // "REFERENCES", "DEPICTS", "MENTIONS", "LOCATED_AT", "CREATED_AT", "SIMILAR_TO"
    val weight: Float = 1.0f,
    val evidenceSnippet: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "rag_chunks",
    indices = [
        Index(value = ["filePath"]),
        Index(value = ["fileType"])
    ]
)
data class RagChunkEntity(
    @PrimaryKey val chunkId: String, // hash(filePath + chunkIndex)
    val filePath: String,
    val fileType: String, // "DOCUMENT" or "IMAGE"
    val chunkIndex: Int,
    val content: String,
    val tagsJson: String = "[]",
    val indexedTimestamp: Long = System.currentTimeMillis()
)

