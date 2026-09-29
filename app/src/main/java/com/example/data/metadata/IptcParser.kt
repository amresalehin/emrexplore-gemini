package com.example.data.metadata

import java.io.File
import java.nio.charset.Charset

class IptcParser {

    fun parse(file: File): IptcReport {
        return try {
            val bytes = file.inputStream().use { it.readBytes() }
            parseFromBytes(bytes)
        } catch (e: Exception) {
            IptcReport(hasIptc = false, groups = emptyList(), allDatasets = emptyList())
        }
    }

    fun parseFromBytes(bytes: ByteArray): IptcReport {
        val iptcRawBytes = extractIptcBlock(bytes)
        if (iptcRawBytes == null || iptcRawBytes.isEmpty()) {
            return IptcReport(hasIptc = false, groups = emptyList(), allDatasets = emptyList())
        }

        val datasets = parseIptcDatasets(iptcRawBytes)
        if (datasets.isEmpty()) {
            return IptcReport(hasIptc = false, groups = emptyList(), allDatasets = emptyList())
        }

        // Group into logical photography / metadata categories
        val grouped = datasets.groupBy { it.category }
        val categoryOrder = listOf(
            "Editorial & Content",
            "Creator & Credit",
            "Rights & Licensing",
            "Location",
            "Dates & Workflow",
            "Envelope & Technical",
            "Other"
        )

        val groups = mutableListOf<IptcGroupNode>()
        for (cat in categoryOrder) {
            val items = grouped[cat]
            if (!items.isNullOrEmpty()) {
                groups.add(IptcGroupNode(categoryName = cat, datasets = items))
            }
        }
        // Add any remaining categories
        for ((cat, items) in grouped) {
            if (cat !in categoryOrder && items.isNotEmpty()) {
                groups.add(IptcGroupNode(categoryName = cat, datasets = items))
            }
        }

        return IptcReport(
            hasIptc = true,
            groups = groups,
            allDatasets = datasets
        )
    }

