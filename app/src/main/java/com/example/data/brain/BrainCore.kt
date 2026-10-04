package com.example.data.brain

import java.security.MessageDigest
import java.util.Locale

object BrainChunker {
    fun chunk(
        text: String,
        maxChars: Int = 1600,
        overlap: Int = 240,
        maxChunks: Int = 120
    ): List<String> {
        if (text.isBlank() || maxChars <= 0 || maxChunks <= 0) return emptyList()

        val result = mutableListOf<String>()
        var start = 0
        val step = (maxChars - overlap).coerceAtLeast(1)

        while (start < text.length && result.size < maxChunks) {
            val end = (start + maxChars).coerceAtMost(text.length)
            val chunk = text.substring(start, end).trim()
            if (chunk.isNotBlank()) result += chunk
            if (end == text.length) break
            start += step
        }

        return result
    }
}

object BrainPrivacy {
    fun redactSensitive(value: String): String {
        return value
            .replace(Regex("(?im)^\\s*(GPS|Location):.*(?:\\R|$)"), "")
            .replace(
                Regex("(?i)(exact coordinates|geographic coordinates|coordinates?)\\s*[:=]?\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?")
            ) { match ->
                match.groupValues[1] + ": [redacted]"
            }
    }
}

object BrainIdentity {
    fun normalize(value: String): String =
        value.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")

    fun stableKey(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }

    fun fileNodeId(path: String): String = "file:" + stableKey(path)

    fun entityNodeId(type: String, name: String): String =
        "entity:" + type + ":" + stableKey(normalize(name))

    fun chunkId(path: String, index: Int): String =
        "chunk:" + stableKey(path) + ":" + index
}
