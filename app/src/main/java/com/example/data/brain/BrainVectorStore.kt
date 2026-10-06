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
        var afterIndexedAt: Long? = null
        var afterId: String? = null
        while (true) {
            val page = when (kind) {
                BrainVectorKind.TEXT -> chunkDao.getEmbeddedPage(model, 128, afterIndexedAt, afterId)
                BrainVectorKind.IMAGE -> chunkDao.getImageEmbeddedPage(model, 128, afterIndexedAt, afterId)
            }
            if (page.isEmpty()) break
            for (chunk in page) {
                val stored = BrainVectorCodec.fromJson(if (kind == BrainVectorKind.TEXT) chunk.embeddingJson else chunk.imageEmbeddingJson)
                if (stored.size != vector.size || stored.isEmpty()) continue
                val score = BrainVectorCodec.cosine(vector, stored)
                if (score >= 0.20f) {
                    if (queue.size < limit) queue.offer(VectorSearchResult(chunk.id, score))
                    else if ((queue.peek()?.score ?: Float.NEGATIVE_INFINITY) < score) { queue.poll(); queue.offer(VectorSearchResult(chunk.id, score)) }
                }
            }
            if (page.size < 128) break
            val last = page.last()
            afterIndexedAt = last.indexedAt
            afterId = last.id
        }
        return buildList {
            while (queue.isNotEmpty()) {
                queue.poll()?.let(::add)
            }
        }.asReversed()
    }
}

class QdrantBrainVectorStore : BrainVectorStore {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()

    override suspend fun upsert(chunks: List<BrainChunkEntity>, config: AiProviderConfigEntity) {
        upsertKind(chunks.filter { it.embeddingJson.isNotBlank() }, config, BrainVectorKind.TEXT)
        upsertKind(chunks.filter { it.imageEmbeddingJson.isNotBlank() }, config, BrainVectorKind.IMAGE)
    }

    private fun upsertKind(chunks: List<BrainChunkEntity>, config: AiProviderConfigEntity, kind: BrainVectorKind) {
        if (chunks.isEmpty()) return
        val vectors = chunks.mapNotNull { chunk ->
            val raw = if (kind == BrainVectorKind.TEXT) chunk.embeddingJson else chunk.imageEmbeddingJson
            BrainVectorCodec.fromJson(raw).takeIf { it.isNotEmpty() }?.let { chunk to it }
        }
        if (vectors.isEmpty()) return
        ensureCollection(vectors.first().second, config, kind)
        val points = JSONArray()
        vectors.forEach { (chunk, vector) ->
            points.put(
                JSONObject().put("id", stablePointId(chunk.id, kind)).put("vector", JSONArray(vector.toList()))
                    .put("payload", JSONObject().put("chunkId", chunk.id).put("filePath", chunk.filePath)
                        .put("embeddingModel", if (kind == BrainVectorKind.TEXT) chunk.embeddingModel else chunk.imageEmbeddingModel)
                        .put("kind", kind.name))
            )
        }
        request("PUT", endpoint(config, "/points?wait=true", kind), JSONObject().put("points", points), config)
    }

    override suspend fun delete(ids: List<String>, config: AiProviderConfigEntity) {
        if (ids.isEmpty()) return
        BrainVectorKind.entries.forEach { kind ->
            val points = JSONArray()
            ids.forEach { points.put(stablePointId(it, kind)) }
            request("POST", endpoint(config, "/points/delete?wait=true", kind), JSONObject().put("points", points), config, allowMissing = true)
        }
    }

    override suspend fun search(vector: FloatArray, model: String, limit: Int, config: AiProviderConfigEntity, kind: BrainVectorKind): List<VectorSearchResult> {
        if (vector.isEmpty() || limit <= 0) return emptyList()
        val json = request("POST", endpoint(config, "/points/search", kind),
            JSONObject().put("vector", JSONArray(vector.toList())).put("limit", limit).put("with_payload", true), config, allowMissing = true)
        val result = json.optJSONArray("result") ?: return emptyList()
        return buildList {
            for (i in 0 until result.length()) {
                val item = result.optJSONObject(i) ?: continue
                val payload = item.optJSONObject("payload") ?: continue
                if (payload.optString("embeddingModel") != model || payload.optString("kind") != kind.name) continue
                val id = payload.optString("chunkId")
                if (id.isNotBlank()) add(VectorSearchResult(id, item.optDouble("score", 0.0).toFloat()))
            }
        }
    }

    override suspend fun clear(config: AiProviderConfigEntity) {
        BrainVectorKind.entries.forEach { kind ->
            val req = Request.Builder().url(endpoint(config, "", kind)).delete().applyHeaders(config).build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful && response.code != 404) {
                    throw IllegalStateException("Could not clear vector collection (" + response.code + ")")
                }
            }
        }
    }

    private fun ensureCollection(vector: FloatArray, config: AiProviderConfigEntity, kind: BrainVectorKind) {
        val url = endpoint(config, "", kind)
        val req = Request.Builder().url(url)
            .put(JSONObject().put("vectors", JSONObject().put("size", vector.size).put("distance", "Cosine")).toString().toRequestBody(jsonType))
            .applyHeaders(config).build()
        client.newCall(req).execute().use { response ->
            if (response.isSuccessful) return
            if (response.code != 409) throw IllegalStateException("Qdrant collection setup failed")
        }
    }

    private fun request(method: String, url: String, body: JSONObject, config: AiProviderConfigEntity, allowMissing: Boolean = false): JSONObject {
        val requestBuilder = Request.Builder().url(url).applyHeaders(config)
        val request = if (method == "PUT") requestBuilder.put(body.toString().toRequestBody(jsonType)).build() else requestBuilder.post(body.toString().toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (allowMissing && response.code == 404) return JSONObject()
                throw IllegalStateException("Vector database request failed (" + response.code + ")")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun Request.Builder.applyHeaders(config: AiProviderConfigEntity): Request.Builder {
        if (config.vectorDatabaseApiKey.isNotBlank()) header("api-key", config.vectorDatabaseApiKey.trim())
        return header("Accept", "application/json")
    }

    private fun endpoint(config: AiProviderConfigEntity, suffix: String, kind: BrainVectorKind): String {
        val base = config.vectorDatabaseBaseUrl.trim().trimEnd('/')
        require(base.isNotBlank()) { "Qdrant URL is required" }
        val root = config.vectorDatabaseCollection.trim().ifBlank { "emrexplore_brain" }
        val collection = root + if (kind == BrainVectorKind.IMAGE) "_image" else ""
        return base + "/collections/" + collection + suffix
    }

    private fun stablePointId(id: String, kind: BrainVectorKind): String =
        java.util.UUID.nameUUIDFromBytes((id + ":" + kind.name).toByteArray(Charsets.UTF_8)).toString()
}
fun vectorStoreFor(config: AiProviderConfigEntity, chunkDao: BrainChunkDao): BrainVectorStore =
    when (VectorDatabaseType.fromString(config.vectorDatabaseType)) {
        VectorDatabaseType.QDRANT -> QdrantBrainVectorStore()
        VectorDatabaseType.ROOM -> RoomBrainVectorStore(chunkDao)
    }
