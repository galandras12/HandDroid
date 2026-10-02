package com.galandras12.handdroid

import android.app.Application
import com.galandras12.handdroid.data.PresetRepository
import com.galandras12.handdroid.data.SettingsStore
import com.galandras12.handdroid.engine.EncodeEngine
import com.galandras12.handdroid.engine.EncodeService

class HandDroidApp : Application() {
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var presets: PresetRepository
        private set
    lateinit var engine: EncodeEngine
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        presets = PresetRepository(this)
        engine = EncodeEngine(this, settingsStore)
        EncodeService.createChannels(this)
    }
}
