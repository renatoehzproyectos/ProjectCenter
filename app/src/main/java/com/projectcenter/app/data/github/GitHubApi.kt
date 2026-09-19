package com.projectcenter.app.data.github

import com.projectcenter.app.domain.models.CreateRepoRequest
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface GitHubApi {

    // --- User ---
    @GET("user")
    suspend fun getAuthenticatedUser(): GitHubUserDto

    // --- Repositories ---
    @GET("user/repos")
    suspend fun getRepositories(
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated",
        @Query("direction") direction: String = "desc"
    ): List<RepositoryDto>

    @POST("user/repos")
    suspend fun createRepository(@Body body: CreateRepoRequest): RepositoryDto

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): RepositoryDto

    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Unit>

    // --- ZIP export (used for the safety backup before a repo is deleted) ---
    @GET("repos/{owner}/{repo}/zipball/{ref}")
    @Streaming
    suspend fun downloadRepositoryZipball(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String
    ): ResponseBody

    // --- Contents (for upload) ---
    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body body: CreateFileRequest
    ): ContentResponseDto

    // --- Git Trees / Blobs for bulk upload (preferred for many files) ---
    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateBlobRequest
    ): BlobResponseDto

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateTreeRequest
    ): TreeResponseDto

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateCommitRequest
    ): CommitResponseDto

    @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
    suspend fun getRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): RefResponseDto

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateRefRequest
    ): RefResponseDto

    @PUT("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body body: UpdateRefRequest
    ): RefResponseDto

    // --- Workflows ---
    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun getWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): WorkflowsResponseDto

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20
    ): WorkflowRunsResponseDto

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): WorkflowRunDto

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun getJobsForRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): JobsResponseDto

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/rerun")
    suspend fun rerunWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<Unit>

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/rerun-failed-jobs")
    suspend fun rerunFailedJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    @Streaming
    suspend fun getJobLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("job_id") jobId: Long
    ): ResponseBody

    // --- Artifacts ---
    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun getArtifactsForRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): ArtifactsResponseDto

    @GET("repos/{owner}/{repo}/actions/artifacts/{artifact_id}/zip")
    @Streaming
    suspend fun downloadArtifact(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("artifact_id") artifactId: Long
    ): ResponseBody
}

// ========== DTOs ==========

@JsonClass(generateAdapter = true)
data class GitHubUserDto(
    val id: Long,
    val login: String,
    val name: String?,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "html_url") val htmlUrl: String?
)

@JsonClass(generateAdapter = true)
data class RepositoryDto(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val description: String?,
    val private: Boolean,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "default_branch") val defaultBranch: String,
    val owner: OwnerDto
)

@JsonClass(generateAdapter = true)
data class OwnerDto(
    val login: String
)

@JsonClass(generateAdapter = true)
data class CreateFileRequest(
    val message: String,
    val content: String, // base64
    val branch: String? = null,
    val sha: String? = null
)

@JsonClass(generateAdapter = true)
data class ContentResponseDto(
    val content: ContentDto?,
    val commit: CommitDto?
)

@JsonClass(generateAdapter = true)
data class ContentDto(
    val name: String,
    val path: String,
    val sha: String
)

@JsonClass(generateAdapter = true)
data class CommitDto(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class CreateBlobRequest(
    val content: String,
    val encoding: String = "base64"
)

@JsonClass(generateAdapter = true)
data class BlobResponseDto(
    val sha: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class CreateTreeRequest(
    @Json(name = "base_tree") val baseTree: String? = null,
    val tree: List<TreeItem>
)

@JsonClass(generateAdapter = true)
data class TreeItem(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String? = null,
    val content: String? = null
)

@JsonClass(generateAdapter = true)
data class TreeResponseDto(
    val sha: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class CreateCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CommitResponseDto(
    val sha: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class RefResponseDto(
    val ref: String,
    val `object`: RefObjectDto
)

@JsonClass(generateAdapter = true)
data class RefObjectDto(
    val sha: String,
    val type: String
)

@JsonClass(generateAdapter = true)
data class CreateRefRequest(
    val ref: String,
    val sha: String
)

@JsonClass(generateAdapter = true)
data class UpdateRefRequest(
    val sha: String,
    val force: Boolean = true
)

@JsonClass(generateAdapter = true)
data class WorkflowsResponseDto(
    @Json(name = "total_count") val totalCount: Int,
    val workflows: List<WorkflowDto>
)

@JsonClass(generateAdapter = true)
data class WorkflowDto(
    val id: Long,
    val name: String,
    val path: String,
    val state: String,
    @Json(name = "html_url") val htmlUrl: String
)

@JsonClass(generateAdapter = true)
data class WorkflowRunsResponseDto(
    @Json(name = "total_count") val totalCount: Int,
    @Json(name = "workflow_runs") val workflowRuns: List<WorkflowRunDto>
)

@JsonClass(generateAdapter = true)
data class WorkflowRunDto(
    val id: Long,
    val name: String?,
    val status: String,
    val conclusion: String?,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "head_branch") val headBranch: String?,
    val event: String?
)

@JsonClass(generateAdapter = true)
data class JobsResponseDto(
    @Json(name = "total_count") val totalCount: Int,
    val jobs: List<JobDto>
)

@JsonClass(generateAdapter = true)
data class JobDto(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val steps: List<StepDto>?
)

@JsonClass(generateAdapter = true)
data class StepDto(
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int
)

@JsonClass(generateAdapter = true)
data class ArtifactsResponseDto(
    @Json(name = "total_count") val totalCount: Int,
    val artifacts: List<ArtifactDto>
)

@JsonClass(generateAdapter = true)
data class ArtifactDto(
    val id: Long,
    val name: String,
    @Json(name = "size_in_bytes") val sizeInBytes: Long,
    val expired: Boolean,
    @Json(name = "archive_download_url") val archiveDownloadUrl: String,
    @Json(name = "created_at") val createdAt: String
)
