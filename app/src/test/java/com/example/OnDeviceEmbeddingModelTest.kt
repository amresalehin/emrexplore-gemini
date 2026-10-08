package com.example

import com.example.data.brain.BertWordPieceTokenizer
import com.example.data.brain.BrainVectorCodec
import com.example.data.brain.OnDeviceBrainModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OnDeviceEmbeddingModelTest {

    @Test
    fun wordPieceTokenizer_addsSpecialTokensAndSubwords() {
        val vocab = File.createTempFile("brain-vocab", ".txt")
        try {
            val tokens = buildList {
                add("[PAD]")
                add("[unused0]")
                add("[unused1]")
                add("[unused2]")
                add("[unused3]")
                addAll(List(95) { "[unused${it + 4}]" })
                add("[UNK]")      // 100
                add("[unused101]")
                add("[CLS]")      // 102
                add("[SEP]")      // 103
                add("hello")      // 104
                add("world")      // 105
                add("##s")        // 106
            }
            vocab.writeText(tokens.joinToString("\n") + "\n")

            val tokenizer = BertWordPieceTokenizer(vocab, maxTokens = 8)
            val encoded = tokenizer.encode("Hello worlds")

            assertEquals(5, encoded.size)
            assertEquals(102, encoded[0])
            assertEquals(104, encoded[1])
            assertEquals(105, encoded[2])
            assertEquals(106, encoded[3])
            assertEquals(103, encoded[encoded.lastIndex])
        } finally {
            vocab.delete()
        }
    }

    @Test
    fun offlineModelCatalog_exposesDistinctSelectableModels() {
        val models = OnDeviceBrainModelCatalog.all
        assertTrue(models.size >= 2)
        assertEquals(models.size, models.map { it.id }.distinct().size)
        assertTrue(models.all { it.embeddingDimension == 384 })
    }

    @Test
    fun vectorCodec_roundTripsAndKeepsNegativeCosineValid() {
        val a = floatArrayOf(1f, 0f, -1f)
        val encoded = BrainVectorCodec.toJson(a)
        val decoded = BrainVectorCodec.fromJson(encoded)

        assertTrue(decoded.contentEquals(a))
        assertTrue(BrainVectorCodec.cosine(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)) < -0.9f)
    }
}
