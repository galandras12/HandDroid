package com.galandras12.handdroid.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.galandras12.handdroid.HandDroidApp
import com.galandras12.handdroid.MainActivity
import com.galandras12.handdroid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Keeps the process alive and shows progress while the queue is encoding. */
class EncodeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = (application as HandDroidApp).engine
        if (intent?.action == ACTION_STOP) engine.stop()

        createChannels(this)
        startForegroundCompat(buildNotification(this, engine.jobs.value, true))

        if (wakeLock == null) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HandDroid:encode").apply { acquire(6 * 60 * 60 * 1000L) }
        }
        if (watcher == null) {
            watcher = scope.launch {
                var finishedAnnounced = false
                engine.jobs.combine(engine.active) { jobs, active -> jobs to active }.collect { (jobs, active) ->
                    if (active) {
                        finishedAnnounced = false
                        NotificationManagerCompat.from(this@EncodeService).notifySafely(NOTIF_ID, buildNotification(this@EncodeService, jobs, true))
                    } else if (!finishedAnnounced) {
                        finishedAnnounced = true
                        announceFinished(jobs)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            startForeground(NOTIF_ID, n, type)
        } else startForeground(NOTIF_ID, n)
    }

    private fun announceFinished(jobs: List<EncodeJob>) {
        val done = jobs.count { it.status == JobStatus.DONE }
        val failed = jobs.count { it.status == JobStatus.FAILED }
        if (done + failed == 0) return
        val text = if (failed == 0) resources.getQuantityString(R.plurals.notif_finished_ok, done, done)
        else getString(R.string.notif_finished_mixed, done, failed)
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_handdroid)
            .setContentTitle(getString(R.string.notif_finished_title))
            .setContentText(text)
            .setContentIntent(openAppIntent(this))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notifySafely(NOTIF_DONE_ID, n)
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_PROGRESS = "encode_progress"
        private const val CHANNEL_DONE = "encode_done"
        private const val NOTIF_ID = 1
        private const val NOTIF_DONE_ID = 2
        const val ACTION_STOP = "com.galandras12.handdroid.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, EncodeService::class.java))
        }

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        private fun openAppIntent(context: Context) = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun buildNotification(context: Context, jobs: List<EncodeJob>, ongoing: Boolean): Notification {
            val running = jobs.firstOrNull { it.status == JobStatus.RUNNING }
            val total = jobs.count { it.status != JobStatus.CANCELLED }
            val index = jobs.count { it.status == JobStatus.DONE || it.status == JobStatus.FAILED } + 1
            val stopIntent = PendingIntent.getService(
                context, 1, Intent(context, EncodeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
            )
            val b = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_stat_handdroid)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent(context))
                .addAction(0, context.getString(R.string.action_stop), stopIntent)
            if (running == null) {
                b.setContentTitle(context.getString(R.string.app_name)).setProgress(0, 0, true)
            } else {
                b.setContentTitle(context.getString(R.string.notif_encoding, index.coerceAtMost(total), total, running.outputName))
                val pct = (running.progress * 100).toInt()
                if (running.progress < 0) b.setProgress(0, 0, true) else b.setProgress(100, pct, false)
                val eta = if (running.etaSeconds >= 0) " · " + context.getString(R.string.eta_short, formatClock(running.etaSeconds)) else ""
                b.setContentText(if (running.progress >= 0) "$pct %$eta" else context.getString(R.string.working))
            }
            return b.build()
        }

        fun formatClock(seconds: Long): String {
            val h = seconds / 3600
            val m = seconds % 3600 / 60
            val s = seconds % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }

        private fun NotificationManagerCompat.notifySafely(id: Int, n: Notification) {
            try { notify(id, n) } catch (_: SecurityException) { /* notification permission denied */ }
        }
    }
}
