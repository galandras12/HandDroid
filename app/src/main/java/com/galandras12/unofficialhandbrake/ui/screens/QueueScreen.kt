package com.galandras12.unofficialhandbrake.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galandras12.unofficialhandbrake.R
import com.galandras12.unofficialhandbrake.engine.EncodeJob
import com.galandras12.unofficialhandbrake.engine.EncodeService
import com.galandras12.unofficialhandbrake.engine.JobStatus
import com.galandras12.unofficialhandbrake.ui.MainViewModel
import com.galandras12.unofficialhandbrake.ui.formatSize

@Composable
fun QueueScreen(vm: MainViewModel, onOpenLog: (Long) -> Unit, requestNotifications: () -> Unit) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val active by vm.queueActive.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                Button(onClick = vm::stopQueue) { Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.stop_queue)) }
            } else {
                Button(
                    onClick = { requestNotifications(); vm.startQueue() },
                    enabled = jobs.any { it.status == JobStatus.PENDING },
                ) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.start_queue)) }
            }
            Box(Modifier.weight(1f))
            TextButton(onClick = vm::clearFinished, enabled = jobs.any { it.finished }) { Text(stringResource(R.string.clear_finished)) }
        }
        if (jobs.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                Text(stringResource(R.string.queue_empty), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text(stringResource(R.string.queue_empty_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(jobs, key = { it.id }) { job ->
                    JobCard(job, vm, onOpenLog)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JobCard(job: EncodeJob, vm: MainViewModel, onOpenLog: (Long) -> Unit) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusIcon(job.status)
                Column(Modifier.weight(1f)) {
                    Text("${job.outputName}.${job.settings.container.ext}", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(
                        "${job.presetName} · ${job.settings.videoEncoder.label}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (job.status != JobStatus.RUNNING) {
                    IconButton(onClick = { vm.removeJob(job.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.remove), Modifier.size(20.dp))
                    }
                }
            }
            when (job.status) {
                JobStatus.PENDING -> Text(stringResource(R.string.status_waiting), style = MaterialTheme.typography.bodySmall)
                JobStatus.RUNNING -> {
                    if (job.progress >= 0) LinearWavyProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
                    else LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    val parts = buildList {
                        if (job.progress >= 0) add("${(job.progress * 100).toInt()} %")
                        if (job.fps > 0) add("%.1f fps".format(job.fps))
                        if (job.speed > 0) add("%.2f×".format(job.speed))
                        if (job.etaSeconds >= 0) add(stringResource(R.string.eta_short, EncodeService.formatClock(job.etaSeconds)))
                    }
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (job.usedSoftwareFallback) Text(stringResource(R.string.hw_fallback), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                JobStatus.DONE -> Text(
                    stringResource(R.string.status_done) + " · " + formatSize(job.outputSizeBytes) +
                        if (job.source.sizeBytes > 0 && job.outputSizeBytes > 0) " (${job.outputSizeBytes * 100 / job.source.sizeBytes} %)" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                JobStatus.FAILED -> Text(job.error ?: stringResource(R.string.status_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 4)
                JobStatus.CANCELLED -> Text(stringResource(R.string.status_cancelled), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (job.status) {
                    JobStatus.RUNNING -> OutlinedButton(onClick = { vm.cancelJob(job.id) }) { Text(stringResource(R.string.cancel)) }
                    JobStatus.PENDING -> OutlinedButton(onClick = { vm.cancelJob(job.id) }) { Text(stringResource(R.string.cancel)) }
                    JobStatus.DONE -> {
                        job.outputUri?.let { uri ->
                            OutlinedButton(onClick = {
                                context.startActivity(Intent.createChooser(
                                    Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), job.settings.container.mime)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                            }) { Text(stringResource(R.string.open)) }
                            TextButton(onClick = {
                                context.startActivity(Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType(job.settings.container.mime)
                                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                            }) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.share)) }
                        }
                    }
                    JobStatus.FAILED, JobStatus.CANCELLED -> OutlinedButton(onClick = { vm.retryJob(job.id) }) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.retry))
                    }
                }
                if (job.log.isNotEmpty()) TextButton(onClick = { onOpenLog(job.id) }) { Text(stringResource(R.string.activity_log)) }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: JobStatus) {
    val (icon, tint) = when (status) {
        JobStatus.PENDING -> Icons.Rounded.Schedule to MaterialTheme.colorScheme.outline
        JobStatus.RUNNING -> Icons.Rounded.PlayArrow to MaterialTheme.colorScheme.primary
        JobStatus.DONE -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
        JobStatus.FAILED -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
        JobStatus.CANCELLED -> Icons.Rounded.Cancel to MaterialTheme.colorScheme.outline
    }
    Icon(icon, null, tint = tint)
}
