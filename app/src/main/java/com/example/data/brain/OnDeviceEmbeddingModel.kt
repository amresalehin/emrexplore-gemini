package com.example.data.brain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.sqrt

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
        if (!model.isFile || model.length() <= 0L || !vocab.isFile || vocab.length() < 1000L || !manifest.isFile) {
            return false
        }
        return runCatching {
            manifest.readText(Charsets.UTF_8).trim() ==
                manifestLine(spec) && vocabLooksValid(vocab)
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
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return false
        return text.contains("[PAD]\\n") &&
            text.contains("[UNK]\\n") &&
            text.contains("[CLS]\\n") &&
            text.contains("[SEP]\\n") &&
            text.lineSequence().count() >= 30000
    }

    private fun replaceAtomically(source: File, target: File) {
        if (!source.isFile) throw IOException("Temporary model file is missing: ${source.name}")
        if (target.exists() && !target.delete()) {
            throw IOException("Could not replace existing ${target.name}")
        }
        if (!source.renameTo(target)) {
            throw IOException("Could not finalize ${target.name}")
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

private class BertWordPieceTokenizer(
    vocabFile: File,
    private val maxTokens: Int
) {
    private val vocab: Map<String, Int>
    private val unkId: Int
    private val clsId: Int
    private val sepId: Int

    init {
        val map = HashMap<String, Int>(32_000)
        vocabFile.useLines(Charsets.UTF_8) { lines ->
            lines.forEachIndexed { index, token ->
                map[token.trimEnd('\\r')] = index
            }
        }
        vocab = map
        unkId = requireToken("[UNK]")
        clsId = requireToken("[CLS]")
        sepId = requireToken("[SEP]")
    }

    fun encode(text: String): IntArray {
        val basicTokens = basicTokens(text)
        val pieces = ArrayList<String>(basicTokens.size * 2)
        for (token in basicTokens) {
            pieces += wordPiece(token)
        }

        val contentLimit = (maxTokens - 2).coerceAtLeast(1)
        val limited = if (pieces.size > contentLimit) pieces.take(contentLimit) else pieces
        val ids = IntArray(limited.size + 2)
        ids[0] = clsId
        limited.forEachIndexed { index, piece -> ids[index + 1] = vocab[piece] ?: unkId }
        ids[ids.lastIndex] = sepId
        return ids
    }

    private fun wordPiece(token: String): List<String> {
        if (token.isBlank()) return emptyList()
        val result = ArrayList<String>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var matched: String? = null

            while (start < end) {
                val candidate = token.substring(start, end).let {
                    if (start == 0) it else "##${it}"
                }
                if (vocab.containsKey(candidate)) {
                    matched = candidate
                    break
                }
                end--
            }

            if (matched == null) return listOf("[UNK]")
            result += matched
            start = end
        }
        return result
    }

    private fun basicTokens(text: String): List<String> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .lowercase(Locale.US)
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }

        val out = ArrayList<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                out += current.toString()
                current.setLength(0)
            }
        }

        for (ch in normalized) {
            when {
                ch.isWhitespace() || Character.isISOControl(ch) -> flush()
                isCjk(ch) -> {
                    flush()
                    out += ch.toString()
                }
                isPunctuation(ch) -> {
                    flush()
                    out += ch.toString()
                }
                else -> current.append(ch)
            }
        }
        flush()
        return out
    }

    private fun isPunctuation(ch: Char): Boolean =
        Character.getType(ch) in setOf(
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            Character.MATH_SYMBOL.toInt(),
            Character.CURRENCY_SYMBOL.toInt(),
            Character.MODIFIER_SYMBOL.toInt(),
            Character.OTHER_SYMBOL.toInt()
        )

    private fun isCjk(ch: Char): Boolean {
        val block = Character.UnicodeBlock.of(ch)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA
    }

    private fun requireToken(token: String): Int =
        vocab[token] ?: throw IllegalArgumentException("Tokenizer vocabulary is missing ${token}")
}

