package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        KgEdgeEntity::class,
        RagChunkEntity::class,
        MemoryFactEntity::class,
        EntityMentionEntity::class,
        IndexFingerprintEntity::class,
        ModelRunEntity::class
    ],
    version = 5,
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
    abstract fun ragDao(): RagDao

    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun entityMentionDao(): EntityMentionDao
    abstract fun indexFingerprintDao(): IndexFingerprintDao
    abstract fun modelRunDao(): ModelRunDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "emrexplore.db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
