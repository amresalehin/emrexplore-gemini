package com.example.data.performance

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates I/O priority between high-priority interactive operations
 * (folder opening, scrolling, navigation, sorting) and low-priority background operations
 * (SQLite indexing, thumbnail generation, large calculations).
 */
object IoPriorityCoordinator {
    private val activeInteractiveCount = AtomicInteger(0)
    private val lastInteractiveTimestampMs = AtomicLong(0L)
    private val isYieldingFlag = AtomicBoolean(false)
    val totalYieldCount = AtomicInteger(0)

    // Duration of inactivity required after user navigation before indexer resumes full speed
    const val IDLE_QUIET_PERIOD_MS = 2000L

    fun notifyInteractiveActivity() {
        lastInteractiveTimestampMs.set(System.currentTimeMillis())
    }

    /**
     * Executes a high-priority interactive block (e.g. folder listing, search typing, page loading).
     * Background tasks will automatically yield and pause while this is running.
     */
    suspend fun <T> withInteractivePriority(block: suspend () -> T): T {
        activeInteractiveCount.incrementAndGet()
        lastInteractiveTimestampMs.set(System.currentTimeMillis())
        try {
            return block()
        } finally {
            lastInteractiveTimestampMs.set(System.currentTimeMillis())
            activeInteractiveCount.decrementAndGet()
        }
    }

    fun isInteractiveActive(): Boolean {
        if (activeInteractiveCount.get() > 0) return true
        val elapsed = System.currentTimeMillis() - lastInteractiveTimestampMs.get()
        return elapsed < IDLE_QUIET_PERIOD_MS
    }

    /**
     * Background tasks (e.g. storage indexing) MUST invoke this periodically.
     * When interactive browsing is happening, this suspends until the user has been idle for [IDLE_QUIET_PERIOD_MS].
     */
    suspend fun yieldIfInteractive() {
        if (isInteractiveActive()) {
            isYieldingFlag.set(true)
            totalYieldCount.incrementAndGet()
            PerformanceMonitor.recordYield()
            while (isInteractiveActive()) {
                delay(200L)
            }
            isYieldingFlag.set(false)
        }
        // Base throttle delay to prevent 100% bus lockup on slow eMMC/UFS storage
        delay(12L)
    }

    fun reset() {
        activeInteractiveCount.set(0)
        lastInteractiveTimestampMs.set(0L)
        totalYieldCount.set(0)
    }
}

data class PerformanceMetrics(
    val lastFolderOpenLatencyMs: Long = 0L,
    val lastPagedLoadLatencyMs: Long = 0L,
    val firstVisibleFileLatencyMs: Long = 0L,
    val totalIoYields: Int = 0,
    val folderCacheHits: Int = 0,
    val statCacheHits: Int = 0,
    val diskReadsAvoided: Int = 0,
    val activeOperationSpeed: String = "--"
)

object PerformanceMonitor {
    private val _metrics = MutableStateFlow(PerformanceMetrics())
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()

    private val folderCacheHitsCounter = AtomicInteger(0)
    private val statCacheHitsCounter = AtomicInteger(0)
    private val diskReadsAvoidedCounter = AtomicInteger(0)

    fun recordFolderOpen(latencyMs: Long) {
        _metrics.update {
            it.copy(
                lastFolderOpenLatencyMs = latencyMs,
                totalIoYields = IoPriorityCoordinator.totalYieldCount.get()
            )
        }
    }

    fun recordPagedLoad(latencyMs: Long) {
        _metrics.update {
            it.copy(
                lastPagedLoadLatencyMs = latencyMs,
                totalIoYields = IoPriorityCoordinator.totalYieldCount.get()
            )
        }
    }

    fun recordFirstVisible(latencyMs: Long) {
        _metrics.update { it.copy(firstVisibleFileLatencyMs = latencyMs) }
    }

    fun recordYield() {
        _metrics.update { it.copy(totalIoYields = IoPriorityCoordinator.totalYieldCount.get()) }
    }

    fun recordFolderCacheHit() {
        val count = folderCacheHitsCounter.incrementAndGet()
        _metrics.update { it.copy(folderCacheHits = count) }
    }

    fun recordStatCacheHit(avoidedReads: Int = 1) {
        val count = statCacheHitsCounter.incrementAndGet()
        val totalAvoided = diskReadsAvoidedCounter.addAndGet(avoidedReads)
        _metrics.update {
            it.copy(
                statCacheHits = count,
                diskReadsAvoided = totalAvoided
            )
        }
    }

    fun updateOperationSpeed(speedStr: String) {
        _metrics.update { it.copy(activeOperationSpeed = speedStr) }
    }
}
