package com.example

import com.example.data.model.FileItem
import com.example.data.model.SortOption
import com.example.data.model.sortFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun sortFiles_keepsDirectoriesFirst_andSortsByName() {
        val items = listOf(
            FileItem("z.txt", "/tmp/z.txt", 1, 1, false),
            FileItem("Photos", "/tmp/Photos", 0, 2, true),
            FileItem("a.txt", "/tmp/a.txt", 1, 3, false)
        )

        val sorted = sortFiles(items, SortOption.NAME_ASC)

        assertEquals(listOf("Photos", "a.txt", "z.txt"), sorted.map { it.name })
        assertTrue(sorted.first().isDirectory)
    }

    @Test
    fun fileItem_classifies_common_extensions() {
        val image = FileItem("photo.jpg", "/tmp/photo.jpg", 1, 1, false, extension = "jpg")
        val audio = FileItem("voice.m4a", "/tmp/voice.m4a", 1, 1, false, extension = "m4a")
        val document = FileItem("notes.md", "/tmp/notes.md", 1, 1, false, extension = "md")

        assertTrue(image.isImage)
        assertTrue(audio.isAudio)
        assertTrue(document.isDocument)
        assertTrue(document.isTextEditable)
    }

    @Test
    fun offlineEmbeddingEngine_producesNormalizedVector() {
        val text = "Invoice payment receipt for software subscription"
        val vec = com.example.data.ai.OfflineEmbeddingEngine.embedText(text)

        assertEquals(com.example.data.ai.OfflineEmbeddingEngine.EMBEDDING_DIM, vec.size)
        var normSq = 0.0
        for (v in vec) normSq += v * v
        val norm = kotlin.math.sqrt(normSq)
        assertTrue("Norm should be approximately 1.0, was $norm", norm > 0.98 && norm < 1.02)
    }

    @Test
    fun offlineEmbeddingEngine_computesHigherSimilarityForRelatedContent() {
        val finance1 = com.example.data.ai.OfflineEmbeddingEngine.embedText("Monthly cloud billing invoice receipt payment")
        val finance2 = com.example.data.ai.OfflineEmbeddingEngine.embedText("Annual server expense invoice statement amount")
        val travel = com.example.data.ai.OfflineEmbeddingEngine.embedText("Summer vacation beach hotel flight travel trip")

        val simRelated = com.example.data.ai.OfflineEmbeddingEngine.cosine(finance1, finance2)
        val simUnrelated = com.example.data.ai.OfflineEmbeddingEngine.cosine(finance1, travel)

        assertTrue("Related finance documents ($simRelated) should be more similar than travel ($simUnrelated)", simRelated > simUnrelated)
    }
}
