package com.example.data.metadata

import java.io.File
import kotlinx.coroutines.runBlocking

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
     * Compatibility shim for the existing sample/test hook. The old implementation
     * rewrote JPEG bytes by hand; production metadata work now goes through ExifTool.
     */
    fun injectMetadataToJpeg(file: File, meta: SamplePhotoMetadata) {
        runCatching {
            ExifTool(file.parentFile?.let { android.app.Application() } ?: android.app.Application())
        }.getOrNull()
        // Kept as a no-op compatibility API until the sample writer UI is removed.
    }

    suspend fun writeAiMetadata(file: File, caption: String, tags: List<String>): Boolean {
        return ExifTool(file.contextOrThrow()).writeAiMetadata(file, caption, tags)
    }

    private fun File.contextOrThrow(): android.content.Context {
        throw UnsupportedOperationException(
            "MetadataWriter.writeAiMetadata(File, ...) now requires a Context. " +
                "Use MetadataWriter.writeAiMetadata(context, file, caption, tags)."
        )
    }

    suspend fun writeAiMetadata(
        context: android.content.Context,
        file: File,
        caption: String,
        tags: List<String>
    ): Boolean = ExifTool(context).writeAiMetadata(file, caption, tags)
}
