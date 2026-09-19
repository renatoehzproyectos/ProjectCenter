package com.projectcenter.app.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Writes files into the public Downloads folder. Uses MediaStore on Android 10+
 * (no storage permission required to write there), and falls back to direct file
 * access on older versions.
 */
object DownloadsWriter {

    sealed class WriteResult {
        data class Success(val uri: Uri?, val displayName: String) : WriteResult()
        data class Error(val message: String) : WriteResult()
    }

    suspend fun writeStream(
        context: Context,
        displayName: String,
        mimeType: String,
        input: InputStream
    ): WriteResult = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val itemUri = resolver.insert(collection, values)
                    ?: return@withContext WriteResult.Error("Could not create file in Downloads")

                resolver.openOutputStream(itemUri)?.use { out ->
                    input.copyTo(out)
                } ?: return@withContext WriteResult.Error("Could not open Downloads for writing")

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)

                WriteResult.Success(itemUri, displayName)
            } else {
                @Suppress("DEPRECATION")
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadDir.mkdirs()
                val dest = File(downloadDir, displayName)
                FileOutputStream(dest).use { out -> input.copyTo(out) }
                WriteResult.Success(Uri.fromFile(dest), displayName)
            }
        } catch (e: Exception) {
            WriteResult.Error(e.message ?: "Failed to write to Downloads")
        }
    }

    suspend fun writeText(
        context: Context,
        displayName: String,
        text: String
    ): WriteResult = writeStream(context, displayName, "text/plain", text.byteInputStream())

    /** Unique, filesystem-safe file name, e.g. "myrepo-backup-2026-09-18_1200.zip" */
    fun timestampedName(baseName: String, extension: String): String {
        val safeBase = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return "${safeBase}_$stamp.$extension"
    }
}
