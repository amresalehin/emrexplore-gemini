package com.example.data.media

import android.content.Context
import android.location.Geocoder
import android.media.ExifInterface
import android.os.Build
import android.provider.MediaStore
import com.example.data.local.AppDatabase
import com.example.data.local.MediaMetadataEntity
import com.example.data.local.PlaceSearchCacheEntity
import com.example.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MediaMetadataRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(appContext)
    private val metadataDao = database.mediaMetadataDao()
    private val placeDao = database.placeSearchCacheDao()

    suspend fun getOrRead(item: MediaItem, requireOriginalLocation: Boolean): MediaMetadataEntity =
        withContext(Dispatchers.IO) {
            val key = item.uri.toString()
            val cached = metadataDao.get(key)
            if (cached != null && cached.size == item.size && cached.dateAdded == item.dateAdded) {
                return@withContext cached
            }
            val extracted = readExif(item, requireOriginalLocation)
            metadataDao.insertOrUpdate(extracted)
            extracted
        }

    private fun readExif(item: MediaItem, requireOriginalLocation: Boolean): MediaMetadataEntity {
        if (item.isVideo) {
            return MediaMetadataEntity(
                uri = item.uri.toString(),
                path = item.path,
                size = item.size,
                dateAdded = item.dateAdded,
                searchableText = item.name.lowercase(Locale.US)
            )
        }

        return try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requireOriginalLocation) {
                MediaStore.setRequireOriginal(item.uri)
            } else {
                item.uri
            }

            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                val lens = (
                    exif.getAttribute("LensModel").orEmpty() + " " +
                        exif.getAttribute("LensMake").orEmpty()
                    ).trim().ifBlank { null }

                val iso = exif.getAttributeInt(
                    ExifInterface.TAG_ISO_SPEED_RATINGS,
                    -1
                ).takeIf { it >= 0 }

                val aperture = exif.getAttributeDouble(
                    ExifInterface.TAG_F_NUMBER,
                    Double.NaN
                ).takeIf { it.isFinite() && it > 0.0 }

                val focalLength = exif.getAttributeDouble(
                    ExifInterface.TAG_FOCAL_LENGTH,
                    Double.NaN
                ).takeIf { it.isFinite() && it > 0.0 }

                val gps = FloatArray(2)
                val hasGps = exif.getLatLong(gps)

                val capturedAt = parseExifTimestamp(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                )

                val searchableText = listOf(
                    item.name,
                    make,
                    model,
                    lens,
                    exif.getAttribute(ExifInterface.TAG_ARTIST),
                    exif.getAttribute(ExifInterface.TAG_COPYRIGHT),
                    exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
                    exif.getAttribute(ExifInterface.TAG_USER_COMMENT),
                    exif.getAttribute(ExifInterface.TAG_SOFTWARE),
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                    exif.getAttribute(ExifInterface.TAG_DATETIME),
                    exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                ).filterNotNull()
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .lowercase(Locale.US)

                MediaMetadataEntity(
                    uri = item.uri.toString(),
                    path = item.path,
                    size = item.size,
                    dateAdded = item.dateAdded,
                    make = make,
                    model = model,
                    lens = lens,
                    iso = iso,
                    aperture = aperture,
                    focalLength = focalLength,
                    latitude = gps[0].toDouble().takeIf { hasGps },
                    longitude = gps[1].toDouble().takeIf { hasGps },
                    hasGps = hasGps,
                    capturedAt = capturedAt,
                    searchableText = searchableText
                )
            } ?: emptyMetadata(item)
        } catch (_: SecurityException) {
            emptyMetadata(item)
        } catch (_: Exception) {
            emptyMetadata(item)
        }
    }

    private fun emptyMetadata(item: MediaItem) = MediaMetadataEntity(
        uri = item.uri.toString(),
        path = item.path,
        size = item.size,
        dateAdded = item.dateAdded,
        searchableText = item.name.lowercase(Locale.US)
    )

    private fun parseExifTimestamp(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                isLenient = false
                timeZone = java.util.TimeZone.getDefault()
            }.parse(value)?.time
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    suspend fun resolvePlace(query: String): PlaceSearchCacheEntity? = withContext(Dispatchers.IO) {
        val normalized = query.trim().lowercase(Locale.US)
        if (normalized.isBlank()) return@withContext null
        placeDao.get(normalized)?.let { return@withContext it }

        try {
            val address = Geocoder(appContext, Locale.getDefault())
                .getFromLocationName(normalized, 1)
                ?.firstOrNull() ?: return@withContext null

            val cached = PlaceSearchCacheEntity(
                query = normalized,
                latitude = address.latitude,
                longitude = address.longitude,
                label = listOfNotNull(
                    address.locality,
                    address.adminArea,
                    address.countryName
                ).joinToString(", ")
            )
            placeDao.insertOrUpdate(cached)
            cached
        } catch (_: Exception) {
            null
        }
    }
}
