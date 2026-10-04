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
import java.util.concurrent.TimeUnit

class AiProviderClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(config: AiProviderConfigEntity): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val prompt = "Reply with 'OK' if you can read this."
            val response = executePrompt(
                prompt = prompt,
                config = config,
                isTest = true
            )
            val duration = System.currentTimeMillis() - startTime
            if (response.isNotBlank()) {
                ConnectionTestResult(
                    success = true,
                    message = "Connected successfully! Response: ${response.take(60)}",
                    responseTimeMs = duration
                )
            } else {
                ConnectionTestResult(
                    success = false,
                    message = "Received empty response from server",
                    responseTimeMs = duration
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e("AiProviderClient", "Connection test failed", e)
            ConnectionTestResult(
                success = false,
                message = e.localizedMessage ?: e.message ?: "Connection failed",
                responseTimeMs = duration
            )
        }
    }

    suspend fun analyzeDocument(
        text: String,
        fileName: String,
        config: AiProviderConfigEntity
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are a Knowledge Graph and document analysis engine.
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
            Log.e("AiProviderClient", "Document analysis failed: ${e.message}")
            fallbackAnalysis(fileName, text)
        }
    }


    suspend fun embedTexts(texts: List<String>, config: AiProviderConfigEntity): List<FloatArray> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty() || config.embeddingModel.isBlank()) return@withContext emptyList()

            // Bound request size so long documents do not create a large JSON body or
            // a large simultaneous provider response on memory-constrained devices.
            val batches = texts.chunked(16)
            try {
                batches.flatMap { batch ->
                    when (ProviderType.fromString(config.providerType)) {
                        ProviderType.GEMINI -> embedGemini(batch, config)
                        ProviderType.OLLAMA -> embedOllama(batch, config)
                        else -> embedOpenAi(batch, config)
                    }
                }
            } catch (error: Exception) {
                Log.w("AiProviderClient", "Embedding request failed: ${error.message}")
                throw error
            }
        }

    private fun embedGemini(texts: List<String>, config: AiProviderConfigEntity): List<FloatArray> {
        val baseUrl = config.baseUrl.trimEnd('/').ifBlank { "https://generativelanguage.googleapis.com" }
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
            if (!response.isSuccessful) throw RuntimeException("Gemini embedding HTTP ${response.code}: ${response.body?.string().orEmpty()}")
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
        val base = config.baseUrl.trimEnd('/').removeSuffix("/v1")
        val url = "$base/api/embed"
        val root = JSONObject()
            .put("model", config.embeddingModel)
            .put("input", JSONArray().apply { texts.forEach { put(it.take(8000)) } })
        val requestBuilder = Request.Builder().url(url)
            .post(root.toString().toRequestBody(jsonMediaType))
        applyCustomHeaders(requestBuilder, config)
        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("Ollama embedding HTTP ${response.code}: ${response.body?.string().orEmpty()}")
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

    private fun embedOpenAi(texts: List<String>, config: AiProviderConfigEntity): List<FloatArray> {
        val rawBase = config.baseUrl.trimEnd('/').ifBlank { "https://api.openai.com/v1" }
        val url = if (rawBase.endsWith("/embeddings")) rawBase else "$rawBase/embeddings"
        val root = JSONObject()
            .put("model", config.embeddingModel)
            .put("input", JSONArray().apply { texts.forEach { put(it.take(32000)) } })
        val builder = Request.Builder().url(url).post(root.toString().toRequestBody(jsonMediaType))
        config.apiKey.trim().takeIf { it.isNotBlank() }?.let { builder.addHeader("Authorization", "Bearer $it") }
        applyCustomHeaders(builder, config)
        okHttpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("Embedding HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            val data = JSONObject(response.body?.string().orEmpty()).optJSONArray("data") ?: throw RuntimeException("Embedding response missing data")
            if (data.length() != texts.size) throw RuntimeException("Embedding response count ${data.length()} != request count ${texts.size}")
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
            Log.w("AiProviderClient", "Model discovery failed: ${error.message}")
            throw error
        }
    }

    private fun listGeminiModels(config: AiProviderConfigEntity): List<AvailableAiModel> {
        val base = config.baseUrl.trimEnd('/').ifBlank { "https://generativelanguage.googleapis.com" }
        val endpoint = if (base.endsWith("/v1beta") || base.endsWith("/v1")) base else "$base/v1beta"
        val request = Request.Builder().url("$endpoint/models").addHeader("x-goog-api-key", config.apiKey.trim()).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("Gemini models HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            val models = JSONObject(response.body?.string().orEmpty()).optJSONArray("models") ?: return emptyList()
            return (0 until models.length()).mapNotNull { i ->
                val item = models.optJSONObject(i) ?: return@mapNotNull null
                val name = item.optString("name").removePrefix("models/")
                val methods = item.optJSONArray("supportedGenerationMethods")
                val generation = methods != null && (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                if (!generation && !name.contains("embedding", true)) return@mapNotNull null
                AvailableAiModel(
                    id = name,
                    supportsVision = generation && !name.contains("live", true) && !name.contains("tts", true) && !name.contains("transcribe", true),
                    supportsEmbedding = name.contains("embedding", true)
                )
            }.sortedBy { it.id }
        }
    }

    private fun listOllamaModels(config: AiProviderConfigEntity): List<AvailableAiModel> {
        val base = config.baseUrl.trimEnd('/').removeSuffix("/v1")
        val request = Request.Builder().url("$base/api/tags").get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("Ollama models HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            val models = JSONObject(response.body?.string().orEmpty()).optJSONArray("models") ?: return emptyList()
            return (0 until models.length()).mapNotNull { i ->
                val item = models.optJSONObject(i) ?: return@mapNotNull null
                val name = item.optString("name").ifBlank { item.optString("model") }
                if (name.isBlank()) return@mapNotNull null
                val details = item.optJSONObject("details")
                val families = details?.optJSONArray("families")
                val hasClipFamily = families != null && (0 until families.length()).any { families.optString(it).contains("clip", true) }
                AvailableAiModel(
                    id = name,
                    supportsVision = hasClipFamily || name.contains("vision", true) || name.contains("gemma3", true),
                    supportsEmbedding = name.contains("embed", true) || name.contains("bge", true) || name.contains("e5", true)
                )
            }.sortedBy { it.id }
        }
    }

    private fun listOpenAiModels(config: AiProviderConfigEntity): List<AvailableAiModel> {
        val base = config.baseUrl.trimEnd('/').let { if (it.endsWith("/models")) it else "$it/models" }
        val builder = Request.Builder().url(base).get()
        config.apiKey.trim().takeIf { it.isNotBlank() }?.let { builder.addHeader("Authorization", "Bearer $it") }
        applyCustomHeaders(builder, config)
        okHttpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("Models HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            val data = JSONObject(response.body?.string().orEmpty()).optJSONArray("data") ?: return emptyList()
            return (0 until data.length()).mapNotNull { i ->
                val item = data.optJSONObject(i) ?: return@mapNotNull null
                val id = item.optString("id").trim()
                if (id.isBlank()) return@mapNotNull null
                val architecture = item.optJSONObject("architecture")
                val inputs = architecture?.optJSONArray("input_modalities")
                val hasImage = inputs != null && (0 until inputs.length()).any { inputs.optString(it).equals("image", true) }
                AvailableAiModel(
                    id = id,
                    supportsVision = hasImage || id.contains("vision", true) || id.contains("vl", true),
                    supportsEmbedding = id.contains("embedding", true) || id.contains("embed", true)
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
        metadataSummary: String,
        fileName: String,
        config: AiProviderConfigEntity
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val prompt = """
            You are a Knowledge Graph and multimodal image analysis engine.
            Analyze this image named "$fileName".
            Metadata available from camera/sensors:
            $metadataSummary
            
            Extract:
            1. summary: Detailed description of what is depicted (objects, scene, activities, locations, people, visible text or diagrams).
            2. entities: Identified subjects, places, landmarks, OCR text subjects, or event types. Format: [{"name": "...", "type": "LOCATION|PERSON|TOPIC|OBJECT|EVENT"}]
            3. relations: Relationships between the image and entities. Format: [{"source": "$fileName", "relation": "DEPICTS|LOCATED_AT|CONTAINS_TEXT|ASSOCIATED_WITH", "target": "...", "evidence": "..."}]
            4. tags: 3 to 6 relevant visual/topic tags.
            
            Return ONLY a valid JSON object matching this schema without markdown:
            {
              "summary": "...",
              "entities": [{"name": "...", "type": "..."}],
              "relations": [{"source": "...", "relation": "...", "target": "...", "evidence": "..."}],
              "tags": ["tag1", "tag2"]
            }
        """.trimIndent()

        try {
            val responseText = if (base64Jpeg != null && base64Jpeg.isNotBlank()) {
                executeMultimodalPrompt(
                    prompt = prompt,
                    base64Jpeg = base64Jpeg,
                    config = config
                )
            } else {
                executePrompt(
                    prompt = prompt,
                    config = config
                )
            }
            parseAnalysisJson(responseText, fileName)
        } catch (e: Exception) {
            Log.e("AiProviderClient", "Image analysis failed: ${e.message}")
            fallbackImageAnalysis(fileName, metadataSummary)
        }
    }

    suspend fun generateRagAnswer(
        question: String,
        contextText: String,
        graphContext: String,
        config: AiProviderConfigEntity
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are an intelligent knowledge assistant connected to the user's private documents and photos.
            Answer the user's question using the retrieved document excerpts and knowledge graph connections below.
            
            === KNOWLEDGE GRAPH CONNECTIONS (CONNECTED DOTS) ===
            $graphContext
            
            === RETRIEVED DOCUMENT & IMAGE EXCERPTS ===
            $contextText
            
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
        val baseUrl = rawBase.removeSuffix("/v1beta").removeSuffix("/v1")
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
                val errorBody = response.body?.string().orEmpty()
                throw RuntimeException("Gemini API Error HTTP ${response.code}: $errorBody")
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
        val url = if (rawBase.endsWith("/chat/completions")) rawBase else "$rawBase/chat/completions"
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
                val errorBody = response.body?.string().orEmpty()
                throw RuntimeException("OpenAI API Error HTTP ${response.code}: $errorBody")
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
