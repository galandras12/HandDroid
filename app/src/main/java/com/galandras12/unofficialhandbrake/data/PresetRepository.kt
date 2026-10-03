package com.galandras12.unofficialhandbrake.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Built-in presets (derived from HandBrake's) plus the user's own. */
class PresetRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val userFile get() = File(context.filesDir, "user_presets.json")

    private val builtIn: List<Preset> by lazy {
        context.assets.open("presets.json").bufferedReader().use { json.decodeFromString<PresetFile>(it.readText()).presets }
    }
    private val _all = MutableStateFlow<List<Preset>>(emptyList())
    val all: StateFlow<List<Preset>> = _all.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) { _all.value = builtIn + readUser() }

    fun find(name: String?): Preset? = _all.value.firstOrNull { it.name == name }

    suspend fun saveUser(name: String, description: String, settings: EncodeSettings) = withContext(Dispatchers.IO) {
        val user = readUser().filterNot { it.name == name } + Preset(name, USER_CATEGORY, description, settings.normalized(), builtIn = false)
        userFile.writeText(json.encodeToString(PresetFile.serializer(), PresetFile(user)))
        _all.value = builtIn.filterNot { b -> user.any { it.name == b.name } } + user
    }

    suspend fun deleteUser(name: String) = withContext(Dispatchers.IO) {
        val user = readUser().filterNot { it.name == name }
        userFile.writeText(json.encodeToString(PresetFile.serializer(), PresetFile(user)))
        _all.value = builtIn + user
    }

    private fun readUser(): List<Preset> = runCatching {
        if (userFile.exists()) json.decodeFromString<PresetFile>(userFile.readText()).presets else emptyList()
    }.getOrDefault(emptyList())

    companion object {
        const val USER_CATEGORY = "Custom"
        const val DEFAULT_PRESET = "Fast 1080p30"
    }
}
