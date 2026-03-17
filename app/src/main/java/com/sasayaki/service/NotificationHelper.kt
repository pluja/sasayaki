package com.sasayaki.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
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
            description = "Keeps dictation service running"
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun buildForegroundNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_STOP).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.bubble_notification_title))
            .setContentText(context.getString(R.string.bubble_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.action_stop), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
