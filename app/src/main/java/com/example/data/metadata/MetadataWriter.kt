package com.example.data.metadata

import android.content.Context
import java.io.File

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

    /**
     * Writes the metadata fields exposed by the existing sample API through ExifTool.
     * The old byte-level JPEG surgery has been removed.
     */
    suspend fun injectMetadata(
        context: Context,
        file: File,
        meta: SamplePhotoMetadata
    ): Boolean {
        return ExifTool(context).writeMetadata(
            file = file,
            values = linkedMapOf(
                "EXIF:Make" to meta.make,
                "EXIF:Model" to meta.model,
                "EXIF:LensModel" to meta.lensModel,
                "EXIF:ExposureTime" to meta.exposureTime,
                "EXIF:FNumber" to meta.fNumber,
                "EXIF:ISO" to meta.iso,
                "EXIF:FocalLength" to meta.focalLength,
                "EXIF:FocalLengthIn35mmFormat" to meta.focalLength35mm,
                "EXIF:DateTimeOriginal" to meta.dateTime,
                "EXIF:DateTime" to meta.dateTime,
                "EXIF:DateTimeDigitized" to meta.dateTime,
                "EXIF:Artist" to meta.artist,
                "EXIF:Copyright" to meta.copyright,
                "EXIF:Software" to meta.software,
                "XMP-photoshop:Headline" to meta.headline,
                "XMP-dc:Title" to meta.title,
                "XMP-dc:Description" to meta.caption,
                "XMP-photoshop:City" to meta.city,
                "XMP-photoshop:State" to meta.state,
                "XMP-photoshop:Country" to meta.country,
                "XMP-photoshop:Credit" to meta.credit,
                "XMP-photoshop:Source" to meta.source
            ),
            gpsLatitude = meta.latDeg,
            gpsLongitude = meta.lonDeg,
            gpsAltitude = meta.altitudeM,
            keywords = meta.keywords
        )
    }

    suspend fun writeAiMetadata(
        context: Context,
        file: File,
        caption: String,
        tags: List<String>
    ): Boolean = ExifTool(context).writeAiMetadata(file, caption, tags)
}
