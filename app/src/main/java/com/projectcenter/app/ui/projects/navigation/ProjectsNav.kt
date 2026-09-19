package com.projectcenter.app.ui.projects.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectcenter.app.ui.projects.ProjectsScreen
import com.projectcenter.app.ui.projects.PushConfirmationScreen
import com.projectcenter.app.ui.projects.PushProgressScreen
import com.projectcenter.app.ui.projects.PushSuccessScreen
import com.projectcenter.app.ui.projects.RootSelectionScreen
import com.projectcenter.app.ui.projects.create.CreateProjectScreen
import com.projectcenter.app.ui.projects.explore.ExploreZipScreen
import com.projectcenter.app.ui.projects.update.ReplaceWarningDialog
import com.projectcenter.app.ui.projects.update.SelectRepositoryScreen
import com.projectcenter.app.ui.projects.update.UpdateModeScreen
import com.projectcenter.app.ui.projects.zipaction.ZipActionScreen
import com.projectcenter.app.ui.workflows.WorkflowStatusScreen

@Composable
fun ProjectsFlow(
    viewModel: ProjectsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is ProjectsUiState.Idle -> {
            ProjectsScreen(
                onZipSelected = { uriString -> viewModel.onZipPicked(uriString) }
            )
        }

        is ProjectsUiState.ActionChoice -> {
            ZipActionScreen(
                zip = s.zip,
                onActionSelected = { action -> viewModel.onActionChosen(action) },
                onBack = { viewModel.goBack() }
            )
        }

        is ProjectsUiState.CreateForm -> {
            CreateProjectScreen(
                zip = s.zip,
                suggestedName = s.suggestedName,
                onCreateAndPush = { config -> viewModel.onCreateConfig(config) },
                onBack = { viewModel.goBack() }
            )
        }

        is ProjectsUiState.SelectRepo -> {
            SelectRepositoryScreen(
                zip = s.zip,
                repositories = s.repositories,
                isLoading = s.isLoading,
                
                onRepositorySelected = { repo -> viewModel.onRepoSelected(repo) },
                onBack = { viewModel.goBack() }
            )
        }

        is ProjectsUiState.UpdateMode -> {
            UpdateModeScreen(
                zip = s.zip,
                repository = s.repository,
                onModeSelected = { mode -> viewModel.onUpdateModeSelected(mode) },
                onBack = { viewModel.goBack() }
            )
        }

        is ProjectsUiState.RootAmbiguity -> {
            RootSelectionScreen(
                candidates = s.candidates,
                onRootSelected = { root -> viewModel.onRootChosen(root) },
                onBack = { viewModel.goBack() }
            )
        }

        is ProjectsUiState.Confirm -> {
            PushConfirmationScreen(
                confirmation = s.confirmation,
                onConfirm = { viewModel.onConfirmPush() },
                onCancel = { viewModel.goBack() }
            )
            if (s.showReplaceWarning) {
                ReplaceWarningDialog(
                    onConfirm = { viewModel.onReplaceWarningAccepted() },
                    onDismiss = { viewModel.dismissReplaceWarning() }
                )
            }
        }

        is ProjectsUiState.Pushing -> {
            PushProgressScreen(
                steps = s.steps,
                currentMessage = s.message,
                fileProgress = s.fileProgress,
                isFinished = s.isFinished,
                isError = s.isError,
                errorMessage = s.errorMessage
            )
        }

        is ProjectsUiState.PushSuccess -> {
            PushSuccessScreen(
                repoFullName = s.fullName,
                commitSha = s.commitSha,
                hasWorkflows = s.hasWorkflows,
                onMonitorWorkflow = { viewModel.startMonitoring() },
                onDone = { viewModel.goBack() }
            )
        }

        is ProjectsUiState.Monitoring -> {
            WorkflowStatusScreen(
                repoFullName = s.fullName,
                run = s.run,
                jobs = s.jobs,
                isLoading = s.isLoading,
                artifacts = s.artifacts,
                isLoadingArtifacts = s.isLoadingArtifacts,
                artifactStatusMessage = s.artifactStatusMessage,
                foundApks = s.foundApks,
                isDownloadingLog = s.isDownloadingLog,
                isRerunning = s.isRerunning,
                onDownloadArtifact = { artifact -> viewModel.downloadArtifactToDownloads(artifact) },
                onDecompressArtifact = { artifact -> viewModel.decompressArtifact(artifact) },
                onInstallApk = { apk -> viewModel.installApk(apk) },
                onDownloadBuildLog = { viewModel.downloadBuildLog() },
                onRerunFailedJobs = { viewModel.rerunFailedJobs() },
                onRerunAllJobs = { viewModel.rerunWorkflow() },
                onBack = { viewModel.goBack() }
            )
        }

        is ProjectsUiState.Explore -> {
            ExploreZipScreen(
                zip = s.zip,
                analysis = s.analysis,
                isAnalyzing = s.isAnalyzing,
                onBack = { viewModel.goBack() }
            )
        }
    }
}
