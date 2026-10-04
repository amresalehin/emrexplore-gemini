package com.example.data.ai

import android.content.Context
import androidx.room.withTransaction
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.data.local.AiProviderConfigEntity
import com.example.data.local.BrainTopicEntity
import com.example.data.local.AppDatabase
import com.example.data.local.KgEdgeEntity
import com.example.data.local.KgEdgeEvidenceEntity
import com.example.data.local.KgNodeEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.local.EntityMentionEntity
import com.example.data.local.ModelRunEntity
import com.example.data.local.IndexFingerprintEntity
import com.example.data.local.RagChunkEntity
import com.example.data.metadata.MetadataExtractor
import com.example.data.metadata.MetadataWriter
import com.example.data.media.MediaMetadataRepository
import com.example.data.model.MediaItem
import com.example.data.security.ApiKeyProtector
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class BrainTopicFile(
    val node: KgNodeEntity,
    val score: Float
)

class KnowledgeGraphRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val kgDao = db.kgDao()
    private val ragDao = db.ragDao()
    private val aiConfigDao = db.aiProviderConfigDao()
    private val brainTopicDao = db.brainTopicDao()
    private val fileIndexDao = db.fileIndexDao()
    private val mediaMetadataDao = db.mediaMetadataDao()
    private val fingerprintDao = db.indexFingerprintDao()
    private val factDao = db.memoryFactDao()
    private val mentionDao = db.entityMentionDao()
    private val modelRunDao = db.modelRunDao()
    private val client = AiProviderClient()
    private val brainEngine = BrainEngine(ragDao, kgDao, client)
    private data class IndexResult(val success: Boolean, val hasSearchableContent: Boolean)

    private val metadataExtractor = MetadataExtractor(context)
    private val mediaMetadataRepository = MediaMetadataRepository(context)

    private val brainIndexVersion = "brain-v3"

    private fun isAiReady(config: AiProviderConfigEntity): Boolean =
        config.isEnabled && (ProviderType.fromString(config.providerType) in setOf(ProviderType.OLLAMA, ProviderType.OPENAI_COMPATIBLE, ProviderType.CUSTOM) || config.apiKey.isNotBlank())

    private fun requiresEmbeddings(config: AiProviderConfigEntity): Boolean =
        isAiReady(config) && config.embeddingModel.isNotBlank()

    val allNodesFlow: Flow<List<KgNodeEntity>> = kgDao.getAllNodesFlow()
    val allEdgesFlow: Flow<List<KgEdgeEntity>> = kgDao.getAllEdgesFlow()
    val nodeCountFlow: Flow<Int> = kgDao.getNodeCountFlow()
    val edgeCountFlow: Flow<Int> = kgDao.getEdgeCountFlow()
    val chunkCountFlow: Flow<Int> = ragDao.getChunkCountFlow()
    val aiConfigFlow: Flow<AiProviderConfigEntity?> = aiConfigDao.getConfigFlow().map { it?.let(::decryptConfig) }
    val brainTopicsFlow: Flow<List<BrainTopicEntity>> = brainTopicDao.getAllFlow()

    private fun normalizeAiConfig(config: AiProviderConfigEntity): AiProviderConfigEntity {
        if (config.embeddingModel != "gemini-embedding-2-preview") return config
        val provider = ProviderType.fromString(config.providerType)
        val replacement = provider.defaultEmbeddingModel
        return if (replacement.isNotBlank()) config.copy(embeddingModel = replacement) else config.copy(embeddingModel = "")
    }

    private fun decryptConfig(config: AiProviderConfigEntity): AiProviderConfigEntity =
        config.copy(apiKey = ApiKeyProtector.decrypt(config.apiKey))

    private fun encryptConfig(config: AiProviderConfigEntity): AiProviderConfigEntity =
        config.copy(apiKey = ApiKeyProtector.encrypt(config.apiKey))

    suspend fun getAiConfig(): AiProviderConfigEntity = withContext(Dispatchers.IO) {
        val raw = aiConfigDao.getConfig() ?: AiProviderConfigEntity()
        val decrypted = decryptConfig(raw)
        val normalized = normalizeAiConfig(decrypted)
        if (normalized != decrypted || raw.apiKey != normalized.apiKey) {
            aiConfigDao.saveConfig(encryptConfig(normalized))
        }
        normalized
    }

    suspend fun saveAiConfig(config: AiProviderConfigEntity) = withContext(Dispatchers.IO) {
        aiConfigDao.saveConfig(encryptConfig(normalizeAiConfig(config)))
    }

    suspend fun testConnection(config: AiProviderConfigEntity): ConnectionTestResult {
        return client.testConnection(config)
    }

    suspend fun listAiModels(config: AiProviderConfigEntity) = client.listModels(config)

    suspend fun clearGraph() = withContext(Dispatchers.IO) {
        db.withTransaction {
            kgDao.clearAllEdgeEvidence()
            kgDao.clearAllEdges()
            kgDao.clearAllNodes()
            ragDao.clearAllChunks()
            factDao.clearAll()
            mentionDao.clearAll()
            fingerprintDao.clearAll()
            modelRunDao.clearAll()
        }
    }

    suspend fun removeIndexedSource(filePath: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            kgDao.deleteEdgeEvidenceBySource(filePath)
            kgDao.deleteSourcedEdgesWithoutEvidence()
            kgDao.deleteEdgesForNode("doc:$filePath")
            kgDao.deleteEdgesForNode("img:$filePath")
            kgDao.deleteNodeByFilePath(filePath)
            ragDao.deleteChunksForFile(filePath)
            factDao.deleteForFile(filePath)
            mentionDao.deleteForFile(filePath)
            fingerprintDao.delete(filePath)
        }
        kgDao.deleteOrphanedNonFileNodes()
        kgDao.recomputeDegrees()
    }

    suspend fun getBrainCandidates(): List<com.example.data.local.IndexedFileEntity> = withContext(Dispatchers.IO) {
        fileIndexDao.getAllIndexedFilesForBrain()
    }

    suspend fun saveBrainTopic(
        id: String?,
        heading: String,
        description: String
    ): BrainTopicEntity = withContext(Dispatchers.IO) {
        val cleanHeading = heading.trim()
        require(cleanHeading.isNotBlank()) { "Topic heading cannot be blank" }
        val cleanDescription = description.trim()
        val existing = id?.let { brainTopicDao.get(it) }
        val now = System.currentTimeMillis()
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

    /**
     * Finds the files semantically closest to a user-defined topic.
     * This intentionally uses the same semantic-only Brain retrieval path as RAG;
     * there is no lexical fallback when embeddings are unavailable.
     */
    suspend fun getRelevantFilesForBrainTopic(
        topic: BrainTopicEntity,
        config: AiProviderConfigEntity,
        limit: Int = 12
    ): List<BrainTopicFile> = withContext(Dispatchers.IO) {
        if (limit <= 0 || !requiresEmbeddings(config)) return@withContext emptyList()
        val query = listOf(topic.heading, topic.description)
            .filter { it.isNotBlank() }
            .joinToString(". ")
        if (query.isBlank()) return@withContext emptyList()

        val scored = brainEngine.search(query, config, (limit * 4).coerceAtLeast(limit))
        val bestByPath = linkedMapOf<String, Float>()
        scored.forEach { match ->
            if (match.chunk.filePath.isNotBlank()) {
                val current = bestByPath[match.chunk.filePath]
                if (current == null || match.score > current) {
                    bestByPath[match.chunk.filePath] = match.score
                }
            }
        }

        val orderedPaths = bestByPath.entries
            .sortedByDescending { it.value }
            .take(limit)
        if (orderedPaths.isEmpty()) return@withContext emptyList()

        val nodesByPath = kgDao.getNodesByFilePaths(orderedPaths.map { it.key })
            .filter { it.nodeType == "DOCUMENT" || it.nodeType == "IMAGE" }
            .associateBy { it.sourceFilePath.orEmpty() }

        orderedPaths.mapNotNull { scoredPath ->
            nodesByPath[scoredPath.key]?.let { node ->
                BrainTopicFile(node = node, score = scoredPath.value)
            }
        }
    }

    suspend fun recomputeGraphDegrees() = withContext(Dispatchers.IO) {
        // Expensive orphan cleanup happens once at the end of a sync, not once per file.
        kgDao.deleteOrphanedNonFileNodes()
        kgDao.recomputeDegrees()
    }

    /**
     * Finds files (documents & images) that share entities or direct relations with this file.
     */
    suspend fun getConnectedDotsForFile(filePath: String): List<ConnectedDotsItem> = withContext(Dispatchers.IO) {
        val fileNode = kgDao.getNodeByFilePath(filePath) ?: return@withContext emptyList()
        val directEdges = kgDao.getEdgesForNode(fileNode.id)
        val connectedItems = mutableListOf<ConnectedDotsItem>()
        val visitedNodeIds = mutableSetOf(fileNode.id)

        for (edge in directEdges) {
            val neighborId = if (edge.sourceNodeId == fileNode.id) edge.targetNodeId else edge.sourceNodeId
            val neighborNode = kgDao.getNode(neighborId) ?: continue

            // If neighbor is an entity, check other files connected to this entity
            if (neighborNode.nodeType != "DOCUMENT" && neighborNode.nodeType != "IMAGE") {
                val entityEdges = kgDao.getEdgesForNode(neighborNode.id)
                for (entEdge in entityEdges) {
                    val otherFileNodeId = if (entEdge.sourceNodeId == neighborNode.id) entEdge.targetNodeId else entEdge.sourceNodeId
                    if (otherFileNodeId != fileNode.id && !visitedNodeIds.contains(otherFileNodeId)) {
                        val otherFileNode = kgDao.getNode(otherFileNodeId)
                        if (otherFileNode != null && (otherFileNode.nodeType == "DOCUMENT" || otherFileNode.nodeType == "IMAGE")) {
                            visitedNodeIds.add(otherFileNodeId)
                            connectedItems.add(
                                ConnectedDotsItem(
                                    fileNode = otherFileNode,
                                    relationship = "Connected via ${neighborNode.label} (${edge.relation.lowercase()})",
                                    targetNode = neighborNode,
                                    snippet = otherFileNode.summary
                                )
                            )
                        }
                    }
                }
            } else if (!visitedNodeIds.contains(neighborNode.id)) {
                // Direct file-to-file link
                visitedNodeIds.add(neighborNode.id)
                connectedItems.add(
                    ConnectedDotsItem(
                        fileNode = neighborNode,
                        relationship = edge.relation,
                        targetNode = fileNode,
                        snippet = edge.evidenceSnippet.ifBlank { neighborNode.summary }
                    )
                )
            }
        }

        connectedItems
    }

    suspend fun getSmartSuggestions(): List<String> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<String>()
        val recentFiles = kgDao.getRecentFileNodes(limit = 6)
        for (node in recentFiles) {
            if (node.nodeType == "DOCUMENT") {
                suggestions.add("Summarize ${node.label}")
            } else if (node.nodeType == "IMAGE") {
                suggestions.add("Details for ${node.label}")
            }
        }
        val locNodes = kgDao.getNodesByType("LOCATION", limit = 3)
        for (loc in locNodes) {
            suggestions.add("Photos from ${loc.label}")
        }
        val devNodes = kgDao.getNodesByType("DEVICE", limit = 3)
        for (dev in devNodes) {
            suggestions.add("Taken with ${dev.label}")
        }
        suggestions.distinct().take(4)
    }

    /**
     * Analyzes and indexes a single document or image into the Knowledge Graph & RAG store.
     */
    suspend fun enrichGalleryImage(
        file: File,
        uri: android.net.Uri,
        config: AiProviderConfigEntity
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isAiReady(config) || !file.exists() || !file.isFile || !file.canRead()) return@withContext false
        val ext = file.extension.lowercase()
        if (ext !in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")) return@withContext false

        val existing = mediaMetadataDao.getByPath(file.absolutePath)
        if (existing != null &&
            existing.aiProcessedAt > 0L &&
            existing.aiModel == config.visionModel &&
            existing.size == file.length() &&
            existing.aiFileLastModified == file.lastModified()
        ) {
            return@withContext true
        }

        val report = try { metadataExtractor.extract(file) } catch (_: Exception) { null }
        val allowSensitiveLocation = ProviderType.fromString(config.providerType) == ProviderType.OLLAMA
        val metadataSummary = buildString {
            report?.summary?.let { meta ->
                if (!meta.make.isNullOrBlank()) append("Camera: ${meta.make} ${meta.model.orEmpty()}. ")
                if (allowSensitiveLocation && !meta.city.isNullOrBlank()) append("Location: ${meta.city}, ${meta.country.orEmpty()}. ")
                if (!meta.dateTimeOriginal.isNullOrBlank()) append("Date: ${meta.dateTimeOriginal}. ")
                if (meta.description?.isNotBlank() == true) append("Caption: ${meta.description}. ")
                if (meta.keywords.isNotEmpty()) append("Keywords: ${meta.keywords.joinToString()}. ")
            }
        }

        val item = MediaItem(
            id = file.absolutePath.hashCode().toLong(),
            uri = uri,
            name = file.name,
            path = file.absolutePath,
            size = file.length(),
            dateAdded = file.lastModified(),
            mimeType = "image/$ext",
            isVideo = false
        )
        val base64 = getCompressedBase64(file, uri, maxDimension = 1024)
        val analysis = client.analyzeImage(base64, metadataSummary, file.name, config)
        val tags = analysis.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(50)
        val tagsJson = JSONArray(tags).toString()
        val entitiesJson = JSONArray().apply {
            analysis.entities.forEach { entity ->
                put(org.json.JSONObject().apply {
                    put("name", entity.name.trim())
                    put("type", entity.type.ifBlank { "TOPIC" })
                    put("confidence", entity.confidence.coerceIn(0f, 1f))
                })
            }
        }.toString()
        val relationsJson = JSONArray().apply {
            analysis.relations.forEach { relation ->
                put(org.json.JSONObject().apply {
                    put("source", relation.source.trim())
                    put("relation", relation.relation.trim())
                    put("target", relation.target.trim())
                    put("evidence", relation.evidence.trim())
                })
            }
        }.toString()

        MetadataWriter.writeAiMetadata(file, analysis.summary.trim(), tags)
        val persistedItem = item.copy(size = file.length())
        mediaMetadataRepository.getOrRead(persistedItem, requireOriginalLocation = false)
        mediaMetadataRepository.saveAiEnrichment(
            item = persistedItem,
            caption = analysis.summary.trim(),
            tagsJson = tagsJson,
            entitiesJson = entitiesJson,
            relationsJson = relationsJson,
            model = config.visionModel
        )
        // Force Brain to rebuild its derived representation from the newly enriched image.
        fingerprintDao.delete(file.absolutePath)
        true
    }
    suspend fun indexFile(file: File, config: AiProviderConfigEntity): Boolean =
        indexFile(file, null, config)

    suspend fun indexFile(file: File, uri: android.net.Uri?, config: AiProviderConfigEntity): Boolean = withContext(Dispatchers.IO) {
        val canReadDirectly = file.exists() && file.isFile && file.canRead()
        if (!canReadDirectly && uri == null) return@withContext false
        val ext = file.extension.lowercase()

        val isImage = ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
        val isDoc = ext in setOf("txt", "md", "json", "csv", "xml", "html", "htm", "log", "kt", "java", "py", "js", "ts", "c", "cpp", "properties", "sql", "yaml", "yml", "pdf", "conf", "ini", "tsv", "gradle", "kts", "env")
        if (!isImage && !isDoc) return@withContext true

        val filePath = file.absolutePath
        val runId = UUID.randomUUID().toString()
        val runStartedAt = System.currentTimeMillis()
        val modelVersion = brainIndexVersion + ":" + ProviderType.fromString(config.providerType).name + ":" + config.chatModel + ":" + config.visionModel + ":" + config.isEnabled
        val existing = fingerprintDao.get(filePath)

        // For ordinary files, size + mtime let us skip a full SHA-256 read on every sync.
        if (existing != null &&
            canReadDirectly &&
            existing.size == file.length() &&
            existing.lastModified == file.lastModified() &&
            existing.modelVersion == modelVersion &&
            existing.embeddingModel == config.embeddingModel
        ) {
            return@withContext true
        }

        val hash = computeFileHash(file, uri)
        if (existing != null &&
            existing.size == file.length() &&
            existing.lastModified == file.lastModified() &&
            existing.contentHash == hash &&
            existing.modelVersion == modelVersion &&
            existing.embeddingModel == config.embeddingModel
        ) {
            return@withContext true
        }

        val result = try {
            db.withTransaction {
                replaceSourceData(filePath)
                val staged = if (isDoc) indexDocumentInternal(file, uri, config) else indexImageInternal(file, uri, config)
                if (!staged.success) throw IllegalStateException("Brain indexing failed for $filePath")
                staged
            }
        } catch (error: Exception) {
            Log.w("KGRepo", "Indexing failed; existing Brain data was preserved for $filePath: ${error.message}")
            modelRunDao.insert(ModelRunEntity(runId, filePath, "INDEX", config.chatModel.ifBlank { "local" }, false, error.message, runStartedAt, System.currentTimeMillis()))
            return@withContext false
        }

        val chunkCount = ragDao.getChunkCountForFile(filePath)
        val indexed = kgDao.getNodeByFilePath(filePath) != null &&
            (!result.hasSearchableContent || chunkCount > 0)
        val embeddingsReady = !result.hasSearchableContent ||
            !requiresEmbeddings(config) ||
            ragDao.getChunksMissingEmbeddings(filePath, config.embeddingModel) == 0
        if (indexed && embeddingsReady) {
            fingerprintDao.insert(IndexFingerprintEntity(filePath, file.length(), file.lastModified(), hash, modelVersion, config.embeddingModel))
            modelRunDao.insert(ModelRunEntity(runId, filePath, "INDEX", config.chatModel.ifBlank { "local" }, true, null, runStartedAt, System.currentTimeMillis()))
            return@withContext true
        } else {
            fingerprintDao.delete(filePath)
            modelRunDao.insert(ModelRunEntity(runId, filePath, "INDEX", config.chatModel.ifBlank { "local" }, false, "Embeddings or indexed source incomplete", runStartedAt, System.currentTimeMillis()))
            return@withContext false
        }
    }

    private fun extractPdfText(file: File, uri: android.net.Uri?): String {
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            val document = if (file.exists() && file.canRead()) {
                PDDocument.load(file)
            } else {
                val input = uri?.let { context.contentResolver.openInputStream(it) } ?: return ""
                input.use { PDDocument.load(it) }
            }
            document.use { pdf ->
                if (pdf.isEncrypted) return ""
                val stripper = PDFTextStripper().apply {
                    startPage = 1
                    endPage = minOf(pdf.numberOfPages, 120)
                }
                stripper.getText(pdf).take(50000)
            }
        } catch (e: Exception) {
            Log.w("KGRepo", "Could not extract PDF text: ${e.message}")
            ""
        }
    }


    private suspend fun indexDocumentInternal(file: File, uri: android.net.Uri?, config: AiProviderConfigEntity): IndexResult {
        val filePath = file.absolutePath
        val contentText = try {
            if (file.extension.equals("pdf", ignoreCase = true)) {
                extractPdfText(file, uri)
            } else if (file.exists() && file.canRead()) {
                file.inputStream().bufferedReader().use { it.readText().take(50000) }
            } else ""
        } catch (e: Exception) {
            Log.w("KGRepo", "Could not read doc text: ${e.message}")
            return IndexResult(success = false, hasSearchableContent = false)
        }

        if (contentText.isBlank()) {
            kgDao.insertNodes(listOf(
                KgNodeEntity(
                    id = "doc:$filePath",
                    label = file.name,
                    nodeType = "DOCUMENT",
                    sourceFilePath = filePath,
                    summary = "No extractable text"
                )
            ))
            return IndexResult(success = true, hasSearchableContent = false)
        }

        // Source data was cleared atomically by indexFile() before this stage.
        // 1. Chunk document
        val structuredChunks = chunkStructuredText(contentText)
        val chunks = structuredChunks.map { it.first }
        val embeddings = if (isAiReady(config)) client.embedTexts(chunks, config) else emptyList()
        val ragChunks = chunks.mapIndexed { idx, chunk ->
            RagChunkEntity(
                chunkId = hashKey("$filePath:$idx"),
                filePath = filePath,
                fileType = "DOCUMENT",
                chunkIndex = idx,
                content = chunk,
                tagsJson = "[]",
                embeddingJson = embeddings.getOrNull(idx)?.let { embeddingToJson(it) },
                embeddingModel = embeddings.getOrNull(idx)?.let { config.embeddingModel },
                contentHash = hashKey(chunk),
                sectionPath = structuredChunks.getOrNull(idx)?.second.orEmpty()
            )
        }
        ragDao.insertChunks(ragChunks)

        // 2. Extract entities and relations
        val analysis = if (isAiReady(config)) {
            client.analyzeDocument(contentText, file.name, config)
        } else {
            createLocalTextAnalysis(file.name, contentText)
        }

        val docNodeId = "doc:$filePath"
        val nodesToInsert = mutableListOf<KgNodeEntity>()
        val edgesToInsert = mutableListOf<KgEdgeEntity>()

        val docNode = KgNodeEntity(
            id = docNodeId,
            label = file.name,
            nodeType = "DOCUMENT",
            sourceFilePath = filePath,
            summary = analysis.summary,
            degree = 0,
            confidence = 1f
        )
        nodesToInsert.add(docNode)

        // Link with folder if available
        val parentDir = file.parentFile?.name
        if (!parentDir.isNullOrBlank() && parentDir !in setOf("/", "0", "emulated", "storage")) {
            val folderId = "folder:${hashKey(filePath.substringBeforeLast(File.separator))}"
            nodesToInsert.add(
                KgNodeEntity(
                    id = folderId,
                    label = parentDir,
                    nodeType = "FOLDER",
                    summary = "Folder: $parentDir"
                )
            )
            edgesToInsert.add(
                KgEdgeEntity(
                    sourceNodeId = docNodeId,
                    targetNodeId = folderId,
                    relation = "STORED_IN",
                    evidenceSnippet = "Located in $parentDir folder"
                )
            )
        }

        for (ent in analysis.entities) {
            val entId = "ent:${hashKey(normalize(ent.name))}"
            val entNode = KgNodeEntity(
                id = entId,
                label = ent.name.trim(),
                nodeType = ent.type.ifBlank { "TOPIC" },
                summary = "Observed in ${file.name}",
                confidence = ent.confidence.coerceIn(0f, 1f)
            )
            nodesToInsert.add(entNode)

            edgesToInsert.add(
                KgEdgeEntity(
                    sourceNodeId = docNodeId,
                    targetNodeId = entId,
                    relation = "MENTIONS",
                    evidenceSnippet = "Mentioned in ${file.name}",
                    evidenceSource = filePath
                )
            )
        }

        for (rel in analysis.relations) {
            val sourceId = if (rel.source.equals(file.name, ignoreCase = true)) docNodeId else "ent:${hashKey(normalize(rel.source))}"
            val targetId = "ent:${hashKey(normalize(rel.target))}"
            edgesToInsert.add(
                KgEdgeEntity(
                    sourceNodeId = sourceId,
                    targetNodeId = targetId,
                    relation = rel.relation,
                    evidenceSnippet = rel.evidence,
                    evidenceSource = filePath
                )
            )
        }

        ensureRelationNodes(nodesToInsert, analysis.relations, file.name)
        persistMemoryEvidence(filePath, file.name, hashKey("$filePath:0"), analysis, config, "MENTIONS")
        kgDao.insertNodes(nodesToInsert)
        kgDao.insertEdges(edgesToInsert)
        val evidence = edgesToInsert.mapNotNull { edge ->
            edge.evidenceSource?.takeIf { it.isNotBlank() }?.let { source ->
                KgEdgeEvidenceEntity(
                    sourceNodeId = edge.sourceNodeId,
                    targetNodeId = edge.targetNodeId,
                    relation = edge.relation,
                    evidenceSource = source,
                    evidenceSnippet = edge.evidenceSnippet
                )
            }
        }
        if (evidence.isNotEmpty()) kgDao.insertEdgeEvidence(evidence)
        return IndexResult(success = true, hasSearchableContent = true)
    }

    private suspend fun indexImageInternal(file: File, uri: android.net.Uri?, config: AiProviderConfigEntity): IndexResult {
        val filePath = file.absolutePath

        // 1. Extract EXIF / IPTC / XMP metadata using existing MetadataExtractor
        val metadataReport = try {
            if (file.exists() && file.canRead()) {
                metadataExtractor.extract(file)
            } else if (uri != null) {
                metadataExtractor.extractFromUri(uri, file.name, file.length(), file.absolutePath)
            } else null
        } catch (_: Exception) {
            null
        }

        val metadataSummaryBuilder = StringBuilder()
        val allowSensitiveLocation = ProviderType.fromString(config.providerType) == ProviderType.OLLAMA
        metadataReport?.summary?.let { s ->
            if (!s.make.isNullOrBlank()) metadataSummaryBuilder.append("Camera: ${s.make} ${s.model.orEmpty()}. ")
            if (allowSensitiveLocation && s.latitude != null && s.longitude != null) {
                metadataSummaryBuilder.append("GPS: ${s.latitude}, ${s.longitude}. ")
            }
            if (allowSensitiveLocation && !s.city.isNullOrBlank()) {
                metadataSummaryBuilder.append("Location: ${s.city}, ${s.country.orEmpty()}. ")
            }
            if (!s.dateTimeOriginal.isNullOrBlank()) metadataSummaryBuilder.append("Date: ${s.dateTimeOriginal}. ")
            if (s.keywords.isNotEmpty()) metadataSummaryBuilder.append("Keywords: ${s.keywords.joinToString()}. ")
            if (!s.title.isNullOrBlank()) metadataSummaryBuilder.append("Title: ${s.title}. ")
            if (!s.description.isNullOrBlank()) metadataSummaryBuilder.append("Caption: ${s.description}. ")
        }

        val metadataSummary = metadataSummaryBuilder.toString()

        // 2. Call AI vision if enabled & key provided
        val base64Thumbnail = if (isAiReady(config)) {
            getCompressedBase64(file, uri, maxDimension = 640)
        } else null

        val storedAi = mediaMetadataDao.getByPath(filePath)?.takeIf {
            it.aiProcessedAt > 0L &&
                it.aiModel == config.visionModel &&
                it.size == file.length() &&
                it.aiFileLastModified == file.lastModified()
        }
        val analysis = when {
            storedAi != null -> analysisFromStoredAi(storedAi)
            isAiReady(config) -> client.analyzeImage(base64Thumbnail, metadataSummary, file.name, config)
            else -> createLocalImageAnalysis(file.name, metadataSummary, metadataReport)
        }

        // 3. Store RAG chunk for visual caption and OCR
        val chunkContent = "Image: ${file.name}\nDescription: ${analysis.summary}\nMetadata: $metadataSummary\nTags: ${analysis.tags.joinToString(", ")}"
        val tagsJsonArray = JSONArray(analysis.tags).toString()
        val chunk = RagChunkEntity(
            chunkId = hashKey("img:$filePath:0:$chunkContent"),
            filePath = filePath,
            fileType = "IMAGE",
            chunkIndex = 0,
            content = chunkContent,
            tagsJson = tagsJsonArray,
            contentHash = hashKey(chunkContent)
        )
        val vector = if (isAiReady(config)) client.embedTexts(listOf(chunkContent), config).firstOrNull() else null
        ragDao.insertChunks(listOf(chunk.copy(
            embeddingJson = vector?.let { embeddingToJson(it) },
            embeddingModel = vector?.let { config.embeddingModel }
        )))

        // 4. Build Knowledge Graph nodes & edges
        val imgNodeId = "img:$filePath"
        val nodesToInsert = mutableListOf<KgNodeEntity>()
        val edgesToInsert = mutableListOf<KgEdgeEntity>()

        val imgNode = KgNodeEntity(
            id = imgNodeId,
            label = file.name,
            nodeType = "IMAGE",
            sourceFilePath = filePath,
            thumbnailUri = if (uri != null) uri.toString() else "file://$filePath",
            summary = analysis.summary,
            degree = analysis.entities.size
        )
        nodesToInsert.add(imgNode)

        // Link with folder
        val parentDir = file.parentFile?.name
        if (!parentDir.isNullOrBlank() && parentDir !in setOf("/", "0", "emulated", "storage")) {
            val folderId = "folder:${hashKey(File(filePath).parentFile?.absolutePath ?: parentDir)}"
            nodesToInsert.add(
                KgNodeEntity(
                    id = folderId,
                    label = parentDir,
                    nodeType = "FOLDER",
                    summary = "Folder: $parentDir"
                )
            )
            edgesToInsert.add(
                KgEdgeEntity(
                    sourceNodeId = imgNodeId,
                    targetNodeId = folderId,
                    relation = "STORED_IN",
                    evidenceSnippet = "Stored in $parentDir folder"
                )
            )
        }

        for (ent in analysis.entities) {
            val entId = "ent:${hashKey(normalize(ent.name))}"
            val entNode = KgNodeEntity(
                id = entId,
                label = ent.name.trim(),
                nodeType = ent.type.ifBlank { "TOPIC" },
                summary = "Observed in ${file.name}",
                confidence = ent.confidence.coerceIn(0f, 1f)
            )
            nodesToInsert.add(entNode)

            edgesToInsert.add(
                KgEdgeEntity(
                    sourceNodeId = imgNodeId,
                    targetNodeId = entId,
                    relation = "DEPICTS",
                    evidenceSnippet = "Depicted in photo ${file.name}",
                    evidenceSource = filePath
                )
            )
        }

        // Add GPS Location node if available
        metadataReport?.summary?.let { s ->
            if (s.latitude != null && s.longitude != null) {
                val locLabel = if (!s.city.isNullOrBlank()) "${s.city}, ${s.country.orEmpty()}".trim() else "Geo (${String.format("%.3f", s.latitude)}, ${String.format("%.3f", s.longitude)})"
                val locId = "loc:${s.latitude}:${s.longitude}"
                nodesToInsert.add(
                    KgNodeEntity(
                        id = locId,
                        label = locLabel,
                        nodeType = "LOCATION",
                        summary = "Geographic coordinates: ${s.latitude}, ${s.longitude}"
                    )
                )
                edgesToInsert.add(
                    KgEdgeEntity(
                        sourceNodeId = imgNodeId,
                        targetNodeId = locId,
                        relation = "LOCATED_AT",
                        evidenceSnippet = "Taken at coordinates ${s.latitude}, ${s.longitude}",
                        evidenceSource = filePath
                    )
                )
            }
        }

        persistMemoryEvidence(filePath, file.name, hashKey("img:$filePath:0:$chunkContent"), analysis, config, "DEPICTS")
        kgDao.insertNodes(nodesToInsert)
        kgDao.insertEdges(edgesToInsert)
        val evidence = edgesToInsert.mapNotNull { edge ->
            edge.evidenceSource?.takeIf { it.isNotBlank() }?.let { source ->
                KgEdgeEvidenceEntity(
                    sourceNodeId = edge.sourceNodeId,
                    targetNodeId = edge.targetNodeId,
                    relation = edge.relation,
                    evidenceSource = source,
                    evidenceSnippet = edge.evidenceSnippet
                )
            }
        }
        if (evidence.isNotEmpty()) kgDao.insertEdgeEvidence(evidence)
        return IndexResult(success = true, hasSearchableContent = true)
    }

    /**
     * Executes RAG: Hybrid retrieval over text chunks + Knowledge Graph traversal + real synthesis.
     */
    suspend fun queryRag(question: String): RagAnswer = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val clean = question.trim()
        if (clean.isBlank()) return@withContext RagAnswer("Please enter a question.", isSuccessful = false)
        val config = getAiConfig()
        val scored = try {
            brainEngine.search(clean, config, 8)
        } catch (error: Exception) {
            return@withContext RagAnswer(
                "Semantic retrieval is unavailable: ${error.message ?: "embedding failed"}. No lexical fallback is used.",
                isSuccessful = false,
                latencyMs = System.currentTimeMillis() - started
            )
        }
        val matchedChunks = scored.map { it.chunk }
        val seedNodes = mutableListOf<KgNodeEntity>()
        tokenizeQuestion(clean).take(8).forEach { token -> seedNodes += kgDao.searchNodes(token).take(5) }
        val (graphNodes, evidence) = brainEngine.graphContext(seedNodes)
        val context = buildString {
            matchedChunks.forEachIndexed { index, chunk ->
                append("=== SOURCE ${index + 1}: ${File(chunk.filePath).name} (${chunk.fileType}) ===\n")
                append(chunk.content).append("\n\n")
            }
        }
        val answer = if (isAiReady(config)) {
            try { client.generateRagAnswer(clean, context, evidence.joinToString("\n"), config) }
            catch (error: Exception) { synthesizeRealOfflineAnswer(clean, matchedChunks, seedNodes, graphNodes, evidence) }
        } else synthesizeRealOfflineAnswer(clean, matchedChunks, seedNodes, graphNodes, evidence)
        RagAnswer(answer, matchedChunks, (seedNodes + graphNodes).distinctBy { it.id }.take(30), true, System.currentTimeMillis() - started)
    }

    private fun tokenizeQuestion(value: String): List<String> = value.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }.distinct()
    private fun chunkStructuredText(text: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var section = "Document"
        val buffer = StringBuilder()
        fun flush() { val v = buffer.toString().trim(); if (v.isNotBlank()) out.add(v to section); buffer.setLength(0) }
        text.lines().forEach { line -> val t=line.trim(); if (t.startsWith("#")) { flush(); section=t.trimStart('#').trim().ifBlank { section } }; buffer.append(line).append('\n'); if(buffer.length>=1400) flush() }
        flush()
        return if(out.isEmpty()) listOf(text.take(1400) to section) else out
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun embeddingToJson(vector: FloatArray): String = JSONArray().apply { vector.forEach { put(it.toDouble()) } }.toString()
    private fun analysisFromStoredAi(metadata: com.example.data.local.MediaMetadataEntity): AnalysisResult {
        val entities = try {
            val array = JSONArray(metadata.aiEntitiesJson)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                if (name.isBlank()) return@mapNotNull null
                ExtractedEntity(
                    name = name,
                    type = obj.optString("type").ifBlank { "TOPIC" },
                    confidence = obj.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
        val tags = try {
            val array = JSONArray(metadata.aiTagsJson)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        } catch (_: Exception) {
            emptyList()
        }
        val relations = try {
            val array = JSONArray(metadata.aiRelationsJson)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val source = obj.optString("source").trim()
                val relation = obj.optString("relation").trim()
                val target = obj.optString("target").trim()
                if (source.isBlank() || relation.isBlank() || target.isBlank()) return@mapNotNull null
                ExtractedRelation(source, relation, target, obj.optString("evidence"))
            }
        } catch (_: Exception) {
            emptyList()
        }
        return AnalysisResult(
            summary = metadata.aiCaption.orEmpty(),
            entities = entities,
            relations = relations,
            tags = tags
        )
    }


    private fun computeFileHash(file: File, uri: android.net.Uri?): String {
        return try {
            val digest=MessageDigest.getInstance("SHA-256")
            val input=when { file.exists() && file.canRead() -> file.inputStream(); uri!=null -> context.contentResolver.openInputStream(uri); else -> null } ?: return hashKey("${file.absolutePath}:${file.length()}:${file.lastModified()}")
            input.use { val buffer=ByteArray(8192); while(true){ val n=it.read(buffer); if(n<=0) break; digest.update(buffer,0,n) } }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch(_: Exception){ hashKey("${file.absolutePath}:${file.length()}:${file.lastModified()}") }
    }

    private fun ensureRelationNodes(
        nodes: MutableList<KgNodeEntity>,
        relations: List<ExtractedRelation>,
        fileName: String
    ) {
        val existing = nodes.mapTo(mutableSetOf()) { it.id }
        relations.forEach { relation ->
            listOf(relation.source, relation.target).forEach { name ->
                val normalized = normalize(name)
                if (normalized.isBlank() || normalized == normalize(fileName)) return@forEach
                val id = "ent:${hashKey(normalized)}"
                if (existing.add(id)) {
                    nodes += KgNodeEntity(
                        id = id,
                        label = name.trim(),
                        nodeType = "TOPIC",
                        summary = "Referenced by ${fileName}",
                        confidence = 0.5f
                    )
                }
            }
        }
    }
    private suspend fun persistMemoryEvidence(
        filePath: String,
        fileName: String,
        chunkId: String,
        analysis: AnalysisResult,
        config: AiProviderConfigEntity,
        predicate: String
    ) {
        val sourceType = if (isAiReady(config)) "AI" else "LOCAL"
        val facts = analysis.entities.map { entity ->
            MemoryFactEntity(
                id = hashKey("fact:$filePath:$predicate:${normalize(entity.name)}"),
                subject = fileName,
                normalizedSubject = normalize(fileName),
                predicate = predicate,
                objectValue = entity.name.trim(),
                normalizedObject = normalize(entity.name),
                confidence = entity.confidence.coerceIn(0f, 1f),
                sourceType = sourceType,
                evidence = "Observed in $fileName",
                sourceFilePath = filePath
            )
        }
        facts.forEach { factDao.insert(it) }
        val mentions = analysis.entities.map { entity ->
            EntityMentionEntity(
                entityId = "ent:${hashKey(normalize(entity.name))}",
                sourceFilePath = filePath,
                chunkId = chunkId,
                mentionText = entity.name.trim(),
                entityType = entity.type.ifBlank { "TOPIC" },
                confidence = entity.confidence.coerceIn(0f, 1f)
            )
        }
        if (mentions.isNotEmpty()) mentionDao.insertAll(mentions)
    }
    private suspend fun replaceSourceData(filePath: String) {
        kgDao.deleteEdgeEvidenceBySource(filePath)
        kgDao.deleteSourcedEdgesWithoutEvidence()
        kgDao.deleteEdgesForNode("doc:$filePath")
        kgDao.deleteEdgesForNode("img:$filePath")
        kgDao.deleteNodeByFilePath(filePath)
        ragDao.deleteChunksForFile(filePath)
        factDao.deleteForFile(filePath)
        mentionDao.deleteForFile(filePath)
        kgDao.deleteOrphanedNonFileNodes()
    }

    private fun synthesizeRealOfflineAnswer(
        question: String,
        chunks: List<RagChunkEntity>,
        matchedNodes: List<KgNodeEntity>,
        connectedNodes: List<KgNodeEntity>,
        connections: List<String>
    ): String {
        val totalNodes = (matchedNodes + connectedNodes).distinctBy { it.id }
        if (totalNodes.isEmpty() && chunks.isEmpty()) {
            return "No matching indexed files found in your Brain for \"$question\".\n\nTry searching for specific file names, topics in your notes, or camera models from your storage."
        }

        val sb = StringBuilder()
        val docNodes = totalNodes.filter { it.nodeType == "DOCUMENT" }
        val imgNodes = totalNodes.filter { it.nodeType == "IMAGE" }
        val locNodes = totalNodes.filter { it.nodeType == "LOCATION" }
        val devNodes = totalNodes.filter { it.nodeType == "DEVICE" }

        sb.append("Matching results for \"$question\":\n\n")

        if (docNodes.isNotEmpty()) {
            sb.append("📄 Documents (${docNodes.size}):\n")
            docNodes.take(4).forEach { doc ->
                sb.append("• ${doc.label}: ${doc.summary}\n")
            }
            sb.append("\n")
        }

        if (imgNodes.isNotEmpty()) {
            sb.append("📸 Photos & Images (${imgNodes.size}):\n")
            imgNodes.take(4).forEach { img ->
                sb.append("• ${img.label}: ${img.summary}\n")
            }
            sb.append("\n")
        }

        if (locNodes.isNotEmpty()) {
            sb.append("📍 Locations:\n")
            locNodes.take(3).forEach { loc ->
                sb.append("• ${loc.label}\n")
            }
            sb.append("\n")
        }

        if (devNodes.isNotEmpty()) {
            sb.append("📷 Cameras / Devices:\n")
            devNodes.take(3).forEach { dev ->
                sb.append("• ${dev.label}\n")
            }
            sb.append("\n")
        }

        if (connections.isNotEmpty()) {
            sb.append("🔗 Connections:\n")
            connections.take(4).forEach { c ->
                sb.append("$c\n")
            }
        }

        return sb.toString().trim()
    }

    private fun chunkText(text: String, chunkSize: Int, overlap: Int): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + chunkSize).coerceAtMost(text.length)
            chunks.add(text.substring(start, end).trim())
            start += (chunkSize - overlap).coerceAtLeast(1)
        }
        return chunks
    }

    private fun createLocalTextAnalysis(fileName: String, text: String): AnalysisResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val words = text.split("\\s+".toRegex())
            .map { it.trim('(', ')', '"', '\'', '.', ',', '!', '?', ':', ';', '{', '}', '[', ']', '<', '>') }
            .filter { it.length in 3..25 && it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' } }
            .filter { it.lowercase() !in setOf("the", "and", "for", "with", "this", "that", "from", "have", "will", "been", "they", "were", "what", "when", "where", "your", "more") }
            .distinct()
            .take(12)

        val entities = mutableListOf<ExtractedEntity>()
        words.take(6).forEach { word ->
            val type = if (word.firstOrNull()?.isUpperCase() == true) "ENTITY" else "TOPIC"
            entities.add(ExtractedEntity(name = word, type = type))
        }

        val baseName = fileName.substringBeforeLast('.')
        baseName.split("_", "-", " ").filter { it.length >= 3 && it.lowercase() !in setOf("doc", "text", "file", "note", "untitled") }.forEach {
            entities.add(ExtractedEntity(name = it.replaceFirstChar { c -> c.uppercase() }, type = "TOPIC"))
        }

        val distinctEntities = entities.distinctBy { it.name.lowercase() }
        val relations = distinctEntities.map {
            ExtractedRelation(source = fileName, relation = "DISCUSSES", target = it.name)
        }

        val snippet = lines.take(3).joinToString(" ").take(160)
        val summary = "Document '$fileName': $snippet"

        return AnalysisResult(
            summary = summary,
            entities = distinctEntities,
            relations = relations,
            tags = listOf(fileName.substringAfterLast('.', "doc")) + words.take(4)
        )
    }

    private fun createLocalImageAnalysis(
        fileName: String,
        metadataSummary: String,
        report: com.example.data.metadata.MetadataReport?
    ): AnalysisResult {
        val entities = mutableListOf<ExtractedEntity>()
        val tags = mutableListOf("Photo", fileName.substringAfterLast('.', "jpg"))

        // Extract camera device
        report?.summary?.let { s ->
            if (!s.make.isNullOrBlank() || !s.model.isNullOrBlank()) {
                val cam = "${s.make.orEmpty()} ${s.model.orEmpty()}".trim()
                if (cam.isNotBlank()) {
                    entities.add(ExtractedEntity(name = cam, type = "DEVICE"))
                    tags.add(cam)
                }
            }
            if (!s.city.isNullOrBlank()) {
                entities.add(ExtractedEntity(name = s.city, type = "LOCATION"))
                tags.add(s.city)
            }
            if (!s.country.isNullOrBlank()) {
                entities.add(ExtractedEntity(name = s.country, type = "LOCATION"))
                tags.add(s.country)
            }
            if (!s.dateTimeOriginal.isNullOrBlank()) {
                val year = s.dateTimeOriginal.take(4)
                if (year.all { it.isDigit() }) {
                    entities.add(ExtractedEntity(name = "Year $year", type = "DATE"))
                }
            }
            s.keywords.forEach { kw ->
                entities.add(ExtractedEntity(name = kw, type = "TOPIC"))
                tags.add(kw)
            }
        }

        // Also extract meaningful terms from the filename itself
        val baseName = fileName.substringBeforeLast('.')
        val nameTokens = baseName.split("_", "-", " ", ".").filter { token ->
            token.length >= 3 && token.none { it.isDigit() } && token.lowercase() !in setOf("img", "pxl", "pic", "photo", "dcm", "vid", "screenshot")
        }
        for (token in nameTokens.take(3)) {
            entities.add(ExtractedEntity(name = token.replaceFirstChar { it.uppercase() }, type = "TOPIC"))
            tags.add(token)
        }

        val summary = if (metadataSummary.isNotBlank()) {
            "Photo $fileName: $metadataSummary"
        } else {
            "Photo $fileName (Format: ${fileName.substringAfterLast('.').uppercase()})"
        }

        val distinctEntities = entities.distinctBy { it.name.lowercase() }
        val relations = distinctEntities.map {
            val rel = if (it.type == "LOCATION") "LOCATED_AT" else if (it.type == "DEVICE") "CAPTURED_WITH" else "DEPICTS"
            ExtractedRelation(source = fileName, relation = rel, target = it.name)
        }

        return AnalysisResult(
            summary = summary,
            entities = distinctEntities,
            relations = relations,
            tags = tags.distinct()
        )
    }

    private fun getCompressedBase64(file: File, uri: android.net.Uri?, maxDimension: Int): String? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val openInput = { if (file.exists() && file.canRead()) file.inputStream() else uri?.let { context.contentResolver.openInputStream(it) } }
            openInput()?.use { BitmapFactory.decodeStream(it, null, boundsOptions) } ?: return null
            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDimension || boundsOptions.outHeight / sampleSize > maxDimension) sampleSize *= 2
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = openInput()?.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return null
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            bitmap.recycle()
            val bytes = stream.toByteArray()
            stream.close()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w("KGRepo", "Bitmap compress failed: ${e.message}")
            null
        }
    }

    private fun hashKey(str: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(str.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
