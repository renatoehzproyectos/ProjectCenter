package com.projectcenter.app.ui.vercel

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectcenter.app.core.security.SecureTokenStore
import com.projectcenter.app.data.vercel.VercelRepository
import com.projectcenter.app.domain.models.VercelProject
import kotlinx.coroutines.launch

@Composable
fun VercelScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenStore = remember { SecureTokenStore(context) }
    val vercelRepo = remember { VercelRepository(tokenStore) }

    var projects by remember { mutableStateOf<List<VercelProject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Deploy from a linked GitHub project.
    var deployTarget by remember { mutableStateOf<VercelProject?>(null) }
    var isDeploying by remember { mutableStateOf(false) }
    var deployError by remember { mutableStateOf<String?>(null) }
    var deploySuccess by remember { mutableStateOf<String?>(null) }

    // Legacy single-project delete (type-to-confirm), still reachable per-row.
    var deleteTarget by remember { mutableStateOf<VercelProject?>(null) }
    var confirmName by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    // Fast delete: multi-select + one-tap delete, no confirmation typing, no backup.
    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkConfirm by remember { mutableStateOf(false) }
    var bulkDeleting by remember { mutableStateOf(false) }
    var bulkProgress by remember { mutableStateOf<String?>(null) }
    var bulkError by remember { mutableStateOf<String?>(null) }

    fun loadProjects() {
        if (!tokenStore.isVercelLoggedIn()) {
            error = "Sign in with a Vercel token in Settings first."
            return
        }
        isLoading = true
        error = null
        scope.launch {
            vercelRepo.getProjects()
                .onSuccess { projects = it }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadProjects() }

    fun exitSelectMode() {
        selectMode = false
        selectedIds = emptySet()
        bulkError = null
    }

    if (deployTarget != null) {
        val project = deployTarget!!
        DeployDialog(
            project = project,
            isDeploying = isDeploying,
            error = deployError,
            success = deploySuccess,
            onDismiss = {
                if (!isDeploying) {
                    deployTarget = null
                    deployError = null
                    deploySuccess = null
                }
            },
            onDeploy = { repoFullName, ref ->
                isDeploying = true
                deployError = null
                deploySuccess = null
                scope.launch {
                    vercelRepo.deployFromGitHub(project.name, repoFullName, ref)
                        .onSuccess { deploySuccess = "Deployment started: ${it.url}" }
                        .onFailure { deployError = it.message ?: "Deployment failed" }
                    isDeploying = false
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selectMode) "${selectedIds.size} selected" else "Vercel",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (selectMode) {
                IconButton(onClick = { exitSelectMode() }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                }
            } else {
                IconButton(onClick = { selectMode = true }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Fast delete multiple projects")
                }
            }
        }

        if (selectMode) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Fast delete removes the selected projects from Vercel immediately — no backup, no typing to confirm.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { showBulkConfirm = true },
                enabled = selectedIds.isNotEmpty() && !bulkDeleting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (selectedIds.isEmpty()) "Select projects to fast delete"
                    else "Fast delete ${selectedIds.size} ${if (selectedIds.size == 1) "project" else "projects"}"
                )
            }
            if (bulkDeleting) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                bulkProgress?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            bulkError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Filter projects") },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            )
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        val filtered = projects.filter {
            searchQuery.isBlank() || it.name.contains(searchQuery, true)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filtered, key = { it.id }) { project ->
                val checked = selectedIds.contains(project.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(enabled = selectMode) {
                            selectedIds = if (checked) selectedIds - project.id else selectedIds + project.id
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectMode) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                selectedIds = if (on) selectedIds + project.id else selectedIds - project.id
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(project.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(
                            project.linkedRepo ?: (project.framework ?: "No GitHub repo linked"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!selectMode) {
                        IconButton(
                            onClick = {
                                deployTarget = project
                                deployError = null
                                deploySuccess = null
                            },
                            enabled = project.linkedRepo != null
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = "Deploy GitHub project")
                        }
                        IconButton(onClick = {
                            val url = "https://vercel.com/dashboard/${project.name}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open web")
                        }
                        IconButton(onClick = {
                            deleteTarget = project
                            confirmName = ""
                            deleteError = null
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    // --- Fast delete confirmation + execution ---
    if (showBulkConfirm) {
        val targets = projects.filter { selectedIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { if (!bulkDeleting) showBulkConfirm = false },
            title = { Text("Fast delete ${targets.size} ${if (targets.size == 1) "project" else "projects"}?") },
            text = {
                Column {
                    Text("This cannot be undone. These projects will be permanently deleted from Vercel:")
                    Spacer(modifier = Modifier.height(8.dp))
                    targets.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        bulkDeleting = true
                        bulkError = null
                        scope.launch {
                            var failures = 0
                            targets.forEachIndexed { index, project ->
                                bulkProgress = "${index + 1}/${targets.size} · ${project.name}"
                                vercelRepo.deleteProject(project.id)
                                    .onFailure {
                                        failures++
                                        bulkError = "Failed to delete ${project.name}: ${it.message}"
                                    }
                            }
                            bulkDeleting = false
                            bulkProgress = null
                            showBulkConfirm = false
                            exitSelectMode()
                            loadProjects()
                            if (failures > 0) {
                                bulkError = "$failures of ${targets.size} projects could not be fully deleted."
                            }
                        }
                    },
                    enabled = !bulkDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Fast delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkConfirm = false }, enabled = !bulkDeleting) { Text("Cancel") }
            }
        )
    }

    // --- Legacy single-project delete (type name to confirm) ---
    deleteTarget?.let { project ->
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    deleteTarget = null
                    deleteError = null
                }
            },
            title = { Text("Delete project?") },
            text = {
                Column {
                    Text("${project.name}\n\nThis cannot be undone.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Type ${project.name} to confirm:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmName,
                        onValueChange = { confirmName = it },
                        singleLine = true,
                        label = { Text(project.name) },
                        enabled = !isDeleting
                    )
                    deleteError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = confirmName.trim() == project.name
                        if (!ok) {
                            deleteError = "Name does not match"
                            return@Button
                        }
                        isDeleting = true
                        deleteError = null
                        scope.launch {
                            val result = vercelRepo.deleteProject(project.id)
                            isDeleting = false
                            result
                                .onSuccess {
                                    deleteTarget = null
                                    loadProjects()
                                }
                                .onFailure { e ->
                                    deleteError = e.message ?: "Delete failed"
                                }
                        }
                    },
                    enabled = !isDeleting && confirmName.trim() == project.name,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(if (isDeleting) "Deleting…" else "Delete project")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null; deleteError = null },
                    enabled = !isDeleting
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DeployDialog(
    project: VercelProject,
    isDeploying: Boolean,
    error: String?,
    success: String?,
    onDismiss: () -> Unit,
    onDeploy: (repoFullName: String, ref: String) -> Unit
) {
    var repoFullName by remember { mutableStateOf(project.linkedRepo ?: "") }
    var ref by remember { mutableStateOf("main") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deploy ${project.name}") },
        text = {
            Column {
                Text(
                    "Triggers a new Vercel deployment from the linked GitHub repository.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = repoFullName,
                    onValueChange = { repoFullName = it },
                    label = { Text("GitHub repo (owner/name)") },
                    singleLine = true,
                    enabled = !isDeploying
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text("Branch") },
                    singleLine = true,
                    enabled = !isDeploying
                )
                success?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDeploy(repoFullName.trim(), ref.trim().ifBlank { "main" }) },
                enabled = repoFullName.isNotBlank() && !isDeploying
            ) {
                Text(if (isDeploying) "Deploying…" else "Deploy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeploying) { Text("Close") }
        }
    )
}
