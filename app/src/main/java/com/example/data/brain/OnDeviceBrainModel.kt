package com.example.data.brain

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

enum class OnDeviceBrainModelStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    READY,
    ERROR
}

data class OnDeviceBrainModelSpec(
    val id: String,
    val displayName: String,
    val modelUrl: String,
    val modelSha256: String,
    val modelFileName: String,
    val vocabUrl: String,
    val maxTokens: Int,
    val embeddingDimension: Int,
    val sizeLabel: String
)

data class OnDeviceBrainModelUiState(
    val status: OnDeviceBrainModelStatus = OnDeviceBrainModelStatus.NOT_INSTALLED,
    val modelId: String = "",
    val displayName: String = "On-device semantic model",
    val sizeLabel: String = "",
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null
)

object OnDeviceBrainModelCatalog {
    private const val REVISION = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41"
    private const val BASE_URL =
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/$REVISION/"

    private const val ARM64_SHA256 =
        "4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474"
    private const val GENERIC_SHA256 =
        "6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452"

    private val arm64 = OnDeviceBrainModelSpec(
        id = "all-MiniLM-L6-v2-int8-arm64",
        displayName = "all-MiniLM-L6-v2 · INT8 (ARM64)",
        modelUrl = BASE_URL + "onnx/model_qint8_arm64.onnx",
        modelSha256 = ARM64_SHA256,
        modelFileName = "model.onnx",
        vocabUrl = BASE_URL + "vocab.txt",
        maxTokens = 256,
        embeddingDimension = 384,
        sizeLabel = "~23 MB"
    )

    private val generic = OnDeviceBrainModelSpec(
        id = "all-MiniLM-L6-v2-fp32",
        displayName = "all-MiniLM-L6-v2 · FP32",
        modelUrl = BASE_URL + "onnx/model.onnx",
        modelSha256 = GENERIC_SHA256,
        modelFileName = "model.onnx",
        vocabUrl = BASE_URL + "vocab.txt",
        maxTokens = 256,
        embeddingDimension = 384,
        sizeLabel = "~90 MB"
    )

    val default: OnDeviceBrainModelSpec
        get() = if (Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) arm64 else generic
}

class OnDeviceEmbeddingModelManager(context: Context) {
    private val root = context.applicationContext.filesDir.resolve("brain-models")
    private val httpClient = OkHttpClient()

    init {
        root.mkdirs()
    }

    fun defaultSpec(): OnDeviceBrainModelSpec = OnDeviceBrainModelCatalog.default

    fun modelDirectory(spec: OnDeviceBrainModelSpec = defaultSpec()): File =
        root.resolve(spec.id)

    fun modelFile(spec: OnDeviceBrainModelSpec = defaultSpec()): File =
        modelDirectory(spec).resolve(spec.modelFileName)

    fun vocabFile(spec: OnDeviceBrainModelSpec = defaultSpec()): File =
        modelDirectory(spec).resolve("vocab.txt")

    private fun manifestFile(spec: OnDeviceBrainModelSpec = defaultSpec()): File =
        modelDirectory(spec).resolve("manifest.txt")

    fun isInstalled(spec: OnDeviceBrainModelSpec = defaultSpec()): Boolean {
        val model = modelFile(spec)
        val vocab = vocabFile(spec)
        val manifest = manifestFile(spec)

        return model.isFile &&
            model.length() > 1_000_000L &&
            vocab.isFile &&
            vocab.length() >= 1000L &&
            manifest.isFile &&
            runCatching {
                manifest.readText(Charsets.UTF_8).trim() == manifestLine(spec)
            }.getOrDefault(false)
    }

    fun uiState(error: String? = null): OnDeviceBrainModelUiState {
        val spec = defaultSpec()
        return OnDeviceBrainModelUiState(
            status = when {
                error != null -> OnDeviceBrainModelStatus.ERROR
                isInstalled(spec) -> OnDeviceBrainModelStatus.READY
                else -> OnDeviceBrainModelStatus.NOT_INSTALLED
            },
            modelId = spec.id,
            displayName = spec.displayName,
            sizeLabel = spec.sizeLabel,
            error = error
        )
    }

