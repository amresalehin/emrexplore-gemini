package com.example.data.brain

import com.example.data.ai.VectorDatabaseType
import com.example.data.local.AiProviderConfigEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class BrainVectorKind { TEXT, IMAGE }

data class VectorSearchResult(val id: String, val score: Float)

interface BrainVectorStore {
    suspend fun upsert(chunks: List<BrainChunkEntity>, config: AiProviderConfigEntity)
    suspend fun delete(ids: List<String>, config: AiProviderConfigEntity)
    suspend fun search(vector: FloatArray, model: String, limit: Int, config: AiProviderConfigEntity, kind: BrainVectorKind = BrainVectorKind.TEXT): List<VectorSearchResult>
    suspend fun clear(config: AiProviderConfigEntity)
}

class RoomBrainVectorStore(private val chunkDao: BrainChunkDao) : BrainVectorStore {
    override suspend fun upsert(chunks: List<BrainChunkEntity>, config: AiProviderConfigEntity) = Unit
    override suspend fun delete(ids: List<String>, config: AiProviderConfigEntity) = Unit
    override suspend fun clear(config: AiProviderConfigEntity) = Unit

    override suspend fun search(vector: FloatArray, model: String, limit: Int, config: AiProviderConfigEntity, kind: BrainVectorKind): List<VectorSearchResult> {
        val queue = java.util.PriorityQueue<VectorSearchResult>(limit.coerceAtLeast(1)) { a, b -> a.score.compareTo(b.score) }
        var offset = 0
        while (true) {
            val page = when (kind) {
                BrainVectorKind.TEXT -> chunkDao.getEmbeddedPage(model, 128, offset)
                BrainVectorKind.IMAGE -> chunkDao.getImageEmbeddedPage(model, 128, offset)
            }
            if (page.isEmpty()) break
            for (chunk in page) {
                val stored = BrainVectorCodec.fromJson(if (kind == BrainVectorKind.TEXT) chunk.embeddingJson else chunk.imageEmbeddingJson)
                if (stored.size != vector.size || stored.isEmpty()) continue
                val score = BrainVectorCodec.cosine(vector, stored)
                if (score >= 0.20f) {
                    if (queue.size < limit) queue.offer(VectorSearchResult(chunk.id, score))
                    else if (queue.peek().score < score) { queue.poll(); queue.offer(VectorSearchResult(chunk.id, score)) }
                }
            }
            if (page.size < 128) break
            offset += page.size
            if (offset >= 20_000) break
        }
        return buildList { while (queue.isNotEmpty()) add(queue.poll()) }.asReversed()
    }
}

class QdrantBrainVectorStore : BrainVectorStore {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()

    override suspend fun upsert(chunks: List<BrainChunkEntity>, config: AiProviderConfigEntity) {
        if (chunks.isEmpty()) return
        ensureCollection(chunks.first().embeddingJson, config)
        val points = JSONArray()
        chunks.forEach { chunk ->
            val vector = BrainVectorCodec.fromJson(chunk.embeddingJson)
            if (vector.isEmpty()) return@forEach
            points.put(JSONObject().put("id", stablePointId(chunk.id)).put("vector", JSONArray(vector.toList()))
                .put("payload", JSONObject().put("chunkId", chunk.id).put("filePath", chunk.filePath).put("embeddingModel", chunk.embeddingModel)))
        }
        request("PUT", endpoint(config, "/points?wait=true"), JSONObject().put("points", points), config)
    }

    override suspend fun delete(ids: List<String>, config: AiProviderConfigEntity) {
        if (ids.isEmpty()) return
        val points = JSONArray()
        ids.forEach { points.put(stablePointId(it)) }
        request("POST", endpoint(config, "/points/delete?wait=true"), JSONObject().put("points", points), config)
    }

