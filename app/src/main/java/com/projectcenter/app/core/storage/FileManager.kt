package com.projectcenter.app.core.storage

import com.projectcenter.app.domain.models.ManagedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lightweight project/download file manager. Starts at Download, ordered by lastModified DESC.
 * Not a full file explorer — focused on project ZIP workflow.
 */
class FileManager {

    suspend fun listDirectory(path: String): List<ManagedFile> = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return@withContext emptyList()
        dir.listFiles()
            ?.map { it.toManaged() }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    suspend fun search(path: String, query: String): List<ManagedFile> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withContext listDirectory(path)
        listDirectory(path).filter { it.name.lowercase().contains(q) }
    }

    fun parentPath(path: String): String? {
        val parent = File(path).parentFile ?: return null
        return parent.absolutePath
    }

    fun isAtDownloads(path: String): Boolean {
        return path == RecentZipScanner.downloadsPath() ||
            path.trimEnd('/') == RecentZipScanner.downloadsPath().trimEnd('/')
    }

    companion object {
        fun File.toManaged(): ManagedFile {
            val ext = if (isDirectory) null else name.substringAfterLast('.', "").ifEmpty { null }
            return ManagedFile(
                name = name,
                path = absolutePath,
                isDirectory = isDirectory,
                sizeBytes = if (isDirectory) 0L else length(),
                lastModified = lastModified(),
                extension = ext?.lowercase()
            )
        }
    }
}
