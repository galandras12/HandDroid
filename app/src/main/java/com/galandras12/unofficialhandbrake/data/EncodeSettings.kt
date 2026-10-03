package com.galandras12.unofficialhandbrake.data

import kotlinx.serialization.Serializable

enum class Container(val ext: String, val label: String) {
    MP4("mp4", "MP4"), MKV("mkv", "MKV"), WEBM("webm", "WebM");

    val mime: String get() = when (this) { MP4 -> "video/mp4"; MKV -> "video/x-matroska"; WEBM -> "video/webm" }
}

enum class VideoEncoder(
    val label: String,
    val ffmpegName: String,
    val containers: Set<Container>,
    val hardware: Boolean = false,
    val tenBit: Boolean = false,
    /** Highest value of the constant-quality scale (RF); 0 when CRF isn't supported. */
    val maxRf: Int = 51,
) {
    X264("H.264 (x264)", "libx264", setOf(Container.MP4, Container.MKV)),
    X265("H.265 (x265)", "libx265", setOf(Container.MP4, Container.MKV)),
    X265_10BIT("H.265 10-bit (x265)", "libx265", setOf(Container.MP4, Container.MKV), tenBit = true),
    VP9("VP9 (libvpx)", "libvpx-vp9", setOf(Container.MKV, Container.WEBM), maxRf = 63),
    AV1("AV1 (libaom)", "libaom-av1", setOf(Container.MP4, Container.MKV, Container.WEBM), maxRf = 63),
    H264_HW("H.264 (Android hardware)", "h264_mediacodec", setOf(Container.MP4, Container.MKV), hardware = true, maxRf = 0),
    H265_HW("H.265 (Android hardware)", "hevc_mediacodec", setOf(Container.MP4, Container.MKV), hardware = true, maxRf = 0);

    val isX26x: Boolean get() = this == X264 || this == X265 || this == X265_10BIT
    val isHevc: Boolean get() = this == X265 || this == X265_10BIT || this == H265_HW

    /** The software encoder that replaces a hardware one when MediaCodec fails on a device. */
    val softwareEquivalent: VideoEncoder get() = when (this) { H264_HW -> X264; H265_HW -> X265; else -> this }
}

enum class QualityMode { CONSTANT_QUALITY, AVG_BITRATE }
enum class FramerateMode { PEAK, CONSTANT, VARIABLE }
enum class TrackMode { NONE, FIRST, ALL }
enum class Rotation { NONE, CW90, ROT180, CCW90, HFLIP }
enum class Deinterlace { OFF, DECOMB, YADIF, BWDIF }
enum class Strength { OFF, LIGHT, MEDIUM, STRONG }

enum class AudioEncoder(val label: String, val ffmpegName: String, val codec: String, val containers: Set<Container>) {
    AAC("AAC", "aac", "aac", setOf(Container.MP4, Container.MKV)),
    OPUS("Opus", "libopus", "opus", setOf(Container.MP4, Container.MKV, Container.WEBM)),
    MP3("MP3", "libmp3lame", "mp3", setOf(Container.MP4, Container.MKV)),
    AC3("AC-3", "ac3", "ac3", setOf(Container.MP4, Container.MKV)),
    EAC3("E-AC-3", "eac3", "eac3", setOf(Container.MP4, Container.MKV)),
    FLAC("FLAC", "flac", "flac", setOf(Container.MP4, Container.MKV)),
    VORBIS("Vorbis", "libvorbis", "vorbis", setOf(Container.MKV, Container.WEBM)),
    /** Copy whatever the source has when the container accepts it; otherwise fall back to AAC (Opus for WebM). */
    COPY("Auto passthru", "copy", "", Container.entries.toSet());

    val lossless: Boolean get() = this == FLAC
}

enum class Mixdown(val channels: Int) { AUTO(0), MONO(1), STEREO(2), SURROUND_5_1(6) }

@Serializable
data class EncodeSettings(
    // Summary
    val container: Container = Container.MP4,
    val webOptimized: Boolean = false,
    val chapterMarkers: Boolean = true,
    // Video
    val videoEncoder: VideoEncoder = VideoEncoder.X264,
    val framerate: Int = 0,
    val framerateMode: FramerateMode = FramerateMode.PEAK,
    val qualityMode: QualityMode = QualityMode.CONSTANT_QUALITY,
    val quality: Float = 22f,
    val bitrateKbps: Int = 4000,
    val twoPass: Boolean = false,
    /** 0 = slowest / best compression … 9 = fastest. Mapped to each encoder's own preset scale. */
    val speed: Int = 4,
    val profile: String = "auto",
    val level: String = "auto",
    val tune: String = "none",
    val extraOptions: String = "",
    // Audio
    val audioTracks: TrackMode = TrackMode.FIRST,
    val audioEncoder: AudioEncoder = AudioEncoder.AAC,
    val audioMixdown: Mixdown = Mixdown.STEREO,
    val audioBitrate: Int = 160,
    val audioSampleRate: Int = 0,
    val audioGainDb: Int = 0,
    /** Copy the source track untouched when it already uses the selected encoder's codec. */
    val audioPassthruMatching: Boolean = true,
    // Subtitles (soft subtitles only)
    val subtitles: TrackMode = TrackMode.NONE,
    // Picture
    val maxWidth: Int = 0,
    val maxHeight: Int = 0,
    val rotation: Rotation = Rotation.NONE,
    val cropTop: Int = 0,
    val cropBottom: Int = 0,
    val cropLeft: Int = 0,
    val cropRight: Int = 0,
    // Filters
    val deinterlace: Deinterlace = Deinterlace.OFF,
    val denoise: Strength = Strength.OFF,
    val sharpen: Strength = Strength.OFF,
    val grayscale: Boolean = false,
) {
    /** Returns a copy with values that don't make sense for the chosen encoder / container fixed up. */
    fun normalized(): EncodeSettings {
        var s = this
        if (s.container !in s.videoEncoder.containers) s = s.copy(container = s.videoEncoder.containers.first())
        if (s.videoEncoder.maxRf == 0) s = s.copy(qualityMode = QualityMode.AVG_BITRATE, twoPass = false)
        if (s.videoEncoder.maxRf > 0 && s.qualityMode == QualityMode.CONSTANT_QUALITY) {
            s = s.copy(quality = s.quality.coerceIn(0f, s.videoEncoder.maxRf.toFloat()))
        }
        if (s.container == Container.WEBM && s.subtitles != TrackMode.NONE) s = s.copy(subtitles = TrackMode.NONE)
        if (s.audioEncoder != AudioEncoder.COPY && s.container !in s.audioEncoder.containers) {
            s = s.copy(audioEncoder = if (s.container == Container.WEBM) AudioEncoder.OPUS else AudioEncoder.AAC)
        }
        return s
    }
}

@Serializable
data class Preset(
    val name: String,
    val category: String,
    val description: String = "",
    val settings: EncodeSettings,
    val builtIn: Boolean = true,
)

@Serializable
data class PresetFile(val presets: List<Preset>)
