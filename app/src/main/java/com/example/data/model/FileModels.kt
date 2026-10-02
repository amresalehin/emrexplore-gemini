package com.example.data.model

import android.net.Uri

data class FileItem(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val mimeType: String = "",
    val extension: String = "",
    val isFavorite: Boolean = false,
    val childCount: Int = 0,
    val uri: Uri? = null
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/") || extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

    val isVideo: Boolean
        get() = mimeType.startsWith("video/") || extension.lowercase() in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp")

    val isAudio: Boolean
        get() = mimeType.startsWith("audio/") || extension.lowercase() in listOf("mp3", "m4a", "wav", "ogg", "flac", "aac")

    val isDocument: Boolean
        get() = extension.lowercase() in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "json", "xml", "html", "kt", "java", "py")

    val isArchive: Boolean
        get() = extension.lowercase() in listOf("zip", "rar", "7z", "tar", "gz", "bz2")

    val isApk: Boolean
        get() = extension.lowercase() in listOf("apk", "xapk", "apks")

    val isTextEditable: Boolean
        get() = extension.lowercase() in listOf("txt", "md", "json", "xml", "html", "kt", "java", "py", "csv", "log", "properties", "yaml", "yml", "gradle")
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val dateAdded: Long,
    val mimeType: String,
    val duration: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val bucketId: String = "",
    val bucketName: String = "",
    val isVideo: Boolean = false,
    val isFavorite: Boolean = false
)

data class MediaAlbum(
    val id: String,
    val name: String,
    val coverUri: Uri? = null,
    val coverPath: String? = null,
    val itemCount: Int = 0
)

enum class CategoryType(val displayName: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    APKS("APKs"),
    DOWNLOADS("Downloads")
}

enum class SortOption(val title: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_DESC("Date (Newest first)"),
    DATE_ASC("Date (Oldest first)"),
    SIZE_DESC("Size (Largest first)"),
    SIZE_ASC("Size (Smallest first)"),
    TYPE("File Type")
}

enum class ViewMode {
    DETAILED_LIST,
    COMPACT_LIST,
    GRID
}

enum class GroupByOption(val label: String) {
    NONE("None"),
    DATE("Date"),
    TYPE("File Type"),
    SIZE("File Size")
}

data class StorageStats(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val imagesBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val documentsBytes: Long = 0L,
    val archivesBytes: Long = 0L,
    val apksBytes: Long = 0L,
    val otherBytes: Long = 0L
)

enum class ExplorerFilterType(val label: String) {
    ALL("All"),
    FOLDERS("Folders"),
    DOCUMENTS("Documents"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    ARCHIVES("Archives"),
    APKS("APKs")
}

enum class ExplorerDateFilter(val label: String) {
    ALL("Any time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 days"),
    THIS_MONTH("This month"),
    THIS_YEAR("This year")
}

enum class ExplorerSizeFilter(val label: String) {
    ALL("Any size"),
    SMALL("< 1 MB"),
    MEDIUM("1 MB – 50 MB"),
    LARGE("> 50 MB")
}

enum class ExplorerSearchScope(val label: String) {
    CURRENT_FOLDER("This Folder"),
    SUBFOLDERS("Subfolders"),
    ALL_STORAGE("All Storage")
}

fun sortFiles(list: List<FileItem>, option: SortOption): List<FileItem> {
    val (dirs, files) = list.partition { it.isDirectory }
    val sortedDirs = when (option) {
        SortOption.NAME_ASC -> dirs.sortedBy { it.name.lowercase() }
        SortOption.NAME_DESC -> dirs.sortedByDescending { it.name.lowercase() }
        SortOption.DATE_DESC -> dirs.sortedByDescending { it.lastModified }
        SortOption.DATE_ASC -> dirs.sortedBy { it.lastModified }
        SortOption.SIZE_DESC -> dirs.sortedByDescending { it.childCount }
        SortOption.SIZE_ASC -> dirs.sortedBy { it.childCount }
        SortOption.TYPE -> dirs.sortedBy { it.name.lowercase() }
    }

    val sortedFiles = when (option) {
        SortOption.NAME_ASC -> files.sortedBy { it.name.lowercase() }
        SortOption.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
        SortOption.DATE_DESC -> files.sortedByDescending { it.lastModified }
        SortOption.DATE_ASC -> files.sortedBy { it.lastModified }
        SortOption.SIZE_DESC -> files.sortedByDescending { it.size }
        SortOption.SIZE_ASC -> files.sortedBy { it.size }
        SortOption.TYPE -> files.sortedBy { it.extension }
    }

    return sortedDirs + sortedFiles
}
