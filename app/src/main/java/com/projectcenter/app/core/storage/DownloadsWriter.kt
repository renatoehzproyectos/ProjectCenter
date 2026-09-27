package com.projectcenter.app.core.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes files into the public Download directory (MediaStore on Q+).
 */
object DownloadsWriter {

    sealed class WriteResult {
        data class Success(val displayName: String, val pathOrUri: String) : WriteResult()
        data class Error(val message: String) : WriteResult()
    }

    fun timestampedName(base: String, extension: String): String {
        val safe = base.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = extension.removePrefix(".")
        return "${safe}_$ts.$ext"
    }

    fun downloadDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    fun write(source: File, targetName: String): File {
        downloadDir().mkdirs()
        val dest = File(downloadDir(), targetName)
        source.inputStream().use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        return dest
    }

    fun writeStream(
        context: Context,
        fileName: String,
        mimeType: String,
        input: InputStream
    ): WriteResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return WriteResult.Error("Could not create MediaStore entry")
                resolver.openOutputStream(uri)?.use { out -> input.copyTo(out) }
                    ?: return WriteResult.Error("Could not open output stream")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                WriteResult.Success(fileName, uri.toString())
            } else {
                val dir = downloadDir().also { it.mkdirs() }
                val dest = File(dir, fileName)
                FileOutputStream(dest).use { out -> input.copyTo(out) }
                WriteResult.Success(fileName, dest.absolutePath)
            }
        } catch (e: Exception) {
            WriteResult.Error(e.message ?: "Write failed")
        }
    }

    fun writeText(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String = "text/plain"
    ): WriteResult {
        return writeStream(context, fileName, mimeType, content.byteInputStream())
    }
}
