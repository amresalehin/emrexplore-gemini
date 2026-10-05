package com.example.data.brain

import com.example.data.ai.ProviderType
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
    private val chunkDao: BrainChunkDao,
    private val nodeDao: BrainNodeDao,
    private val edgeDao: BrainEdgeDao,
    private val onDeviceEmbedding: OnDeviceEmbeddingEngine
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

        val localModel = onDeviceEmbedding.modelIdIfReady()
        val semanticHits = if (localModel != null) {
            val queryVector = runCatching { onDeviceEmbedding.embedText(clean) }.getOrNull()
            if (queryVector != null) {
                collectLocalHits(localModel, queryVector, limit * 3)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Lexical search remains useful for exact filenames, IDs, names, and other
        // terms that a semantic encoder can under-rank.
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

    private suspend fun collectLocalHits(
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
                val vector = BrainVectorCodec.fromJson(chunk.embeddingJson)
                if (vector.isEmpty() || vector.size != queryVector.size) continue
                val score = BrainVectorCodec.cosine(queryVector, vector)
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


