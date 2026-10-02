package com.galandras12.handdroid.data

data class AudioStream(
    /** Position among the audio streams of the file (0-based), usable as `0:a:N`. */
    val ordinal: Int,
    val codec: String,
    val channels: Int,
    val sampleRate: Int,
    val bitrateKbps: Int,
    val language: String?,
    val title: String?,
)

data class SubtitleStream(
    val ordinal: Int,
    val codec: String,
    val language: String?,
    val title: String?,
) {
    /** Picture based subtitles (DVD / Blu-ray) can't be converted to the text format MP4 needs. */
    val bitmap: Boolean get() = codec in BITMAP_CODECS

    companion object {
        val BITMAP_CODECS = setOf("hdmv_pgs_subtitle", "dvd_subtitle", "dvb_subtitle", "xsub")
    }
}

data class SourceInfo(
    /** What gets passed to FFmpeg's `-i` (a `saf:` URL for content:// documents). */
    val input: String,
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Double,
    val videoCodec: String,
    val videoBitrateKbps: Int,
    val chapters: Int,
    val audio: List<AudioStream>,
    val subtitles: List<SubtitleStream>,
) {
    val baseName: String get() = name.substringBeforeLast('.', name)
}
