package com.projectcenter.app.data.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.deleteBackupDataStore by preferencesDataStore("delete_backup_prefs")

/**
 * Whether repositories are backed up to a ZIP (saved to Downloads) before being
 * deleted through the "easy mode" bulk-delete flow. Enabled by default — this is
 * a safety net so a deleted repository can always be pushed back.
 */
class DeleteBackupPreferences(private val context: Context) {
    private val key = booleanPreferencesKey("backup_before_delete")

    val backupEnabledFlow: Flow<Boolean> = context.deleteBackupDataStore.data.map { prefs ->
        prefs[key] ?: true
    }

    suspend fun setBackupEnabled(enabled: Boolean) {
        context.deleteBackupDataStore.edit { it[key] = enabled }
    }
}
