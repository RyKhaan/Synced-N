package com.example.syncedn.data.auth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Handles ministry and team notification delivery, channel registration, and
 * preference checks (general notifications toggle and team chat unmute toggle).
 */
object SyncedMessagingService {
    const val CHANNEL_ID = "syncedn_ministry_channel"
    private const val CHANNEL_NAME = "Synced N Ministry Alerts"

    fun isGeneralNotificationEnabled(preferences: SharedPreferences?): Boolean {
        return preferences?.getBoolean("notifications", true) ?: true
    }

    fun isTeamChatNotificationEnabled(preferences: SharedPreferences?): Boolean {
        if (!isGeneralNotificationEnabled(preferences)) return false
        return preferences?.getBoolean("teamUnmuted", true) ?: true
    }

    private fun ensureChannel(context: Context): NotificationManager? {
        return try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val existing = manager.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Notifications for worship services, urgent announcements, and team chat"
                    }
                    manager.createNotificationChannel(channel)
                }
            }
            manager
        } catch (_: Throwable) {
            null
        }
    }

    fun dispatchSystemNotification(
        context: Context,
        preferences: SharedPreferences?,
        title: String,
        body: String,
        isChatMessage: Boolean = false
    ) {
        val allowed = if (isChatMessage) {
            isTeamChatNotificationEnabled(preferences)
        } else {
            isGeneralNotificationEnabled(preferences)
        }
        if (!allowed) return

        try {
            val manager = ensureChannel(context) ?: return
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            val notifId = ((title + body).hashCode() and 0x7FFFFFFF)
            manager.notify(notifId, notification)
        } catch (_: Throwable) {
            // Safely ignore if POST_NOTIFICATIONS permission is not granted on Android 13+
        }
    }
}
