package com.projectcenter.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Token storage with:
 * 1. EncryptedSharedPreferences (Keystore) while the app is installed
 * 2. Plain SharedPreferences mirror so Android Auto Backup can restore login
 *    after uninstall/reinstall (Keystore keys are NOT restored by backup).
 *
 * Security note: the backup mirror is readable if the device backup is.
 * Prefer a short-lived PAT and regenerate if the device is untrusted.
 */
class SecureTokenStore(context: Context) {

    private val appContext = context.applicationContext

    private val securePrefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_SECURE,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fall back if Keystore fails
        appContext.getSharedPreferences(PREFS_SECURE, Context.MODE_PRIVATE)
    }

    /** Survives uninstall when allowBackup is true + Google Backup */
    private val backupPrefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_BACKUP, Context.MODE_PRIVATE)

    init {
        // After reinstall, Keystore is empty but backup prefs may have token
        migrateFromBackupIfNeeded()
    }

    private fun migrateFromBackupIfNeeded() {
        if (!securePrefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()) return
        val backupToken = backupPrefs.getString(KEY_ACCESS_TOKEN, null) ?: return
        securePrefs.edit()
            .putString(KEY_ACCESS_TOKEN, backupToken)
            .putString(KEY_USER_LOGIN, backupPrefs.getString(KEY_USER_LOGIN, null))
            .apply()
    }

    fun saveAccessToken(token: String) {
        securePrefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
        backupPrefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getAccessToken(): String? {
        return securePrefs.getString(KEY_ACCESS_TOKEN, null)
            ?: backupPrefs.getString(KEY_ACCESS_TOKEN, null)?.also {
                // Re-hydrate secure store
                securePrefs.edit().putString(KEY_ACCESS_TOKEN, it).apply()
            }
    }

    fun saveRefreshToken(token: String?) {
        securePrefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
        backupPrefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    fun getRefreshToken(): String? =
        securePrefs.getString(KEY_REFRESH_TOKEN, null)
            ?: backupPrefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveUserLogin(login: String) {
        securePrefs.edit().putString(KEY_USER_LOGIN, login).apply()
        backupPrefs.edit().putString(KEY_USER_LOGIN, login).apply()
    }

    fun getUserLogin(): String? =
        securePrefs.getString(KEY_USER_LOGIN, null)
            ?: backupPrefs.getString(KEY_USER_LOGIN, null)

    fun clear() {
        securePrefs.edit().clear().apply()
        backupPrefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrBlank()

    // --- Vercel Personal Access Token ---

    fun saveVercelToken(token: String) {
        securePrefs.edit().putString(KEY_VERCEL_TOKEN, token).apply()
        backupPrefs.edit().putString(KEY_VERCEL_TOKEN, token).apply()
    }

    fun getVercelToken(): String? =
        securePrefs.getString(KEY_VERCEL_TOKEN, null)
            ?: backupPrefs.getString(KEY_VERCEL_TOKEN, null)?.also {
                securePrefs.edit().putString(KEY_VERCEL_TOKEN, it).apply()
            }

    fun saveVercelUser(username: String) {
        securePrefs.edit().putString(KEY_VERCEL_USER, username).apply()
        backupPrefs.edit().putString(KEY_VERCEL_USER, username).apply()
    }

    fun getVercelUser(): String? =
        securePrefs.getString(KEY_VERCEL_USER, null)
            ?: backupPrefs.getString(KEY_VERCEL_USER, null)

    fun isVercelLoggedIn(): Boolean = !getVercelToken().isNullOrBlank()

    fun clearVercel() {
        securePrefs.edit().remove(KEY_VERCEL_TOKEN).remove(KEY_VERCEL_USER).apply()
        backupPrefs.edit().remove(KEY_VERCEL_TOKEN).remove(KEY_VERCEL_USER).apply()
    }

    companion object {
        private const val PREFS_SECURE = "project_center_secure_prefs"
        private const val PREFS_BACKUP = "project_center_backup_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_LOGIN = "user_login"
        private const val KEY_VERCEL_TOKEN = "vercel_access_token"
        private const val KEY_VERCEL_USER = "vercel_user_login"
    }
}