class OnDeviceEmbeddingEngine(
    context: Context
) {
    private val manager = OnDeviceEmbeddingModelManager(context)
    private val environment = OrtEnvironment.getEnvironment()
    private val lock = Any()

    @Volatile
    private var session: OrtSession? = null
    @Volatile
    private var tokenizer: BertWordPieceTokenizer? = null
    @Volatile
    private var loadedModelId: String? = null

    fun modelIdIfReady(): String? =
        manager.defaultSpec().takeIf { manager.isInstalled(it) }?.id

    fun modelSignature(): String =
        manager.defaultSpec().id + if (manager.isInstalled()) "-ready" else "-missing"

    fun isReady(): Boolean = manager.isInstalled()

    suspend fun embedText(text: String): FloatArray? =
        withContext(Dispatchers.Default) {
            if (text.isBlank() || !manager.isInstalled()) return@withContext null
            embedBatch(listOf(text)).firstOrNull()
        }

    suspend fun embedTextPassages(passages: List<String>): List<FloatArray>? =
        withContext(Dispatchers.Default) {
            if (passages.isEmpty() || !manager.isInstalled()) return@withContext emptyList()
            embedBatch(passages)
        }

    fun unload() {
        synchronized(lock) {
            session?.close()
            session = null
            tokenizer = null
            loadedModelId = null
        }
    }

    fun manager(): OnDeviceEmbeddingModelManager = manager

    private fun embedBatch(texts: List<String>): List<FloatArray> {
        val spec = manager.defaultSpec()
        synchronized(lock) {
            ensureLoaded(spec)
            val activeSession = session ?: throw IllegalStateException("ONNX Brain session is unavailable")
            val activeTokenizer = tokenizer ?: throw IllegalStateException("Brain tokenizer is unavailable")
            return texts.chunked(8).flatMap { batch ->
                runBatch(activeSession, activeTokenizer, spec, batch)
            }
        }
    }

    private fun ensureLoaded(spec: OnDeviceBrainModelSpec) {
        if (loadedModelId == spec.id && session != null && tokenizer != null) return

        session?.close()
        val modelFile = manager.modelFile(spec)
        val vocabFile = manager.vocabFile(spec)
        if (!manager.isInstalled(spec) || !modelFile.isFile || !vocabFile.isFile) {
            throw IllegalStateException("On-device Brain model is not installed")
        }

        val options = OrtSession.SessionOptions()
        runCatching {
            options.addXnnpack(emptyMap<String, String>())
        }
        val newSession = environment.createSession(modelFile.absolutePath, options)
        val newTokenizer = BertWordPieceTokenizer(vocabFile, spec.maxTokens)

        session = newSession
        tokenizer = newTokenizer
        loadedModelId = spec.id
    }

    private fun runBatch(
        session: OrtSession,
        tokenizer: BertWordPieceTokenizer,
        spec: OnDeviceBrainModelSpec,
        texts: List<String>
    ): List<FloatArray> {
        val encoded = texts.map(tokenizer::encode)
        val sequenceLength = encoded.maxOf { it.size }.coerceAtLeast(2)
        val inputIds = Array(encoded.size) { row ->
            LongArray(sequenceLength) { col ->
                if (col < encoded[row].size) encoded[row][col].toLong() else 0L
            }
        }
        val attention = Array(encoded.size) { row ->
            LongArray(sequenceLength) { col ->
                if (col < encoded[row].size) 1L else 0L
            }
        }
        val tokenTypes = Array(encoded.size) { LongArray(sequenceLength) }

        val inputNames = session.inputNames
        val inputs = linkedMapOf<String, OnnxTensor>()

        try {
            if ("input_ids" in inputNames) {
                inputs["input_ids"] = OnnxTensor.createTensor(environment, inputIds)
            }
            if ("attention_mask" in inputNames) {
                inputs["attention_mask"] = OnnxTensor.createTensor(environment, attention)
            }
            if ("token_type_ids" in inputNames) {
                inputs["token_type_ids"] = OnnxTensor.createTensor(environment, tokenTypes)
            }

            if (inputs.isEmpty()) {
                throw IllegalStateException("On-device Brain model exposes no supported BERT inputs")
            }

            session.run(inputs).use { result ->
                val output = result[0].value
                return extractEmbeddings(output, attention, spec.embeddingDimension)
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun extractEmbeddings(
        output: Any?,
        attention: Array<LongArray>,
        expectedDimension: Int
    ): List<FloatArray> {
        val pooled = when (output) {
            is Array<*> -> {
                if (output.isEmpty()) {
                    throw IllegalStateException("On-device Brain model returned an empty tensor")
                }

                val first = output[0]
                when (first) {
                    is FloatArray -> output.map { it as FloatArray }
                    is Array<*> -> output.mapIndexed { batchIndex, batchValue ->
                        val sequence = batchValue as? Array<*>
                            ?: throw IllegalStateException("Unexpected ONNX tensor rank")
                        val vector = FloatArray(expectedDimension)
                        var count = 0L
                        for (tokenIndex in sequence.indices) {
                            if (tokenIndex >= attention[batchIndex].size || attention[batchIndex][tokenIndex] == 0L) continue
                            val tokenVector = sequence[tokenIndex] as? FloatArray
                                ?: throw IllegalStateException("Unexpected ONNX hidden-state tensor")
                            val dims = minOf(expectedDimension, tokenVector.size)
                            for (dim in 0 until dims) vector[dim] += tokenVector[dim]
                            count++
                        }
                        if (count == 0L) throw IllegalStateException("Model returned no active tokens")
                        for (dim in vector.indices) vector[dim] /= count.toFloat()
                        normalize(vector)
                        vector
                    }
                    else -> throw IllegalStateException("Unsupported ONNX Brain output type: ${first?.javaClass?.name}")
                }
            }
            else -> throw IllegalStateException("Unsupported ONNX Brain output tensor")
        }

        return pooled.map { vector ->
            if (vector.size != expectedDimension) vector.copyOf(expectedDimension) else vector
        }
    }

    private fun normalize(vector: FloatArray) {
        var normSquared = 0.0
        for (value in vector) normSquared += value * value
        val norm = sqrt(normSquared)
        if (norm <= 1e-8) return
        val inverse = (1.0 / norm).toFloat()
        for (i in vector.indices) vector[i] *= inverse
    }
}

object BrainVectorCodec {
    fun toJson(vector: FloatArray): String {
        val builder = StringBuilder(vector.size * 8).append('[')
        vector.forEachIndexed { index, value ->
            if (index > 0) builder.append(',')
            builder.append(String.format(Locale.US, "%.6f", value))
        }
        return builder.append(']').toString()
    }

    fun fromJson(json: String?): FloatArray {
        if (json.isNullOrBlank()) return FloatArray(0)
        val body = json.trim().removePrefix("[").removeSuffix("]")
        if (body.isBlank()) return FloatArray(0)
        return body.split(',').mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA <= 1e-12 || normB <= 1e-12) return 0f
        return (dot / sqrt(normA * normB)).toFloat().coerceIn(-1f, 1f)
    }
}
