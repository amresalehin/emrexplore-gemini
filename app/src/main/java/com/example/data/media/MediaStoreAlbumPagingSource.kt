package com.example.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.data.model.MediaItem
import kotlinx.coroutines.CancellationException

class MediaStoreAlbumPagingSource(
    context: Context,
    private val bucketId: String
) : PagingSource<Int, MediaItem>() {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize.coerceIn(1, 120)
        return try {
            val rows = query(offset, limit)
            LoadResult.Page(
                rows,
                if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                if (rows.size < limit) null else offset + rows.size
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    private fun query(offset: Int, limit: Int): List<MediaItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

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
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?) AND " +
            MediaStore.Files.FileColumns.BUCKET_ID + " = ?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            bucketId
        )
        val uri = MediaStore.Files.getContentUri("external")
        val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC, " +
            MediaStore.Files.FileColumns._ID + " DESC"

        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }

        val cursor = resolver.query(uri, projection, queryArgs, null)
        return cursor?.use { readCursor(it) } ?: emptyList()
    }

    private fun readCursor(cursor: Cursor): List<MediaItem> {
        val result = ArrayList<MediaItem>(cursor.count.coerceAtMost(120))
        val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val name = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val data = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val mime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val mediaType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val duration = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
        val width = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        val height = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        val bucketId = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
        val bucketName = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val rowId = cursor.getLong(id)
            val isVideo = cursor.getInt(mediaType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val uri = if (isVideo) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rowId)
            }
            result += MediaItem(
                id = if (isVideo) rowId + 1_000_000L else rowId,
                uri = uri,
                name = cursor.getString(name) ?: "Media_$rowId",
                path = if (data >= 0) cursor.getString(data) ?: "" else "",
                size = cursor.getLong(size),
                dateAdded = cursor.getLong(dateAdded) * 1000L,
                mimeType = cursor.getString(mime) ?: if (isVideo) "video/*" else "image/*",
                duration = if (duration >= 0 && !cursor.isNull(duration)) cursor.getLong(duration) else 0L,
                width = if (width >= 0 && !cursor.isNull(width)) cursor.getInt(width) else 0,
                height = if (height >= 0 && !cursor.isNull(height)) cursor.getInt(height) else 0,
                bucketId = if (bucketId >= 0) cursor.getString(bucketId) ?: "" else "",
                bucketName = if (bucketName >= 0) cursor.getString(bucketName) ?: "" else "",
                isVideo = isVideo
            )
        }
        return result
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(state.config.pageSize)
            ?: page.nextKey?.minus(state.config.pageSize)
    }
}
