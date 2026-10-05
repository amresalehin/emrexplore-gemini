package com.example.data.metadata

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Application-facing ExifTool adapter.
 *
 * ExifTool runs through the cross-compiled Perl interpreter shipped in jniLibs.
 * AssetExtractor installs the ExifTool script/library tree and wires XS modules
 * to the ABI-specific native libraries before the first invocation.
 */
class ExifTool(private val context: Context) {
    private val appContext = context.applicationContext

    suspend fun readJson(file: File): String = withContext(Dispatchers.IO) {
        ensureInstalled()
        run(listOf("-j", "-G1", "-a", "-s", "-n", "-struct", file.absolutePath))
    }

    suspend fun readJson(uri: Uri, displayName: String): String = withContext(Dispatchers.IO) {
        val staged = stage(uri, displayName)
            ?: throw ExifToolException("Unable to open metadata URI")
        try {
            readJson(staged)
        } finally {
            staged.delete()
        }
    }

    suspend fun writeMetadata(
        file: File,
        values: Map<String, String>,
        gpsLatitude: Double,
        gpsLongitude: Double,
        gpsAltitude: Double,
        keywords: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        if (!file.isFile || !file.canRead() || !file.canWrite()) return@withContext false
        runCatching {
            ensureInstalled()
            val args = buildList {
                add("-overwrite_original")
                values.filterValues(String::isNotBlank).forEach { (tag, value) ->
                    add("-$tag=$value")
                }
                keywords.asSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .take(MAX_KEYWORDS)
                    .forEach { add("-XMP-dc:Subject+=$it") }
                if (gpsLatitude.isFinite() && gpsLongitude.isFinite()) {
                    add("-GPSLatitude=$gpsLatitude")
                    add("-GPSLongitude=$gpsLongitude")
                    if (gpsAltitude.isFinite()) add("-GPSAltitude=$gpsAltitude")
                }
                add(file.absolutePath)
            }
            run(args)
            true
        }.getOrDefault(false)
    }

    suspend fun writeAiMetadata(file: File, caption: String, tags: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            if (!file.isFile || !file.canRead() || !file.canWrite()) return@withContext false
            runCatching {
                ensureInstalled()
                run(buildAiWriteArgs(file, caption, tags))
                true
            }.getOrDefault(false)
        }

    suspend fun writeAiMetadata(
        uri: Uri,
        displayName: String,
        caption: String,
        tags: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        val staged = stage(uri, displayName) ?: return@withContext false
        try {
            ensureInstalled()
            run(buildAiWriteArgs(staged, caption, tags))
            copyBack(uri, staged)
            true
        } catch (_: Exception) {
            false
        } finally {
            staged.delete()
        }
    }

    private fun buildAiWriteArgs(file: File, caption: String, tags: List<String>): List<String> =
        buildList {
            add("-overwrite_original")
            val cleanCaption = caption.trim()
            if (cleanCaption.isNotBlank()) {
                add("-XMP-dc:Description=$cleanCaption")
                add("-EXIF:ImageDescription=$cleanCaption")
            }
            tags.asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .take(MAX_KEYWORDS)
                .forEach { add("-XMP-dc:Subject+=$it") }
            add(file.absolutePath)
        }

    private fun ensureInstalled() {
        if (!AssetExtractor.isInstalled(appContext)) {
            kotlinx.coroutines.runBlocking {
                AssetExtractor.ensureInstalled(appContext)
            }
        }
        check(ExifToolRunner.isInstalled(appContext)) {
            "ExifTool runtime is not installed for this ABI"
        }
    }

    private fun run(args: List<String>): String {
        val command = ExifToolRunner.buildBaseCommand(appContext, args)
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw ExifToolException("ExifTool timed out")
        }
        if (process.exitValue() != 0) {
            throw ExifToolException("ExifTool failed: " + output.take(MAX_ERROR_CHARS))
        }
        return output
    }

    private fun stage(uri: Uri, displayName: String): File? {
        val dir = File(appContext.cacheDir, "exiftool").apply { mkdirs() }
        val safeName = displayName.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "input.bin" }
        val staged = File.createTempFile("src_", "_" + safeName, dir)
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                staged.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            staged
        } catch (_: Exception) {
            staged.delete()
            null
        }
    }

    private fun copyBack(uri: Uri, staged: File) {
        appContext.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            staged.inputStream().use { input -> input.copyTo(output) }
        } ?: throw ExifToolException("Unable to write metadata back to URI")
    }

    class ExifToolException(message: String) : IllegalStateException(message)

    companion object {
        private const val TIMEOUT_SECONDS = 45L
        private const val MAX_ERROR_CHARS = 2_000
        private const val MAX_KEYWORDS = 50
    }
}
