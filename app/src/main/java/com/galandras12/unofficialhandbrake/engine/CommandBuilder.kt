package com.galandras12.unofficialhandbrake.engine

import com.galandras12.unofficialhandbrake.data.AudioEncoder
import com.galandras12.unofficialhandbrake.data.AudioStream
import com.galandras12.unofficialhandbrake.data.Container
import com.galandras12.unofficialhandbrake.data.Deinterlace
import com.galandras12.unofficialhandbrake.data.EncodeSettings
import com.galandras12.unofficialhandbrake.data.FramerateMode
import com.galandras12.unofficialhandbrake.data.Mixdown
import com.galandras12.unofficialhandbrake.data.QualityMode
import com.galandras12.unofficialhandbrake.data.Rotation
import com.galandras12.unofficialhandbrake.data.SourceInfo
import com.galandras12.unofficialhandbrake.data.Strength
import com.galandras12.unofficialhandbrake.data.TrackMode
import com.galandras12.unofficialhandbrake.data.VideoEncoder
import java.util.Locale

/** One FFmpeg invocation; [weight] is its share of the job's total progress. */
data class FfmpegPass(val args: List<String>, val weight: Float)

/**
 * Translates HandDroid / HandBrake style settings into FFmpeg command lines.
 * Pure Kotlin so it can be unit tested on the JVM.
 */
object CommandBuilder {

    private val X264_SPEEDS = listOf("placebo", "veryslow", "slower", "slow", "medium", "fast", "faster", "veryfast", "superfast", "ultrafast")

    val X264_PROFILES = listOf("auto", "baseline", "main", "high")
    val X265_PROFILES = listOf("auto", "main")
    val X265_10BIT_PROFILES = listOf("auto", "main10")
    val LEVELS = listOf("auto", "3.0", "3.1", "3.2", "4.0", "4.1", "4.2", "5.0", "5.1", "5.2")
    val X264_TUNES = listOf("none", "film", "animation", "grain", "stillimage", "psnr", "ssim", "fastdecode", "zerolatency")
    val X265_TUNES = listOf("none", "animation", "grain", "psnr", "ssim", "fastdecode", "zerolatency")

    fun profilesFor(e: VideoEncoder): List<String> = when (e) {
        VideoEncoder.X264 -> X264_PROFILES
        VideoEncoder.X265 -> X265_PROFILES
        VideoEncoder.X265_10BIT -> X265_10BIT_PROFILES
        else -> emptyList()
    }

    fun tunesFor(e: VideoEncoder): List<String> = when (e) {
        VideoEncoder.X264 -> X264_TUNES
        VideoEncoder.X265, VideoEncoder.X265_10BIT -> X265_TUNES
        else -> emptyList()
    }

    /** Name of the encoder preset shown in the UI for the given speed slider position. */
    fun speedLabel(e: VideoEncoder, speed: Int): String = when {
        e.isX26x -> X264_SPEEDS[speed.coerceIn(0, 9)]
        e == VideoEncoder.VP9 -> "cpu-used ${vp9CpuUsed(speed)}"
        e == VideoEncoder.AV1 -> "cpu-used ${av1CpuUsed(speed)}"
        else -> ""
    }

    private fun vp9CpuUsed(speed: Int) = (speed.coerceIn(0, 9) * 5 / 9)
    private fun av1CpuUsed(speed: Int) = (speed.coerceIn(0, 9) + 1).coerceAtMost(8)

    /** Container-compatible audio codecs for stream copy. */
    private fun copyable(container: Container): Set<String>? = when (container) {
        Container.MP4 -> setOf("aac", "ac3", "eac3", "mp3", "opus", "flac", "alac")
        Container.MKV -> null // Matroska takes (nearly) everything
        Container.WEBM -> setOf("opus", "vorbis")
    }

    private fun maxChannels(e: AudioEncoder) = when (e) {
        AudioEncoder.MP3 -> 2
        AudioEncoder.AC3 -> 6
        else -> 8
    }

