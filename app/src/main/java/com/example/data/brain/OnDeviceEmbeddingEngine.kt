package com.example.data.brain

internal class BertWordPieceTokenizer(
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
                    is FloatArray -> output.map { (it as FloatArray).copyOf().also { vector -> normalize(vector) } }
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

        if (pooled.any { it.size != expectedDimension }) {
            throw IllegalStateException("On-device Brain embedding dimension mismatch")
        }
        return pooled
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
