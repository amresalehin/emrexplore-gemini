package com.example.data.metadata

import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

object MetadataWriter {

    data class SamplePhotoMetadata(
        val make: String,
        val model: String,
        val lensModel: String,
        val exposureTime: String,
        val fNumber: String,
        val iso: String,
        val focalLength: String,
        val focalLength35mm: String,
        val dateTime: String,
        val latDeg: Double,
        val lonDeg: Double,
        val altitudeM: Double,
        val artist: String,
        val copyright: String,
        val software: String,
        val headline: String,
        val title: String,
        val caption: String,
        val keywords: List<String>,
        val city: String,
        val state: String,
        val country: String,
        val credit: String,
        val source: String
    )

    fun injectMetadataToJpeg(file: File, meta: SamplePhotoMetadata) {
        try {
            val originalBytes = file.readBytes()
            if (originalBytes.size < 4 || originalBytes[0] != 0xFF.toByte() || originalBytes[1] != 0xD8.toByte()) {
                return
            }

            // 1. Build IPTC-IIM bytes
            val iptcData = buildIptcBytes(meta)
            val app13Segment = buildApp13Segment(iptcData)

            // 2. Build XMP XML & APP1 segment
            val xmpXml = buildXmpXml(meta)
            val xmpApp1Segment = buildXmpApp1Segment(xmpXml)

            // 3. Insert APP13 & XMP APP1 right after SOI (0xFF 0xD8)
            val outStream = ByteArrayOutputStream()
            outStream.write(0xFF)
            outStream.write(0xD8)

            // Write our XMP APP1 segment
            outStream.write(xmpApp1Segment)

            // Write our IPTC APP13 segment
            outStream.write(app13Segment)

            // Write the rest of the original file (skip SOI 0xFF 0xD8)
            outStream.write(originalBytes, 2, originalBytes.size - 2)

            file.writeBytes(outStream.toByteArray())

            // 4. Use android.media.ExifInterface to write complete EXIF tags
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_MAKE, meta.make)
            exif.setAttribute(ExifInterface.TAG_MODEL, meta.model)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, meta.software)
            exif.setAttribute(ExifInterface.TAG_ARTIST, meta.artist)
            exif.setAttribute(ExifInterface.TAG_COPYRIGHT, meta.copyright)
            exif.setAttribute(ExifInterface.TAG_DATETIME, meta.dateTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, meta.dateTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, meta.dateTime)
            exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, meta.exposureTime)
            exif.setAttribute(ExifInterface.TAG_F_NUMBER, meta.fNumber)
            exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, meta.iso)
            exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, meta.focalLength)
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, meta.headline)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, "0")
            exif.setAttribute(ExifInterface.TAG_METERING_MODE, "5") // Pattern
            exif.setAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM, "3") // Aperture priority

            // GPS
            val latDms = convertToDms(meta.latDeg)
            val lonDms = convertToDms(meta.lonDeg)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latDms)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (meta.latDeg >= 0) "N" else "S")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lonDms)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (meta.lonDeg >= 0) "E" else "W")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${Math.abs(meta.altitudeM).toInt()}/1")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (meta.altitudeM >= 0) "0" else "1")

            exif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun convertToDms(coord: Double): String {
        val abs = Math.abs(coord)
        val deg = abs.toInt()
        val min = ((abs - deg) * 60).toInt()
        val sec = Math.round((abs - deg - min / 60.0) * 3600.0 * 100.0)
        return "$deg/1,$min/1,$sec/100"
    }

    private fun buildIptcBytes(meta: SamplePhotoMetadata): ByteArray {
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

        // Record 1: Coded Character Set UTF-8
        val utf8Code = "\u001B%G".toByteArray(Charsets.ISO_8859_1)
        out.write(0x1C)
        out.write(1)
        out.write(90) // Coded Character Set
        out.write(0)
        out.write(utf8Code.size)
        out.write(utf8Code)

        // Record 2: Application datasets
        writeDataset(2, 5, meta.title)
        writeDataset(2, 105, meta.headline)
        writeDataset(2, 120, meta.caption)
        writeDataset(2, 80, meta.artist)
        writeDataset(2, 85, "Staff Photographer")
        writeDataset(2, 110, meta.credit)
        writeDataset(2, 115, meta.source)
        writeDataset(2, 116, meta.copyright)
        writeDataset(2, 90, meta.city)
        writeDataset(2, 95, meta.state)
        writeDataset(2, 101, meta.country)
        writeDataset(2, 100, "USA")
        writeDataset(2, 55, meta.dateTime.substringBefore(" ").replace(":", "-"))
        writeDataset(2, 60, meta.dateTime.substringAfter(" "))
        writeDataset(2, 65, meta.software)

        for (kw in meta.keywords) {
            writeDataset(2, 25, kw)
        }

        return out.toByteArray()
    }

    private fun buildApp13Segment(iptcData: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        // Marker
        out.write(0xFF)
        out.write(0xED) // APP13

        // 8BIM block for IPTC (0x0404)
        val bimBlock = ByteArrayOutputStream()
        bimBlock.write("8BIM".toByteArray(Charsets.US_ASCII))
        bimBlock.write(0x04)
        bimBlock.write(0x04) // 0x0404 IPTC-NAA
        // Pascal string name (0 length, 2 bytes)
        bimBlock.write(0x00)
        bimBlock.write(0x00)
        // 4-byte size
        bimBlock.write((iptcData.size shr 24) and 0xFF)
        bimBlock.write((iptcData.size shr 16) and 0xFF)
        bimBlock.write((iptcData.size shr 8) and 0xFF)
        bimBlock.write(iptcData.size and 0xFF)
        bimBlock.write(iptcData)
        if (iptcData.size % 2 != 0) {
            bimBlock.write(0x00) // 8BIM padding
        }

        val bimBytes = bimBlock.toByteArray()
        val photoshopHeader = "Photoshop 3.0\u0000".toByteArray(Charsets.US_ASCII)

        val totalLen = 2 + photoshopHeader.size + bimBytes.size
        out.write((totalLen shr 8) and 0xFF)
        out.write(totalLen and 0xFF)
        out.write(photoshopHeader)
        out.write(bimBytes)

        return out.toByteArray()
    }

    private fun buildXmpXml(meta: SamplePhotoMetadata): String {
        val keywordsXml = meta.keywords.joinToString("\n") { "          <rdf:li>$it</rdf:li>" }
        return """
            <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 7.0">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                    xmlns:dc="http://purl.org/dc/elements/1.1/"
                    xmlns:photoshop="http://ns.adobe.com/photoshop/1.0/"
                    xmlns:xmp="http://ns.adobe.com/xap/1.0/"
                    xmlns:xmpRights="http://ns.adobe.com/xap/1.0/rights/"
                    xmlns:xmpMM="http://ns.adobe.com/xmp/1.0/mm/"
                    xmlns:tiff="http://ns.adobe.com/tiff/1.0/"
                    xmlns:exif="http://ns.adobe.com/exif/1.0/"
                    xmlns:aux="http://ns.adobe.com/exif/1.0/aux/">
                  <dc:title>
                    <rdf:Alt>
                      <rdf:li xml:lang="x-default">${meta.title}</rdf:li>
                    </rdf:Alt>
                  </dc:title>
                  <dc:description>
                    <rdf:Alt>
                      <rdf:li xml:lang="x-default">${meta.caption}</rdf:li>
                    </rdf:Alt>
                  </dc:description>
                  <dc:creator>
                    <rdf:Seq>
                      <rdf:li>${meta.artist}</rdf:li>
                    </rdf:Seq>
                  </dc:creator>
                  <dc:rights>
                    <rdf:Alt>
                      <rdf:li xml:lang="x-default">${meta.copyright}</rdf:li>
                    </rdf:Alt>
                  </dc:rights>
                  <dc:subject>
                    <rdf:Bag>
            $keywordsXml
                    </rdf:Bag>
                  </dc:subject>
                  <photoshop:Headline>${meta.headline}</photoshop:Headline>
                  <photoshop:City>${meta.city}</photoshop:City>
                  <photoshop:State>${meta.state}</photoshop:State>
                  <photoshop:Country>${meta.country}</photoshop:Country>
                  <photoshop:Credit>${meta.credit}</photoshop:Credit>
                  <photoshop:Source>${meta.source}</photoshop:Source>
                  <photoshop:DateCreated>${meta.dateTime}</photoshop:DateCreated>
                  <xmp:CreatorTool>${meta.software}</xmp:CreatorTool>
                  <xmp:CreateDate>${meta.dateTime}</xmp:CreateDate>
                  <xmp:ModifyDate>${meta.dateTime}</xmp:ModifyDate>
                  <xmp:Rating>5</xmp:Rating>
                  <xmp:Label>Approved</xmp:Label>
                  <xmpRights:Marked>True</xmpRights:Marked>
                  <xmpRights:WebStatement>https://creativecommons.org/licenses/by-sa/4.0/</xmpRights:WebStatement>
                  <xmpMM:DocumentID>xmp.did:${meta.title.hashCode().toString(16)}</xmpMM:DocumentID>
                  <tiff:Make>${meta.make}</tiff:Make>
                  <tiff:Model>${meta.model}</tiff:Model>
                  <exif:ExposureTime>${meta.exposureTime}</exif:ExposureTime>
                  <exif:FNumber>${meta.fNumber}</exif:FNumber>
                  <exif:ISOSpeedRatings>
                    <rdf:Seq>
                      <rdf:li>${meta.iso}</rdf:li>
                    </rdf:Seq>
                  </exif:ISOSpeedRatings>
                  <exif:FocalLength>${meta.focalLength}</exif:FocalLength>
                  <aux:Lens>${meta.lensModel}</aux:Lens>
                  <aux:LensInfo>${meta.lensModel}</aux:LensInfo>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
        """.trimIndent()
    }

    private fun buildXmpApp1Segment(xmpXml: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xFF)
        out.write(0xE1) // APP1

        val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
        val xmlBytes = xmpXml.toByteArray(Charsets.UTF_8)
        val totalLen = 2 + xmpHeader.size + xmlBytes.size

        out.write((totalLen shr 8) and 0xFF)
        out.write(totalLen and 0xFF)
        out.write(xmpHeader)
        out.write(xmlBytes)

        return out.toByteArray()
    }
}