    fun build(
        input: String,
        output: String,
        settings: EncodeSettings,
        source: SourceInfo?,
        passLogPrefix: String,
    ): List<FfmpegPass> {
        val s = settings.normalized()
        val enc = s.videoEncoder
        val twoPass = s.twoPass && s.qualityMode == QualityMode.AVG_BITRATE && !enc.hardware

        
        if (!twoPass) {
            val args = mutableListOf("-hide_banner", "-y", "-i", input, "-map", "0:V:0")
            args += videoArgs(s, source, 0, passLogPrefix)
            args += audioArgs(s, source)
            args += subtitleArgs(s, source)
            args += listOf("-map_chapters", if (s.chapterMarkers) "0" else "-1", "-map_metadata", "0")
            if (s.container == Container.MP4 && s.webOptimized) args += listOf("-movflags", "+faststart")
            if (s.container == Container.MP4 && enc.isHevc) args += listOf("-tag:v", "hvc1")
            args += listOf("-max_muxing_queue_size", "9999", output)
            return listOf(FfmpegPass(args, 1f))
        }

        val first = mutableListOf("-hide_banner", "-y", "-i", input, "-map", "0:V:0")
        first += videoArgs(s, source, 1, passLogPrefix)
        first += listOf("-an", "-sn", "-f", "null", "/dev/null")

        val second = mutableListOf("-hide_banner", "-y", "-i", input, "-map", "0:V:0")
        second += videoArgs(s, source, 2, passLogPrefix)
        second += audioArgs(s, source)
        second += subtitleArgs(s, source)
        second += listOf("-map_chapters", if (s.chapterMarkers) "0" else "-1", "-map_metadata", "0")
        if (s.container == Container.MP4 && s.webOptimized) second += listOf("-movflags", "+faststart")
        if (s.container == Container.MP4 && enc.isHevc) second += listOf("-tag:v", "hvc1")
        second += listOf("-max_muxing_queue_size", "9999", output)
        return listOf(FfmpegPass(first, 0.4f), FfmpegPass(second, 0.6f))
    }

    // ---------------------------------------------------------------- video

    /** [pass] is 0 for single pass encodes, otherwise 1 or 2. */
    private fun videoArgs(s: EncodeSettings, src: SourceInfo?, pass: Int, log: String): List<String> {
        val enc = s.videoEncoder
        val a = mutableListOf<String>()

        val filters = videoFilters(s, src)
        if (filters.isNotEmpty()) a += listOf("-vf", filters.joinToString(","))

        a += framerateArgs(s, src)
        a += listOf("-c:v", enc.ffmpegName)

        val pixFmt = if (enc.tenBit) "yuv420p10le" else "yuv420p"
        a += listOf("-pix_fmt", pixFmt)

        val cq = s.qualityMode == QualityMode.CONSTANT_QUALITY && enc.maxRf > 0
        when {
            enc.isX26x -> {
                a += listOf("-preset", X264_SPEEDS[s.speed.coerceIn(0, 9)])
                if (cq) a += listOf("-crf", fmt(s.quality)) else a += listOf("-b:v", "${s.bitrateKbps}k")
                if (s.tune != "none" && s.tune in tunesFor(enc)) a += listOf("-tune", s.tune)
                if (s.profile != "auto" && s.profile in profilesFor(enc)) a += listOf("-profile:v", s.profile)
                if (enc == VideoEncoder.X264) {
                    if (s.level != "auto") a += listOf("-level:v", s.level)
                    if (s.extraOptions.isNotBlank()) a += listOf("-x264-params", s.extraOptions.trim())
                } else {
                    val params = mutableListOf<String>()
                    if (s.level != "auto") params += "level-idc=${s.level}"
                    if (s.extraOptions.isNotBlank()) params += s.extraOptions.trim()
                    if (pass > 0) params += listOf("pass=$pass", "stats=$log.x265")
                    if (params.isNotEmpty()) a += listOf("-x265-params", params.joinToString(":"))
                }
            }
            enc == VideoEncoder.VP9 -> {
                a += listOf("-deadline", "good", "-cpu-used", vp9CpuUsed(s.speed).toString(), "-row-mt", "1", "-tile-columns", "2")
                if (cq) a += listOf("-b:v", "0", "-crf", fmt(s.quality)) else a += listOf("-b:v", "${s.bitrateKbps}k")
            }
            enc == VideoEncoder.AV1 -> {
                a += listOf("-cpu-used", av1CpuUsed(s.speed).toString(), "-row-mt", "1", "-tiles", "2x2")
                if (cq) a += listOf("-b:v", "0", "-crf", fmt(s.quality)) else a += listOf("-b:v", "${s.bitrateKbps}k")
            }
            else -> { // MediaCodec hardware encoders
                a += listOf("-b:v", "${s.bitrateKbps}k")
            }
        }
        if (pass > 0 && (!enc.isX26x || enc == VideoEncoder.X264)) a += listOf("-pass", pass.toString(), "-passlogfile", log)
        return a
    }

