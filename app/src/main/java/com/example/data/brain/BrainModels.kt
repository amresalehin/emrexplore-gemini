package com.example.data.brain

data class BrainTopicFile(
    val node: BrainNodeEntity,
    val score: Float
)

data class RagAnswer(
    val answer: String,
    val sourceChunks: List<BrainChunkEntity> = emptyList(),
    val connectedNodes: List<BrainNodeEntity> = emptyList(),
    val isSuccessful: Boolean = true,
    val latencyMs: Long = 0L
)

data class ConnectedDotsItem(
    val fileNode: BrainNodeEntity,
    val relationship: String,
    val targetNode: BrainNodeEntity,
    val snippet: String = ""
)

data class AttachedAiFile(
    val file: java.io.File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val mimeType: String = "",
    val size: Long = file.length(),
    val isImage: Boolean = false
)

