package com.projectcenter.app.ui.filemanager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectcenter.app.core.storage.FileAccessPermission
import com.projectcenter.app.domain.models.ManagedFile
import com.projectcenter.app.domain.models.RelatedFileGroup
import java.text.DateFormat
import java.util.Date

@Composable
fun FileManagerScreen(
    onOpenZip: (String) -> Unit = {},
    viewModel: FileManagerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val legacyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshAccessAndLoad() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccessAndLoad()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showCreateFolder by remember { mutableStateOf(false) }
    var createFolderName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ManagedFile?>(null) }
    var renameName by remember { mutableStateOf("") }
    var expandedGroup by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "File Manager",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.toggleSort() }) {
                Icon(Icons.Default.Sort, contentDescription = "Sort")
            }
            IconButton(onClick = { showCreateFolder = true }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
            }
        }

        Text(
            text = state.currentPath.removePrefix("/storage/emulated/0/"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (!state.hasAccess) {
            AccessRequiredCard(
                onRequest = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        context.startActivity(FileAccessPermission.allFilesAccessIntent(context))
                    } else {
                        legacyPermLauncher.launch(FileAccessPermission.legacyPermission())
                    }
                }
            )
            return@Column
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.setSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search files…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { viewModel.goBack() },
                enabled = !state.currentPath.endsWith("/Download")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            if (state.selectedPaths.isNotEmpty()) {
                Text(
                    "${state.selectedPaths.size} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.clearSelection() }) { Text("Clear") }
                IconButton(onClick = { viewModel.requestDeleteSelected() }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.relatedGroups.isNotEmpty() && state.searchQuery.isBlank()) {
                item {
                    Text(
                        "Related files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(state.relatedGroups, key = { it.name }) { group ->
                    RelatedGroupCard(
                        group = group,
                        expanded = expandedGroup == group.name,
                        onToggleExpand = {
                            expandedGroup = if (expandedGroup == group.name) null else group.name
                        },
                        onSelectRelated = { viewModel.selectRelated(group) },
                        onOrganize = { viewModel.requestOrganize(group) }
                    )
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "All files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            items(state.files, key = { it.path }) { file ->
                FileRow(
                    file = file,
                    selected = file.path in state.selectedPaths,
                    onToggleSelect = { viewModel.toggleSelect(file.path) },
                    onOpen = {
                        if (file.isDirectory) viewModel.openDirectory(file)
                        else if (file.extension == "zip") onOpenZip(file.path)
                    },
                    onLongPress = {
                        renameTarget = file
                        renameName = file.name
                    }
                )
            }

            if (state.files.isEmpty()) {
                item {
                    Text(
                        "No files here",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        }
    }

    state.pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete ${pending.size} file${if (pending.size == 1) "" else "s"}?") },
            text = { Column { pending.forEach { Text("• ${it.name}") } } },
            confirmButton = {
                Button(onClick = { viewModel.confirmDelete() }) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") }
            }
        )
    }

    state.pendingOrganize?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelOrganize() },
            title = { Text("Organize ${prompt.groupName}") },
            text = {
                Column {
                    Text("Move:")
                    prompt.files.forEach { Text("• ${it.name}") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Into: ${prompt.destFolder.removePrefix("/storage/emulated/0/")}")
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmOrganize() }) { Text("Organize") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.cancelOrganize() }) { Text("Cancel") }
            }
        )
    }

    state.pendingOverwrite?.let { p ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelOverwrite() },
            title = { Text("File already exists") },
            text = {
                Column {
                    Text(p.existing.name, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Existing: ${formatSize(p.existing.sizeBytes)}")
                    Text("New: ${formatSize(p.newSizeBytes)}")
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmOverwrite() }) { Text("Replace") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.cancelOverwrite() }) { Text("Cancel") }
            }
        )
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }

    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = createFolderName,
                    onValueChange = { createFolderName = it },
                    singleLine = true,
                    placeholder = { Text("Folder name") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (createFolderName.isNotBlank()) {
                        viewModel.createFolder(createFolderName.trim())
                        createFolderName = ""
                        showCreateFolder = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateFolder = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    singleLine = true,
                    label = { Text("New name") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameName.isNotBlank() && renameName != target.name) {
                        viewModel.rename(target.path, renameName.trim())
                    }
                    renameTarget = null
                }) { Text("Rename") }
            },
            dismissButton = {
                OutlinedButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AccessRequiredCard(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Text("Storage access required", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            FileAccessPermission.ACCESS_RATIONALE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Grant access") }
    }
}

@Composable
private fun RelatedGroupCard(
    group: RelatedFileGroup,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectRelated: () -> Unit,
    onOrganize: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .clickable(onClick = onToggleExpand)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📦 ${group.name}",
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${group.files.size} files",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            group.files.forEach { f ->
                Text("  • ${f.name}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSelectRelated) { Text("Select related") }
                OutlinedButton(onClick = onOrganize) { Text("Organize") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: ManagedFile,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleSelect, modifier = Modifier.size(36.dp)) {
            Icon(
                if (selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = "Select",
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = when {
                file.isDirectory -> Icons.Default.Folder
                file.extension == "zip" -> Icons.Default.FolderZip
                else -> Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                buildString {
                    if (!file.isDirectory) append(formatSize(file.sizeBytes)).append(" · ")
                    append(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(file.lastModified))
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}
