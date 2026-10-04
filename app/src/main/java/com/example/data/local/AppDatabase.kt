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
    version = 10,
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
                db.execSQL("CREATE TABLE IF NOT EXISTS brain_topics (id TEXT NOT NULL PRIMARY KEY, heading TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_brain_topics_updatedAt ON brain_topics(updatedAt)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN textEmbeddingModel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ai_provider_config ADD COLUMN multimodalEmbeddingModel TEXT NOT NULL DEFAULT ''")
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
                db.execSQL("ALTER TABLE rag_chunks ADD COLUMN embeddingJson TEXT")
                db.execSQL("ALTER TABLE rag_chunks ADD COLUMN embeddingModel TEXT")
                db.execSQL("ALTER TABLE rag_chunks ADD COLUMN contentHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rag_chunks ADD COLUMN sectionPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rag_chunks ADD COLUMN pageNumber INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS memory_facts (id TEXT NOT NULL PRIMARY KEY, subject TEXT NOT NULL, normalizedSubject TEXT NOT NULL, predicate TEXT NOT NULL, objectValue TEXT NOT NULL, normalizedObject TEXT NOT NULL, confidence REAL NOT NULL DEFAULT 0.5, sourceType TEXT NOT NULL DEFAULT 'AI', evidence TEXT NOT NULL DEFAULT '', sourceFilePath TEXT, validFrom INTEGER, validTo INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_facts_normalizedSubject ON memory_facts(normalizedSubject)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_facts_predicate ON memory_facts(predicate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_facts_validFrom ON memory_facts(validFrom)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_facts_validTo ON memory_facts(validTo)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_facts_confidence ON memory_facts(confidence)")
                db.execSQL("CREATE TABLE IF NOT EXISTS entity_mentions (entityId TEXT NOT NULL, sourceFilePath TEXT NOT NULL, chunkId TEXT NOT NULL, mentionText TEXT NOT NULL, entityType TEXT NOT NULL, confidence REAL NOT NULL DEFAULT 0.5, createdAt INTEGER NOT NULL, PRIMARY KEY(entityId, sourceFilePath, chunkId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_entity_mentions_entityId ON entity_mentions(entityId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_entity_mentions_sourceFilePath ON entity_mentions(sourceFilePath)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_entity_mentions_chunkId ON entity_mentions(chunkId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS index_fingerprints (filePath TEXT NOT NULL PRIMARY KEY, size INTEGER NOT NULL, lastModified INTEGER NOT NULL, contentHash TEXT NOT NULL, modelVersion TEXT NOT NULL, embeddingModel TEXT NOT NULL, indexedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_index_fingerprints_filePath ON index_fingerprints(filePath)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_index_fingerprints_contentHash ON index_fingerprints(contentHash)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_index_fingerprints_modelVersion ON index_fingerprints(modelVersion)")
                db.execSQL("CREATE TABLE IF NOT EXISTS model_runs (id TEXT NOT NULL PRIMARY KEY, filePath TEXT, operation TEXT NOT NULL, model TEXT NOT NULL, success INTEGER NOT NULL, error TEXT, startedAt INTEGER NOT NULL, finishedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_model_runs_filePath ON model_runs(filePath)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_model_runs_startedAt ON model_runs(startedAt)")
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
                )  .addMigrations(MIGRATION_4_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                 .fallbackToDestructiveMigrationFrom(1, 2, 3)
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
