package com.projectcenter.app.ui.projects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectcenter.app.domain.models.PushConfirmation
import com.projectcenter.app.domain.models.UpdateMode
import com.projectcenter.app.domain.models.ZipAction

@Composable
fun PushConfirmationScreen(
    confirmation: PushConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Ready",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        InfoRow("Action", when (confirmation.action) {
            ZipAction.CREATE_PROJECT -> "Create project"
            ZipAction.UPDATE_PROJECT -> "Update project"
            ZipAction.EXPLORE_ZIP -> "Explore"
        })

        when (confirmation.action) {
            ZipAction.CREATE_PROJECT -> {
                confirmation.createConfig?.let { cfg ->
                    InfoRow("Repository", cfg.name)
                    InfoRow("Visibility", if (cfg.isPrivate) "Private" else "Public")
                    if (!cfg.description.isNullOrBlank()) {
                        InfoRow("Description", cfg.description)
                    }
                }
            }
            ZipAction.UPDATE_PROJECT -> {
                confirmation.updateConfig?.let { cfg ->
                    InfoRow("Repository", cfg.fullName)
                    InfoRow(
                        "Mode",
                        if (cfg.mode == UpdateMode.REPLACE) "Replace project" else "Update files"
                    )
                }
            }
            else -> {}
        }

        InfoRow("ZIP", confirmation.zip.displayName)

        confirmation.analysis.selectedRoot?.let { root ->
            InfoRow("Project root", root.relativePath.ifEmpty { "." })
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Destructive warning for Replace
        if (confirmation.action == ZipAction.UPDATE_PROJECT &&
            confirmation.updateConfig?.mode == UpdateMode.REPLACE
        ) {
            Text(
                text = "⚠ Some files currently in the repository may be deleted if they are not present in the ZIP.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (confirmation.action == ZipAction.CREATE_PROJECT)
                        "Create & Push"
                    else
                        "Push to GitHub"
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
