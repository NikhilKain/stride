package com.vythera.stride.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vythera.stride.MainActivity
import com.vythera.stride.R
import com.vythera.stride.domain.AchievementDef
import com.vythera.stride.util.Formatters
import com.vythera.stride.util.achievementTitle

class Notifier(private val context: Context) {

    companion object {
        const val CHANNEL_GOAL = "goal_celebrations"
        const val CHANNEL_NUDGE = "movement_nudges"
        const val CHANNEL_TRACKING = "step_tracking"
    }

    fun ensureChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GOAL,
                context.getString(R.string.notif_channel_goal),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NUDGE,
                context.getString(R.string.notif_channel_nudge),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING,
                context.getString(R.string.notif_channel_tracking),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun goalReached(steps: Long) {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(context, CHANNEL_GOAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_goal_title))
            .setContentText(context.getString(R.string.notif_goal_text, Formatters.steps(steps)))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1001, n)
    }

    fun nudge(steps: Long, goal: Int) {
        if (!canNotify()) return
        val remaining = (goal - steps).coerceAtLeast(0)
        val n = NotificationCompat.Builder(context, CHANNEL_NUDGE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_nudge_title))
            .setContentText(context.getString(R.string.notif_nudge_text, Formatters.steps(remaining)))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1002, n)
    }

    fun achievementUnlocked(def: AchievementDef) {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(context, CHANNEL_GOAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_badge_title))
            .setContentText(context.getString(R.string.notif_badge_text, context.achievementTitle(def)))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1003, n)
    }
}
