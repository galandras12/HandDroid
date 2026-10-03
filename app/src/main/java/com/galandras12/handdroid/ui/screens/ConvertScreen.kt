package com.galandras12.handdroid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galandras12.handdroid.R
import com.galandras12.handdroid.data.AudioEncoder
import com.galandras12.handdroid.data.Container
import com.galandras12.handdroid.data.Deinterlace
import com.galandras12.handdroid.data.EncodeSettings
import com.galandras12.handdroid.data.FramerateMode
import com.galandras12.handdroid.data.Mixdown
import com.galandras12.handdroid.data.QualityMode
import com.galandras12.handdroid.data.Rotation
import com.galandras12.handdroid.data.SourceInfo
import com.galandras12.handdroid.data.Strength
import com.galandras12.handdroid.data.TrackMode
import com.galandras12.handdroid.data.VideoEncoder
import com.galandras12.handdroid.engine.CommandBuilder
import com.galandras12.handdroid.ui.DropdownField
import com.galandras12.handdroid.ui.Hint
import com.galandras12.handdroid.ui.IntField
import com.galandras12.handdroid.ui.MainViewModel
import com.galandras12.handdroid.ui.SectionCard
import com.galandras12.handdroid.ui.SegmentedChoice
import com.galandras12.handdroid.ui.SliderRow
import com.galandras12.handdroid.ui.SwitchRow
import com.galandras12.handdroid.ui.channelLabel
import com.galandras12.handdroid.ui.codecLabel
import com.galandras12.handdroid.ui.formatDuration
import com.galandras12.handdroid.ui.formatSize
import com.galandras12.handdroid.ui.label

private enum class ConvertTab(val title: Int) {
    SUMMARY(R.string.tab_summary), VIDEO(R.string.tab_video), AUDIO(R.string.tab_audio),
    SUBTITLES(R.string.tab_subtitles), PICTURE(R.string.tab_picture), FILTERS(R.string.tab_filters)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(vm: MainViewModel, onChoosePreset: () -> Unit, onQueued: () -> Unit, requestNotifications: () -> Unit) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val probing by vm.probing.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val presetName by vm.presetName.collectAsStateWithLifecycle()
    val modified by vm.modified.collectAsStateWithLifecycle()
    val outputName by vm.outputName.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val outputTree by vm.outputTree.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSave by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.onSourcesPicked(it) }
    val openSource = { picker.launch(arrayOf("video/*", "application/octet-stream")) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SourceCard(sources, probing, onOpen = openSource)

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.preset), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(presetName + if (modified) "  ·  " + stringResource(R.string.modified) else "", style = MaterialTheme.typography.titleMedium)
                        presets.firstOrNull { it.name == presetName }?.description?.takeIf { it.isNotBlank() }?.let {
                            Hint(it)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onChoosePreset) {
                        Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.change_preset))
                    }
                    TextButton(onClick = { showSave = true }) {
                        Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.save_preset))
                    }
                }
            }

            PrimaryScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 0.dp,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                ConvertTab.entries.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t.title)) })
                }
            }

            when (ConvertTab.entries[tab]) {
                ConvertTab.SUMMARY -> SummaryTab(settings, sources, outputName, outputTree != null, vm)
                ConvertTab.VIDEO -> VideoTab(settings, vm)
                ConvertTab.AUDIO -> AudioTab(settings, sources.firstOrNull(), vm)
                ConvertTab.SUBTITLES -> SubtitlesTab(settings, sources.firstOrNull(), vm)
                ConvertTab.PICTURE -> PictureTab(settings, sources.firstOrNull(), vm)
                ConvertTab.FILTERS -> FiltersTab(settings, vm)
            }
        }

        // Action bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { if (vm.addToQueue(false)) onQueued() },
                enabled = sources.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.add_to_queue)) }
            Button(
                onClick = { requestNotifications(); if (vm.addToQueue(true)) onQueued() },
                enabled = sources.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp)); Text(" " + stringResource(R.string.start_encode)) }
        }
    }

    if (showSave) {
        var name by remember { mutableStateOf(if (modified) "" else presetName) }
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text(stringResource(R.string.save_preset)) },
            text = {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.preset_name)) })
            },
            confirmButton = {
                TextButton(onClick = { vm.saveCurrentAsPreset(name); showSave = false }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SourceCard(sources: List<SourceInfo>, probing: Boolean, onOpen: () -> Unit) {
    SectionCard {
        if (probing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LoadingIndicator(Modifier.size(40.dp))
                Text(stringResource(R.string.scanning))
            }
        } else if (sources.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Rounded.VideoFile, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.open_source), style = MaterialTheme.typography.titleMedium)
                    Hint(stringResource(R.string.open_source_hint))
                }
            }
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_source)) }
        } else {
            val s = sources.first()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Rounded.VideoFile, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(
                        listOfNotNull(
                            "${s.width}×${s.height}",
                            if (s.fps > 0) "%.3g fps".format(s.fps) else null,
                            codecLabel(s.videoCodec),
                            if (s.durationMs > 0) formatDuration(s.durationMs) else null,
                            if (s.sizeBytes > 0) formatSize(s.sizeBytes) else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.tracks_found, s.audio.size, s.subtitles.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (sources.size > 1) {
                Text(
                    pluralStringResource(R.plurals.more_files, sources.size - 1, sources.size - 1),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.change_source)) }
        }
    }
}

