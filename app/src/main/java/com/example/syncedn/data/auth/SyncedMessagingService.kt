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
import com.example.syncedn.data.receiver.ScheduleAlarmReceiver
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles ministry and team notification delivery, channel registration,
 * preference checks (general notifications toggle and team chat unmute toggle),
 * deep-link routing on notification click, and automatic real-time Firestore
 * background listeners for Services, Announcements, Substitutions, Chat Messages,
 * and Team Member Join/Leave events (including when the app is closed/off).
 */
object SyncedMessagingService {
    private const val TAG = "SyncedNotif"
    const val CHANNEL_ID = "syncedn_ministry_alerts_high"
    private const val CHANNEL_NAME = "Synced N Ministry Alerts"

    private var activeTeamCode: String = ""
    private var activeRejoinTeamCode: String = ""
    private var servicesListener: ListenerRegistration? = null
    private var announcementsListener: ListenerRegistration? = null
    private var substitutionsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var rosterWatcherListener: ListenerRegistration? = null
    private var teamDocWatcherListener: ListenerRegistration? = null
    private var rejoinRequestsListener: ListenerRegistration? = null

    @Volatile
    var onRejoinApprovedInApp: ((String) -> Unit)? = null

    @Volatile
    var onLeaderRejoinRequestInApp: ((String, String) -> Unit)? = null

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
    private val knownTeamMembers = mutableMapOf<String, String>()
    private val knownRejoinStatuses = mutableMapOf<String, String>()

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
                        description = "Heads-up notifications for worship schedule changes, urgent announcements, substitutions, team roster updates, and team chat"
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
        isChatMessage: Boolean = false,
        targetPage: String = "home",
        targetTeamTab: String = ""
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

            val effectivePage = when {
                targetPage.isNotBlank() && targetPage != "home" -> targetPage
                isChatMessage -> "teams"
                else -> "home"
            }
            val effectiveTeamTab = when {
                targetTeamTab.isNotBlank() -> targetTeamTab
                isChatMessage -> "Team Chat"
                else -> ""
            }

