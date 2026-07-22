package com.vythera.stride.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vythera.stride.Graph
import com.vythera.stride.MainActivity
import com.vythera.stride.R
import com.vythera.stride.notifications.Notifier
import com.vythera.stride.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground step-tracking service: keeps the hardware step counter alive in
 * the background with a live progress notification. Started/stopped from
 * Settings ("Background tracking") or the notification's Stop action.
 */
class StepTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null

    /** Mirrors the user's "Live updates" preference for notification building. */
    @Volatile
    private var promoteLiveUpdate = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Graph.ensureInit(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { Graph.prefs.setBackgroundTracking(false) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // The manifest declares FGS type "health", which on Android 14+ needs a
        // health permission (ACTIVITY_RECOGNITION) granted at runtime. When it
        // isn't, fall back to a plain foreground start so the ongoing count can
        // still run off Health Connect data.
        val healthGranted = android.os.Build.VERSION.SDK_INT < 29 ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val started = runCatching {
            if (healthGranted) {
                androidx.core.app.ServiceCompat.startForeground(
                    this, NOTIF_ID, buildNotification(0, 8000),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                androidx.core.app.ServiceCompat.startForeground(
                    this, NOTIF_ID, buildNotification(0, 8000),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            }
        }.onFailure {
            android.util.Log.e("StepTracking", "startForeground failed", it)
        }
        if (started.isFailure) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (trackingJob == null) {
            trackingJob = scope.launch {
                // Hardware counter → repository (max-merged with Health Connect)
                launch {
                    runCatching {
                        Graph.stepSensor.rawSteps().collect { raw ->
                            Graph.repository.onSensorRaw(raw)
                        }
                    }
                }
                // Periodic full refresh + nudge check
                launch {
                    while (isActive) {
                        runCatching { Graph.repository.syncToday() }
                        runCatching { Graph.repository.maybeNudge() }
                        delay(5 * 60_000)
                    }
                }
                // Live-update preference drives whether the ongoing notification
                // is promoted to a status-bar chip
                launch {
                    Graph.prefs.prefs
                        .map { it.liveUpdates }
                        .distinctUntilChanged()
                        .collect { enabled ->
                            promoteLiveUpdate = enabled
                            val stats = Graph.repository.observeToday().first()
                            runCatching {
                                NotificationManagerCompat.from(this@StepTrackingService)
                                    .notify(NOTIF_ID, buildNotification(stats.steps, stats.goal))
                            }
                        }
                }
                // Keep the live notification's count fresh
                Graph.repository.observeToday()
                    .map { it.steps to it.goal }
                    .distinctUntilChanged()
                    .collect { (steps, goal) ->
                        runCatching {
                            NotificationManagerCompat.from(this@StepTrackingService)
                                .notify(NOTIF_ID, buildNotification(steps, goal))
                        }
                    }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(steps: Long, goal: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StepTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val progress = if (goal > 0) ((steps * 100) / goal).toInt().coerceIn(0, 100) else 0

        // Android 16+ Live Updates: a progress-centric notification promoted to
        // an ongoing "live" chip in the status bar and lock screen.
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            val live = runCatching {
                buildLiveUpdateNotification(progress, steps, goal, openIntent, stopIntent)
            }.onFailure {
                android.util.Log.e("StepTracking", "live update build failed", it)
            }
            live.getOrNull()?.let { return it }
        }

        return NotificationCompat.Builder(this, Notifier.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_steps_today, Formatters.steps(steps)))
            .setContentText(getString(R.string.notif_goal_progress, progress, Formatters.steps(goal.toLong())))
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    @androidx.annotation.RequiresApi(36)
    private fun buildLiveUpdateNotification(
        progress: Int,
        steps: Long,
        goal: Int,
        openIntent: PendingIntent,
        stopIntent: PendingIntent
    ): Notification {
        val accent = androidx.core.content.ContextCompat.getColor(this, R.color.live_update_accent)

        // The tracker icon rides along the bar as the day fills up; the segment
        // spans the whole goal so progress reads as "% of goal".
        val style = Notification.ProgressStyle()
            .setStyledByProgress(false)
            .setProgress(progress)
            .setProgressSegments(
                listOf(Notification.ProgressStyle.Segment(100).setColor(accent))
            )
            .setProgressTrackerIcon(
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notification)
            )

        val builder = Notification.Builder(this, Notifier.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_steps_today, Formatters.steps(steps)))
            .setContentText(getString(R.string.notif_goal_progress, progress, Formatters.steps(goal.toLong())))
            .setStyle(style)
            // Text shown inside the status-bar chip
            .setShortCriticalText("$progress%")
            // Classic progress extras as well, so notification-listener bridges
            // (e.g. LiveBridge) that parse EXTRA_PROGRESS can render this too
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(accent)
            .setColorized(true)
            .setContentIntent(openIntent)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.stop), stopIntent).build()
            )
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        // Upgrade the ongoing progress notification into a Live Update: the
        // status-bar chip plus prominent placement on the lock screen and at the
        // top of the shade. Requires POST_PROMOTED_NOTIFICATIONS plus an ongoing,
        // progress-centric notification.
        if (promoteLiveUpdate) {
            builder.setFlag(Notification.FLAG_PROMOTED_ONGOING, true)
        }
        return builder.build()
    }

    companion object {
        const val ACTION_STOP = "com.vythera.stride.action.STOP_TRACKING"
        private const val NOTIF_ID = 2001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, StepTrackingService::class.java)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, StepTrackingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
