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

class FavoriteMediaPagingSource(context: Context) : PagingSource<Int, MediaItem>() {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val favoriteDao = AppDatabase.getDatabase(appContext).favoriteDao()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize.coerceIn(1, 120)
        return try {
            val paths = favoriteDao.getFavoritePathsPage(limit, offset)
            if (paths.isEmpty()) return LoadResult.Page(emptyList(), if (offset == 0) null else offset - limit, null)
            val rows = query(paths)
            LoadResult.Page(
                rows,
                if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                if (paths.size < limit) null else offset + paths.size
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    private fun query(paths: List<String>): List<MediaItem> {
        val placeholders = paths.joinToString(",") { "?" }
        val selection = MediaStore.Files.FileColumns.DATA + " IN ($placeholders) AND " +
            MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
        val args = paths + listOf(
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
        val found = HashMap<String, MediaItem>(paths.size)
        resolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            args.toTypedArray(),
            MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        )?.use { cursor ->
            readCursor(cursor, found)
        }
        return paths.mapNotNull { found[it] }
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
