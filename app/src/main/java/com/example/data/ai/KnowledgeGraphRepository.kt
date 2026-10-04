package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.data.local.AiProviderConfigEntity
import com.example.data.local.AppDatabase
import com.example.data.local.KgEdgeEntity
import com.example.data.local.KgNodeEntity
import com.example.data.local.RagChunkEntity
import com.example.data.metadata.MetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

class KnowledgeGraphRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val kgDao = db.kgDao()
    private val ragDao = db.ragDao()
    private val aiConfigDao = db.aiProviderConfigDao()
    private val fileIndexDao = db.fileIndexDao()
    private val mediaMetadataDao = db.mediaMetadataDao()
    private val client = AiProviderClient()
    private val metadataExtractor = MetadataExtractor(context)

    val allNodesFlow: Flow<List<KgNodeEntity>> = kgDao.getAllNodesFlow()
    val allEdgesFlow: Flow<List<KgEdgeEntity>> = kgDao.getAllEdgesFlow()
    val nodeCountFlow: Flow<Int> = kgDao.getNodeCountFlow()
    val edgeCountFlow: Flow<Int> = kgDao.getEdgeCountFlow()
    val chunkCountFlow: Flow<Int> = ragDao.getChunkCountFlow()
    val aiConfigFlow: Flow<AiProviderConfigEntity?> = aiConfigDao.getConfigFlow()

    suspend fun getAiConfig(): AiProviderConfigEntity {
        return withContext(Dispatchers.IO) {
            aiConfigDao.getConfig() ?: AiProviderConfigEntity()
        }
    }

    suspend fun saveAiConfig(config: AiProviderConfigEntity) = withContext(Dispatchers.IO) {
        aiConfigDao.saveConfig(config)
    }

    suspend fun testConnection(config: AiProviderConfigEntity): ConnectionTestResult {
        return client.testConnection(config)
    }

    suspend fun clearGraph() = withContext(Dispatchers.IO) {
        kgDao.clearAllNodes()
        kgDao.clearAllEdges()
        ragDao.clearAllChunks()
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
    suspend fun indexFile(file: File, config: AiProviderConfigEntity) {
        indexFile(file, null, config)
    }

    suspend fun indexFile(file: File, uri: android.net.Uri?, config: AiProviderConfigEntity) = withContext(Dispatchers.IO) {
        val canReadDirectly = file.exists() && file.isFile && file.canRead()
        if (!canReadDirectly && uri == null) return@withContext
        val ext = file.extension.lowercase()

        val isImage = ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
        val isDoc = ext in setOf("txt", "md", "json", "csv", "xml", "html", "htm", "log", "kt", "java", "py", "js", "ts", "c", "cpp", "properties", "sql", "yaml", "yml", "pdf", "conf", "ini", "tsv", "gradle", "kts", "env")

        if (!isImage && !isDoc) return@withContext

        val filePath = file.absolutePath

        // Clean existing graph nodes & chunks for this file
        kgDao.deleteNodeByFilePath(filePath)
        kgDao.deleteEdgesForNode("doc:$filePath")
        kgDao.deleteEdgesForNode("img:$filePath")
        ragDao.deleteChunksForFile(filePath)

        if (isDoc) {
            indexDocumentInternal(file, uri, config)
        } else {
            indexImageInternal(file, uri, config)
        }
    }

    private suspend fun indexDocumentInternal(file: File, uri: android.net.Uri?, config: AiProviderConfigEntity) {
        val filePath = file.absolutePath
        val contentText = try {
            if (file.exists() && file.canRead()) {
                file.bufferedReader().use { it.readText().take(50000) }
            } else if (uri != null) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(50000) }.orEmpty()
            } else ""
        } catch (e: Exception) {
            Log.w("KGRepo", "Could not read doc text: ${e.message}")
            return
        }

        if (contentText.isBlank()) return

        // 1. Chunk document
        val chunks = chunkText(contentText, chunkSize = 1200, overlap = 200)
        val ragChunks = chunks.mapIndexed { idx, chunk ->
            RagChunkEntity(
                chunkId = hashKey("$filePath:$idx"),
                filePath = filePath,
                fileType = "DOCUMENT",
                chunkIndex = idx,
                content = chunk,
                tagsJson = "[]"
            )
        }
        ragDao.insertChunks(ragChunks)

        // 2. Extract entities and relations
        val analysis = if (config.isEnabled && config.apiKey.isNotBlank()) {
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
            degree = analysis.entities.size
        )
        nodesToInsert.add(docNode)

        // Link with folder if available
        val parentDir = file.parentFile?.name
        if (!parentDir.isNullOrBlank() && parentDir !in setOf("/", "0", "emulated", "storage")) {
            val folderId = "folder:${parentDir.lowercase()}"
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
            val entId = "ent:${ent.name.trim().lowercase()}"
            val entNode = KgNodeEntity(
                id = entId,
                label = ent.name.trim(),
                nodeType = ent.type.ifBlank { "TOPIC" },
                summary = "Entity in ${file.name}"
            )
            nodesToInsert.add(entNode)

            edgesToInsert.add(
                KgEdgeEntity(
                    sourceNodeId = docNodeId,
                    targetNodeId = entId,
                    relation = "MENTIONS",
                    evidenceSnippet = "Mentioned in ${file.name}"
                )
            )
        }

        for (rel in analysis.relations) {
            val sourceId = if (rel.source.equals(file.name, ignoreCase = true)) docNodeId else "ent:${rel.source.lowercase()}"
            val targetId = "ent:${rel.target.lowercase()}"
            edgesToInsert.add(
                KgEdgeEntity(
                    sourceNodeId = sourceId,
                    targetNodeId = targetId,
                    relation = rel.relation,
                    evidenceSnippet = rel.evidence
                )
            )
        }

        kgDao.insertNodes(nodesToInsert)
        kgDao.insertEdges(edgesToInsert)
    }

    private suspend fun indexImageInternal(file: File, uri: android.net.Uri?, config: AiProviderConfigEntity) {
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
        metadataReport?.summary?.let { s ->
            if (!s.make.isNullOrBlank()) metadataSummaryBuilder.append("Camera: ${s.make} ${s.model.orEmpty()}. ")
            if (s.latitude != null && s.longitude != null) metadataSummaryBuilder.append("GPS: ${s.latitude}, ${s.longitude}. ")
            if (!s.city.isNullOrBlank()) metadataSummaryBuilder.append("Location: ${s.city}, ${s.country.orEmpty()}. ")
            if (!s.dateTimeOriginal.isNullOrBlank()) metadataSummaryBuilder.append("Date: ${s.dateTimeOriginal}. ")
            if (s.keywords.isNotEmpty()) metadataSummaryBuilder.append("Keywords: ${s.keywords.joinToString()}. ")
            if (!s.title.isNullOrBlank()) metadataSummaryBuilder.append("Title: ${s.title}. ")
            if (!s.description.isNullOrBlank()) metadataSummaryBuilder.append("Caption: ${s.description}. ")
        }

        val metadataSummary = metadataSummaryBuilder.toString()

        // 2. Call AI vision if enabled & key provided
        val base64Thumbnail = if (config.isEnabled && config.apiKey.isNotBlank() && file.exists() && file.canRead()) {
            getCompressedBase64(file, maxDimension = 768)
        } else null

        val analysis = if (config.isEnabled && config.apiKey.isNotBlank()) {
            client.analyzeImage(base64Thumbnail, metadataSummary, file.name, config)
        } else {
            createLocalImageAnalysis(file.name, metadataSummary, metadataReport)
        }

        // 3. Store RAG chunk for visual caption and OCR
        val chunkContent = "Image: ${file.name}\nDescription: ${analysis.summary}\nMetadata: $metadataSummary"
        val tagsJsonArray = JSONArray(analysis.tags).toString()
        val chunk = RagChunkEntity(
            chunkId = hashKey("img:$filePath:0"),
            filePath = filePath,
            fileType = "IMAGE",
            chunkIndex = 0,
            content = chunkContent,
            tagsJson = tagsJsonArray
        )
        ragDao.insertChunks(listOf(chunk))

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
            val folderId = "folder:${parentDir.lowercase()}"
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
            val entId = "ent:${ent.name.trim().lowercase()}"
            val entNode = KgNodeEntity(
                id = entId,
                label = ent.name.trim(),
                nodeType = ent.type.ifBlank { "TOPIC" },
                summary = "Entity detected in ${file.name}"
            )
            nodesToInsert.add(entNode)

            edgesToInsert.add(
                KgEdgeEntity(
                    sourceNodeId = imgNodeId,
                    targetNodeId = entId,
                    relation = "DEPICTS",
                    evidenceSnippet = "Depicted in photo ${file.name}"
                )
            )
        }

        // Add GPS Location node if available
        metadataReport?.summary?.let { s ->
            if (s.latitude != null && s.longitude != null) {
                val locLabel = if (!s.city.isNullOrBlank()) "${s.city}, ${s.country.orEmpty()}".trim() else "Geo (${String.format("%.3f", s.latitude)}, ${String.format("%.3f", s.longitude)})"
                val locId = "ent:${locLabel.lowercase()}"
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
                        evidenceSnippet = "Taken at coordinates ${s.latitude}, ${s.longitude}"
                    )
                )
            }
        }

        kgDao.insertNodes(nodesToInsert)
        kgDao.insertEdges(edgesToInsert)
    }

    /**
     * Executes RAG: Hybrid retrieval over text chunks + Knowledge Graph traversal + real synthesis.
     */
    suspend fun queryRag(question: String): RagAnswer = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val config = getAiConfig()

        val cleanQuestion = question.trim()
        val stopWords = setOf(
            "what", "where", "when", "which", "show", "find", "tell", "about", "with",
            "from", "that", "this", "have", "does", "matching", "matches", "photos",
            "photo", "images", "image", "docs", "document", "documents", "files", "file"
        )
        val rawKeywords = cleanQuestion.split("\\s+".toRegex())
            .map { it.trim('?', '.', ',', '!', '"', '\'', ':', ';').lowercase() }
            .filter { it.length >= 2 }

        val searchKeywords = rawKeywords.filter { it !in stopWords }.ifEmpty { rawKeywords }

        val isAskingImages = rawKeywords.any { it in setOf("photo", "photos", "image", "images", "picture", "pictures", "camera", "jpg", "png", "screenshot", "screenshots") }
        val isAskingDocs = rawKeywords.any { it in setOf("doc", "docs", "document", "documents", "note", "notes", "text", "txt", "pdf", "receipt", "plan", "summary") }
        val isAskingLocation = rawKeywords.any { it in setOf("location", "locations", "place", "places", "where", "city", "country", "gps", "travel", "paris", "tokyo", "trip") }

        // 1. Search relevant RAG chunks
        val matchedChunks = mutableListOf<RagChunkEntity>()
        for (kw in searchKeywords.take(6)) {
            val found = ragDao.searchChunks(kw, limit = 8)
            for (c in found) {
                if (matchedChunks.none { it.chunkId == c.chunkId }) {
                    matchedChunks.add(c)
                }
            }
        }

        // 2. Search relevant Knowledge Graph nodes
        val matchedNodes = mutableListOf<KgNodeEntity>()
        for (kw in searchKeywords.take(6)) {
            val nodes = kgDao.searchNodes(kw)
            for (n in nodes) {
                if (matchedNodes.none { it.id == n.id }) {
                    matchedNodes.add(n)
                }
            }
        }

        // If specific keyword matching was sparse, broaden based on query intent
        if (isAskingImages) {
            val imageNodes = kgDao.getNodesByType("IMAGE", limit = 10)
            for (n in imageNodes) {
                if (matchedNodes.none { it.id == n.id }) matchedNodes.add(n)
            }
        }
        if (isAskingDocs) {
            val docNodes = kgDao.getNodesByType("DOCUMENT", limit = 10)
            for (n in docNodes) {
                if (matchedNodes.none { it.id == n.id }) matchedNodes.add(n)
            }
        }
        if (isAskingLocation) {
            val locNodes = kgDao.getNodesByType("LOCATION", limit = 10)
            for (n in locNodes) {
                if (matchedNodes.none { it.id == n.id }) matchedNodes.add(n)
            }
        }

        // 3. Traverse 1-hop edges from matched nodes to connect dots
        val connectionExcerpts = mutableListOf<String>()
        val connectedNodes = mutableListOf<KgNodeEntity>()

        for (node in matchedNodes.take(8)) {
            val edges = kgDao.getEdgesForNode(node.id)
            for (edge in edges.take(4)) {
                val neighborId = if (edge.sourceNodeId == node.id) edge.targetNodeId else edge.sourceNodeId
                val neighbor = kgDao.getNode(neighborId)
                if (neighbor != null && neighbor.id != node.id) {
                    if (connectedNodes.none { it.id == neighbor.id }) {
                        connectedNodes.add(neighbor)
                    }
                    val snippet = edge.evidenceSnippet.ifBlank { "${node.label} ${edge.relation.lowercase()} ${neighbor.label}" }
                    connectionExcerpts.add("• [${node.label}] ──(${edge.relation})──> [${neighbor.label}]: $snippet")
                }
            }
        }

        // 4. Build context
        val contextText = StringBuilder()
        matchedChunks.take(8).forEach { chunk ->
            val fileName = chunk.filePath.substringAfterLast(File.separator)
            contextText.append("--- Source: $fileName (${chunk.fileType}) ---\n")
            contextText.append("${chunk.content}\n\n")
        }

        // 5. Synthesize answer using AI client or local real-data summarizer
        val answerText = if (config.isEnabled && config.apiKey.isNotBlank()) {
            try {
                client.generateRagAnswer(
                    question = cleanQuestion,
                    contextText = contextText.toString(),
                    graphContext = connectionExcerpts.joinToString("\n"),
                    config = config
                )
            } catch (e: Exception) {
                synthesizeRealOfflineAnswer(cleanQuestion, matchedChunks, matchedNodes, connectedNodes, connectionExcerpts)
            }
        } else {
            synthesizeRealOfflineAnswer(cleanQuestion, matchedChunks, matchedNodes, connectedNodes, connectionExcerpts)
        }

        val allCitations = (matchedNodes + connectedNodes).distinctBy { it.id }
        RagAnswer(
            answer = answerText,
            sourceChunks = matchedChunks,
            connectedNodes = allCitations,
            isSuccessful = true,
            latencyMs = System.currentTimeMillis() - startTime
        )
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

    private fun getCompressedBase64(file: File, maxDimension: Int): String? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)

            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            bitmap.recycle()
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
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
