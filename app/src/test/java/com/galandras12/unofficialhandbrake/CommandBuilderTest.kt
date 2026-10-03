package com.galandras12.unofficialhandbrake

import com.galandras12.unofficialhandbrake.data.*
import com.galandras12.unofficialhandbrake.engine.CommandBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommandBuilderTest {
    private val src = SourceInfo(
        input = "in.mkv", uri = "content://x", name = "in.mkv", sizeBytes = 1, durationMs = 10_000,
        width = 1920, height = 1080, fps = 25.0, videoCodec = "h264", videoBitrateKbps = 5000, chapters = 0,
        audio = listOf(
            AudioStream(0, "ac3", 6, 48000, 384, "eng", null),
            AudioStream(1, "aac", 2, 48000, 128, "hun", null),
        ),
        subtitles = listOf(SubtitleStream(0, "subrip", "eng", null), SubtitleStream(1, "hdmv_pgs_subtitle", "eng", null)),
    )

    private fun build(s: EncodeSettings, source: SourceInfo? = src) =
        CommandBuilder.build("in.mkv", "out.${s.container.ext}", s, source, "/tmp/log")

    @Test fun x264ConstantQuality() {
        val a = build(EncodeSettings(quality = 20f, speed = 3, profile = "high", level = "4.0")).single().args
        assertTrue(a.containsAll(listOf("-c:v", "libx264", "-crf", "20.0", "-preset", "slow", "-profile:v", "high", "-level:v", "4.0")))
        assertEquals("out.mp4", a.last())
    }

    @Test fun scaleKeepsAspectAndNeverUpscales() {
        val f = CommandBuilder.videoFilters(EncodeSettings(maxWidth = 1280, maxHeight = 720))
        assertTrue(f.any { it.startsWith("scale=w='min(iw,1280)':h='min(ih,720)'") && "force_original_aspect_ratio=decrease" in it })
    }

    @Test fun twoPassProducesTwoCommands() {
        val p = build(EncodeSettings(qualityMode = QualityMode.AVG_BITRATE, twoPass = true, bitrateKbps = 3000))
        assertEquals(2, p.size)
        assertTrue("-an" in p[0].args && "-pass" in p[0].args)
        assertTrue("-pass" in p[1].args && "-an" !in p[1].args)
        assertEquals(1f, p.sumOf { it.weight.toDouble() }.toFloat(), 0.001f)
    }

    @Test fun x265TwoPassUsesX265Params() {
        val p = build(EncodeSettings(videoEncoder = VideoEncoder.X265, qualityMode = QualityMode.AVG_BITRATE, twoPass = true))
        assertTrue(p[0].args.any { it.startsWith("pass=1") })
        assertFalse("-pass" in p[0].args)
    }

    @Test fun hardwareEncoderForcesBitrateAndNoTwoPass() {
        val s = EncodeSettings(videoEncoder = VideoEncoder.H264_HW, twoPass = true).normalized()
        assertEquals(QualityMode.AVG_BITRATE, s.qualityMode)
        assertEquals(1, build(s).size)
    }

    @Test fun audioPassthruWhenContainerAllows() {
        val a = build(EncodeSettings(audioEncoder = AudioEncoder.COPY, audioTracks = TrackMode.ALL)).single().args
        assertTrue(a.containsAll(listOf("-map", "0:a:0", "-c:a:0", "copy", "-map", "0:a:1", "-c:a:1", "copy")))
    }

    @Test fun audioFallsBackToAacWhenCopyImpossible() {
        val dts = src.copy(audio = listOf(AudioStream(0, "dts", 6, 48000, 768, null, null)))
        val a = build(EncodeSettings(audioEncoder = AudioEncoder.COPY, audioMixdown = Mixdown.AUTO), dts).single().args
        assertTrue(a.containsAll(listOf("-c:a:0", "aac")))
        assertFalse("-ac:a:0" in a) // 5.1 source, AAC can carry it: keep the channels
    }

    @Test fun stereoMixdownDownmixesSurround() {
        val a = build(EncodeSettings(audioMixdown = Mixdown.STEREO, audioPassthruMatching = false)).single().args
        assertEquals("2", a[a.indexOf("-ac:a:0") + 1])
    }

    @Test fun mp4SubtitlesSkipBitmapTracks() {
        val a = build(EncodeSettings(subtitles = TrackMode.ALL)).single().args
        assertTrue(a.containsAll(listOf("0:s:0", "mov_text")))
        assertFalse("0:s:1" in a)
    }

    @Test fun webmDropsSubtitlesAndForcesCompatibleAudio() {
        val s = EncodeSettings(container = Container.WEBM, videoEncoder = VideoEncoder.VP9, subtitles = TrackMode.ALL, audioEncoder = AudioEncoder.AAC).normalized()
        assertEquals(TrackMode.NONE, s.subtitles)
        assertEquals(AudioEncoder.OPUS, s.audioEncoder)
    }

    @Test fun builtInPresetsAllBuild() {
        val file = File("src/main/assets/presets.json")
        val presets = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<PresetFile>(file.readText()).presets
        assertTrue(presets.size > 50)
        presets.forEach { p ->
            val passes = build(p.settings)
            assertTrue(p.name, passes.isNotEmpty())
        }
        // dump for manual validation against a desktop ffmpeg (see tools/validate_commands.py)
        val dump = presets.joinToString("\n") { p -> p.name + "\t" + build(p.settings).joinToString("\t") { it.args.joinToString("\u001f") } }
        File("build/commands.tsv").apply { parentFile.mkdirs() }.writeText(dump)
    }

    @Test fun dumpVariants() {
        val base = EncodeSettings()
        val v = linkedMapOf(
            "x264 2pass" to base.copy(qualityMode = QualityMode.AVG_BITRATE, twoPass = true, bitrateKbps = 800),
            "x265 2pass" to base.copy(videoEncoder = VideoEncoder.X265, qualityMode = QualityMode.AVG_BITRATE, twoPass = true, bitrateKbps = 800),
            "x265 10bit tune" to base.copy(videoEncoder = VideoEncoder.X265_10BIT, tune = "grain", profile = "main10", level = "4.0", extraOptions = "bframes=4"),
            "vp9 2pass webm" to base.copy(container = Container.WEBM, videoEncoder = VideoEncoder.VP9, qualityMode = QualityMode.AVG_BITRATE, twoPass = true, bitrateKbps = 600, audioEncoder = AudioEncoder.OPUS),
            "vp9 crf mkv" to base.copy(container = Container.MKV, videoEncoder = VideoEncoder.VP9, quality = 33f),
            "av1 2pass" to base.copy(videoEncoder = VideoEncoder.AV1, qualityMode = QualityMode.AVG_BITRATE, twoPass = true, bitrateKbps = 500, speed = 9),
            "x264 extra" to base.copy(extraOptions = "ref=2:bframes=2", tune = "film", profile = "high", level = "4.1", speed = 8),
            "filters" to base.copy(deinterlace = Deinterlace.BWDIF, denoise = Strength.MEDIUM, sharpen = Strength.LIGHT, rotation = Rotation.CW90, grayscale = true, cropTop = 10, cropBottom = 10, cropLeft = 20, cropRight = 20, maxWidth = 640, maxHeight = 360),
            "rot180 flip" to base.copy(rotation = Rotation.ROT180),
            "cfr 24" to base.copy(framerate = 24, framerateMode = FramerateMode.CONSTANT),
            "cfr source" to base.copy(framerateMode = FramerateMode.CONSTANT),
            "variable" to base.copy(framerateMode = FramerateMode.VARIABLE),
            "audio all aac mp4" to base.copy(audioTracks = TrackMode.ALL, audioMixdown = Mixdown.AUTO, audioGainDb = 3, audioSampleRate = 44100),
            "audio opus mkv" to base.copy(container = Container.MKV, audioEncoder = AudioEncoder.OPUS, audioMixdown = Mixdown.SURROUND_5_1, audioBitrate = 384, audioTracks = TrackMode.ALL),
            "audio mp3" to base.copy(audioEncoder = AudioEncoder.MP3, audioBitrate = 128),
            "audio ac3" to base.copy(audioEncoder = AudioEncoder.AC3, audioBitrate = 448, audioMixdown = Mixdown.SURROUND_5_1),
            "audio eac3" to base.copy(audioEncoder = AudioEncoder.EAC3, audioBitrate = 384, audioMixdown = Mixdown.AUTO),
            "audio flac mkv" to base.copy(container = Container.MKV, audioEncoder = AudioEncoder.FLAC, audioMixdown = Mixdown.AUTO),
            "audio vorbis mkv" to base.copy(container = Container.MKV, audioEncoder = AudioEncoder.VORBIS),
            "audio copy mkv all" to base.copy(container = Container.MKV, audioEncoder = AudioEncoder.COPY, audioTracks = TrackMode.ALL),
            "audio mono" to base.copy(audioMixdown = Mixdown.MONO),
            "no audio" to base.copy(audioTracks = TrackMode.NONE),
            "subs mp4" to base.copy(subtitles = TrackMode.ALL),
            "subs mkv" to base.copy(container = Container.MKV, subtitles = TrackMode.ALL),
            "web optimized hevc" to base.copy(videoEncoder = VideoEncoder.X265, webOptimized = true, chapterMarkers = false),
        )
        val lines = v.map { (n, s) -> n + "\t" + build(s).joinToString("\t") { it.args.joinToString("\u001f") } }
        File("build/commands_variants.tsv").apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n"))
    }
}
