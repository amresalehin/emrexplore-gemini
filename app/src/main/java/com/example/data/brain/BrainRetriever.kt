package com.example.data.brain

import com.example.data.ai.OfflineEmbeddingEngine
import com.example.data.ai.ProviderType
import com.example.data.ai.isKeylessAiConfig
import com.example.data.local.AiProviderConfigEntity
import java.io.File
import java.util.PriorityQueue
import java.util.Locale
import kotlin.math.max

data class BrainSearchHit(
    val chunk: BrainChunkEntity,
    val score: Float
)

data class BrainRetrieval(
    val hits: List<BrainSearchHit>,
    val relatedNodes: List<BrainNodeEntity>,
    val evidence: List<String>
) {
    val hasMatch: Boolean
        get() = hits.isNotEmpty() || relatedNodes.isNotEmpty()
}

class BrainRetriever(
    private val context: android.content.Context,
    private val chunkDao: BrainChunkDao,
    private val nodeDao: BrainNodeDao,
    private val edgeDao: BrainEdgeDao,
    private val client: BrainAiGateway
) {
    suspend fun retrieve(
        question: String,
        config: AiProviderConfigEntity,
        limit: Int = 8
    ): BrainRetrieval {
        val clean = question.trim()
        if (clean.isBlank() || limit <= 0) {
            return BrainRetrieval(emptyList(), emptyList(), emptyList())
        }

        val offlineQuery = OfflineEmbeddingEngine.embedText(clean)
        val onlineModel = config.textEmbeddingModel.trim().ifBlank { config.embeddingModel.trim() }
        val onlineQuery = if (
            config.isEnabled &&
            onlineModel.isNotBlank() &&
            (isKeylessAiConfig(config) || config.apiKey.isNotBlank())
        ) {
            runCatching { client.embedTextQuery(clean, config) }.getOrNull()
        } else {
            null
        }

        val offlineHits = collectOfflineHits(offlineQuery, limit * 3)
        val onlineHits = if (onlineQuery != null) {
            collectModelHits(onlineModel, onlineQuery, limit * 3)
        } else {
            emptyList()
        }

        var bestHits = fuseRanks(offlineHits, onlineHits, limit)
        if (bestHits.size < limit) {
            bestHits = mergeLexicalFallback(clean, bestHits, limit)
        }

        bestHits = bestHits
            .filter { hit ->
                val file = File(hit.chunk.filePath)
                val segments = file.absolutePath.split(File.separatorChar)
                file.name.startsWith(".").not() &&
                    segments.none { it == ".trash" } &&
                    file.isFile &&
                    file.canRead()
            }
            .take(limit)

        val paths = bestHits.map { it.chunk.filePath }.distinct().take(MAX_FILE_SOURCES)
        val fileNodes = if (paths.isEmpty()) {
            emptyList()
        } else {
            nodeDao.getByFilePaths(paths).filter { it.nodeType == "DOCUMENT" || it.nodeType == "IMAGE" }
        }
        val fileNodeByPath = fileNodes.associateBy { it.sourceFilePath.orEmpty() }

        val fileNodeIds = paths.mapNotNull { fileNodeByPath[it]?.id }
        val edges = if (fileNodeIds.isEmpty()) emptyList() else edgeDao.forNodes(fileNodeIds)
        val safe = ProviderType.fromString(config.providerType) == ProviderType.OLLAMA

        val neighborIds = edges.asSequence()
            .flatMap { sequenceOf(it.sourceNodeId, it.targetNodeId) }
            .filter { it !in fileNodeIds }
            .distinct()
            .take(MAX_RELATED_NODES)
            .toList()
        val relatedNodesRaw = if (neighborIds.isEmpty()) emptyList() else nodeDao.getByIds(neighborIds)
        val relatedNodes = if (safe) {
            relatedNodesRaw
        } else {
            relatedNodesRaw.map { node ->
                node.copy(summary = BrainPrivacy.redactSensitive(node.summary))
            }
        }

        val nodeMap = (fileNodes + relatedNodes).associateBy { it.id }
        val evidence = edges.asSequence()
            .filter { safe || it.relation != "LOCATED_AT" }
            .sortedByDescending { it.weight }
            .take(MAX_EVIDENCE)
            .mapNotNull { edge ->
                val source = nodeMap[edge.sourceNodeId] ?: return@mapNotNull null
                val target = nodeMap[edge.targetNodeId] ?: return@mapNotNull null
                val snippet = BrainPrivacy.redactSensitive(edge.evidenceSnippet)
                "[" + source.label + "] -" + edge.relation + "-> [" + target.label + "]" +
                    if (snippet.isBlank()) "" else " (" + snippet + ")"
            }
            .distinct()
            .toList()

        return BrainRetrieval(bestHits, (fileNodes + relatedNodes).distinctBy { it.id }, evidence)
    }

    fun buildContext(
        retrieval: BrainRetrieval,
        config: AiProviderConfigEntity,
        maxChars: Int = MAX_CONTEXT_CHARS
    ): Pair<String, String> {
        val safe = ProviderType.fromString(config.providerType) == ProviderType.OLLAMA
        val contextBuilder = StringBuilder()
        var sourceNumber = 1

        for (hit in retrieval.hits) {
            if (contextBuilder.length >= maxChars) break
            val file = File(hit.chunk.filePath)
            if (!file.exists() || !file.isFile) continue

            val content = if (safe) hit.chunk.content else BrainPrivacy.redactSensitive(hit.chunk.content)
            val header = "=== SOURCE " + sourceNumber + ": " + file.name + " ===\n"
            val remaining = maxChars - contextBuilder.length
            if (remaining <= header.length) break
            contextBuilder.append(header)
            contextBuilder.append(content.take(remaining - header.length).trim())
            contextBuilder.append("\n\n")
            sourceNumber++
        }

        val evidence = retrieval.evidence.joinToString("\n")
        return contextBuilder.toString().trim() to evidence
    }

    private suspend fun collectOfflineHits(
        queryVector: FloatArray,
        limit: Int
    ): List<BrainSearchHit> {
        if (queryVector.isEmpty() || limit <= 0) return emptyList()

        val queue = PriorityQueue<BrainSearchHit>(limit * 2) { a, b ->
            a.score.compareTo(b.score)
        }
        var offset = 0
        while (true) {
            val page = chunkDao.getOfflinePage(PAGE_SIZE, offset)
            if (page.isEmpty()) break

            for (chunk in page) {
                val vector = OfflineEmbeddingEngine.parseEmbedding(chunk.offlineEmbeddingJson)
                if (vector.isEmpty() || vector.size != queryVector.size) continue
                val score = OfflineEmbeddingEngine.cosine(queryVector, vector)
                if (score >= MIN_VECTOR_SCORE) {
                    offer(queue, BrainSearchHit(chunk, score), limit)
                }
            }

            if (page.size < PAGE_SIZE) break
            offset += page.size
            if (offset >= MAX_SCAN_ROWS) break
        }
        return drainTop(queue, limit)
    }

    private suspend fun collectModelHits(
        model: String,
        queryVector: FloatArray,
        limit: Int
    ): List<BrainSearchHit> {
        if (model.isBlank() || queryVector.isEmpty() || limit <= 0) return emptyList()

        val queue = PriorityQueue<BrainSearchHit>(limit * 2) { a, b ->
            a.score.compareTo(b.score)
        }
        var offset = 0
        while (true) {
            val page = chunkDao.getEmbeddedPage(model, PAGE_SIZE, offset)
            if (page.isEmpty()) break

            for (chunk in page) {
                val vector = OfflineEmbeddingEngine.parseEmbedding(chunk.embeddingJson)
                if (vector.isEmpty() || vector.size != queryVector.size) continue
                val score = OfflineEmbeddingEngine.cosine(queryVector, vector)
                if (score >= MIN_VECTOR_SCORE) {
                    offer(queue, BrainSearchHit(chunk, score), limit)
                }
            }

            if (page.size < PAGE_SIZE) break
            offset += page.size
            if (offset >= MAX_SCAN_ROWS) break
        }
        return drainTop(queue, limit)
    }

    private fun fuseRanks(
        offlineHits: List<BrainSearchHit>,
        onlineHits: List<BrainSearchHit>,
        limit: Int
    ): List<BrainSearchHit> {
        data class RankScore(
            val chunk: BrainChunkEntity,
            val rrf: Float
        )

        val fused = mutableMapOf<String, RankScore>()
        fun addRanks(hits: List<BrainSearchHit>) {
            hits.forEachIndexed { index, hit ->
                val contribution = 1f / (RRF_K + index + 1)
                val current = fused[hit.chunk.id]
                fused[hit.chunk.id] = RankScore(
                    chunk = hit.chunk,
                    rrf = (current?.rrf ?: 0f) + contribution
                )
            }
        }

        addRanks(offlineHits)
        addRanks(onlineHits)

        return fused.values
            .sortedWith(compareByDescending<RankScore> { it.rrf }.thenBy { it.chunk.id })
            .take(limit)
            .map { BrainSearchHit(it.chunk, it.rrf) }
    }

    private suspend fun mergeLexicalFallback(
        question: String,
        existing: List<BrainSearchHit>,
        limit: Int
    ): List<BrainSearchHit> {
        if (existing.size >= limit) return existing.take(limit)

        val existingIds = existing.asSequence().map { it.chunk.id }.toHashSet()
        val lexicalScores = LinkedHashMap<String, Float>()
        val tokens = tokenize(question)

        for (token in tokens) {
            val candidates = chunkDao.lexical(token, 8)
            for (chunk in candidates) {
                if (chunk.id in existingIds) continue
                val score = lexicalScore(chunk.content, tokens)
                lexicalScores[chunk.id] = maxOf(lexicalScores[chunk.id] ?: 0f, score)
            }
        }

        if (lexicalScores.isEmpty()) return existing

        val lexicalIds = lexicalScores
            .entries
            .sortedByDescending { it.value }
            .take((limit - existing.size).coerceAtLeast(0))
            .map { it.key }

        val chunks = chunkDao.getByIds(lexicalIds).associateBy { it.id }
        val lexicalHits = lexicalIds.mapNotNull { id ->
            chunks[id]?.let { chunk -> BrainSearchHit(chunk, lexicalScores[id] ?: 0f) }
        }

        return (existing + lexicalHits).take(limit)
    }

    private fun tokenize(value: String): List<String> =
        value.lowercase(Locale.US)
            .split(Regex("[^\\p{L}\\p{N}_-]+"))
            .filter { it.length >= 3 }
            .distinct()
            .take(12)

    private fun lexicalScore(content: String, tokens: List<String>): Float {
        if (tokens.isEmpty()) return 0f
        val lower = content.lowercase(Locale.US)
        val hits = tokens.count { token -> lower.contains(token) }
        return hits.toFloat() / max(tokens.size, 1).toFloat()
    }

    private fun offer(
        queue: PriorityQueue<BrainSearchHit>,
        item: BrainSearchHit,
        capacity: Int
    ) {
        queue.offer(item)
        while (queue.size > capacity) queue.poll()
    }

    private fun drainTop(queue: PriorityQueue<BrainSearchHit>, limit: Int): List<BrainSearchHit> =
        buildList {
            while (queue.isNotEmpty()) add(queue.poll())
        }.asReversed().take(limit)

    companion object {
        const val PAGE_SIZE = 128
        const val MAX_SCAN_ROWS = 20_000
        const val MAX_FILE_SOURCES = 12
        const val MAX_RELATED_NODES = 24
        const val MAX_EVIDENCE = 18
        const val MAX_CONTEXT_CHARS = 18_000
        const val MIN_VECTOR_SCORE = 0.12f
        const val RRF_K = 60f
    }
}
