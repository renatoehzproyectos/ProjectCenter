package com.projectcenter.app.ui.filemanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectcenter.app.core.storage.FileAccessPermission
import com.projectcenter.app.core.storage.FileManager
import com.projectcenter.app.core.storage.FileOperations
import com.projectcenter.app.core.storage.RecentZipScanner
import com.projectcenter.app.core.storage.RelatedFileDetector
import com.projectcenter.app.domain.models.ManagedFile
import com.projectcenter.app.domain.models.RelatedFileGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FileManagerUiState(
    val currentPath: String = RecentZipScanner.downloadsPath(),
    val files: List<ManagedFile> = emptyList(),
    val relatedGroups: List<RelatedFileGroup> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val hasAccess: Boolean = false,
    val error: String? = null,
    val pendingOverwrite: OverwritePrompt? = null,
    val pendingDelete: List<ManagedFile>? = null,
    val pendingOrganize: OrganizePrompt? = null,
    val sortNewestFirst: Boolean = true
)

data class OverwritePrompt(
    val existing: ManagedFile,
    val newSizeBytes: Long,
    val destPath: String,
    val srcPath: String,
    val isMove: Boolean
)

data class OrganizePrompt(
    val groupName: String,
    val files: List<ManagedFile>,
    val destFolder: String
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val fileManager = FileManager()
    private val _state = MutableStateFlow(FileManagerUiState())
    val state: StateFlow<FileManagerUiState> = _state.asStateFlow()

    private var watchJob: Job? = null

    init {
        refreshAccessAndLoad()
        startLightweightWatch()
    }

    fun refreshAccessAndLoad() {
        val ctx = getApplication<Application>()
        val access = FileAccessPermission.hasAccess(ctx)
        _state.value = _state.value.copy(hasAccess = access)
        if (access) loadDirectory(_state.value.currentPath)
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, currentPath = path)
            val files = if (_state.value.searchQuery.isBlank()) {
                fileManager.listDirectory(path)
            } else {
                fileManager.search(path, _state.value.searchQuery)
            }
            val ordered = if (_state.value.sortNewestFirst) files
            else files.sortedBy { it.lastModified }
            val groups = RelatedFileDetector.detectGroups(ordered)
            _state.value = _state.value.copy(
                files = ordered,
                relatedGroups = groups,
                isLoading = false,
                selectedPaths = emptySet()
            )
        }
    }

    fun setSearch(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        loadDirectory(_state.value.currentPath)
    }

    fun toggleSort() {
        _state.value = _state.value.copy(sortNewestFirst = !_state.value.sortNewestFirst)
        loadDirectory(_state.value.currentPath)
    }

    fun openDirectory(file: ManagedFile) {
        if (file.isDirectory) loadDirectory(file.path)
    }

    fun goBack() {
        val parent = fileManager.parentPath(_state.value.currentPath) ?: return
        loadDirectory(parent)
    }

    fun toggleSelect(path: String) {
        val cur = _state.value.selectedPaths.toMutableSet()
        if (path in cur) cur.remove(path) else cur.add(path)
        _state.value = _state.value.copy(selectedPaths = cur)
    }

    fun selectRelated(group: RelatedFileGroup) {
        _state.value = _state.value.copy(
            selectedPaths = group.files.map { it.path }.toSet()
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedPaths = emptySet())
    }

    fun requestDeleteSelected() {
        val selected = _state.value.files.filter { it.path in _state.value.selectedPaths }
        if (selected.isEmpty()) return
        _state.value = _state.value.copy(pendingDelete = selected)
    }

    fun confirmDelete() {
        val pending = _state.value.pendingDelete ?: return
        viewModelScope.launch {
            val result = FileOperations.delete(pending.map { it.path })
            _state.value = _state.value.copy(pendingDelete = null, selectedPaths = emptySet())
            if (!result.success) {
                _state.value = _state.value.copy(error = result.error)
            }
            loadDirectory(_state.value.currentPath)
        }
    }

    fun cancelDelete() {
        _state.value = _state.value.copy(pendingDelete = null)
    }

    fun requestOrganize(group: RelatedFileGroup) {
        val dest = "${_state.value.currentPath}/${group.name}"
        _state.value = _state.value.copy(
            pendingOrganize = OrganizePrompt(group.name, group.files, dest)
        )
    }

    fun confirmOrganize() {
        val prompt = _state.value.pendingOrganize ?: return
        viewModelScope.launch {
            val result = FileOperations.organizeIntoFolder(
                prompt.files,
                _state.value.currentPath,
                prompt.groupName
            )
            _state.value = _state.value.copy(pendingOrganize = null, selectedPaths = emptySet())
            if (!result.success) {
                _state.value = _state.value.copy(error = result.error)
            }
            loadDirectory(_state.value.currentPath)
        }
    }

    fun cancelOrganize() {
        _state.value = _state.value.copy(pendingOrganize = null)
    }

    fun rename(path: String, newName: String) {
        viewModelScope.launch {
            val result = FileOperations.rename(path, newName)
            if (!result.success) _state.value = _state.value.copy(error = result.error)
            loadDirectory(_state.value.currentPath)
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val result = FileOperations.createFolder(_state.value.currentPath, name)
            if (!result.success) _state.value = _state.value.copy(error = result.error)
            loadDirectory(_state.value.currentPath)
        }
    }

    fun requestCopyOrMove(srcPath: String, destPath: String, isMove: Boolean) {
        viewModelScope.launch {
            val dest = java.io.File(destPath)
            if (dest.exists()) {
                val existing = FileManager.Companion.run {
                    dest.toManaged()
                }
                val src = java.io.File(srcPath)
                _state.value = _state.value.copy(
                    pendingOverwrite = OverwritePrompt(
                        existing = existing,
                        newSizeBytes = src.length(),
                        destPath = destPath,
                        srcPath = srcPath,
                        isMove = isMove
                    )
                )
            } else {
                val result = if (isMove) FileOperations.move(srcPath, destPath)
                else FileOperations.copy(srcPath, destPath)
                if (!result.success) _state.value = _state.value.copy(error = result.error)
                loadDirectory(_state.value.currentPath)
            }
        }
    }

    fun confirmOverwrite() {
        val p = _state.value.pendingOverwrite ?: return
        viewModelScope.launch {
            val result = if (p.isMove) FileOperations.move(p.srcPath, p.destPath, overwrite = true)
            else FileOperations.copy(p.srcPath, p.destPath, overwrite = true)
            _state.value = _state.value.copy(pendingOverwrite = null)
            if (!result.success) _state.value = _state.value.copy(error = result.error)
            loadDirectory(_state.value.currentPath)
        }
    }

    fun cancelOverwrite() {
        _state.value = _state.value.copy(pendingOverwrite = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** Lightweight refresh while File Manager is open so new downloads appear. */
    private fun startLightweightWatch() {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            var lastFingerprint = ""
            while (isActive) {
                delay(2500)
                if (!_state.value.hasAccess) continue
                val path = _state.value.currentPath
                val dir = java.io.File(path)
                if (!dir.isDirectory) continue
                val fingerprint = dir.listFiles()
                    ?.joinToString("|") { "${it.name}:${it.lastModified()}:${it.length()}" }
                    ?: ""
                if (fingerprint != lastFingerprint) {
                    lastFingerprint = fingerprint
                    // Avoid full rebuild noise if user is mid-selection with same contents
                    if (_state.value.selectedPaths.isEmpty() && _state.value.pendingDelete == null) {
                        loadDirectory(path)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        watchJob?.cancel()
        super.onCleared()
    }
}
