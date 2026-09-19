package com.projectcenter.app.core.apk

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Installs or updates APKs via PackageInstaller so same-package updates
 * do not fail with a generic "conflict with an existing package".
 *
 * Requirements for a successful update:
 * - Same applicationId
 * - Higher versionCode
 * - Same signing certificate (debug builds must keep the same keystore)
 */
object ApkInstaller {

    private const val TAG = "ApkInstaller"
    const val ACTION_INSTALL_RESULT = "com.projectcenter.app.INSTALL_RESULT"

    fun install(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Log.e(TAG, "APK missing or empty: ${apkFile.absolutePath}")
            return
        }

        try {
            installWithSession(context, apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "PackageInstaller session failed, falling back to ACTION_VIEW", e)
            installWithViewIntent(context, apkFile)
        }
    }

    private fun installWithSession(context: Context, apkFile: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        session.openWrite("package", 0, apkFile.length()).use { out ->
            apkFile.inputStream().use { input ->
                input.copyTo(out)
            }
            session.fsync(out)
        }

        val intent = Intent(ACTION_INSTALL_RESULT).apply {
            setPackage(context.packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)

        // Receiver for result logging (optional UI can listen later)
        try {
            ContextCompat.registerReceiver(
                context.applicationContext,
                InstallResultReceiver(),
                IntentFilter(ACTION_INSTALL_RESULT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {
        }

        session.commit(pending.intentSender)
        session.close()
    }

    private fun installWithViewIntent(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        context.startActivity(intent)
    }

    class InstallResultReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (confirm != null) context.startActivity(confirm)
                }
                PackageInstaller.STATUS_SUCCESS ->
                    Log.i(TAG, "Install/update succeeded")
                PackageInstaller.STATUS_FAILURE_CONFLICT ->
                    Log.e(TAG, "Conflict: different signature or lower versionCode. $message")
                else ->
                    Log.e(TAG, "Install failed status=$status msg=$message")
            }
        }
    }
}
