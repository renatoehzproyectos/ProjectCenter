package com.projectcenter.app.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.projectcenter.app.domain.models.ProjectPushConfiguration
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pushConfigDataStore by preferencesDataStore("push_configs")

/**
 * Persists project → GitHub repository associations for Automatic Push.
 * Does NOT store tokens (those stay in SecureTokenStore).
 */
class PushConfigStore(private val context: Context) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, ProjectPushConfiguration::class.java)
    private val adapter = moshi.adapter<List<ProjectPushConfiguration>>(listType)

    private val KEY = stringPreferencesKey("configs_json")

    val configs: Flow<List<ProjectPushConfiguration>> = context.pushConfigDataStore.data.map { prefs ->
        val json = prefs[KEY] ?: return@map emptyList()
        runCatching { adapter.fromJson(json) ?: emptyList() }.getOrElse { emptyList() }
    }

    suspend fun getAll(): List<ProjectPushConfiguration> = configs.first()

    suspend fun findByProjectName(name: String): ProjectPushConfiguration? {
        val normalized = normalize(name)
        return getAll().firstOrNull { normalize(it.projectName) == normalized }
    }

    suspend fun save(config: ProjectPushConfiguration) {
        context.pushConfigDataStore.edit { prefs ->
            val current = getAll().toMutableList()
            val idx = current.indexOfFirst { normalize(it.projectName) == normalize(config.projectName) }
            if (idx >= 0) current[idx] = config else current.add(config)
            prefs[KEY] = adapter.toJson(current)
        }
    }

    suspend fun remove(projectName: String) {
        context.pushConfigDataStore.edit { prefs ->
            val current = getAll().filterNot { normalize(it.projectName) == normalize(projectName) }
            prefs[KEY] = adapter.toJson(current)
        }
    }

    private fun normalize(name: String): String =
        name.removeSuffix(".zip").removeSuffix(".ZIP").trim().lowercase()
}
