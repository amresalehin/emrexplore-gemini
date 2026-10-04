package com.example

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FileItem
import com.example.data.model.SortOption
import com.example.data.model.sortFiles
import com.example.ui.components.createAllFilesAccessIntent
import com.example.ui.components.getRequiredStoragePermissions
import com.example.ui.viewmodel.GalleryDateFilter
import com.example.ui.viewmodel.GalleryLocationFilter
import com.example.ui.viewmodel.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun resources_are_defined() {
        assertEquals("emrexplore", context.getString(R.string.app_name))
        assertEquals("Home", context.getString(R.string.nav_home))
        assertEquals("Files", context.getString(R.string.nav_files))
        assertEquals("Gallery", context.getString(R.string.nav_gallery))
    }

    @Test
    fun required_storage_permissions_include_media_images() {
        val permissions = getRequiredStoragePermissions()
        assertTrue(permissions.isNotEmpty())
        assertTrue(permissions.contains(Manifest.permission.READ_MEDIA_IMAGES))
    }

    @Test
    fun all_files_access_intent_is_well_formed() {
        val intent = createAllFilesAccessIntent(context)
        assertNotNull(intent)
        assertNotNull(intent.action)
    }

    @Test
    fun ui_state_defaults_are_stable() {
        val state = UiState()
        assertEquals("", state.homeSearchQuery)
        assertEquals(emptyList<FileItem>(), state.homeSearchResults)
        assertEquals("ALL", state.galleryFilter)
        assertEquals(GalleryDateFilter.ALL, state.galleryDateFilter)
        assertEquals(GalleryLocationFilter.ALL, state.galleryLocationFilter)
    }

    @Test
    fun gallery_state_values_remain_independent() {
        val state = UiState(
            gallerySearchQuery = "beach",
            galleryFilter = "PHOTOS",
            galleryDateFilter = GalleryDateFilter.TODAY,
            galleryLocationFilter = GalleryLocationFilter.WITH_GPS
        )
        assertEquals("beach", state.gallerySearchQuery)
        assertEquals("PHOTOS", state.galleryFilter)
        assertEquals(GalleryDateFilter.TODAY, state.galleryDateFilter)
        assertEquals(GalleryLocationFilter.WITH_GPS, state.galleryLocationFilter)
    }

    @Test
    fun sort_files_keeps_directories_first() {
        val sorted = sortFiles(
            listOf(
                FileItem("z.txt", "/tmp/z.txt", 1, 1, false),
                FileItem("Photos", "/tmp/Photos", 0, 2, true),
                FileItem("a.txt", "/tmp/a.txt", 1, 3, false)
            ),
            SortOption.NAME_ASC
        )
        assertEquals(listOf("Photos", "a.txt", "z.txt"), sorted.map { it.name })
        assertTrue(sorted.first().isDirectory)
    }
}
