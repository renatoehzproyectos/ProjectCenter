package com.projectcenter.app.ui.workflows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectcenter.app.domain.models.Artifact
import com.projectcenter.app.domain.models.Job
import com.projectcenter.app.domain.models.Step
import com.projectcenter.app.domain.models.WorkflowRun
import com.projectcenter.app.ui.theme.FailedRed
import com.projectcenter.app.ui.theme.QueuedGray
import com.projectcenter.app.ui.theme.RunningBlue
import com.projectcenter.app.ui.theme.SuccessGreen
import java.io.File

@Composable
fun WorkflowStatusScreen(
    repoFullName: String,
    run: WorkflowRun?,
    jobs: List<Job>,
    isLoading: Boolean,
    artifacts: List<Artifact> = emptyList(),
    isLoadingArtifacts: Boolean = false,
    artifactStatusMessage: String? = null,
    foundApks: List<File> = emptyList(),
    isDownloadingLog: Boolean = false,
    isRerunning: Boolean = false,
    onDownloadArtifact: (Artifact) -> Unit = {},
    onDecompressArtifact: (Artifact) -> Unit = {},
    onInstallApk: (File) -> Unit = {},
    onDownloadBuildLog: () -> Unit = {},
    onRerunFailedJobs: () -> Unit = {},
    onRerunAllJobs: () -> Unit = {},
    onBack: () -> Unit
) {
    val isFailure = run?.conclusion == "failure"
    val isSuccess = run?.conclusion == "success"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = repoFullName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = run?.name ?: "Workflow",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (run != null) {
            StatusBadge(run.status, run.conclusion)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            jobs.forEach { job ->
                item {
                    Text(
                        text = job.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(job.steps) { step ->
                    StepRow(step)
                }
            }

            // Artifact handling lives here now — no separate "View Artifact" screen.
            if (isSuccess) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    ArtifactSection(
                        artifacts = artifacts,
                        isLoading = isLoadingArtifacts,
                        statusMessage = artifactStatusMessage,
                        foundApks = foundApks,
                        onDownload = onDownloadArtifact,
                        onDecompress = onDecompressArtifact,
                        onInstallApk = onInstallApk
                    )
                }
            }
        }

        // Failure state: the workflow itself, plus log download and the ability to re-run it.
        if (isFailure) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDownloadBuildLog,
                enabled = !isDownloadingLog,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDownloadingLog) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isDownloadingLog) "Downloading…" else "Download Build Log")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRerunFailedJobs,
                enabled = !isRerunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Re-run failed jobs")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRerunAllJobs,
                enabled = !isRerunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Re-run all jobs")
            }
        }
    }
}

@Composable
private fun ArtifactSection(
    artifacts: List<Artifact>,
    isLoading: Boolean,
    statusMessage: String?,
    foundApks: List<File>,
    onDownload: (Artifact) -> Unit,
    onDecompress: (Artifact) -> Unit,
    onInstallApk: (File) -> Unit
) {
    Column {
        Text(
            text = "Artifacts",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (foundApks.isNotEmpty()) {
            foundApks.forEach { apk ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(apk.name, style = MaterialTheme.typography.titleSmall)
                            Text(formatSize(apk.length()), style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { onInstallApk(apk) }) { Text("Install") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (artifacts.isEmpty() && !isLoading) {
            Text(
                "No artifacts were produced by this run.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            artifacts.forEach { artifact ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(artifact.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    formatSize(artifact.sizeInBytes) + if (artifact.expired) " · expired" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row {
                            OutlinedButton(onClick = { onDownload(artifact) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = { onDecompress(artifact) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Decompress")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String, conclusion: String?) {
    val (label, color) = when {
        conclusion == "success" -> "Success" to SuccessGreen
        conclusion == "failure" -> "Failed" to FailedRed
        status == "in_progress" || status == "queued" -> "Running" to RunningBlue
        else -> status to QueuedGray
    }
    Text(
        text = "● $label",
        style = MaterialTheme.typography.titleMedium,
        color = color
    )
}

@Composable
private fun StepRow(step: Step) {
    val (icon, tint) = when {
        step.conclusion == "success" -> Icons.Default.CheckCircle to SuccessGreen
        step.conclusion == "failure" -> Icons.Default.Error to FailedRed
        step.status == "in_progress" -> Icons.Default.PlayArrow to RunningBlue
        step.status == "queued" -> Icons.Default.HourglassEmpty to QueuedGray
        else -> Icons.Default.RadioButtonUnchecked to QueuedGray
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = step.name, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
