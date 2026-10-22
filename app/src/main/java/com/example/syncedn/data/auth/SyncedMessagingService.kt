package com.example.syncedn.data.auth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.syncedn.MainActivity
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Handles ministry and team notification delivery, channel registration,
 * preference checks (general notifications toggle and team chat unmute toggle),
 * and automatic real-time Firestore background listeners for Services, Announcements,
 * Substitutions, and Chat Messages.
 */
object SyncedMessagingService {
    private const val TAG = "SyncedNotif"
    const val CHANNEL_ID = "syncedn_ministry_alerts_high"
    private const val CHANNEL_NAME = "Synced N Ministry Alerts"

    private var activeTeamCode: String = ""
    private var servicesListener: ListenerRegistration? = null
    private var announcementsListener: ListenerRegistration? = null
    private var substitutionsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

    private data class ServiceSnapshotMeta(
        val status: String,
        val date: String,
        val start: String,
        val end: String,
        val venue: String,
        val title: String
    )

    private val knownServices = mutableMapOf<String, ServiceSnapshotMeta>()
    private val knownAnnouncements = mutableMapOf<String, Long>()
    private val knownSubstitutions = mutableMapOf<String, String>()
    private val knownMessages = mutableSetOf<String>()

    fun isGeneralNotificationEnabled(preferences: SharedPreferences?): Boolean {
        return preferences?.getBoolean("notifications", true) ?: true
    }

    fun isTeamChatNotificationEnabled(preferences: SharedPreferences?): Boolean {
        if (!isGeneralNotificationEnabled(preferences)) return false
        return preferences?.getBoolean("teamUnmuted", true) ?: true
    }

    fun ensureChannel(context: Context): NotificationManager? {
        return try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val existing = manager.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Heads-up notifications for worship schedule changes, urgent announcements, substitutions, and team chat"
                        enableVibration(true)
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
            val appCtx = context.applicationContext ?: context
            val manager = ensureChannel(appCtx) ?: return

            val launchIntent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(appCtx, 0, launchIntent, pendingFlags)

            val notification = NotificationCompat.Builder(appCtx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            val notifId = ((title + body + System.currentTimeMillis() / 1000).hashCode() and 0x7FFFFFFF)
            manager.notify(notifId, notification)
        } catch (_: Throwable) {
            // Safely ignore if POST_NOTIFICATIONS permission is not granted on Android 13+
        }
    }

    /**
     * Starts automatic real-time Firestore listeners for the user's active team so that
     * sudden service changes (date, time, venue), new announcements, substitution status updates,
     * and team chat messages automatically trigger heads-up system notifications without
     * requiring manual broadcasts from the Firebase Console.
     */
    fun startRealtimeTeamWatchers(
        context: Context,
        preferences: SharedPreferences
    ) {
        val code = preferences.getString("teamCode", "").orEmpty().trim().uppercase()
        if (code.isBlank()) {
            stopRealtimeTeamWatchers()
            return
        }
        if (code == activeTeamCode && servicesListener != null) {
            return
        }

        stopRealtimeTeamWatchers()
        activeTeamCode = code
        val appCtx = context.applicationContext ?: context
        val db = try {
            FirebaseFirestore.getInstance()
        } catch (_: Throwable) {
            return
        }

        // 1. Watch Worship Services for new publications or sudden Date / Time / Venue changes
        var initialServicesLoaded = false
        servicesListener = db.collection("teams").document(code).collection("services")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val currentUid = preferences.getString("userId", "").orEmpty()
                if (!initialServicesLoaded) {
                    for (doc in snapshot.documents) {
                        val status = doc.getString("status").orEmpty()
                        val date = doc.getString("date").orEmpty()
                        val start = doc.getString("start").orEmpty()
                        val end = doc.getString("end").orEmpty()
                        val venue = doc.getString("venue").orEmpty()
                        val title = doc.getString("title").orEmpty().ifBlank { "Worship Service" }
                        knownServices[doc.id] = ServiceSnapshotMeta(status, date, start, end, venue, title)
                    }
                    initialServicesLoaded = true
                    return@addSnapshotListener
                }

                for (change in snapshot.documentChanges) {
                    val doc = change.document
                    val id = doc.id
                    val status = doc.getString("status").orEmpty()
                    val date = doc.getString("date").orEmpty()
                    val start = doc.getString("start").orEmpty()
                    val end = doc.getString("end").orEmpty()
                    val venue = doc.getString("venue").orEmpty()
                    val title = doc.getString("title").orEmpty().ifBlank { "Worship Service" }
                    val createdBy = doc.getString("createdBy").orEmpty()
                    val newMeta = ServiceSnapshotMeta(status, date, start, end, venue, title)
                    val prevMeta = knownServices[id]
                    knownServices[id] = newMeta

                    if (status.equals("Published", ignoreCase = true)) {
                        if (prevMeta == null || !prevMeta.status.equals("Published", ignoreCase = true)) {
                            if (createdBy != currentUid) {
                                dispatchSystemNotification(
                                    appCtx,
                                    preferences,
                                    "📅 New Service Scheduled",
                                    "$title on $date ($start - $end) at $venue"
                                )
                            }
                        } else if (
                            prevMeta.date != date ||
                            prevMeta.start != start ||
                            prevMeta.end != end ||
                            prevMeta.venue != venue
                        ) {
                            val changes = mutableListOf<String>()
                            if (prevMeta.date != date) changes.add("Date: $date")
                            if (prevMeta.start != start || prevMeta.end != end) changes.add("Time: $start - $end")
                            if (prevMeta.venue != venue) changes.add("Venue: $venue")
                            dispatchSystemNotification(
                                appCtx,
                                preferences,
                                "⚠️ Sudden Change in $title",
                                "Updated schedule — ${changes.joinToString(", ")}"
                            )
                        }
                    }
                }
            }

