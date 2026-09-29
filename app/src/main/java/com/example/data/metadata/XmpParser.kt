package com.example.data.metadata

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader

class XmpParser {

    fun parse(file: File): XmpReport {
        return try {
            val bytes = file.inputStream().use { it.readBytes() }
            parseFromBytes(bytes)
        } catch (e: Exception) {
            XmpReport(hasXmp = false, rawXml = "", schemas = emptyList(), allProperties = emptyList())
        }
    }

    fun parseFromBytes(bytes: ByteArray): XmpReport {
        val rawXml = extractXmpXml(bytes)
        if (rawXml.isNullOrBlank()) {
            return XmpReport(hasXmp = false, rawXml = "", schemas = emptyList(), allProperties = emptyList())
        }

        val cleanedXml = cleanAndFormatXml(rawXml)
        val properties = parseXmpProperties(cleanedXml)

        // Group into schema nodes
        val grouped = properties.groupBy { it.namespacePrefix }
        val schemas = mutableListOf<XmpSchemaNode>()

        val wellKnownSchemas = listOf(
            Triple("dc", "http://purl.org/dc/elements/1.1/", "Dublin Core (dc)"),
            Triple("photoshop", "http://ns.adobe.com/photoshop/1.0/", "Adobe Photoshop (photoshop)"),
            Triple("xmp", "http://ns.adobe.com/xap/1.0/", "XMP Basic (xmp)"),
            Triple("xmpRights", "http://ns.adobe.com/xap/1.0/rights/", "XMP Rights Management (xmpRights)"),
            Triple("xmpMM", "http://ns.adobe.com/xmp/1.0/mm/", "XMP Media Management (xmpMM)"),
            Triple("exif", "http://ns.adobe.com/exif/1.0/", "EXIF Schema (exif)"),
            Triple("tiff", "http://ns.adobe.com/tiff/1.0/", "TIFF Schema (tiff)"),
            Triple("aux", "http://ns.adobe.com/exif/1.0/aux/", "Camera & Lens Aux (aux)"),
            Triple("crs", "http://ns.adobe.com/camera-raw-settings/1.0/", "Camera Raw Settings (crs)")
        )

        for ((prefix, uri, displayName) in wellKnownSchemas) {
            val props = grouped[prefix] ?: grouped[prefix.lowercase()]
            if (!props.isNullOrEmpty()) {
                schemas.add(XmpSchemaNode(prefix, uri, displayName, props))
            }
        }

        // Add any remaining namespaces
        val knownPrefixes = wellKnownSchemas.map { it.first.lowercase() }.toSet()
        for ((prefix, props) in grouped) {
            if (prefix.lowercase() !in knownPrefixes && props.isNotEmpty()) {
                val uri = props.firstOrNull()?.namespaceUri ?: ""
                schemas.add(XmpSchemaNode(prefix, uri, "Namespace ($prefix)", props))
            }
        }

        return XmpReport(
            hasXmp = properties.isNotEmpty() || cleanedXml.isNotBlank(),
            rawXml = cleanedXml,
            schemas = schemas,
            allProperties = properties
        )
    }

    fun extractXmpXml(bytes: ByteArray): String? {
        if (bytes.size < 10) return null

        // 1. Check if directly an XML string
        val prefix = String(bytes, 0, minOf(bytes.size, 100), Charsets.UTF_8).trim()
        if (prefix.startsWith("<x:xmpmeta") || prefix.startsWith("<?xpacket") || prefix.startsWith("<rdf:RDF")) {
            return String(bytes, Charsets.UTF_8)
        }

        // 2. Check JPEG APP1 segments for "http://ns.adobe.com/xap/1.0/\0"
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            var offset = 2
            val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000"
            while (offset < bytes.size - 4) {
                if (bytes[offset] != 0xFF.toByte()) {
                    offset++
                    continue
                }
                val marker = bytes[offset + 1].toInt() and 0xFF
                if (marker == 0xDA || marker == 0xD9) break

                val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
                if (offset + 2 + length > bytes.size) break

                if (marker == 0xE1 && length >= 32) {
                    val sig = String(bytes, offset + 4, minOf(xmpHeader.length, length - 2), Charsets.US_ASCII)
                    if (sig.startsWith("http://ns.adobe.com/xap/1.0/")) {
                        val xmpStart = offset + 4 + 29 // skip "http://ns.adobe.com/xap/1.0/\0"
                        val xmpLen = (offset + 2 + length) - xmpStart
                        if (xmpLen > 0 && xmpStart + xmpLen <= bytes.size) {
                            return String(bytes, xmpStart, xmpLen, Charsets.UTF_8)
                        }
                    }
                }
                offset += 2 + length
            }
        }

