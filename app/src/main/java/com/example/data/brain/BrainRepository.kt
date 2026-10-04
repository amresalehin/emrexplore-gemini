package com.example.data.brain

import android.content.Context
import androidx.room.withTransaction
import com.example.data.ai.AnalysisResult
import com.example.data.ai.AiProviderClient
import com.example.data.ai.AttachedAiFile
import com.example.data.ai.AvailableAiModel
import com.example.data.ai.ConnectedDotsItem
import com.example.data.ai.ConnectionTestResult
import com.example.data.ai.ExtractedEntity
import com.example.data.ai.ProviderType
import com.example.data.ai.RagAnswer
import com.example.data.ai.isKeylessAiConfig
import com.example.data.local.AiProviderConfigEntity
import com.example.data.brain.BrainTopicEntity
import com.example.data.brain.BrainEdgeEntity
import com.example.data.brain.BrainNodeEntity
import com.example.data.brain.BrainChunkEntity
import com.example.data.local.IndexedFileEntity
import com.example.data.local.AppDatabase
import com.example.data.media.MediaMetadataRepository
import com.example.data.model.MediaItem
import com.example.data.metadata.MetadataWriter
import com.example.data.repository.FileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

data class BrainSyncResult(
    val total: Int,
    val indexed: Int,
    val skipped: Int,
    val failed: Int
)

class BrainRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getDatabase(appContext)
    private val brainNodeDao = db.brainNodeDao()
    private val brainEdgeDao = db.brainEdgeDao()
    private val brainTopicDao = db.brainTopicDao()
    private val brainChunkDao = db.brainChunkDao()
    private val brainDocumentDao = db.brainDocumentDao()
    private val brainRunDao = db.brainRunDao()
    private val aiConfigDao = db.aiProviderConfigDao()
    private val fileIndexDao = db.fileIndexDao()

    private val client = AiProviderClient()
    private val brainAi = DefaultBrainAiGateway(client)
    private val contentReader = BrainContentReader(appContext)
    private val indexer = BrainIndexer(
        context = appContext,
        documentDao = brainDocumentDao,
        chunkDao = brainChunkDao,
        nodeDao = brainNodeDao,
        edgeDao = brainEdgeDao,
        runDao = brainRunDao,
        client = brainAi
    )
    private val retriever = BrainRetriever(
        context = appContext,
        chunkDao = brainChunkDao,
        nodeDao = brainNodeDao,
        edgeDao = brainEdgeDao,
        client = brainAi
    )
    private val fileRepository = FileRepository(appContext)
    private val mediaMetadataRepository = MediaMetadataRepository(appContext)

    val allNodesFlow: Flow<List<BrainNodeEntity>> = brainNodeDao.observePreview().map { it }
    val allEdgesFlow: Flow<List<BrainEdgeEntity>> = brainNodeDao.observeEdges().map { it }
    val nodeCountFlow: Flow<Int> = brainNodeDao.countFlow()
    val edgeCountFlow: Flow<Int> = brainEdgeDao.countFlow()
    val chunkCountFlow: Flow<Int> = brainChunkDao.countFlow()

    val aiConfigFlow: Flow<AiProviderConfigEntity?> = aiConfigDao.getConfigFlow().map {
        it?.let(::decryptConfig)?.let(::normalizeAiConfig)
    }

    val brainTopicsFlow: Flow<List<BrainTopicEntity>> = brainTopicDao.getAllFlow().map { it }

    suspend fun getAiConfig(): AiProviderConfigEntity = withContext(Dispatchers.IO) {
        val stored = aiConfigDao.getConfig() ?: AiProviderConfigEntity()
        normalizeAiConfig(decryptConfig(stored))
    }

    suspend fun saveAiConfig(config: AiProviderConfigEntity) = withContext(Dispatchers.IO) {
        aiConfigDao.saveConfig(encryptConfig(normalizeAiConfig(config)))
    }

    suspend fun testConnection(config: AiProviderConfigEntity): ConnectionTestResult =
        client.testConnection(normalizeAiConfig(config))

    suspend fun listAiModels(config: AiProviderConfigEntity): List<AvailableAiModel> =
        client.listModels(normalizeAiConfig(config))

    suspend fun clearGraph() = withContext(Dispatchers.IO) {
        db.withTransaction {
            brainEdgeDao.clearAll()
            brainNodeDao.clearAll()
            brainChunkDao.clearAll()
            brainDocumentDao.clearAll()
            brainRunDao.clearAll()
        }
    }

    suspend fun getBrainCandidates(): List<IndexedFileEntity> = withContext(Dispatchers.IO) {
        fileIndexDao.getAllIndexedFilesForBrain()
    }

    suspend fun syncAll(
        force: Boolean = true,
        onProgress: suspend (current: Int, total: Int, path: String, outcome: BrainIndexOutcome) -> Unit = { _, _, _, _ -> }
    ): BrainSyncResult = withContext(Dispatchers.IO) {
        // Refresh the filesystem index first so newly created/moved files can be seen.
        fileRepository.indexStorage(force = true)

        val config = getAiConfig()
        val candidates = fileIndexDao.getAllIndexedFilesForBrain()
            .filter { it.extension.lowercase(Locale.US) in BrainContentReader.SUPPORTED_EXTENSIONS }
            .filterNot { it.name.startsWith(".") }
            .filterNot { it.path.split(File.separatorChar).any { segment -> segment == ".trash" } }

        val candidatePaths = candidates.map { it.path }.toHashSet()
        val existingBrainPaths = brainDocumentDao.getAllPaths()
        for (stalePath in existingBrainPaths.filterNot { it in candidatePaths }) {
            removeIndexedSource(stalePath)
        }

        var indexed = 0
        var skipped = 0
        var failed = 0

        for ((index, candidate) in candidates.withIndex()) {
            if (!File(candidate.path).isFile || !File(candidate.path).canRead()) {
                fileIndexDao.deleteByPath(candidate.path)
                val outcome = BrainIndexOutcome(false, error = "File is no longer readable")
                failed++
                onProgress(index + 1, candidates.size, candidate.path, outcome)
                continue
            }

            val outcome = indexer.index(File(candidate.path), config, force)
            when {
                outcome.success && outcome.skipped -> skipped++
                outcome.success -> indexed++
                else -> failed++
            }
            onProgress(index + 1, candidates.size, candidate.path, outcome)
        }

        brainNodeDao.deleteOrphans()
        brainNodeDao.recomputeDegrees()

        BrainSyncResult(
            total = candidates.size,
            indexed = indexed,
            skipped = skipped,
            failed = failed
        )
    }

    suspend fun indexFile(
        file: File,
        uri: android.net.Uri? = null,
        config: AiProviderConfigEntity,
        force: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        indexer.index(file, normalizeAiConfig(config), force).success
    }

    suspend fun indexPath(path: File, config: AiProviderConfigEntity) = withContext(Dispatchers.IO) {
        if (!path.exists()) return@withContext
        if (path.isFile) {
            indexFile(path, null, config, force = true)
            return@withContext
        }

        val visited = mutableSetOf<String>()
        val stack = ArrayDeque<File>()
        stack.add(path)

        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: dir.absolutePath
            if (!visited.add(canonical)) continue

            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.name.startsWith(".") || child.name == ".trash") continue
                if (child.isDirectory) {
                    stack.add(child)
                } else if (child.extension.lowercase(Locale.US) in BrainContentReader.SUPPORTED_EXTENSIONS) {
                    indexFile(child, null, config, force = true)
                }
            }
        }
        brainNodeDao.recomputeDegrees()
    }

    suspend fun indexPath(path: File) = indexPath(path, getAiConfig())

    suspend fun onFileRenamed(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        removeIndexedSource(oldPath)
        indexPath(File(newPath), getAiConfig())
    }

    suspend fun removeIndexedSource(filePath: String) = withContext(Dispatchers.IO) {
        val paths = brainDocumentDao.getPathsUnder(filePath, filePath).ifEmpty { listOf(filePath) }
        db.withTransaction {
            for (path in paths) {
                val fileNodeId = fileNodeId(path)
                brainEdgeDao.deleteForFile(path, fileNodeId)
                brainChunkDao.deleteForFile(path)
                brainNodeDao.deleteFileNode(path)
                brainDocumentDao.delete(path)
            }
        }
        brainNodeDao.deleteOrphans()
        brainNodeDao.recomputeDegrees()
    }

    suspend fun recomputeGraphDegrees() = withContext(Dispatchers.IO) {
        brainNodeDao.deleteOrphans()
        brainNodeDao.recomputeDegrees()
    }

    suspend fun saveBrainTopic(
        id: String?,
        heading: String,
        description: String
    ): BrainTopicEntity = withContext(Dispatchers.IO) {
        val cleanHeading = heading.trim()
        require(cleanHeading.isNotBlank()) { "Topic heading cannot be blank" }
        val cleanDescription = description.trim()
        val now = System.currentTimeMillis()
        val existing = id?.let { brainTopicDao.get(it) }
        val topic = BrainTopicEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            heading = cleanHeading,
            description = cleanDescription,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        brainTopicDao.insertOrUpdate(topic)
        topic
    }

    suspend fun deleteBrainTopic(id: String) = withContext(Dispatchers.IO) {
        brainTopicDao.delete(id)
    }

    suspend fun getRelevantFilesForBrainTopic(
        topic: BrainTopicEntity,
        config: AiProviderConfigEntity,
        limit: Int = 12
    ): List<BrainTopicFile> = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext emptyList()
        val query = listOf(topic.heading, topic.description)
            .filter { it.isNotBlank() }
            .joinToString(". ")
        if (query.isBlank()) return@withContext emptyList()

        val retrieval = retriever.retrieve(query, normalizeAiConfig(config), limit.coerceAtLeast(8))
        val bestPaths = retrieval.hits
            .groupBy { it.chunk.filePath }
            .mapValues { (_, hits) -> hits.maxOf { it.score } }
            .entries
            .sortedByDescending { it.value }
            .take(limit)

        val nodes = brainNodeDao.getByFilePaths(bestPaths.map { it.key })
            .filter { it.nodeType == "DOCUMENT" || it.nodeType == "IMAGE" }
            .associateBy { it.sourceFilePath.orEmpty() }

        bestPaths.mapNotNull { entry ->
            nodes[entry.key]?.let { BrainTopicFile(it, entry.value) }
        }
    }

    suspend fun getConnectedDotsForFile(filePath: String): List<ConnectedDotsItem> = withContext(Dispatchers.IO) {
        val fileNode = brainNodeDao.getByFilePath(filePath) ?: return@withContext emptyList()
        val edges = brainEdgeDao.forNode(fileNode.id)
        val nodeIds = edges.flatMap { listOf(it.sourceNodeId, it.targetNodeId) }.distinct()
        val nodes = brainNodeDao.getByIds(nodeIds).associateBy { it.id }

        val out = mutableListOf<ConnectedDotsItem>()
        val seenFiles = mutableSetOf(fileNode.id)
        for (edge in edges.sortedByDescending { it.weight }) {
            val neighborId = if (edge.sourceNodeId == fileNode.id) edge.targetNodeId else edge.sourceNodeId
            val neighbor = nodes[neighborId] ?: continue

            if (neighbor.nodeType == "DOCUMENT" || neighbor.nodeType == "IMAGE") {
                if (seenFiles.add(neighbor.id)) {
                    out += ConnectedDotsItem(
                        fileNode = neighbor,
                        relationship = edge.relation,
                        targetNode = fileNode,
                        snippet = edge.evidenceSnippet
                    )
                }
                continue
            }

            val secondHopEdges = brainEdgeDao.forNode(neighbor.id)
            for (secondHop in secondHopEdges.sortedByDescending { it.weight }.take(8)) {
                val otherId = if (secondHop.sourceNodeId == neighbor.id) secondHop.targetNodeId else secondHop.sourceNodeId
                if (!seenFiles.add(otherId)) continue
                val other = brainNodeDao.get(otherId) ?: continue
                if (other.nodeType == "DOCUMENT" || other.nodeType == "IMAGE") {
                    out += ConnectedDotsItem(
                        fileNode = other,
                        relationship = "Connected via " + neighbor.label,
                        targetNode = neighbor,
                        snippet = secondHop.evidenceSnippet
                    )
                }
                if (out.size >= 24) break
            }
            if (out.size >= 24) break
        }
        out.take(24)
    }

    suspend fun getSmartSuggestions(): List<String> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<String>()
        brainNodeDao.recentFiles(6).forEach { node ->
            suggestions += if (node.nodeType == "IMAGE") "Details for " + node.label else "Summarize " + node.label
        }
        brainNodeDao.byType("LOCATION", 3).forEach { node ->
            suggestions += "Photos from " + node.label
        }
        brainNodeDao.byType("DEVICE", 3).forEach { node ->
            suggestions += "Taken with " + node.label
        }
        suggestions.distinct().take(12)
    }

    suspend fun enrichGalleryImage(
        file: File,
        uri: android.net.Uri?,
        config: AiProviderConfigEntity,
        force: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile || !BrainContentReader.IMAGE_EXTENSIONS.contains(file.extension.lowercase(Locale.US))) {
            return@withContext false
        }

        val normalized = normalizeAiConfig(config)
        val input = runCatching { contentReader.read(file, normalized) }.getOrNull() ?: return@withContext false
        val metadata = mediaMetadataRepository.getFreshAiEnrichment(
            file.absolutePath,
            file.length(),
            file.lastModified(),
            normalized.visionModel
        )

        if (!force && metadata != null) return@withContext true

        val analysis = if (
            normalized.isEnabled &&
            (isKeylessAiConfig(normalized) || normalized.apiKey.isNotBlank())
        ) {
            client.analyzeImage(input.imageBase64, input.metadataSummary, file.name, normalized)
        } else {
            AnalysisResult(
                summary = input.metadataSummary.ifBlank { "Image " + file.name },
                tags = input.metadata?.summary?.keywords.orEmpty().take(8)
            )
        }

        val item = MediaItem(
            id = file.absolutePath.hashCode().toLong(),
            uri = uri ?: android.net.Uri.fromFile(file),
            name = file.name,
            path = file.absolutePath,
            size = file.length(),
            dateAdded = file.lastModified(),
            mimeType = input.mimeType,
            isVideo = false
        )
        val tagsJson = JSONArray(analysis.tags).toString()
        val entitiesJson = JSONArray().apply {
            analysis.entities.forEach {
                put(JSONObject().apply {
                    put("name", it.name.trim())
                    put("type", it.type.trim().uppercase(Locale.US))
                    put("confidence", it.confidence)
                })
            }
        }.toString()
        val relationsJson = JSONArray().apply {
            analysis.relations.forEach {
                put(JSONObject().apply {
                    put("source", it.source.trim())
                    put("relation", it.relation.trim())
                    put("target", it.target.trim())
                    put("evidence", it.evidence.trim())
                })
            }
        }.toString()

        MetadataWriter.writeAiMetadata(file, analysis.summary.trim(), analysis.tags)
        mediaMetadataRepository.getOrRead(item, requireOriginalLocation = false)
        mediaMetadataRepository.saveAiEnrichment(
            item = item,
            caption = analysis.summary.trim(),
            tagsJson = tagsJson,
            entitiesJson = entitiesJson,
            relationsJson = relationsJson,
            model = normalized.visionModel
        )
        true
    }

    suspend fun queryFileSpecifically(
        file: File,
        question: String,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): RagAnswer = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        if (!file.exists() || !file.isFile || !file.canRead()) {
            return@withContext RagAnswer(
                answer = "Could not read the attached file \"" + file.name + "\".",
                isSuccessful = false,
                latencyMs = System.currentTimeMillis() - started
            )
        }

        val config = getAiConfig()
        val input = try {
            contentReader.read(file, config)
        } catch (error: Exception) {
            return@withContext RagAnswer(
                answer = "Could not read the attached file \"" + file.name + "\": " + (error.message ?: "I/O error"),
                isSuccessful = false,
                latencyMs = System.currentTimeMillis() - started
            )
        }

        val isAiReady = config.isEnabled && (isKeylessAiConfig(config) || config.apiKey.isNotBlank())
        return@withContext try {
            val answer = if (isAiReady) {
                client.chatAboutFile(
                    question = question.trim(),
                    fileName = file.name,
                    fileContent = if (input.isImage) null else input.text,
                    base64Jpeg = input.imageBase64,
                    metadataSummary = input.metadataSummary,
                    chatHistory = chatHistory.filter { it.first.isNotBlank() && it.second.isNotBlank() },
                    config = config
                )
            } else {
                localFileAnswer(file, input)
            }

            val node = brainNodeDao.getByFilePath(file.absolutePath) ?: BrainNodeEntity(
                id = "file:" + stableKey(file.absolutePath),
                label = file.name,
                nodeType = if (input.isImage) "IMAGE" else "DOCUMENT",
                sourceFilePath = file.absolutePath,
                summary = input.text.take(500).ifBlank { input.metadataSummary.take(500) }
            )
            RagAnswer(
                answer = answer,
                connectedNodes = listOf(node),
                isSuccessful = true,
                latencyMs = System.currentTimeMillis() - started
            )
        } catch (error: Exception) {
            RagAnswer(
                answer = localFileAnswer(file, input) + "\n\nAI service error: " + (error.message ?: "unknown error"),
                connectedNodes = brainNodeDao.getByFilePath(file.absolutePath)?.let { listOf(it) }.orEmpty(),
                isSuccessful = false,
                latencyMs = System.currentTimeMillis() - started
            )
        }
    }

    suspend fun queryRag(
        question: String,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): RagAnswer = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val clean = question.trim()
        if (clean.isBlank()) {
            return@withContext RagAnswer("Please enter a question.", isSuccessful = false)
        }

        val config = getAiConfig()
        val retrieval = retriever.retrieve(clean, config, 8)
        if (!retrieval.hasMatch) {
            return@withContext RagAnswer(
                answer = "No indexed Brain sources matched \"" + clean + "\". Sync Brain first or attach a specific file.",
                isSuccessful = false,
                latencyMs = System.currentTimeMillis() - started
            )
        }

        val (context, evidence) = retriever.buildContext(retrieval, config)
        if (context.isBlank()) {
            return@withContext RagAnswer(
                answer = "The Brain index matched sources, but their files are no longer readable. Sync Brain to refresh the index.",
                sourceChunks = retrieval.hits.map { it.chunk },
                connectedNodes = retrieval.relatedNodes,
                isSuccessful = false,
                latencyMs = System.currentTimeMillis() - started
            )
        }

        val answer: String
        val successful: Boolean
        if (isAiReady(config)) {
            try {
                answer = client.generateRagAnswer(
                    question = clean,
                    contextText = context,
                    graphContext = evidence,
                    chatHistory = chatHistory,
                    config = config
                )
                successful = true
            } catch (_: Exception) {
                answer = localRagAnswer(retrieval)
                successful = false
            }
        } else {
            answer = localRagAnswer(retrieval)
            successful = true
        }

        RagAnswer(
            answer = answer,
            sourceChunks = retrieval.hits.map { it.chunk },
            connectedNodes = retrieval.relatedNodes,
            isSuccessful = successful,
            latencyMs = System.currentTimeMillis() - started
        )
    }

    private fun localFileAnswer(file: File, content: BrainFileContent): String {
        return buildString {
            append("### Attached File: ").append(file.name).append("\n\n")
            if (content.metadataSummary.isNotBlank()) {
                append(content.metadataSummary).append("\n\n")
            }
            if (content.text.isNotBlank()) {
                val clean = content.text.replace(Regex("\\s+"), " ").trim()
                append("Excerpt:\n").append(clean.take(900))
            } else {
                append("No text content was extracted locally.")
            }
        }
    }

    private fun localRagAnswer(retrieval: BrainRetrieval): String {
        val sb = StringBuilder("Matching Brain sources:\n\n")
        retrieval.hits.take(6).forEach { hit ->
            val file = File(hit.chunk.filePath)
            sb.append("• ").append(file.name).append(" — ")
                .append(hit.chunk.content.replace(Regex("\\s+"), " ").trim().take(260))
                .append('\n')
        }
        if (retrieval.evidence.isNotEmpty()) {
            sb.append("\nConnections:\n")
            retrieval.evidence.take(6).forEach { sb.append("• ").append(it).append('\n') }
        }
        return sb.toString().trim()
    }

    private fun isAiReady(config: AiProviderConfigEntity): Boolean =
        config.isEnabled && (isKeylessAiConfig(config) || config.apiKey.isNotBlank())

    private fun normalizeAiConfig(config: AiProviderConfigEntity): AiProviderConfigEntity {
        val provider = ProviderType.fromString(config.providerType)
        val explicitTextEmbedding = config.textEmbeddingModel.trim()
        val legacyEmbedding = config.embeddingModel.trim()
        val textEmbedding = explicitTextEmbedding.ifBlank { legacyEmbedding }
        return config.copy(
            providerType = provider.name,
            textEmbeddingModel = textEmbedding,
            embeddingModel = legacyEmbedding
        )
    }

    private fun decryptConfig(config: AiProviderConfigEntity): AiProviderConfigEntity =
        config.copy(apiKey = com.example.data.security.ApiKeyProtector.decrypt(config.apiKey))

    private fun encryptConfig(config: AiProviderConfigEntity): AiProviderConfigEntity =
        config.copy(apiKey = com.example.data.security.ApiKeyProtector.encrypt(config.apiKey))

    private fun fileNodeId(path: String): String = "file:" + stableKey(path)

    private fun stableKey(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}