        // 2. Watch Announcements & Urgent Alerts
        var initialAnnouncementsLoaded = false
        announcementsListener = db.collection("teams").document(code).collection("announcements")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val currentUid = preferences.getString("userId", "").orEmpty()
                if (!initialAnnouncementsLoaded) {
                    for (doc in snapshot.documents) {
                        val updated = (doc.get("updatedAt") as? Number)?.toLong() ?: 0L
                        knownAnnouncements[doc.id] = updated
                    }
                    initialAnnouncementsLoaded = true
                    return@addSnapshotListener
                }

                for (change in snapshot.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                        val doc = change.document
                        val id = doc.id
                        val updated = (doc.get("updatedAt") as? Number)?.toLong() ?: System.currentTimeMillis()
                        val prevUpdated = knownAnnouncements[id]
                        knownAnnouncements[id] = updated

                        if (prevUpdated == null || updated > prevUpdated) {
                            val createdBy = doc.getString("createdBy").orEmpty()
                            if (createdBy != currentUid) {
                                val title = doc.getString("title").orEmpty().ifBlank { "Team Announcement" }
                                val message = doc.getString("message").orEmpty()
                                val urgent = doc.getBoolean("urgent") ?: false
                                dispatchSystemNotification(
                                    appCtx,
                                    preferences,
                                    if (urgent) "🚨 URGENT: $title" else "📢 $title",
                                    message
                                )
                            }
                        }
                    }
                }
            }

        // 3. Watch Substitutions (New requests & Accepted/Declined status changes)
        var initialSubstitutionsLoaded = false
        substitutionsListener = db.collection("teams").document(code).collection("substitutions")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val currentUid = preferences.getString("userId", "").orEmpty()
                if (!initialSubstitutionsLoaded) {
                    for (doc in snapshot.documents) {
                        knownSubstitutions[doc.id] = doc.getString("status").orEmpty()
                    }
                    initialSubstitutionsLoaded = true
                    return@addSnapshotListener
                }

                for (change in snapshot.documentChanges) {
                    val doc = change.document
                    val id = doc.id
                    val status = doc.getString("status").orEmpty()
                    val requesterUid = doc.getString("requesterUid").orEmpty()
                    val requesterName = doc.getString("requesterName").orEmpty().ifBlank { "A teammate" }
                    val substituteUid = doc.getString("substituteUid").orEmpty()
                    val substituteName = doc.getString("substituteName").orEmpty().ifBlank { "A teammate" }
                    val role = doc.getString("role").orEmpty()
                    val serviceTitle = doc.getString("serviceTitle").orEmpty().ifBlank { "Worship Service" }

                    val prevStatus = knownSubstitutions[id]
                    knownSubstitutions[id] = status

                    if (prevStatus == null && status.equals("Pending", ignoreCase = true)) {
                        // Notify the proposed substitute or team leader
                        val userRole = preferences.getString("role", "Member").orEmpty()
                        if (substituteUid == currentUid || (userRole.equals("Leader", true) && requesterUid != currentUid)) {
                            dispatchSystemNotification(
                                appCtx,
                                preferences,
                                "🔄 New Substitution Request",
                                "$requesterName requested $substituteName to cover $role for $serviceTitle."
                            )
                        }
                    } else if (prevStatus != null && !prevStatus.equals(status, ignoreCase = true)) {
                        if (status.equals("Accepted", ignoreCase = true)) {
                            dispatchSystemNotification(
                                appCtx,
                                preferences,
                                "✅ Substitution Accepted",
                                "$substituteName accepted the substitution for $role in $serviceTitle."
                            )
                        } else if (status.equals("Declined", ignoreCase = true)) {
                            dispatchSystemNotification(
                                appCtx,
                                preferences,
                                "❌ Substitution Declined",
                                "$substituteName declined the substitution for $role in $serviceTitle."
                            )
                        }
                    }
                }
            }

        // 4. Watch Team Chat Messages
        var initialMessagesLoaded = false
        messagesListener = db.collection("teams").document(code).collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val currentUid = preferences.getString("userId", "").orEmpty()
                if (!initialMessagesLoaded) {
                    for (doc in snapshot.documents) {
                        knownMessages.add(doc.id)
                    }
                    initialMessagesLoaded = true
                    return@addSnapshotListener
                }

                for (change in snapshot.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        if (knownMessages.add(doc.id)) {
                            val senderUid = doc.getString("senderUid").orEmpty()
                            if (senderUid.isNotBlank() && senderUid != currentUid) {
                                val senderName = doc.getString("senderName").orEmpty().ifBlank { "Team Member" }
                                val text = doc.getString("text").orEmpty().ifBlank { "📷 Sent an image" }
                                dispatchSystemNotification(
                                    appCtx,
                                    preferences,
                                    "💬 $senderName",
                                    text,
                                    isChatMessage = true
                                )
                            }
                        }
                    }
                }
            }

        Log.d(TAG, "Started real-time notification watchers for team $code")
    }

    fun stopRealtimeTeamWatchers() {
        servicesListener?.remove()
        announcementsListener?.remove()
        substitutionsListener?.remove()
        messagesListener?.remove()
        servicesListener = null
        announcementsListener = null
        substitutionsListener = null
        messagesListener = null
        activeTeamCode = ""
        knownServices.clear()
        knownAnnouncements.clear()
        knownSubstitutions.clear()
        knownMessages.clear()
    }
}
