package com.galandras12.handdroid.engine

import android.content.Context
import android.net.Uri
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.galandras12.handdroid.data.EncodeSettings
import com.galandras12.handdroid.data.QualityMode
import com.galandras12.handdroid.data.SettingsStore
import com.galandras12.handdroid.data.SourceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/** Runs the encode queue one job at a time. Lives as long as the application process. */
class EncodeEngine(private val context: Context, private val settingsStore: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nextId = AtomicLong(1)

    private val _jobs = MutableStateFlow<List<EncodeJob>>(emptyList())
    val jobs: StateFlow<List<EncodeJob>> = _jobs.asStateFlow()

    private val _active = MutableStateFlow(false)
    /** True while the queue is being processed. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var runner: Job? = null
    @Volatile private var currentSession: Long = -1
    @Volatile private var cancelRequested: Long = -1

    private val tempDir get() = File(context.cacheDir, "encode").apply { mkdirs() }

    fun add(source: SourceInfo, settings: EncodeSettings, presetName: String, outputName: String): Long {
        val id = nextId.getAndIncrement()
        val job = EncodeJob(id, source, settings.normalized(), presetName, outputName)
        _jobs.update { it + job }
        return id
    }

    fun remove(id: Long) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (job.status == JobStatus.RUNNING) cancelJob(id)
        _jobs.update { list -> list.filterNot { it.id == id } }
    }

    fun clearFinished() = _jobs.update { list -> list.filterNot { it.finished } }

    fun retry(id: Long) = patch(id) {
        it.copy(status = JobStatus.PENDING, progress = 0f, error = null, log = "", outputUri = null, usedSoftwareFallback = false)
    }

    /** Starts processing pending jobs. Returns false when there is nothing to do. */
    fun start(): Boolean {
        if (_jobs.value.none { it.status == JobStatus.PENDING }) return false
        if (runner?.isActive == true) return true
        _active.value = true
        runner = scope.launch {
            try {
                while (true) {
                    val job = _jobs.value.firstOrNull { it.status == JobStatus.PENDING } ?: break
                    runJob(job)
                }
            } finally {
                _active.value = false
            }
        }
        return true
    }

    /** Cancels the running job and stops the queue; remaining jobs stay pending. */
    fun stop() {
        runner?.let { r ->
            _jobs.value.firstOrNull { it.status == JobStatus.RUNNING }?.let { cancelJob(it.id) }
            r.cancel()
        }
    }

    fun cancelJob(id: Long) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (job.status == JobStatus.RUNNING) {
            cancelRequested = id
            if (currentSession >= 0) FFmpegKit.cancel(currentSession)
        } else if (job.status == JobStatus.PENDING) {
            patch(id) { it.copy(status = JobStatus.CANCELLED) }
        }
    }

    // ------------------------------------------------------------------ internals

    private fun patch(id: Long, f: (EncodeJob) -> EncodeJob) = _jobs.update { list -> list.map { if (it.id == id) f(it) else it } }

    private suspend fun runJob(job: EncodeJob) {
        patch(job.id) { it.copy(status = JobStatus.RUNNING, progress = 0f, error = null, log = "") }
        val temp = File(tempDir, "job${job.id}.${job.settings.container.ext}")
        try {
            var settings = job.settings
            var result = encode(job, settings, temp)
            if (result is Outcome.Failed && settings.videoEncoder.hardware && cancelRequested != job.id) {
                // MediaCodec encoders are device dependent; retry with the matching software encoder.
                val sw = settings.videoEncoder.softwareEquivalent
                appendLog(job.id, "\nHardware encoder failed - retrying with ${sw.label}\n")
                settings = settings.copy(videoEncoder = sw, qualityMode = QualityMode.AVG_BITRATE, speed = 7).normalized()
                patch(job.id) { it.copy(usedSoftwareFallback = true, progress = 0f) }
                result = encode(job, settings, temp)
            }
            when (result) {
                Outcome.Success -> {
                    val tree = settingsStore.outputTree.first()
                    val saved = OutputStore.save(context, temp, job.outputName, settings.container, tree)
                    patch(job.id) {
                        it.copy(status = JobStatus.DONE, progress = 1f, etaSeconds = 0, outputUri = saved.uri, outputSizeBytes = saved.sizeBytes)
                    }
                }
                Outcome.Cancelled -> patch(job.id) { it.copy(status = JobStatus.CANCELLED) }
                is Outcome.Failed -> patch(job.id) { it.copy(status = JobStatus.FAILED, error = result.message) }
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            patch(job.id) { it.copy(status = JobStatus.CANCELLED) }
            throw t
        } catch (t: Throwable) {
            patch(job.id) { it.copy(status = JobStatus.FAILED, error = t.message ?: t.javaClass.simpleName) }
        } finally {
            temp.delete()
            tempDir.listFiles { f -> f.name.startsWith("job${job.id}.") }?.forEach { it.delete() }
            currentSession = -1
            if (cancelRequested == job.id) cancelRequested = -1
        }
    }

    private sealed interface Outcome {
        data object Success : Outcome
        data object Cancelled : Outcome
        data class Failed(val message: String) : Outcome
    }

    private suspend fun encode(job: EncodeJob, settings: EncodeSettings, temp: File): Outcome {
        val input = Uri.parse(job.source.uri).let {
            if (it.scheme == "content") FFmpegKitConfig.getSafParameterForRead(context, it) else it.toString()
        }
        temp.delete()
        val passes = CommandBuilder.build(input, temp.absolutePath, settings, job.source, File(tempDir, "job${job.id}").absolutePath)
        val duration = job.source.durationMs.toDouble()
        var done = 0f
        for ((i, pass) in passes.withIndex()) {
            appendLog(job.id, CommandBuilder.display(pass.args) + "\n\n")
            val base = done
            val weight = pass.weight
            val startedAt = System.currentTimeMillis()
            val code = suspendCancellableCoroutine { cont ->
                val session = FFmpegKit.executeWithArgumentsAsync(
                    pass.args.toTypedArray(),
                    { s -> cont.resume(s.returnCode) },
                    { log -> appendLog(job.id, log.message) },
                    { st ->
                        val p = if (duration > 0) ((st.time / duration).coerceIn(0.0, 1.0)).toFloat() else -1f
                        val overall = if (p < 0) -1f else base + weight * p
                        val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
                        val eta = if (p > 0.01f) {
                            val passRemaining = elapsed * (1 - p) / p
                            val laterPasses = passes.drop(i + 1).sumOf { it.weight.toDouble() } / weight * (elapsed / p)
                            (passRemaining + laterPasses).toLong()
                        } else -1L
                        patch(job.id) { it.copy(progress = overall, fps = st.videoFps, speed = st.speed.toFloat(), etaSeconds = eta) }
                    },
                )
                currentSession = session.sessionId
                if (cancelRequested == job.id) FFmpegKit.cancel(session.sessionId)
                cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
            }
            appendLog(job.id, "", force = true)
            when {
                ReturnCode.isCancel(code) -> return Outcome.Cancelled
                !ReturnCode.isSuccess(code) -> return Outcome.Failed(lastLogLines(job.id))
            }
            done += weight
        }
        return if (temp.exists() && temp.length() > 0) Outcome.Success else Outcome.Failed("The encoder produced no output")
    }

    // FFmpeg can log many lines per second; batch them so the UI isn't recomposed for each one.
    private val logLock = Any()
    private val pendingLog = HashMap<Long, StringBuilder>()
    private var lastLogFlush = 0L

    private fun appendLog(id: Long, text: String, force: Boolean = false) {
        synchronized(logLock) {
            if (text.isNotEmpty()) pendingLog.getOrPut(id) { StringBuilder() }.append(text)
            val now = System.currentTimeMillis()
            if (!force && now - lastLogFlush < LOG_FLUSH_MS) return
            lastLogFlush = now
            val chunk = pendingLog.remove(id)?.toString() ?: return
            patch(id) { it.copy(log = (it.log + chunk).takeLast(MAX_LOG)) }
        }
    }

    private fun lastLogLines(id: Long): String {
        appendLog(id, "", force = true)
        return lastLogLinesOf(id)
    }

    private fun lastLogLinesOf(id: Long): String =
        _jobs.value.firstOrNull { it.id == id }?.log?.trim()?.lines()?.filter { it.isNotBlank() }?.takeLast(3)?.joinToString("\n")
            ?: "Encoding failed"

    private companion object {
        const val MAX_LOG = 60_000
        const val LOG_FLUSH_MS = 400L
    }
}
