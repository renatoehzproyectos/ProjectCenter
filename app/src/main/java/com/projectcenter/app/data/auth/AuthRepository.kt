package com.projectcenter.app.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.projectcenter.app.BuildConfig
import com.projectcenter.app.core.security.SecureTokenStore
import com.projectcenter.app.domain.models.GitHubUser
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * GitHub authentication.
 *
 * Primary: OAuth in Custom Tabs (supports GitHub passkeys + saved sessions in the browser).
 * Fallback: Personal Access Token (classic or fine-grained) — works without a backend.
 *
 * Google Credential Manager cannot sign into GitHub/Vercel accounts directly;
 * those providers own their identity. Passkeys work on github.com / vercel.com
 * inside the Custom Tab when the user has enrolled them.
 */
class AuthRepository(
    context: Context,
    private val tokenStore: SecureTokenStore
) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Volatile
    private var pendingState: String? = null

    fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()
    fun getStoredLogin(): String? = tokenStore.getUserLogin()

    fun isOAuthConfigured(): Boolean {
        val id = BuildConfig.GITHUB_CLIENT_ID
        return id.isNotBlank() && id != "YOUR_GITHUB_CLIENT_ID"
    }

    /**
     * Opens GitHub OAuth in a Custom Tab.
     * GitHub's page supports: account picker, password, and passkeys.
     */
    fun startOAuthLogin(): Result<Unit> = runCatching {
        if (!isOAuthConfigured()) {
            error(
                "GitHub OAuth Client ID is not configured. " +
                    "Use a Personal Access Token below, or set GITHUB_CLIENT_ID in build.gradle.kts."
            )
        }

        val state = UUID.randomUUID().toString()
        pendingState = state

        val scopes = listOf("repo", "workflow", "delete_repo", "read:user").joinToString(" ")

        val authUrl = Uri.Builder()
            .scheme("https")
            .authority("github.com")
            .path("login/oauth/authorize")
            .appendQueryParameter("client_id", BuildConfig.GITHUB_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.GITHUB_REDIRECT_URI)
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("state", state)
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(appContext, authUrl)
    }

    /** Open GitHub login page only (passkeys / account switch) without full OAuth app flow. */
    fun openGitHubLoginPage() {
        val url = Uri.parse("https://github.com/login")
        val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
        tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tabs.launchUrl(appContext, url)
    }

    /** Open Vercel login (supports GitHub SSO + passkeys on Vercel's page). */
    fun openVercelLoginPage() {
        val url = Uri.parse("https://vercel.com/login")
        val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
        tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tabs.launchUrl(appContext, url)
    }

    suspend fun handleOAuthCallback(uri: Uri): Result<GitHubUser> = withContext(Dispatchers.IO) {
        runCatching {
            val error = uri.getQueryParameter("error")
            if (error != null) {
                val desc = uri.getQueryParameter("error_description") ?: error
                throw IllegalStateException("GitHub OAuth error: $desc")
            }

            val code = uri.getQueryParameter("code")
                ?: throw IllegalStateException("Missing authorization code from GitHub")
            val state = uri.getQueryParameter("state")
            if (pendingState != null && state != pendingState) {
                throw IllegalStateException("Invalid OAuth state — try again")
            }
            pendingState = null

            // Public OAuth apps require client_secret on a backend.
            // Without a backend, this exchange will fail — use PAT instead.
            val tokenResponse = exchangeCodeForToken(code)
            val accessToken = tokenResponse.accessToken
                ?: throw IllegalStateException(
                    "GitHub did not return an access token. Use a Personal Access Token instead."
                )
            tokenStore.saveAccessToken(accessToken)
            tokenStore.saveRefreshToken(null)

            val user = fetchUser(accessToken)
            tokenStore.saveUserLogin(user.login)
            user
        }
    }

    /**
     * Sign in with a GitHub Personal Access Token.
     * Create at: https://github.com/settings/tokens
     * Scopes: repo, workflow, delete_repo, read:user
     */
    suspend fun signInWithPersonalAccessToken(token: String): Result<GitHubUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = token.trim()
                if (trimmed.isBlank()) error("Token is empty")
                if (!trimmed.startsWith("ghp_") &&
                    !trimmed.startsWith("github_pat_") &&
                    !trimmed.startsWith("gho_")
                ) {
                    // Still try — some tokens look different
                }
                val user = fetchUser(trimmed)
                tokenStore.saveAccessToken(trimmed)
                tokenStore.saveUserLogin(user.login)
                user
            }
        }

    fun logout() {
        tokenStore.clear()
        pendingState = null
    }

    private fun exchangeCodeForToken(code: String): TokenResponse {
        val body = FormBody.Builder()
            .add("client_id", BuildConfig.GITHUB_CLIENT_ID)
            .add("code", code)
            .add("redirect_uri", BuildConfig.GITHUB_REDIRECT_URI)
            .build()

        val request = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .header("Accept", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: throw Exception("Empty token response")
            if (!response.isSuccessful) {
                throw Exception("Token exchange failed (${response.code}). Use a Personal Access Token instead.")
            }
            val adapter = moshi.adapter(TokenResponse::class.java)
            val parsed = adapter.fromJson(json) ?: throw Exception("Invalid token JSON")
            if (parsed.accessToken.isNullOrBlank()) {
                throw Exception(
                    "GitHub did not return an access token. " +
                        "OAuth apps need a backend for client_secret. Use a Personal Access Token."
                )
            }
            return parsed
        }
    }

    private fun fetchUser(token: String): GitHubUser {
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: throw Exception("Empty user response")
            if (!response.isSuccessful) {
                throw Exception("GitHub auth failed (${response.code}). Check the token scopes.")
            }
            val adapter = moshi.adapter(UserDto::class.java)
            val dto = adapter.fromJson(json) ?: throw Exception("Invalid user JSON")
            return GitHubUser(dto.id, dto.login, dto.name, dto.avatarUrl, dto.htmlUrl)
        }
    }

    @JsonClass(generateAdapter = true)
    data class TokenResponse(
        @Json(name = "access_token") val accessToken: String?,
        @Json(name = "token_type") val tokenType: String? = null,
        val scope: String? = null,
        val error: String? = null,
        @Json(name = "error_description") val errorDescription: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class UserDto(
        val id: Long,
        val login: String,
        val name: String?,
        @Json(name = "avatar_url") val avatarUrl: String?,
        @Json(name = "html_url") val htmlUrl: String?
    )
}
