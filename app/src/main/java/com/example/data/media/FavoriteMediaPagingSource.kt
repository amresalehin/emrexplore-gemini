package com.example.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import kotlinx.coroutines.CancellationException

class FavoriteMediaPagingSource(
    context: Context,
    private val sort: com.example.ui.viewmodel.GallerySortOption = com.example.ui.viewmodel.GallerySortOption.DATE_DESC
) : PagingSource<Int, MediaItem>() {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val favoriteDao = AppDatabase.getDatabase(appContext).favoriteDao()
    private var cachedItems: List<MediaItem>? = null

    suspend fun loadAllSorted(): List<MediaItem> {
        cachedItems?.let { return it }
        val paths = favoriteDao.getAllFavoritePathsSync()
        val items = query(paths).sortedWith(mediaComparator())
        cachedItems = items
        return items
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize.coerceIn(1, 120)
        return try {
            val items = loadAllSorted()
            if (offset >= items.size) {
                return LoadResult.Page(
                    emptyList(),
                    if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                    null
                )
            }
            val end = (offset + limit).coerceAtMost(items.size)
            LoadResult.Page(
                data = items.subList(offset, end),
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = end.takeIf { it < items.size }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    private fun query(paths: List<String>): List<MediaItem> {
        if (paths.isEmpty()) return emptyList()
        val found = HashMap<String, MediaItem>(paths.size)

        // Keep each IN clause below SQLite's bind-parameter ceiling.
        paths.chunked(800).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = MediaStore.Files.FileColumns.DATA + " IN ($placeholders) AND " +
                MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
            val args = chunk + listOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )
            resolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                args.toTypedArray(),
                null
            )?.use { cursor ->
                readCursor(cursor, found)
            }
        }
        return found.values.toList()
    }

    private fun mediaComparator(): Comparator<MediaItem> = Comparator { left, right ->
        when (sort) {
            com.example.ui.viewmodel.GallerySortOption.DATE_DESC -> compareValuesBy(right, left, { it.dateAdded }, { it.id })
            com.example.ui.viewmodel.GallerySortOption.DATE_ASC -> compareValuesBy(left, right, { it.dateAdded }, { it.id })
            com.example.ui.viewmodel.GallerySortOption.NAME_ASC -> {
                val byName = left.name.compareTo(right.name, ignoreCase = true)
                if (byName != 0) byName else left.id.compareTo(right.id)
            }
            com.example.ui.viewmodel.GallerySortOption.NAME_DESC -> {
                val byName = right.name.compareTo(left.name, ignoreCase = true)
                if (byName != 0) byName else right.id.compareTo(left.id)
            }
            com.example.ui.viewmodel.GallerySortOption.SIZE_DESC -> compareValuesBy(right, left, { it.size }, { it.id })
            com.example.ui.viewmodel.GallerySortOption.SIZE_ASC -> compareValuesBy(left, right, { it.size }, { it.id })
        }
    }

    private fun readCursor(cursor: Cursor, found: MutableMap<String, MediaItem>) {
        val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val name = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val data = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val date = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val mime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val type = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val duration = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
        val width = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        val height = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        val bucketId = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
        val bucketName = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val rowId = cursor.getLong(id)
            val isVideo = cursor.getInt(type) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val path = cursor.getString(data) ?: continue
            val uri = if (isVideo) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rowId)
            }
            found[path] = MediaItem(
                id = if (isVideo) rowId + 1_000_000L else rowId,
                uri = uri,
                name = cursor.getString(name) ?: path.substringAfterLast('/'),
                path = path,
                size = cursor.getLong(size),
                dateAdded = cursor.getLong(date) * 1000L,
                mimeType = cursor.getString(mime) ?: if (isVideo) "video/*" else "image/*",
                duration = if (duration >= 0 && !cursor.isNull(duration)) cursor.getLong(duration) else 0L,
                width = if (width >= 0 && !cursor.isNull(width)) cursor.getInt(width) else 0,
                height = if (height >= 0 && !cursor.isNull(height)) cursor.getInt(height) else 0,
                bucketId = if (bucketId >= 0) cursor.getString(bucketId) ?: "" else "",
                bucketName = if (bucketName >= 0) cursor.getString(bucketName) ?: "" else "",
                isVideo = isVideo,
                isFavorite = true
            )
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(state.config.pageSize)
            ?: page.nextKey?.minus(state.config.pageSize)
    }
}
