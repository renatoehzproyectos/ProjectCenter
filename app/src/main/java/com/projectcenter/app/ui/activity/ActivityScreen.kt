package com.projectcenter.app.ui.activity

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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectcenter.app.data.activity.ActivityRepository
import com.projectcenter.app.ui.theme.FailedRed
import com.projectcenter.app.ui.theme.SuccessGreen

data class ActivityItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val timeLabel: String,
    val type: ActivityType
)

enum class ActivityType { SUCCESS, FAILED, ARTIFACT }

@Composable
fun ActivityScreen() {
    val repo = remember { ActivityRepository() }
    val items by repo.items.collectAsState()

    // Seed demo items if empty so UI is not blank on first open
    val displayItems = if (items.isEmpty()) {
        listOf(
            ActivityItem("demo1", "FloatingAutoClicker", "Build successful", "2 minutes ago", ActivityType.SUCCESS),
            ActivityItem("demo2", "AndroidMacroRecorder", "Artifact available", "10 minutes ago", ActivityType.ARTIFACT),
            ActivityItem("demo3", "TestProject", "Build failed", "25 minutes ago", ActivityType.FAILED)
        )
    } else items

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Activity",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Recent workflow runs and artifacts",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(displayItems, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (icon, tint) = when (item.type) {
                            ActivityType.SUCCESS -> Icons.Default.CheckCircle to SuccessGreen
                            ActivityType.FAILED -> Icons.Default.Error to FailedRed
                            ActivityType.ARTIFACT -> Icons.Default.Inventory2 to MaterialTheme.colorScheme.primary
                        }
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = item.timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
