package com.projectcenter.app.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectcenter.app.data.github.ProjectUploader

@Composable
fun PushProgressScreen(
    steps: List<ProgressStep>,
    currentMessage: String?,
    fileProgress: Pair<Int, Int>?, // current / total
    isFinished: Boolean,
    isError: Boolean,
    errorMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Push to GitHub",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        steps.forEach { step ->
            StepRow(step)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (fileProgress != null && !isFinished && !isError) {
            val (current, total) = fileProgress
            Text(
                text = "Uploading files… $current / $total",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (total > 0) current.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth()
            )
        } else if (!isFinished && !isError) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (currentMessage != null && !isFinished) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = currentMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (isFinished && !isError) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Upload complete",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

data class ProgressStep(
    val label: String,
    val state: StepState
)

enum class StepState {
    PENDING, RUNNING, SUCCESS, ERROR
}

@Composable
private fun StepRow(step: ProgressStep) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        when (step.state) {
            StepState.SUCCESS -> Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            StepState.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
            StepState.ERROR -> Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            StepState.PENDING -> Icon(
                Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = step.label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** Helper to map uploader events into UI steps */
fun buildStepsFromProgress(
    last: ProjectUploader.UploadProgress?
): List<ProgressStep> {
    val labels = listOf(
        "Reading ZIP",
        "Extracting",
        "Detecting project root",
        "Creating / selecting repository",
        "Uploading files",
        "Creating commit"
    )
    // Simplified static mapping for foundation; real ViewModel will drive precise states
    return labels.mapIndexed { index, label ->
        ProgressStep(label, StepState.PENDING)
    }
}
