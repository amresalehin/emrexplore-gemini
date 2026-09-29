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
import com.example.data.local.AppDatabase
import com.example.data.local.MediaMetadataEntity
import com.example.data.model.MediaItem
import com.example.ui.viewmodel.GallerySortOption
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Provider-backed paged Gallery search.
 *
 * Filename/date/folder/type predicates are pushed to MediaStore. EXIF/GPS predicates
 * use the persistent Room metadata cache and only extract a cache-miss image once.
 */
class MediaSearchPagingSource(
    context: Context,
    private val rawQuery: String,
    private val baseFilter: MediaFilter,
    private val favoritesOnly: Boolean = false,
    private val sort: GallerySortOption = GallerySortOption.DATE_DESC
) : PagingSource<Int, MediaItem>() {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val favoriteDao = AppDatabase.getDatabase(appContext).favoriteDao()
    private val metadataRepository = MediaMetadataRepository(appContext)
    private val parsed = MediaSearchParser.parse(rawQuery, baseFilter)
    private var resolvedParsed: ParsedMediaSearch? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize.coerceIn(1, MediaStorePagingSource.MAX_PAGE_SIZE)
        val active = resolveParsed()

        if (active.locationText != null && active.near == null) {
            return LoadResult.Page(
                emptyList(),
                if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                null
            )
        }

        return try {
            val page = query(offset, limit, active)
            val data = if (active.requiresMetadata) {
                page.rows.filter { matchesMetadata(it, active) }
            } else {
                page.rows
            }

            LoadResult.Page(
                data = data,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = if (page.exhausted) null else offset + page.consumed
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    private suspend fun resolveParsed(): ParsedMediaSearch {
        resolvedParsed?.let { return it }

        val active = parsed.locationText?.let { location ->
            metadataRepository.resolvePlace(location)?.let { place ->
                parsed.copy(
                    near = Near(
                        place.latitude,
                        place.longitude,
                        parsed.locationRadiusKm
                    )
                )
            }
        } ?: parsed

        resolvedParsed = active
        return active
    }

    private suspend fun query(
        offset: Int,
        limit: Int,
        p: ParsedMediaSearch
    ): QueryPage {
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

        val baseSelection = buildProviderSelection(p)
        val baseArgs = buildProviderArgs(p)

        if (favoritesOnly) {
            val paths = favoriteDao.getFavoritePathsPage(limit, offset)
            if (paths.isEmpty()) return QueryPage(emptyList(), 0, true)

            val placeholders = paths.joinToString(",") { "?" }
            val selection = "(" + baseSelection + ") AND " +
                MediaStore.Files.FileColumns.DATA + " IN ($placeholders)"
            val args = paths + baseArgs

            val rows = queryProvider(
                projection,
                selection,
                args.toTypedArray(),
                0,
                limit,
                sortOrder()
            )

            return QueryPage(
                rows = rows,
                consumed = paths.size,
                exhausted = paths.size < limit
            )
        }

        val rows = queryProvider(
            projection,
            baseSelection,
            baseArgs.toTypedArray(),
            offset,
            limit,
            MediaStore.Files.FileColumns.DATE_ADDED + " DESC, " +
                MediaStore.Files.FileColumns._ID + " DESC"
        )

        return QueryPage(rows, rows.size, rows.size < limit)
    }

    private fun queryProvider(
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>,
        offset: Int,
        limit: Int,
        sortOrder: String
    ): List<MediaItem> {
        val uri = MediaStore.Files.getContentUri("external")
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val args = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            resolver.query(uri, projection, args, null)
        } else {
            resolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "$sortOrder LIMIT $limit OFFSET $offset"
            )
        }
        return cursor?.use(::readCursor) ?: emptyList()
    }

    private fun sortOrder(): String = when (sort) {
        GallerySortOption.DATE_DESC -> MediaStore.Files.FileColumns.DATE_ADDED + " DESC, " + MediaStore.Files.FileColumns._ID + " DESC"
        GallerySortOption.DATE_ASC -> MediaStore.Files.FileColumns.DATE_ADDED + " ASC, " + MediaStore.Files.FileColumns._ID + " ASC"
        GallerySortOption.NAME_ASC -> MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE ASC, " + MediaStore.Files.FileColumns._ID + " ASC"
        GallerySortOption.NAME_DESC -> MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE DESC, " + MediaStore.Files.FileColumns._ID + " DESC"
        GallerySortOption.SIZE_DESC -> MediaStore.Files.FileColumns.SIZE + " DESC, " + MediaStore.Files.FileColumns._ID + " DESC"
        GallerySortOption.SIZE_ASC -> MediaStore.Files.FileColumns.SIZE + " ASC, " + MediaStore.Files.FileColumns._ID + " ASC"
    }

    private fun buildProviderSelection(p: ParsedMediaSearch): String {
        val clauses = mutableListOf<String>()
        clauses += when (p.type) {
            MediaFilter.PHOTOS -> MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
            MediaFilter.VIDEOS -> MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
            MediaFilter.ALL, null -> MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
        }

        p.nameTerms.forEach {
            clauses += "(" +
                "LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE ? OR " +
                "LOWER(" + MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME + ") LIKE ? OR " +
                "LOWER(" + MediaStore.Files.FileColumns.DATA + ") LIKE ?" +
                ")"
        }
        p.album?.let {
            clauses += "LOWER(" + MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME + ") LIKE ?"
        }
        p.after?.let { clauses += MediaStore.Images.ImageColumns.DATE_TAKEN + " >= ?" }
        p.before?.let { clauses += MediaStore.Images.ImageColumns.DATE_TAKEN + " < ?" }
        return clauses.joinToString(" AND ")
    }

    private fun buildProviderArgs(p: ParsedMediaSearch): List<String> {
        val args = mutableListOf<String>()
        when (p.type) {
            MediaFilter.PHOTOS -> args += MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
            MediaFilter.VIDEOS -> args += MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            MediaFilter.ALL, null -> {
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            }
        }
        p.nameTerms.forEach {
            val pattern = "%" + it.lowercase(Locale.US) + "%"
            args += pattern
            args += pattern
            args += pattern
        }
        p.album?.let { args += "%" + it.lowercase(Locale.US) + "%" }
        p.after?.let { args += it.toString() }
        p.before?.let { args += it.toString() }
        return args
    }

    private fun readCursor(cursor: Cursor): List<MediaItem> {
        val result = ArrayList<MediaItem>(
            cursor.count.coerceAtMost(MediaStorePagingSource.MAX_PAGE_SIZE)
        )
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
            val isVideo =
                cursor.getInt(mediaType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val uri = if (isVideo) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rowId)
            }

            result += MediaItem(
                id = if (isVideo) rowId + VIDEO_ID_OFFSET else rowId,
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

    private suspend fun matchesMetadata(
        item: MediaItem,
        p: ParsedMediaSearch
    ): Boolean {
        if (item.isVideo) return false

        val metadata: MediaMetadataEntity = metadataRepository.getOrRead(
            item,
            requireOriginalLocation = p.hasGps != null || p.near != null
        )

        if (p.exifTerms.any { term ->
                term.isNotBlank() &&
                    !metadata.searchableText.contains(term.lowercase(Locale.US))
            }
        ) return false

        p.make?.let { if (!metadata.make.orEmpty().contains(it, true)) return false }
        p.model?.let { if (!metadata.model.orEmpty().contains(it, true)) return false }
        p.lens?.let { if (!metadata.lens.orEmpty().contains(it, true)) return false }
        p.iso?.let { if (metadata.iso != it) return false }

        p.focalLength?.let {
            val actual = metadata.focalLength ?: return false
            if (abs(actual - it) > 0.2) return false
        }

        p.aperture?.let {
            val actual = metadata.aperture ?: return false
            if (abs(actual - it) > 0.2) return false
        }

        when (p.hasGps) {
            true -> if (!metadata.hasGps) return false
            false -> if (metadata.hasGps) return false
            null -> Unit
        }

        p.near?.let {
            val lat = metadata.latitude ?: return false
            val lon = metadata.longitude ?: return false
            if (distanceKm(lat, lon, it.lat, it.lon) > it.radiusKm) return false
        }

        return true
    }

    private fun distanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinLat = sin(dLat / 2.0)
        val sinLon = sin(dLon / 2.0)
        val a = (
            sinLat * sinLat +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sinLon * sinLon
            ).coerceIn(0.0, 1.0)
        return 6371.0 * 2.0 * asin(sqrt(a))
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(state.config.pageSize)
            ?: page.nextKey?.minus(state.config.pageSize)
    }

    companion object {
        private const val VIDEO_ID_OFFSET = 1_000_000L
    }
}