    suspend fun downloadDefaultModel(
        onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val spec = defaultSpec()
        if (isInstalled(spec)) {
            onProgress(1f, modelFile(spec).length(), modelFile(spec).length())
            return@withContext
        }

        val dir = modelDirectory(spec)
        dir.mkdirs()
        val modelPart = File(dir, spec.modelFileName + ".part")
        val vocabPart = File(dir, "vocab.txt.part")

        try {
            downloadVerified(
                url = spec.modelUrl,
                destination = modelPart,
                expectedSha256 = spec.modelSha256
            ) { downloaded, total ->
                val knownTotal = if (total > 0L) total else 1L
                onProgress((downloaded.toDouble() / knownTotal).toFloat() * 0.95f, downloaded, knownTotal)
            }

            downloadVerified(
                url = spec.vocabUrl,
                destination = vocabPart,
                expectedSha256 = null
            ) { downloaded, total ->
                val knownTotal = if (total > 0L) total else 1L
                onProgress(0.95f + (downloaded.toDouble() / knownTotal).toFloat() * 0.05f, downloaded, knownTotal)
            }

            if (!vocabLooksValid(vocabPart)) {
                throw IOException("Downloaded tokenizer vocabulary is invalid")
            }

            replaceAtomically(modelPart, modelFile(spec))
            replaceAtomically(vocabPart, vocabFile(spec))
            manifestFile(spec).writeText(manifestLine(spec), Charsets.UTF_8)

            if (!isInstalled(spec)) {
                throw IOException("On-device Brain model failed final validation")
            }

            onProgress(1f, modelFile(spec).length(), modelFile(spec).length())
        } catch (error: Exception) {
            modelPart.delete()
            vocabPart.delete()
            throw error
        }
    }

    fun deleteDefaultModel() {
        modelDirectory(defaultSpec()).deleteRecursively()
    }

    private fun manifestLine(spec: OnDeviceBrainModelSpec): String =
        listOf(spec.id, spec.modelSha256, spec.maxTokens, spec.embeddingDimension).joinToString("|")

    private fun vocabLooksValid(file: File): Boolean {
        if (!file.isFile || file.length() < 1000L) return false
        val required = setOf("[PAD]", "[UNK]", "[CLS]", "[SEP]")
        val found = HashSet<String>()
        var count = 0
        runCatching {
            file.forEachLine(Charsets.UTF_8) { line ->
                count++
                if (line in required) found += line
            }
        }.getOrElse { return false }
        return count >= 30_000 && found.containsAll(required)
    }

    private fun replaceAtomically(source: File, target: File) {
        if (!source.isFile) throw IOException("Temporary model file is missing: ${source.name}")

        val backup = File(target.parentFile, target.name + ".old")
        if (backup.exists() && !backup.delete()) {
            throw IOException("Could not remove stale ${backup.name}")
        }

        var backedUp = false
        if (target.exists()) {
            if (!target.renameTo(backup)) {
                throw IOException("Could not stage replacement for ${target.name}")
            }
            backedUp = true
        }

        try {
            if (!source.renameTo(target)) {
                throw IOException("Could not finalize ${target.name}")
            }
            if (backedUp) backup.delete()
        } catch (error: Exception) {
            target.delete()
            if (backedUp && backup.exists()) backup.renameTo(target)
            throw error
        }
    }

    private fun downloadVerified(
        url: String,
        destination: File,
        expectedSha256: String?,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Model download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Model download returned no body")
            val total = body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L

            destination.outputStream().buffered().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                }
            }

            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256 != null && !actualSha256.equals(expectedSha256, ignoreCase = true)) {
                destination.delete()
                throw IOException("Downloaded model checksum mismatch")
            }
        }
    }
}
