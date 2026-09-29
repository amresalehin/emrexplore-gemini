package com.example.data.metadata

import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExifTreeParser {

    fun parse(file: File): List<ExifDirectoryNode> {
        val bytes = try {
            file.inputStream().use { it.readBytes() }
        } catch (e: Exception) {
            null
        }

        val parsedDirectories = if (bytes != null) {
            parseFromBytes(bytes)
        } else {
            emptyList()
        }

        // Also query standard Android ExifInterface to enrich or merge any tags missed by binary parser
        return try {
            val exifInterface = ExifInterface(file.absolutePath)
            mergeWithAndroidExif(parsedDirectories, exifInterface)
        } catch (e: Exception) {
            if (parsedDirectories.isNotEmpty()) parsedDirectories else fallbackDirectoryList()
        }
    }

    fun parseFromBytes(imageBytes: ByteArray): List<ExifDirectoryNode> {
        val exifData = extractExifPayload(imageBytes) ?: return emptyList()
        return parseTiffExif(exifData)
    }

    private fun extractExifPayload(bytes: ByteArray): ByteArray? {
        if (bytes.size < 4) return null

        // Check if raw TIFF (II or MM)
        if ((bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte()) ||
            (bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte())) {
            return bytes
        }

        // JPEG check (0xFF 0xD8)
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            var offset = 2
            while (offset < bytes.size - 4) {
                if (bytes[offset] != 0xFF.toByte()) {
                    offset++
                    continue
                }
                val marker = bytes[offset + 1].toInt() and 0xFF
                if (marker == 0xDA || marker == 0xD9) break // SOS or EOI

                val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
                if (offset + 2 + length > bytes.size) break

                // APP1 marker is 0xE1
                if (marker == 0xE1 && length >= 8) {
                    val header = String(bytes, offset + 4, 4)
                    if (header == "Exif") {
                        val payloadStart = offset + 10 // skip marker (2), length (2), 'Exif\0\0' (6)
                        val payloadLength = (offset + 2 + length) - payloadStart
                        if (payloadLength > 0 && payloadStart + payloadLength <= bytes.size) {
                            val tiffBytes = ByteArray(payloadLength)
                            System.arraycopy(bytes, payloadStart, tiffBytes, 0, payloadLength)
                            return tiffBytes
                        }
                    }
                }
                offset += 2 + length
            }
        }

        return null
    }

    private fun parseTiffExif(tiffBytes: ByteArray): List<ExifDirectoryNode> {
        if (tiffBytes.size < 8) return emptyList()

        val isLittleEndian = tiffBytes[0] == 'I'.code.toByte() && tiffBytes[1] == 'I'.code.toByte()
        val isBigEndian = tiffBytes[0] == 'M'.code.toByte() && tiffBytes[1] == 'M'.code.toByte()
        if (!isLittleEndian && !isBigEndian) return emptyList()

        val buffer = ByteBuffer.wrap(tiffBytes)
        val order = if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        buffer.order(order)

        val fortyTwo = buffer.getShort(2).toInt() and 0xFFFF
        if (fortyTwo != 42 && fortyTwo != 0x002A) return emptyList()

        val ifd0Offset = buffer.getInt(4).toLong() and 0xFFFFFFFFL

        val ifd0Tags = mutableListOf<MetadataTagItem>()
        val exifSubIfdTags = mutableListOf<MetadataTagItem>()
        val gpsIfdTags = mutableListOf<MetadataTagItem>()
        val interopIfdTags = mutableListOf<MetadataTagItem>()
        val ifd1Tags = mutableListOf<MetadataTagItem>()

        var exifSubIfdOffset: Long? = null
        var gpsIfdOffset: Long? = null
        var interopIfdOffset: Long? = null

        // Parse IFD0
        var nextIfdOffset: Long = 0L
        if (ifd0Offset > 0 && ifd0Offset < tiffBytes.size - 2) {
            val (tags, nextOffset, subPointers) = parseIfd(buffer, ifd0Offset, "IFD0 (Main Image)")
            ifd0Tags.addAll(tags)
            nextIfdOffset = nextOffset
            exifSubIfdOffset = subPointers[0x8769]
            gpsIfdOffset = subPointers[0x8825]
        }

        // Parse Exif SubIFD
        if (exifSubIfdOffset != null && exifSubIfdOffset > 0 && exifSubIfdOffset < tiffBytes.size - 2) {
            val (tags, _, subPointers) = parseIfd(buffer, exifSubIfdOffset, "Exif SubIFD (Photo Details)")
            exifSubIfdTags.addAll(tags)
            interopIfdOffset = subPointers[0xA005]
        }

        // Parse GPS IFD
        if (gpsIfdOffset != null && gpsIfdOffset > 0 && gpsIfdOffset < tiffBytes.size - 2) {
            val (tags, _, _) = parseIfd(buffer, gpsIfdOffset, "GPS IFD (Location)")
            gpsIfdTags.addAll(tags)
        }

        // Parse Interoperability IFD
        if (interopIfdOffset != null && interopIfdOffset > 0 && interopIfdOffset < tiffBytes.size - 2) {
            val (tags, _, _) = parseIfd(buffer, interopIfdOffset, "Interoperability IFD")
            interopIfdTags.addAll(tags)
        }

        // Parse IFD1 (Thumbnail)
        if (nextIfdOffset > 0 && nextIfdOffset < tiffBytes.size - 2) {
            val (tags, _, _) = parseIfd(buffer, nextIfdOffset, "IFD1 (Thumbnail)")
            ifd1Tags.addAll(tags)
        }

        val result = mutableListOf<ExifDirectoryNode>()
        if (ifd0Tags.isNotEmpty()) {
            result.add(ExifDirectoryNode("IFD0 (Primary Image)", "Core camera and capture baseline directory", ifd0Tags))
        }
        if (exifSubIfdTags.isNotEmpty()) {
            result.add(ExifDirectoryNode("Exif SubIFD (Photo)", "Exposure, sensor, optics and camera settings", exifSubIfdTags))
        }
        if (gpsIfdTags.isNotEmpty()) {
            result.add(ExifDirectoryNode("GPS IFD", "Geographic coordinates, altitude, and timestamp", gpsIfdTags))
        }
        if (interopIfdTags.isNotEmpty()) {
            result.add(ExifDirectoryNode("Interoperability IFD", "Interoperability rules and specifications", interopIfdTags))
        }
        if (ifd1Tags.isNotEmpty()) {
            result.add(ExifDirectoryNode("IFD1 (Thumbnail)", "Embedded preview thumbnail metadata", ifd1Tags))
        }

        return result
    }

    private data class IfdParseResult(
        val tags: List<MetadataTagItem>,
        val nextIfdOffset: Long,
        val specialPointers: Map<Int, Long>
    )

    private fun parseIfd(buffer: ByteBuffer, offset: Long, directoryName: String): IfdParseResult {
        val tags = mutableListOf<MetadataTagItem>()
        val specialPointers = mutableMapOf<Int, Long>()
        if (offset < 0 || offset > buffer.capacity() - 2) {
            return IfdParseResult(emptyList(), 0L, emptyMap())
        }

        val numEntries = buffer.getShort(offset.toInt()).toInt() and 0xFFFF
        var curPos = offset.toInt() + 2

        for (i in 0 until numEntries) {
            if (curPos + 12 > buffer.capacity()) break

            val tagId = buffer.getShort(curPos).toInt() and 0xFFFF
            val type = buffer.getShort(curPos + 2).toInt() and 0xFFFF
            val count = buffer.getInt(curPos + 4).toLong() and 0xFFFFFFFFL
            val valueOrOffset = buffer.getInt(curPos + 8).toLong() and 0xFFFFFFFFL

            // Track sub-IFD pointers
            if (tagId == 0x8769 || tagId == 0x8825 || tagId == 0xA005) {
                specialPointers[tagId] = valueOrOffset
            }

            val typeName = getTypeName(type)
            val tagInfo = getTagDefinition(tagId, directoryName)
            val tagName = tagInfo.name
            val (formattedVal, rawVal) = readTagValue(buffer, type, count, curPos + 8, valueOrOffset, tagId)

            tags.add(
                MetadataTagItem(
                    tagIdHex = "0x" + tagId.toString(16).uppercase().padStart(4, '0'),
                    tagIdInt = tagId,
                    name = tagName,
                    directory = directoryName,
                    formattedValue = tagInfo.formatter(formattedVal),
                    rawValue = rawVal,
                    dataType = typeName
                )
            )

            curPos += 12
        }

        val nextIfdOffset = if (curPos + 4 <= buffer.capacity()) {
            buffer.getInt(curPos).toLong() and 0xFFFFFFFFL
        } else 0L

        return IfdParseResult(tags, nextIfdOffset, specialPointers)
    }

    private fun readTagValue(
        buffer: ByteBuffer,
        type: Int,
        count: Long,
        inlinePos: Int,
        valueOrOffset: Long,
        tagId: Int
    ): Pair<String, String> {
        val bytesPerUnit = getTypeBytes(type)
        val totalBytes = count * bytesPerUnit

        val dataOffset = if (totalBytes <= 4) inlinePos else valueOrOffset.toInt()
        if (dataOffset < 0 || dataOffset + totalBytes > buffer.capacity()) {
            return Pair("0x${valueOrOffset.toString(16)}", "0x${valueOrOffset.toString(16)}")
        }

        return when (type) {
            2 -> { // ASCII
                val strBytes = ByteArray(totalBytes.toInt())
                val oldPos = buffer.position()
                buffer.position(dataOffset)
                buffer.get(strBytes)
                buffer.position(oldPos)
                val s = String(strBytes, Charsets.UTF_8).trimEnd('\u0000', ' ')
                Pair(s, s)
            }
            3 -> { // SHORT (16-bit unsigned)
                if (count == 1L) {
                    val s = buffer.getShort(dataOffset).toInt() and 0xFFFF
                    Pair(s.toString(), s.toString())
                } else {
                    val list = mutableListOf<Int>()
                    for (c in 0 until minOf(count.toInt(), 32)) {
                        list.add(buffer.getShort(dataOffset + c * 2).toInt() and 0xFFFF)
                    }
                    Pair(list.joinToString(", "), list.toString())
                }
            }
            4 -> { // LONG (32-bit unsigned)
                if (count == 1L) {
                    val l = buffer.getInt(dataOffset).toLong() and 0xFFFFFFFFL
                    Pair(l.toString(), l.toString())
                } else {
                    val list = mutableListOf<Long>()
                    for (c in 0 until minOf(count.toInt(), 32)) {
                        list.add(buffer.getInt(dataOffset + c * 4).toLong() and 0xFFFFFFFFL)
                    }
                    Pair(list.joinToString(", "), list.toString())
                }
            }
            5 -> { // RATIONAL (two LONGs: numerator, denominator)
                if (count == 1L) {
                    val num = buffer.getInt(dataOffset).toLong() and 0xFFFFFFFFL
                    val den = buffer.getInt(dataOffset + 4).toLong() and 0xFFFFFFFFL
                    val formatted = formatRational(num, den, tagId)
                    Pair(formatted, "$num/$den")
                } else {
                    val list = mutableListOf<String>()
                    for (c in 0 until minOf(count.toInt(), 16)) {
                        val num = buffer.getInt(dataOffset + c * 8).toLong() and 0xFFFFFFFFL
                        val den = buffer.getInt(dataOffset + c * 8 + 4).toLong() and 0xFFFFFFFFL
                        list.add(formatRational(num, den, tagId))
                    }
                    Pair(list.joinToString(", "), list.toString())
                }
            }
            10 -> { // SRATIONAL (two SLONGs)
                val num = buffer.getInt(dataOffset)
                val den = buffer.getInt(dataOffset + 4)
                val v = if (den != 0) (num.toDouble() / den.toDouble()) else 0.0
                Pair(String.format("%.2f", v), "$num/$den")
            }
            7 -> { // UNDEFINED
                if (tagId == 0x9000 || tagId == 0x0000 || tagId == 0xA000) { // ExifVersion or FlashpixVersion
                    val ver = String(ByteArray(4) { buffer.get(dataOffset + it) }, Charsets.US_ASCII)
                    Pair(ver, ver)
                } else if (totalBytes <= 16) {
                    val hex = (0 until totalBytes.toInt()).joinToString(" ") {
                        String.format("%02X", buffer.get(dataOffset + it))
                    }
                    Pair(hex, hex)
                } else {
                    Pair("[$totalBytes bytes raw binary]", "[$totalBytes bytes]")
                }
            }
            else -> Pair(valueOrOffset.toString(), valueOrOffset.toString())
        }
    }

    private fun formatRational(num: Long, den: Long, tagId: Int): String {
        if (den == 0L) return "$num/0"
        val d = num.toDouble() / den.toDouble()

        // Exposure Time (0x829A)
        if (tagId == 0x829A) {
            return if (d < 1.0 && d > 0.0) {
                val inv = Math.round(1.0 / d)
                "1/$inv sec ($d s)"
            } else {
                "${d}s"
            }
        }

        // FNumber (0x829D) or MaxAperture (0x9205)
        if (tagId == 0x829D || tagId == 0x9205 || tagId == 0x9202) {
            return String.format("ƒ/%.1f", d)
        }

        // Focal Length (0x920A)
        if (tagId == 0x920A) {
            return String.format("%.1f mm", d)
        }

        return if (num % den == 0L) (num / den).toString() else String.format("%.2f", d)
    }

    private fun getTypeName(type: Int): String = when (type) {
        1 -> "BYTE"
        2 -> "ASCII"
        3 -> "SHORT"
        4 -> "LONG"
        5 -> "RATIONAL"
        7 -> "UNDEFINED"
        9 -> "SLONG"
        10 -> "SRATIONAL"
        11 -> "FLOAT"
        12 -> "DOUBLE"
        else -> "TYPE_$type"
    }

    private fun getTypeBytes(type: Int): Int = when (type) {
        1, 2, 7 -> 1
        3, 8 -> 2
        4, 9, 11 -> 4
        5, 10, 12 -> 8
        else -> 1
    }

    private data class TagDefinition(
        val name: String,
        val formatter: (String) -> String = { it }
    )

    private fun getTagDefinition(tagId: Int, directory: String): TagDefinition {
        // Tag definitions map
        return when (tagId) {
            // IFD0 Baseline
            0x010E -> TagDefinition("ImageDescription")
            0x010F -> TagDefinition("Make")
            0x0110 -> TagDefinition("Model")
            0x0112 -> TagDefinition("Orientation") { formatOrientation(it) }
            0x011A -> TagDefinition("XResolution")
            0x011B -> TagDefinition("YResolution")
            0x0128 -> TagDefinition("ResolutionUnit") { if (it == "2") "Inches (dpi)" else if (it == "3") "Centimeters" else it }
            0x0131 -> TagDefinition("Software")
            0x0132 -> TagDefinition("DateTime (Modified)")
            0x013B -> TagDefinition("Artist")
            0x013E -> TagDefinition("WhitePoint")
            0x013F -> TagDefinition("PrimaryChromaticities")
            0x0211 -> TagDefinition("YCbCrCoefficients")
            0x0213 -> TagDefinition("YCbCrPositioning") { if (it == "1") "Centered" else if (it == "2") "Co-sited" else it }
            0x0214 -> TagDefinition("ReferenceBlackWhite")
            0x8298 -> TagDefinition("Copyright")
            0x8769 -> TagDefinition("ExifOffset (SubIFD Pointer)")
            0x8825 -> TagDefinition("GPSInfoOffset (GPS Pointer)")

            // Exif SubIFD
            0x829A -> TagDefinition("ExposureTime")
            0x829D -> TagDefinition("FNumber")
            0x8822 -> TagDefinition("ExposureProgram") { formatExposureProgram(it) }
            0x8827 -> TagDefinition("ISOSpeedRatings / ISO")
            0x8830 -> TagDefinition("SensitivityType")
            0x8832 -> TagDefinition("RecommendedExposureIndex")
            0x9000 -> TagDefinition("ExifVersion")
            0x9003 -> TagDefinition("DateTimeOriginal")
            0x9004 -> TagDefinition("DateTimeDigitized")
            0x9101 -> TagDefinition("ComponentsConfiguration")
            0x9102 -> TagDefinition("CompressedBitsPerPixel")
            0x9201 -> TagDefinition("ShutterSpeedValue")
            0x9202 -> TagDefinition("ApertureValue")
            0x9203 -> TagDefinition("BrightnessValue")
            0x9204 -> TagDefinition("ExposureBiasValue") { "${it} EV" }
            0x9205 -> TagDefinition("MaxApertureValue")
            0x9206 -> TagDefinition("SubjectDistance") { "${it} m" }
            0x9207 -> TagDefinition("MeteringMode") { formatMeteringMode(it) }
            0x9208 -> TagDefinition("LightSource") { formatLightSource(it) }
            0x9209 -> TagDefinition("Flash") { formatFlash(it) }
            0x920A -> TagDefinition("FocalLength")
            0x927C -> TagDefinition("MakerNote")
            0x9286 -> TagDefinition("UserComment")
            0x9290 -> TagDefinition("SubSecTime")
            0x9291 -> TagDefinition("SubSecTimeOriginal")
            0x9292 -> TagDefinition("SubSecTimeDigitized")
            0xA000 -> TagDefinition("FlashpixVersion")
            0xA001 -> TagDefinition("ColorSpace") { if (it == "1") "sRGB" else if (it == "65535" || it == "-1") "Uncalibrated / Adobe RGB" else it }
            0xA002 -> TagDefinition("PixelXDimension") { "$it px" }
            0xA003 -> TagDefinition("PixelYDimension") { "$it px" }
            0xA005 -> TagDefinition("InteroperabilityOffset")
            0xA20E -> TagDefinition("FocalPlaneXResolution")
            0xA20F -> TagDefinition("FocalPlaneYResolution")
            0xA210 -> TagDefinition("FocalPlaneResolutionUnit")
            0xA217 -> TagDefinition("SensingMethod") { if (it == "2") "One-chip color area sensor" else it }
            0xA300 -> TagDefinition("FileSource") { if (it == "3") "DSC (Digital Still Camera)" else it }
            0xA301 -> TagDefinition("SceneType") { if (it == "1") "Directly photographed image" else it }
            0xA401 -> TagDefinition("CustomRendered") { if (it == "0") "Normal process" else if (it == "1") "Custom process" else it }
            0xA402 -> TagDefinition("ExposureMode") { if (it == "0") "Auto exposure" else if (it == "1") "Manual exposure" else if (it == "2") "Auto bracket" else it }
            0xA403 -> TagDefinition("WhiteBalance") { if (it == "0") "Auto" else if (it == "1") "Manual" else it }
            0xA404 -> TagDefinition("DigitalZoomRatio")
            0xA405 -> TagDefinition("FocalLengthIn35mmFilm") { "$it mm" }
            0xA406 -> TagDefinition("SceneCaptureType") { formatSceneCapture(it) }
            0xA407 -> TagDefinition("GainControl") { formatGainControl(it) }
            0xA408 -> TagDefinition("Contrast") { if (it == "0") "Normal" else if (it == "1") "Soft" else if (it == "2") "Hard" else it }
            0xA409 -> TagDefinition("Saturation") { if (it == "0") "Normal" else if (it == "1") "Low saturation" else if (it == "2") "High saturation" else it }
            0xA40A -> TagDefinition("Sharpness") { if (it == "0") "Normal" else if (it == "1") "Soft" else if (it == "2") "Hard" else it }
            0xA420 -> TagDefinition("ImageUniqueID")
            0xA431 -> TagDefinition("BodySerialNumber")
            0xA432 -> TagDefinition("LensSpecification")
            0xA433 -> TagDefinition("LensMake")
            0xA434 -> TagDefinition("LensModel")
            0xA435 -> TagDefinition("LensSerialNumber")

            // GPS IFD
            0x0000 -> TagDefinition("GPSVersionID")
            0x0001 -> TagDefinition("GPSLatitudeRef")
            0x0002 -> TagDefinition("GPSLatitude")
            0x0003 -> TagDefinition("GPSLongitudeRef")
            0x0004 -> TagDefinition("GPSLongitude")
            0x0005 -> TagDefinition("GPSAltitudeRef") { if (it == "0") "Above sea level" else if (it == "1") "Below sea level" else it }
            0x0006 -> TagDefinition("GPSAltitude") { "$it m" }
            0x0007 -> TagDefinition("GPSTimeStamp (UTC)")
            0x0008 -> TagDefinition("GPSSatellites")
            0x0009 -> TagDefinition("GPSStatus")
            0x000A -> TagDefinition("GPSMeasureMode")
            0x000B -> TagDefinition("GPSDOP")
            0x000C -> TagDefinition("GPSSpeedRef")
            0x000D -> TagDefinition("GPSSpeed")
            0x000E -> TagDefinition("GPSTrackRef")
            0x000F -> TagDefinition("GPSTrack")
            0x0010 -> TagDefinition("GPSImgDirectionRef")
            0x0011 -> TagDefinition("GPSImgDirection")
            0x0012 -> TagDefinition("GPSMapDatum")
            0x001D -> TagDefinition("GPSDateStamp")
            0x001B -> TagDefinition("GPSProcessingMethod")

            // Interop IFD
            0x0001 -> if (directory.contains("Interop")) TagDefinition("InteroperabilityIndex") else TagDefinition("GPSLatitudeRef")
            0x0002 -> if (directory.contains("Interop")) TagDefinition("InteroperabilityVersion") else TagDefinition("GPSLatitude")

            // IFD1 Thumbnail
            0x0103 -> TagDefinition("Compression") { if (it == "6") "JPEG compression" else it }
            0x0100 -> TagDefinition("ImageWidth") { "$it px" }
            0x0101 -> TagDefinition("ImageLength") { "$it px" }
            0x0201 -> TagDefinition("JPEGInterchangeFormat (Offset)")
            0x0202 -> TagDefinition("JPEGInterchangeFormatLength") { "$it bytes" }

            else -> TagDefinition("Tag_0x" + tagId.toString(16).uppercase())
        }
    }

    private fun formatOrientation(v: String): String = when (v) {
        "1" -> "Top, left-hand side (Normal / 0°)"
        "2" -> "Top, right-hand side (Mirror horizontal)"
        "3" -> "Bottom, right-hand side (Rotate 180°)"
        "4" -> "Bottom, left-hand side (Mirror vertical)"
        "5" -> "Left-hand side, top (Mirror horizontal and rotate 270° CW)"
        "6" -> "Right-hand side, top (Rotate 90° CW)"
        "7" -> "Right-hand side, bottom (Mirror horizontal and rotate 90° CW)"
        "8" -> "Left-hand side, bottom (Rotate 270° CW)"
        else -> v
    }

    private fun formatExposureProgram(v: String): String = when (v) {
        "0" -> "Not defined"
        "1" -> "Manual"
        "2" -> "Normal program"
        "3" -> "Aperture priority"
        "4" -> "Shutter priority"
        "5" -> "Creative program (depth of field biased)"
        "6" -> "Action program (fast shutter biased)"
        "7" -> "Portrait mode"
        "8" -> "Landscape mode"
        else -> v
    }

    private fun formatMeteringMode(v: String): String = when (v) {
        "0" -> "Unknown"
        "1" -> "Average"
        "2" -> "Center-weighted average"
        "3" -> "Spot"
        "4" -> "Multi-spot"
        "5" -> "Pattern / Multi-segment"
        "6" -> "Partial"
        "255" -> "Other"
        else -> v
    }

    private fun formatLightSource(v: String): String = when (v) {
        "0" -> "Auto / Unknown"
        "1" -> "Daylight"
        "2" -> "Fluorescent"
        "3" -> "Tungsten (incandescent light)"
        "4" -> "Flash"
        "9" -> "Fine weather"
        "10" -> "Cloudy weather"
        "11" -> "Shade"
        else -> v
    }

    private fun formatFlash(v: String): String {
        val f = v.toIntOrNull() ?: return v
        val fired = (f and 0x1) != 0
        return if (!fired) "Flash did not fire" else "Flash fired (mode 0x${f.toString(16)})"
    }

    private fun formatSceneCapture(v: String): String = when (v) {
        "0" -> "Standard"
        "1" -> "Landscape"
        "2" -> "Portrait"
        "3" -> "Night scene"
        else -> v
    }

    private fun formatGainControl(v: String): String = when (v) {
        "0" -> "None"
        "1" -> "Low gain up"
        "2" -> "High gain up"
        "3" -> "Low gain down"
        "4" -> "High gain down"
        else -> v
    }

    private fun mergeWithAndroidExif(
        existing: List<ExifDirectoryNode>,
        exif: ExifInterface
    ): List<ExifDirectoryNode> {
        if (existing.isNotEmpty()) return existing

        val ifd0Tags = mutableListOf<MetadataTagItem>()
        val exifSubIfdTags = mutableListOf<MetadataTagItem>()
        val gpsIfdTags = mutableListOf<MetadataTagItem>()

        // Helper to probe standard attributes
        fun probe(attr: String, tagId: Int, dir: String, dest: MutableList<MetadataTagItem>) {
            val v = exif.getAttribute(attr)
            if (!v.isNullOrEmpty()) {
                val def = getTagDefinition(tagId, dir)
                dest.add(
                    MetadataTagItem(
                        tagIdHex = "0x" + tagId.toString(16).uppercase().padStart(4, '0'),
                        tagIdInt = tagId,
                        name = def.name,
                        directory = dir,
                        formattedValue = def.formatter(v),
                        rawValue = v,
                        dataType = "ASCII"
                    )
                )
            }
        }

        // IFD0
        probe(ExifInterface.TAG_MAKE, 0x010F, "IFD0 (Main Image)", ifd0Tags)
        probe(ExifInterface.TAG_MODEL, 0x0110, "IFD0 (Main Image)", ifd0Tags)
        probe(ExifInterface.TAG_ORIENTATION, 0x0112, "IFD0 (Main Image)", ifd0Tags)
        probe(ExifInterface.TAG_SOFTWARE, 0x0131, "IFD0 (Main Image)", ifd0Tags)
        probe(ExifInterface.TAG_DATETIME, 0x0132, "IFD0 (Main Image)", ifd0Tags)
        probe(ExifInterface.TAG_ARTIST, 0x013B, "IFD0 (Main Image)", ifd0Tags)
        probe(ExifInterface.TAG_COPYRIGHT, 0x8298, "IFD0 (Main Image)", ifd0Tags)
        probe(ExifInterface.TAG_IMAGE_WIDTH, 0x0100, "IFD0 (Main Image)", ifd0Tags)
        probe(ExifInterface.TAG_IMAGE_LENGTH, 0x0101, "IFD0 (Main Image)", ifd0Tags)

        // SubIFD
        probe(ExifInterface.TAG_EXPOSURE_TIME, 0x829A, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_F_NUMBER, 0x829D, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_EXPOSURE_PROGRAM, 0x8822, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_ISO_SPEED_RATINGS, 0x8827, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_DATETIME_ORIGINAL, 0x9003, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_DATETIME_DIGITIZED, 0x9004, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_SHUTTER_SPEED_VALUE, 0x9201, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_APERTURE_VALUE, 0x9202, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, 0x9204, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_METERING_MODE, 0x9207, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_LIGHT_SOURCE, 0x9208, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_FLASH, 0x9209, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_FOCAL_LENGTH, 0x920A, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_WHITE_BALANCE, 0xA403, "Exif SubIFD (Photo Details)", exifSubIfdTags)
        probe(ExifInterface.TAG_SCENE_CAPTURE_TYPE, 0xA406, "Exif SubIFD (Photo Details)", exifSubIfdTags)

        // GPS
        probe(ExifInterface.TAG_GPS_LATITUDE_REF, 0x0001, "GPS IFD (Location)", gpsIfdTags)
        probe(ExifInterface.TAG_GPS_LATITUDE, 0x0002, "GPS IFD (Location)", gpsIfdTags)
        probe(ExifInterface.TAG_GPS_LONGITUDE_REF, 0x0003, "GPS IFD (Location)", gpsIfdTags)
        probe(ExifInterface.TAG_GPS_LONGITUDE, 0x0004, "GPS IFD (Location)", gpsIfdTags)
        probe(ExifInterface.TAG_GPS_ALTITUDE_REF, 0x0005, "GPS IFD (Location)", gpsIfdTags)
        probe(ExifInterface.TAG_GPS_ALTITUDE, 0x0006, "GPS IFD (Location)", gpsIfdTags)
        probe(ExifInterface.TAG_GPS_DATESTAMP, 0x001D, "GPS IFD (Location)", gpsIfdTags)
        probe(ExifInterface.TAG_GPS_TIMESTAMP, 0x0007, "GPS IFD (Location)", gpsIfdTags)

        val result = mutableListOf<ExifDirectoryNode>()
        if (ifd0Tags.isNotEmpty()) result.add(ExifDirectoryNode("IFD0 (Primary Image)", "Core camera and capture baseline directory", ifd0Tags))
        if (exifSubIfdTags.isNotEmpty()) result.add(ExifDirectoryNode("Exif SubIFD (Photo)", "Exposure, sensor, optics and camera settings", exifSubIfdTags))
        if (gpsIfdTags.isNotEmpty()) result.add(ExifDirectoryNode("GPS IFD", "Geographic coordinates, altitude, and timestamp", gpsIfdTags))
        return result
    }

    private fun fallbackDirectoryList(): List<ExifDirectoryNode> = emptyList()
}
