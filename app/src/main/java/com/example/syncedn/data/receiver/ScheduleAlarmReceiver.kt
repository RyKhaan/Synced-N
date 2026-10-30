package com.example.syncedn.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.syncedn.data.auth.SyncedMessagingService

/**
 * BroadcastReceiver triggered by AlarmManager when a scheduled Service or Rehearsal starts.
 * Dispatches an outside-the-app heads-up system notification to alert team members in real-time.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val title = intent.getStringExtra("title") ?: "Upcoming Schedule"
        val body = intent.getStringExtra("body") ?: "A schedule is starting now."
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        Log.d("ScheduleAlarmReceiver", "Triggering schedule alert notification: $title - $body")
        try {
            SyncedMessagingService.dispatchSystemNotification(
                context = context,
                preferences = prefs,
                title = title,
                body = body,
                isChatMessage = false
            )
        } catch (t: Throwable) {
            Log.e("ScheduleAlarmReceiver", "Failed to dispatch notification: ${t.message}", t)
        }
    }
}
