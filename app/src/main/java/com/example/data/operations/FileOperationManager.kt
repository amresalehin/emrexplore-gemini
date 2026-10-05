package com.example.data.operations

import android.os.Build
import android.os.StatFs
import com.example.data.model.ConflictResolution
import com.example.data.model.FileConflict
import com.example.data.model.FileOperationProgress
import com.example.data.model.OperationStatus
import com.example.data.model.OperationType
import com.example.data.performance.PerformanceMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class FileOperationManager(
    private val onFilesMutated: suspend (
        relocatedPaths: List<Pair<String, String>>,
        removedPaths: List<String>,
        affectedPaths: List<String>
    ) -> Unit,
    private val deleteFile: suspend (path: String, toTrash: Boolean) -> Boolean = { path, _ -> File(path).let { file ->
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    } }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _progress = MutableStateFlow(FileOperationProgress())
    val progress: StateFlow<FileOperationProgress> = _progress.asStateFlow()

    private val isPaused = AtomicBoolean(false)
    private var pauseSignal = CompletableDeferred<Unit>().apply { complete(Unit) }

    private var conflictDeferred: CompletableDeferred<ConflictResolution>? = null

    // For retry
    private var lastOperationType: OperationType? = null
    private var lastSourcePaths: List<String> = emptyList()
    private var lastTargetDir: String = ""
    private var lastToTrash: Boolean = true

    fun shutdown() {
        currentJob?.cancel()
        scope.cancel()
    }

    fun startCopy(sourcePaths: List<String>, targetDir: String) {
        startOperation(OperationType.COPY, sourcePaths, targetDir, toTrash = false)
    }

    fun startMove(sourcePaths: List<String>, targetDir: String) {
        startOperation(OperationType.MOVE, sourcePaths, targetDir, toTrash = false)
    }

    fun startDelete(paths: List<String>, toTrash: Boolean) {
        startOperation(OperationType.DELETE, paths, targetDir = "", toTrash = toTrash)
    }

    private fun startOperation(
        type: OperationType,
        sourcePaths: List<String>,
        targetDir: String,
        toTrash: Boolean
    ) {
        currentJob?.cancel()
        isPaused.set(false)
        pauseSignal = CompletableDeferred<Unit>().apply { complete(Unit) }
        conflictDeferred = null

        lastOperationType = type
        lastSourcePaths = sourcePaths
        lastTargetDir = targetDir
        lastToTrash = toTrash

        val opId = UUID.randomUUID().toString()

        currentJob = scope.launch {
            val validSources = sourcePaths.map { File(it) }.filter { it.exists() }
            if (validSources.isEmpty()) {
                _progress.update {
                    it.copy(
                        id = opId,
                        type = type,
                        status = OperationStatus.ERROR,
                        errorMessage = "Selected files do not exist."
                    )
                }
                return@launch
            }

            // 1. Pre-calculate total files and bytes
            var totalFiles = 0
            var totalBytes = 0L

            for (src in validSources) {
                if (src.isDirectory) {
                    val tree = collectAllFiles(src)
                    totalFiles += tree.size + 1 // +1 for the folder itself
                    totalBytes += tree.sumOf { it.length() }
                } else {
                    totalFiles += 1
                    totalBytes += src.length()
                }
            }

            // 2. Pre-flight destination validation for COPY / MOVE.
            // Do this before creating the destination directory so a descendant
            // target cannot become part of the source tree during the operation.
            if (type == OperationType.COPY || type == OperationType.MOVE) {
                if (targetDir.isBlank()) {
                    _progress.update {
                        it.copy(
                            id = opId,
                            type = type,
                            status = OperationStatus.ERROR,
                            errorMessage = "A destination folder is required."
                        )
                    }
                    return@launch
                }
                for (src in validSources) {
                    val destinationError = validateDestination(src, File(targetDir))
                    if (destinationError != null) {
                        _progress.update {
                            it.copy(
                                id = opId,
                                type = type,
                                status = OperationStatus.ERROR,
                                errorMessage = destinationError
                            )
                        }
                        return@launch
                    }
                }

                val destDir = File(targetDir)
                if (!destDir.exists()) destDir.mkdirs()

                val usableSpace = getAvailableBytes(destDir)
                // Add 10MB safety margin
                val requiredBytes = totalBytes + (10L * 1024L * 1024L)
                if (usableSpace in 1 until requiredBytes) {
                    val reqMb = requiredBytes / (1024 * 1024)
                    val availMb = usableSpace / (1024 * 1024)
                    _progress.update {
                        it.copy(
                            id = opId,
                            type = type,
                            status = OperationStatus.ERROR,
                            errorMessage = "Insufficient storage space. Requires ${reqMb}MB, but only ${availMb}MB is available."
                        )
                    }
                    return@launch
                }
            }

            // Initialize progress
            _progress.update {
                FileOperationProgress(
                    id = opId,
                    type = type,
                    status = OperationStatus.RUNNING,
                    targetDirectory = targetDir,
                    totalFiles = totalFiles,
                    totalBytes = totalBytes,
                    filesProcessed = 0,
                    bytesProcessed = 0L
                )
            }

            val affectedDirectories = mutableSetOf<String>()
            if (targetDir.isNotBlank()) affectedDirectories.add(targetDir)
            val relocatedPaths = mutableListOf<Pair<String, String>>()
            val removedPaths = mutableListOf<String>()

            var processedFilesCount = 0
            var processedBytesSum = 0L
            var lastSpeedTimestamp = System.currentTimeMillis()
            var bytesSinceLastSpeedCalc = 0L

            try {
                for (src in validSources) {
                    checkPausedOrCancelled()
                    val parent = src.parent
                    if (parent != null) affectedDirectories.add(parent)

                    when (type) {
                        OperationType.COPY -> {
                            val dest = File(targetDir, src.name)
                            copyRecursivelyWithProgress(
                                src = src,
                                dest = dest,
                                onByteChunk = { chunk ->
                                    processedBytesSum += chunk
                                    bytesSinceLastSpeedCalc += chunk

                                    val now = System.currentTimeMillis()
                                    val elapsed = now - lastSpeedTimestamp
                                    if (elapsed >= 500) {
                                        val speed = (bytesSinceLastSpeedCalc * 1000L) / elapsed
                                        val remainingBytes = (totalBytes - processedBytesSum).coerceAtLeast(0L)
                                        val eta = if (speed > 0) (remainingBytes * 1000L) / speed else 0L

                                        PerformanceMonitor.updateOperationSpeed(
                                            FileOperationProgress(speedBytesPerSec = speed).formattedSpeed
                                        )

                                        _progress.update { current ->
                                            current.copy(
                                                bytesProcessed = processedBytesSum,
                                                speedBytesPerSec = speed,
                                                estimatedRemainingTimeMs = eta
                                            )
                                        }
                                        lastSpeedTimestamp = now
                                        bytesSinceLastSpeedCalc = 0L
                                    }
                                },
                                onFileCompleted = { completedFile ->
                                    processedFilesCount++
                                    _progress.update { current ->
                                        current.copy(
                                            currentFileName = completedFile.name,
                                            filesProcessed = processedFilesCount,
                                            bytesProcessed = processedBytesSum
                                        )
                                    }
                                }
                            )
                            relocatedPaths.add(src.absolutePath to dest.absolutePath)
                        }

                        OperationType.MOVE -> {
                            val dest = File(targetDir, src.name)
                            // Try atomic rename first if same filesystem
                            val movedQuickly = try {
                                if (!dest.exists()) src.renameTo(dest) else false
                            } catch (e: Exception) {
                                false
                            }

                            if (movedQuickly) {
                                val fileSize = if (src.isDirectory) 0L else src.length()
                                processedBytesSum += fileSize
                                processedFilesCount++
                                _progress.update { current ->
                                    current.copy(
                                        currentFileName = src.name,
                                        filesProcessed = processedFilesCount,
                                        bytesProcessed = processedBytesSum
                                    )
                                }
                            } else {
                                // Fallback streaming copy and delete
                                val copiedCompletely = copyRecursivelyWithProgress(
                                    src = src,
                                    dest = dest,
                                    onByteChunk = { chunk ->
                                        processedBytesSum += chunk
                                        bytesSinceLastSpeedCalc += chunk
                                    },
                                    onFileCompleted = { completedFile ->
                                        processedFilesCount++
                                        _progress.update { current ->
                                            current.copy(
                                                currentFileName = completedFile.name,
                                                filesProcessed = processedFilesCount,
                                                bytesProcessed = processedBytesSum
                                            )
                                        }
                                    }
                                )
                                if (!copiedCompletely) {
                                    throw IOException("Move could not complete because one or more conflicts were skipped")
                                }
                                if (src.exists() && !src.deleteRecursively()) {
                                    throw IOException("Copied successfully but could not delete source")
                                }
                            }
                            relocatedPaths.add(src.absolutePath to dest.absolutePath)
                        }

                        OperationType.DELETE -> {
                            _progress.update { current -> current.copy(currentFileName = src.name) }
                            checkPausedOrCancelled()
                            if (!deleteFile(src.absolutePath, toTrash)) {
                                throw IOException("Could not delete " + src.name)
                            }
                            processedFilesCount++
                            removedPaths.add(src.absolutePath)
                            _progress.update { current ->
                                current.copy(filesProcessed = processedFilesCount)
                            }
                        }

                        else -> {}
                    }
                }

                _progress.update {
                    it.copy(
                        status = OperationStatus.COMPLETED,
                        filesProcessed = totalFiles,
                        bytesProcessed = totalBytes,
                        speedBytesPerSec = 0L,
                        estimatedRemainingTimeMs = 0L
                    )
                }

                onFilesMutated(
                    relocatedPaths = relocatedPaths,
                    removedPaths = removedPaths,
                    affectedPaths = affectedDirectories.toList()
                )

            } catch (e: CancellationException) {
                _progress.update {
                    it.copy(
                        status = OperationStatus.CANCELLED,
                        speedBytesPerSec = 0L,
                        estimatedRemainingTimeMs = 0L
                    )
                }
                onFilesMutated(
                    relocatedPaths = relocatedPaths,
                    removedPaths = removedPaths,
                    affectedPaths = affectedDirectories.toList()
                )
            } catch (e: Exception) {
                _progress.update {
                    it.copy(
                        status = OperationStatus.ERROR,
                        errorMessage = e.localizedMessage ?: "File operation failed",
                        speedBytesPerSec = 0L
                    )
                }
                onFilesMutated(
                    relocatedPaths = relocatedPaths,
                    removedPaths = removedPaths,
                    affectedPaths = affectedDirectories.toList()
                )
            }
        }
    }

    private suspend fun deleteRecursivelyWithProgress(
        dir: File,
        onFileDeleted: suspend (File) -> Unit
    ) {
        checkPausedOrCancelled()
        val children = dir.listFiles() ?: throw IOException("Could not list " + dir.name)
        for (child in children) {
            checkPausedOrCancelled()
            if (child.isDirectory) {
                deleteRecursivelyWithProgress(child, onFileDeleted)
            } else {
                if (!child.delete()) throw IOException("Could not delete " + child.name)
                onFileDeleted(child)
            }
        }
        checkPausedOrCancelled()
        if (!dir.delete()) throw IOException("Could not delete " + dir.name)
        onFileDeleted(dir)
    }
    private suspend fun copyRecursivelyWithProgress(
        src: File,
        dest: File,
        onByteChunk: (Long) -> Unit,
        onFileCompleted: (File) -> Unit
    ): Boolean {
        checkPausedOrCancelled()

        return if (src.isDirectory) {
            if (!dest.exists() && !dest.mkdirs()) throw IOException("Could not create destination")
            val children = src.listFiles() ?: return false
            var complete = true
            for (child in children) {
                val childDest = File(dest, child.name)
                if (!copyRecursivelyWithProgress(child, childDest, onByteChunk, onFileCompleted)) {
                    complete = false
                }
            }
            if (complete) onFileCompleted(src)
            complete
        } else {
            var targetFile = dest
            if (dest.exists()) {
                // Conflict detection
                val conflict = FileConflict(
                    sourcePath = src.absolutePath,
                    destinationPath = dest.absolutePath,
                    fileName = src.name,
                    existingSize = dest.length(),
                    newSize = src.length(),
                    existingLastModified = dest.lastModified(),
                    newLastModified = src.lastModified()
                )

                val resolution = askConflictResolution(conflict)
                when (resolution) {
                    ConflictResolution.SKIP -> {
                        return false
                    }
                    ConflictResolution.KEEP_BOTH -> {
                        targetFile = generateUniqueFile(dest)
                    }
                    ConflictResolution.OVERWRITE -> {
                        targetFile = dest
                    }
                }
            }

            streamCopyFile(src, targetFile, onByteChunk)
            onFileCompleted(src)
            true
        }
    }

    private suspend fun streamCopyFile(src: File, dest: File, onByteChunk: (Long) -> Unit) {
        withContext(Dispatchers.IO) {
            if (src.canonicalFile == dest.canonicalFile) {
                throw IOException("Source and destination are the same file.")
            }
            val parent = dest.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()

            val buffer = ByteArray(65536) // 64 KB buffer
            var inStream: FileInputStream? = null
            var outStream: FileOutputStream? = null

            try {
                inStream = FileInputStream(src)
                outStream = FileOutputStream(dest)

                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    checkPausedOrCancelled()
                    outStream.write(buffer, 0, bytesRead)
                    onByteChunk(bytesRead.toLong())
                }
                outStream.flush()
            } catch (e: Exception) {
                // On cancellation or error, delete incomplete file to prevent corruption
                if (dest.exists() && dest.length() < src.length()) {
                    dest.delete()
                }
                throw e
            } finally {
                try { inStream?.close() } catch (e: IOException) {}
                try { outStream?.close() } catch (e: IOException) {}
            }
        }
    }

    private suspend fun checkPausedOrCancelled() {
        if (isPaused.get()) {
            pauseSignal.await()
        }
    }

    private suspend fun askConflictResolution(conflict: FileConflict): ConflictResolution {
        val deferred = CompletableDeferred<ConflictResolution>()
        conflictDeferred = deferred

        _progress.update {
            it.copy(
                status = OperationStatus.CONFLICT,
                conflict = conflict
            )
        }

        return deferred.await()
    }

    fun resolveConflict(resolution: ConflictResolution) {
        conflictDeferred?.complete(resolution)
        conflictDeferred = null
        _progress.update {
            it.copy(
                status = if (isPaused.get()) OperationStatus.PAUSED else OperationStatus.RUNNING,
                conflict = null
            )
        }
    }

    fun pause() {
        if (!isPaused.get() && _progress.value.status == OperationStatus.RUNNING) {
            isPaused.set(true)
            pauseSignal = CompletableDeferred()
            _progress.update { it.copy(status = OperationStatus.PAUSED, speedBytesPerSec = 0L) }
        }
    }

    fun resume() {
        if (isPaused.get()) {
            isPaused.set(false)
            pauseSignal.complete(Unit)
            _progress.update { it.copy(status = OperationStatus.RUNNING) }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        isPaused.set(false)
        pauseSignal.complete(Unit)
        conflictDeferred?.cancel()
        _progress.update {
            it.copy(
                status = OperationStatus.CANCELLED,
                speedBytesPerSec = 0L,
                estimatedRemainingTimeMs = 0L
            )
        }
    }

    fun retry() {
        val type = lastOperationType ?: return
        if (lastSourcePaths.isNotEmpty()) {
            startOperation(type, lastSourcePaths, lastTargetDir, lastToTrash)
        }
    }

    fun dismiss() {
        _progress.update { FileOperationProgress(status = OperationStatus.IDLE) }
    }

    private fun validateDestination(source: File, targetDir: File): String? {
        return try {
            val canonicalSource = source.canonicalFile
            val canonicalTargetDir = targetDir.canonicalFile
            val canonicalDestination = File(canonicalTargetDir, source.name).canonicalFile

            when {
                canonicalSource == canonicalDestination ->
                    "Source and destination are the same path."
                source.isDirectory && canonicalDestination.absolutePath.startsWith(canonicalSource.absolutePath + File.separator) ->
                    "Cannot copy or move a folder into itself or one of its descendants."
                else -> null
            }
        } catch (e: IOException) {
            "Could not validate the destination path: ${e.localizedMessage ?: "I/O error"}"
        }
    }

    private fun generateUniqueFile(file: File): File {
        val parent = file.parentFile ?: return file
        val nameWithoutExt = file.nameWithoutExtension
        val ext = file.extension
        var count = 1
        var candidate = File(parent, "$nameWithoutExt ($count)${if (ext.isNotEmpty()) ".$ext" else ""}")
        while (candidate.exists()) {
            count++
            candidate = File(parent, "$nameWithoutExt ($count)${if (ext.isNotEmpty()) ".$ext" else ""}")
        }
        return candidate
    }

    private fun collectAllFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.add(dir)

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val list = current.listFiles() ?: continue
            for (f in list) {
                if (f.isDirectory) {
                    stack.add(f)
                } else {
                    result.add(f)
                }
            }
        }
        return result
    }

    private fun getAvailableBytes(dir: File): Long {
        return try {
            val target = if (dir.exists()) dir else (dir.parentFile ?: dir)
            val stat = StatFs(target.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            dir.usableSpace
        }
    }
}
