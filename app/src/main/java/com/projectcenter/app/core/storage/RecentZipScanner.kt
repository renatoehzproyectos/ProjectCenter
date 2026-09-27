package com.projectcenter.app.core.storage

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RecentZipFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long
)

/**
 * Scans /storage/emulated/0/Download for recent .zip files.
 * Reused by Projects screen and File Manager — do not replace.
 */
object RecentZipScanner {

    private val downloadDir: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    suspend fun findRecentZips(limit: Int = 10): List<RecentZipFile> = withContext(Dispatchers.IO) {
        val dir = downloadDir
        if (!dir.exists() || !dir.canRead()) return@withContext emptyList()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".zip", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            ?.map {
                RecentZipFile(
                    name = it.name,
                    path = it.absolutePath,
                    sizeBytes = it.length(),
                    lastModified = it.lastModified()
                )
            }
            ?: emptyList()
    }

    fun downloadsPath(): String = downloadDir.absolutePath
}
