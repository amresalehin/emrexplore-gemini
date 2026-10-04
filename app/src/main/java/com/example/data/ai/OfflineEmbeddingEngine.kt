package com.example.data.ai

import org.json.JSONArray
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * High-performance, deterministic on-device offline embedding model.
 * Provides fallback semantic text representations (256-dimensional unit vectors)
 * without requiring network connectivity, API keys, or heavy external ML runtimes.
 *
 * Employs subword character n-gram feature hashing (Murmur3/FNV-1a dual-hash trick),
 * semantic concept subspace anchor projections, and L2 unit-norm sphere projection.
 */
object OfflineEmbeddingEngine {

    const val MODEL_NAME = "offline-hash-embed-v1"
    const val EMBEDDING_DIM = 256

    private val STOP_WORDS = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and",
        "any", "are", "aren't", "as", "at", "be", "because", "been", "before", "being",
        "below", "between", "both", "but", "by", "can't", "cannot", "could", "couldn't",
        "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during",
        "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
        "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here",
        "here's", "hers", "herself", "him", "himself", "his", "how", "how's", "i",
        "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it", "it's",
        "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself",
        "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought",
        "our", "ours", "ourselves", "out", "over", "own", "same", "shan't", "she",
        "she'd", "she'll", "she's", "should", "shouldn't", "so", "some", "such",
        "than", "that", "that's", "the", "their", "theirs", "them", "themselves",
        "then", "there", "there's", "these", "they", "they'd", "they'll", "they're",
        "they've", "this", "those", "through", "to", "too", "under", "until", "up",
        "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were",
        "weren't", "what", "what's", "when", "when's", "where", "where's", "which",
        "while", "who", "who's", "whom", "why", "why's", "with", "won't", "would",
        "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your", "yours",
        "yourself", "yourselves"
    )

    // Semantic domain anchors mapped to primary subspace centroids
    private val DOMAIN_ANCHORS = mapOf(
        "finance" to setOf("receipt", "invoice", "billing", "payment", "bank", "tax", "price", "total", "amount", "usd", "cost", "fee", "expense", "salary", "statement"),
        "legal" to setOf("contract", "agreement", "terms", "license", "policy", "privacy", "clause", "signed", "party", "confidential"),
        "tech" to setOf("code", "kotlin", "java", "android", "function", "class", "query", "database", "api", "git", "bug", "server", "json", "python", "script", "xml", "file"),
        "media" to setOf("photo", "camera", "image", "lens", "iso", "focal", "shutter", "resolution", "portrait", "landscape", "gallery", "video", "audio", "jpeg", "png"),
        "travel" to setOf("travel", "trip", "flight", "hotel", "vacation", "city", "country", "location", "gps", "airport", "tour", "beach", "mountain"),
        "work" to setOf("project", "meeting", "roadmap", "task", "deadline", "client", "quarter", "report", "presentation", "summary", "plan", "notes"),
        "personal" to setOf("personal", "family", "health", "doctor", "medical", "prescription", "recipe", "home", "diary", "journal")
    )

    /**
     * Embeds a single text passage into a 256-dimensional unit vector.
     */
    fun embedText(text: String): FloatArray {
        if (text.isBlank()) return FloatArray(EMBEDDING_DIM)

        val vector = FloatArray(EMBEDDING_DIM)
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return vector

        val termFrequencies = mutableMapOf<String, Int>()
        for (token in tokens) {
            termFrequencies[token] = (termFrequencies[token] ?: 0) + 1
        }

        // 1. Word token and Subword n-gram hashing
        for ((token, count) in termFrequencies) {
            val weight = ln(1.0f + count)

            // Whole token projection
            projectFeature(token, weight * 1.5f, vector)

            // Subword 3-gram to 5-gram projections (captures roots, plurals, typos)
            val len = token.length
            if (len >= 3) {
                for (n in 3..minOf(5, len)) {
                    for (i in 0..(len - n)) {
                        val subword = token.substring(i, i + n)
                        projectFeature(subword, weight * 0.6f, vector)
                    }
                }
            }

            // Domain concept subspace bias
            for ((domain, anchors) in DOMAIN_ANCHORS) {
                if (token in anchors) {
                    val domainOffset = (domain.hashCode() and 0x7FFFFFFF) % (EMBEDDING_DIM / 4)
                    val dim = (domainOffset * 4 + 0) % EMBEDDING_DIM
                    vector[dim] += weight * 2.0f
                }
            }
        }

        // 2. L2 Unit Normalization
        var sumSquares = 0.0
        for (i in vector.indices) {
            sumSquares += vector[i] * vector[i]
        }
        val norm = sqrt(sumSquares)
        if (norm > 0.000001) {
            val inv = (1.0 / norm).toFloat()
            for (i in vector.indices) {
                vector[i] *= inv
            }
        }

        return vector
    }

    /**
     * Batch embeds multiple text chunks.
     */
    fun embedTextPassages(passages: List<String>): List<FloatArray> {
        return passages.map { embedText(it) }
    }

    /**
     * Computes cosine similarity between two unit vectors: dot(a, b).
     * Output clamped between 0.0f and 1.0f.
     */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return dot.toFloat().coerceIn(0f, 1f)
    }

    /**
     * Serializes embedding vector to JSON string.
     */
    fun embeddingToJson(vector: FloatArray): String {
        val array = JSONArray()
        for (value in vector) {
            // Round to 5 decimal places to minimize storage size
            val rounded = (value * 100000f).toInt() / 100000.0
            array.put(rounded)
        }
        return array.toString()
    }

    /**
     * Parses JSON string into FloatArray.
     */
    fun parseEmbedding(json: String?): FloatArray {
        if (json.isNullOrBlank()) return FloatArray(0)
        return try {
            val array = JSONArray(json)
            FloatArray(array.length()) { array.optDouble(it, 0.0).toFloat() }
        } catch (_: Exception) {
            FloatArray(0)
        }
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 && it !in STOP_WORDS }
    }

    private fun projectFeature(feature: String, weight: Float, vector: FloatArray) {
        val h1 = fnv1a(feature)
        val h2 = murmur3Part(feature)

        val index = (h1 and 0x7FFFFFFF) % EMBEDDING_DIM
        val sign = if ((h2 and 1) == 0) 1.0f else -1.0f

        vector[index] += sign * weight
    }

    private fun fnv1a(s: String): Int {
        var hash = 0x811c9dc5.toInt()
        for (ch in s) {
            hash = hash xor ch.code
            hash *= 0x01000193
        }
        return hash
    }

    private fun murmur3Part(s: String): Int {
        var h = 0x12345678
        for (ch in s) {
            var k = ch.code
            k *= 0xcc9e2d51.toInt()
            k = Integer.rotateLeft(k, 15)
            k *= 0x1b873593

            h = h xor k
            h = Integer.rotateLeft(h, 13)
            h = h * 5 + 0xe6546b64.toInt()
        }
        h = h xor s.length
        h = h xor (h ushr 16)
        h *= 0x85ebca6b.toInt()
        h = h xor (h ushr 13)
        h *= 0xc2b2ae35.toInt()
        h = h xor (h ushr 16)
        return h
    }
}
