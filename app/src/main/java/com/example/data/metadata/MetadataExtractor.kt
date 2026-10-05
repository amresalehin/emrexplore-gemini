package com.example.data.metadata

import android.content.Context
import android.net.Uri
import java.io.File

class MetadataExtractor(private val context: Context) {
    private val exifTool = ExifTool(context)

    suspend fun extract(file: File): MetadataReport {
        val parsed = exifTool.readJson(file).let(ExifToolMetadataParser::parse)
        return MetadataReport(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            mimeType = mimeType(file.name),
            summary = parsed.summary,
            exifDirectories = parsed.directories,
            iptcReport = IptcReport(
                hasIptc = parsed.directories.any { it.name.equals("IPTC", true) },
                groups = emptyList(),
                allDatasets = emptyList()
            ),
            xmpReport = XmpReport(
                hasXmp = parsed.directories.any { it.name.equals("XMP", true) },
                rawXml = "",
                schemas = emptyList(),
                allProperties = emptyList()
            )
        )
    }

    suspend fun extractFromUri(uri: Uri, name: String, size: Long, path: String): MetadataReport {
        return try {
            val parsed = exifTool.readJson(uri, name).let(ExifToolMetadataParser::parse)
            MetadataReport(
                fileName = name,
                filePath = path.ifBlank { uri.toString() },
                fileSize = size,
                mimeType = mimeType(name),
                summary = parsed.summary,
                exifDirectories = parsed.directories,
                iptcReport = IptcReport(
                    hasIptc = parsed.directories.any { it.name.equals("IPTC", true) },
                    groups = emptyList(),
                    allDatasets = emptyList()
                ),
                xmpReport = XmpReport(
                    hasXmp = parsed.directories.any { it.name.equals("XMP", true) },
                    rawXml = "",
                    schemas = emptyList(),
                    allProperties = emptyList()
                )
            )
        } catch (_: Exception) {
            MetadataReport(
                fileName = name,
                filePath = path.ifBlank { uri.toString() },
                fileSize = size,
                mimeType = mimeType(name),
                summary = MetadataSummary(),
                exifDirectories = emptyList(),
                iptcReport = IptcReport(false, emptyList(), emptyList()),
                xmpReport = XmpReport(false, "", emptyList(), emptyList())
            )
        }
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "dng" -> "image/x-adobe-dng"
        "tif", "tiff" -> "image/tiff"
        "avif" -> "image/avif"
        "gif" -> "image/gif"
        else -> "image/*"
    }
}
