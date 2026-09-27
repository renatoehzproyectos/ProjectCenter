package com.projectcenter.app.domain.models

import com.squareup.moshi.Json
import java.io.File

// ── ZIP / Project analysis ──────────────────────────────────────────────────

data class SelectedZip(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long
)

enum class ProjectType {
    ANDROID, NODE, PYTHON, UNKNOWN
}

data class RootCandidateInfo(
    val relativePath: String,
    val file: File,
    val score: Int,
    val markers: List<String>
) {
    val absolutePath: String get() = file.absolutePath
}

data class ZipAnalysis(
    val extractedDir: File,
    val selectedRoot: RootCandidateInfo?,
    val rootCandidates: List<RootCandidateInfo>,
    val isAmbiguous: Boolean,
    val projectType: ProjectType,
    val hasGitHubActions: Boolean,
    val workflowPaths: List<String>,
    val fileCount: Int,
    val totalSizeBytes: Long
)

enum class ZipAction {
    CREATE_PROJECT,
    UPDATE_PROJECT,
    EXPLORE_ZIP,
    AUTOMATIC_PUSH
}

enum class UpdateMode {
    REPLACE,
    UPDATE_ONLY
}

data class CreateProjectConfig(
    val name: String,
    val isPrivate: Boolean = false,
    val description: String? = null
)

data class UpdateProjectConfig(
    val owner: String,
    val repoName: String,
    val fullName: String,
    val mode: UpdateMode
)

data class PushConfirmation(
    val action: ZipAction,
    val zip: SelectedZip,
    val analysis: ZipAnalysis,
    val createConfig: CreateProjectConfig? = null,
    val updateConfig: UpdateProjectConfig? = null,
    val filesToDelete: List<String> = emptyList()
)

// ── GitHub domain ───────────────────────────────────────────────────────────

data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val htmlUrl: String?
)

data class Repository(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: String,
    val private: Boolean,
    val htmlUrl: String,
    val defaultBranch: String = "main",
    val description: String? = null
)

data class CreateRepoRequest(
    val name: String,
    val private: Boolean = false,
    val description: String? = null,
    @Json(name = "auto_init") val autoInit: Boolean = true
)

data class Workflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String,
    val htmlUrl: String? = null
)

data class WorkflowRun(
    val id: Long,
    val name: String?,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val headBranch: String?,
    val event: String? = null
)

data class Job(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val steps: List<Step> = emptyList()
)

data class Step(
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int = 0
)

data class Artifact(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val expired: Boolean,
    val archiveDownloadUrl: String? = null,
    val createdAt: String? = null
)

// ── Vercel domain ───────────────────────────────────────────────────────────

data class VercelUser(
    val id: String,
    val username: String,
    val name: String? = null,
    val email: String? = null
)

data class VercelProject(
    val id: String,
    val name: String,
    val framework: String?,
    val linkedRepo: String? = null,
    val latestUrl: String? = null,
    val updatedAt: Long? = null
)

data class VercelDeployment(
    val id: String,
    val url: String?,
    val state: String?,
    val createdAt: Long? = null
)

// ── File Manager models ─────────────────────────────────────────────────────

data class ManagedFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String?
)

data class RelatedFileGroup(
    val name: String,
    val files: List<ManagedFile>,
    val confidence: Float
)

data class ProjectPushConfiguration(
    val projectName: String,
    val githubOwner: String,
    val githubRepository: String,
    val branch: String = "main",
    val rootPath: String? = null
)
