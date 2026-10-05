package com.example.data.metadata

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Single process boundary for the bundled ExifTool runtime.
 *
 * Commands are passed as argv entries (never through a shell). SAF/content URIs
 * are staged into an app-private temporary file and copied back after writes.
 */
class ExifTool(private val context: Context) {
    private val appContext = context.applicationContext

    suspend fun readJson(file: File): String = withContext(Dispatchers.IO) {
        require(file.isFile && file.canRead()) { "Unreadable metadata source: " + file.path }
        run(listOf("-j", "-G1", "-a", "-s", "-n", "-struct", file.absolutePath))
    }

    suspend fun readJson(uri: Uri, displayName: String): String = withContext(Dispatchers.IO) {
        val staged = stage(uri, displayName)
            ?: throw ExifToolException("Unable to open metadata URI")
        try {
            run(listOf("-j", "-G1", "-a", "-s", "-n", "-struct", staged.absolutePath))
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
            val args = buildList {
                add("-overwrite_original")
                values.filterValues { it.isNotBlank() }.forEach { (tag, value) ->
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
                    add("-GPSAltitude=$gpsAltitude")
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
                add("-XMP-dc:Description=" + cleanCaption)
                add("-EXIF:ImageDescription=" + cleanCaption)
            }
            tags.asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .take(MAX_KEYWORDS)
                .forEach { add("-XMP-dc:Subject+=" + it) }
            add(file.absolutePath)
        }

    private fun run(args: List<String>): String {
        val executable = File(appContext.applicationInfo.nativeLibraryDir, EXECUTABLE_NAME)
        check(executable.canExecute()) {
            "Bundled ExifTool runtime is unavailable: " + executable.absolutePath
        }

        val command = buildList {
            add(executable.absolutePath)
            add("-charset")
            add("filename=UTF8")
            addAll(args)
        }

        val process = ProcessBuilder(command)
            .directory(appContext.filesDir)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }

        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw ExifToolException("ExifTool timed out")
        }
        if (process.exitValue() != 0) {
            throw ExifToolException(
                "ExifTool failed (" + process.exitValue() + "): " +
                    stderr.trim().take(MAX_ERROR_CHARS)
            )
        }
        return stdout
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
        private const val EXECUTABLE_NAME = "libexiftool.so"
        private const val TIMEOUT_SECONDS = 45L
        private const val MAX_ERROR_CHARS = 2_000
        private const val MAX_KEYWORDS = 50
    }
}
