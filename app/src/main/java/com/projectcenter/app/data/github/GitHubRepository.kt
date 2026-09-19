package com.projectcenter.app.data.github

import com.projectcenter.app.core.security.SecureTokenStore
import com.projectcenter.app.domain.models.Artifact
import com.projectcenter.app.domain.models.CreateRepoRequest
import com.projectcenter.app.domain.models.GitHubUser
import com.projectcenter.app.domain.models.Job
import com.projectcenter.app.domain.models.Repository
import com.projectcenter.app.domain.models.Step
import com.projectcenter.app.domain.models.Workflow
import com.projectcenter.app.domain.models.WorkflowRun
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class GitHubRepository(
    private val tokenStore: SecureTokenStore
) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val token = tokenStore.getAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val logging = HttpLoggingInterceptor().apply {
        // Never log Authorization header values
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16  // parallel blob uploads to api.github.com
        })
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val api: GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GitHubApi::class.java)

    suspend fun getAuthenticatedUser(): Result<GitHubUser> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = api.getAuthenticatedUser()
            GitHubUser(dto.id, dto.login, dto.name, dto.avatarUrl, dto.htmlUrl)
        }
    }

    suspend fun getRepositories(): Result<List<Repository>> = withContext(Dispatchers.IO) {
        runCatching {
            api.getRepositories().map { it.toDomain() }
        }
    }

    suspend fun createRepository(request: CreateRepoRequest): Result<Repository> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                api.createRepository(request).toDomain()
            } catch (e: HttpException) {
                // 422 name already exists — fetch and reuse
                if (e.code() == 422) {
                    val login = api.getAuthenticatedUser().login
                    return@runCatching api.getRepository(login, request.name).toDomain()
                }
                throw Exception(httpMessage(e, "Create repository failed"))
            }
        }
    }

    suspend fun deleteRepository(owner: String, repo: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.deleteRepository(owner, repo)
            if (!response.isSuccessful) {
                val body = try { response.errorBody()?.string()?.take(200) } catch (_: Exception) { null }
                val hint = when (response.code()) {
                    403 -> " Token needs the delete_repo scope (or admin rights)."
                    404 -> " Repository not found or already deleted."
                    else -> ""
                }
                throw Exception("Delete failed: HTTP ${response.code()}$hint${body?.let { " — $it" } ?: ""}")
            }
        }
    }

    /** Raw ZIP archive stream of a repository at [ref] (branch/tag/sha). Caller must close the body. */
    suspend fun downloadRepositoryZipball(owner: String, repo: String, ref: String): Result<okhttp3.ResponseBody> =
        withContext(Dispatchers.IO) {
            runCatching { api.downloadRepositoryZipball(owner, repo, ref) }
        }

    private fun httpMessage(e: HttpException, prefix: String): String {
        val body = try { e.response()?.errorBody()?.string()?.take(200) } catch (_: Exception) { null }
        return "$prefix: HTTP ${e.code()}${body?.let { " — $it" } ?: ""}"
    }

    suspend fun getWorkflows(owner: String, repo: String): Result<List<Workflow>> = withContext(Dispatchers.IO) {
        runCatching {
            api.getWorkflows(owner, repo).workflows.map {
                Workflow(it.id, it.name, it.path, it.state, it.htmlUrl)
            }
        }
    }

    suspend fun getWorkflowRuns(owner: String, repo: String): Result<List<WorkflowRun>> = withContext(Dispatchers.IO) {
        runCatching {
            api.getWorkflowRuns(owner, repo).workflowRuns.map { it.toDomain() }
        }
    }

    suspend fun getWorkflowRun(owner: String, repo: String, runId: Long): Result<WorkflowRun> = withContext(Dispatchers.IO) {
        runCatching {
            api.getWorkflowRun(owner, repo, runId).toDomain()
        }
    }

    suspend fun getJobs(owner: String, repo: String, runId: Long): Result<List<Job>> = withContext(Dispatchers.IO) {
        runCatching {
            api.getJobsForRun(owner, repo, runId).jobs.map { job ->
                Job(
                    id = job.id,
                    name = job.name,
                    status = job.status,
                    conclusion = job.conclusion,
                    steps = job.steps?.map { Step(it.name, it.status, it.conclusion, it.number) } ?: emptyList()
                )
            }
        }
    }

    suspend fun rerunWorkflow(owner: String, repo: String, runId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.rerunWorkflow(owner, repo, runId)
            if (!response.isSuccessful) throw Exception("Rerun failed: ${response.code()}")
        }
    }

    suspend fun rerunFailedJobs(owner: String, repo: String, runId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.rerunFailedJobs(owner, repo, runId)
            if (!response.isSuccessful) throw Exception("Rerun failed jobs failed: ${response.code()}")
        }
    }

    suspend fun getArtifacts(owner: String, repo: String, runId: Long): Result<List<Artifact>> = withContext(Dispatchers.IO) {
        runCatching {
            api.getArtifactsForRun(owner, repo, runId).artifacts.map {
                Artifact(it.id, it.name, it.sizeInBytes, it.expired, it.archiveDownloadUrl, it.createdAt)
            }
        }
    }

    // Raw access for advanced upload / download later
    fun getApi(): GitHubApi = api

    private fun RepositoryDto.toDomain() = Repository(
        id = id,
        name = name,
        fullName = fullName,
        description = description,
        private = private,
        htmlUrl = htmlUrl,
        defaultBranch = defaultBranch,
        owner = owner.login
    )

    private fun WorkflowRunDto.toDomain() = WorkflowRun(
        id = id,
        name = name,
        status = status,
        conclusion = conclusion,
        htmlUrl = htmlUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        headBranch = headBranch,
        event = event
    )
}
