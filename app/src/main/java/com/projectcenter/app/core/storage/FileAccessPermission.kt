package com.projectcenter.app.core.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Centralized storage-access checks for the shared Download folder.
 * Reused by Projects screen and File Manager — do not introduce a second system.
 */
object FileAccessPermission {

    const val LEGACY_PERMISSION: String = Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun legacyPermission(): String = LEGACY_PERMISSION

    fun allFilesAccessIntent(context: Context): Intent = manageAllFilesIntent(context)

    fun manageAllFilesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    /** Human-readable reason shown when access is required. */
    const val ACCESS_RATIONALE =
        "ProjectCenter needs access to your Download folder so you can manage " +
        "project ZIPs downloaded from your AI agent without copying them first."
}