    override suspend fun search(vector: FloatArray, model: String, limit: Int, config: AiProviderConfigEntity, kind: BrainVectorKind): List<VectorSearchResult> {
        if (vector.isEmpty() || limit <= 0) return emptyList()
        val json = request("POST", endpoint(config, "/points/search"),
            JSONObject().put("vector", JSONArray(vector.toList())).put("limit", limit).put("with_payload", true), config)
        val result = json.optJSONArray("result") ?: return emptyList()
        return buildList {
            for (i in 0 until result.length()) {
                val item = result.optJSONObject(i) ?: continue
                val payload = item.optJSONObject("payload") ?: continue
                if (payload.optString("embeddingModel") != model) continue
                val id = payload.optString("chunkId")
                if (id.isNotBlank()) add(VectorSearchResult(id, item.optDouble("score", 0.0).toFloat()))
            }
        }
    }

    override suspend fun clear(config: AiProviderConfigEntity) = Unit

    private fun ensureCollection(sampleEmbeddingJson: String, config: AiProviderConfigEntity) {
        val vector = BrainVectorCodec.fromJson(sampleEmbeddingJson)
        if (vector.isEmpty()) return
        val url = endpoint(config, "")
        val request = Request.Builder().url(url)
            .put(JSONObject().put("vectors", JSONObject().put("size", vector.size).put("distance", "Cosine")).toString().toRequestBody(jsonType))
            .applyHeaders(config).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) return
            if (response.code != 409) throw IllegalStateException("Qdrant collection setup failed (${response.code})")
        }
        val existing = Request.Builder().url(url).get().applyHeaders(config).build()
        client.newCall(existing).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val currentSize = runCatching {
                JSONObject(body).getJSONObject("result").getJSONObject("config")
                    .getJSONObject("params").getJSONObject("vectors").getInt("size")
            }.getOrNull()
            if (currentSize != null && currentSize != vector.size) {
                client.newCall(Request.Builder().url(url).delete().applyHeaders(config).build()).execute().use { deleted ->
                    if (!deleted.isSuccessful && deleted.code != 404) throw IllegalStateException("Could not reset Qdrant collection")
                }
                client.newCall(Request.Builder().url(url)
                    .put(JSONObject().put("vectors", JSONObject().put("size", vector.size).put("distance", "Cosine")).toString().toRequestBody(jsonType))
                    .applyHeaders(config).build()).execute().use { created ->
                    if (!created.isSuccessful) throw IllegalStateException("Could not recreate Qdrant collection")
                }
            }
        }
    }

    private fun request(method: String, url: String, body: JSONObject, config: AiProviderConfigEntity): JSONObject {
        val requestBuilder = Request.Builder().url(url).applyHeaders(config)
        val request = when (method) {
            "PUT" -> requestBuilder.put(body.toString().toRequestBody(jsonType)).build()
            else -> requestBuilder.post(body.toString().toRequestBody(jsonType)).build()
        }
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Vector database request failed (${response.code}): ${text.take(300)}")
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun Request.Builder.applyHeaders(config: AiProviderConfigEntity): Request.Builder {
        if (config.vectorDatabaseApiKey.isNotBlank()) header("api-key", config.vectorDatabaseApiKey.trim())
        return header("Accept", "application/json")
    }

    private fun endpoint(config: AiProviderConfigEntity, suffix: String): String {
        val base = config.vectorDatabaseBaseUrl.trim().trimEnd('/')
        require(base.isNotBlank()) { "Qdrant URL is required" }
        val collection = config.vectorDatabaseCollection.trim().ifBlank { "emrexplore_brain" }
        return "$base/collections/$collection$suffix"
    }

    private fun stablePointId(id: String): String = java.util.UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8)).toString()
}

fun vectorStoreFor(config: AiProviderConfigEntity, chunkDao: BrainChunkDao): BrainVectorStore =
    when (VectorDatabaseType.fromString(config.vectorDatabaseType)) {
        VectorDatabaseType.QDRANT -> QdrantBrainVectorStore()
        VectorDatabaseType.ROOM -> RoomBrainVectorStore(chunkDao)
    }
