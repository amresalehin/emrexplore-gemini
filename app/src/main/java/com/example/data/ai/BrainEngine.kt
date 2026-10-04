package com.example.data.ai

import com.example.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class BrainEngine(
    private val ragDao: RagDao,
    private val kgDao: KgDao,
    private val client: AiProviderClient
) {
    data class ScoredChunk(val chunk: RagChunkEntity, val score: Float)

    suspend fun search(question: String, config: AiProviderConfigEntity, limit: Int = 8): List<ScoredChunk> = withContext(Dispatchers.IO) {
        val lexical = linkedMapOf<String, Float>()
        tokenize(question).forEach { token ->
            ragDao.searchChunks(token, 16).forEachIndexed { index, chunk ->
                val score = 1f / (index + 1)
                lexical[chunk.chunkId] = maxOf(lexical[chunk.chunkId] ?: 0f, score)
            }
        }
        val embedded = ragDao.getEmbeddedChunks()
        val queryEmbedding = if (config.isEnabled && config.apiKey.isNotBlank()) client.embedTexts(listOf(question), config).firstOrNull() else null
        val candidates = linkedMapOf<String, Float>()
        embedded.forEach { chunk ->
            val semantic = queryEmbedding?.let { cosine(it, parseEmbedding(chunk.embeddingJson)) } ?: 0f
            val lex = lexical[chunk.chunkId] ?: 0f
            val score = 0.70f * semantic + 0.30f * lex
            if (score > 0f) candidates[chunk.chunkId] = score
        }
        lexical.forEach { (id, score) -> candidates[id] = maxOf(candidates[id] ?: 0f, 0.30f * score) }
        if (candidates.isEmpty()) return@withContext emptyList()
        val rows = ragDao.getChunksByIds(candidates.keys.toList())\n        rows.sortedByDescending { candidates[it.chunkId] ?: 0f }.take(limit).map { ScoredChunk(it, candidates[it.chunkId] ?: 0f) }
    }

    suspend fun graphContext(seedNodes: List<KgNodeEntity>, maxDepth: Int = 3, maxPerNode: Int = 8): Pair<List<KgNodeEntity>, List<String>> = withContext(Dispatchers.IO) {
        val discovered = LinkedHashMap<String, KgNodeEntity>()
        val evidence = mutableListOf<String>()
        seedNodes.distinctBy { it.id }.take(12).forEach { seed ->
            val queue = ArrayDeque<Pair<String, Int>>()
            val seen = mutableSetOf(seed.id)
            queue.add(seed.id to 0)
            while (queue.isNotEmpty()) {
                val (nodeId, depth) = queue.removeFirst()
                if (depth >= maxDepth) continue
                kgDao.getEdgesForNode(nodeId).sortedByDescending { it.weight }.take(maxPerNode).forEach { graphEdge ->
                    val neighborId = if (graphEdge.sourceNodeId == nodeId) graphEdge.targetNodeId else graphEdge.sourceNodeId
                    if (!seen.add(neighborId)) return@forEach
                    val neighbor = kgDao.getNode(neighborId) ?: return@forEach
                    discovered.putIfAbsent(neighbor.id, neighbor)
                    val sourceLabel = kgDao.getNode(nodeId)?.label ?: nodeId
                    evidence += "[$sourceLabel] -${graphEdge.relation}-> [${neighbor.label}] (${graphEdge.evidenceSnippet})"
                    queue.add(neighborId to depth + 1)
                }
            }
        }
        discovered.values.toList() to evidence.distinct().take(48)
    }

    private fun tokenize(value: String): List<String> = value.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }.distinct()

    private fun parseEmbedding(json: String?): FloatArray {
        if (json.isNullOrBlank()) return FloatArray(0)
        return try {
            val array = org.json.JSONArray(json)
            FloatArray(array.length()) { array.optDouble(it, 0.0).toFloat() }
        } catch (_: Exception) { FloatArray(0) }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0; var aa = 0.0; var bb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i] }
        return if (aa == 0.0 || bb == 0.0) 0f else (dot / (sqrt(aa) * sqrt(bb))).toFloat().coerceIn(0f, 1f)
    }
}