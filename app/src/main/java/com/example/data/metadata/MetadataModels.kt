package com.example.data.metadata

data class MetadataReport(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val mimeType: String,
    val summary: MetadataSummary,
    val exifDirectories: List<ExifDirectoryNode>,
    val iptcReport: IptcReport,
    val xmpReport: XmpReport,
    val segments: List<SegmentItem> = emptyList()
) {
    val totalTagCount: Int
        get() = exifDirectories.sumOf { it.tags.size } +
                iptcReport.allDatasets.size +
                xmpReport.allProperties.size

    val hasExif: Boolean
        get() = exifDirectories.any { it.tags.isNotEmpty() }

    val hasIptc: Boolean
        get() = iptcReport.hasIptc

    val hasXmp: Boolean
        get() = xmpReport.hasXmp

    val hasGps: Boolean
        get() = summary.latitude != null && summary.longitude != null
}

data class MetadataSummary(
    val make: String? = null,
    val model: String? = null,
    val lensModel: String? = null,
    val exposureTime: String? = null,
    val fNumber: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val focalLength35mm: String? = null,
    val dateTimeOriginal: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val flash: String? = null,
    val whiteBalance: String? = null,
    val meteringMode: String? = null,
    val exposureProgram: String? = null,
    val software: String? = null,
    val artist: String? = null,
    val copyright: String? = null,
    val title: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val keywords: List<String> = emptyList(),
    val city: String? = null,
    val state: String? = null,
    val country: String? = null
)

data class ExifDirectoryNode(
    val name: String,
    val description: String,
    val tags: List<MetadataTagItem>,
    val isExpandedByDefault: Boolean = true
)

data class MetadataTagItem(
    val tagIdHex: String,
    val tagIdInt: Int,
    val name: String,
    val directory: String,
    val formattedValue: String,
    val rawValue: String = formattedValue,
    val dataType: String = "STRING"
)

data class IptcReport(
    val hasIptc: Boolean,
    val groups: List<IptcGroupNode>,
    val allDatasets: List<IptcDatasetItem>
)

data class IptcGroupNode(
    val categoryName: String,
    val datasets: List<IptcDatasetItem>
)

data class IptcDatasetItem(
    val recordNumber: Int,
    val datasetNumber: Int,
    val tagCode: String, // e.g. "2:105"
    val name: String,
    val category: String,
    val value: String,
    val rawBytesLength: Int = value.length
)

data class XmpReport(
    val hasXmp: Boolean,
    val rawXml: String,
    val schemas: List<XmpSchemaNode>,
    val allProperties: List<XmpPropertyItem>
)

data class XmpSchemaNode(
    val prefix: String,
    val namespaceUri: String,
    val displayName: String,
    val properties: List<XmpPropertyItem>
)

data class XmpPropertyItem(
    val namespacePrefix: String,
    val namespaceUri: String,
    val propertyName: String,
    val qualifiedName: String, // e.g. "dc:title"
    val value: String,
    val isList: Boolean = false,
    val listValues: List<String> = emptyList()
)

data class SegmentItem(
    val marker: String, // e.g. "APP1", "APP13"
    val markerHex: String, // e.g. "0xFFE1"
    val description: String,
    val length: Int
)