private data class QueryPage(
    val rows: List<MediaItem>,
    val consumed: Int,
    val exhausted: Boolean
)

private data class ParsedMediaSearch(
    val type: MediaFilter? = MediaFilter.ALL,
    val nameTerms: List<String> = emptyList(),
    val album: String? = null,
    val after: Long? = null,
    val before: Long? = null,
    val make: String? = null,
    val model: String? = null,
    val lens: String? = null,
    val iso: Int? = null,
    val focalLength: Double? = null,
    val aperture: Double? = null,
    val hasGps: Boolean? = null,
    val near: Near? = null,
    val exifTerms: List<String> = emptyList(),
    val locationText: String? = null,
    val locationRadiusKm: Double = 25.0
) {
    val requiresMetadata: Boolean
        get() = make != null ||
            model != null ||
            lens != null ||
            iso != null ||
            focalLength != null ||
            aperture != null ||
            hasGps != null ||
            near != null ||
            exifTerms.isNotEmpty()
}

private data class Near(
    val lat: Double,
    val lon: Double,
    val radiusKm: Double
)

private object MediaSearchParser {
    fun parse(rawQuery: String, baseFilter: MediaFilter): ParsedMediaSearch {
        val terms = tokenize(rawQuery)
        val nameTerms = mutableListOf<String>()
        val exifTerms = mutableListOf<String>()

        var type: MediaFilter? = if (baseFilter == MediaFilter.ALL) null else baseFilter
        var album: String? = null
        var after: Long? = null
        var before: Long? = null
        var make: String? = null
        var model: String? = null
        var lens: String? = null
        var iso: Int? = null
        var focalLength: Double? = null
        var aperture: Double? = null
        var hasGps: Boolean? = null
        var near: Near? = null
        var locationText: String? = null

        for (raw in terms) {
            val separator = raw.indexOf(':')
            if (separator <= 0) {
                nameTerms += raw
                continue
            }

            val key = raw.substring(0, separator).lowercase(Locale.US)
            val value = raw.substring(separator + 1).trim()

            when (key) {
                "type" -> when (value.lowercase(Locale.US)) {
                    "photo", "photos", "image", "images" -> type = MediaFilter.PHOTOS
                    "video", "videos" -> type = MediaFilter.VIDEOS
                    "all" -> type = null
                    else -> nameTerms += raw
                }

                "album", "folder", "path" ->
                    if (value.isNotBlank()) album = value else nameTerms += raw

                "name" ->
                    if (value.isNotBlank()) nameTerms += value else nameTerms += raw

                "make", "camera" ->
                    if (value.isNotBlank()) make = value else nameTerms += raw

                "model" ->
                    if (value.isNotBlank()) model = value else nameTerms += raw

                "lens" ->
                    if (value.isNotBlank()) lens = value else nameTerms += raw

                "iso" ->
                    value.toIntOrNull()?.let { iso = it } ?: exifTerms.add(value)

                "focal", "focallength" ->
                    value.toDoubleOrNull()?.let { focalLength = it } ?: exifTerms.add(value)

                "aperture", "fnumber", "f" ->
                    value.toDoubleOrNull()?.let { aperture = it } ?: exifTerms.add(value)

                "description", "caption", "tag", "keyword", "exif" ->
                    if (value.isNotBlank()) exifTerms += value else nameTerms += raw

                "gps" -> when (value.lowercase(Locale.US)) {
                    "yes", "true", "1" -> hasGps = true
                    "no", "false", "0" -> hasGps = false
                    else -> nameTerms += raw
                }

                "near" ->
                    parseNear(value)?.let { near = it } ?: nameTerms.add(raw)

                "location" ->
                    parseNear(value)?.let { near = it }
                        ?: if (value.isNotBlank()) locationText = value else nameTerms.add(raw)

                "after" ->
                    parseDate(value)?.let { after = it } ?: nameTerms.add(raw)

                "before" ->
                    parseDate(value)?.let { before = it } ?: nameTerms.add(raw)

                "date", "taken" ->
                    parseDate(value)?.let {
                        after = it
                        before = it + DAY_MILLIS
                    } ?: nameTerms.add(raw)

                "year" ->
                    parseYear(value)?.let {
                        after = it.first
                        before = it.second
                    } ?: nameTerms.add(raw)

                "month" ->
                    parseMonth(value)?.let {
                        after = it.first
                        before = it.second
                    } ?: nameTerms.add(raw)

                else -> nameTerms += raw
            }
        }

        return ParsedMediaSearch(
            type = type,
            nameTerms = nameTerms,
            album = album,
            after = after,
            before = before,
            make = make,
            model = model,
            lens = lens,
            iso = iso,
            focalLength = focalLength,
            aperture = aperture,
            hasGps = hasGps,
            near = near,
            exifTerms = exifTerms,
            locationText = locationText
        )
    }

