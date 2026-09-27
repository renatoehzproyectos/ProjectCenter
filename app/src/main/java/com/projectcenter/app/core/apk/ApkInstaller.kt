package com.projectcenter.app.core.apk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * Installs an APK via the system package installer.
 */
object ApkInstaller {

    fun install(context: Context, apkFile: File) {
        if (!apkFile.exists()) return
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun findApks(dir: File): List<File> {
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
            .toList()
    }
}