// ------------------------------------------------------------------------- tabs

@Composable
private fun SummaryTab(s: EncodeSettings, sources: List<SourceInfo>, outputName: String, customFolder: Boolean, vm: MainViewModel) {
    SectionCard(title = stringResource(R.string.output)) {
        DropdownField(
            label = stringResource(R.string.format),
            selected = s.container,
            options = Container.entries.filter { it in s.videoEncoder.containers },
            optionLabel = { it.label },
            onSelect = { c -> vm.update { it.copy(container = c) } },
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(
            stringResource(R.string.web_optimized), s.webOptimized, { v -> vm.update { it.copy(webOptimized = v) } },
            subtitle = stringResource(R.string.web_optimized_hint), enabled = s.container == Container.MP4,
        )
        SwitchRow(stringResource(R.string.chapter_markers), s.chapterMarkers, { v -> vm.update { it.copy(chapterMarkers = v) } })
        if (sources.size <= 1) {
            OutlinedTextField(
                value = outputName,
                onValueChange = vm::setOutputName,
                label = { Text(stringResource(R.string.file_name)) },
                suffix = { Text("." + s.container.ext) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else Hint(stringResource(R.string.batch_names_hint))
        Hint(stringResource(if (customFolder) R.string.saved_to_custom else R.string.saved_to_default))
    }
    SectionCard(title = stringResource(R.string.summary_title)) {
        Text(summaryText(s), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun summaryText(s: EncodeSettings): String {
    val v = s.videoEncoder
    val q = if (s.qualityMode == QualityMode.CONSTANT_QUALITY && v.maxRf > 0) "RF ${"%.1f".format(s.quality)}" else "${s.bitrateKbps} kbps" + if (s.twoPass) " · " + stringResource(R.string.two_pass) else ""
    val res = if (s.maxWidth > 0 || s.maxHeight > 0) "≤ ${s.maxWidth.takeIf { it > 0 } ?: "∞"}×${s.maxHeight.takeIf { it > 0 } ?: "∞"}" else stringResource(R.string.res_original)
    val a = if (s.audioTracks == TrackMode.NONE) stringResource(R.string.track_none) else "${s.audioEncoder.label}" +
        (if (s.audioEncoder != AudioEncoder.COPY && !s.audioEncoder.lossless) " ${s.audioBitrate} kbps" else "") + " · ${s.audioTracks.label()}"
    return "${s.container.label}\n${v.label} · $q · $res\n$a"
}

@Composable
private fun VideoTab(s: EncodeSettings, vm: MainViewModel) {
    val enc = s.videoEncoder
    SectionCard(title = stringResource(R.string.tab_video)) {
        DropdownField(
            label = stringResource(R.string.video_encoder),
            selected = enc,
            options = VideoEncoder.entries,
            optionLabel = { it.label },
            onSelect = { e -> vm.update { cur ->
                cur.copy(videoEncoder = e, profile = "auto", tune = "none", extraOptions = "",
                    quality = if (e.maxRf > 0) cur.quality.coerceAtMost(e.maxRf.toFloat()) else cur.quality)
            } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (enc.hardware) Hint(stringResource(R.string.hw_hint))
        if (enc == VideoEncoder.AV1) Hint(stringResource(R.string.av1_hint))

        val rates = listOf(0, 15, 24, 25, 30, 50, 60)
        DropdownField(
            label = stringResource(R.string.framerate),
            selected = s.framerate,
            options = if (s.framerate in rates) rates else (rates + s.framerate).sorted(),
            optionLabel = { if (it == 0) stringResource(R.string.same_as_source) else "$it" },
            onSelect = { r -> vm.update { it.copy(framerate = r) } },
            modifier = Modifier.fillMaxWidth(),
        )
        SegmentedChoice(
            options = if (s.framerate == 0) FramerateMode.entries else listOf(FramerateMode.PEAK, FramerateMode.CONSTANT),
            selected = if (s.framerate == 0 || s.framerateMode != FramerateMode.VARIABLE) s.framerateMode else FramerateMode.PEAK,
            optionLabel = { it.label() },
            onSelect = { m -> vm.update { it.copy(framerateMode = m) } },
        )
    }
    SectionCard(title = stringResource(R.string.quality)) {
        SegmentedChoice(
            options = QualityMode.entries,
            selected = s.qualityMode,
            optionLabel = { it.label() },
            onSelect = { m -> vm.update { it.copy(qualityMode = m) } },
            enabled = enc.maxRf > 0,
        )
        if (s.qualityMode == QualityMode.CONSTANT_QUALITY && enc.maxRf > 0) {
            val max = enc.maxRf.toFloat()
            SliderRow(
                label = stringResource(R.string.constant_quality),
                valueText = "RF " + "%.1f".format(s.quality),
                value = max - s.quality,
                range = 0f..max,
                steps = enc.maxRf * 2 - 1,
                onChange = { v -> vm.update { it.copy(quality = max - v) } },
                startHint = stringResource(R.string.lower_quality),
                endHint = stringResource(R.string.higher_quality),
            )
        } else {
            IntField(
                label = stringResource(R.string.avg_bitrate), value = s.bitrateKbps, suffix = "kbps",
                onValue = { b -> vm.update { it.copy(bitrateKbps = b.coerceIn(1, 500_000)) } },
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(stringResource(R.string.two_pass), s.twoPass, { v -> vm.update { it.copy(twoPass = v) } }, enabled = !enc.hardware)
        }
    }
    if (!enc.hardware) {
        SectionCard(title = stringResource(R.string.optimise_video)) {
            SliderRow(
                label = stringResource(R.string.encoder_preset),
                valueText = CommandBuilder.speedLabel(enc, s.speed),
                value = (9 - s.speed).toFloat(),
                range = 0f..9f,
                steps = 8,
                onChange = { v -> vm.update { it.copy(speed = 9 - v.toInt()) } },
                startHint = stringResource(R.string.faster),
                endHint = stringResource(R.string.slower),
            )
            if (enc.isX26x) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownField(
                        stringResource(R.string.profile), s.profile, CommandBuilder.profilesFor(enc),
                        { if (it == "auto") stringResource(R.string.auto) else it },
                        { p -> vm.update { it.copy(profile = p) } }, Modifier.weight(1f),
                    )
                    DropdownField(
                        stringResource(R.string.level), s.level, CommandBuilder.LEVELS,
                        { if (it == "auto") stringResource(R.string.auto) else it },
                        { l -> vm.update { it.copy(level = l) } }, Modifier.weight(1f),
                    )
                }
                DropdownField(
                    stringResource(R.string.tune), s.tune, CommandBuilder.tunesFor(enc),
                    { if (it == "none") stringResource(R.string.none) else it },
                    { t -> vm.update { it.copy(tune = t) } }, Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.extraOptions,
                    onValueChange = { t -> vm.update { it.copy(extraOptions = t) } },
                    label = { Text(stringResource(R.string.extra_options)) },
                    placeholder = { Text("ref=4:bframes=3") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AudioTab(s: EncodeSettings, src: SourceInfo?, vm: MainViewModel) {
    SectionCard(title = stringResource(R.string.tab_audio)) {
        Text(stringResource(R.string.audio_tracks), style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(TrackMode.entries, s.audioTracks, { it.label() }, { m -> vm.update { it.copy(audioTracks = m) } })
        if (src != null) {
            if (src.audio.isEmpty()) Hint(stringResource(R.string.no_audio_in_source))
            else src.audio.forEach { a ->
                Hint("${a.ordinal + 1}. " + listOfNotNull(codecLabel(a.codec), channelLabel(a.channels), a.language, a.title).joinToString(" · "))
            }
        }
    }
    if (s.audioTracks != TrackMode.NONE) {
        SectionCard(title = stringResource(R.string.encoder_settings)) {
            DropdownField(
                stringResource(R.string.audio_encoder), s.audioEncoder,
                AudioEncoder.entries.filter { it == AudioEncoder.COPY || s.container in it.containers },
                { if (it == AudioEncoder.COPY) stringResource(R.string.auto_passthru) else it.label },
                { e -> vm.update { it.copy(audioEncoder = e) } }, Modifier.fillMaxWidth(),
            )
            if (s.audioEncoder != AudioEncoder.COPY) {
                SwitchRow(
                    stringResource(R.string.passthru_matching), s.audioPassthruMatching,
                    { v -> vm.update { it.copy(audioPassthruMatching = v) } },
                    subtitle = stringResource(R.string.passthru_matching_hint),
                )
                DropdownField(
                    stringResource(R.string.mixdown), s.audioMixdown, Mixdown.entries,
                    { it.label() }, { m -> vm.update { it.copy(audioMixdown = m) } }, Modifier.fillMaxWidth(),
                )
                if (!s.audioEncoder.lossless) {
                    val rates = listOf(64, 96, 128, 160, 192, 224, 256, 320, 384, 448, 512, 640)
                    DropdownField(
                        stringResource(R.string.bitrate), s.audioBitrate,
                        if (s.audioBitrate in rates) rates else (rates + s.audioBitrate).sorted(),
                        { "$it kbps" }, { b -> vm.update { it.copy(audioBitrate = b) } }, Modifier.fillMaxWidth(),
                    )
                }
                DropdownField(
                    stringResource(R.string.samplerate), s.audioSampleRate, listOf(0, 22050, 32000, 44100, 48000),
                    { if (it == 0) stringResource(R.string.auto) else "$it Hz" },
                    { r -> vm.update { it.copy(audioSampleRate = r) } }, Modifier.fillMaxWidth(),
                )
                SliderRow(
                    label = stringResource(R.string.gain), valueText = "${if (s.audioGainDb > 0) "+" else ""}${s.audioGainDb} dB",
                    value = s.audioGainDb.toFloat(), range = -20f..20f, steps = 39,
                    onChange = { v -> vm.update { it.copy(audioGainDb = v.toInt()) } },
                )
            } else Hint(stringResource(R.string.auto_passthru_hint))
        }
    }
}

@Composable
private fun SubtitlesTab(s: EncodeSettings, src: SourceInfo?, vm: MainViewModel) {
    SectionCard(title = stringResource(R.string.tab_subtitles)) {
        SegmentedChoice(TrackMode.entries, s.subtitles, { it.label() }, { m -> vm.update { it.copy(subtitles = m) } }, enabled = s.container != Container.WEBM)
        Hint(stringResource(R.string.subtitles_hint))
        if (s.container == Container.WEBM) Hint(stringResource(R.string.subtitles_webm))
        if (src != null) {
            if (src.subtitles.isEmpty()) Hint(stringResource(R.string.no_subs_in_source))
            else src.subtitles.forEach { t ->
                Hint("${t.ordinal + 1}. " + listOfNotNull(codecLabel(t.codec), t.language, t.title,
                    if (t.bitmap && s.container == Container.MP4) stringResource(R.string.not_supported_mp4) else null).joinToString(" · "))
            }
        }
    }
}

private val RESOLUTIONS = listOf(
    0 to 0, 3840 to 2160, 2560 to 1440, 1920 to 1080, 1280 to 720, 960 to 540, 720 to 576, 720 to 480, 640 to 360,
)

@Composable
private fun PictureTab(s: EncodeSettings, src: SourceInfo?, vm: MainViewModel) {
    SectionCard(title = stringResource(R.string.size)) {
        val current = s.maxWidth to s.maxHeight
        DropdownField(
            stringResource(R.string.max_resolution), current,
            if (current in RESOLUTIONS) RESOLUTIONS else RESOLUTIONS + current,
            { (w, h) -> if (w == 0 && h == 0) stringResource(R.string.res_original) else "$w×$h" },
            { (w, h) -> vm.update { it.copy(maxWidth = w, maxHeight = h) } }, Modifier.fillMaxWidth(),
        )
        Hint(stringResource(R.string.res_hint) + src?.let { "  (${it.width}×${it.height})" }.orEmpty())
        DropdownField(
            stringResource(R.string.rotation), s.rotation, Rotation.entries, { it.label() },
            { r -> vm.update { it.copy(rotation = r) } }, Modifier.fillMaxWidth(),
        )
    }
    SectionCard(title = stringResource(R.string.crop)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IntField(stringResource(R.string.crop_top), s.cropTop, { v -> vm.update { it.copy(cropTop = v) } }, Modifier.weight(1f), "px")
            IntField(stringResource(R.string.crop_bottom), s.cropBottom, { v -> vm.update { it.copy(cropBottom = v) } }, Modifier.weight(1f), "px")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IntField(stringResource(R.string.crop_left), s.cropLeft, { v -> vm.update { it.copy(cropLeft = v) } }, Modifier.weight(1f), "px")
            IntField(stringResource(R.string.crop_right), s.cropRight, { v -> vm.update { it.copy(cropRight = v) } }, Modifier.weight(1f), "px")
        }
    }
}

@Composable
private fun FiltersTab(s: EncodeSettings, vm: MainViewModel) {
    SectionCard(title = stringResource(R.string.tab_filters)) {
        DropdownField(stringResource(R.string.deinterlace), s.deinterlace, Deinterlace.entries, { it.label() },
            { d -> vm.update { it.copy(deinterlace = d) } }, Modifier.fillMaxWidth())
        DropdownField(stringResource(R.string.denoise), s.denoise, Strength.entries, { it.label() },
            { d -> vm.update { it.copy(denoise = d) } }, Modifier.fillMaxWidth())
        DropdownField(stringResource(R.string.sharpen), s.sharpen, Strength.entries, { it.label() },
            { d -> vm.update { it.copy(sharpen = d) } }, Modifier.fillMaxWidth())
        SwitchRow(stringResource(R.string.grayscale), s.grayscale, { v -> vm.update { it.copy(grayscale = v) } })
    }
}
