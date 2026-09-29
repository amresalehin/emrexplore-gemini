package com.example.data.model

import java.util.Locale

enum class OperationType(val displayName: String) {
    COPY("Copying"),
    MOVE("Moving"),
    DELETE("Deleting"),
    ZIP("Compressing"),
    UNZIP("Extracting")
}

enum class OperationStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    ERROR,
    CONFLICT
}

enum class ConflictResolution {
    OVERWRITE,
    SKIP,
    KEEP_BOTH
}

data class FileConflict(
    val sourcePath: String,
    val destinationPath: String,
    val fileName: String,
    val existingSize: Long,
    val newSize: Long,
    val existingLastModified: Long,
    val newLastModified: Long
)

data class FileOperationProgress(
    val id: String = "",
    val type: OperationType = OperationType.COPY,
    val status: OperationStatus = OperationStatus.IDLE,
    val targetDirectory: String = "",
    val currentFileName: String = "",
    val currentFileBytes: Long = 0L,
    val currentFileTotalBytes: Long = 0L,
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val estimatedRemainingTimeMs: Long = 0L,
    val errorMessage: String? = null,
    val conflict: FileConflict? = null
) {
    val isActive: Boolean
        get() = status == OperationStatus.RUNNING || status == OperationStatus.PAUSED || status == OperationStatus.CONFLICT

    val percentOverall: Float
        get() {
            if (totalBytes > 0L) {
                return (bytesProcessed.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
            }
            if (totalFiles > 0) {
                return (filesProcessed.toDouble() / totalFiles.toDouble()).coerceIn(0.0, 1.0).toFloat()
            }
            return 0f
        }

    val percentCurrentFile: Float
        get() {
            if (currentFileTotalBytes > 0L) {
                return (currentFileBytes.toDouble() / currentFileTotalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
            }
            return 0f
        }

    val formattedSpeed: String
        get() {
            if (speedBytesPerSec <= 0L) return "-- MB/s"
            val mb = speedBytesPerSec / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format(Locale.US, "%.1f MB/s", mb)
            } else {
                val kb = speedBytesPerSec / 1024.0
                String.format(Locale.US, "%.0f KB/s", kb)
            }
        }

    val formattedEta: String
        get() {
            if (estimatedRemainingTimeMs <= 0L || speedBytesPerSec <= 0L) return "--"
            val seconds = (estimatedRemainingTimeMs / 1000).coerceAtLeast(1)
            val minutes = seconds / 60
            val remainingSec = seconds % 60
            return if (minutes > 0) {
                String.format(Locale.US, "%dm %02ds remaining", minutes, remainingSec)
            } else {
                String.format(Locale.US, "%02ds remaining", remainingSec)
            }
        }

    val progressSummary: String
        get() {
            val pct = (percentOverall * 100).toInt()
            return when (status) {
                OperationStatus.RUNNING -> "${type.displayName} $pct% — $filesProcessed/$totalFiles files — $formattedSpeed"
                OperationStatus.PAUSED -> "Paused ($pct% complete) — $filesProcessed/$totalFiles files"
                OperationStatus.COMPLETED -> "${type.displayName.replace("ing", "ed")} $totalFiles files successfully"
                OperationStatus.CANCELLED -> "Operation cancelled ($filesProcessed/$totalFiles completed)"
                OperationStatus.ERROR -> "Error: ${errorMessage ?: "Operation failed"}"
                OperationStatus.CONFLICT -> "Conflict: $currentFileName already exists"
                OperationStatus.IDLE -> ""
            }
        }
}
