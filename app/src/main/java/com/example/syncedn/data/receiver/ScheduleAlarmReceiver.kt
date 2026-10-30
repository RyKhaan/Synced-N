package com.example.syncedn.data.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.syncedn.data.auth.SessionManager
import com.example.syncedn.data.auth.SyncedMessagingService

/**
 * BroadcastReceiver triggered by AlarmManager when a scheduled Service or Rehearsal starts,
 * or periodically in the background to keep real-time Firestore notification watchers active
 * even when the app is closed/off.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appCtx = context.applicationContext ?: context
        val prefs = appCtx.getSharedPreferences(SessionManager.PREFS_NAME, Context.MODE_PRIVATE)

        // Always ensure real-time Firestore watchers are active when woken up in background
        try {
            SyncedMessagingService.startRealtimeTeamWatchers(appCtx, prefs)
            scheduleKeepAlive(appCtx, 45_000L)
        } catch (_: Throwable) {}

        if (intent == null) return
        val action = intent.action.orEmpty()
        if (action == ACTION_KEEP_ALIVE_SYNC ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val title = intent.getStringExtra("title") ?: return
        val body = intent.getStringExtra("body") ?: "A schedule is starting now."
        Log.d("ScheduleAlarmReceiver", "Triggering schedule alert notification: $title - $body")
        try {
            SyncedMessagingService.dispatchSystemNotification(
                context = appCtx,
                preferences = prefs,
                title = title,
                body = body,
                isChatMessage = false,
                targetPage = "schedule"
            )
        } catch (t: Throwable) {
            Log.e("ScheduleAlarmReceiver", "Failed to dispatch notification: ${t.message}", t)
        }
    }

    companion object {
        const val ACTION_KEEP_ALIVE_SYNC = "com.example.syncedn.ACTION_KEEP_ALIVE_SYNC"
        private const val KEEP_ALIVE_REQ_CODE = 990421

        fun scheduleKeepAlive(context: Context, delayMs: Long = 45_000L) {
            try {
                val appCtx = context.applicationContext ?: context
                val prefs = appCtx.getSharedPreferences(SessionManager.PREFS_NAME, Context.MODE_PRIVATE)
                val teamCode = prefs.getString("teamCode", "").orEmpty()
                val userId = prefs.getString("userId", "").orEmpty()
                if (teamCode.isBlank() && userId.isBlank()) return

                val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(appCtx, ScheduleAlarmReceiver::class.java).apply {
                    action = ACTION_KEEP_ALIVE_SYNC
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pi = PendingIntent.getBroadcast(appCtx, KEEP_ALIVE_REQ_CODE, intent, flags)
                val triggerAt = System.currentTimeMillis() + delayMs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (_: Throwable) {}
        }
    }
}
