package com.projectcenter.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure storage for OAuth tokens. Compatible with security-crypto 1.0.0.
 */
class SecureTokenStore(context: Context) {

    private val masterKeyAlias: String = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "project_center_secure",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ── GitHub ──────────────────────────────────────────────────────────────

    fun getAccessToken(): String? = prefs.getString(KEY_GITHUB_TOKEN, null)

    fun setAccessToken(token: String) = saveAccessToken(token)

    fun saveAccessToken(token: String) {
        prefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
    }

    fun saveRefreshToken(token: String?) {
        prefs.edit().apply {
            if (token == null) remove(KEY_GITHUB_REFRESH) else putString(KEY_GITHUB_REFRESH, token)
        }.apply()
    }

    fun clearAccessToken() {
        prefs.edit().remove(KEY_GITHUB_TOKEN).apply()
    }

    fun saveUserLogin(login: String) {
        prefs.edit().putString(KEY_GITHUB_LOGIN, login).apply()
    }

    fun getUserLogin(): String? = prefs.getString(KEY_GITHUB_LOGIN, null)

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrBlank()

    fun isGitHubLoggedIn(): Boolean = isLoggedIn()

    fun clear() {
        prefs.edit()
            .remove(KEY_GITHUB_TOKEN)
            .remove(KEY_GITHUB_REFRESH)
            .remove(KEY_GITHUB_LOGIN)
            .apply()
    }

    // ── Vercel ──────────────────────────────────────────────────────────────

    fun getVercelToken(): String? = prefs.getString(KEY_VERCEL_TOKEN, null)

    fun setVercelToken(token: String) = saveVercelToken(token)

    fun saveVercelToken(token: String) {
        prefs.edit().putString(KEY_VERCEL_TOKEN, token).apply()
    }

    fun clearVercelToken() = clearVercel()

    fun clearVercel() {
        prefs.edit()
            .remove(KEY_VERCEL_TOKEN)
            .remove(KEY_VERCEL_USER)
            .apply()
    }

    fun saveVercelUser(username: String) {
        prefs.edit().putString(KEY_VERCEL_USER, username).apply()
    }

    fun getVercelUser(): String? = prefs.getString(KEY_VERCEL_USER, null)

    fun isVercelLoggedIn(): Boolean = !getVercelToken().isNullOrBlank()

    companion object {
        private const val KEY_GITHUB_TOKEN = "github_access_token"
        private const val KEY_GITHUB_REFRESH = "github_refresh_token"
        private const val KEY_GITHUB_LOGIN = "github_user_login"
        private const val KEY_VERCEL_TOKEN = "vercel_access_token"
        private const val KEY_VERCEL_USER = "vercel_username"
    }
}
