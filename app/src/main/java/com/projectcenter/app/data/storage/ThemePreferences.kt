package com.projectcenter.app.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.projectcenter.app.ui.theme.AppThemeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore("theme_prefs")

class ThemePreferences(private val context: Context) {
    private val key = stringPreferencesKey("theme_id")

    val themeFlow: Flow<AppThemeOption> = context.themeDataStore.data.map { prefs ->
        AppThemeOption.fromId(prefs[key])
    }

    suspend fun setTheme(option: AppThemeOption) {
        context.themeDataStore.edit { it[key] = option.id }
    }
}
