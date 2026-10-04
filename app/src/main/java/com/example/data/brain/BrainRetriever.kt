package com.example.data.brain

import com.example.data.ai.AiProviderClient
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
    private val client: AiProviderClient
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

        val hits = PriorityQueue<BrainSearchHit>(limit * 2) { a, b ->
            a.score.compareTo(b.score)
        }
        collectOfflineHits(offlineQuery, hits, limit * 2)

        if (onlineQuery != null) {
            collectModelHits(onlineModel, onlineQuery, hits, limit * 2)
        }

        var bestHits = drainTop(hits, limit)
        if (bestHits.isEmpty() || bestHits.first().score < MIN_VECTOR_SCORE) {
            bestHits = mergeLexicalFallback(clean, bestHits, limit)
        }

        bestHits = bestHits
            .filter { hit ->
                val file = File(hit.chunk.filePath)
                file.isFile && file.canRead()
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
        val relatedNodes = if (neighborIds.isEmpty()) emptyList() else nodeDao.getByIds(neighborIds)

        val nodeMap = (fileNodes + relatedNodes).associateBy { it.id }
        val evidence = edges.asSequence()
            .filter { safe || it.relation != "LOCATED_AT" }
            .sortedByDescending { it.weight }
            .take(MAX_EVIDENCE)
            .mapNotNull { edge ->
                val source = nodeMap[edge.sourceNodeId] ?: return@mapNotNull null
                val target = nodeMap[edge.targetNodeId] ?: return@mapNotNull null
                val snippet = redactSensitive(edge.evidenceSnippet)
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

            val content = if (safe) hit.chunk.content else redactSensitive(hit.chunk.content)
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
        queue: PriorityQueue<BrainSearchHit>,
        capacity: Int
    ) {
        if (queryVector.isEmpty()) return

        var offset = 0
        while (true) {
            val page = chunkDao.getOfflinePage(PAGE_SIZE, offset)
            if (page.isEmpty()) break

            for (chunk in page) {
                val vector = OfflineEmbeddingEngine.parseEmbedding(chunk.offlineEmbeddingJson)
                if (vector.isEmpty() || vector.size != queryVector.size) continue
                val score = OfflineEmbeddingEngine.cosine(queryVector, vector)
                offer(queue, BrainSearchHit(chunk, score), capacity)
            }

            if (page.size < PAGE_SIZE) break
            offset += page.size
            if (offset >= MAX_SCAN_ROWS) break
        }
    }

    private suspend fun collectModelHits(
        model: String,
        queryVector: FloatArray,
        queue: PriorityQueue<BrainSearchHit>,
        capacity: Int
    ) {
        if (model.isBlank() || queryVector.isEmpty()) return

        var offset = 0
        while (true) {
            val page = chunkDao.getEmbeddedPage(model, PAGE_SIZE, offset)
            if (page.isEmpty()) break

            for (chunk in page) {
                val vector = OfflineEmbeddingEngine.parseEmbedding(chunk.embeddingJson)
                if (vector.isEmpty() || vector.size != queryVector.size) continue
                val score = OfflineEmbeddingEngine.cosine(queryVector, vector)
                offer(queue, BrainSearchHit(chunk, score), capacity)
            }

            if (page.size < PAGE_SIZE) break
            offset += page.size
            if (offset >= MAX_SCAN_ROWS) break
        }
    }

    private suspend fun mergeLexicalFallback(
        question: String,
        existing: List<BrainSearchHit>,
        limit: Int
    ): List<BrainSearchHit> {
        val byId = LinkedHashMap<String, BrainSearchHit>()
        existing.forEach { byId[it.chunk.id] = it }

        val tokens = tokenize(question)
        for (token in tokens) {
            val candidates = chunkDao.lexical(token, 8)
            for (chunk in candidates) {
                val score = lexicalScore(chunk.content, tokens)
                val current = byId[chunk.id]
                if (current == null || score > current.score) {
                    byId[chunk.id] = BrainSearchHit(chunk, score)
                }
            }
        }

        return byId.values.sortedByDescending { it.score }.take(limit)
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

    private fun redactSensitive(value: String): String {
        return value
            .replace(Regex("(?im)^\\s*(GPS|Location):.*(?:\\R|$)"), "")
            .replace(
                Regex("(?i)(exact coordinates|geographic coordinates|coordinates?)\\s*[:=]?\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?")
            ) { match ->
                match.groupValues[1] + ": [redacted]"
            }
    }

    companion object {
        const val PAGE_SIZE = 128
        const val MAX_SCAN_ROWS = 20_000
        const val MAX_FILE_SOURCES = 12
        const val MAX_RELATED_NODES = 24
        const val MAX_EVIDENCE = 18
        const val MAX_CONTEXT_CHARS = 18_000
        const val MIN_VECTOR_SCORE = 0.12f
    }
}
