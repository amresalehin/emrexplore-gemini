package com.example.data.metadata

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

class MetadataExtractor(private val context: Context) {

    private val exifParser = ExifTreeParser()
    private val iptcParser = IptcParser()
    private val xmpParser = XmpParser()

    fun extract(file: File): MetadataReport {
        val exifTree = exifParser.parse(file)
        val iptcReport = iptcParser.parse(file)
        val xmpReport = xmpParser.parse(file)
        val segments = try {
            val bytes = file.inputStream().use { it.readBytes() }
            detectJpegSegments(bytes)
        } catch (e: Exception) {
            emptyList()
        }

        val summary = buildSummary(file.name, exifTree, iptcReport, xmpReport)

        return MetadataReport(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            mimeType = getMimeType(file.name),
            summary = summary,
            exifDirectories = exifTree,
            iptcReport = iptcReport,
            xmpReport = xmpReport,
            segments = segments
        )
    }

    fun extractFromUri(uri: Uri, name: String, size: Long, path: String): MetadataReport {
        val tempFile = try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val temp = File.createTempFile("inspect_", ".tmp", context.cacheDir)
                temp.outputStream().use { out -> inputStream.copyTo(out) }
                temp
            } else null
        } catch (e: Exception) {
            null
        }

        if (tempFile != null && tempFile.exists()) {
            val rep = extract(tempFile)
            tempFile.delete()
            return rep.copy(
                fileName = name,
                filePath = path.ifEmpty { uri.toString() },
                fileSize = if (size > 0) size else rep.fileSize
            )
        }

        // If file direct path exists
        val directFile = File(path)
        if (directFile.exists() && directFile.canRead()) {
            return extract(directFile)
        }

        // Minimal fallback
        return MetadataReport(
            fileName = name,
            filePath = path,
            fileSize = size,
            mimeType = getMimeType(name),
            summary = MetadataSummary(),
            exifDirectories = emptyList(),
            iptcReport = IptcReport(false, emptyList(), emptyList()),
            xmpReport = XmpReport(false, "", emptyList(), emptyList())
        )
    }

    private fun buildSummary(
        fileName: String,
        exifTree: List<ExifDirectoryNode>,
        iptc: IptcReport,
        xmp: XmpReport
    ): MetadataSummary {
        val allExifTags = exifTree.flatMap { it.tags }.associateBy { it.name.lowercase() }
        val allIptcDatasets = iptc.allDatasets.associateBy { it.name.lowercase() }
        val allXmpProps = xmp.allProperties.associateBy { it.propertyName.lowercase() }

        // Camera info
        val make = allExifTags["make"]?.formattedValue
            ?: allXmpProps["make"]?.value
        val model = allExifTags["model"]?.formattedValue
            ?: allXmpProps["model"]?.value
        val lensModel = allExifTags["lensmodel"]?.formattedValue
            ?: allXmpProps["lensmodel"]?.value
            ?: allXmpProps["lens"]?.value

        // Exposure info
        val exposureTime = allExifTags["exposuretime"]?.formattedValue
            ?: allXmpProps["exposuretime"]?.value
        val fNumber = allExifTags["fnumber"]?.formattedValue
            ?: allXmpProps["fnumber"]?.value
            ?: allXmpProps["aperturevalue"]?.value
        val iso = allExifTags["isospeedratings / iso"]?.formattedValue
            ?: allExifTags["iso"]?.formattedValue
            ?: allXmpProps["isospeedratings"]?.value
        val focalLength = allExifTags["focallength"]?.formattedValue
            ?: allXmpProps["focallength"]?.value
        val focalLength35mm = allExifTags["focallengthin35mmfilm"]?.formattedValue

        val dateTimeOriginal = allExifTags["datetimeoriginal"]?.formattedValue
            ?: allExifTags["datetime (modified)"]?.formattedValue
            ?: allXmpProps["createdate"]?.value
            ?: allIptcDatasets["date created"]?.value

        // Resolution
        val width = allExifTags["pixelxdimension"]?.formattedValue?.filter { it.isDigit() }?.toIntOrNull()
            ?: allExifTags["imagewidth"]?.formattedValue?.filter { it.isDigit() }?.toIntOrNull()
            ?: allXmpProps["imagewidth"]?.value?.toIntOrNull()
            ?: 0
        val height = allExifTags["pixelydimension"]?.formattedValue?.filter { it.isDigit() }?.toIntOrNull()
            ?: allExifTags["imagelength"]?.formattedValue?.filter { it.isDigit() }?.toIntOrNull()
            ?: allXmpProps["imageheight"]?.value?.toIntOrNull()
            ?: 0

        // GPS
        val latStr = allExifTags["gpslatitude"]?.formattedValue
        val latRef = allExifTags["gpslatituderef"]?.formattedValue ?: "N"
        val lonStr = allExifTags["gpslongitude"]?.formattedValue
        val lonRef = allExifTags["gpslongituderef"]?.formattedValue ?: "W"
        val altStr = allExifTags["gpsaltitude"]?.formattedValue

        val latitude = parseGpsCoordinate(latStr, latRef)
        val longitude = parseGpsCoordinate(lonStr, lonRef)
        val altitude = altStr?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()

        // Content
        val title = allIptcDatasets["object name / title"]?.value
            ?: allXmpProps["title"]?.value
        val headline = allIptcDatasets["headline"]?.value
            ?: allXmpProps["headline"]?.value
        val description = allIptcDatasets["caption / abstract"]?.value
            ?: allXmpProps["description"]?.value
            ?: allExifTags["imagedescription"]?.formattedValue

        // Keywords
        val keywords = mutableListOf<String>()
        val iptcKeywords = iptc.allDatasets.filter { it.name.equals("Keywords", ignoreCase = true) }.map { it.value }
        keywords.addAll(iptcKeywords)
        val xmpSubject = allXmpProps["subject"]?.listValues
        if (!xmpSubject.isNullOrEmpty()) {
            keywords.addAll(xmpSubject)
        }

        // Creator & Rights
        val artist = allIptcDatasets["by-line (creator)"]?.value
            ?: allXmpProps["creator"]?.value
            ?: allExifTags["artist"]?.formattedValue
        val copyright = allIptcDatasets["copyright notice"]?.value
            ?: allXmpProps["rights"]?.value
            ?: allExifTags["copyright"]?.formattedValue

        // Location
        val city = allIptcDatasets["city"]?.value ?: allXmpProps["city"]?.value
        val state = allIptcDatasets["province / state"]?.value ?: allXmpProps["state"]?.value
        val country = allIptcDatasets["country name"]?.value ?: allXmpProps["country"]?.value

        return MetadataSummary(
            make = make,
            model = model,
            lensModel = lensModel,
            exposureTime = exposureTime,
            fNumber = fNumber,
            iso = iso,
            focalLength = focalLength,
            focalLength35mm = focalLength35mm,
            dateTimeOriginal = dateTimeOriginal,
            imageWidth = width,
            imageHeight = height,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            flash = allExifTags["flash"]?.formattedValue,
            whiteBalance = allExifTags["whitebalance"]?.formattedValue,
            meteringMode = allExifTags["meteringmode"]?.formattedValue,
            exposureProgram = allExifTags["exposureprogram"]?.formattedValue,
            software = allExifTags["software"]?.formattedValue ?: allXmpProps["creatortool"]?.value,
            artist = artist,
            copyright = copyright,
            title = title,
            headline = headline,
            description = description,
            keywords = keywords.distinct().filter { it.isNotBlank() },
            city = city,
            state = state,
            country = country
        )
    }

    private fun parseGpsCoordinate(coordStr: String?, ref: String): Double? {
        if (coordStr.isNullOrBlank()) return null
        try {
            // Check decimal degrees
            val d = coordStr.filter { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull()
            if (d != null && !coordStr.contains(",")) {
                return if (ref.equals("S", ignoreCase = true) || ref.equals("W", ignoreCase = true)) -Math.abs(d) else Math.abs(d)
            }

            // Check deg/min/sec format (e.g. "37/1, 46/1, 2988/100")
            val parts = coordStr.split(",")
            if (parts.size >= 3) {
                fun parsePart(p: String): Double {
                    val trimmed = p.trim()
                    if (trimmed.contains("/")) {
                        val num = trimmed.substringBefore("/").toDoubleOrNull() ?: 0.0
                        val den = trimmed.substringAfter("/").toDoubleOrNull() ?: 1.0
                        return if (den != 0.0) num / den else 0.0
                    }
                    return trimmed.toDoubleOrNull() ?: 0.0
                }
                val deg = parsePart(parts[0])
                val min = parsePart(parts[1])
                val sec = parsePart(parts[2])
                var total = deg + (min / 60.0) + (sec / 3600.0)
                if (ref.equals("S", ignoreCase = true) || ref.equals("W", ignoreCase = true)) {
                    total = -total
                }
                return total
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    private fun detectJpegSegments(bytes: ByteArray): List<SegmentItem> {
        val segments = mutableListOf<SegmentItem>()
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
            return segments
        }
        segments.add(SegmentItem("SOI", "0xFFD8", "Start of Image", 2))

        var offset = 2
        while (offset < bytes.size - 4) {
            if (bytes[offset] != 0xFF.toByte()) {
                offset++
                continue
            }
            val marker = bytes[offset + 1].toInt() and 0xFF
            val markerHex = "0xFF" + marker.toString(16).uppercase().padStart(2, '0')

            if (marker == 0xD9) { // EOI
                segments.add(SegmentItem("EOI", markerHex, "End of Image", 2))
                break
            }
            if (marker == 0xDA) { // SOS
                val len = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
                segments.add(SegmentItem("SOS", markerHex, "Start of Scan (Compressed Image Stream)", len + 2))
                break
            }

            if (offset + 3 >= bytes.size) break
            val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)

            val desc = when (marker) {
                0xE0 -> "APP0 (JFIF Header)"
                0xE1 -> {
                    val sig = String(bytes, offset + 4, minOf(8, length - 2), Charsets.US_ASCII)
                    if (sig.startsWith("Exif")) "APP1 (EXIF Metadata)"
                    else if (sig.startsWith("http")) "APP1 (XMP Metadata Packet)"
                    else "APP1"
                }
                0xE2 -> "APP2 (ICC Profile / Extended XMP)"
                0xED -> "APP13 (Photoshop 3.0 IPTC-IIM)"
                0xEE -> "APP14 (Adobe)"
                0xDB -> "DQT (Define Quantization Table)"
                0xC0 -> "SOF0 (Baseline DCT Frame Header)"
                0xC2 -> "SOF2 (Progressive DCT Frame Header)"
                0xC4 -> "DHT (Define Huffman Table)"
                0xFE -> "COM (Comment)"
                else -> "Segment $markerHex"
            }

            segments.add(SegmentItem(getMarkerName(marker), markerHex, desc, length + 2))
            offset += 2 + length
        }
        return segments
    }

    private fun getMarkerName(marker: Int): String = when (marker) {
        0xE0 -> "APP0"
        0xE1 -> "APP1"
        0xE2 -> "APP2"
        0xED -> "APP13"
        0xEE -> "APP14"
        0xDB -> "DQT"
        0xC0 -> "SOF0"
        0xC2 -> "SOF2"
        0xC4 -> "DHT"
        0xFE -> "COM"
        0xDA -> "SOS"
        0xD9 -> "EOI"
        else -> "0x" + marker.toString(16).uppercase()
    }

    private fun getMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "dng" -> "image/x-adobe-dng"
            "tif", "tiff" -> "image/tiff"
            else -> "image/*"
        }
    }

    fun generateFormattedReport(report: MetadataReport): String {
        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("EMREXPLORE METADATA INSPECTION REPORT")
        sb.appendLine("==========================================")
        sb.appendLine("File: ${report.fileName}")
        sb.appendLine("Path: ${report.filePath}")
        sb.appendLine("Size: ${report.fileSize} bytes")
        sb.appendLine("MIME: ${report.mimeType}")
        sb.appendLine()

        val s = report.summary
        sb.appendLine("--- QUICK SUMMARY ---")
        if (!s.make.isNullOrBlank() || !s.model.isNullOrBlank()) sb.appendLine("Camera: ${s.make ?: ""} ${s.model ?: ""}".trim())
        if (!s.lensModel.isNullOrBlank()) sb.appendLine("Lens: ${s.lensModel}")
        if (!s.exposureTime.isNullOrBlank() || !s.fNumber.isNullOrBlank() || !s.iso.isNullOrBlank()) {
            sb.appendLine("Settings: ${s.exposureTime ?: ""} · ${s.fNumber ?: ""} · ISO ${s.iso ?: ""} · ${s.focalLength ?: ""}".trim())
        }
        if (s.imageWidth > 0 && s.imageHeight > 0) sb.appendLine("Resolution: ${s.imageWidth} x ${s.imageHeight}")
        if (s.latitude != null && s.longitude != null) sb.appendLine("GPS: ${s.latitude}, ${s.longitude} (${s.altitude ?: 0.0}m)")
        if (!s.artist.isNullOrBlank()) sb.appendLine("Author/Artist: ${s.artist}")
        if (!s.copyright.isNullOrBlank()) sb.appendLine("Copyright: ${s.copyright}")
        if (!s.headline.isNullOrBlank()) sb.appendLine("Headline: ${s.headline}")
        if (!s.description.isNullOrBlank()) sb.appendLine("Description: ${s.description}")
        if (s.keywords.isNotEmpty()) sb.appendLine("Keywords: ${s.keywords.joinToString(", ")}")
        sb.appendLine()

        // EXIF TREE
        sb.appendLine("--- FULL EXIF TREE (${report.exifDirectories.sumOf { it.tags.size }} tags) ---")
        for (dir in report.exifDirectories) {
            sb.appendLine("[${dir.name}] (${dir.tags.size} tags)")
            for (tag in dir.tags) {
                sb.appendLine("  ${tag.tagIdHex} ${tag.name} [${tag.dataType}]: ${tag.formattedValue}")
            }
        }
        sb.appendLine()

        // IPTC-IIM
        sb.appendLine("--- IPTC-IIM (${report.iptcReport.allDatasets.size} datasets) ---")
        for (grp in report.iptcReport.groups) {
            sb.appendLine("[${grp.categoryName}]")
            for (ds in grp.datasets) {
                sb.appendLine("  ${ds.tagCode} ${ds.name}: ${ds.value}")
            }
        }
        sb.appendLine()

        // XMP
        sb.appendLine("--- XMP SCHEMAS (${report.xmpReport.allProperties.size} properties) ---")
        for (schema in report.xmpReport.schemas) {
            sb.appendLine("[${schema.displayName}]")
            for (prop in schema.properties) {
                sb.appendLine("  ${prop.qualifiedName} = ${prop.value}")
            }
        }

        return sb.toString()
    }
}
