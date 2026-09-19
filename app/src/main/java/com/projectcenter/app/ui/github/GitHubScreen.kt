package com.projectcenter.app.ui.github

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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.projectcenter.app.data.github.GitHubRepository
import com.projectcenter.app.data.github.RepoBackupManager
import com.projectcenter.app.data.storage.DeleteBackupPreferences
import com.projectcenter.app.domain.models.CreateRepoRequest
import com.projectcenter.app.domain.models.Repository
import kotlinx.coroutines.launch

@Composable
fun GitHubScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenStore = remember { SecureTokenStore(context) }
    val githubRepo = remember { GitHubRepository(tokenStore) }
    val backupManager = remember { RepoBackupManager(githubRepo) }
    val backupPrefs = remember { DeleteBackupPreferences(context) }
    val backupEnabledDefault by backupPrefs.backupEnabledFlow.collectAsState(initial = true)

    var repositories by remember { mutableStateOf<List<Repository>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }

    // Legacy single-repo delete (type-to-confirm), still reachable per-row.
    var deleteTarget by remember { mutableStateOf<Repository?>(null) }
    var confirmName by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    // Easy mode: multi-select + one-tap delete with automatic backup.
    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var backupEnabled by remember(backupEnabledDefault) { mutableStateOf(backupEnabledDefault) }
    var showBulkConfirm by remember { mutableStateOf(false) }
    var bulkDeleting by remember { mutableStateOf(false) }
    var bulkProgress by remember { mutableStateOf<String?>(null) }
    var bulkError by remember { mutableStateOf<String?>(null) }

    fun loadRepos() {
        if (!tokenStore.isLoggedIn()) {
            error = "Sign in with a GitHub token in Settings first."
            return
        }
        isLoading = true
        error = null
        scope.launch {
            githubRepo.getRepositories()
                .onSuccess { repositories = it }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadRepos() }

    fun exitSelectMode() {
        selectMode = false
        selectedIds = emptySet()
        bulkError = null
    }

    if (showCreate) {
        CreateRepoDialog(
            isCreating = isCreating,
            onDismiss = { showCreate = false },
            onCreate = { req ->
                isCreating = true
                scope.launch {
                    githubRepo.createRepository(req)
                        .onSuccess {
                            showCreate = false
                            loadRepos()
                        }
                        .onFailure { error = it.message }
                    isCreating = false
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
                if (selectMode) "${selectedIds.size} selected" else "GitHub",
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
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Delete multiple repositories")
                }
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create repository")
                }
            }
        }

        if (selectMode) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Backup before delete", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(
                        "Saves a ZIP of each repo to Downloads first, so you can push it again to restore it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = backupEnabled,
                    onCheckedChange = { checked ->
                        backupEnabled = checked
                        scope.launch { backupPrefs.setBackupEnabled(checked) }
                    }
                )
            }
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
                    if (selectedIds.isEmpty()) "Select repositories to delete"
                    else "Delete ${selectedIds.size} ${if (selectedIds.size == 1) "repository" else "repositories"}"
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
            placeholder = { Text("Filter repositories") },
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

        val filtered = repositories.filter {
            searchQuery.isBlank() ||
                it.name.contains(searchQuery, true) ||
                it.fullName.contains(searchQuery, true)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filtered, key = { it.id }) { repo ->
                val checked = selectedIds.contains(repo.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(enabled = selectMode) {
                            selectedIds = if (checked) selectedIds - repo.id else selectedIds + repo.id
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectMode) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                selectedIds = if (on) selectedIds + repo.id else selectedIds - repo.id
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(repo.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(
                            if (repo.private) "Private" else "Public",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!selectMode) {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl)))
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open web")
                        }
                        IconButton(onClick = {
                            deleteTarget = repo
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

    // --- Bulk delete confirmation + execution ---
    if (showBulkConfirm) {
        val targets = repositories.filter { selectedIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { if (!bulkDeleting) showBulkConfirm = false },
            title = { Text("Delete ${targets.size} ${if (targets.size == 1) "repository" else "repositories"}?") },
            text = {
                Column {
                    Text(
                        if (backupEnabled)
                            "Each repository will first be backed up as a ZIP to your Downloads folder, then permanently deleted from GitHub."
                        else
                            "This cannot be undone — no backup will be created. These repositories will be permanently deleted from GitHub:"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    targets.forEach { Text("• ${it.fullName}", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        bulkDeleting = true
                        bulkError = null
                        scope.launch {
                            var failures = 0
                            targets.forEachIndexed { index, repo ->
                                bulkProgress = "${index + 1}/${targets.size} · ${repo.name}"
                                if (backupEnabled) {
                                    bulkProgress = "Backing up ${repo.name}…"
                                    val backup = backupManager.backup(context, repo)
                                    if (backup is RepoBackupManager.BackupResult.Error) {
                                        failures++
                                        bulkError = "Skipped ${repo.name}: backup failed (${backup.message})"
                                        return@forEachIndexed
                                    }
                                }
                                bulkProgress = "Deleting ${repo.name}…"
                                githubRepo.deleteRepository(repo.owner, repo.name)
                                    .onFailure {
                                        failures++
                                        bulkError = "Failed to delete ${repo.name}: ${it.message}"
                                    }
                            }
                            bulkDeleting = false
                            bulkProgress = null
                            showBulkConfirm = false
                            exitSelectMode()
                            loadRepos()
                            if (failures > 0) {
                                bulkError = "$failures of ${targets.size} repositories could not be fully processed."
                            }
                        }
                    },
                    enabled = !bulkDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(if (backupEnabled) "Backup & delete" else "Delete without backup")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkConfirm = false }, enabled = !bulkDeleting) { Text("Cancel") }
            }
        )
    }

    // --- Legacy single-repo delete (type name to confirm) ---
    deleteTarget?.let { repo ->
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    deleteTarget = null
                    deleteError = null
                }
            },
            title = { Text("Delete repository?") },
            text = {
                Column {
                    Text("${repo.fullName}\n\nThis cannot be undone.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Type ${repo.name} to confirm:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmName,
                        onValueChange = { confirmName = it },
                        singleLine = true,
                        label = { Text(repo.name) },
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
                        val ok = confirmName.trim() == repo.name
                        if (!ok) {
                            deleteError = "Name does not match"
                            return@Button
                        }
                        isDeleting = true
                        deleteError = null
                        scope.launch {
                            val result = githubRepo.deleteRepository(repo.owner, repo.name)
                            isDeleting = false
                            result
                                .onSuccess {
                                    deleteTarget = null
                                    loadRepos()
                                }
                                .onFailure { e ->
                                    deleteError = e.message ?: "Delete failed"
                                }
                        }
                    },
                    enabled = !isDeleting && confirmName.trim() == repo.name,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(if (isDeleting) "Deleting…" else "Delete repository")
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
private fun CreateRepoDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (CreateRepoRequest) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("Create repository") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !isCreating
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    enabled = !isCreating
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { isPrivate = !isPrivate }, enabled = !isCreating) {
                    Text(if (isPrivate) "Visibility: Private" else "Visibility: Public")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    onCreate(
                        CreateRepoRequest(
                            name = name.trim(),
                            description = description.ifBlank { null },
                            private = isPrivate,
                            autoInit = true
                        )
                    )
                },
                enabled = name.isNotBlank() && !isCreating
            ) {
                Text(if (isCreating) "Creating…" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) { Text("Cancel") }
        }
    )
}
