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

    fun generateFormattedReport(report: MetadataReport): String = buildString {
        appendLine("==========================================")
        appendLine("EMREXPLORE METADATA INSPECTION REPORT")
        appendLine("==========================================")
        appendLine("File: " + report.fileName)
        appendLine("Path: " + report.filePath)
        appendLine("Size: " + report.fileSize + " bytes")
        appendLine("MIME: " + report.mimeType)
        appendLine()
        appendLine("--- QUICK SUMMARY ---")
        val s = report.summary
        if (!s.make.isNullOrBlank() || !s.model.isNullOrBlank()) appendLine("Camera: " + listOfNotNull(s.make, s.model).joinToString(" "))
        if (!s.lensModel.isNullOrBlank()) appendLine("Lens: " + s.lensModel)
        if (!s.dateTimeOriginal.isNullOrBlank()) appendLine("Date: " + s.dateTimeOriginal)
        if (s.imageWidth > 0 && s.imageHeight > 0) appendLine("Resolution: " + s.imageWidth + " x " + s.imageHeight)
        if (!s.artist.isNullOrBlank()) appendLine("Artist: " + s.artist)
        if (!s.copyright.isNullOrBlank()) appendLine("Copyright: " + s.copyright)
        if (!s.description.isNullOrBlank()) appendLine("Description: " + s.description)
        if (s.keywords.isNotEmpty()) appendLine("Keywords: " + s.keywords.joinToString(", "))
        if (s.latitude != null && s.longitude != null) appendLine("GPS: " + s.latitude + ", " + s.longitude)
        appendLine()
        appendLine("--- EXIFTOOL METADATA TREE ---")
        report.exifDirectories.forEach { directory ->
            appendLine("[" + directory.name + "]")
            directory.tags.forEach { tag ->
                appendLine("  " + tag.name + " = " + tag.formattedValue)
            }
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
