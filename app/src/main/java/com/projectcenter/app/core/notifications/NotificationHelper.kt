package com.projectcenter.app.core.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.projectcenter.app.MainActivity
import com.projectcenter.app.ProjectCenterApp

object NotificationHelper {

    fun showWorkflowCompleted(context: Context, projectName: String, artifactName: String?) {
        val text = if (artifactName != null)
            "$projectName build completed successfully.\nArtifact: $artifactName"
        else
            "$projectName build completed successfully."
        show(context, "Build successful", text, 1001)
    }

    fun showWorkflowFailed(context: Context, projectName: String) {
        show(context, "Build failed", "$projectName build failed.", 1002)
    }

    fun showArtifactAvailable(context: Context, projectName: String, artifactName: String) {
        show(context, "Artifact available", "$projectName · $artifactName", 1003)
    }

    private fun show(context: Context, title: String, body: String, id: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ProjectCenterApp.CHANNEL_WORKFLOW)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(id, notification)
    }
}
