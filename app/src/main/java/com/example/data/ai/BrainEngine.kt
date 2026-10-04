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

    companion object {
        private const val LEXICAL_RESULTS_PER_TOKEN = 8
        private const val MAX_QUERY_TOKENS = 6
        private const val SEMANTIC_PAGE_SIZE = 64
        private const val SEMANTIC_TIME_BUDGET_MS = 750L
        private const val MAX_SEMANTIC_ROWS = 8192
        private const val MAX_GRAPH_SEEDS = 8
        private const val MAX_GRAPH_NODES = 64
        private const val MAX_GRAPH_EVIDENCE = 24
    }

    suspend fun search(question: String, config: AiProviderConfigEntity, limit: Int = 8): List<ScoredChunk> = withContext(Dispatchers.IO) {
        val lexical = linkedMapOf<String, Float>()
        tokenize(question).forEach { token ->
            ragDao.searchChunks(token, LEXICAL_RESULTS_PER_TOKEN).forEachIndexed { index, chunk ->
                val score = 1f / (index + 1)
                lexical[chunk.chunkId] = maxOf(lexical[chunk.chunkId] ?: 0f, score)
            }
        }

        val semanticAvailable = config.isEnabled &&
            (ProviderType.fromString(config.providerType) in setOf(
                ProviderType.OLLAMA,
                ProviderType.OPENAI_COMPATIBLE,
                ProviderType.CUSTOM
            ) || config.apiKey.isNotBlank()) &&
            config.embeddingModel.isNotBlank()

        // Keep semantic retrieval active whenever embeddings are available. This is
        // the high-value recall path for paraphrases and concepts that do not share
        // exact words with the query. The scan itself remains bounded for resource use.
        val queryEmbedding = if (semanticAvailable && question.isNotBlank()) {
            try {
                client.embedTexts(listOf(question), config).firstOrNull()
            } catch (_: Exception) {
                // Semantic retrieval is optional at query time. Fall back to lexical retrieval
                // when the embedding provider is temporarily unavailable.
                null
            }
        } else null

        val candidates = linkedMapOf<String, Float>()
        if (queryEmbedding != null) {
            val startedAt = System.nanoTime()
            var offset = 0
            var scanned = 0

            while (scanned < MAX_SEMANTIC_ROWS &&
                (System.nanoTime() - startedAt) / 1_000_000L < SEMANTIC_TIME_BUDGET_MS
            ) {
                val pageLimit = minOf(SEMANTIC_PAGE_SIZE, MAX_SEMANTIC_ROWS - scanned)
                val page = ragDao.getEmbeddedChunksPage(config.embeddingModel, pageLimit, offset)
                if (page.isEmpty()) break

                page.forEach { row ->
                    val vector = parseEmbedding(row.embeddingJson)
                    if (vector.isNotEmpty()) {
                        val semantic = cosine(queryEmbedding, vector)
                        val lex = lexical[row.chunkId] ?: 0f
                        val score = 0.70f * semantic + 0.30f * lex
                        if (score > 0f) candidates[row.chunkId] = score
                    }
                }

                scanned += page.size
                offset += page.size
                if (page.size < pageLimit) break
            }
        }

        lexical.forEach { (id, score) ->
            candidates[id] = maxOf(candidates[id] ?: 0f, 0.30f * score)
        }
        if (candidates.isEmpty()) return@withContext emptyList()

        val topIds = candidates.entries
            .sortedByDescending { it.value }
            .take((limit * 4).coerceAtLeast(limit))
            .map { it.key }
        val rows = ragDao.getChunksByIds(topIds)
        rows.sortedByDescending { candidates[it.chunkId] ?: 0f }
            .take(limit)
            .map { ScoredChunk(it, candidates[it.chunkId] ?: 0f) }
    }

    suspend fun graphContext(
        seedNodes: List<KgNodeEntity>,
        maxDepth: Int = 3,
        maxPerNode: Int = 6
    ): Pair<List<KgNodeEntity>, List<String>> = withContext(Dispatchers.IO) {
        val seeds = seedNodes.distinctBy { it.id }.take(MAX_GRAPH_SEEDS)
        if (seeds.isEmpty() || maxDepth <= 0) return@withContext emptyList<KgNodeEntity>() to emptyList()

        val discovered = LinkedHashMap<String, KgNodeEntity>()
        val knownNodes = seeds.associateBy { it.id }.toMutableMap()
        val seen = seeds.mapTo(mutableSetOf()) { it.id }
        var frontier = seeds
        val evidence = mutableListOf<String>()

        repeat(maxDepth) {
            if (frontier.isEmpty() || discovered.size >= MAX_GRAPH_NODES) return@repeat

            val frontierIds = frontier.map { it.id }
            val frontierSet = frontierIds.toHashSet()
            val edges = kgDao.getEdgesForNodes(frontierIds)
            val edgesByNode = HashMap<String, MutableList<KgEdgeEntity>>()

            for (edge in edges) {
                if (edge.sourceNodeId in frontierSet) {
                    edgesByNode.getOrPut(edge.sourceNodeId) { mutableListOf() }.add(edge)
                }
                if (edge.targetNodeId in frontierSet) {
                    edgesByNode.getOrPut(edge.targetNodeId) { mutableListOf() }.add(edge)
                }
            }

            val neighborIds = edges
                .asSequence()
                .flatMap { sequenceOf(it.sourceNodeId, it.targetNodeId) }
                .filter { it !in seen }
                .distinct()
                .take(MAX_GRAPH_NODES - discovered.size)
                .toList()
            if (neighborIds.isEmpty()) return@repeat

            val neighbors = kgDao.getNodes(neighborIds).associateBy { it.id }
            knownNodes.putAll(neighbors)
            val nextFrontier = LinkedHashMap<String, KgNodeEntity>()

            for (node in frontier) {
                val nodeEdges = edgesByNode[node.id]
                    .orEmpty()
                    .sortedByDescending { it.weight }
                    .take(maxPerNode)

                for (edge in nodeEdges) {
                    val neighborId = if (edge.sourceNodeId == node.id) edge.targetNodeId else edge.sourceNodeId
                    if (!seen.add(neighborId)) continue

                    val neighbor = knownNodes[neighborId] ?: continue
                    discovered.putIfAbsent(neighbor.id, neighbor)

                    val sourceLabel = knownNodes[node.id]?.label ?: node.id
                    evidence += "[$sourceLabel] -${edge.relation}-> [${neighbor.label}] (${edge.evidenceSnippet})"
                    nextFrontier.putIfAbsent(neighbor.id, neighbor)

                    if (discovered.size >= MAX_GRAPH_NODES) break
                }
                if (discovered.size >= MAX_GRAPH_NODES) break
            }

            frontier = nextFrontier.values.take(MAX_GRAPH_SEEDS * maxPerNode).toList()
        }

        discovered.values.toList() to evidence.distinct().take(MAX_GRAPH_EVIDENCE)
    }

    private fun tokenize(value: String): List<String> {
        val stopWords = setOf(
            "the", "and", "for", "with", "this", "that", "from", "what", "when", "where",
            "your", "have", "has", "are", "was", "were", "will", "would", "could", "should",
            "about", "into", "over", "under", "then", "than", "how", "why", "who", "which",
            "my", "me", "you", "our", "their", "its", "does", "did", "can"
        )
        return value.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .asSequence()
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
            .sortedByDescending { it.length }
            .take(MAX_QUERY_TOKENS)
            .toList()
    }
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