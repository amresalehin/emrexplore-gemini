package com.example.data.ai

import android.util.Log
import com.example.data.local.AiProviderConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

class AiProviderClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun validateEndpoint(raw: String, provider: ProviderType): okhttp3.HttpUrl {
        val url = raw.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid provider URL")
        val host = url.host.lowercase()
        val localEndpoint = provider in setOf(
            ProviderType.OLLAMA,
            ProviderType.OPENAI_COMPATIBLE,
            ProviderType.CUSTOM
        ) && (
            host in setOf("localhost", "127.0.0.1", "10.0.2.2") ||
            isPrivateIpv4(host)
        )
        if (url.scheme != "https" && !localEndpoint) throw IllegalArgumentException("Provider endpoint must use HTTPS")
        return url
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val (a, b) = octets
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254)
    }

    private class AiHttpException(
        val provider: String,
        val code: Int
    ) : RuntimeException("$provider HTTP $code")

    private fun safeHttpError(provider: String, code: Int): AiHttpException =
        AiHttpException(provider, code)

    suspend fun testConnection(config: AiProviderConfigEntity): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val provider = ProviderType.fromString(config.providerType)
            if (config.baseUrl.isBlank() && provider == ProviderType.CUSTOM) {
                return@withContext ConnectionTestResult(false, "Enter a base URL first.", System.currentTimeMillis() - startTime)
            }
            if (!isKeylessAiConfig(config) && config.apiKey.isBlank()) {
                return@withContext ConnectionTestResult(false, "Enter an API key first.", System.currentTimeMillis() - startTime)
            }
            if (config.chatModel.isBlank()) {
                return@withContext ConnectionTestResult(false, "Choose a chat model first. Tap Fetch models.", System.currentTimeMillis() - startTime)
            }

            val listedModels = try {
                listModels(config)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                null
            }
            if (!listedModels.isNullOrEmpty()) {
                val selectedModels = listOf(
                    "chat" to config.chatModel,
                    "vision" to config.visionModel
                )
                for ((role, modelIdRaw) in selectedModels) {
                    val modelId = modelIdRaw.trim()
                    if (modelId.isNotBlank() && listedModels.none { it.id == modelId }) {
                        return@withContext ConnectionTestResult(
                            false,
                            "Selected $role model is not available: $modelId. Tap Fetch and choose an available model.",
                            System.currentTimeMillis() - startTime
                        )
                    }
                }
            }

            val response = executePrompt(prompt = "Reply with OK only.", config = config, isTest = true)
            val duration = System.currentTimeMillis() - startTime
            if (response.isNotBlank()) {
                ConnectionTestResult(true, "Connected · ${provider.displayName}", duration)
            } else {
                ConnectionTestResult(false, "Server responded, but returned no text.", duration)
            }
        } catch (error: AiHttpException) {
            val duration = System.currentTimeMillis() - startTime
            Log.e("AiProviderClient", "Connection test failed: HTTP ${error.code}")
            val provider = ProviderType.fromString(config.providerType)
            val message = when {
                error.code == 401 || error.code == 403 -> "The endpoint is reachable, but the API key was rejected (${error.code})."
                error.code == 404 && config.baseUrl.contains("integrate.api.nvidia.com", ignoreCase = true) -> "NVIDIA returned 404 for this model or route. Tap Fetch models and choose a current model."
                error.code == 404 -> "The endpoint is reachable, but the path or selected model was not found. Check Base URL and choose a model from Fetch."
                else -> "${provider.displayName} returned HTTP ${error.code}."
            }
            ConnectionTestResult(false, message, duration)
        } catch (error: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e("AiProviderClient", "Connection test failed: " + error.javaClass.simpleName)
            ConnectionTestResult(false, error.localizedMessage?.take(160) ?: "Could not connect. Check the URL, key, and model.", duration)
        }
    }
    suspend fun analyzeDocument(
        text: String,
        fileName: String,
        config: AiProviderConfigEntity
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are a private-document analysis engine for Brain v2.
            Analyze the document named "$fileName".
            Extract:
            1. summary: A concise 2-sentence summary of the content.
            2. entities: Key named entities mentioned (locations, people, projects, concepts, organizations, events). Format: [{"name": "...", "type": "LOCATION|PERSON|TOPIC|ORGANIZATION|EVENT|CONCEPT"}]
            3. relations: Direct relationships between the file and key entities, or between entities. Format: [{"source": "...", "relation": "MENTIONS|DEFINES|LOCATED_AT|REFERENCES", "target": "...", "evidence": "..."}]
            4. tags: 3 to 6 high-level semantic tags.
            
            Return ONLY a valid JSON object matching this schema without any markdown formatting or commentary:
            {
              "summary": "...",
              "entities": [{"name": "...", "type": "..."}],
              "relations": [{"source": "...", "relation": "...", "target": "...", "evidence": "..."}],
              "tags": ["tag1", "tag2"]
            }
        """.trimIndent()

        val userPrompt = "Document content:\n" + text.take(6000)
        try {
            val responseText = executePrompt(
                prompt = "$systemPrompt\n\n$userPrompt",
                config = config
            )
            parseAnalysisJson(responseText, fileName)
        } catch (e: Exception) {
            Log.e("AiProviderClient", "Document analysis failed: " + e.javaClass.simpleName)
            fallbackAnalysis(fileName, text)
        }
    }


    suspend fun embedTextPassages(
        texts: List<String>,
        config: AiProviderConfigEntity
    ): List<FloatArray> {
        val model = config.textEmbeddingModel.ifBlank { config.embeddingModel }.trim()
        if (EmbeddingProviderType.fromString(config.embeddingProviderType) == EmbeddingProviderType.OFFLINE) return emptyList()
        return embedText(texts, config, model, "passage")
    }

    suspend fun listEmbeddingModels(config: AiProviderConfigEntity): List<AvailableAiModel> {
        if (EmbeddingProviderType.fromString(config.embeddingProviderType) == EmbeddingProviderType.OFFLINE) return emptyList()
        return listModels(embeddingConfig(config)).filter { it.supportsEmbedding || it.supportsMultimodalEmbedding }
    }
    suspend fun testEmbeddingConnection(config: AiProviderConfigEntity): ConnectionTestResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        if (EmbeddingProviderType.fromString(config.embeddingProviderType) == EmbeddingProviderType.OFFLINE) {
            return@withContext ConnectionTestResult(true, "On-device embedding · private", System.currentTimeMillis() - started)
        }
        val providerConfig = embeddingConfig(config)
        try {
            val provider = EmbeddingProviderType.fromString(config.embeddingProviderType)
            if (!isKeylessAiConfig(providerConfig) && config.embeddingApiKey.isBlank()) {
                return@withContext ConnectionTestResult(false, "Enter an embedding API key first.", System.currentTimeMillis() - started)
            }
            val model = config.textEmbeddingModel.ifBlank { config.embeddingModel }.trim()
            if (model.isBlank()) {
                return@withContext ConnectionTestResult(false, "Choose an embedding model first.", System.currentTimeMillis() - started)
            }
            val vector = embedText(listOf("embedding connection test"), config, model, "query").firstOrNull()
            if (vector == null || vector.isEmpty()) {
                ConnectionTestResult(false, "Embedding provider returned no vector.", System.currentTimeMillis() - started)
            } else {
                ConnectionTestResult(true, "Embedding connected · " + provider.displayName, System.currentTimeMillis() - started)
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            ConnectionTestResult(false, error.message?.take(180) ?: "Could not connect to embedding provider.", System.currentTimeMillis() - started)
        }
    }

    suspend fun embedTextQuery(
        text: String,
        config: AiProviderConfigEntity
    ): FloatArray? {
        if (EmbeddingProviderType.fromString(config.embeddingProviderType) == EmbeddingProviderType.OFFLINE) return null
        val model = config.textEmbeddingModel.ifBlank { config.embeddingModel }.trim()
        return embedText(listOf(text), config, model, "query").firstOrNull()
    }

    suspend fun embedMultimodalQuery(
        text: String,
        config: AiProviderConfigEntity
    ): FloatArray? {
        if (EmbeddingProviderType.fromString(config.embeddingProviderType) == EmbeddingProviderType.OFFLINE) return null
        val model = config.multimodalEmbeddingModel.ifBlank { config.textEmbeddingModel.ifBlank { config.embeddingModel } }.trim()
        return embedText(listOf(text), config, model, "query").firstOrNull()
    }

    suspend fun embedMultimodalDocument(
        base64Jpeg: String?,
        text: String,
        config: AiProviderConfigEntity
    ): FloatArray? = withContext(Dispatchers.IO) {
        val model = config.multimodalEmbeddingModel.trim()
            .ifBlank { config.textEmbeddingModel.trim().ifBlank { config.embeddingModel.trim() } }
        if (model.isBlank() || base64Jpeg.isNullOrBlank()) return@withContext null
        if (EmbeddingProviderType.fromString(config.embeddingProviderType) == EmbeddingProviderType.OFFLINE) return@withContext null
        when (EmbeddingProviderType.fromString(config.embeddingProviderType)) {
            EmbeddingProviderType.GEMINI, EmbeddingProviderType.OLLAMA -> embedText(
                listOf(text),
                config,
                config.textEmbeddingModel.ifBlank { config.embeddingModel },
                "passage"
            ).firstOrNull()
            else -> embedOpenAi(
                inputs = listOf("$text data:image/jpeg;base64,$base64Jpeg"),
                config = config,
                model = model,
                inputType = "passage",
                modality = "text_image"
            ).firstOrNull()
        }
    }

    private suspend fun embedText(
        texts: List<String>,
        config: AiProviderConfigEntity,
        model: String,
        inputType: String
    ): List<FloatArray> = withContext(Dispatchers.IO) {
        if (texts.isEmpty() || model.isBlank()) return@withContext emptyList()
        val effectiveConfig = embeddingConfig(config).copy(embeddingModel = model, textEmbeddingModel = model)
        texts.chunked(16).flatMap { batch ->
            when (EmbeddingProviderType.fromString(config.embeddingProviderType)) {
                EmbeddingProviderType.GEMINI -> embedGemini(batch, effectiveConfig)
                EmbeddingProviderType.OLLAMA -> embedOllama(batch, effectiveConfig)
                EmbeddingProviderType.OPENAI_COMPATIBLE,
                EmbeddingProviderType.OPENROUTER,
                EmbeddingProviderType.CUSTOM -> embedOpenAi(
                    inputs = batch,
                    config = effectiveConfig,
                    model = model,
                    inputType = inputType,
                    modality = "text"
                )
                EmbeddingProviderType.OFFLINE -> emptyList()
            }
        }
    }

    private fun embeddingConfig(config: AiProviderConfigEntity): AiProviderConfigEntity =
        config.copy(
            providerType = when (EmbeddingProviderType.fromString(config.embeddingProviderType)) {
                EmbeddingProviderType.GEMINI -> ProviderType.GEMINI.name
                EmbeddingProviderType.OLLAMA -> ProviderType.OLLAMA.name
                EmbeddingProviderType.OPENROUTER -> ProviderType.OPENROUTER.name
                EmbeddingProviderType.OPENAI_COMPATIBLE -> ProviderType.OPENAI_COMPATIBLE.name
                EmbeddingProviderType.CUSTOM -> ProviderType.CUSTOM.name
                EmbeddingProviderType.OFFLINE -> ProviderType.OLLAMA.name
            },
            apiKey = config.embeddingApiKey,
            baseUrl = config.embeddingBaseUrl.ifBlank {
                when (EmbeddingProviderType.fromString(config.embeddingProviderType)) {
                    EmbeddingProviderType.GEMINI -> EmbeddingProviderType.GEMINI.defaultBaseUrl
                    EmbeddingProviderType.OLLAMA -> EmbeddingProviderType.OLLAMA.defaultBaseUrl
                    EmbeddingProviderType.OPENROUTER -> EmbeddingProviderType.OPENROUTER.defaultBaseUrl
                    EmbeddingProviderType.OPENAI_COMPATIBLE -> EmbeddingProviderType.OPENAI_COMPATIBLE.defaultBaseUrl
                    EmbeddingProviderType.CUSTOM,
                    EmbeddingProviderType.OFFLINE -> ""
                }
            }
        )

    private fun requiresNvidiaEmbeddingParams(model: String): Boolean {
        val id = model.lowercase()
        return id.startsWith("nvidia/") && id.contains("embed")
    }

    private fun embedGemini(texts: List<String>, config: AiProviderConfigEntity): List<FloatArray> {
        val baseUrl = validateEndpoint(config.baseUrl.trimEnd('/').ifBlank { "https://generativelanguage.googleapis.com" }, ProviderType.GEMINI).toString()
        val model = config.embeddingModel.trim().removePrefix("models/")
        val endpoint = if (baseUrl.endsWith("/v1beta") || baseUrl.endsWith("/v1")) baseUrl else "$baseUrl/v1beta"
        val url = "$endpoint/models/$model:batchEmbedContents"
        val requests = JSONArray()
        texts.forEach { value ->
            requests.put(
                JSONObject()
                    .put("model", "models/$model")
                    .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", value.take(8000)))))
            )
        }
        val request = Request.Builder().url(url)
            .addHeader("x-goog-api-key", config.apiKey.trim())
            .post(JSONObject().put("requests", requests).toString().toRequestBody(jsonMediaType))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw safeHttpError("Gemini embedding", response.code)
            val embeddings = JSONObject(response.body?.string().orEmpty()).optJSONArray("embeddings") ?: throw RuntimeException("Gemini embedding response missing embeddings")
            if (embeddings.length() != texts.size) throw RuntimeException("Gemini embedding response count ${embeddings.length()} != request count ${texts.size}")
            return (0 until embeddings.length()).map { i ->
                val values = embeddings.optJSONObject(i)?.optJSONArray("values")
                    ?: throw RuntimeException("Gemini embedding item $i is missing values")
                FloatArray(values.length()) { idx -> values.optDouble(idx, 0.0).toFloat() }
            }
        }
    }

    private fun embedOllama(texts: List<String>, config: AiProviderConfigEntity): List<FloatArray> {
        val base = validateEndpoint(config.baseUrl.trimEnd('/').removeSuffix("/v1"), ProviderType.OLLAMA).toString()
        val url = "$base/api/embed"
        val root = JSONObject()
            .put("model", config.embeddingModel)
            .put("input", JSONArray().apply { texts.forEach { put(it.take(8000)) } })
        val requestBuilder = Request.Builder().url(url)
            .post(root.toString().toRequestBody(jsonMediaType))
        applyCustomHeaders(requestBuilder, config)
        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw safeHttpError("Ollama embedding", response.code)
            val body = JSONObject(response.body?.string().orEmpty())
            val array = body.optJSONArray("embeddings") ?: throw RuntimeException("Ollama embedding response missing embeddings")
            if (array.length() != texts.size) throw RuntimeException("Ollama embedding response count ${array.length()} != request count ${texts.size}")
            return (0 until array.length()).map { i ->
                val values = array.optJSONArray(i)
                    ?: throw RuntimeException("Ollama embedding item $i is missing values")
                FloatArray(values.length()) { idx -> values.optDouble(idx, 0.0).toFloat() }
            }
        }
    }

    private fun embedOpenAi(
        inputs: List<String>,
        config: AiProviderConfigEntity,
        model: String,
        inputType: String? = null,
        modality: String? = null
    ): List<FloatArray> {
        val rawBase = config.baseUrl.trimEnd('/').ifBlank { "https://api.openai.com/v1" }
        val validatedBase = validateEndpoint(rawBase, ProviderType.fromString(config.providerType)).toString()
        val url = if (validatedBase.endsWith("/embeddings")) validatedBase else "\${validatedBase}/embeddings"
        val root = JSONObject()
            .put("model", model)
            .put("input", JSONArray().apply { inputs.forEach { put(it.take(120000)) } })
            .put("encoding_format", "float")
        if (requiresNvidiaEmbeddingParams(model)) {
            inputType?.let { root.put("input_type", it) }
            modality?.let { root.put("modality", it) }
            root.put("truncate", "END")
        }
        val builder = Request.Builder().url(url).post(root.toString().toRequestBody(jsonMediaType))
        config.apiKey.trim().takeIf { it.isNotBlank() }?.let { builder.addHeader("Authorization", "Bearer $it") }
        applyCustomHeaders(builder, config)
        okHttpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw safeHttpError("Embedding", response.code)
            val data = JSONObject(response.body?.string().orEmpty()).optJSONArray("data")
                ?: throw RuntimeException("Embedding response missing data")
            if (data.length() != inputs.size) {
                throw RuntimeException("Embedding response count ${data.length()} != request count ${inputs.size}")
            }
            return (0 until data.length()).map { i ->
                val item = data.optJSONObject(i) ?: throw RuntimeException("Embedding item $i is malformed")
                val index = item.optInt("index", i)
                val values = item.optJSONArray("embedding")
                    ?: throw RuntimeException("Embedding item $i is missing embedding values")
                index to FloatArray(values.length()) { idx -> values.optDouble(idx, 0.0).toFloat() }
            }.sortedBy { it.first }.map { it.second }
        }
    }
    suspend fun listModels(config: AiProviderConfigEntity): List<AvailableAiModel> = withContext(Dispatchers.IO) {
        try {
            when (ProviderType.fromString(config.providerType)) {
                ProviderType.GEMINI -> listGeminiModels(config)
                ProviderType.OLLAMA -> listOllamaModels(config)
                else -> listOpenAiModels(config)
            }
        } catch (error: Exception) {
            Log.w("AiProviderClient", "Model discovery failed: " + error.javaClass.simpleName)
            throw error
        }
    }

    private fun listGeminiModels(config: AiProviderConfigEntity): List<AvailableAiModel> {
        val base = validateEndpoint(config.baseUrl.trimEnd('/').ifBlank { "https://generativelanguage.googleapis.com" }, ProviderType.GEMINI).toString().trimEnd('/')
        val endpoint = if (base.endsWith("/v1beta") || base.endsWith("/v1")) base else "$base/v1beta"
        val request = Request.Builder().url("$endpoint/models").addHeader("x-goog-api-key", config.apiKey.trim()).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw safeHttpError("Gemini models", response.code)
            val models = JSONObject(response.body?.string().orEmpty()).optJSONArray("models") ?: return emptyList()
            return (0 until models.length()).mapNotNull { i ->
                val item = models.optJSONObject(i) ?: return@mapNotNull null
                val name = item.optString("name").removePrefix("models/")
                val methods = item.optJSONArray("supportedGenerationMethods")
                val generation = methods != null && (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                if (!generation && !name.contains("embedding", true)) return@mapNotNull null
                AvailableAiModel(
                    id = name,
                    supportsChat = generation,
                    supportsVision = generation && !name.contains("live", true) && !name.contains("tts", true) && !name.contains("transcribe", true),
                    supportsEmbedding = name.contains("embedding", true)
                )
            }.sortedBy { it.id }
        }
    }

    private fun listOllamaModels(config: AiProviderConfigEntity): List<AvailableAiModel> {
        val base = validateEndpoint(config.baseUrl.trimEnd('/').removeSuffix("/v1"), ProviderType.OLLAMA).toString().trimEnd('/')
        val request = Request.Builder().url("$base/api/tags").get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw safeHttpError("Ollama models", response.code)
            val models = JSONObject(response.body?.string().orEmpty()).optJSONArray("models") ?: return emptyList()
            return (0 until models.length()).mapNotNull { i ->
                val item = models.optJSONObject(i) ?: return@mapNotNull null
                val name = item.optString("name").ifBlank { item.optString("model") }
                if (name.isBlank()) return@mapNotNull null
                val details = item.optJSONObject("details")
                val families = details?.optJSONArray("families")
                val hasClipFamily = families != null && (0 until families.length()).any { families.optString(it).contains("clip", true) }
                val embedding = name.contains("embed", true) || name.contains("bge", true) || name.contains("e5", true)
                AvailableAiModel(
                    id = name,
                    supportsChat = !embedding,
                    supportsVision = !embedding && (hasClipFamily || name.contains("vision", true) || name.contains("gemma3", true)),
                    supportsEmbedding = embedding,
                    isFree = true,
                    priceKnown = true
                )
            }.sortedBy { it.id }
        }
    }

    private fun listOpenAiModels(config: AiProviderConfigEntity): List<AvailableAiModel> {
        val raw = config.baseUrl.trimEnd('/').ifBlank { "https://api.openai.com/v1" }
        val normalizedRaw = raw.removeSuffix("/chat/completions")
        val modelsUrl = normalizedRaw.let { if (it.endsWith("/models")) it else "$it/models" }
        val base = validateEndpoint(modelsUrl, ProviderType.fromString(config.providerType))
        val builder = Request.Builder().url(base).get()
        config.apiKey.trim().takeIf { it.isNotBlank() }?.let { builder.addHeader("Authorization", "Bearer $it") }
        applyCustomHeaders(builder, config)
        okHttpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw safeHttpError("Models", response.code)
            val data = JSONObject(response.body?.string().orEmpty()).optJSONArray("data") ?: return emptyList()
            return (0 until data.length()).mapNotNull { i ->
                val item = data.optJSONObject(i) ?: return@mapNotNull null
                val id = item.optString("id").trim()
                if (id.isBlank()) return@mapNotNull null
                val architecture = item.optJSONObject("architecture")
                val inputs = architecture?.optJSONArray("input_modalities")
                val hasImage = inputs != null && (0 until inputs.length()).any { inputs.optString(it).equals("image", true) }
                val embedding = id.contains("embedding", true) || id.contains("embed", true)
                val multimodalEmbedding = embedding && id.contains("embed-vl", true)
                val lowerId = id.lowercase()
                val nonChat = listOf("embedding", "embed-", "rerank", "moderation", "transcri", "whisper", "tts", "speech", "image-generation", "text-to-image")
                    .any { lowerId.contains(it) }
                val pricing = item.optJSONObject("pricing")
                val inputPrice = pricing?.optString("prompt")?.toDoubleOrNull()
                val outputPrice = pricing?.optString("completion")?.toDoubleOrNull()
                val priceKnown = inputPrice != null && outputPrice != null
                val nvidiaHostedFree = raw.contains("integrate.api.nvidia.com", ignoreCase = true) &&
                    id.lowercase() in setOf(
                        "nvidia/nemotron-3-super-120b-a12b",
                        "meta/llama-3.2-11b-vision-instruct",
                        "nvidia/llama-nemotron-embed-vl-1b-v2",
                        "nvidia/nv-embedqa-e5-v5"
                    )
                val free = nvidiaHostedFree || (priceKnown && inputPrice <= 0.0 && outputPrice <= 0.0)
                AvailableAiModel(
                    id = id,
                    supportsChat = !embedding && !nonChat,
                    supportsVision = !embedding && !nonChat && (hasImage || id.contains("vision", true) || id.contains("vl", true)),
                    supportsEmbedding = embedding,
                    supportsMultimodalEmbedding = multimodalEmbedding,
                    isFree = free,
                    priceKnown = priceKnown
                )
            }.sortedBy { it.id }
        }
    }
    private fun applyCustomHeaders(builder: Request.Builder, config: AiProviderConfigEntity) {
        try {
            val headers = JSONObject(config.customHeadersJson.ifBlank { "{}" })
            headers.keys().forEach { key -> builder.addHeader(key, headers.optString(key)) }
        } catch (_: Exception) { }
    }

    suspend fun analyzeImage(
        base64Jpeg: String?,
        ocrText: String,
        metadataSummary: String,
        fileName: String,
        config: AiProviderConfigEntity
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val prompt = """
            You are the Vision Language Model enrichment stage of Brain.
            Use the supplied image as the visual source of truth.
            Local OCR was extracted on-device and ExifTool/media metadata was extracted locally; use both as supporting evidence.
            Correct obvious OCR errors only when the image supports the correction. Never invent unsupported facts.
            Build a reusable image profile for semantic search and clustering.
            Return ONLY valid JSON:
            {"summary":"1-3 sentence visual description","entities":[{"name":"...","type":"PERSON|LOCATION|ORGANIZATION|EVENT|OBJECT|TOPIC"}],"relations":[{"source":"$fileName","relation":"DEPICTS|LOCATED_AT|CONTAINS_TEXT|ASSOCIATED_WITH","target":"...","evidence":"..."}],"tags":["tag1","tag2","tag3"]}
        """.trimIndent()

        val userPrompt = buildString {
            append("File name: ").append(fileName).append("\n\n")
            append("Local OCR (on-device):\n").append(ocrText.take(20_000).ifBlank { "<none>" }).append("\n\n")
            append("ExifTool / media metadata:\n").append(metadataSummary.ifBlank { "<none>" }).append("\n\n")
            append("Validate and enrich these signals against the image.")
        }

        try {
            val responseText = if (base64Jpeg.isNullOrBlank()) {
                executePrompt("$prompt\n\n$userPrompt", config)
            } else {
                executeMultimodalPrompt("$prompt\n\n$userPrompt", base64Jpeg, config)
            }
            parseAnalysisJson(responseText, fileName)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("AiProviderClient", "Image analysis failed: " + e.javaClass.simpleName)
            fallbackImageAnalysis(fileName, metadataSummary + if (ocrText.isBlank()) "" else "\nOCR: " + ocrText.take(1200))
        }
    }

    suspend fun generateRagAnswer(
        question: String,
        contextText: String,
        graphContext: String,
        chatHistory: List<Pair<String, String>> = emptyList(),
        config: AiProviderConfigEntity
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are an intelligent knowledge assistant connected to the user's private documents and photos.
            Answer the user's question using the retrieved document excerpts and knowledge graph connections below.
            
            === KNOWLEDGE GRAPH CONNECTIONS (CONNECTED DOTS) ===
            $graphContext
            
            === RETRIEVED DOCUMENT & IMAGE EXCERPTS ===
            $contextText
            
            ${if (chatHistory.isNotEmpty()) {
                "=== RECENT CONVERSATION TURNS ===\n" +
                    chatHistory.takeLast(6).joinToString("\n\n") { (userQ, aiA) ->
                        "User: $userQ\nAssistant: $aiA"
                    } + "\n"
            } else ""}
            === USER QUESTION ===
            $question
            
            Instructions:
            - Provide a comprehensive, clear, and direct answer.
            - Explicitly connect the dots between documents and images where relevant (e.g. "Document X mentions topic Y, which is depicted in photo Z").
            - Reference specific file names so the user knows where the information comes from.
            - If information is not available in the excerpts, clearly state what is missing.
        """.trimIndent()

        executePrompt(prompt = prompt, config = config)
    }

    suspend fun chatAboutFile(
        question: String,
        fileName: String,
        fileContent: String?,
        base64Jpeg: String?,
        metadataSummary: String?,
        chatHistory: List<Pair<String, String>> = emptyList(),
        config: AiProviderConfigEntity
    ): String = withContext(Dispatchers.IO) {
        val historyText = if (chatHistory.isNotEmpty()) {
            buildString {
                append("=== RECENT CONVERSATION TURNS ===\n")
                chatHistory.takeLast(6).forEach { (userQ, aiA) ->
                    append("User: $userQ\nAssistant: $aiA\n\n")
                }
            }
        } else ""

        val prompt = buildString {
            append("You are an intelligent, helpful AI file assistant in a personal file explorer app.\n")
            append("CRITICAL: The user has attached ONLY this specific file: \"$fileName\".\n")
            append("You must focus exclusively on this attached file. Answer questions, describe its contents, explain details, summarize, and converse about it directly.\n\n")
            if (!metadataSummary.isNullOrBlank()) {
                append("=== ATTACHED FILE METADATA ===\n")
                append(metadataSummary)
                append("\n\n")
            }
            if (!fileContent.isNullOrBlank()) {
                append("=== ATTACHED FILE CONTENT ($fileName) ===\n")
                append(fileContent.take(65000))
                append("\n=== END OF ATTACHED FILE CONTENT ===\n\n")
            }
            if (historyText.isNotBlank()) {
                append(historyText)
                append("\n")
            }
            append("=== USER QUESTION ===\n")
            append(question)
        }.trimIndent()

        if (base64Jpeg != null && base64Jpeg.isNotBlank()) {
            executeMultimodalPrompt(prompt, base64Jpeg, config)
        } else {
            executePrompt(prompt, config)
        }
    }

    suspend fun chatGeneral(
        question: String,
        knowledgeContext: String? = null,
        chatHistory: List<Pair<String, String>> = emptyList(),
        config: AiProviderConfigEntity
    ): String = withContext(Dispatchers.IO) {
        val historyText = if (chatHistory.isNotEmpty()) {
            buildString {
                append("=== RECENT CONVERSATION TURNS ===\n")
                chatHistory.takeLast(6).forEach { (userQ, aiA) ->
                    append("User: $userQ\nAssistant: $aiA\n\n")
                }
            }
        } else ""

        val prompt = buildString {
            append("You are a helpful AI assistant in a local file explorer and media app.\n")
            if (!knowledgeContext.isNullOrBlank()) {
                append("=== RETRIEVED RELEVANT FILE CONTEXT ===\n")
                append(knowledgeContext)
                append("\n\n")
            }
            if (historyText.isNotBlank()) {
                append(historyText)
                append("\n")
            }
            append("=== USER QUESTION ===\n")
            append(question)
            append("\n\nProvide a clear, helpful, and direct answer.")
        }.trimIndent()

        executePrompt(prompt, config)
    }

    private suspend fun executePrompt(
        prompt: String,
        config: AiProviderConfigEntity,
        isTest: Boolean = false
    ): String {
        val provider = ProviderType.fromString(config.providerType)
        return when (provider) {
            ProviderType.GEMINI -> executeGemini(prompt, null, config, config.chatModel)
            else -> executeOpenAi(prompt, null, config, config.chatModel)
        }
    }

    private suspend fun executeMultimodalPrompt(
        prompt: String,
        base64Jpeg: String,
        config: AiProviderConfigEntity
    ): String {
        val provider = ProviderType.fromString(config.providerType)
        val model = config.visionModel.ifBlank { config.chatModel }
        return when (provider) {
            ProviderType.GEMINI -> executeGemini(prompt, base64Jpeg, config, model)
            else -> executeOpenAi(prompt, base64Jpeg, config, model)
        }
    }

    private fun executeGemini(
        prompt: String,
        base64Jpeg: String?,
        config: AiProviderConfigEntity,
        modelOverride: String = ""
    ): String {
        val rawBase = config.baseUrl.trimEnd('/').ifBlank { "https://generativelanguage.googleapis.com" }
        val validatedBase = validateEndpoint(rawBase, ProviderType.GEMINI).toString()
        val baseUrl = validateEndpoint(rawBase, ProviderType.GEMINI).toString().removeSuffix("/v1beta").removeSuffix("/v1")
        val model = modelOverride.ifBlank { config.chatModel.ifBlank { "gemini-3.8-flash" } }.removePrefix("models/")
        val apiKey = config.apiKey.trim()

        val url = "$baseUrl/v1beta/models/$model:generateContent"

        val partsArray = JSONArray()
        partsArray.put(JSONObject().put("text", prompt))

        if (base64Jpeg != null) {
            val inlineData = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Jpeg)
            }
            partsArray.put(JSONObject().put("inlineData", inlineData))
        }

        val contentsArray = JSONArray().put(JSONObject().put("parts", partsArray))
        val root = JSONObject().apply {
            put("contents", contentsArray)
            if (!model.startsWith("gemini-3.", ignoreCase = true)) {
                put("generationConfig", JSONObject().put("temperature", config.temperature.toDouble()))
            }
        }

        val requestBody = root.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw safeHttpError("Gemini API", response.code)
            }
            val responseBody = response.body?.string().orEmpty()
            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            if (parts.length() == 0) return ""
            return parts.getJSONObject(0).optString("text", "")
        }
    }

    private fun executeOpenAi(
        prompt: String,
        base64Jpeg: String?,
        config: AiProviderConfigEntity,
        modelOverride: String = ""
    ): String {
        val rawBase = if (config.baseUrl.isNotBlank()) config.baseUrl.trimEnd('/') else "https://api.openai.com/v1"
        val baseUrl = validateEndpoint(rawBase, ProviderType.fromString(config.providerType)).toString().trimEnd('/')
        val url = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        val model = modelOverride.ifBlank { config.chatModel.ifBlank { "gpt-4o-mini" } }
        val apiKey = config.apiKey.trim()

        val messagesArray = JSONArray()
        val userMessage = JSONObject().put("role", "user")

        if (base64Jpeg != null) {
            val contentList = JSONArray()
            contentList.put(JSONObject().put("type", "text").put("text", prompt))
            val imageUrlObj = JSONObject().put("url", "data:image/jpeg;base64,$base64Jpeg")
            contentList.put(JSONObject().put("type", "image_url").put("image_url", imageUrlObj))
            userMessage.put("content", contentList)
        } else {
            userMessage.put("content", prompt)
        }
        messagesArray.put(userMessage)

        val root = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", config.temperature.toDouble())
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(root.toString().toRequestBody(jsonMediaType))

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        // Apply any user custom headers if configured
        try {
            if (config.customHeadersJson.isNotBlank() && config.customHeadersJson != "{}") {
                val headersJson = JSONObject(config.customHeadersJson)
                headersJson.keys().forEach { key ->
                    requestBuilder.addHeader(key, headersJson.optString(key))
                }
            }
        } catch (_: Exception) {}

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw safeHttpError("OpenAI-compatible API", response.code)
            }
            val responseBody = response.body?.string().orEmpty()
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message") ?: return ""
            return message.optString("content", "")
        }
    }

    private fun parseAnalysisJson(rawText: String, fileName: String): AnalysisResult {
        val cleanJson = extractJsonSubstring(rawText)
        if (cleanJson.isBlank()) {
            return AnalysisResult(
                summary = rawText.take(250).ifBlank { "Analysis of $fileName" },
                tags = listOf("File", fileName.substringAfterLast('.', "general"))
            )
        }

        return try {
            val json = JSONObject(cleanJson)
            val summary = json.optString("summary", "Analysis of $fileName")

            val entitiesList = mutableListOf<ExtractedEntity>()
            val entitiesArray = json.optJSONArray("entities")
            if (entitiesArray != null) {
                for (i in 0 until entitiesArray.length()) {
                    val item = entitiesArray.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    val type = item.optString("type", "TOPIC").uppercase()
                    if (name.isNotBlank()) {
                        entitiesList.add(ExtractedEntity(name = name, type = type))
                    }
                }
            }

            val relationsList = mutableListOf<ExtractedRelation>()
            val relationsArray = json.optJSONArray("relations")
            if (relationsArray != null) {
                for (i in 0 until relationsArray.length()) {
                    val item = relationsArray.optJSONObject(i) ?: continue
                    val source = item.optString("source").trim()
                    val relation = item.optString("relation", "MENTIONS").uppercase()
                    val target = item.optString("target").trim()
                    val evidence = item.optString("evidence", "")
                    if (source.isNotBlank() && target.isNotBlank()) {
                        relationsList.add(
                            ExtractedRelation(
                                source = source,
                                relation = relation,
                                target = target,
                                evidence = evidence
                            )
                        )
                    }
                }
            }

            val tagsList = mutableListOf<String>()
            val tagsArray = json.optJSONArray("tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    val tag = tagsArray.optString(i).trim()
                    if (tag.isNotBlank()) tagsList.add(tag)
                }
            }

            AnalysisResult(
                summary = summary,
                entities = entitiesList,
                relations = relationsList,
                tags = tagsList
            )
        } catch (e: Exception) {
            Log.w("AiProviderClient", "JSON parse error, using fallback: ${e.message}")
            AnalysisResult(
                summary = rawText.take(250),
                tags = listOf("Extracted", fileName.substringAfterLast('.', "doc"))
            )
        }
    }

    private fun extractJsonSubstring(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1)
        }
        return ""
    }

    private fun fallbackAnalysis(fileName: String, text: String): AnalysisResult {
        val words = text.split("\\s+".toRegex()).filter { it.length > 4 }.distinct().take(5)
        return AnalysisResult(
            summary = "Document $fileName containing ${text.length} characters.",
            entities = words.map { ExtractedEntity(name = it, type = "TOPIC") },
            relations = words.map {
                ExtractedRelation(source = fileName, relation = "MENTIONS", target = it)
            },
            tags = listOf(fileName.substringAfterLast('.', "document")) + words.take(3)
        )
    }

    private fun fallbackImageAnalysis(fileName: String, metadataSummary: String): AnalysisResult {
        return AnalysisResult(
            summary = "Image $fileName with EXIF/IPTC metadata: $metadataSummary",
            entities = listOf(
                ExtractedEntity(name = fileName, type = "IMAGE"),
                ExtractedEntity(name = "Photograph", type = "TOPIC")
            ),
            relations = listOf(
                ExtractedRelation(source = fileName, relation = "TYPE_OF", target = "Photograph")
            ),
            tags = listOf("Photo", "Media", fileName.substringAfterLast('.', "jpg"))
        )
    }
}
