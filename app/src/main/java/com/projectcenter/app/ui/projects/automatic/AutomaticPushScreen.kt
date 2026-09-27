package com.projectcenter.app.ui.projects.automatic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectcenter.app.domain.models.ProjectPushConfiguration
import com.projectcenter.app.domain.models.SelectedZip

/**
 * Extremely simple Automatic Push UI.
 * When association exists: one-tap PUSH.
 * When missing: Configure & Push.
 */
@Composable
fun AutomaticPushScreen(
    zip: SelectedZip,
    config: ProjectPushConfiguration?,
    onPush: () -> Unit,
    onPushAndDeploy: () -> Unit = {},
    onConfigure: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Automatic Push",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            Icons.Default.FolderZip,
            contentDescription = null,
            modifier = Modifier.padding(8.dp)
        )
        Text(zip.displayName, style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(24.dp))

        if (config != null) {
            Text(
                "GitHub",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${config.githubOwner}/${config.githubRepository}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            if (config.branch != "main") {
                Text("Branch: ${config.branch}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onPush,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🚀  PUSH")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onPushAndDeploy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🚀  PUSH + DEPLOY")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onConfigure,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Change repository")
            }
        } else {
            Text(
                "No GitHub repository configured",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onConfigure,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Configure & Push")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
