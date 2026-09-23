package com.projectcenter.app.domain.models

import java.io.File

/**
 * Represents a user-selected ZIP before any GitHub action.
 */
data class SelectedZip(
    val uri: String,           // content URI or file path
    val displayName: String,   // e.g. FloatingAutoClicker.zip
    val sizeBytes: Long,
    val lastModified: Long
)

/**
 * Result of root detection + basic project analysis.
 */
data class ZipAnalysis(
    val extractedDir: File,
    val rootCandidates: List<RootCandidateInfo>,
    val selectedRoot: RootCandidateInfo?,
    val isAmbiguous: Boolean,
    val projectType: ProjectType,
    val hasGitHubActions: Boolean,
    val workflowPaths: List<String>,
    val fileCount: Int,
    val totalSizeBytes: Long
)

data class RootCandidateInfo(
    val relativePath: String,
    val absolutePath: String,
    val score: Int,
    val markers: List<String>
)

enum class ProjectType {
    ANDROID,
    NODE,
    PYTHON,
    UNKNOWN
}

enum class ZipAction {
    CREATE_PROJECT,
    UPDATE_PROJECT,
    EXPLORE_ZIP
}

enum class UpdateMode {
    REPLACE,    // make repo match ZIP (may delete files)
    UPDATE_ONLY // only create/update files present in ZIP
}

data class CreateProjectConfig(
    val name: String,
    val description: String?,
    val isPrivate: Boolean
)

data class UpdateProjectConfig(
    val owner: String,
    val repoName: String,
    val fullName: String,
    val mode: UpdateMode
)

/**
 * Final confirmation payload before any network write.
 */
data class PushConfirmation(
    val action: ZipAction,
    val zip: SelectedZip,
    val analysis: ZipAnalysis,
    val createConfig: CreateProjectConfig? = null,
    val updateConfig: UpdateProjectConfig? = null,
    val filesToDelete: List<String> = emptyList()
)
