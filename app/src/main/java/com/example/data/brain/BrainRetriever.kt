package com.example.data.brain

import com.example.data.ai.ProviderType
import com.example.data.ai.EmbeddingProviderType
import com.example.data.ai.AiProviderClient
import com.example.data.local.AiProviderConfigEntity
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.PriorityQueue
import java.util.Locale

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

internal object BrainLexicalScorer {
    private val lowSignalTerms = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "can", "did", "do", "does",
        "for", "from", "how", "i", "in", "is", "it", "me", "my", "of", "on", "or",
        "that", "the", "this", "to", "was", "what", "when", "where", "which", "who",
        "why", "with", "you", "your", "document", "file", "project"
    )
    private val tokenPattern = Regex("[a-z0-9_./-]+")

    fun meaningfulTokens(query: String): List<String> =
        tokenPattern.findAll(query.lowercase(Locale.US))
            .map { it.value }
            .filter { it.length >= 2 && it !in lowSignalTerms }
            .distinct()
            .toList()

    fun score(query: String, tokens: List<String>, content: String, filePath: String): Float {
        if (tokens.isEmpty()) return 0f
        val normalizedContent = content.lowercase(Locale.US)
        val normalizedPath = filePath.lowercase(Locale.US)
        val contentTokens = tokenPattern.findAll(normalizedContent).map { it.value }.toSet()
        val pathTokens = tokenPattern.findAll(normalizedPath).map { it.value }.toSet()
        val matches = tokens.count { it in contentTokens || it in pathTokens }
        if (matches == 0) return 0f

        val coverage = matches.toFloat() / tokens.size
        val rareCoverage = tokens.count { it.length >= 6 && (it in contentTokens || it in pathTokens) }.toFloat() /
            tokens.count { it.length >= 6 }.coerceAtLeast(1)
        val normalizedQuery = tokens.joinToString(" ")
        val phraseBonus = if (tokens.size >= 2 && normalizedContent.contains(normalizedQuery)) 0.25f else 0f
        val pathBonus = if (tokens.any { it in pathTokens }) 0.15f else 0f
        return (coverage * 0.45f + rareCoverage.coerceAtMost(1f) * 0.20f + phraseBonus + pathBonus).coerceAtMost(0.95f)
    }
}

