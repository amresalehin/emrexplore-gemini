package com.example.data.brain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.data.ai.ProviderType
import com.example.data.local.AiProviderConfigEntity
import com.example.data.metadata.MetadataExtractor
import com.example.data.metadata.MetadataReport
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

data class BrainFileContent(
    val file: File,
    val mimeType: String,
    val isImage: Boolean,
    val text: String,
    val imageBase64: String?,
    val metadata: MetadataReport?,
    val metadataSummary: String
)

class BrainContentReader(
    private val context: android.content.Context
) {
    private val metadataExtractor = MetadataExtractor(context.applicationContext)

    fun read(file: File, config: AiProviderConfigEntity): BrainFileContent {
        require(file.exists() && file.isFile && file.canRead()) {
            "File is not readable: " + file.absolutePath
        }

        val extension = file.extension.lowercase(Locale.US)
        val isImage = extension in IMAGE_EXTENSIONS
        val mimeType = inferMime(extension, isImage)
        val metadata = if (isImage) runCatching { metadataExtractor.extract(file) }.getOrNull() else null
        val metadataSummary = buildMetadataSummary(file, metadata, config)

        return when {
            isImage -> BrainFileContent(
                file = file,
                mimeType = mimeType,
                isImage = true,
                text = "",
                imageBase64 = encodePreview(file),
                metadata = metadata,
                metadataSummary = metadataSummary
            )

            extension == "pdf" -> BrainFileContent(
                file = file,
                mimeType = "application/pdf",
                isImage = false,
                text = readPdf(file),
                imageBase64 = null,
                metadata = null,
                metadataSummary = metadataSummary
            )

            extension in TEXT_EXTENSIONS -> BrainFileContent(
                file = file,
                mimeType = mimeType,
                isImage = false,
                text = readBoundedText(file, MAX_TEXT_CHARS),
                imageBase64 = null,
                metadata = null,
                metadataSummary = metadataSummary
            )

            else -> BrainFileContent(
                file = file,
                mimeType = mimeType,
                isImage = false,
                text = metadataSummary,
                imageBase64 = null,
                metadata = null,
                metadataSummary = metadataSummary
            )
        }
    }

    private fun buildMetadataSummary(
        file: File,
        report: MetadataReport?,
        config: AiProviderConfigEntity
    ): String {
        val allowPreciseLocation = ProviderType.fromString(config.providerType) == ProviderType.OLLAMA
        return buildString {
            append("File Name: ").append(file.name).append('\n')
            append("Size: ").append(file.length()).append(" bytes\n")
            append("Last Modified: ")
                .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(file.lastModified())))
                .append('\n')

            report?.summary?.let { meta ->
                meta.make?.takeIf { it.isNotBlank() }?.let {
                    append("Camera: ").append(it)
                    meta.model?.takeIf { model -> model.isNotBlank() }?.let { model ->
                        append(' ').append(model)
                    }
                    append('\n')
                }
                meta.dateTimeOriginal?.takeIf { it.isNotBlank() }?.let {
                    append("Date Shot: ").append(it).append('\n')
                }
                if (meta.imageWidth > 0 && meta.imageHeight > 0) {
                    append("Resolution: ").append(meta.imageWidth).append('x').append(meta.imageHeight).append('\n')
                }
                meta.title?.takeIf { it.isNotBlank() }?.let { append("Title: ").append(it).append('\n') }
                meta.description?.takeIf { it.isNotBlank() }?.let { append("Description: ").append(it).append('\n') }
                if (meta.keywords.isNotEmpty()) {
                    append("Keywords: ").append(meta.keywords.joinToString(", ")).append('\n')
                }
                if (allowPreciseLocation) {
                    meta.city?.takeIf { it.isNotBlank() }?.let {
                        append("Location: ").append(it).append(", ").append(meta.country.orEmpty()).append('\n')
                    }
                    if (meta.latitude != null && meta.longitude != null) {
                        append("GPS: ").append(meta.latitude).append(", ").append(meta.longitude).append('\n')
                    }
                }
            }
        }.trim()
    }

    private fun readBoundedText(file: File, maxChars: Int): String {
        file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8192)
            val out = StringBuilder(minOf(maxChars, 8192))
            while (out.length < maxChars) {
                val remaining = maxChars - out.length
                val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
                if (count <= 0) break
                out.append(buffer, 0, count)
            }
            return out.toString()
        }
    }

    private fun readPdf(file: File): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file).use { pdf ->
            if (pdf.isEncrypted) return ""
            val stripper = PDFTextStripper().apply {
                startPage = 1
                endPage = minOf(pdf.numberOfPages, MAX_PDF_PAGES)
            }
            return stripper.getText(pdf).take(MAX_TEXT_CHARS)
        }
    }

    private fun encodePreview(file: File): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = calculateSample(bounds.outWidth, bounds.outHeight, MAX_IMAGE_DIMENSION)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        return try {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }

    private fun calculateSample(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var nextWidth = width
        var nextHeight = height
        while (maxOf(nextWidth, nextHeight) > maxDimension * 2) {
            sample *= 2
            nextWidth = width / sample
            nextHeight = height / sample
        }
        return sample.coerceAtLeast(1)
    }

    private fun inferMime(extension: String, isImage: Boolean): String {
        if (isImage) return "image/" + if (extension == "jpg") "jpeg" else extension
        return when (extension) {
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            "html", "htm" -> "text/html"
            "kt", "java", "py", "js", "ts", "c", "cpp", "h", "sql", "sh", "bat",
            "txt", "md", "log", "yaml", "yml", "gradle", "kts", "properties", "ini", "conf", "env", "tsv" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    companion object {
        const val MAX_TEXT_CHARS = 50_000
        const val MAX_PDF_PAGES = 120
        const val MAX_IMAGE_DIMENSION = 1024

        val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp"
        )

        val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "xml", "csv", "html", "htm", "log",
            "kt", "java", "py", "js", "ts", "c", "cpp", "h", "sql",
            "yaml", "yml", "gradle", "kts", "properties", "ini", "conf", "env", "tsv"
        )

        val SUPPORTED_EXTENSIONS = IMAGE_EXTENSIONS + TEXT_EXTENSIONS + setOf("pdf")
    }
}
