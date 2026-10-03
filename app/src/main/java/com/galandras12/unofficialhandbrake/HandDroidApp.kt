package com.galandras12.unofficialhandbrake

import android.app.Application
import android.content.res.Resources
import com.galandras12.unofficialhandbrake.i18n.AutoTranslation
import com.galandras12.unofficialhandbrake.data.PresetRepository
import com.galandras12.unofficialhandbrake.data.SettingsStore
import com.galandras12.unofficialhandbrake.engine.EncodeEngine
import com.galandras12.unofficialhandbrake.engine.EncodeService

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
        AutoTranslation.prepare(this)
        EncodeService.createChannels(this)
    }

    override fun getResources(): Resources = AutoTranslation.resources ?: super.getResources()
}