    private fun framerateArgs(s: EncodeSettings, src: SourceInfo?): List<String> {
        val n = s.framerate
        return when (s.framerateMode) {
            FramerateMode.CONSTANT -> if (n > 0) listOf("-fps_mode", "cfr", "-r", n.toString()) else listOf("-fps_mode", "cfr")
            // "Peak": never exceed n. With a known source rate the cap is applied by the fps filter (see videoFilters);
            // without one, fall back to FFmpeg's own -fpsmax.
            FramerateMode.PEAK -> if (n > 0 && src == null) listOf("-fpsmax", n.toString()) else emptyList()
            FramerateMode.VARIABLE -> listOf("-fps_mode", "vfr")
        }
    }

    fun videoFilters(s: EncodeSettings, src: SourceInfo? = null): List<String> {
        val f = mutableListOf<String>()
        if (s.cropTop + s.cropBottom + s.cropLeft + s.cropRight > 0) {
            f += "crop=iw-${s.cropLeft + s.cropRight}:ih-${s.cropTop + s.cropBottom}:${s.cropLeft}:${s.cropTop}"
        }
        when (s.deinterlace) {
            Deinterlace.OFF -> {}
            Deinterlace.DECOMB -> f += "yadif=0:-1:1"
            Deinterlace.YADIF -> f += "yadif=0:-1:0"
            Deinterlace.BWDIF -> f += "bwdif=0:-1:0"
        }
        when (s.denoise) {
            Strength.OFF -> {}
            Strength.LIGHT -> f += "hqdn3d=2:1:2:3"
            Strength.MEDIUM -> f += "hqdn3d=3:2:2:3"
            Strength.STRONG -> f += "hqdn3d=7:7:5:5"
        }
        when (s.sharpen) {
            Strength.OFF -> {}
            Strength.LIGHT -> f += "unsharp=5:5:0.5:5:5:0"
            Strength.MEDIUM -> f += "unsharp=5:5:1.0:5:5:0"
            Strength.STRONG -> f += "unsharp=5:5:1.5:5:5:0"
        }
        when (s.rotation) {
            Rotation.NONE -> {}
            Rotation.CW90 -> f += "transpose=1"
            Rotation.CCW90 -> f += "transpose=2"
            Rotation.ROT180 -> f += "hflip,vflip"
            Rotation.HFLIP -> f += "hflip"
        }
        if (s.framerateMode == FramerateMode.PEAK && s.framerate > 0 && src != null && src.fps > s.framerate + 0.01) {
            f += "fps=${s.framerate}"
        }
        if (s.maxWidth > 0 || s.maxHeight > 0) {
            val w = if (s.maxWidth > 0) "min(iw,${s.maxWidth})" else "iw"
            val h = if (s.maxHeight > 0) "min(ih,${s.maxHeight})" else "ih"
            f += "scale=w='$w':h='$h':force_original_aspect_ratio=decrease:force_divisible_by=2"
        } else {
            f += "scale=w='trunc(iw/2)*2':h='trunc(ih/2)*2'"
        }
        f += "setsar=1"
        if (s.grayscale) f += "hue=s=0"
        return f
    }

    // ---------------------------------------------------------------- audio

    private fun audioArgs(s: EncodeSettings, src: SourceInfo?): List<String> {
        if (s.audioTracks == TrackMode.NONE) return emptyList()
        val streams: List<AudioStream?> = when {
            src == null -> listOf(null)
            src.audio.isEmpty() -> return emptyList()
            s.audioTracks == TrackMode.FIRST -> listOf(src.audio.first())
            else -> src.audio
        }
        val a = mutableListOf<String>()
        streams.forEachIndexed { j, st ->
            if (st != null) a += listOf("-map", "0:a:${st.ordinal}")
            else a += listOf("-map", if (s.audioTracks == TrackMode.FIRST) "0:a:0?" else "0:a?")
        }
        // Without probe data (st == null) all mapped tracks share the same options.
        streams.forEachIndexed { j, st ->
            val idx = if (st == null) "" else ":$j"
            a += audioStreamArgs(s, st, idx)
        }
        return a
    }

