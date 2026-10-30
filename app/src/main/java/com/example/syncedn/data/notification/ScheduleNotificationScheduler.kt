package com.example.syncedn.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.syncedn.data.receiver.ScheduleAlarmReceiver
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Handles scheduling exact and standby alarms using AlarmManager to fire outside-the-app
 * notifications when a Worship Service or Rehearsal starts.
 */
object ScheduleNotificationScheduler {
    private const val TAG = "ScheduleScheduler"

    /**
     * Schedules a system alarm for a single event (Service or Rehearsal).
     */
    fun scheduleEvent(
        context: Context,
        eventId: String,
        title: String,
        dateIso: String,
        startTime: String,
        venue: String,
        isRehearsal: Boolean
    ) {
        if (dateIso.isBlank() || startTime.isBlank()) return
        val targetMillis = parseDateTimeToEpoch(dateIso, startTime) ?: return
        val now = System.currentTimeMillis()
        if (targetMillis <= now) {
            // Event has already started or is in the past
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra("title", if (isRehearsal) "$title (Rehearsal Starting Now)" else "$title Starting Now")
            putExtra(
                "body",
                if (isRehearsal) "Rehearsal at $venue is starting now."
                else "Worship Service at $venue is starting now."
            )
            putExtra("eventId", eventId)
        }

        val suffix = if (isRehearsal) "_reh" else "_srv"
        val requestCode = (eventId + suffix).hashCode() and 0x7FFFFFFF
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent)
            }
            Log.d(TAG, "Scheduled alarm for $eventId (${if (isRehearsal) "rehearsal" else "service"}) at $dateIso $startTime ($targetMillis)")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to schedule alarm for $eventId: ${e.message}")
        }
    }

    /**
     * Schedules alarms for all published services and rehearsals in the given list.
     */
    fun scheduleAll(context: Context, services: List<JSONObject>) {
        for (srv in services) {
            val status = srv.optString("status")
            if (status != "Published") continue
            val id = srv.optString("id").ifBlank { "srv_${System.currentTimeMillis()}" }
            val title = srv.optString("title", "Sunday Worship Service")
            val date = srv.optString("date")
            val start = srv.optString("start")
            val venue = srv.optString("venue", "Main Sanctuary")

            if (date.isNotBlank() && start.isNotBlank()) {
                scheduleEvent(context, id, title, date, start, venue, isRehearsal = false)
            }

            val rehObj = srv.optJSONObject("rehearsal")
            val rDate = rehObj?.optString("date").orEmpty().ifBlank { srv.optString("rehearsalDate").orEmpty() }
            val rStart = rehObj?.optString("start").orEmpty().ifBlank { srv.optString("rehearsalStart").orEmpty() }
            val rVenue = rehObj?.optString("venue").orEmpty()
                .ifBlank { srv.optString("rehearsalVenue").orEmpty() }
                .ifBlank { venue }

            if (rDate.isNotBlank() && rStart.isNotBlank()) {
                scheduleEvent(context, id, title, rDate, rStart, rVenue, isRehearsal = true)
            }
        }
    }

    /**
     * Parses ISO date "yyyy-MM-dd" and time string ("HH:mm", "h:mm a") in Asia/Manila timezone to epoch millis.
     */
    fun parseDateTimeToEpoch(dateIso: String, timeStr: String): Long? {
        val cleanTime = timeStr.trim().split("–")[0].split("-")[0].trim()
        val pht = TimeZone.getTimeZone("Asia/Manila")
        val formats = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd H:mm",
            "yyyy-MM-dd hh:mm a",
            "yyyy-MM-dd h:mm a",
            "yyyy-MM-dd hh:mma",
            "yyyy-MM-dd h:mma"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply { timeZone = pht }
                val parsed = sdf.parse("$dateIso $cleanTime")
                if (parsed != null) return parsed.time
            } catch (_: Throwable) {}
        }
        return null
    }
}
