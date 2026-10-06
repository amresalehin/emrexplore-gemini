package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.brain.BrainChunkEntity
import com.example.data.brain.BrainImageProfileEntity
import com.example.data.brain.BrainDocumentEntity
import com.example.data.brain.BrainEdgeEntity
import com.example.data.brain.BrainEdgeEvidenceEntity
import com.example.data.brain.BrainNodeEntity
import com.example.data.brain.BrainRunEntity
import com.example.data.brain.BrainTopicEntity
import com.example.data.brain.BrainVectorSyncOperationEntity

@Database(
    entities = [
        FavoriteEntity::class,
        TrashEntity::class,
        RecentEntity::class,
        BookmarkEntity::class,
        IndexedFileEntity::class,
        ExplorerPreferencesEntity::class,
        IndexStatusEntity::class,
        MediaMetadataEntity::class,
        PlaceSearchCacheEntity::class,
        AiProviderConfigEntity::class,
        BrainNodeEntity::class,
        BrainTopicEntity::class,
        BrainEdgeEntity::class,
        BrainEdgeEvidenceEntity::class,
        BrainChunkEntity::class,
        BrainDocumentEntity::class,
        BrainRunEntity::class,
        BrainImageProfileEntity::class,
        BrainVectorSyncOperationEntity::class
    ],
    version = 19,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun trashDao(): TrashDao
    abstract fun recentDao(): RecentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun fileIndexDao(): FileIndexDao
    abstract fun preferencesDao(): PreferencesDao
    abstract fun indexStatusDao(): IndexStatusDao
    abstract fun mediaMetadataDao(): MediaMetadataDao
    abstract fun placeSearchCacheDao(): PlaceSearchCacheDao
    abstract fun aiProviderConfigDao(): AiProviderConfigDao
    abstract fun brainNodeDao(): com.example.data.brain.BrainNodeDao
    abstract fun brainEdgeDao(): com.example.data.brain.BrainEdgeDao
    abstract fun brainEdgeEvidenceDao(): com.example.data.brain.BrainEdgeEvidenceDao
    abstract fun brainTopicDao(): com.example.data.brain.BrainTopicDao
    abstract fun brainChunkDao(): com.example.data.brain.BrainChunkDao
    abstract fun brainImageProfileDao(): com.example.data.brain.BrainImageProfileDao
    abstract fun brainDocumentDao(): com.example.data.brain.BrainDocumentDao
    abstract fun brainRunDao(): com.example.data.brain.BrainRunDao
    abstract fun brainVectorSyncDao(): com.example.data.brain.BrainVectorSyncDao

    companion object {
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_metadata ADD COLUMN aiCaption TEXT")
                db.execSQL("ALTER TABLE media_metadata ADD COLUMN aiTagsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE media_metadata ADD COLUMN aiEntitiesJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE media_metadata ADD COLUMN aiRelationsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE media_metadata ADD COLUMN aiModel TEXT")
                db.execSQL("ALTER TABLE media_metadata ADD COLUMN aiFileLastModified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE media_metadata ADD COLUMN aiProcessedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `brain_topics` (`id` TEXT NOT NULL, `heading` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_topics_updatedAt` ON `brain_topics` (`updatedAt`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN textEmbeddingModel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN multimodalEmbeddingModel TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate rag_chunks cleanly to match Room entity schema (no SQLite defaults and all indices)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `rag_chunks_new` (
                        `chunkId` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `fileType` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `tagsJson` TEXT NOT NULL,
                        `embeddingJson` TEXT,
                        `embeddingModel` TEXT,
                        `contentHash` TEXT NOT NULL,
                        `sectionPath` TEXT NOT NULL,
                        `pageNumber` INTEGER,
                        `indexedTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`chunkId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO `rag_chunks_new` (
                        `chunkId`, `filePath`, `fileType`, `chunkIndex`, `content`, `tagsJson`,
                        `embeddingJson`, `embeddingModel`, `contentHash`, `sectionPath`, `pageNumber`, `indexedTimestamp`
                    )
                    SELECT 
                        `chunkId`, `filePath`, `fileType`, `chunkIndex`, `content`, `tagsJson`,
                        `embeddingJson`, `embeddingModel`, COALESCE(`contentHash`, ''), COALESCE(`sectionPath`, ''), `pageNumber`, `indexedTimestamp`
                    FROM `rag_chunks`
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `rag_chunks`")
                db.execSQL("ALTER TABLE `rag_chunks_new` RENAME TO `rag_chunks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_chunks_filePath` ON `rag_chunks` (`filePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_chunks_fileType` ON `rag_chunks` (`fileType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_chunks_indexedTimestamp` ON `rag_chunks` (`indexedTimestamp`)")

                // Recreate memory_facts cleanly if it had default values
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `memory_facts_new` (
                        `id` TEXT NOT NULL,
                        `subject` TEXT NOT NULL,
                        `normalizedSubject` TEXT NOT NULL,
                        `predicate` TEXT NOT NULL,
                        `objectValue` TEXT NOT NULL,
                        `normalizedObject` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `evidence` TEXT NOT NULL,
                        `sourceFilePath` TEXT,
                        `validFrom` INTEGER,
                        `validTo` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("INSERT OR IGNORE INTO `memory_facts_new` SELECT * FROM `memory_facts`")
                db.execSQL("DROP TABLE IF EXISTS `memory_facts`")
                db.execSQL("ALTER TABLE `memory_facts_new` RENAME TO `memory_facts`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_facts_normalizedSubject` ON `memory_facts` (`normalizedSubject`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_facts_predicate` ON `memory_facts` (`predicate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_facts_validFrom` ON `memory_facts` (`validFrom`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_facts_validTo` ON `memory_facts` (`validTo`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_facts_confidence` ON `memory_facts` (`confidence`)")

                // Recreate entity_mentions cleanly if it had default values
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `entity_mentions_new` (
                        `entityId` TEXT NOT NULL,
                        `sourceFilePath` TEXT NOT NULL,
                        `chunkId` TEXT NOT NULL,
                        `mentionText` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`entityId`, `sourceFilePath`, `chunkId`)
                    )
                """.trimIndent())
                db.execSQL("INSERT OR IGNORE INTO `entity_mentions_new` SELECT * FROM `entity_mentions`")
                db.execSQL("DROP TABLE IF EXISTS `entity_mentions`")
                db.execSQL("ALTER TABLE `entity_mentions_new` RENAME TO `entity_mentions`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_mentions_entityId` ON `entity_mentions` (`entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_mentions_sourceFilePath` ON `entity_mentions` (`sourceFilePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_mentions_chunkId` ON `entity_mentions` (`chunkId`)")

                // Recreate brain_topics cleanly if it had default values
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `brain_topics_new` (
                        `id` TEXT NOT NULL,
                        `heading` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("INSERT OR IGNORE INTO `brain_topics_new` SELECT * FROM `brain_topics`")
                db.execSQL("DROP TABLE IF EXISTS `brain_topics`")
                db.execSQL("ALTER TABLE `brain_topics_new` RENAME TO `brain_topics`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_topics_updatedAt` ON `brain_topics` (`updatedAt`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS kg_edge_evidence (sourceNodeId TEXT NOT NULL, targetNodeId TEXT NOT NULL, relation TEXT NOT NULL, evidenceSource TEXT NOT NULL, evidenceSnippet TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL, PRIMARY KEY(sourceNodeId, targetNodeId, relation, evidenceSource))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_kg_edge_evidence_evidenceSource ON kg_edge_evidence(evidenceSource)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_kg_edge_evidence_edge ON kg_edge_evidence(sourceNodeId, targetNodeId, relation)")
                db.execSQL("INSERT OR IGNORE INTO kg_edge_evidence(sourceNodeId, targetNodeId, relation, evidenceSource, evidenceSnippet, createdAt) SELECT sourceNodeId, targetNodeId, relation, evidenceSource, evidenceSnippet, createdAt FROM kg_edges WHERE evidenceSource IS NOT NULL AND evidenceSource != ''")
            }
        }

        private val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kg_nodes ADD COLUMN confidence REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE kg_edges ADD COLUMN evidenceSource TEXT")
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `rag_chunks_new` (
                        `chunkId` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `fileType` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `tagsJson` TEXT NOT NULL,
                        `embeddingJson` TEXT,
                        `embeddingModel` TEXT,
                        `contentHash` TEXT NOT NULL,
                        `sectionPath` TEXT NOT NULL,
                        `pageNumber` INTEGER,
                        `indexedTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`chunkId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO `rag_chunks_new` (
                        `chunkId`, `filePath`, `fileType`, `chunkIndex`, `content`, `tagsJson`,
                        `embeddingJson`, `embeddingModel`, `contentHash`, `sectionPath`, `pageNumber`, `indexedTimestamp`
                    )
                    SELECT 
                        `chunkId`, `filePath`, `fileType`, `chunkIndex`, `content`, `tagsJson`,
                        NULL, NULL, '', '', NULL, `indexedTimestamp`
                    FROM `rag_chunks`
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `rag_chunks`")
                db.execSQL("ALTER TABLE `rag_chunks_new` RENAME TO `rag_chunks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_chunks_filePath` ON `rag_chunks` (`filePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_chunks_fileType` ON `rag_chunks` (`fileType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_chunks_indexedTimestamp` ON `rag_chunks` (`indexedTimestamp`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `memory_facts` (`id` TEXT NOT NULL, `subject` TEXT NOT NULL, `normalizedSubject` TEXT NOT NULL, `predicate` TEXT NOT NULL, `objectValue` TEXT NOT NULL, `normalizedObject` TEXT NOT NULL, `confidence` REAL NOT NULL, `sourceType` TEXT NOT NULL, `evidence` TEXT NOT NULL, `sourceFilePath` TEXT, `validFrom` INTEGER, `validTo` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_facts_normalizedSubject` ON `memory_facts` (`normalizedSubject`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_facts_predicate` ON `memory_facts` (`predicate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_facts_validFrom` ON `memory_facts` (`validFrom`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_facts_validTo` ON `memory_facts` (`validTo`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_facts_confidence` ON `memory_facts` (`confidence`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `entity_mentions` (`entityId` TEXT NOT NULL, `sourceFilePath` TEXT NOT NULL, `chunkId` TEXT NOT NULL, `mentionText` TEXT NOT NULL, `entityType` TEXT NOT NULL, `confidence` REAL NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`entityId`, `sourceFilePath`, `chunkId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_mentions_entityId` ON `entity_mentions` (`entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_mentions_sourceFilePath` ON `entity_mentions` (`sourceFilePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_mentions_chunkId` ON `entity_mentions` (`chunkId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `index_fingerprints` (`filePath` TEXT NOT NULL, `size` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, `contentHash` TEXT NOT NULL, `modelVersion` TEXT NOT NULL, `embeddingModel` TEXT NOT NULL, `indexedAt` INTEGER NOT NULL, PRIMARY KEY(`filePath`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_index_fingerprints_filePath` ON `index_fingerprints` (`filePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_index_fingerprints_contentHash` ON `index_fingerprints` (`contentHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_index_fingerprints_modelVersion` ON `index_fingerprints` (`modelVersion`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `model_runs` (`id` TEXT NOT NULL, `filePath` TEXT, `operation` TEXT NOT NULL, `model` TEXT NOT NULL, `success` INTEGER NOT NULL, `error` TEXT, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_model_runs_filePath` ON `model_runs` (`filePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_model_runs_startedAt` ON `model_runs` (`startedAt`)")
            }
        }
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS kg_edge_evidence")
                db.execSQL("DROP TABLE IF EXISTS kg_edges")
                db.execSQL("DROP TABLE IF EXISTS kg_nodes")
                db.execSQL("DROP TABLE IF EXISTS rag_chunks")
                db.execSQL("DROP TABLE IF EXISTS memory_facts")
                db.execSQL("DROP TABLE IF EXISTS entity_mentions")
                db.execSQL("DROP TABLE IF EXISTS index_fingerprints")
                db.execSQL("DROP TABLE IF EXISTS model_runs")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `brain_documents` (
                        `path` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `size` INTEGER NOT NULL,
                        `lastModified` INTEGER NOT NULL,
                        `contentHash` TEXT NOT NULL,
                        `modelSignature` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `error` TEXT,
                        `indexedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`path`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_documents_lastModified` ON `brain_documents` (`lastModified`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_documents_state` ON `brain_documents` (`state`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_documents_modelSignature` ON `brain_documents` (`modelSignature`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `brain_chunks` (
                        `id` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `embeddingJson` TEXT NOT NULL,
                        `embeddingModel` TEXT NOT NULL,
                        `offlineEmbeddingJson` TEXT NOT NULL,
                        `locator` TEXT NOT NULL,
                        `pageNumber` INTEGER,
                        `indexedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_chunks_filePath` ON `brain_chunks` (`filePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_chunks_embeddingModel` ON `brain_chunks` (`embeddingModel`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_chunks_indexedAt` ON `brain_chunks` (`indexedAt`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `brain_nodes` (
                        `id` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `nodeType` TEXT NOT NULL,
                        `sourceFilePath` TEXT,
                        `thumbnailUri` TEXT,
                        `summary` TEXT NOT NULL,
                        `degree` INTEGER NOT NULL,
                        `confidence` REAL NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_nodes_label` ON `brain_nodes` (`label`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_nodes_nodeType` ON `brain_nodes` (`nodeType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_nodes_sourceFilePath` ON `brain_nodes` (`sourceFilePath`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `brain_edges` (
                        `sourceNodeId` TEXT NOT NULL,
                        `targetNodeId` TEXT NOT NULL,
                        `relation` TEXT NOT NULL,
                        `weight` REAL NOT NULL,
                        `evidenceSnippet` TEXT NOT NULL,
                        `evidenceSource` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceNodeId`, `targetNodeId`, `relation`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_edges_sourceNodeId` ON `brain_edges` (`sourceNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_edges_targetNodeId` ON `brain_edges` (`targetNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_edges_relation` ON `brain_edges` (`relation`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_edges_evidenceSource` ON `brain_edges` (`evidenceSource`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `brain_runs` (
                        `id` TEXT NOT NULL,
                        `filePath` TEXT,
                        `operation` TEXT NOT NULL,
                        `success` INTEGER NOT NULL,
                        `error` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_runs_filePath` ON `brain_runs` (`filePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_runs_startedAt` ON `brain_runs` (`startedAt`)")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `brain_edge_evidence` (
                        `sourceNodeId` TEXT NOT NULL,
                        `targetNodeId` TEXT NOT NULL,
                        `relation` TEXT NOT NULL,
                        `evidenceSource` TEXT NOT NULL,
                        `evidenceSnippet` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceNodeId`, `targetNodeId`, `relation`, `evidenceSource`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_edge_evidence_evidenceSource` ON `brain_edge_evidence` (`evidenceSource`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_edge_evidence_edge` ON `brain_edge_evidence` (`sourceNodeId`, `targetNodeId`, `relation`)")
                db.execSQL("""
                    INSERT OR IGNORE INTO `brain_edge_evidence` (
                        `sourceNodeId`, `targetNodeId`, `relation`, `evidenceSource`, `evidenceSnippet`, `createdAt`
                    )
                    SELECT `sourceNodeId`, `targetNodeId`, `relation`, `evidenceSource`, `evidenceSnippet`, `createdAt`
                    FROM `brain_edges`
                    WHERE `evidenceSource` IS NOT NULL AND `evidenceSource` != ''
                """.trimIndent())
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Brain owns semantic/media intelligence now. Drop the obsolete
                // Gallery-AI cache columns while preserving EXIF/media metadata.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `media_metadata_new` (
                        `uri` TEXT NOT NULL,
                        `path` TEXT NOT NULL,
                        `size` INTEGER NOT NULL,
                        `dateAdded` INTEGER NOT NULL,
                        `make` TEXT,
                        `model` TEXT,
                        `lens` TEXT,
                        `iso` INTEGER,
                        `aperture` REAL,
                        `focalLength` REAL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `hasGps` INTEGER NOT NULL,
                        `capturedAt` INTEGER,
                        `searchableText` TEXT NOT NULL,
                        `indexedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`uri`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR REPLACE INTO `media_metadata_new` (
                        `uri`, `path`, `size`, `dateAdded`, `make`, `model`,
                        `lens`, `iso`, `aperture`, `focalLength`, `latitude`,
                        `longitude`, `hasGps`, `capturedAt`, `searchableText`, `indexedAt`
                    )
                    SELECT
                        `uri`, `path`, `size`, `dateAdded`, `make`, `model`,
                        `lens`, `iso`, `aperture`, `focalLength`, `latitude`,
                        `longitude`, `hasGps`, `capturedAt`, `searchableText`, `indexedAt`
                    FROM `media_metadata`
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `media_metadata`")
                db.execSQL("ALTER TABLE `media_metadata_new` RENAME TO `media_metadata`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_metadata_path` ON `media_metadata` (`path`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_metadata_make` ON `media_metadata` (`make`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_metadata_model` ON `media_metadata` (`model`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_metadata_hasGps` ON `media_metadata` (`hasGps`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_metadata_capturedAt` ON `media_metadata` (`capturedAt`)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `brain_chunks_new` (
                        `id` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `embeddingJson` TEXT NOT NULL,
                        `embeddingModel` TEXT NOT NULL,
                        `locator` TEXT NOT NULL,
                        `pageNumber` INTEGER,
                        `indexedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR REPLACE INTO `brain_chunks_new` (
                        `id`, `filePath`, `chunkIndex`, `content`,
                        `embeddingJson`, `embeddingModel`, `locator`,
                        `pageNumber`, `indexedAt`
                    )
                    SELECT
                        `id`, `filePath`, `chunkIndex`, `content`,
                        `embeddingJson`, `embeddingModel`, `locator`,
                        `pageNumber`, `indexedAt`
                    FROM `brain_chunks`
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `brain_chunks`")
                db.execSQL("ALTER TABLE `brain_chunks_new` RENAME TO `brain_chunks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_chunks_filePath` ON `brain_chunks` (`filePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_chunks_embeddingModel` ON `brain_chunks` (`embeddingModel`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brain_chunks_indexedAt` ON `brain_chunks` (`indexedAt`)")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN embeddingProviderType TEXT NOT NULL DEFAULT 'OFFLINE'")
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN embeddingApiKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN embeddingBaseUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE brain_chunks ADD COLUMN imageEmbeddingJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE brain_chunks ADD COLUMN imageEmbeddingModel TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS brain_image_profiles (
                        filePath TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        ocrText TEXT NOT NULL,
                        metadataSummary TEXT NOT NULL,
                        description TEXT NOT NULL,
                        tagsJson TEXT NOT NULL,
                        entitiesJson TEXT NOT NULL,
                        relationsJson TEXT NOT NULL,
                        visionModel TEXT NOT NULL,
                        imageEmbeddingModel TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(filePath)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_brain_image_profiles_visionModel ON brain_image_profiles(visionModel)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_brain_image_profiles_updatedAt ON brain_image_profiles(updatedAt)")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS brain_vector_sync_operations (
                        id TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        chunkId TEXT,
                        state TEXT NOT NULL,
                        attempts INTEGER NOT NULL,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_brain_vector_sync_operations_state_createdAt ON brain_vector_sync_operations(state, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_brain_vector_sync_operations_chunkId_createdAt ON brain_vector_sync_operations(chunkId, createdAt)")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN vectorDatabaseType TEXT NOT NULL DEFAULT 'ROOM'")
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN vectorDatabaseBaseUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN vectorDatabaseApiKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN vectorDatabaseCollection TEXT NOT NULL DEFAULT 'emrexplore_brain'")
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN brainSetupCompleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "emrexplore.db"
                )
                    .addMigrations(
                        MIGRATION_4_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19
                    )
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 5)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
