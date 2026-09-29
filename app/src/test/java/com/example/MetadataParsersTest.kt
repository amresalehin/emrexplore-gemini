package com.example

import com.example.data.metadata.ExifTreeParser
import com.example.data.metadata.IptcParser
import com.example.data.metadata.MetadataWriter
import com.example.data.metadata.XmpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MetadataParsersTest {

    @Test
    fun testIptcParser_parsesRecord2Datasets() {
        val parser = IptcParser()
        val out = ByteArrayOutputStream()

        fun writeDataset(record: Int, dataset: Int, stringVal: String) {
            val bytes = stringVal.toByteArray(Charsets.UTF_8)
            out.write(0x1C)
            out.write(record)
            out.write(dataset)
            out.write((bytes.size shr 8) and 0xFF)
            out.write(bytes.size and 0xFF)
            out.write(bytes)
        }

        writeDataset(2, 105, "Breaking Sunset Over Bay")
        writeDataset(2, 80, "John Doe")
        writeDataset(2, 116, "© 2026 emrexplore Project")
        writeDataset(2, 90, "San Francisco")
        writeDataset(2, 25, "landscape")
        writeDataset(2, 25, "sunset")

        val report = parser.parseFromBytes(out.toByteArray())
        assertTrue("Expected hasIptc to be true", report.hasIptc)
        assertEquals(6, report.allDatasets.size)

        val headline = report.allDatasets.find { it.tagCode == "2:105" }
        assertNotNull(headline)
        assertEquals("Breaking Sunset Over Bay", headline?.value)
        assertEquals("Editorial & Content", headline?.category)

        val author = report.allDatasets.find { it.tagCode == "2:080" }
        assertNotNull(author)
        assertEquals("John Doe", author?.value)

        val keywords = report.allDatasets.filter { it.datasetNumber == 25 }.map { it.value }
        assertEquals(listOf("landscape", "sunset"), keywords)
    }

    @Test
    fun testXmpParser_parsesNamespacesAndLists() {
        val parser = XmpParser()
        val sampleXml = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                    xmlns:dc="http://purl.org/dc/elements/1.1/"
                    xmlns:photoshop="http://ns.adobe.com/photoshop/1.0/"
                    xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                  <dc:title>
                    <rdf:Alt>
                      <rdf:li xml:lang="x-default">Alpine Horizon</rdf:li>
                    </rdf:Alt>
                  </dc:title>
                  <dc:creator>
                    <rdf:Seq>
                      <rdf:li>Elena Rostova</rdf:li>
                    </rdf:Seq>
                  </dc:creator>
                  <dc:subject>
                    <rdf:Bag>
                      <rdf:li>mountains</rdf:li>
                      <rdf:li>nature</rdf:li>
                    </rdf:Bag>
                  </dc:subject>
                  <photoshop:Headline>Snowy Peaks in Golden Hour</photoshop:Headline>
                  <photoshop:City>Chamonix</photoshop:City>
                  <xmp:Rating>5</xmp:Rating>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()

        val report = parser.parseFromBytes(sampleXml.toByteArray(Charsets.UTF_8))
        assertTrue("Expected hasXmp to be true", report.hasXmp)
        assertTrue("Expected properties to be found", report.allProperties.isNotEmpty())

        val titleProp = report.allProperties.find { it.qualifiedName == "dc:title" }
        assertNotNull(titleProp)
        assertEquals("Alpine Horizon", titleProp?.value)

        val headlineProp = report.allProperties.find { it.qualifiedName == "photoshop:Headline" }
        assertNotNull(headlineProp)
        assertEquals("Snowy Peaks in Golden Hour", headlineProp?.value)

        val subjectProp = report.allProperties.find { it.qualifiedName == "dc:subject" }
        assertNotNull(subjectProp)
        assertTrue(subjectProp?.isList == true)
        assertEquals(listOf("mountains", "nature"), subjectProp?.listValues)
    }

    @Test
    fun testExifTreeParser_parsesTiffStructure() {
        val parser = ExifTreeParser()

        // Create a minimal synthetic TIFF header with IFD0
        // Header: 'I' 'I', 42 (0x002A), offset to IFD0 = 8
        val bb = ByteBuffer.allocate(256)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        bb.put('I'.code.toByte())
        bb.put('I'.code.toByte())
        bb.putShort(42.toShort())
        bb.putInt(8) // IFD0 offset

        // At offset 8: numEntries = 2
        bb.putShort(2.toShort())

        // Entry 1: Make (0x010F), ASCII (2), count = 5 ("Sony\0"), value inline
        bb.putShort(0x010F.toShort()) // tag
        bb.putShort(2.toShort())      // type ASCII
        bb.putInt(5)                 // count
        bb.putInt(32)                // offset to string

        // Entry 2: Orientation (0x0112), SHORT (3), count = 1, value = 1 (inline)
        bb.putShort(0x0112.toShort())
        bb.putShort(3.toShort())
        bb.putInt(1)
        bb.putShort(1.toShort())
        bb.putShort(0.toShort()) // pad

        bb.putInt(0) // Next IFD offset = 0

        // At offset 32: string "Sony\0"
        bb.position(32)
        bb.put("Sony\u0000".toByteArray(Charsets.US_ASCII))

        val directories = parser.parseFromBytes(bb.array())
        assertTrue("Expected parsed directories", directories.isNotEmpty())

        val ifd0 = directories.find { it.name.contains("IFD0") }
        assertNotNull("Expected IFD0", ifd0)

        val makeTag = ifd0?.tags?.find { it.tagIdInt == 0x010F }
        assertNotNull("Expected Make tag", makeTag)
        assertEquals("Sony", makeTag?.formattedValue)

        val orientTag = ifd0?.tags?.find { it.tagIdInt == 0x0112 }
        assertNotNull("Expected Orientation tag", orientTag)
        assertTrue(orientTag?.formattedValue?.contains("Normal") == true)
    }
}