class BrainRetriever(
    private val chunkDao: BrainChunkDao,
    private val nodeDao: BrainNodeDao,
    private val edgeDao: BrainEdgeDao,
    private val onDeviceEmbedding: OnDeviceEmbeddingEngine,
    private val embeddingClient: AiProviderClient,
    private val vectorStoreFactory: (AiProviderConfigEntity) -> BrainVectorStore = { config -> vectorStoreFor(config, chunkDao) }
) {
    suspend fun retrieve(
        question: String,
        config: AiProviderConfigEntity,
        limit: Int = 8
    ): BrainRetrieval {
        val clean = question.trim()
        if (clean.isBlank() || limit <= 0) return BrainRetrieval(emptyList(), emptyList(), emptyList())

        val embeddingProvider = EmbeddingProviderType.fromString(config.embeddingProviderType)
        val textSemanticHits = runCatching {
            when (embeddingProvider) {
                EmbeddingProviderType.OFFLINE -> {
                    onDeviceEmbedding.embedText(clean)?.let {
                        vectorStoreFactory(config).search(
                            it, onDeviceEmbedding.modelIdIfReady().orEmpty(), limit * 3, config, BrainVectorKind.TEXT
                        )
                    }?.let { results -> results.mapNotNull { result ->
                        chunkDao.getByIds(listOf(result.id)).firstOrNull()?.let { BrainSearchHit(it, result.score) }
                    }}.orEmpty()
                }
                else -> {
                    val model = config.textEmbeddingModel.ifBlank { config.embeddingModel }.trim()
                    embeddingClient.embedTextQuery(clean, config)?.let {
                        vectorStoreFactory(config).search(
                            it, remoteEmbeddingSignature(config, model), limit * 3, config, BrainVectorKind.TEXT
                        )
                    }?.let { results -> results.mapNotNull { result ->
                        chunkDao.getByIds(listOf(result.id)).firstOrNull()?.let { BrainSearchHit(it, result.score) }
                    }}.orEmpty()
                }
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            emptyList()
        }

        // Brain never generates or queries image embeddings. Gallery AI owns image embedding generation;
        // Brain retrieves the saved image AI profile through the normal text/lexical representation.
        val imageSemanticHits = emptyList<BrainSearchHit>()

        val semanticHits = fuseSemanticHits(textSemanticHits, imageSemanticHits, limit * 3)

        val bestHits = mergeLexicalFallback(clean, semanticHits, limit)
            .filter { hit ->
                val file = File(hit.chunk.filePath)
                val segments = file.absolutePath.split(File.separatorChar)
                !file.name.startsWith(".") &&
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

        val fileNodeIds = fileNodes.map { it.id }
        val edges = if (fileNodeIds.isEmpty()) emptyList() else edgeDao.forNodes(fileNodeIds)
        val safe = ProviderType.fromString(config.providerType) == ProviderType.OLLAMA

        val neighborIds = edges.asSequence()
            .flatMap { sequenceOf(it.sourceNodeId, it.targetNodeId) }
            .filter { it !in fileNodeIds }
            .distinct()
            .take(MAX_RELATED_NODES)
            .toList()

        val relatedRaw = if (neighborIds.isEmpty()) emptyList() else nodeDao.getByIds(neighborIds)
        val relatedNodes = if (safe) relatedRaw else relatedRaw.map {
            it.copy(summary = BrainPrivacy.redactSensitive(it.summary))
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
        val context = StringBuilder()
        var sourceNumber = 1

        for (hit in retrieval.hits) {
            if (context.length >= maxChars) break
            val file = File(hit.chunk.filePath)
            if (!file.isFile || !file.canRead()) continue

            val content = if (safe) hit.chunk.content else BrainPrivacy.redactSensitive(hit.chunk.content)
            val header = "=== SOURCE " + sourceNumber + ": " + file.name + " ===\n"
            val remaining = maxChars - context.length
            if (remaining <= header.length) break
            context.append(header)
            context.append(content.take(remaining - header.length).trim())
            context.append("\n\n")
            sourceNumber++
        }

        return context.toString().trim() to retrieval.evidence.joinToString("\n")
    }

    private fun fuseSemanticHits(
        textHits: List<BrainSearchHit>,
        imageHits: List<BrainSearchHit>,
        limit: Int
    ): List<BrainSearchHit> {
        val scores = linkedMapOf<String, Float>()
        val best = linkedMapOf<String, BrainSearchHit>()
        textHits.take(limit).forEachIndexed { rank, hit ->
            scores[hit.chunk.id] = (scores[hit.chunk.id] ?: 0f) + 0.65f / (rank + 1)
            best[hit.chunk.id] = hit
        }
        imageHits.take(limit).forEachIndexed { rank, hit ->
            scores[hit.chunk.id] = (scores[hit.chunk.id] ?: 0f) + 0.35f / (rank + 1)
            if (best[hit.chunk.id] == null || hit.score > best.getValue(hit.chunk.id).score) best[hit.chunk.id] = hit
        }
        return scores.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, score) -> best[id]?.copy(score = score) }
            .take(limit)
    }

    private suspend fun collectLocalHits(
        model: String,
        queryVector: FloatArray,
        limit: Int
    ): List<BrainSearchHit> {
        if (model.isBlank() || queryVector.isEmpty() || limit <= 0) return emptyList()

        val queue = PriorityQueue<BrainSearchHit>(limit * 2) { a, b -> a.score.compareTo(b.score) }
        var afterIndexedAt: Long? = null
        var afterId: String? = null

        while (true) {
            val page = chunkDao.getEmbeddedPage(model, PAGE_SIZE, afterIndexedAt, afterId)
            if (page.isEmpty()) break

            for (chunk in page) {
                val vector = BrainVectorCodec.fromJson(chunk.embeddingJson)
                if (vector.isEmpty() || vector.size != queryVector.size) continue
                val score = BrainVectorCodec.cosine(queryVector, vector)
                if (score >= MIN_VECTOR_SCORE) offer(queue, BrainSearchHit(chunk, score), limit)
            }

            if (page.size < PAGE_SIZE) break
            val last = page.last()
            afterIndexedAt = last.indexedAt
            afterId = last.id
        }

        return drainTop(queue, limit)
    }

    private suspend fun mergeLexicalFallback(
        query: String,
        semantic: List<BrainSearchHit>,
        limit: Int
    ): List<BrainSearchHit> {
        val tokens = BrainLexicalScorer.meaningfulTokens(query).take(MAX_QUERY_TERMS)
        if (tokens.isEmpty()) return semantic.take(limit)

        val lexical = tokens.flatMap { token ->
            chunkDao.lexical(token, limit * 4)
        }

        val scores = linkedMapOf<String, Float>()
        semantic.forEach { scores[it.chunk.id] = maxOf(scores[it.chunk.id] ?: 0f, it.score) }

        lexical.forEach { chunk ->
            val lexicalScore = BrainLexicalScorer.score(query, tokens, chunk.content, chunk.filePath)
            if (lexicalScore >= MIN_LEXICAL_SCORE) {
                scores[chunk.id] = maxOf(scores[chunk.id] ?: 0f, lexicalScore)
            }
        }

        val allIds = scores.keys.toList()
        if (allIds.isEmpty()) return emptyList()
        val chunks = chunkDao.getByIds(allIds).associateBy { it.id }
        return scores.mapNotNull { (id, score) ->
            chunks[id]?.let { BrainSearchHit(it, score) }
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun offer(queue: PriorityQueue<BrainSearchHit>, hit: BrainSearchHit, limit: Int) {
        if (queue.size < limit) {
            queue.offer(hit)
        } else if ((queue.peek()?.score ?: Float.NEGATIVE_INFINITY) < hit.score) {
            queue.poll()
            queue.offer(hit)
        }
    }

    private fun drainTop(queue: PriorityQueue<BrainSearchHit>, limit: Int): List<BrainSearchHit> {
        val result = ArrayList<BrainSearchHit>(queue.size)
        while (queue.isNotEmpty()) {
            queue.poll()?.let { result += it }
        }
        result.reverse()
        return result.take(limit)
    }

    companion object {
        private const val PAGE_SIZE = 128
        private const val MIN_VECTOR_SCORE = 0.20f
        private const val MIN_LEXICAL_SCORE = 0.32f
        private const val MAX_QUERY_TERMS = 12
        private const val MAX_FILE_SOURCES = 12
        private const val MAX_RELATED_NODES = 24
        private const val MAX_EVIDENCE = 32
        private const val MAX_CONTEXT_CHARS = 24_000
    }
}