    private fun tokenize(rawQuery: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false

        rawQuery.forEach { char ->
            when {
                char == '"' -> quoted = !quoted
                char.isWhitespace() && !quoted -> {
                    if (current.isNotEmpty()) {
                        result += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun parseDate(value: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }.parse(value)?.time
    }.getOrNull()

    private fun parseYear(value: String): Pair<Long, Long>? = runCatching {
        val year = value.toInt()
        if (year !in 1..9999) return null
        val zone = TimeZone.getDefault()
        val start = Calendar.getInstance(zone).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val end = Calendar.getInstance(zone).apply {
            clear()
            set(Calendar.YEAR, year + 1)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        start to end
    }.getOrNull()

    private fun parseMonth(value: String): Pair<Long, Long>? = runCatching {
        val parts = value.split("-")
        if (parts.size != 2) return null
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        if (year !in 1..9999 || month !in 1..12) return null

        val zone = TimeZone.getDefault()
        val start = Calendar.getInstance(zone).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val end = Calendar.getInstance(zone).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        start to end
    }.getOrNull()

    private fun parseNear(value: String): Near? = runCatching {
        val parts = value.split(",")
        if (parts.size !in 2..3) return null

        val lat = parts[0].trim().toDouble()
        val lon = parts[1].trim().toDouble()
        val radius = parts.getOrNull(2)
            ?.trim()
            ?.removeSuffix("km")
            ?.toDoubleOrNull()
            ?: 5.0

        if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || radius < 0.0) {
            return null
        }

        Near(lat, lon, radius)
    }.getOrNull()

    private const val DAY_MILLIS = 86_400_000L
}
