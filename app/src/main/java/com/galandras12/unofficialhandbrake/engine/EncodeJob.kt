package com.galandras12.unofficialhandbrake.engine

import com.galandras12.unofficialhandbrake.data.EncodeSettings
import com.galandras12.unofficialhandbrake.data.SourceInfo

enum class JobStatus { PENDING, RUNNING, DONE, FAILED, CANCELLED }

data class EncodeJob(
    val id: Long,
    val source: SourceInfo,
    val settings: EncodeSettings,
    val presetName: String,
    /** File name without extension. */
    val outputName: String,
    val status: JobStatus = JobStatus.PENDING,
    /** 0..1, or -1 when the source duration is unknown. */
    val progress: Float = 0f,
    val fps: Float = 0f,
    val speed: Float = 0f,
    val etaSeconds: Long = -1,
    val outputUri: String? = null,
    val outputSizeBytes: Long = 0,
    val error: String? = null,
    val usedSoftwareFallback: Boolean = false,
    val log: String = "",
) {
    val finished: Boolean get() = status == JobStatus.DONE || status == JobStatus.FAILED || status == JobStatus.CANCELLED
}
