package com.galandras12.handdroid.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.antonkarpenko.ffmpegkit.StreamInformation
import com.galandras12.handdroid.data.AudioStream
import com.galandras12.handdroid.data.SourceInfo
import com.galandras12.handdroid.data.SubtitleStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads stream information from a user selected document with FFprobe. */
object MediaProbe {

    suspend fun probe(context: Context, uri: Uri): Result<SourceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val (name, size) = queryNameAndSize(context, uri)
            val input = if (uri.scheme == "content") FFmpegKitConfig.getSafParameterForRead(context, uri) else uri.toString()
            val session = FFprobeKit.getMediaInformation(input)
            val info = session.mediaInformation ?: error("Unsupported or unreadable media file")

            val streams = info.streams
            val video = streams.firstOrNull { it.type == "video" && !isCoverArt(it) }
                ?: error("No video stream found")
            val audio = streams.filter { it.type == "audio" }.mapIndexed { i, s ->
                AudioStream(
                    ordinal = i,
                    codec = s.codec ?: "unknown",
                    channels = (s.getNumberProperty("channels") ?: 2L).toInt(),
                    sampleRate = s.sampleRate?.toIntOrNull() ?: 0,
                    bitrateKbps = ((s.bitrate?.toLongOrNull() ?: 0L) / 1000).toInt(),
                    language = s.tag("language"),
                    title = s.tag("title"),
                )
            }
            val subs = streams.filter { it.type == "subtitle" }.mapIndexed { i, s ->
                SubtitleStream(i, s.codec ?: "unknown", s.tag("language"), s.tag("title"))
            }
            SourceInfo(
                input = input,
                uri = uri.toString(),
                name = name,
                sizeBytes = size,
                durationMs = ((info.duration?.toDoubleOrNull() ?: 0.0) * 1000).toLong(),
                width = (video.width ?: 0L).toInt(),
                height = (video.height ?: 0L).toInt(),
                fps = parseRate(video.averageFrameRate) ?: parseRate(video.realFrameRate) ?: 0.0,
                videoCodec = video.codec ?: "unknown",
                videoBitrateKbps = ((video.bitrate?.toLongOrNull() ?: info.bitrate?.toLongOrNull() ?: 0L) / 1000).toInt(),
                chapters = info.chapters?.size ?: 0,
                audio = audio,
                subtitles = subs,
            )
        }
    }

    private fun isCoverArt(s: StreamInformation) = s.codec in setOf("mjpeg", "png") && (s.averageFrameRate == "0/0" || s.averageFrameRate == null)

    private fun StreamInformation.tag(key: String): String? = tags?.optString(key)?.takeIf { it.isNotBlank() }

    private fun parseRate(r: String?): Double? {
        if (r.isNullOrBlank()) return null
        val parts = r.split('/')
        val v = if (parts.size == 2) {
            val d = parts[1].toDoubleOrNull() ?: return null
            if (d == 0.0) return null
            (parts[0].toDoubleOrNull() ?: return null) / d
        } else r.toDoubleOrNull()
        return v?.takeIf { it > 0 }
    }

    private fun queryNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "video"
        var size = 0L
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        return name to size
    }
}
