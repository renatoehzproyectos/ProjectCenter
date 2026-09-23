package com.projectcenter.app.ui.projects.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Paths that hold repo configuration critical enough to call out by name if they'd be deleted. */
private val criticalPathHints = listOf(".github/workflows/", ".gitignore", ".gitattributes")

private fun isCritical(path: String): Boolean =
    criticalPathHints.any { path == it || path.startsWith(it) }

@Composable
fun ReplaceWarningDialog(
    filesToDelete: List<String> = emptyList(),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val criticalCount = filesToDelete.count { isCritical(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This will delete ${filesToDelete.size} ${if (filesToDelete.size == 1) "file" else "files"} from the repo") },
        text = {
            Column {
                Text(
                    "These files exist in the repository but not in the ZIP, so \"Replace\" will remove them:",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (criticalCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (criticalCount == 1)
                            "1 of these is a config file (workflow, .gitignore, or .gitattributes) — deleting it can break CI builds."
                        else
                            "$criticalCount of these are config files (workflow, .gitignore, or .gitattributes) — deleting them can break CI builds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(filesToDelete) { path ->
                        Text(
                            "• $path",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCritical(path))
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete these and continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
