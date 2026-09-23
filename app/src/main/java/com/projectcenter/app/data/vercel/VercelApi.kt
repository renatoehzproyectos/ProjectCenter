package com.projectcenter.app.data.vercel

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface VercelApi {

    // --- User ---
    @GET("v2/user")
    suspend fun getAuthenticatedUser(): VercelUserResponseDto

    // --- Projects ---
    @GET("v9/projects")
    suspend fun getProjects(
        @Query("limit") limit: Int = 100
    ): VercelProjectsResponseDto

    @DELETE("v9/projects/{idOrName}")
    suspend fun deleteProject(
        @Path("idOrName") idOrName: String
    ): Response<Unit>

    // --- Deployments (deploy a GitHub-connected project) ---
    @POST("v13/deployments")
    suspend fun createDeployment(
        @Body body: CreateDeploymentRequest
    ): DeploymentResponseDto
}

// ========== DTOs ==========

@JsonClass(generateAdapter = true)
data class VercelUserResponseDto(
    val user: VercelUserDto
)

@JsonClass(generateAdapter = true)
data class VercelUserDto(
    val id: String,
    val username: String,
    val email: String?
)

@JsonClass(generateAdapter = true)
data class VercelProjectsResponseDto(
    val projects: List<VercelProjectDto>
)

@JsonClass(generateAdapter = true)
data class VercelProjectDto(
    val id: String,
    val name: String,
    val framework: String?,
    val link: VercelProjectLinkDto?,
    @Json(name = "latestDeployments") val latestDeployments: List<VercelLatestDeploymentDto>? = null,
    @Json(name = "updatedAt") val updatedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class VercelProjectLinkDto(
    val type: String? = null,
    val org: String? = null,
    val repo: String? = null,
    val repoId: Long? = null
)

@JsonClass(generateAdapter = true)
data class VercelLatestDeploymentDto(
    val url: String?
)

@JsonClass(generateAdapter = true)
data class CreateDeploymentRequest(
    val name: String,
    @Json(name = "gitSource") val gitSource: GitSourceDto,
    val target: String = "production",
    val project: String? = null
)

@JsonClass(generateAdapter = true)
data class GitSourceDto(
    val type: String = "github",
    val repo: String,
    val ref: String = "main"
)

@JsonClass(generateAdapter = true)
data class DeploymentResponseDto(
    val id: String,
    val url: String,
    @Json(name = "readyState") val readyState: String? = null
)
