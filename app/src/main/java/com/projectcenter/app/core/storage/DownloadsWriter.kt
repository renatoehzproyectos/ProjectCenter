package com.projectcenter.app.core.storage

import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Writes files into the public Download directory.
 */
object DownloadsWriter {

    private val downloadDir: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    fun write(source: File, targetName: String): File {
        downloadDir.mkdirs()
        val dest = File(downloadDir, targetName)
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        return dest
    }

    fun downloadDir(): File = downloadDir
}
