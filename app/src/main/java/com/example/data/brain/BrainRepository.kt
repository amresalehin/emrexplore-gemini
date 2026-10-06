package com.example.data.brain

import android.content.Context
import androidx.room.withTransaction
import com.example.data.ai.AiProviderClient
import com.example.data.ai.AvailableAiModel
import com.example.data.ai.ConnectionTestResult
import com.example.data.ai.ProviderType
import com.example.data.ai.EmbeddingProviderType
import com.example.data.ai.isKeylessAiConfig
import com.example.data.local.AiProviderConfigEntity
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

data class BrainSyncResult(
    val total: Int,
    val indexed: Int,
    val skipped: Int,
    val failed: Int
)

class BrainRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getDatabase(appContext)
    private val brainNodeDao = db.brainNodeDao()
    private val brainEdgeDao = db.brainEdgeDao()
    private val brainEdgeEvidenceDao = db.brainEdgeEvidenceDao()
    private val brainTopicDao = db.brainTopicDao()
    private val brainChunkDao = db.brainChunkDao()
    private val brainDocumentDao = db.brainDocumentDao()
    private val brainRunDao = db.brainRunDao()
    private val brainImageProfileDao = db.brainImageProfileDao()
    private val aiConfigDao = db.aiProviderConfigDao()
    private val fileIndexDao = db.fileIndexDao()

    private val client = AiProviderClient()
    private val brainAi = DefaultBrainAiGateway(client)
    private val onDeviceEmbedding = OnDeviceEmbeddingEngine(appContext)
    private val contentReader = BrainContentReader(appContext)
    private val indexer = BrainIndexer(
        context = appContext,
        documentDao = brainDocumentDao,
        chunkDao = brainChunkDao,
        nodeDao = brainNodeDao,
        edgeDao = brainEdgeDao,
        edgeEvidenceDao = brainEdgeEvidenceDao,
        runDao = brainRunDao,
        imageProfileDao = brainImageProfileDao,
        client = brainAi,
        embeddingClient = client,
        db = db,
        onDeviceEmbedding = onDeviceEmbedding
    )
    private val retriever = BrainRetriever(
        chunkDao = brainChunkDao,
        nodeDao = brainNodeDao,
        edgeDao = brainEdgeDao,
        onDeviceEmbedding = onDeviceEmbedding,
        embeddingClient = client
    )

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

    suspend fun listEmbeddingModels(config: AiProviderConfigEntity): List<AvailableAiModel> =
        client.listEmbeddingModels(normalizeAiConfig(config))

    suspend fun testEmbeddingConnection(config: AiProviderConfigEntity): ConnectionTestResult =
        client.testEmbeddingConnection(normalizeAiConfig(config))

    fun getOnDeviceBrainModelState(error: String? = null): OnDeviceBrainModelUiState =
        onDeviceEmbedding.manager().uiState(error)

    fun getOnDeviceBrainModelSpecs(): List<OnDeviceBrainModelSpec> =
        onDeviceEmbedding.manager().availableSpecs()

    fun getOnDeviceBrainModelSpec(): OnDeviceBrainModelSpec =
        onDeviceEmbedding.manager().defaultSpec()

    fun selectOnDeviceBrainModel(modelId: String): OnDeviceBrainModelSpec =
        onDeviceEmbedding.manager().selectModel(modelId).also {
            onDeviceEmbedding.unload()
        }

    suspend fun downloadOnDeviceBrainModel(
        modelId: String? = null,
        onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        if (!modelId.isNullOrBlank()) {
            onDeviceEmbedding.manager().selectModel(modelId)
        }
        onDeviceEmbedding.manager().downloadSelectedModel(onProgress)
        onDeviceEmbedding.unload()
    }

    suspend fun deleteOnDeviceBrainModel() = withContext(Dispatchers.IO) {
        onDeviceEmbedding.unload()
        onDeviceEmbedding.manager().deleteSelectedModel()
    }

    fun isOnDeviceBrainModelReady(): Boolean = onDeviceEmbedding.isReady()

    suspend fun clearGraph() = withContext(Dispatchers.IO) {
        val config = getAiConfig()
        runCatching { vectorStoreFor(config, brainChunkDao).clear(config) }
        db.withTransaction {
            brainEdgeEvidenceDao.clearAll()
            brainEdgeDao.clearAll()
            brainNodeDao.clearAll()
            brainChunkDao.clearAll()
            brainDocumentDao.clearAll()
            brainRunDao.clearAll()
            brainImageProfileDao.clearAll()
        }
    }

    suspend fun syncAll(
        force: Boolean = true,
        onProgress: suspend (current: Int, total: Int, path: String, outcome: BrainIndexOutcome) -> Unit = { _, _, _, _ -> }
    ): BrainSyncResult = withContext(Dispatchers.IO) {
        // FileRepository owns the canonical filesystem index. Brain consumes it;
        // it must never recursively scan storage or rebuild the same index.
        val config = getAiConfig()
        val extensions = BrainContentReader.SUPPORTED_EXTENSIONS.map { it.lowercase(Locale.US) }
        val total = fileIndexDao.getBrainCandidateCount(extensions)
        val candidatePaths = HashSet<String>(total)
        val existingBrainPaths = brainDocumentDao.getAllPaths()

        var indexed = 0
        var skipped = 0
        var failed = 0
        var processed = 0
        var lastPath = ""
        val pageSize = 256

        while (true) {
            val candidates = fileIndexDao.getBrainCandidatesPage(extensions, lastPath, pageSize)
            if (candidates.isEmpty()) break

            for (candidate in candidates) {
                lastPath = candidate.path
                if (candidate.name.startsWith(".") || candidate.path.split(File.separatorChar).any { it == ".trash" }) {
                    processed++
                    continue
                }

                val file = File(candidate.path)
                if (!file.isFile || !file.canRead()) {
                    failed++
                    onProgress(processed + 1, total, candidate.path, BrainIndexOutcome(false, error = "File is no longer readable"))
                    processed++
                    continue
                }

                candidatePaths += candidate.path
                val outcome = indexer.index(file, config, force)
                when {
                    outcome.success && outcome.skipped -> skipped++
                    outcome.success -> indexed++
                    else -> failed++
                }
                processed++
                onProgress(processed, total, candidate.path, outcome)
            }

            if (candidates.size < pageSize) break
        }

        for (stalePath in existingBrainPaths.filterNot { it in candidatePaths }) {
            removeIndexedSource(stalePath)
        }

        brainNodeDao.deleteOrphans()
        brainNodeDao.recomputeDegrees()

        BrainSyncResult(total, indexed, skipped, failed)
    }

    suspend fun indexFile(file: File, config: AiProviderConfigEntity, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        indexer.index(file, normalizeAiConfig(config), force).success
    }

    suspend fun reconcileMutation(
        relocatedPaths: List<Pair<String, String>>,
        removedPaths: List<String>
    ) = withContext(Dispatchers.IO) {
        for (oldPath in removedPaths.distinct()) {
            removeIndexedSource(oldPath)
        }

        for ((oldPath, newPath) in relocatedPaths) {
            if (!File(oldPath).exists()) {
                removeIndexedSource(oldPath)
            }
            if (EmbeddingProviderType.fromString(getAiConfig().embeddingProviderType) == EmbeddingProviderType.OFFLINE &&
                !isOnDeviceBrainModelReady()
            ) continue

            val file = File(newPath)
            if (!file.exists() || !file.canRead()) continue
            val config = getAiConfig()
            if (file.isDirectory) {
                val descendants = fileIndexDao.getFilesUnderPath(newPath, newPath)
                for (child in descendants) {
                    currentCoroutineContext().ensureActive()
                    if (!child.isDirectory &&
                        child.extension.lowercase(Locale.US) in BrainContentReader.SUPPORTED_EXTENSIONS
                    ) {
                        val childFile = File(child.path)
                        if (childFile.isFile && childFile.canRead()) {
                            indexFile(childFile, config, force = true)
                        }
                    }
                }
            } else if (file.extension.lowercase(Locale.US) in BrainContentReader.SUPPORTED_EXTENSIONS) {
                indexFile(file, config, force = true)
            }
        }
    }

    suspend fun onFileRenamed(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        removeIndexedSource(oldPath)
        val indexed = fileIndexDao.getByPath(newPath) ?: return@withContext
        val config = getAiConfig()
        if (!indexed.isDirectory) {
            if (indexed.extension.lowercase(Locale.US) in BrainContentReader.SUPPORTED_EXTENSIONS) {
                indexFile(File(indexed.path), config, force = true)
            }
        } else {
            // Directory rename: the FileRepository has already rebuilt the canonical
            // subtree. Brain must consume that index and rehydrate every supported child.
            val descendants = fileIndexDao.getFilesUnderPath(newPath, newPath)
            for (child in descendants) {
                currentCoroutineContext().ensureActive()
                if (!child.isDirectory &&
                    child.extension.lowercase(Locale.US) in BrainContentReader.SUPPORTED_EXTENSIONS
                ) {
                    val file = File(child.path)
                    if (file.isFile && file.canRead()) {
                        indexFile(file, config, force = true)
                    }
                }
            }
        }
        brainNodeDao.recomputeDegrees()
    }

    suspend fun removeIndexedSource(filePath: String) = withContext(Dispatchers.IO) {
        val config = getAiConfig()
        val store = vectorStoreFor(config, brainChunkDao)
        val paths = brainDocumentDao.getPathsUnder(filePath, filePath + File.separator).ifEmpty { listOf(filePath) }
        for (path in paths) {
            val ids = brainChunkDao.getForFile(path).map { it.id }
            runCatching { store.delete(ids, config) }
        }
        db.withTransaction {
            for (path in paths) {
                val fileNodeId = BrainIdentity.fileNodeId(path)
                brainEdgeEvidenceDao.deleteForFile(path)
                brainEdgeDao.deleteForFileNode(fileNodeId)
                brainChunkDao.deleteForFile(path)
                brainNodeDao.deleteFileNode(path)
                brainDocumentDao.delete(path)
                brainImageProfileDao.delete(path)
            }
            brainEdgeEvidenceDao.refreshRepresentatives()
            brainEdgeEvidenceDao.deleteEdgesWithoutEvidence()
        }
        brainNodeDao.deleteOrphans()
        brainNodeDao.recomputeDegrees()
    }

    suspend fun getImageProfile(path: String): BrainImageProfileEntity? = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext null
        brainImageProfileDao.get(path)
    }

    suspend fun getBrainNode(path: String): BrainNodeEntity? = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext null
        brainNodeDao.getByFilePath(path)
    }

    suspend fun recomputeGraphDegrees() = withContext(Dispatchers.IO) {
        brainNodeDao.deleteOrphans()
        brainNodeDao.recomputeDegrees()
    }

    suspend fun saveBrainTopic(id: String?, heading: String, description: String): BrainTopicEntity = withContext(Dispatchers.IO) {
        val cleanHeading = heading.trim()
        require(cleanHeading.isNotBlank()) { "Topic heading cannot be blank" }
        val cleanDescription = description.trim()
        val now = System.currentTimeMillis()
        val existing = id?.let { brainTopicDao.get(it) }
        val topic = BrainTopicEntity(existing?.id ?: UUID.randomUUID().toString(), cleanHeading, cleanDescription, existing?.createdAt ?: now, now)
        brainTopicDao.insertOrUpdate(topic)
        topic
    }

    suspend fun deleteBrainTopic(id: String) = withContext(Dispatchers.IO) { brainTopicDao.delete(id) }

    suspend fun getRelevantFilesForBrainTopic(topic: BrainTopicEntity, config: AiProviderConfigEntity, limit: Int = 12): List<BrainTopicFile> = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext emptyList()
        val query = listOf(topic.heading, topic.description).filter { it.isNotBlank() }.joinToString(". ")
        if (query.isBlank()) return@withContext emptyList()
        val retrieval = retriever.retrieve(query, normalizeAiConfig(config), limit.coerceAtLeast(8))
        val bestPaths = retrieval.hits.groupBy { it.chunk.filePath }.mapValues { (_, hits) -> hits.maxOf { it.score } }.entries.sortedByDescending { it.value }.take(limit)
        val nodes = brainNodeDao.getByFilePaths(bestPaths.map { it.key }).filter { it.nodeType == "DOCUMENT" || it.nodeType == "IMAGE" }.associateBy { it.sourceFilePath.orEmpty() }
        bestPaths.mapNotNull { entry -> nodes[entry.key]?.let { BrainTopicFile(it, entry.value) } }
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
                if (seenFiles.add(neighbor.id)) out += ConnectedDotsItem(neighbor, edge.relation, fileNode, edge.evidenceSnippet)
                continue
            }
            val secondHopEdges = brainEdgeDao.forNode(neighbor.id)
            for (secondHop in secondHopEdges.sortedByDescending { it.weight }.take(8)) {
                val otherId = if (secondHop.sourceNodeId == neighbor.id) secondHop.targetNodeId else secondHop.sourceNodeId
                if (!seenFiles.add(otherId)) continue
                val other = brainNodeDao.get(otherId) ?: continue
                if (other.nodeType == "DOCUMENT" || other.nodeType == "IMAGE") out += ConnectedDotsItem(other, "Connected via " + neighbor.label, neighbor, secondHop.evidenceSnippet)
                if (out.size >= 24) break
            }
            if (out.size >= 24) break
        }
        out.take(24)
    }

    suspend fun searchContent(query: String, rootPath: String?, limit: Int = 100): List<Pair<String, Float>> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank() || limit <= 0) return@withContext emptyList()
        val normalizedRoot = rootPath?.let { File(it).absoluteFile.normalize().path }
        val retrieval = retriever.retrieve(clean, getAiConfig(), limit.coerceAtLeast(8))
        retrieval.hits
            .asSequence()
            .map { hit -> hit.chunk.filePath to hit.score }
            .filter { (path, _) ->
                val file = File(path)
                if (!file.isFile || !file.canRead()) {
                    false
                } else if (normalizedRoot == null) {
                    true
                } else {
                    val candidate = file.absoluteFile.normalize().path
                    candidate == normalizedRoot || candidate.startsWith(normalizedRoot + File.separator)
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, scores) -> scores.maxOrNull() ?: 0f }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    suspend fun getSmartSuggestions(): List<String> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<String>()
        brainNodeDao.recentFiles(6).forEach { node -> suggestions += if (node.nodeType == "IMAGE") "Details for " + node.label else "Summarize " + node.label }
        brainNodeDao.byType("LOCATION", 3).forEach { node -> suggestions += "Photos from " + node.label }
        brainNodeDao.byType("DEVICE", 3).forEach { node -> suggestions += "Taken with " + node.label }
        suggestions.distinct().take(12)
    }

    suspend fun queryFileSpecifically(file: File, question: String, chatHistory: List<Pair<String, String>> = emptyList()): RagAnswer = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        if (!file.exists() || !file.isFile || !file.canRead()) return@withContext RagAnswer("Could not read the attached file \"${file.name}\".", isSuccessful = false, latencyMs = System.currentTimeMillis() - started)
        val config = getAiConfig()
        val input = try { contentReader.read(file, config) } catch (error: Exception) {
            if (error is CancellationException) throw error
            return@withContext RagAnswer("Could not read the attached file \"${file.name}\": ${error.message ?: "I/O error"}", isSuccessful = false, latencyMs = System.currentTimeMillis() - started)
        }
        return@withContext try {
            val answer = if (isAiReady(config)) {
                brainAi.chatAboutFile(question.trim(), file.name, if (input.isImage) null else input.text, input.imageBase64, input.metadataSummary, chatHistory.filter { it.first.isNotBlank() && it.second.isNotBlank() }, config)
            } else localFileAnswer(file, input)
            val node = brainNodeDao.getByFilePath(file.absolutePath) ?: BrainNodeEntity(BrainIdentity.fileNodeId(file.absolutePath), file.name, if (input.isImage) "IMAGE" else "DOCUMENT", file.absolutePath, if (input.isImage) "file://" + file.absolutePath else null, input.text.take(500).ifBlank { input.metadataSummary.take(500) }, 0, 1f, System.currentTimeMillis())
            RagAnswer(answer, connectedNodes = listOf(node), isSuccessful = true, latencyMs = System.currentTimeMillis() - started)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            RagAnswer(
                answer = localFileAnswer(file, input) + "\n\nAI service error: " + (error.message ?: "unknown error"),
                connectedNodes = brainNodeDao.getByFilePath(file.absolutePath)?.let { listOf(it) }.orEmpty(),
                isSuccessful = false,
                latencyMs = System.currentTimeMillis() - started
            )
        }
    }

    suspend fun queryRag(question: String, chatHistory: List<Pair<String, String>> = emptyList()): RagAnswer = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val clean = question.trim()
        if (clean.isBlank()) return@withContext RagAnswer("Please enter a question.", isSuccessful = false)
        val config = getAiConfig()
        val retrieval = retriever.retrieve(clean, config, 8)
        if (!retrieval.hasMatch) return@withContext RagAnswer("No indexed Brain sources matched \"$clean\". Sync Brain first or attach a specific file.", isSuccessful = false, latencyMs = System.currentTimeMillis() - started)
        val (context, evidence) = retriever.buildContext(retrieval, config)
        if (context.isBlank()) return@withContext RagAnswer("The Brain index matched sources, but their files are no longer readable. Sync Brain to refresh the index.", retrieval.hits.map { it.chunk }, retrieval.relatedNodes, false, System.currentTimeMillis() - started)

        var answer = ""
        var successful = false
        if (isAiReady(config)) {
            try {
                answer = brainAi.generateRagAnswer(clean, context, evidence, chatHistory.filter { it.first.isNotBlank() && it.second.isNotBlank() }, config)
                successful = true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                answer = localRagAnswer(retrieval)
                successful = false
            }
        } else {
            answer = localRagAnswer(retrieval)
            successful = true
        }
        RagAnswer(answer, retrieval.hits.map { it.chunk }, retrieval.relatedNodes, successful, System.currentTimeMillis() - started)
    }

    private fun localFileAnswer(file: File, content: BrainFileContent): String = buildString {
        append("### Attached File: ").append(file.name).append("\n\n")
        if (content.metadataSummary.isNotBlank()) append(content.metadataSummary).append("\n\n")
        if (content.text.isNotBlank()) append("Excerpt:\n").append(content.text.replace(Regex("\\s+"), " ").trim().take(900))
        else append("No text content was extracted locally.")
    }

    private fun localRagAnswer(retrieval: BrainRetrieval): String = buildString {
        append("Matching Brain sources:\n\n")
        retrieval.hits.take(6).forEach { hit ->
            append("• ").append(File(hit.chunk.filePath).name).append(" — ")
                .append(hit.chunk.content.replace(Regex("\\s+"), " ").trim().take(260)).append('\n')
        }
        if (retrieval.evidence.isNotEmpty()) {
            append("\nConnections:\n")
            retrieval.evidence.take(6).forEach { append("• ").append(it).append('\n') }
        }
    }.trim()

    private fun isAiReady(config: AiProviderConfigEntity): Boolean =
        config.isEnabled && (isKeylessAiConfig(config) || config.apiKey.isNotBlank())

    private fun normalizeAiConfig(config: AiProviderConfigEntity): AiProviderConfigEntity {
        val provider = ProviderType.fromString(config.providerType)
        val explicitTextEmbedding = config.textEmbeddingModel.trim()
        val legacyEmbedding = config.embeddingModel.trim()
        val embeddingProvider = EmbeddingProviderType.fromString(config.embeddingProviderType)
        val defaultEmbeddingBase = embeddingProvider.defaultBaseUrl
        return config.copy(
            providerType = provider.name,
            embeddingProviderType = embeddingProvider.name,
            vectorDatabaseType = com.example.data.ai.VectorDatabaseType.fromString(config.vectorDatabaseType).name,
            vectorDatabaseBaseUrl = config.vectorDatabaseBaseUrl.trim(),
            vectorDatabaseApiKey = config.vectorDatabaseApiKey.trim(),
            vectorDatabaseCollection = config.vectorDatabaseCollection.trim().ifBlank { "emrexplore_brain" },
            embeddingApiKey = config.embeddingApiKey.trim(),
            embeddingBaseUrl = config.embeddingBaseUrl.trim().ifBlank { defaultEmbeddingBase },
            textEmbeddingModel = explicitTextEmbedding.ifBlank { legacyEmbedding },
            embeddingModel = legacyEmbedding
        )
    }

    private fun decryptConfig(config: AiProviderConfigEntity): AiProviderConfigEntity =
        config.copy(
            apiKey = com.example.data.security.ApiKeyProtector.decrypt(config.apiKey),
            embeddingApiKey = com.example.data.security.ApiKeyProtector.decrypt(config.embeddingApiKey),
            vectorDatabaseApiKey = com.example.data.security.ApiKeyProtector.decrypt(config.vectorDatabaseApiKey)
        )

    private fun encryptConfig(config: AiProviderConfigEntity): AiProviderConfigEntity =
        config.copy(
            apiKey = com.example.data.security.ApiKeyProtector.encrypt(config.apiKey),
            embeddingApiKey = com.example.data.security.ApiKeyProtector.encrypt(config.embeddingApiKey),
            vectorDatabaseApiKey = com.example.data.security.ApiKeyProtector.encrypt(config.vectorDatabaseApiKey)
        )

    private fun fileNodeId(path: String): String = BrainIdentity.fileNodeId(path)
    private fun stableKey(value: String): String = BrainIdentity.stableKey(value)
}