            val launchIntent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("startPage", effectivePage)
                if (effectiveTeamTab.isNotBlank()) {
                    putExtra("teamTab", effectiveTeamTab)
                }
                putExtra("fromNotification", true)
            }
            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val requestCode = ((title + body + effectivePage + effectiveTeamTab).hashCode() and 0x7FFFFFFF)
            val pendingIntent = PendingIntent.getActivity(appCtx, requestCode, launchIntent, pendingFlags)

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

    private fun loadPersistedServices(code: String, preferences: SharedPreferences) {
        knownServices.clear()
        val raw = preferences.getString("notif_known_services_$code", null) ?: return
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val obj = root.optJSONObject(id) ?: continue
                knownServices[id] = ServiceSnapshotMeta(
                    status = obj.optString("status"),
                    date = obj.optString("date"),
                    start = obj.optString("start"),
                    end = obj.optString("end"),
                    venue = obj.optString("venue"),
                    title = obj.optString("title", "Worship Service")
                )
            }
        } catch (_: Throwable) {}
    }

    private fun savePersistedServices(code: String, preferences: SharedPreferences) {
        try {
            val root = JSONObject()
            for ((id, meta) in knownServices) {
                val obj = JSONObject()
                obj.put("status", meta.status)
                obj.put("date", meta.date)
                obj.put("start", meta.start)
                obj.put("end", meta.end)
                obj.put("venue", meta.venue)
                obj.put("title", meta.title)
                root.put(id, obj)
            }
            preferences.edit()
                .putString("notif_known_services_$code", root.toString())
                .putBoolean("notif_services_init_$code", true)
                .apply()
        } catch (_: Throwable) {}
    }

    private fun loadPersistedAnnouncements(code: String, preferences: SharedPreferences) {
        knownAnnouncements.clear()
        val raw = preferences.getString("notif_known_announcements_$code", null) ?: return
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                knownAnnouncements[id] = root.optLong(id, 0L)
            }
        } catch (_: Throwable) {}
    }

    private fun savePersistedAnnouncements(code: String, preferences: SharedPreferences) {
        try {
            val root = JSONObject()
            for ((id, ts) in knownAnnouncements) {
                root.put(id, ts)
            }
            preferences.edit()
                .putString("notif_known_announcements_$code", root.toString())
                .putBoolean("notif_announcements_init_$code", true)
                .apply()
        } catch (_: Throwable) {}
    }

    private fun loadPersistedSubstitutions(code: String, preferences: SharedPreferences) {
        knownSubstitutions.clear()
        val raw = preferences.getString("notif_known_substitutions_$code", null) ?: return
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                knownSubstitutions[id] = root.optString(id)
            }
        } catch (_: Throwable) {}
    }

    private fun savePersistedSubstitutions(code: String, preferences: SharedPreferences) {
        try {
            val root = JSONObject()
            for ((id, st) in knownSubstitutions) {
                root.put(id, st)
            }
            preferences.edit()
                .putString("notif_known_substitutions_$code", root.toString())
                .putBoolean("notif_substitutions_init_$code", true)
                .apply()
        } catch (_: Throwable) {}
    }

    private fun loadPersistedMessages(code: String, preferences: SharedPreferences) {
        knownMessages.clear()
        val set = preferences.getStringSet("notif_known_messages_$code", null) ?: return
        knownMessages.addAll(set)
    }

    private fun savePersistedMessages(code: String, preferences: SharedPreferences) {
        try {
            preferences.edit()
                .putStringSet("notif_known_messages_$code", knownMessages.toSet())
                .putBoolean("notif_messages_init_$code", true)
                .apply()
        } catch (_: Throwable) {}
    }

    private fun loadPersistedMembers(code: String, preferences: SharedPreferences) {
        knownTeamMembers.clear()
        val raw = preferences.getString("notif_known_members_$code", null) ?: return
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                knownTeamMembers[k] = root.optString(k)
            }
        } catch (_: Throwable) {}
    }

    private fun savePersistedMembers(code: String, preferences: SharedPreferences) {
        try {
            val root = JSONObject()
            for ((k, name) in knownTeamMembers) {
                root.put(k, name)
            }
            preferences.edit()
                .putString("notif_known_members_$code", root.toString())
                .putBoolean("notif_members_init_$code", true)
                .apply()
        } catch (_: Throwable) {}
    }

    /**
     * Starts automatic real-time Firestore listeners for the user's active team so that
     * sudden service changes (date, time, venue), new announcements, substitution status updates,
     * team chat messages, and member join/leave events automatically trigger heads-up system
     * notifications even when the app is closed/backgrounded.
     */
    fun startRealtimeTeamWatchers(
        context: Context,
        preferences: SharedPreferences
    ) {
        val code = preferences.getString("teamCode", "").orEmpty().trim().uppercase()
        val currentEmail = preferences.getString("email", "").orEmpty().trim().lowercase()
        val currentUid = preferences.getString("userId", "").orEmpty().trim()
        val pendingRejoinCode = preferences.getString("pending_rejoin_team_code", "").orEmpty()
            .ifBlank { preferences.getString("removedTeamCode", "").orEmpty() }
            .ifBlank { if (currentEmail.isNotBlank()) preferences.getString("email_removed_team_code_$currentEmail", "").orEmpty() else "" }
            .ifBlank { if (currentUid.isNotBlank()) preferences.getString("user_removed_team_code_$currentUid", "").orEmpty() else "" }
            .trim()
            .uppercase()
        val rejoinWatchCode = code.ifBlank { pendingRejoinCode }

        val appCtx = context.applicationContext ?: context
        if (code.isNotBlank() || rejoinWatchCode.isNotBlank()) {
            SyncedBackgroundSyncService.start(appCtx)
            ScheduleAlarmReceiver.scheduleKeepAlive(appCtx, 45_000L)
        }

        val db = try {
            FirebaseFirestore.getInstance()
        } catch (_: Throwable) {
            return
        }

        if (rejoinWatchCode.isNotBlank() && (rejoinWatchCode != activeRejoinTeamCode || rejoinRequestsListener == null)) {
            rejoinRequestsListener?.remove()
            knownRejoinStatuses.clear()
            activeRejoinTeamCode = rejoinWatchCode
            var initialRejoinsLoaded = false
            rejoinRequestsListener = db.collection("teams").document(rejoinWatchCode).collection("rejoin_requests")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val myUid = preferences.getString("userId", "").orEmpty().trim()
                    val myEmail = preferences.getString("email", "").orEmpty().trim().lowercase()
                    val myRole = preferences.getString("role", "Member").orEmpty()
                    val isLeader = myRole.equals("Leader", ignoreCase = true) || myEmail == "ryanlloydgenturo@gmail.com"
                    val notifiedApproved = preferences.getStringSet("notified_approved_rejoins", emptySet())?.toMutableSet() ?: mutableSetOf()

                    if (!initialRejoinsLoaded) {
                        for (doc in snapshot.documents) {
                            val st = doc.getString("status").orEmpty()
                            val reqUid = doc.getString("userUid").orEmpty().trim()
                            val reqEmail = doc.getString("userEmail").orEmpty().trim().lowercase()
                            knownRejoinStatuses[doc.id] = st
                            val currentActiveCode = preferences.getString("teamCode", "").orEmpty().trim().uppercase()
                            val isAlreadyInTeam = currentActiveCode.isNotBlank()
                            val isMine = (myUid.isNotBlank() && reqUid == myUid) || (myEmail.isNotBlank() && reqEmail == myEmail)
                            if (!isAlreadyInTeam && isMine && st.equals("Approved", ignoreCase = true) && !notifiedApproved.contains(doc.id)) {
                                notifiedApproved.add(doc.id)
                                preferences.edit()
                                    .putStringSet("notified_approved_rejoins", notifiedApproved)
                                    .putBoolean("removedByLeader", false)
                                    .remove("removedTeamCode")
                                    .apply()
                                if (myEmail.isNotBlank()) {
                                    preferences.edit()
                                        .putBoolean("email_removed_by_leader_$myEmail", false)
                                        .remove("email_removed_team_code_$myEmail")
                                        .apply()
                                }
                                if (myUid.isNotBlank()) {
                                    preferences.edit()
                                        .putBoolean("user_removed_by_leader_$myUid", false)
                                        .remove("user_removed_team_code_$myUid")
                                        .apply()
                                }
                                dispatchSystemNotification(
                                    appCtx,
                                    preferences,
                                    "✅ Re-join Request Approved!",
                                    "Your request to re-join has been approved by the Team Leader! You can now enter the team code ($rejoinWatchCode) to join.",
                                    targetPage = "joinTeam"
                                )
                                onRejoinApprovedInApp?.invoke(rejoinWatchCode)
                            }
                        }
                        initialRejoinsLoaded = true
                        return@addSnapshotListener
                    }

                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val id = doc.id
                        val status = doc.getString("status").orEmpty()
                        val reqUid = doc.getString("userUid").orEmpty().trim()
                        val reqEmail = doc.getString("userEmail").orEmpty().trim().lowercase()
                        val reqName = doc.getString("userName").orEmpty().ifBlank { reqEmail }.ifBlank { "A member" }
                        val prevStatus = knownRejoinStatuses[id]
                        knownRejoinStatuses[id] = status

                        val currentActiveCode = preferences.getString("teamCode", "").orEmpty().trim().uppercase()
                        val isAlreadyInTeam = currentActiveCode.isNotBlank()
                        val isMine = (myUid.isNotBlank() && reqUid == myUid) || (myEmail.isNotBlank() && reqEmail == myEmail)

                        if (isLeader && !isMine && status.equals("Pending", ignoreCase = true) && !prevStatus.equals("Pending", ignoreCase = true)) {
                            dispatchSystemNotification(
                                appCtx,
                                preferences,
                                "🙋 Member Re-join Request",
                                "$reqName requested approval to re-join your team ($rejoinWatchCode).",
                                targetPage = "notifications"
                            )
                            onLeaderRejoinRequestInApp?.invoke(reqName, rejoinWatchCode)
                        } else if (!isAlreadyInTeam && isMine && status.equals("Approved", ignoreCase = true) && !prevStatus.equals("Approved", ignoreCase = true)) {
                            notifiedApproved.add(id)
                            preferences.edit()
                                .putStringSet("notified_approved_rejoins", notifiedApproved)
                                .putBoolean("removedByLeader", false)
                                .remove("removedTeamCode")
                                .apply()
                            if (myEmail.isNotBlank()) {
                                preferences.edit()
                                    .putBoolean("email_removed_by_leader_$myEmail", false)
                                    .remove("email_removed_team_code_$myEmail")
                                    .apply()
                            }
                            if (myUid.isNotBlank()) {
                                preferences.edit()
                                    .putBoolean("user_removed_by_leader_$myUid", false)
                                    .remove("user_removed_team_code_$myUid")
                                    .apply()
                            }
                            dispatchSystemNotification(
                                appCtx,
                                preferences,
                                "✅ Re-join Request Approved!",
                                "Your request to re-join has been approved by the Team Leader! You can now enter the team code ($rejoinWatchCode) to join.",
                                targetPage = "joinTeam"
                            )
                            onRejoinApprovedInApp?.invoke(rejoinWatchCode)
                        }
                    }
                }
        }

        if (code.isBlank()) {
            stopTeamCollectionWatchersOnly()
            return
        }
        if (code == activeTeamCode && servicesListener != null && rosterWatcherListener != null) {
            return
        }

        stopTeamCollectionWatchersOnly()
        activeTeamCode = code

        // 1. Watch Worship Services for new publications or sudden Date / Time / Venue changes
        loadPersistedServices(code, preferences)
        var initialServicesLoaded = preferences.getBoolean("notif_services_init_$code", false)
        servicesListener = db.collection("teams").document(code).collection("services")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val myUid = preferences.getString("userId", "").orEmpty()
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
                    savePersistedServices(code, preferences)
                    return@addSnapshotListener
                }

                var changedAny = false
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
                    if (prevMeta != newMeta) {
                        knownServices[id] = newMeta
                        changedAny = true
                    }

                    if (status.equals("Published", ignoreCase = true)) {
                        if (prevMeta == null || !prevMeta.status.equals("Published", ignoreCase = true)) {
                            if (createdBy != myUid) {
                                dispatchSystemNotification(
                                    appCtx,
                                    preferences,
                                    "📅 New Service Scheduled",
                                    "$title on $date ($start - $end) at $venue",
                                    targetPage = "schedule"
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
                                "Updated schedule — ${changes.joinToString(", ")}",
                                targetPage = "schedule"
                            )
                        }
                    }
                }
                if (changedAny) {
                    savePersistedServices(code, preferences)
                }
            }

        // 2. Watch Announcements & Urgent Alerts
        loadPersistedAnnouncements(code, preferences)
        var initialAnnouncementsLoaded = preferences.getBoolean("notif_announcements_init_$code", false)
        announcementsListener = db.collection("teams").document(code).collection("announcements")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val myUid = preferences.getString("userId", "").orEmpty()
                if (!initialAnnouncementsLoaded) {
                    for (doc in snapshot.documents) {
                        val updated = (doc.get("updatedAt") as? Number)?.toLong() ?: 0L
                        knownAnnouncements[doc.id] = updated
                    }
                    initialAnnouncementsLoaded = true
                    savePersistedAnnouncements(code, preferences)
                    return@addSnapshotListener
                }

                var changedAny = false
                for (change in snapshot.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                        val doc = change.document
                        val id = doc.id
                        val updated = (doc.get("updatedAt") as? Number)?.toLong() ?: System.currentTimeMillis()
                        val prevUpdated = knownAnnouncements[id]
                        knownAnnouncements[id] = updated
                        changedAny = true

                        if (prevUpdated == null || updated > prevUpdated) {
                            val createdBy = doc.getString("createdBy").orEmpty()
                            if (createdBy != myUid) {
                                val title = doc.getString("title").orEmpty().ifBlank { "Team Announcement" }
                                val message = doc.getString("message").orEmpty()
                                val urgent = doc.getBoolean("urgent") ?: false
                                dispatchSystemNotification(
                                    appCtx,
                                    preferences,
                                    if (urgent) "🚨 URGENT: $title" else "📢 $title",
                                    message,
                                    targetPage = "announcements"
                                )
                            }
                        }
                    }
                }
                if (changedAny) {
                    savePersistedAnnouncements(code, preferences)
                }
            }

        // 3. Watch Substitutions (New requests & Accepted/Declined status changes)
        loadPersistedSubstitutions(code, preferences)
        var initialSubstitutionsLoaded = preferences.getBoolean("notif_substitutions_init_$code", false)
        substitutionsListener = db.collection("teams").document(code).collection("substitutions")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val myUid = preferences.getString("userId", "").orEmpty()
                if (!initialSubstitutionsLoaded) {
                    for (doc in snapshot.documents) {
                        knownSubstitutions[doc.id] = doc.getString("status").orEmpty()
                    }
                    initialSubstitutionsLoaded = true
                    savePersistedSubstitutions(code, preferences)
                    return@addSnapshotListener
                }

                var changedAny = false
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
                    if (prevStatus != status) {
                        knownSubstitutions[id] = status
                        changedAny = true
                    }

                    if (prevStatus == null && status.equals("Pending", ignoreCase = true)) {
                        val userRole = preferences.getString("role", "Member").orEmpty()
                        if (substituteUid == myUid || (userRole.equals("Leader", true) && requesterUid != myUid)) {
                            dispatchSystemNotification(
                                appCtx,
                                preferences,
                                "🔄 New Substitution Request",
                                "$requesterName requested $substituteName to cover $role for $serviceTitle.",
                                targetPage = "notifications"
                            )
                        }
                    } else if (prevStatus != null && !prevStatus.equals(status, ignoreCase = true)) {
                        val userRole = preferences.getString("role", "Member").orEmpty()
                        if (status.equals("Substitute Accepted", ignoreCase = true)) {
                            if (userRole.equals("Leader", true) || requesterUid == myUid) {
                                dispatchSystemNotification(
                                    appCtx,
                                    preferences,
                                    "⏳ Substitution Awaiting Approval",
                                    "$substituteName accepted covering $role in $serviceTitle (awaiting Leader approval).",
                                    targetPage = "notifications"
                                )
                            }
                        } else if (status.equals("Accepted", ignoreCase = true) || status.equals("Approved", ignoreCase = true)) {
                            dispatchSystemNotification(
                                appCtx,
                                preferences,
                                "✅ Substitution Approved",
                                "Substitution for $role in $serviceTitle ($substituteName) has been approved!",
                                targetPage = "notifications"
                            )
                        } else if (status.equals("Declined", ignoreCase = true)) {
                            dispatchSystemNotification(
                                appCtx,
                                preferences,
                                "❌ Substitution Declined",
                                "$substituteName declined the substitution for $role in $serviceTitle.",
                                targetPage = "notifications"
                            )
                        }
                    }
                }
                if (changedAny) {
                    savePersistedSubstitutions(code, preferences)
                }
            }

        // 4. Watch Team Chat Messages
        loadPersistedMessages(code, preferences)
        var initialMessagesLoaded = preferences.getBoolean("notif_messages_init_$code", false)
        messagesListener = db.collection("teams").document(code).collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val myUid = preferences.getString("userId", "").orEmpty()
                if (!initialMessagesLoaded) {
                    for (doc in snapshot.documents) {
                        knownMessages.add(doc.id)
                    }
                    initialMessagesLoaded = true
                    savePersistedMessages(code, preferences)
                    return@addSnapshotListener
                }

                var changedAny = false
                for (change in snapshot.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        if (knownMessages.add(doc.id)) {
                            changedAny = true
                            val isDeleted = doc.getBoolean("isDeleted") ?: false
                            val senderUid = doc.getString("senderUid").orEmpty()
                            if (!isDeleted && senderUid.isNotBlank() && senderUid != myUid) {
                                val senderName = doc.getString("senderName").orEmpty().ifBlank { "Team Member" }
                                val text = doc.getString("text").orEmpty().ifBlank { "📷 Sent an image" }
                                dispatchSystemNotification(
                                    appCtx,
                                    preferences,
                                    "💬 $senderName",
                                    text,
                                    isChatMessage = true,
                                    targetPage = "teams",
                                    targetTeamTab = "Team Chat"
                                )
                            }
                        }
                    }
                }
                if (changedAny) {
                    savePersistedMessages(code, preferences)
                }
            }

        // 5. Watch Team Roster for Member Join / Leave Events
        loadPersistedMembers(code, preferences)
        var initialMembersLoaded = preferences.getBoolean("notif_members_init_$code", false)
        rosterWatcherListener = db.collection("users").whereEqualTo("teamCode", code)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val myUid = preferences.getString("userId", "").orEmpty().trim()
                val myEmail = preferences.getString("email", "").orEmpty().trim().lowercase()
                val myName = preferences.getString("fullName", "").orEmpty().trim().lowercase()

                val currentMembers = mutableMapOf<String, String>()
                for (doc in snapshot.documents) {
                    val removed = doc.getBoolean("removedByLeader") ?: false
                    if (removed) continue
                    val uid = doc.getString("uid").orEmpty().ifBlank { doc.id }.trim()
                    val email = doc.getString("email").orEmpty().trim().lowercase()
                    val fullName = doc.getString("fullName").orEmpty()
                        .ifBlank { doc.getString("username").orEmpty() }
                        .ifBlank { email }
                        .trim()
                    if (fullName.isBlank()) continue
                    val key = email.ifBlank { fullName.lowercase() }.ifBlank { uid }
                    currentMembers[key] = fullName
                }

                if (!initialMembersLoaded) {
                    knownTeamMembers.clear()
                    knownTeamMembers.putAll(currentMembers)
                    initialMembersLoaded = true
                    savePersistedMembers(code, preferences)
                    return@addSnapshotListener
                }

                var changed = false
                for ((key, name) in currentMembers) {
                    if (!knownTeamMembers.containsKey(key)) {
                        changed = true
                        val isSelf = (myEmail.isNotBlank() && key == myEmail) ||
                            (myName.isNotBlank() && name.trim().lowercase() == myName) ||
                            (myUid.isNotBlank() && key == myUid)
                        if (!isSelf) {
                            dispatchSystemNotification(
                                appCtx,
                                preferences,
                                "👋 New Team Member",
                                "$name joined the team!",
                                targetPage = "teams",
                                targetTeamTab = "Members"
                            )
                        }
                    }
                }
                for ((key, prevName) in knownTeamMembers) {
                    if (!currentMembers.containsKey(key)) {
                        changed = true
                        val isSelf = (myEmail.isNotBlank() && key == myEmail) ||
                            (myName.isNotBlank() && prevName.trim().lowercase() == myName) ||
                            (myUid.isNotBlank() && key == myUid)
                        if (!isSelf) {
                            dispatchSystemNotification(
                                appCtx,
                                preferences,
                                "👋 Team Member Left",
                                "$prevName left the team.",
                                targetPage = "teams",
                                targetTeamTab = "Members"
                            )
                        }
                    }
                }

                if (changed || knownTeamMembers != currentMembers) {
                    knownTeamMembers.clear()
                    knownTeamMembers.putAll(currentMembers)
                    savePersistedMembers(code, preferences)
                }
            }

        // 6. Watch Team Document (/teams/{code}) for Team Name Updates
        teamDocWatcherListener = db.collection("teams").document(code)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val docTeamName = snapshot.getString("teamName").orEmpty().trim()
                val docLeaderUid = snapshot.getString("leaderUid").orEmpty().trim()
                if (docTeamName.isBlank()) return@addSnapshotListener

                val prevKnownName = preferences.getString("notif_known_team_name_$code", "").orEmpty().trim()
                com.example.syncedn.data.database.FirestoreManager.registerLocalTeam(code, docTeamName, docLeaderUid)
                com.example.syncedn.data.database.TeamRepository.syncTeamNameLocally(code, docTeamName, preferences)

                if (prevKnownName.isBlank()) {
                    preferences.edit().putString("notif_known_team_name_$code", docTeamName).apply()
                    return@addSnapshotListener
                }

                if (prevKnownName != docTeamName) {
                    preferences.edit().putString("notif_known_team_name_$code", docTeamName).apply()
                    com.example.syncedn.data.database.TeamRepository.recordTeamNameChangeNotification(
                        teamCode = code,
                        oldName = prevKnownName,
                        newName = docTeamName,
                        preferences = preferences
                    )
                    dispatchSystemNotification(
                        appCtx,
                        preferences,
                        "✏️ Team Name Updated",
                        "Your Team Leader has changed the team name from \"$prevKnownName\" to \"$docTeamName\".",
                        targetPage = "teams"
                    )
                }
            }

        Log.d(TAG, "Started real-time notification watchers for team $code")
    }

    private fun stopTeamCollectionWatchersOnly() {
        servicesListener?.remove()
        announcementsListener?.remove()
        substitutionsListener?.remove()
        messagesListener?.remove()
        rosterWatcherListener?.remove()
        teamDocWatcherListener?.remove()
        servicesListener = null
        announcementsListener = null
        substitutionsListener = null
        messagesListener = null
        rosterWatcherListener = null
        teamDocWatcherListener = null
        activeTeamCode = ""
        knownServices.clear()
        knownAnnouncements.clear()
        knownSubstitutions.clear()
        knownMessages.clear()
        knownTeamMembers.clear()
    }

    fun stopRealtimeTeamWatchers() {
        stopTeamCollectionWatchersOnly()
        rejoinRequestsListener?.remove()
        rejoinRequestsListener = null
        activeRejoinTeamCode = ""
        knownRejoinStatuses.clear()
    }
}
