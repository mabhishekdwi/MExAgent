package com.mexagent.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mexagent.app.utils.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.PREFS_NAME
)

class SettingsDataStore(private val context: Context) {

    companion object {
        val KEY_BACKEND_URL  = stringPreferencesKey(Constants.KEY_BACKEND_URL)
        val KEY_DEPTH        = intPreferencesKey(Constants.KEY_DEPTH)
        val KEY_AI_MODE      = booleanPreferencesKey(Constants.KEY_AI_MODE)
        val KEY_SPEED_MS     = intPreferencesKey("action_delay_ms")
    }

    val backendUrl: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKEND_URL] ?: Constants.DEFAULT_BACKEND_URL }

    val depth: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEPTH] ?: Constants.DEFAULT_DEPTH }

    val aiMode: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AI_MODE] ?: Constants.DEFAULT_AI_MODE }

    // Speed: 800=Fast, 2000=Medium, 4000=Slow
    val speedMs: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SPEED_MS] ?: 2000 }

    suspend fun saveBackendUrl(url: String) {
        context.dataStore.edit { it[KEY_BACKEND_URL] = url }
    }

    suspend fun saveDepth(depth: Int) {
        context.dataStore.edit { it[KEY_DEPTH] = depth }
    }

    suspend fun saveAiMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AI_MODE] = enabled }
    }

    suspend fun saveSpeedMs(ms: Int) {
        context.dataStore.edit { it[KEY_SPEED_MS] = ms }
    }
}
