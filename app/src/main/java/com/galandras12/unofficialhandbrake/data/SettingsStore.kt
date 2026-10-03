package com.galandras12.unofficialhandbrake.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("handdroid_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val treeKey = stringPreferencesKey("output_tree")
    private val presetKey = stringPreferencesKey("last_preset")

    val theme: Flow<ThemeMode> = context.dataStore.data.map { p ->
        p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }
    /** Persisted tree URI of the user chosen output folder, or null for Movies/HandDroid. */
    val outputTree: Flow<String?> = context.dataStore.data.map { it[treeKey] }
    val lastPreset: Flow<String?> = context.dataStore.data.map { it[presetKey] }

    suspend fun setTheme(mode: ThemeMode) { context.dataStore.edit { it[themeKey] = mode.name } }
    suspend fun setOutputTree(uri: String?) {
        context.dataStore.edit { if (uri == null) it.remove(treeKey) else it[treeKey] = uri }
    }
    suspend fun setLastPreset(name: String) { context.dataStore.edit { it[presetKey] = name } }
}
