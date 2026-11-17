package com.sasayaki.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sasayaki.MainActivity
import com.sasayaki.R

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "sasayaki_service"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.sasayaki.ACTION_STOP"
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.bubble_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.bubble_notification_channel_description)
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun buildReadyNotification(): Notification = build(R.string.bubble_notification_title, R.string.bubble_notification_text)

    // buildListeningNotification replaces the ready one while the microphone is on, so the
    // shade says plainly that Sasayaki is recording.
    fun buildListeningNotification(): Notification =
        build(R.string.bubble_notification_listening_title, R.string.bubble_notification_listening_text)

    private fun build(@StringRes title: Int, @StringRes text: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_STOP).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.action_stop), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
