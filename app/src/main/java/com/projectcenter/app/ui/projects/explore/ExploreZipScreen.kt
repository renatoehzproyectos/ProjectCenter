package com.projectcenter.app.ui.projects.explore

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectcenter.app.domain.models.ProjectType
import com.projectcenter.app.domain.models.SelectedZip
import com.projectcenter.app.domain.models.ZipAnalysis
import java.io.File

@Composable
fun ExploreZipScreen(
    zip: SelectedZip,
    analysis: ZipAnalysis?,
    isAnalyzing: Boolean,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.FolderZip,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = zip.displayName,
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isAnalyzing) {
            Text("Analyzing ZIP…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        if (analysis == null) {
            Text("Could not analyze ZIP", color = MaterialTheme.colorScheme.error)
            return
        }

        // Project detection summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Project detection",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                DetectionLine(
                    text = when (analysis.projectType) {
                        ProjectType.ANDROID -> "Android project detected"
                        ProjectType.NODE -> "Node / Web project detected"
                        ProjectType.PYTHON -> "Python project detected"
                        ProjectType.UNKNOWN -> "Project type unknown"
                    },
                    positive = analysis.projectType != ProjectType.UNKNOWN
                )

                if (analysis.projectType == ProjectType.ANDROID) {
                    DetectionLine("Gradle project detected", true)
                }

                DetectionLine(
                    text = if (analysis.hasGitHubActions)
                        "GitHub Actions detected"
                    else
                        "No GitHub Actions workflow found",
                    positive = analysis.hasGitHubActions
                )

                analysis.workflowPaths.forEach { path ->
                    Text(
                        text = "  Workflow: $path",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${analysis.fileCount} files · ${formatSize(analysis.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (analysis.rootCandidates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Detected roots:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    analysis.rootCandidates.take(3).forEach { root ->
                        Text(
                            text = "  ${root.relativePath.ifEmpty { "." }} (score ${root.score})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Contents",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Simple top-level listing of the selected root (or extracted root)
        val listRoot = analysis.selectedRoot?.let { File(it.absolutePath) }
            ?: analysis.extractedDir

        val entries = listRoot.listFiles()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        ) ?: emptyList()

        LazyColumn {
            items(entries, key = { it.absolutePath }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Default.Folder
                        else Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectionLine(text: String, positive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        if (positive) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = if (positive) "✓ $text" else text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
