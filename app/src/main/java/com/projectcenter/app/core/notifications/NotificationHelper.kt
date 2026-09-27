package com.projectcenter.app.core.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.projectcenter.app.MainActivity
import com.projectcenter.app.ProjectCenterApp

object NotificationHelper {

    fun showWorkflowCompleted(context: Context, repo: String, apkHint: String?) {
        val body = if (apkHint != null) {
            "Build succeeded for $repo. $apkHint"
        } else {
            "Build succeeded for $repo."
        }
        notify(context, id = repo.hashCode(), title = "Workflow completed", body = body)
    }

    fun showWorkflowFailed(context: Context, repo: String) {
        notify(
            context,
            id = repo.hashCode() + 1,
            title = "Workflow failed",
            body = "Build failed for $repo. Open ProjectCenter for details."
        )
    }

    private fun notify(context: Context, id: Int, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, ProjectCenterApp.CHANNEL_WORKFLOW)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }
}
