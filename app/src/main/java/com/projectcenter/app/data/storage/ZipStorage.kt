package com.projectcenter.app.data.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.projectcenter.app.core.zip.SafeZipExtractor
import com.projectcenter.app.core.zip.ZipAnalyzer
import com.projectcenter.app.domain.models.SelectedZip
import com.projectcenter.app.domain.models.ZipAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Handles copying a user-selected ZIP into app cache, extracting it safely,
 * and running analysis. All temp files live under the app's cache dir.
 */
class ZipStorage(private val context: Context) {

    private val cacheRoot: File
        get() = File(context.cacheDir, "project_center_zips").also { it.mkdirs() }

    /**
     * Copies the content URI into a local file and returns a SelectedZip.
     */
    suspend fun importZip(uri: Uri): Result<SelectedZip> = withContext(Dispatchers.IO) {
        runCatching {
            val name = queryDisplayName(uri) ?: "project.zip"
            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val dest = File(cacheRoot, "import_${System.currentTimeMillis()}_$safeName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Cannot open ZIP")

            SelectedZip(
                uri = dest.absolutePath,
                displayName = name,
                sizeBytes = dest.length(),
                lastModified = dest.lastModified()
            )
        }
    }

    /**
     * Extracts and analyzes a previously imported ZIP.
     */
    suspend fun extractAndAnalyze(zip: SelectedZip): Result<ZipAnalysis> = withContext(Dispatchers.IO) {
        runCatching {
            val zipFile = File(zip.uri)
            if (!zipFile.exists()) throw IllegalStateException("ZIP no longer available")

            val extractDir = File(cacheRoot, "extract_${System.currentTimeMillis()}")
            val result = SafeZipExtractor.extract(zipFile, extractDir)
            if (!result.success || result.extractedRoot == null) {
                throw IllegalStateException(result.error ?: "Extraction failed")
            }

            ZipAnalyzer.analyze(result.extractedRoot)
        }
    }

    fun clearTemp() {
        cacheRoot.deleteRecursively()
        cacheRoot.mkdirs()
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }
}