    /**
     * Extracts raw IPTC bytes from JPEG APP13 Photoshop 3.0 segment.
     */
    fun extractIptcBlock(bytes: ByteArray): ByteArray? {
        if (bytes.size < 4) return null

        // If directly raw IPTC stream (starts with 0x1C)
        if (bytes[0] == 0x1C.toByte() && (bytes[1] == 1.toByte() || bytes[1] == 2.toByte())) {
            return bytes
        }

        // JPEG stream: look for APP13 (0xFF 0xED)
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            var offset = 2
            while (offset < bytes.size - 4) {
                if (bytes[offset] != 0xFF.toByte()) {
                    offset++
                    continue
                }
                val marker = bytes[offset + 1].toInt() and 0xFF
                if (marker == 0xDA || marker == 0xD9) break // Start of scan or End of image

                val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
                if (offset + 2 + length > bytes.size) break

                if (marker == 0xED && length >= 14) { // APP13
                    val sig = String(bytes, offset + 4, minOf(13, length - 2), Charsets.US_ASCII)
                    if (sig.startsWith("Photoshop 3.0")) {
                        val app13DataStart = offset + 4 + 14 // "Photoshop 3.0\0"
                        val app13End = offset + 2 + length
                        val iptc = extractIptcFrom8Bim(bytes, app13DataStart, app13End)
                        if (iptc != null) return iptc
                    }
                }
                offset += 2 + length
            }
        }
        return null
    }

    /**
     * Traverses 8BIM blocks looking for resource ID 0x0404 (IPTC-NAA record).
     */
    private fun extractIptcFrom8Bim(bytes: ByteArray, start: Int, end: Int): ByteArray? {
        var pos = start
        while (pos + 12 <= end) {
            val sig = String(bytes, pos, 4, Charsets.US_ASCII)
            if (sig != "8BIM" && sig != "8B64" && sig != "MeSa") {
                pos++
                continue
            }

            val resourceId = ((bytes[pos + 4].toInt() and 0xFF) shl 8) or (bytes[pos + 5].toInt() and 0xFF)
            var p = pos + 6

            // Pascal string (name length byte, then characters, padded to even length)
            if (p >= end) break
            val nameLen = bytes[p].toInt() and 0xFF
            var nameTotal = 1 + nameLen
            if (nameTotal % 2 != 0) nameTotal++
            p += nameTotal

            if (p + 4 > end) break
            val resSize = ((bytes[p].toInt() and 0xFF) shl 24) or
                    ((bytes[p + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[p + 2].toInt() and 0xFF) shl 8) or
                    (bytes[p + 3].toInt() and 0xFF)
            p += 4

            if (resourceId == 0x0404 && resSize > 0 && p + resSize <= end) {
                val iptc = ByteArray(resSize)
                System.arraycopy(bytes, p, iptc, 0, resSize)
                return iptc
            }

            var nextBlock = p + resSize
            if (resSize % 2 != 0) nextBlock++ // 8BIM blocks are padded to even boundaries
            pos = nextBlock
        }
        return null
    }

    private fun parseIptcDatasets(bytes: ByteArray): List<IptcDatasetItem> {
        val list = mutableListOf<IptcDatasetItem>()
        var pos = 0
        var charset: Charset = Charsets.UTF_8

        // First scan for 1:90 Coded Character Set
        var scanPos = 0
        while (scanPos + 5 <= bytes.size) {
            if (bytes[scanPos] == 0x1C.toByte()) {
                val rec = bytes[scanPos + 1].toInt() and 0xFF
                val ds = bytes[scanPos + 2].toInt() and 0xFF
                val len = ((bytes[scanPos + 3].toInt() and 0xFF) shl 8) or (bytes[scanPos + 4].toInt() and 0xFF)
                if (rec == 1 && ds == 90 && scanPos + 5 + len <= bytes.size) {
                    val code = String(bytes, scanPos + 5, len, Charsets.ISO_8859_1)
                    if (code.contains("%G") || code == "\u001B%G") {
                        charset = Charsets.UTF_8
                    }
                }
                scanPos += 5 + len
            } else {
                scanPos++
            }
        }

        while (pos + 5 <= bytes.size) {
            if (bytes[pos] != 0x1C.toByte()) {
                pos++
                continue
            }

            val recordNum = bytes[pos + 1].toInt() and 0xFF
            val datasetNum = bytes[pos + 2].toInt() and 0xFF
            val dataLen = ((bytes[pos + 3].toInt() and 0xFF) shl 8) or (bytes[pos + 4].toInt() and 0xFF)

            val dataStart = pos + 5
            if (dataStart + dataLen > bytes.size) {
                break
            }

            val valueString = try {
                // Try UTF-8 first, fallback to ISO-8859-1
                String(bytes, dataStart, dataLen, charset).trim()
            } catch (e: Exception) {
                String(bytes, dataStart, dataLen, Charsets.ISO_8859_1).trim()
            }

            val tagDef = getIptcTagDefinition(recordNum, datasetNum)

            list.add(
                IptcDatasetItem(
                    recordNumber = recordNum,
                    datasetNumber = datasetNum,
                    tagCode = "$recordNum:${datasetNum.toString().padStart(3, '0')}",
                    name = tagDef.name,
                    category = tagDef.category,
                    value = valueString,
                    rawBytesLength = dataLen
                )
            )

            pos += 5 + dataLen
        }

        return list
    }

    private data class IptcTagDefinition(
        val name: String,
        val category: String
    )

    private fun getIptcTagDefinition(record: Int, dataset: Int): IptcTagDefinition {
        return when (record) {
            1 -> when (dataset) {
                0 -> IptcTagDefinition("Model Version", "Envelope & Technical")
                5 -> IptcTagDefinition("Destination", "Envelope & Technical")
                20 -> IptcTagDefinition("File Format", "Envelope & Technical")
                22 -> IptcTagDefinition("File Format Version", "Envelope & Technical")
                30 -> IptcTagDefinition("Service Identifier", "Envelope & Technical")
                40 -> IptcTagDefinition("Envelope Number", "Envelope & Technical")
                50 -> IptcTagDefinition("Product ID", "Envelope & Technical")
                60 -> IptcTagDefinition("Envelope Priority", "Envelope & Technical")
                70 -> IptcTagDefinition("Date Sent", "Envelope & Technical")
                80 -> IptcTagDefinition("Time Sent", "Envelope & Technical")
                90 -> IptcTagDefinition("Coded Character Set", "Envelope & Technical")
                100 -> IptcTagDefinition("Unique Object Name", "Envelope & Technical")
                else -> IptcTagDefinition("Envelope Tag $dataset", "Envelope & Technical")
            }
            2 -> when (dataset) {
                0 -> IptcTagDefinition("Record Version", "Dates & Workflow")
                3 -> IptcTagDefinition("Object Type Reference", "Editorial & Content")
                4 -> IptcTagDefinition("Object Attribute Reference", "Editorial & Content")
                5 -> IptcTagDefinition("Object Name / Title", "Editorial & Content")
                7 -> IptcTagDefinition("Edit Status", "Dates & Workflow")
                8 -> IptcTagDefinition("Editorial Update", "Dates & Workflow")
                10 -> IptcTagDefinition("Urgency", "Editorial & Content")
                12 -> IptcTagDefinition("Subject Reference", "Editorial & Content")
                15 -> IptcTagDefinition("Category", "Editorial & Content")
                20 -> IptcTagDefinition("Supplemental Category", "Editorial & Content")
                22 -> IptcTagDefinition("Fixture Identifier", "Dates & Workflow")
                25 -> IptcTagDefinition("Keywords", "Editorial & Content")
                26 -> IptcTagDefinition("Content Location Code", "Location")
                27 -> IptcTagDefinition("Content Location Name", "Location")
                30 -> IptcTagDefinition("Release Date", "Dates & Workflow")
                35 -> IptcTagDefinition("Release Time", "Dates & Workflow")
                37 -> IptcTagDefinition("Expiration Date", "Dates & Workflow")
                38 -> IptcTagDefinition("Expiration Time", "Dates & Workflow")
                40 -> IptcTagDefinition("Special Instructions", "Rights & Licensing")
                42 -> IptcTagDefinition("Action Advised", "Dates & Workflow")
                55 -> IptcTagDefinition("Date Created", "Dates & Workflow")
                60 -> IptcTagDefinition("Time Created", "Dates & Workflow")
                62 -> IptcTagDefinition("Digital Creation Date", "Dates & Workflow")
                63 -> IptcTagDefinition("Digital Creation Time", "Dates & Workflow")
                65 -> IptcTagDefinition("Originating Program", "Creator & Credit")
                70 -> IptcTagDefinition("Program Version", "Creator & Credit")
                75 -> IptcTagDefinition("Object Cycle", "Dates & Workflow")
                80 -> IptcTagDefinition("By-line (Creator)", "Creator & Credit")
                85 -> IptcTagDefinition("By-line Title", "Creator & Credit")
                90 -> IptcTagDefinition("City", "Location")
                92 -> IptcTagDefinition("Sub-location", "Location")
                95 -> IptcTagDefinition("Province / State", "Location")
                100 -> IptcTagDefinition("Country Code (ISO)", "Location")
                101 -> IptcTagDefinition("Country Name", "Location")
                103 -> IptcTagDefinition("Original Transmission Ref / Job ID", "Dates & Workflow")
                105 -> IptcTagDefinition("Headline", "Editorial & Content")
                110 -> IptcTagDefinition("Credit", "Creator & Credit")
                115 -> IptcTagDefinition("Source", "Creator & Credit")
                116 -> IptcTagDefinition("Copyright Notice", "Rights & Licensing")
                118 -> IptcTagDefinition("Contact Information", "Rights & Licensing")
                120 -> IptcTagDefinition("Caption / Abstract", "Editorial & Content")
                122 -> IptcTagDefinition("Caption Writer / Editor", "Creator & Credit")
                130 -> IptcTagDefinition("Image Type", "Envelope & Technical")
                131 -> IptcTagDefinition("Image Orientation", "Envelope & Technical")
                135 -> IptcTagDefinition("Language Identifier", "Editorial & Content")
                else -> IptcTagDefinition("Application Tag $dataset", "Other")
            }
            else -> IptcTagDefinition("Record $record Tag $dataset", "Other")
        }
    }
}