        // 3. Check PNG iTXt chunks
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) { // PNG signature
            var offset = 8
            while (offset + 8 < bytes.size) {
                val chunkLen = ((bytes[offset].toInt() and 0xFF) shl 24) or
                        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                        (bytes[offset + 3].toInt() and 0xFF)
                val chunkType = String(bytes, offset + 4, 4, Charsets.US_ASCII)
                if (chunkType == "iTXt" && chunkLen > 0 && offset + 8 + chunkLen <= bytes.size) {
                    val content = String(bytes, offset + 8, chunkLen, Charsets.UTF_8)
                    if (content.contains("XML:com.adobe.xmp")) {
                        val packetStart = content.indexOf("<")
                        if (packetStart >= 0) {
                            return content.substring(packetStart)
                        }
                    }
                }
                offset += 8 + chunkLen + 4 // len (4) + type (4) + data (chunkLen) + crc (4)
            }
        }

        // 4. Check WebP 'XMP ' chunk
        if (bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP") {
            var offset = 12
            while (offset + 8 < bytes.size) {
                val fourCC = String(bytes, offset, 4, Charsets.US_ASCII)
                val chunkLen = (bytes[offset + 4].toInt() and 0xFF) or
                        ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                        ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 7].toInt() and 0xFF) shl 24)
                if (fourCC == "XMP " && chunkLen > 0 && offset + 8 + chunkLen <= bytes.size) {
                    return String(bytes, offset + 8, chunkLen, Charsets.UTF_8)
                }
                var next = offset + 8 + chunkLen
                if (chunkLen % 2 != 0) next++
                offset = next
            }
        }

        // 5. Fallback search for <x:xmpmeta or <rdf:RDF within the file
        val textSnippet = String(bytes, 0, minOf(bytes.size, 100_000), Charsets.UTF_8)
        val idxStart = textSnippet.indexOf("<x:xmpmeta")
        if (idxStart >= 0) {
            val idxEnd = textSnippet.indexOf("</x:xmpmeta>")
            if (idxEnd > idxStart) {
                return textSnippet.substring(idxStart, idxEnd + "</x:xmpmeta>".length)
            }
        }
        val rdfStart = textSnippet.indexOf("<rdf:RDF")
        if (rdfStart >= 0) {
            val rdfEnd = textSnippet.indexOf("</rdf:RDF>")
            if (rdfEnd > rdfStart) {
                return textSnippet.substring(rdfStart, rdfEnd + "</rdf:RDF>".length)
            }
        }

        return null
    }

    private fun cleanAndFormatXml(rawXml: String): String {
        // Strip null terminators or header noise
        var text = rawXml.trim().trimEnd('\u0000')

        // Simple XML pretty-printer
        val sb = StringBuilder()
        val lines = text.split("\n")
        var indentLevel = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("</") && !trimmed.contains("><")) {
                indentLevel = maxOf(0, indentLevel - 1)
            }

            sb.append("  ".repeat(indentLevel)).append(trimmed).append("\n")

            if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.endsWith("/>") && !trimmed.startsWith("<?")) {
                val tag = trimmed.substring(1).substringBefore(" ").substringBefore(">")
                if (!trimmed.contains("</$tag>")) {
                    indentLevel++
                }
            }
        }

        return if (sb.isNotEmpty()) sb.toString().trim() else text
    }

    private fun createPullParser(): XmlPullParser {
        return try {
            Xml.newPullParser()
        } catch (e: Throwable) {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            factory.newPullParser()
        }
    }

    private fun parseXmpProperties(xml: String): List<XmpPropertyItem> {
        val properties = mutableListOf<XmpPropertyItem>()
        try {
            val parser = createPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentProperty: String? = null
            var currentPrefix: String? = null
            var currentUri: String? = null
            val currentListItems = mutableListOf<String>()
            var inList = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name ?: ""
                        val prefix = parser.prefix ?: ""
                        val uri = parser.namespace ?: ""

                        // Check attributes on rdf:Description (many XMP tools write props as attributes)
                        if (name.equals("Description", ignoreCase = true)) {
                            for (i in 0 until parser.attributeCount) {
                                val attrPrefix = parser.getAttributePrefix(i)
                                val attrName = parser.getAttributeName(i)
                                val attrVal = parser.getAttributeValue(i)
                                val attrUri = parser.getAttributeNamespace(i)

                                if (!attrPrefix.isNullOrBlank() && attrPrefix != "xmlns" && attrPrefix != "rdf") {
                                    properties.add(
                                        XmpPropertyItem(
                                            namespacePrefix = attrPrefix,
                                            namespaceUri = attrUri ?: "",
                                            propertyName = attrName,
                                            qualifiedName = "$attrPrefix:$attrName",
                                            value = attrVal
                                        )
                                    )
                                }
                            }
                        } else if (name.equals("Bag", ignoreCase = true) ||
                            name.equals("Seq", ignoreCase = true) ||
                            name.equals("Alt", ignoreCase = true)
                        ) {
                            inList = true
                            currentListItems.clear()
                        } else if (inList && name.equals("li", ignoreCase = true)) {
                            // will read text in next event
                        } else if (prefix.isNotBlank() && prefix != "rdf" && prefix != "x") {
                            currentProperty = name
                            currentPrefix = prefix
                            currentUri = uri
                            inList = false
                            currentListItems.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim()
                        if (!text.isNullOrEmpty()) {
                            if (inList) {
                                currentListItems.add(text)
                            } else if (currentProperty != null && currentPrefix != null) {
                                properties.add(
                                    XmpPropertyItem(
                                        namespacePrefix = currentPrefix,
                                        namespaceUri = currentUri ?: "",
                                        propertyName = currentProperty,
                                        qualifiedName = "$currentPrefix:$currentProperty",
                                        value = text
                                    )
                                )
                                currentProperty = null
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name ?: ""
                        if (name.equals("Bag", ignoreCase = true) ||
                            name.equals("Seq", ignoreCase = true) ||
                            name.equals("Alt", ignoreCase = true)
                        ) {
                            if (currentProperty != null && currentPrefix != null && currentListItems.isNotEmpty()) {
                                properties.add(
                                    XmpPropertyItem(
                                        namespacePrefix = currentPrefix,
                                        namespaceUri = currentUri ?: "",
                                        propertyName = currentProperty,
                                        qualifiedName = "$currentPrefix:$currentProperty",
                                        value = currentListItems.joinToString(", "),
                                        isList = true,
                                        listValues = currentListItems.toList()
                                    )
                                )
                                currentProperty = null
                            }
                            inList = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // If XML parsing encounters partial tags, extract using regex fallback
            properties.addAll(fallbackRegexProperties(xml))
        }

        return properties.distinctBy { it.qualifiedName + it.value }
    }

    private fun fallbackRegexProperties(xml: String): List<XmpPropertyItem> {
        val list = mutableListOf<XmpPropertyItem>()
        // Strip top-level wrapper elements so they don't consume all inner tags
        val cleaned = xml
            .replace(Regex("<[/]?(x:xmpmeta|rdf:RDF|rdf:Description)[^>]*>", RegexOption.IGNORE_CASE), "")
        // Match <prefix:tag>value</prefix:tag>
        val regex = Regex("<([a-zA-Z0-9_-]+):([a-zA-Z0-9_-]+)[^>]*>(.*?)</\\1:\\2>", RegexOption.DOT_MATCHES_ALL)
        for (match in regex.findAll(cleaned)) {
            val prefix = match.groupValues[1]
            val tag = match.groupValues[2]
            var content = match.groupValues[3].trim()

            if (prefix == "rdf" || prefix == "x") continue

            // If it contains <rdf:li>, extract list items
            if (content.contains("<rdf:li")) {
                val liRegex = Regex("<rdf:li[^>]*>(.*?)</rdf:li>", RegexOption.DOT_MATCHES_ALL)
                val items = liRegex.findAll(content).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
                if (items.isNotEmpty()) {
                    list.add(
                        XmpPropertyItem(
                            namespacePrefix = prefix,
                            namespaceUri = "",
                            propertyName = tag,
                            qualifiedName = "$prefix:$tag",
                            value = items.joinToString(", "),
                            isList = true,
                            listValues = items
                        )
                    )
                    continue
                }
            }

            // Normal text
            if (!content.contains("<") && content.isNotEmpty()) {
                list.add(
                    XmpPropertyItem(
                        namespacePrefix = prefix,
                        namespaceUri = "",
                        propertyName = tag,
                        qualifiedName = "$prefix:$tag",
                        value = content
                    )
                )
            }
        }
        return list
    }
}
