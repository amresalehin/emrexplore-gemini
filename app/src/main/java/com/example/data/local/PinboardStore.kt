package com.example.data.local

import android.content.Context
import com.example.data.model.FileItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight persistent pinboard.
 *
 * Pins are intentionally path-based so they work for documents, folders and
 * MediaStore-backed images/videos without another Room migration.
 */
data class PinnedItem(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val isDirectory: Boolean
)

class PinboardStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getAll(): List<PinnedItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val path = item.optString("path")
                    if (path.isBlank()) continue
                    add(
                        PinnedItem(
                            path = path,
                            name = item.optString("name").ifBlank { path.substringAfterLast('/') },
                            size = item.optLong("size", 0L),
                            lastModified = item.optLong("lastModified", 0L),
                            mimeType = item.optString("mimeType"),
                            isDirectory = item.optBoolean("isDirectory", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun isPinned(path: String): Boolean = getAll().any { it.path == path }

    @Synchronized
    fun toggle(file: FileItem): Boolean {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.path == file.path }
        val nowPinned: Boolean
        if (index >= 0) {
            current.removeAt(index)
            nowPinned = false
        } else {
            current.add(
                PinnedItem(
                    path = file.path,
                    name = file.name,
                    size = file.size,
                    lastModified = file.lastModified,
                    mimeType = file.mimeType,
                    isDirectory = file.isDirectory
                )
            )
            nowPinned = true
        }
        save(current)
        return nowPinned
    }

    @Synchronized
    fun removePath(path: String) {
        val remaining = getAll().filterNot { it.path == path }
        save(remaining)
    }

    private fun save(items: List<PinnedItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("path", item.path)
                    put("name", item.name)
                    put("size", item.size)
                    put("lastModified", item.lastModified)
                    put("mimeType", item.mimeType)
                    put("isDirectory", item.isDirectory)
                }
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "emrexplore_pinboard"
        private const val KEY_ITEMS = "items"
    }
}
