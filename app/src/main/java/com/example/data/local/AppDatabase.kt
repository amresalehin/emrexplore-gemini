package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        KgNodeEntity::class,
        BrainTopicEntity::class,
        KgEdgeEntity::class,
        KgEdgeEvidenceEntity::class,
        RagChunkEntity::class,
        MemoryFactEntity::class,
        EntityMentionEntity::class,
        IndexFingerprintEntity::class,
        ModelRunEntity::class
    ],
    version = 11,
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
    abstract fun kgDao(): KgDao
    abstract fun brainTopicDao(): BrainTopicDao
    abstract fun ragDao(): RagDao

    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun entityMentionDao(): EntityMentionDao
    abstract fun indexFingerprintDao(): IndexFingerprintDao
    abstract fun modelRunDao(): ModelRunDao

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
                        MIGRATION_10_11
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
