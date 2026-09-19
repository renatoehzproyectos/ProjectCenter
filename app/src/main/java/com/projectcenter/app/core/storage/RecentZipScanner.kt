package com.projectcenter.app.core.storage

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RecentZipFile(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)

/**
 * Scans /storage/emulated/0/Download for .zip files, most recent first.
 */
object RecentZipScanner {

    suspend fun findRecentZips(limit: Int = 5): List<RecentZipFile> = withContext(Dispatchers.IO) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists() || !downloadDir.canRead()) return@withContext emptyList()

        downloadDir.listFiles { f -> f.isFile && f.extension.equals("zip", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            ?.map { RecentZipFile(it, it.name, it.length(), it.lastModified()) }
            ?: emptyList()
    }
}
