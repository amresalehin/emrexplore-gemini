package com.example.data.metadata

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object ExifToolMetadataParser {
    fun parse(json: String): ParsedMetadata {
        val array = JSONArray(json)
        if (array.length() == 0) return ParsedMetadata.empty()
        return parseObject(array.getJSONObject(0))
    }

    private fun parseObject(obj: JSONObject): ParsedMetadata {
        val groups = linkedMapOf<String, MutableList<MetadataTagItem>>()
        val values = linkedMapOf<String, Any?>()

        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "SourceFile") continue
            val value = obj.opt(key)
            val group = key.substringBefore(':', "File")
            val tag = key.substringAfter(':', key)
            val formatted = displayValue(value)
            groups.getOrPut(group) { mutableListOf() }.add(
                MetadataTagItem(
                    tagIdHex = "",
                    tagIdInt = 0,
                    name = tag,
                    directory = group,
                    formattedValue = formatted,
                    rawValue = value?.toString().orEmpty().ifBlank { formatted },
                    dataType = valueType(value)
                )
            )
            values[tag.normalize()] = value
            values[key.normalize()] = value
        }

        val directories = groups.map { (name, tags) ->
            ExifDirectoryNode(
                name = name,
                description = name,
                tags = tags.sortedBy { it.name.lowercase(Locale.US) }
            )
        }

        return ParsedMetadata(
            directories = directories,
            summary = buildSummary(values),
            rawJson = obj.toString()
        )
    }

    private fun buildSummary(values: Map<String, Any?>): MetadataSummary {
        fun text(vararg names: String): String? =
            names.asSequence()
                .mapNotNull { values[it.normalize()]?.let(::displayValue) }
                .firstOrNull(String::isNotBlank)

        fun number(vararg names: String): Double? =
            names.asSequence().mapNotNull { name ->
                when (val value = values[name.normalize()]) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull()
                    else -> null
                }
            }.firstOrNull()

        fun listValues(value: Any?): List<String> = when (value) {
            is JSONArray -> (0 until value.length()).map { value.optString(it) }
            else -> displayValue(value).split(',').map(String::trim)
        }

        val keywords = sequenceOf(
            values["subject".normalize()],
            values["keywords".normalize()]
        )
            .flatMap { listValues(it).asSequence() }
            .filter(String::isNotBlank)
            .distinct()
            .toList()

        return MetadataSummary(
            make = text("Make"),
            model = text("Model"),
            lensModel = text("LensModel", "Lens"),
            exposureTime = text("ExposureTime"),
            fNumber = text("FNumber", "ApertureValue"),
            iso = text("ISO", "ISOSpeedRatings"),
            focalLength = text("FocalLength"),
            focalLength35mm = text("FocalLengthIn35mmFormat"),
            dateTimeOriginal = text("DateTimeOriginal", "CreateDate", "DateTimeCreated"),
            imageWidth = number("ImageWidth", "ExifImageWidth")?.toInt() ?: 0,
            imageHeight = number("ImageHeight", "ExifImageHeight")?.toInt() ?: 0,
            latitude = number("GPSLatitude"),
            longitude = number("GPSLongitude"),
            altitude = number("GPSAltitude"),
            flash = text("Flash"),
            whiteBalance = text("WhiteBalance"),
            meteringMode = text("MeteringMode"),
            exposureProgram = text("ExposureProgram"),
            software = text("Software", "CreatorTool"),
            artist = text("Artist", "Creator"),
            copyright = text("Copyright", "Rights"),
            title = text("Title"),
            headline = text("Headline"),
            description = text("Description", "ImageDescription", "Caption-Abstract"),
            keywords = keywords,
            city = text("City"),
            state = text("State", "Province-State"),
            country = text("Country", "Country-PrimaryLocationName")
        )
    }

    private fun displayValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is JSONObject -> value.toString()
        is JSONArray -> (0 until value.length()).joinToString(", ") { displayValue(value.opt(it)) }
        else -> value.toString()
    }

    private fun valueType(value: Any?): String = when (value) {
        is Boolean -> "BOOLEAN"
        is Number -> "NUMBER"
        is JSONArray, is JSONObject -> "STRUCTURED"
        else -> "STRING"
    }

    private fun String.normalize(): String =
        lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    data class ParsedMetadata(
        val directories: List<ExifDirectoryNode>,
        val summary: MetadataSummary,
        val rawJson: String
    ) {
        companion object {
            fun empty() = ParsedMetadata(emptyList(), MetadataSummary(), "")
        }
    }
}
