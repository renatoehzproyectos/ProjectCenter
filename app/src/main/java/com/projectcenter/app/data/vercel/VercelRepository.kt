package com.projectcenter.app.data.vercel

import com.projectcenter.app.core.security.SecureTokenStore
import com.projectcenter.app.domain.models.VercelDeployment
import com.projectcenter.app.domain.models.VercelProject
import com.projectcenter.app.domain.models.VercelUser
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class VercelRepository(
    private val tokenStore: SecureTokenStore
) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val token = tokenStore.getVercelToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api: VercelApi = Retrofit.Builder()
        .baseUrl("https://api.vercel.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(VercelApi::class.java)

    suspend fun getAuthenticatedUser(): Result<VercelUser> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = api.getAuthenticatedUser().user
            VercelUser(dto.id, dto.username, dto.email)
        }
    }

    suspend fun getProjects(): Result<List<VercelProject>> = withContext(Dispatchers.IO) {
        runCatching {
            api.getProjects().projects.map { it.toDomain() }
        }
    }

    suspend fun deleteProject(idOrName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.deleteProject(idOrName)
            if (!response.isSuccessful) {
                val body = try { response.errorBody()?.string()?.take(200) } catch (_: Exception) { null }
                val hint = when (response.code()) {
                    403 -> " Token does not have access to this project."
                    404 -> " Project not found or already deleted."
                    else -> ""
                }
                throw Exception("Delete failed: HTTP ${response.code()}$hint${body?.let { " — $it" } ?: ""}")
            }
        }
    }

    /** Triggers a new deployment for a project already linked to a GitHub repo (owner/repo). */
    suspend fun deployFromGitHub(
        projectName: String,
        repoFullName: String,
        ref: String = "main"
    ): Result<VercelDeployment> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = api.createDeployment(
                CreateDeploymentRequest(
                    name = projectName,
                    gitSource = GitSourceDto(repo = repoFullName, ref = ref)
                )
            )
            VercelDeployment(dto.id, dto.url, dto.readyState, null)
        }
    }

    private fun VercelProjectDto.toDomain() = VercelProject(
        id = id,
        name = name,
        framework = framework,
        latestUrl = latestDeployments?.firstOrNull()?.url,
        updatedAt = updatedAt,
        linkedRepo = link?.let { l -> if (l.org != null && l.repo != null) "${l.org}/${l.repo}" else l.repo }
    )
}
