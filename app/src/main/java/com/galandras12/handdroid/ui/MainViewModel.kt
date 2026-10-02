package com.galandras12.handdroid.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galandras12.handdroid.HandDroidApp
import com.galandras12.handdroid.R
import com.galandras12.handdroid.data.EncodeSettings
import com.galandras12.handdroid.data.Preset
import com.galandras12.handdroid.data.PresetRepository
import com.galandras12.handdroid.data.SourceInfo
import com.galandras12.handdroid.data.ThemeMode
import com.galandras12.handdroid.engine.EncodeService
import com.galandras12.handdroid.engine.MediaProbe
import com.galandras12.handdroid.engine.OutputStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiMessage {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiMessage
    data class Text(val text: String) : UiMessage
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val hd = app as HandDroidApp
    private val engine = hd.engine

    val presets: StateFlow<List<Preset>> = hd.presets.all
    val jobs = engine.jobs
    val queueActive = engine.active

    private val _settings = MutableStateFlow(EncodeSettings())
    val settings: StateFlow<EncodeSettings> = _settings

    private val _presetName = MutableStateFlow(PresetRepository.DEFAULT_PRESET)
    val presetName: StateFlow<String> = _presetName

    /** True once the user changed something after choosing a preset. */
    private val _modified = MutableStateFlow(false)
    val modified: StateFlow<Boolean> = _modified

    private val _sources = MutableStateFlow<List<SourceInfo>>(emptyList())
    val sources: StateFlow<List<SourceInfo>> = _sources

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing

    private val _outputName = MutableStateFlow("")
    val outputName: StateFlow<String> = _outputName

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val theme: StateFlow<ThemeMode> = hd.settingsStore.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val outputTree: StateFlow<String?> = hd.settingsStore.outputTree.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            hd.presets.load()
            val last = hd.settingsStore.lastPreset.first()
            (hd.presets.find(last) ?: hd.presets.find(PresetRepository.DEFAULT_PRESET))?.let { applyPreset(it, remember = false) }
        }
    }

    // ------------------------------------------------------------- source

    fun onSourcesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _probing.value = true
            val ok = mutableListOf<SourceInfo>()
            for (uri in uris) {
                try {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { /* not every provider offers persistable grants */ }
                MediaProbe.probe(getApplication(), uri)
                    .onSuccess { ok += it }
                    .onFailure { _messages.tryEmit(UiMessage.Res(R.string.msg_probe_failed, listOf(uri.lastPathSegment ?: uri.toString()))) }
            }
            _probing.value = false
            if (ok.isNotEmpty()) {
                _sources.value = ok
                _outputName.value = ok.first().baseName
            }
        }
    }

    fun clearSources() { _sources.value = emptyList() }
    fun setOutputName(name: String) { _outputName.value = name }

    // ----------------------------------------------------------- settings

    fun update(change: (EncodeSettings) -> EncodeSettings) {
        _settings.update { change(it).normalized() }
        _modified.value = true
    }

    fun applyPreset(p: Preset, remember: Boolean = true) {
        _settings.value = p.settings.normalized()
        _presetName.value = p.name
        _modified.value = false
        if (remember) viewModelScope.launch { hd.settingsStore.setLastPreset(p.name) }
    }

    fun saveCurrentAsPreset(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            hd.presets.saveUser(n, "", _settings.value)
            _presetName.value = n
            _modified.value = false
            hd.settingsStore.setLastPreset(n)
            _messages.tryEmit(UiMessage.Res(R.string.msg_preset_saved, listOf(n)))
        }
    }

    fun deletePreset(p: Preset) { viewModelScope.launch { hd.presets.deleteUser(p.name) } }

    // -------------------------------------------------------------- queue

    /** Adds the current source(s) with the current settings to the queue; optionally starts encoding. */
    fun addToQueue(startNow: Boolean): Boolean {
        val list = _sources.value
        if (list.isEmpty()) return false
        val preset = if (_modified.value) "${_presetName.value} *" else _presetName.value
        list.forEach { src ->
            val name = if (list.size == 1) OutputStore.sanitize(_outputName.value.ifBlank { src.baseName }) else src.baseName
            engine.add(src, _settings.value, preset, name)
        }
        _messages.tryEmit(UiMessage.Res(R.string.msg_added_to_queue, listOf(list.size)))
        _sources.value = emptyList()
        if (startNow) startQueue()
        return true
    }

    fun startQueue() {
        if (engine.start()) EncodeService.start(getApplication())
    }

    fun stopQueue() = engine.stop()
    fun cancelJob(id: Long) = engine.cancelJob(id)
    fun removeJob(id: Long) = engine.remove(id)
    fun retryJob(id: Long) { engine.retry(id) }
    fun clearFinished() = engine.clearFinished()

    // ----------------------------------------------------------- app prefs

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { hd.settingsStore.setTheme(mode) } }

    fun setOutputTree(uri: Uri?) {
        viewModelScope.launch {
            if (uri != null) {
                try {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (_: Exception) { }
            }
            hd.settingsStore.setOutputTree(uri?.toString())
        }
    }
}
