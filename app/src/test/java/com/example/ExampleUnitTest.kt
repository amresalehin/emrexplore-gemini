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
        val image = FileItem("photo.jpg", "/tmp/photo.jpg", 1, 1, false)
        val audio = FileItem("voice.m4a", "/tmp/voice.m4a", 1, 1, false)
        val document = FileItem("notes.md", "/tmp/notes.md", 1, 1, false)

        assertTrue(image.isImage)
        assertTrue(audio.isAudio)
        assertTrue(document.isDocument)
        assertTrue(document.isTextEditable)
    }
}
