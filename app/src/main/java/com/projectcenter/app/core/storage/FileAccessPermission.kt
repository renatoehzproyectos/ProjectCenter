package com.projectcenter.app.core.storage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Handles the permission needed to enumerate/watch files that live directly under
 * /storage/emulated/0/Download (e.g. ZIPs saved there by other apps, such as an AI
 * coding agent), so Home can surface the most recent ones.
 *
 * - Android 10 and below: classic READ_EXTERNAL_STORAGE runtime permission.
 * - Android 11+: "All files access" (MANAGE_EXTERNAL_STORAGE) is required to browse
 *   another app's files inside a shared folder like Download; this is a special
 *   permission granted from a system settings screen, not a runtime dialog.
 */
object FileAccessPermission {

    fun hasAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Only meaningful pre-Android 11; use ActivityResultContracts.RequestPermission for this string. */
    const val LEGACY_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE

    /** Builds the intent to launch the "All files access" settings screen (Android 11+). */
    fun manageAllFilesIntent(context: Context): Intent {
        return try {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } catch (_: Exception) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }
}
