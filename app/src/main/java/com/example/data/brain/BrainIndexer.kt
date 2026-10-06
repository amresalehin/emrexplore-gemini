package com.example.data.brain

import androidx.room.withTransaction

import com.example.data.local.AppDatabase
import com.example.data.ai.AnalysisResult
import com.example.data.ai.ExtractedEntity
import com.example.data.ai.ExtractedRelation
import com.example.data.ai.ProviderType
import com.example.data.ai.EmbeddingProviderType
import com.example.data.ai.VectorDatabaseType
import com.example.data.ai.isKeylessAiConfig
import com.example.data.local.AiProviderConfigEntity
import java.io.File
import java.util.Locale

data class BrainIndexOutcome(
    val success: Boolean,
    val skipped: Boolean = false,
    val error: String? = null
)

class BrainIndexer(
    private val context: android.content.Context,
    private val documentDao: BrainDocumentDao,
    private val chunkDao: BrainChunkDao,
    private val nodeDao: BrainNodeDao,
    private val edgeDao: BrainEdgeDao,
    private val edgeEvidenceDao: BrainEdgeEvidenceDao,
    private val runDao: BrainRunDao,
    private val imageProfileDao: BrainImageProfileDao,
    private val client: BrainAiGateway,
    private val embeddingClient: com.example.data.ai.AiProviderClient,
    private val db: AppDatabase,
    private val onDeviceEmbedding: OnDeviceEmbeddingEngine,
    private val vectorSyncDao: BrainVectorSyncDao
) {
    private fun vectorStore(config: AiProviderConfigEntity): BrainVectorStore =
        vectorStoreFor(config, chunkDao)
    private val reader = BrainContentReader(context)

    suspend fun index(file: File, config: AiProviderConfigEntity, force: Boolean = false): BrainIndexOutcome {
        val startedAt = System.currentTimeMillis()
        val path = file.absolutePath
        if (!file.exists() || !file.isFile || !file.canRead()) {
            return BrainIndexOutcome(false, error = "File is not readable")
        }

        val extension = file.extension.lowercase(Locale.US)
        val pathSegments = path.split(File.separatorChar)
        if (file.name.startsWith(".") || pathSegments.any { it == ".trash" }) {
            return BrainIndexOutcome(true, skipped = true)
        }
        if (extension !in BrainContentReader.SUPPORTED_EXTENSIONS) {
            return BrainIndexOutcome(true, skipped = true)
        }

        val initialSize = file.length()
        val initialModified = file.lastModified()
        val existing = documentDao.get(path)
        if (!force &&
            existing != null &&
            existing.state == BrainIndexStates.READY &&
            existing.size == file.length() &&
            existing.lastModified == file.lastModified() &&
            chunkDao.getForFile(path).any { it.embeddingJson.isNotBlank() }
        ) {
            return BrainIndexOutcome(true, skipped = true)
        }

        return try {
            val input = reader.read(file, config)
            val analysis = if (input.isImage) {
                val profile = imageProfileDao.get(path)
                    ?: return fail(path, "Gallery AI must analyze this image before Brain can consume it", startedAt)
                analysisFromImageProfile(profile)
            } else {
                existing?.let(::analysisFromDocument)
                    ?: return fail(path, "File AI must analyze this document before Brain can consume it", startedAt)
            }
            val chunkTexts = buildChunkTexts(input, analysis)
            if (input.text.isNotBlank() && chunkTexts.isEmpty()) {
                return fail(path, "No indexable chunks could be created", startedAt)
            }

            val (embeddings, embeddingModel) = embed(chunkTexts, config)
            val imageEmbedding = if (input.isImage && config.multimodalEmbeddingModel.trim().isNotBlank()) {
                embeddingClient.embedMultimodalDocument(input.imageBase64, config)
                    ?: throw IllegalStateException("Configured image embedding model returned no image vector")
            } else null

            val now = System.currentTimeMillis()
            val fileNode = BrainNodeEntity(
                id = BrainIdentity.fileNodeId(path),
                label = file.name,
                nodeType = if (input.isImage) "IMAGE" else "DOCUMENT",
                sourceFilePath = path,
                thumbnailUri = if (input.isImage) "file://" + path else null,
                summary = analysis.summary.trim().take(MAX_SUMMARY_CHARS),
                confidence = 1f,
                updatedAt = now
            )

            val nodes = linkedMapOf<String, BrainNodeEntity>()
            nodes[fileNode.id] = fileNode
            val edges = linkedMapOf<String, BrainEdgeEntity>()
            val edgeEvidence = linkedMapOf<String, BrainEdgeEvidenceEntity>()

            fun recordEdge(edge: BrainEdgeEntity) {
                if (edge.sourceNodeId == edge.targetNodeId) return
                addEdge(edges, edge)
                edgeEvidence.putIfAbsent(
                    edge.sourceNodeId + "|" + edge.targetNodeId + "|" + edge.relation + "|" + path,
                    BrainEdgeEvidenceEntity(
                        sourceNodeId = edge.sourceNodeId,
                        targetNodeId = edge.targetNodeId,
                        relation = edge.relation,
                        evidenceSource = path,
                        evidenceSnippet = edge.evidenceSnippet,
                        createdAt = edge.createdAt
                    )
                )
            }

            val entities = LinkedHashMap<String, BrainNodeEntity>()
            fun entityNode(name: String, type: String, confidence: Float = 0.6f): BrainNodeEntity {
                val cleanName = name.trim()
                val cleanType = normalizeType(type)
                val id = BrainIdentity.entityNodeId(cleanType, cleanName)
                return entities.getOrPut(id) {
                    BrainNodeEntity(
                        id = id,
                        label = cleanName,
                        nodeType = cleanType,
                        summary = "Mentioned in " + file.name,
                        confidence = confidence.coerceIn(0f, 1f),
                        updatedAt = now
                    )
                }
            }

            for (entity in analysis.entities) {
                val clean = entity.name.trim()
                if (clean.isBlank()) continue
                val node = entityNode(clean, entity.type, entity.confidence)
                nodes[node.id] = node
                recordEdge(
                    BrainEdgeEntity(
                        sourceNodeId = fileNode.id,
                        targetNodeId = node.id,
                        relation = if (input.isImage) "DEPICTS" else "MENTIONS",
                        weight = entity.confidence.coerceIn(0.1f, 1f),
                        evidenceSnippet = "Referenced by " + file.name,
                        evidenceSource = path
                    )
                )
            }

            for (relation in analysis.relations) {
                val relationSource = relation.source.trim()
                val relationTarget = relation.target.trim()
                if (relationTarget.isBlank()) continue

                val sourceId = if (sameFileName(relationSource, file)) {
                    fileNode.id
                } else {
                    val source = entityNode(relationSource, "TOPIC")
                    nodes[source.id] = source
                    source.id
                }

                val target = entityNode(relationTarget, typeFromRelation(relation))
                nodes[target.id] = target

                val relationName = relation.relation.trim().uppercase(Locale.US).ifBlank { "ASSOCIATED_WITH" }
                recordEdge(
                    BrainEdgeEntity(
                        sourceNodeId = sourceId,
                        targetNodeId = target.id,
                        relation = relationName,
                        weight = 0.7f,
                        evidenceSnippet = relation.evidence.trim().take(320),
                        evidenceSource = path
                    )
                )
            }

            // Exact location remains a local-only graph feature. It is never required
            // for cloud indexing and is not part of the canonical vector text.
            if (input.isImage && ProviderType.fromString(config.providerType) == ProviderType.OLLAMA) {
                val meta = input.metadata?.summary
                if (meta?.latitude != null && meta.longitude != null) {
                    meta.city?.takeIf { it.isNotBlank() }?.let { city ->
                        val cityNode = entityNode(city, "LOCATION", 0.9f)
                        nodes[cityNode.id] = cityNode.copy(
                            summary = "Shared city/region metadata from indexed photos.",
                            confidence = maxOf(cityNode.confidence, 0.9f)
                        )
                        recordEdge(
                            BrainEdgeEntity(
                                sourceNodeId = fileNode.id,
                                targetNodeId = cityNode.id,
                                relation = "LOCATED_IN",
                                weight = 0.9f,
                                evidenceSnippet = "City: " + city,
                                evidenceSource = path
                            )
                        )
                    }

                    val coordinateKey = meta.latitude.toString() + "," + meta.longitude.toString()
                    val exactLocation = entityNode(coordinateKey, "LOCATION_EXACT", 1f)
                    nodes[exactLocation.id] = exactLocation.copy(
                        label = "Exact location",
                        summary = "Exact coordinates: " + coordinateKey,
                        confidence = 1f
                    )
                    recordEdge(
                        BrainEdgeEntity(
                            sourceNodeId = fileNode.id,
                            targetNodeId = exactLocation.id,
                            relation = "LOCATED_AT",
                            weight = 1f,
                            evidenceSnippet = "Exact coordinates: " + coordinateKey,
                            evidenceSource = path
                        )
                    )
                }
            }

            val chunks = chunkTexts.mapIndexed { index, text ->
                BrainChunkEntity(
                    id = BrainIdentity.chunkId(path, index),
                    filePath = path,
                    chunkIndex = index,
                    content = text,
                    embeddingJson = BrainVectorCodec.toJson(embeddings[index]),
                    embeddingModel = embeddingModel,
                    imageEmbeddingJson = if (input.isImage && index == 0) imageEmbedding?.let(BrainVectorCodec::toJson).orEmpty() else "",
                    imageEmbeddingModel = if (input.isImage && index == 0) config.multimodalEmbeddingModel.trim() else "",
                    locator = if (input.isImage) file.name else "chunk-" + index,
                    pageNumber = null,
                    indexedAt = now
                )
            }

            // Everything above is staged in memory. No existing Brain data is touched
            // until every chunk has a valid vector and a complete graph representation.
            if (file.length() != initialSize || file.lastModified() != initialModified) {
                return fail(path, "File changed during Brain indexing; retrying from a fresh snapshot", startedAt)
            }

            val document = BrainDocumentEntity(
                path = path,
                name = file.name,
                mimeType = input.mimeType,
                size = file.length(),
                lastModified = file.lastModified(),
                contentHash = sha256(file),
                aiSummary = existing?.aiSummary.orEmpty(),
                aiTagsJson = existing?.aiTagsJson ?: "[]",
                aiEntitiesJson = existing?.aiEntitiesJson ?: "[]",
                aiRelationsJson = existing?.aiRelationsJson ?: "[]",
                aiModel = existing?.aiModel.orEmpty(),
                aiUpdatedAt = existing?.aiUpdatedAt ?: 0L,
                modelSignature = embeddingModel,
                state = BrainIndexStates.READY,
                error = null,
                indexedAt = now
            )

            val previousChunks = chunkDao.getForFile(path)
            val newIds = chunks.map { it.id }.toSet()
            val removedIds = previousChunks.map { it.id }.filterNot { it in newIds }
            val vectorSyncOperations = if (VectorDatabaseType.fromString(config.vectorDatabaseType) == VectorDatabaseType.QDRANT) {
                buildList {
                    chunks.filter { it.embeddingJson.isNotBlank() || it.imageEmbeddingJson.isNotBlank() }.forEach { chunk ->
                        add(
                            BrainVectorSyncOperationEntity(
                                id = java.util.UUID.randomUUID().toString(),
                                operation = BrainVectorSyncOperations.UPSERT,
                                chunkId = chunk.id
                            )
                        )
                    }
                    removedIds.forEach { id ->
                        add(
                            BrainVectorSyncOperationEntity(
                                id = java.util.UUID.randomUUID().toString(),
                                operation = BrainVectorSyncOperations.DELETE,
                                chunkId = id
                            )
                        )
                    }
                }
            } else {
                emptyList()
            }
            withTransaction {
                edgeEvidenceDao.deleteForFile(path)
                edgeEvidenceDao.refreshRepresentatives()
                edgeEvidenceDao.deleteEdgesWithoutEvidence()
                edgeDao.deleteForFileNode(fileNode.id)
                chunkDao.deleteForFile(path)
                nodeDao.deleteFileNode(path)
                documentDao.delete(path)

                if (nodes.isNotEmpty()) nodeDao.insertAll(nodes.values.toList())
                if (edges.isNotEmpty()) edgeDao.insertAll(edges.values.toList())
                if (edgeEvidence.isNotEmpty()) edgeEvidenceDao.insertAll(edgeEvidence.values.toList())
                if (chunks.isNotEmpty()) chunkDao.insertAll(chunks)
                if (vectorSyncOperations.isNotEmpty()) vectorSyncDao.insertAll(vectorSyncOperations)
                documentDao.insert(document)
            }

            if (vectorSyncOperations.isNotEmpty()) {
                BrainVectorSyncWorker.enqueue(context)
            }

            runDao.insert(
                BrainRunEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    filePath = path,
                    operation = "INDEX",
                    success = true,
                    startedAt = startedAt,
                    finishedAt = System.currentTimeMillis()
                )
            )
            BrainIndexOutcome(true)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            fail(path, error.message ?: "Brain indexing failed", startedAt)
        }
    }

    private suspend fun withTransaction(block: suspend () -> Unit) {
        db.withTransaction { block() }
    }

    private suspend fun fail(path: String, message: String, startedAt: Long): BrainIndexOutcome {
        runDao.insert(
            BrainRunEntity(
                id = java.util.UUID.randomUUID().toString(),
                filePath = path,
                operation = "INDEX",
                success = false,
                error = message.take(500),
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis()
            )
        )
        return BrainIndexOutcome(false, error = message.take(500))
    }

    private fun analysisFromDocument(document: BrainDocumentEntity): AnalysisResult {
        val entities = runCatching {
            val array = org.json.JSONArray(document.aiEntitiesJson)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isNotBlank()) add(ExtractedEntity(name, item.optString("type", "TOPIC"), item.optDouble("confidence", 1.0).toFloat()))
                }
            }
        }.getOrDefault(emptyList())
        val relations = runCatching {
            val array = org.json.JSONArray(document.aiRelationsJson)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val source = item.optString("source").trim()
                    val target = item.optString("target").trim()
                    if (source.isNotBlank() && target.isNotBlank()) {
                        add(ExtractedRelation(source, item.optString("relation", "ASSOCIATED_WITH"), target, item.optString("evidence")))
                    }
                }
            }
        }.getOrDefault(emptyList())
        val tags = runCatching {
            val array = org.json.JSONArray(document.aiTagsJson)
            buildList { for (i in 0 until array.length()) array.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add) }
        }.getOrDefault(emptyList())
        return AnalysisResult(document.aiSummary.ifBlank { document.name }, entities, relations, tags)
    }

    private fun analysisFromImageProfile(profile: BrainImageProfileEntity): AnalysisResult {
        val entities = runCatching {
            val array = org.json.JSONArray(profile.entitiesJson)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isNotBlank()) add(ExtractedEntity(name, item.optString("type", "TOPIC"), item.optDouble("confidence", 1.0).toFloat()))
                }
            }
        }.getOrDefault(emptyList())
        val relations = runCatching {
            val array = org.json.JSONArray(profile.relationsJson)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val source = item.optString("source").trim()
                    val target = item.optString("target").trim()
                    if (source.isNotBlank() && target.isNotBlank()) add(ExtractedRelation(source, item.optString("relation", "ASSOCIATED_WITH"), target, item.optString("evidence")))
                }
            }
        }.getOrDefault(emptyList())
        val tags = runCatching {
            val array = org.json.JSONArray(profile.tagsJson)
            buildList { for (i in 0 until array.length()) array.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add) }
        }.getOrDefault(emptyList())
        return AnalysisResult(profile.description.ifBlank { profile.fileName }, entities, relations, tags)
    }

    private fun buildChunkTexts(input: BrainFileContent, analysis: AnalysisResult): List<String> {
        return if (input.isImage) {
            listOf(
                buildString {
                    append("Image: ").append(input.file.name).append('\n')
                    append("Local OCR: ").append(input.ocrText.take(20_000).ifBlank { "<none>" }).append('\n')
                    append("Description: ").append(analysis.summary.trim()).append('\n')
                    if (analysis.tags.isNotEmpty()) append("Tags: ").append(analysis.tags.joinToString(", ")).append('\n')
                    if (analysis.entities.isNotEmpty()) append("Entities: ").append(analysis.entities.joinToString(", ") { it.name }).append('\n')
                    if (analysis.relations.isNotEmpty()) append("Relations: ").append(analysis.relations.joinToString("; ") { it.source + " " + it.relation + " " + it.target }).append('\n')
                    append("Metadata: ").append(input.metadataSummary)
                }.take(MAX_CHUNK_CHARS)
            )
        } else {
            val chunks = BrainChunker.chunk(input.text, MAX_CHUNK_CHARS, CHUNK_OVERLAP, MAX_CHUNKS).toMutableList()
            if (chunks.isNotEmpty()) {
                val aiContext = buildString {
                    append("File AI summary: ").append(analysis.summary.trim()).append('\n')
                    if (analysis.tags.isNotEmpty()) append("File AI tags: ").append(analysis.tags.joinToString(", ")).append('\n')
                    if (analysis.entities.isNotEmpty()) append("File AI entities: ").append(analysis.entities.joinToString(", ") { it.name }).append('\n')
                    if (analysis.relations.isNotEmpty()) append("File AI relations: ").append(
                        analysis.relations.joinToString("; ") { it.source + " " + it.relation + " " + it.target }
                    )
                }.trim()
                if (aiContext.isNotBlank()) {
                    chunks[0] = (aiContext + "\n\n" + chunks[0]).take(MAX_CHUNK_CHARS)
                }
            }
            chunks
        }
    }

    private fun buildImageProfile(input: BrainFileContent, analysis: AnalysisResult, config: AiProviderConfigEntity, imageEmbeddingReady: Boolean): BrainImageProfileEntity {
        val tagsJson = org.json.JSONArray().apply { analysis.tags.take(50).forEach { put(it) } }.toString()
        val entitiesJson = org.json.JSONArray().apply {
            analysis.entities.take(50).forEach { put(org.json.JSONObject().put("name", it.name).put("type", it.type).put("confidence", it.confidence)) }
        }.toString()
        val relationsJson = org.json.JSONArray().apply {
            analysis.relations.take(50).forEach { put(org.json.JSONObject().put("source", it.source).put("relation", it.relation).put("target", it.target).put("evidence", it.evidence)) }
        }.toString()
        return BrainImageProfileEntity(
            filePath = input.file.absolutePath,
            fileName = input.file.name,
            ocrText = input.ocrText,
            metadataSummary = input.metadataSummary,
            description = analysis.summary.trim().take(MAX_SUMMARY_CHARS),
            tagsJson = tagsJson,
            entitiesJson = entitiesJson,
            relationsJson = relationsJson,
            visionModel = config.visionModel.trim().ifBlank { "local-fallback" },
            imageEmbeddingModel = if (imageEmbeddingReady) config.multimodalEmbeddingModel.trim() else ""
        )
    }

    private suspend fun embed(texts: List<String>, config: AiProviderConfigEntity): Pair<List<FloatArray>, String> {
        if (texts.isEmpty()) return emptyList<FloatArray>() to onDeviceEmbedding.modelIdIfReady().orEmpty()

        if (EmbeddingProviderType.fromString(config.embeddingProviderType) != EmbeddingProviderType.OFFLINE) {
            throw IllegalStateException("Only the offline embedding engine is supported")
        }

        return run {
                if (!onDeviceEmbedding.isReady()) {
                    throw IllegalStateException("Download the selected on-device embedding model before indexing")
                }
                val vectors = try {
                    onDeviceEmbedding.embedTextPassages(texts) ?: emptyList()
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    throw IllegalStateException(
                        "On-device embedding failed: " + (error.message ?: "inference error"),
                        error
                    )
                }
                val modelId = onDeviceEmbedding.modelIdIfReady()
                    ?: throw IllegalStateException("Selected on-device embedding model is unavailable")
                vectors to modelId
        }.also { (vectors, _) ->
            if (vectors.size != texts.size || vectors.any { it.isEmpty() }) {
                throw IllegalStateException("Embedding provider returned an incomplete embedding batch")
            }
        }
    }

    private fun localAnalysis(input: BrainFileContent): AnalysisResult {
        val text = if (input.isImage) {
            val summary = buildString {
                append("Image ").append(input.file.name)
                input.metadata?.summary?.let { meta ->
                    meta.make?.takeIf { it.isNotBlank() }?.let { append(" captured with ").append(it) }
                    meta.model?.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
                    meta.dateTimeOriginal?.takeIf { it.isNotBlank() }?.let { append(" on ").append(it) }
                }
                append(".")
            }
            summary
        } else {
            input.text.replace(Regex("\\s+"), " ").trim()
        }

        val tags = tokenize(text)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }

        val entities = text.split(Regex("[^\\p{L}\\p{N}_-]+"))
            .map { it.trim() }
            .filter { it.length in 3..40 }
            .filter { it.firstOrNull()?.isUpperCase() == true }
            .distinctBy { it.lowercase(Locale.US) }
            .take(8)
            .map { ExtractedEntity(it, "TOPIC", 0.5f) }

        val summary = if (text.isBlank()) "No extractable content." else text.take(320)
        return AnalysisResult(summary, entities, emptyList<ExtractedRelation>(), tags)
    }

    private fun addEdge(edges: MutableMap<String, BrainEdgeEntity>, edge: BrainEdgeEntity) {
        if (edge.sourceNodeId == edge.targetNodeId) return
        edges.putIfAbsent(edgeKey(edge), edge)
    }

    private fun edgeKey(edge: BrainEdgeEntity): String =
        edge.sourceNodeId + "|" + edge.targetNodeId + "|" + edge.relation

    private fun sameFileName(value: String, file: File): Boolean {
        return BrainIdentity.normalize(value) == BrainIdentity.normalize(file.name) ||
            BrainIdentity.normalize(value) == BrainIdentity.normalize(file.absolutePath)
    }

    private fun typeFromRelation(relation: ExtractedRelation): String {
        val relationName = relation.relation.uppercase(Locale.US)
        return if (relationName.contains("LOCAT")) "LOCATION" else "TOPIC"
    }

    private fun normalizeType(type: String): String =
        type.trim().uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9_]"), "_")
            .ifBlank { "TOPIC" }

    private fun modelSignature(config: AiProviderConfigEntity): String =
        MODEL_VERSION
    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase(Locale.US)
            .split(Regex("[^\\p{L}\\p{N}_-]+"))
            .filter { it.length >= 3 }
            .filter { it !in STOP_WORDS }

    companion object {
        const val MODEL_VERSION = "brain-v6-multimodal"
        const val MAX_CHUNK_CHARS = 1600
        const val CHUNK_OVERLAP = 240
        const val MAX_CHUNKS = 120
        const val MAX_SUMMARY_CHARS = 1200

        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "this", "that", "from", "have", "has",
            "are", "was", "were", "will", "would", "could", "should", "about",
            "into", "over", "under", "then", "than", "how", "why", "who", "which",
            "your", "you", "our", "their", "they", "there", "what", "when", "where"
        )
    }
}
