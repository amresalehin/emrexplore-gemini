package com.example

import com.example.data.ai.ProviderType
import com.example.data.brain.BrainChunker
import com.example.data.brain.BrainIdentity
import com.example.data.brain.BrainLexicalScorer
import com.example.data.brain.BrainPrivacy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainCoreTest {

    @Test
    fun chunker_respects_max_size_and_limit() {
        val text = "0123456789".repeat(20)
        val chunks = BrainChunker.chunk(text, maxChars = 40, overlap = 10, maxChunks = 3)

        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.length <= 40 })
    }

    @Test
    fun chunker_is_deterministic() {
        val text = "A document with enough content to require multiple chunks."
        assertEquals(
            BrainChunker.chunk(text, 20, 5, 10),
            BrainChunker.chunk(text, 20, 5, 10)
        )
    }

    @Test
    fun privacy_removes_precise_location_lines() {
        val input = "Camera: Pixel\nGPS: 23.8103, 90.4125\nLocation: Dhaka, Bangladesh\nTitle: Trip"
        val output = BrainPrivacy.redactSensitive(input)

        assertFalse(output.contains("23.8103"))
        assertFalse(output.contains("90.4125"))
        assertFalse(output.contains("Location: Dhaka"))
        assertTrue(output.contains("Camera: Pixel"))
        assertTrue(output.contains("Title: Trip"))
    }

    @Test
    fun privacy_redacts_coordinates_inside_text() {
        val output = BrainPrivacy.redactSensitive("Taken at exact coordinates 23.8103, 90.4125.")

        assertFalse(output.contains("23.8103"))
        assertFalse(output.contains("90.4125"))
        assertTrue(output.contains("[redacted]"))
    }

    @Test
    fun identity_is_stable_and_type_namespaced() {
        val a = BrainIdentity.stableKey("/storage/emulated/0/Documents/a.txt")
        val b = BrainIdentity.stableKey("/storage/emulated/0/Documents/a.txt")

        assertEquals(a, b)
        assertNotEquals(
            BrainIdentity.entityNodeId("PERSON", "Alex"),
            BrainIdentity.entityNodeId("ORGANIZATION", "Alex")
        )
        assertTrue(BrainIdentity.fileNodeId("/x").startsWith("file:"))
        assertTrue(BrainIdentity.chunkId("/x", 0).startsWith("chunk:"))
    }


    @Test
    fun lexicalScorer_ignores_generic_query_words() {
        val tokens = BrainLexicalScorer.meaningfulTokens("what is the project file")
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun lexicalScorer_requires_meaningful_overlap() {
        val tokens = BrainLexicalScorer.meaningfulTokens("why is the sync failing")
        assertEquals(listOf("sync", "failing"), tokens)
        assertTrue(BrainLexicalScorer.score(
            "why is the sync failing",
            tokens,
            "This file explains sync behavior and failing retries.",
            "/storage/test/sync.kt"
        ) >= 0.32f)
        assertEquals(
            0f,
            BrainLexicalScorer.score(
                "why is the sync failing",
                tokens,
                "A generic unrelated document.",
                "/storage/test/notes.txt"
            ),
            0.001f
        )
    }

    @Test
    fun lexicalScorer_rejects_low_signal_single_term() {
        assertFalse(BrainLexicalScorer.isSignificantQuery(listOf("ab")))
        assertFalse(BrainLexicalScorer.isSignificantQuery(listOf("the")))
        assertTrue(BrainLexicalScorer.isSignificantQuery(listOf("photos")))
    }

    @Test
    fun lexicalScorer_requires_half_query_coverage() {
        val tokens = BrainLexicalScorer.meaningfulTokens("invoice march")
        assertEquals(
            0.5f,
            BrainLexicalScorer.coverage(
                "invoice march",
                tokens,
                "March summary",
                "/storage/notes.txt"
            ),
            0.001f
        )
        assertEquals(
            0.0f,
            BrainLexicalScorer.coverage(
                "invoice march",
                tokens,
                "Unrelated notes",
                "/storage/notes.txt"
            ),
            0.001f
        )
    }

    @Test
    fun provider_identity_is_preserved() {
        assertEquals(ProviderType.OPENROUTER, ProviderType.fromString("openrouter"))
        assertEquals(ProviderType.GROQ, ProviderType.fromString("groq"))
        assertEquals(ProviderType.CUSTOM, ProviderType.fromString("custom"))
        assertEquals(ProviderType.OPENAI_COMPATIBLE, ProviderType.fromString("openai_compatible"))
    }


}
