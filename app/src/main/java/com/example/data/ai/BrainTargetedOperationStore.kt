package com.example.data.ai

import android.content.Context
import org.json.JSONArray

/**
 * Durable checkpoint for gallery Brain processing.
 *
 * The pending path set is durable outside the ViewModel. A successful Brain run
 * clears it; pausing/cancelling the WorkManager job leaves it available for resume,
 * even after process death.
 */
class BrainTargetedOperationStore(context: Context) {
    companion object {
        private const val PREFS_NAME = "brain_targeted_operation"
        private const val KEY_PATHS = "pending_paths"
        private const val KEY_FORCE_PATHS = "force_paths"
        private const val KEY_TOTAL = "total_count"
        private const val KEY_COMPLETED = "completed_count"
        private const val KEY_PAUSED = "paused"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun addPaths(paths: Collection<String>, force: Boolean = false) {
        val clean = paths.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (clean.isEmpty()) return

        val existing = pendingPaths().toMutableList()
        val hadPendingWork = existing.isNotEmpty()
        val newPaths = clean.filterNot(existing::contains)
        if (newPaths.isNotEmpty()) {
            existing += newPaths
        }

        val forcePaths = prefs.getStringSet(KEY_FORCE_PATHS, emptySet()).orEmpty().toMutableSet()
        if (force) forcePaths.addAll(clean)

        val newOperation = !hadPendingWork && totalCount() == 0
        val nextTotal = if (newOperation) newPaths.size else totalCount() + newPaths.size
        val nextCompleted = if (newOperation) 0 else completedCount()

        prefs.edit()
            .putString(KEY_PATHS, JSONArray(existing).toString())
            .putStringSet(KEY_FORCE_PATHS, forcePaths)
            .putInt(KEY_TOTAL, nextTotal)
            .putInt(KEY_COMPLETED, nextCompleted)
            .putBoolean(KEY_PAUSED, false)
            .commit()
    }

    @Synchronized
    fun pendingPaths(): List<String> {
        val raw = prefs.getString(KEY_PATHS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun isForce(path: String): Boolean =
        prefs.getStringSet(KEY_FORCE_PATHS, emptySet()).orEmpty().contains(path)

    @Synchronized
    fun markCompleted(path: String) {
        val remaining = pendingPaths().filterNot { it == path }
        val forcePaths = prefs.getStringSet(KEY_FORCE_PATHS, emptySet()).orEmpty().toMutableSet()
        forcePaths.remove(path)
        prefs.edit()
            .putString(KEY_PATHS, JSONArray(remaining).toString())
            .putStringSet(KEY_FORCE_PATHS, forcePaths)
            .putInt(KEY_COMPLETED, (completedCount() + 1).coerceAtMost(totalCount()))
            .commit()
    }

    @Synchronized
    fun totalCount(): Int = prefs.getInt(KEY_TOTAL, 0)

    @Synchronized
    fun completedCount(): Int = prefs.getInt(KEY_COMPLETED, 0)

    @Synchronized
    fun hasPendingWork(): Boolean = pendingPaths().isNotEmpty()

    @Synchronized
    fun isPaused(): Boolean = prefs.getBoolean(KEY_PAUSED, false)

    @Synchronized
    fun setPaused(paused: Boolean) {
        prefs.edit().putBoolean(KEY_PAUSED, paused).commit()
    }

    @Synchronized
    fun clear() {
        prefs.edit()
            .remove(KEY_PATHS)
            .remove(KEY_FORCE_PATHS)
            .remove(KEY_TOTAL)
            .remove(KEY_COMPLETED)
            .putBoolean(KEY_PAUSED, false)
            .commit()
    }
}
