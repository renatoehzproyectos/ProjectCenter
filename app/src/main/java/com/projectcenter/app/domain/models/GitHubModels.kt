package com.projectcenter.app.domain.models

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
    val description: String?,
    val private: Boolean,
    val htmlUrl: String,
    val defaultBranch: String,
    val owner: String
)

data class Workflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String,
    val htmlUrl: String
)

data class WorkflowRun(
    val id: Long,
    val name: String?,
    val status: String,          // queued, in_progress, completed
    val conclusion: String?,     // success, failure, cancelled, null
    val htmlUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val headBranch: String?,
    val event: String?
)

data class Job(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val steps: List<Step>
)

data class Step(
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int
)

data class Artifact(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val expired: Boolean,
    val archiveDownloadUrl: String,
    val createdAt: String
)

data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = true,
    @com.squareup.moshi.Json(name = "auto_init") val autoInit: Boolean = false
)