    private fun audioStreamArgs(s: EncodeSettings, st: AudioStream?, idx: String): List<String> {
        val container = s.container
        val copyOk = copyable(container)
        val srcCodec = st?.codec

        var encoder = s.audioEncoder
        var copy = false
        if (encoder == AudioEncoder.COPY) {
            if (srcCodec != null && (copyOk == null || srcCodec in copyOk)) copy = true
            else encoder = if (container == Container.WEBM) AudioEncoder.OPUS else AudioEncoder.AAC
        } else if (s.audioPassthruMatching && srcCodec != null && srcCodec == encoder.codec && s.audioGainDb == 0 &&
            s.audioSampleRate == 0 && (s.audioMixdown == Mixdown.AUTO || (st.channels in 1..s.audioMixdown.channels))
        ) {
            copy = true
        }
        if (copy) return listOf("-c:a$idx", "copy")

        val a = mutableListOf("-c:a$idx", encoder.ffmpegName)
        if (!encoder.lossless) a += listOf("-b:a$idx", "${s.audioBitrate}k")

        val srcCh = st?.channels ?: 0
        val target = if (s.audioMixdown == Mixdown.AUTO) maxChannels(encoder) else minOf(s.audioMixdown.channels, maxChannels(encoder))
        if (srcCh == 0) {
            if (s.audioMixdown != Mixdown.AUTO) a += listOf("-ac:a$idx", target.toString())
        } else if (srcCh > target) {
            a += listOf("-ac:a$idx", target.toString())
        }
        val outCh = if (srcCh == 0) 2 else minOf(srcCh, target)

        // Some encoders cap the bitrate (per channel pair / in total); stay inside their limits.
        val cap = when (encoder) {
            AudioEncoder.MP3 -> 320
            AudioEncoder.AC3 -> 640
            AudioEncoder.OPUS -> 128 * outCh
            else -> 320 * outCh
        }
        val bi = a.indexOf("-b:a$idx")
        if (bi >= 0) a[bi + 1] = "${s.audioBitrate.coerceAtMost(cap)}k"

        val af = mutableListOf<String>()
        // libopus only accepts the plain Vorbis-order layouts (a 5.1(side) source would be rejected)
        if (encoder == AudioEncoder.OPUS && outCh > 2) {
            layoutFor(outCh)?.let { af += "aformat=channel_layouts=$it" }
            a += listOf("-mapping_family:a$idx", "1")
        }
        if (s.audioGainDb != 0) af += "volume=${s.audioGainDb}dB"
        if (af.isNotEmpty()) a += listOf("-filter:a$idx", af.joinToString(","))
        if (s.audioSampleRate > 0) a += listOf("-ar:a$idx", s.audioSampleRate.toString())
        return a
    }

    private fun layoutFor(channels: Int): String? = when (channels) {
        3 -> "3.0"; 4 -> "quad"; 5 -> "5.0"; 6 -> "5.1"; 7 -> "6.1"; 8 -> "7.1"; else -> null
    }

    // ------------------------------------------------------------ subtitles

    private fun subtitleArgs(s: EncodeSettings, src: SourceInfo?): List<String> {
        if (s.subtitles == TrackMode.NONE || s.container == Container.WEBM) return emptyList()
        val candidates = src?.subtitles ?: return emptyList()
        val usable = if (s.container == Container.MP4) candidates.filter { !it.bitmap } else candidates
        val chosen = if (s.subtitles == TrackMode.FIRST) usable.take(1) else usable
        if (chosen.isEmpty()) return emptyList()
        val a = mutableListOf<String>()
        chosen.forEach { a += listOf("-map", "0:s:${it.ordinal}") }
        a += listOf("-c:s", if (s.container == Container.MP4) "mov_text" else "copy")
        return a
    }

    private fun fmt(v: Float) = String.format(Locale.US, "%.1f", v)

    /** Command line for logging: quotes arguments that contain characters special to a shell. */
    fun display(args: List<String>): String = "ffmpeg " + args.joinToString(" ") { if (it.any { c -> c == ' ' || c == '\'' || c == '(' }) "\"$it\"" else it }
}
