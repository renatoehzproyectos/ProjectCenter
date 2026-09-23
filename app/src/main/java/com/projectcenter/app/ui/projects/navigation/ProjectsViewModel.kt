package com.projectcenter.app.ui.projects.navigation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectcenter.app.core.apk.ApkInstaller
import com.projectcenter.app.core.security.SecureTokenStore
import com.projectcenter.app.core.zip.ErrorExtractor
import com.projectcenter.app.data.github.ArtifactManager
import com.projectcenter.app.data.github.GitHubRepository
import com.projectcenter.app.data.github.ProjectUploader
import com.projectcenter.app.data.github.WorkflowMonitor
import com.projectcenter.app.data.storage.ZipStorage
import com.projectcenter.app.domain.models.Artifact
import com.projectcenter.app.domain.models.CreateProjectConfig
import com.projectcenter.app.domain.models.CreateRepoRequest
import com.projectcenter.app.domain.models.Job
import com.projectcenter.app.domain.models.PushConfirmation
import com.projectcenter.app.domain.models.Repository
import com.projectcenter.app.domain.models.RootCandidateInfo
import com.projectcenter.app.domain.models.SelectedZip
import com.projectcenter.app.domain.models.UpdateMode
import com.projectcenter.app.domain.models.UpdateProjectConfig
import com.projectcenter.app.domain.models.WorkflowRun
import com.projectcenter.app.domain.models.ZipAction
import com.projectcenter.app.domain.models.ZipAnalysis
import com.projectcenter.app.ui.projects.ProgressStep
import com.projectcenter.app.ui.projects.StepState
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class ProjectsUiState {
    data object Idle : ProjectsUiState()

    data class ActionChoice(val zip: SelectedZip) : ProjectsUiState()

    data class CreateForm(
        val zip: SelectedZip,
        val suggestedName: String
    ) : ProjectsUiState()

    data class SelectRepo(
        val zip: SelectedZip,
        val repositories: List<Repository>,
        val isLoading: Boolean
    ) : ProjectsUiState()

    data class UpdateMode(
        val zip: SelectedZip,
        val repository: Repository
    ) : ProjectsUiState()

    data class RootAmbiguity(
        val zip: SelectedZip,
        val analysis: ZipAnalysis,
        val candidates: List<RootCandidateInfo>,
        val pendingAction: ZipAction,
        val createConfig: CreateProjectConfig? = null,
        val updateConfig: UpdateProjectConfig? = null
    ) : ProjectsUiState()

    data class Confirm(
        val confirmation: PushConfirmation,
        val showReplaceWarning: Boolean = false
    ) : ProjectsUiState()

    data class Pushing(
        val steps: List<ProgressStep>,
        val message: String? = null,
        val fileProgress: Pair<Int, Int>? = null,
        val isFinished: Boolean = false,
        val isError: Boolean = false,
        val errorMessage: String? = null
    ) : ProjectsUiState()

    data class PushSuccess(
        val owner: String,
        val repo: String,
        val fullName: String,
        val commitSha: String?,
        val hasWorkflows: Boolean
    ) : ProjectsUiState()

    data class Monitoring(
        val owner: String,
        val repo: String,
        val fullName: String,
        val run: WorkflowRun?,
        val jobs: List<Job>,
        val isLoading: Boolean,
        val importantError: String? = null,
        val artifacts: List<Artifact> = emptyList(),
        val isLoadingArtifacts: Boolean = false,
        val artifactStatusMessage: String? = null,
        val foundApks: List<File> = emptyList(),
        val isDownloadingLog: Boolean = false,
        val isRerunning: Boolean = false
    ) : ProjectsUiState()

    data class Explore(
        val zip: SelectedZip,
        val analysis: ZipAnalysis?,
        val isAnalyzing: Boolean
    ) : ProjectsUiState()
}

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = SecureTokenStore(application)
    private val githubRepo = GitHubRepository(tokenStore)
    private val zipStorage = ZipStorage(application)
    private val uploader = ProjectUploader(githubRepo.getApi())
    private val workflowMonitor = WorkflowMonitor(githubRepo)
    private val artifactManager = ArtifactManager(
        githubRepo.getApi(),
        File(application.cacheDir, "artifacts").also { it.mkdirs() }
    )

    private val _state = MutableStateFlow<ProjectsUiState>(ProjectsUiState.Idle)
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    private var currentZip: SelectedZip? = null
    private var currentAnalysis: ZipAnalysis? = null
    private var pendingCreate: CreateProjectConfig? = null
    private var pendingUpdate: UpdateProjectConfig? = null
    private var selectedRoot: RootCandidateInfo? = null
    private var lastOwner: String? = null
    private var lastRepo: String? = null
    private var lastRunId: Long? = null
    private var monitorJob: CoroutineJob? = null

    fun onZipPicked(uriString: String) {
        viewModelScope.launch {
            val uri = Uri.parse(uriString)
            zipStorage.importZip(uri).onSuccess { zip ->
                currentZip = zip
                _state.value = ProjectsUiState.ActionChoice(zip)
            }
        }
    }

    fun onActionChosen(action: ZipAction) {
        val zip = currentZip ?: return
        when (action) {
            ZipAction.CREATE_PROJECT -> {
                val suggested = zip.displayName.removeSuffix(".zip").removeSuffix(".ZIP")
                _state.value = ProjectsUiState.CreateForm(zip, suggested)
            }
            ZipAction.UPDATE_PROJECT -> {
                _state.value = ProjectsUiState.SelectRepo(zip, emptyList(), isLoading = true)
                viewModelScope.launch {
                    val repos = githubRepo.getRepositories().getOrElse { emptyList() }
                    _state.value = ProjectsUiState.SelectRepo(zip, repos, isLoading = false)
                }
            }
            ZipAction.EXPLORE_ZIP -> {
                _state.value = ProjectsUiState.Explore(zip, null, isAnalyzing = true)
                viewModelScope.launch {
                    val analysis = zipStorage.extractAndAnalyze(zip).getOrNull()
                    currentAnalysis = analysis
                    _state.value = ProjectsUiState.Explore(zip, analysis, isAnalyzing = false)
                }
            }
        }
    }

    fun onCreateConfig(config: CreateProjectConfig) {
        pendingCreate = config
        analyzeThenContinue(ZipAction.CREATE_PROJECT)
    }

    fun onRepoSelected(repo: Repository) {
        val zip = currentZip ?: return
        _state.value = ProjectsUiState.UpdateMode(zip, repo)
    }

    fun onUpdateModeSelected(mode: UpdateMode) {
        val state = _state.value
        if (state !is ProjectsUiState.UpdateMode) return
        pendingUpdate = UpdateProjectConfig(
            owner = state.repository.owner,
            repoName = state.repository.name,
            fullName = state.repository.fullName,
            mode = mode
        )
        analyzeThenContinue(ZipAction.UPDATE_PROJECT)
    }

    private fun analyzeThenContinue(action: ZipAction) {
        val zip = currentZip ?: return
        viewModelScope.launch {
            _state.value = ProjectsUiState.Pushing(
                steps = listOf(
                    ProgressStep("Reading ZIP", StepState.SUCCESS),
                    ProgressStep("Extracting", StepState.RUNNING),
                    ProgressStep("Detecting project root", StepState.PENDING)
                ),
                message = "Preparing…"
            )

            zipStorage.extractAndAnalyze(zip).onSuccess { analysis ->
                currentAnalysis = analysis
                if (analysis.isAmbiguous && analysis.rootCandidates.size > 1) {
                    _state.value = ProjectsUiState.RootAmbiguity(
                        zip = zip,
                        analysis = analysis,
                        candidates = analysis.rootCandidates,
                        pendingAction = action,
                        createConfig = pendingCreate,
                        updateConfig = pendingUpdate
                    )
                } else {
                    selectedRoot = analysis.selectedRoot
                    buildConfirmation(action, analysis)
                }
            }.onFailure { e ->
                _state.value = ProjectsUiState.Pushing(
                    steps = emptyList(),
                    isError = true,
                    errorMessage = e.message ?: "Analysis failed"
                )
            }
        }
    }

    fun onRootChosen(root: RootCandidateInfo) {
        selectedRoot = root
        val state = _state.value
        if (state is ProjectsUiState.RootAmbiguity) {
            val patched = state.analysis.copy(selectedRoot = root, isAmbiguous = false)
            currentAnalysis = patched
            buildConfirmation(state.pendingAction, patched)
        }
    }

    private fun buildConfirmation(action: ZipAction, analysis: ZipAnalysis) {
        val zip = currentZip ?: return
        val update = pendingUpdate

        if (action == ZipAction.UPDATE_PROJECT && update?.mode == UpdateMode.REPLACE) {
            viewModelScope.launch {
                val rootDir = File(analysis.selectedRoot?.absolutePath ?: analysis.extractedDir.path)
                val localPaths = com.projectcenter.app.core.zip.ProjectFileLister
                    .listRelativePaths(rootDir)
                    .toSet()
                val remotePaths = githubRepo
                    .getRepositoryFilePaths(update.owner, update.repoName, "main")
                    .getOrElse { emptyList() }
                    .ifEmpty {
                        githubRepo.getRepositoryFilePaths(update.owner, update.repoName, "master").getOrElse { emptyList() }
                    }
                val filesToDelete = remotePaths.filterNot { it in localPaths }.sorted()

                val confirmation = PushConfirmation(
                    action = action,
                    zip = zip,
                    analysis = analysis,
                    createConfig = pendingCreate,
                    updateConfig = pendingUpdate,
                    filesToDelete = filesToDelete
                )
                _state.value = ProjectsUiState.Confirm(confirmation, showReplaceWarning = filesToDelete.isNotEmpty())
            }
            return
        }

        val confirmation = PushConfirmation(
            action = action,
            zip = zip,
            analysis = analysis,
            createConfig = pendingCreate,
            updateConfig = pendingUpdate
        )
        _state.value = ProjectsUiState.Confirm(confirmation, showReplaceWarning = false)
    }

    fun onReplaceWarningAccepted() {
        val s = _state.value
        if (s is ProjectsUiState.Confirm) {
            _state.value = s.copy(showReplaceWarning = false)
        }
    }

    fun dismissReplaceWarning() = goBack()

    fun onConfirmPush() {
        val s = _state.value
        if (s !is ProjectsUiState.Confirm) return
        val confirmation = s.confirmation
        val analysis = confirmation.analysis
        val rootPath = selectedRoot?.absolutePath
            ?: analysis.selectedRoot?.absolutePath
            ?: analysis.extractedDir.absolutePath

        viewModelScope.launch {
            startUpload(confirmation, File(rootPath))
        }
    }

    private suspend fun startUpload(confirmation: PushConfirmation, projectRoot: File) {
        val steps = mutableListOf(
            ProgressStep("Reading ZIP", StepState.SUCCESS),
            ProgressStep("Extracting", StepState.SUCCESS),
            ProgressStep("Detecting project root", StepState.SUCCESS),
            ProgressStep("Creating / selecting repository", StepState.RUNNING),
            ProgressStep("Uploading files", StepState.PENDING),
            ProgressStep("Creating commit", StepState.PENDING)
        )
        _state.value = ProjectsUiState.Pushing(steps = steps, message = "Preparing repository…")

        try {
            val owner: String
            val repoName: String
            val branch: String
            val mode: UpdateMode
            val hasWorkflows = confirmation.analysis.hasGitHubActions

            when (confirmation.action) {
                ZipAction.CREATE_PROJECT -> {
                    val cfg = confirmation.createConfig!!
                    val created = githubRepo.createRepository(
                        CreateRepoRequest(
                            name = cfg.name,
                            description = cfg.description,
                            private = cfg.isPrivate,
                            autoInit = true
                        )
                    ).getOrThrow()
                    owner = created.owner
                    repoName = created.name
                    branch = created.defaultBranch.ifBlank { "main" }
                    mode = UpdateMode.REPLACE
                    steps[3] = steps[3].copy(state = StepState.SUCCESS)
                    _state.value = ProjectsUiState.Pushing(steps = steps.toList())
                }
                ZipAction.UPDATE_PROJECT -> {
                    val cfg = confirmation.updateConfig!!
                    owner = cfg.owner
                    repoName = cfg.repoName
                    branch = "main"
                    mode = cfg.mode
                    steps[3] = steps[3].copy(state = StepState.SUCCESS)
                    _state.value = ProjectsUiState.Pushing(steps = steps.toList())
                }
                else -> return
            }

            lastOwner = owner
            lastRepo = repoName

            steps[4] = steps[4].copy(state = StepState.RUNNING)
            _state.value = ProjectsUiState.Pushing(steps = steps.toList(), message = "Uploading…")

            uploader.upload(
                owner = owner,
                repo = repoName,
                projectRoot = projectRoot,
                branch = branch,
                commitMessage = "Update from Project Center",
                mode = mode
            ).collect { progress ->
                when (progress) {
                    is ProjectUploader.UploadProgress.UploadingFiles -> {
                        _state.value = ProjectsUiState.Pushing(
                            steps = steps.toList(),
                            fileProgress = progress.current to progress.total,
                            message = "Uploading files…"
                        )
                    }
                    is ProjectUploader.UploadProgress.CreatingTree,
                    is ProjectUploader.UploadProgress.Committing -> {
                        steps[4] = steps[4].copy(state = StepState.SUCCESS)
                        steps[5] = steps[5].copy(state = StepState.RUNNING)
                        _state.value = ProjectsUiState.Pushing(
                            steps = steps.toList(),
                            message = "Finalizing commit…"
                        )
                    }
                    is ProjectUploader.UploadProgress.Success -> {
                        steps[5] = steps[5].copy(state = StepState.SUCCESS)
                        _state.value = ProjectsUiState.PushSuccess(
                            owner = owner,
                            repo = repoName,
                            fullName = "$owner/$repoName",
                            commitSha = progress.commitSha,
                            hasWorkflows = hasWorkflows
                        )
                    }
                    is ProjectUploader.UploadProgress.Error -> {
                        _state.value = ProjectsUiState.Pushing(
                            steps = steps.toList(),
                            isError = true,
                            errorMessage = progress.message
                        )
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            _state.value = ProjectsUiState.Pushing(
                steps = emptyList(),
                isError = true,
                errorMessage = e.message ?: "Push failed"
            )
        }
    }

    fun startMonitoring() {
        val owner = lastOwner ?: return
        val repo = lastRepo ?: return
        val fullName = "$owner/$repo"

        monitorJob?.cancel()
        _state.value = ProjectsUiState.Monitoring(
            owner = owner,
            repo = repo,
            fullName = fullName,
            run = null,
            jobs = emptyList(),
            isLoading = true
        )

        monitorJob = viewModelScope.launch {
            // Small delay so GitHub can register the new run
            kotlinx.coroutines.delay(3000)
            workflowMonitor.monitorLatestRun(owner, repo).collect { state ->
                lastRunId = state.run?.id
                var importantError: String? = null
                if (state.run?.conclusion == "failure" && state.jobs.isNotEmpty()) {
                    // Try to fetch logs of the first failed job
                    val failedJob = state.jobs.find { it.conclusion == "failure" }
                    if (failedJob != null) {
                        try {
                            val body = githubRepo.getApi()
                                .getJobLogs(owner, repo, failedJob.id)
                                .string()
                            importantError = ErrorExtractor.extractImportantLines(body)
                        } catch (_: Exception) {}
                    }
                }
                _state.value = ProjectsUiState.Monitoring(
                    owner = owner,
                    repo = repo,
                    fullName = fullName,
                    run = state.run,
                    jobs = state.jobs,
                    isLoading = !state.isComplete,
                    importantError = importantError
                )
                if (state.isComplete && state.run != null) {
                    val app = getApplication<android.app.Application>()
                    when (state.run.conclusion) {
                        "success" -> {
                            com.projectcenter.app.core.notifications.NotificationHelper
                                .showWorkflowCompleted(app, repo, null)
                            loadArtifactsForCurrentRun()
                        }
                        "failure" -> com.projectcenter.app.core.notifications.NotificationHelper
                            .showWorkflowFailed(app, repo)
                    }
                }
            }
        }
    }

    /** Fetches the artifact list for the just-completed run, shown inline in Monitor Workflow. */
    private fun loadArtifactsForCurrentRun() {
        val owner = lastOwner ?: return
        val repo = lastRepo ?: return
        val runId = lastRunId ?: return
        val current = _state.value
        if (current !is ProjectsUiState.Monitoring) return

        viewModelScope.launch {
            _state.value = current.copy(isLoadingArtifacts = true)
            val artifacts = githubRepo.getArtifacts(owner, repo, runId).getOrElse { emptyList() }
            val latest = _state.value
            if (latest is ProjectsUiState.Monitoring) {
                _state.value = latest.copy(artifacts = artifacts, isLoadingArtifacts = false)
            }
        }
    }

    /** Downloads the raw artifact ZIP straight into the device's Downloads folder. */
    fun downloadArtifactToDownloads(artifact: Artifact) {
        val owner = lastOwner ?: return
        val repo = lastRepo ?: return
        val current = _state.value
        if (current !is ProjectsUiState.Monitoring) return

        viewModelScope.launch {
            _state.value = current.copy(artifactStatusMessage = "Downloading ${artifact.name}…")
            runCatching {
                githubRepo.getApi().downloadArtifact(owner, repo, artifact.id).byteStream()
            }.onSuccess { stream ->
                val fileName = com.projectcenter.app.core.storage.DownloadsWriter
                    .timestampedName(artifact.name, "zip")
                val result = com.projectcenter.app.core.storage.DownloadsWriter.writeStream(
                    getApplication(), fileName, "application/zip", stream
                )
                val message = when (result) {
                    is com.projectcenter.app.core.storage.DownloadsWriter.WriteResult.Success ->
                        "Saved to Downloads as ${result.displayName}"
                    is com.projectcenter.app.core.storage.DownloadsWriter.WriteResult.Error ->
                        "Error: ${result.message}"
                }
                val latest = _state.value
                if (latest is ProjectsUiState.Monitoring) {
                    _state.value = latest.copy(artifactStatusMessage = message)
                }
            }.onFailure { e ->
                val latest = _state.value
                if (latest is ProjectsUiState.Monitoring) {
                    _state.value = latest.copy(artifactStatusMessage = "Error: ${e.message}")
                }
            }
        }
    }

    /** Decompresses the artifact ZIP in-app, surfacing any APKs found so they can be installed. */
    fun decompressArtifact(artifact: Artifact) {
        val owner = lastOwner ?: return
        val repo = lastRepo ?: return
        val current = _state.value
        if (current !is ProjectsUiState.Monitoring) return

        viewModelScope.launch {
            artifactManager.downloadAndExtract(owner, repo, artifact).collect { progress ->
                val latest = _state.value
                if (latest !is ProjectsUiState.Monitoring) return@collect
                when (progress) {
                    is ArtifactManager.DownloadProgress.Starting ->
                        _state.value = latest.copy(artifactStatusMessage = "Downloading ${progress.name}…")
                    is ArtifactManager.DownloadProgress.Downloading ->
                        _state.value = latest.copy(artifactStatusMessage = "Downloaded ${progress.bytes / 1024} KB…")
                    is ArtifactManager.DownloadProgress.Extracting ->
                        _state.value = latest.copy(artifactStatusMessage = "Decompressing…")
                    is ArtifactManager.DownloadProgress.Success ->
                        _state.value = latest.copy(artifactStatusMessage = null, foundApks = progress.apkFiles)
                    is ArtifactManager.DownloadProgress.Error ->
                        _state.value = latest.copy(artifactStatusMessage = "Error: ${progress.message}")
                }
            }
        }
    }

    /** Downloads the full build log for the failed job(s) into Downloads. */
    fun downloadBuildLog() {
        val owner = lastOwner ?: return
        val repo = lastRepo ?: return
        val current = _state.value
        if (current !is ProjectsUiState.Monitoring) return
        val runId = lastRunId

        viewModelScope.launch {
            _state.value = current.copy(isDownloadingLog = true)
            val jobs = if (runId != null) githubRepo.getJobs(owner, repo, runId).getOrElse { emptyList() } else emptyList()
            val failedJobs = jobs.filter { it.conclusion == "failure" }.ifEmpty { jobs }
            val log = StringBuilder()
            for (job in failedJobs) {
                runCatching {
                    githubRepo.getApi().getJobLogs(owner, repo, job.id).string()
                }.onSuccess { text ->
                    log.append("===== ${job.name} =====\n").append(text).append("\n\n")
                }
            }
            val result = com.projectcenter.app.core.storage.DownloadsWriter.writeText(
                getApplication(),
                com.projectcenter.app.core.storage.DownloadsWriter.timestampedName("${repo}_build_log", "txt"),
                log.toString().ifBlank { "No log content available." }
            )
            val latest = _state.value
            if (latest is ProjectsUiState.Monitoring) {
                val message = when (result) {
                    is com.projectcenter.app.core.storage.DownloadsWriter.WriteResult.Success ->
                        "Saved to Downloads as ${result.displayName}"
                    is com.projectcenter.app.core.storage.DownloadsWriter.WriteResult.Error ->
                        "Error: ${result.message}"
                }
                _state.value = latest.copy(isDownloadingLog = false, importantError = message)
            }
        }
    }

    fun installApk(apk: File) {
        ApkInstaller.install(getApplication(), apk)
    }

    fun rerunWorkflow() {
        val owner = lastOwner ?: return
        val repo = lastRepo ?: return
        val runId = lastRunId ?: return
        val current = _state.value
        if (current is ProjectsUiState.Monitoring) {
            _state.value = current.copy(isRerunning = true)
        }
        viewModelScope.launch {
            githubRepo.rerunWorkflow(owner, repo, runId)
            startMonitoring()
        }
    }

    fun rerunFailedJobs() {
        val owner = lastOwner ?: return
        val repo = lastRepo ?: return
        val runId = lastRunId ?: return
        val current = _state.value
        if (current is ProjectsUiState.Monitoring) {
            _state.value = current.copy(isRerunning = true)
        }
        viewModelScope.launch {
            githubRepo.rerunFailedJobs(owner, repo, runId)
            startMonitoring()
        }
    }

    fun goBack() {
        monitorJob?.cancel()
        when (_state.value) {
            is ProjectsUiState.ActionChoice -> _state.value = ProjectsUiState.Idle
            is ProjectsUiState.CreateForm,
            is ProjectsUiState.SelectRepo,
            is ProjectsUiState.Explore -> {
                currentZip?.let { _state.value = ProjectsUiState.ActionChoice(it) }
                    ?: run { _state.value = ProjectsUiState.Idle }
            }
            is ProjectsUiState.UpdateMode -> {
                currentZip?.let {
                    _state.value = ProjectsUiState.SelectRepo(it, emptyList(), isLoading = true)
                    viewModelScope.launch {
                        val repos = githubRepo.getRepositories().getOrElse { emptyList() }
                        _state.value = ProjectsUiState.SelectRepo(it, repos, isLoading = false)
                    }
                }
            }
            is ProjectsUiState.RootAmbiguity,
            is ProjectsUiState.Confirm -> {
                currentZip?.let { _state.value = ProjectsUiState.ActionChoice(it) }
            }
            is ProjectsUiState.Pushing -> {
                val p = _state.value as ProjectsUiState.Pushing
                if (p.isFinished || p.isError) reset()
            }
            is ProjectsUiState.PushSuccess,
            is ProjectsUiState.Monitoring -> reset()
            else -> reset()
        }
    }

    private fun reset() {
        monitorJob?.cancel()
        currentZip = null
        currentAnalysis = null
        pendingCreate = null
        pendingUpdate = null
        selectedRoot = null
        lastOwner = null
        lastRepo = null
        lastRunId = null
        zipStorage.clearTemp()
        _state.value = ProjectsUiState.Idle
    }
}
