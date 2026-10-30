package com.example.syncedn.data.auth

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.syncedn.data.receiver.ScheduleAlarmReceiver

/**
 * Sticky background service that keeps Firestore real-time notification listeners
 * alive even when the user closes or swipes away the app from recents.
 */
class SyncedBackgroundSyncService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureWatchersActive()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureWatchersActive()
        return START_STICKY
    }

    private fun ensureWatchersActive() {
        try {
            val prefs = applicationContext.getSharedPreferences(SessionManager.PREFS_NAME, Context.MODE_PRIVATE)
            SyncedMessagingService.startRealtimeTeamWatchers(applicationContext, prefs)
            ScheduleAlarmReceiver.scheduleKeepAlive(applicationContext, 45_000L)
        } catch (t: Throwable) {
            Log.w(TAG, "ensureWatchersActive error: ${t.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            val prefs = applicationContext.getSharedPreferences(SessionManager.PREFS_NAME, Context.MODE_PRIVATE)
            SyncedMessagingService.startRealtimeTeamWatchers(applicationContext, prefs)
            ScheduleAlarmReceiver.scheduleKeepAlive(applicationContext, 3_000L)
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        try {
            ScheduleAlarmReceiver.scheduleKeepAlive(applicationContext, 5_000L)
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SyncedBgService"

        fun start(context: Context) {
            try {
                val appCtx = context.applicationContext ?: context
                appCtx.startService(Intent(appCtx, SyncedBackgroundSyncService::class.java))
            } catch (_: Throwable) {
                // If background start is restricted on Android 8+, ScheduleAlarmReceiver keep-alive handles wakeup
                ScheduleAlarmReceiver.scheduleKeepAlive(context, 5_000L)
            }
        }
    }
}
