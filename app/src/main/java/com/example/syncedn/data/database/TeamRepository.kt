package com.example.syncedn.data.database

import com.example.syncedn.data.model.AnnouncementData
import com.example.syncedn.data.model.ChatMessage
import com.example.syncedn.data.model.ServiceData
import com.example.syncedn.data.model.SongData
import com.example.syncedn.data.model.SubstitutionData
import com.example.syncedn.data.model.TeamData
import com.example.syncedn.data.model.TeamMember
import com.example.syncedn.data.model.RejoinRequestData
import com.example.syncedn.data.model.UnavailableRange
import com.example.syncedn.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import androidx.core.content.edit

/**
 * Repository managing team roster, user profiles, and ministry role operations in Cloud Firestore.
 * Provides caching and fallback resilience for offline and testing scenarios.
 */
object TeamRepository {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val USERS_COLLECTION = "users"
    private const val TEAMS_COLLECTION = "teams"
    private const val SERVICES_SUBCOLLECTION = "services"
    private const val SONGS_SUBCOLLECTION = "songs"
    private const val ANNOUNCEMENTS_SUBCOLLECTION = "announcements"
    private const val SUBSTITUTIONS_SUBCOLLECTION = "substitutions"
    private const val MESSAGES_SUBCOLLECTION = "messages"
    private const val REPORTS_SUBCOLLECTION = "reports"
    private const val REJOIN_REQUESTS_SUBCOLLECTION = "rejoin_requests"
    private const val PREFS_KEY_MESSAGES_PREFIX = "cached_messages_"
    private const val PREFS_KEY_ROSTER_PREFIX = "cached_roster_"
    private const val PREFS_KEY_REPORTS_PREFIX = "cached_reports_"

    // In-memory cache for fast UI updates, offline fallback, and unit tests
    private val cachedRosters = java.util.concurrent.ConcurrentHashMap<String, MutableList<TeamMember>>()
    private val cachedRoles = java.util.concurrent.ConcurrentHashMap<String, MutableList<String>>()
    private val cachedUserRanges = java.util.concurrent.ConcurrentHashMap<String, MutableList<UnavailableRange>>()
    private val lastRosterFetchTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val rosterInFlight = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private val cachedServices = java.util.concurrent.ConcurrentHashMap<String, MutableList<ServiceData>>()
    private val lastServicesFetchTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val servicesInFlight = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private val cachedSongs = java.util.concurrent.ConcurrentHashMap<String, MutableList<SongData>>()
    private val lastSongsFetchTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val songsInFlight = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private val cachedAnnouncements = java.util.concurrent.ConcurrentHashMap<String, MutableList<AnnouncementData>>()
    private val lastAnnouncementsFetchTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val announcementsInFlight = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private val cachedSubstitutions = java.util.concurrent.ConcurrentHashMap<String, MutableList<SubstitutionData>>()
    private val lastSubstitutionsFetchTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val substitutionsInFlight = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private val cachedChatMessages = java.util.concurrent.ConcurrentHashMap<String, MutableList<ChatMessage>>()
    private val cachedReports = java.util.concurrent.ConcurrentHashMap<String, MutableList<Map<String, Any>>>()
    private val cachedRejoinRequests = java.util.concurrent.ConcurrentHashMap<String, MutableList<RejoinRequestData>>()

    data class TeamNotificationItem(
        val id: String,
        val title: String,
        val message: String,
        val timeLabel: String,
        val iconName: String,
        val timestamp: Long
    )

    /**
     * Clears all in-memory roster, role, service, song, announcement, and substitution caches (primarily for unit tests).
     */
    @Synchronized
    fun clearCache() {
        cachedRosters.clear()
        cachedRoles.clear()
        cachedUserRanges.clear()
        lastRosterFetchTimes.clear()
        rosterInFlight.clear()
        cachedServices.clear()
        lastServicesFetchTimes.clear()
        servicesInFlight.clear()
        cachedSongs.clear()
        lastSongsFetchTimes.clear()
        songsInFlight.clear()
        cachedAnnouncements.clear()
        lastAnnouncementsFetchTimes.clear()
        announcementsInFlight.clear()
        cachedSubstitutions.clear()
        lastSubstitutionsFetchTimes.clear()
        substitutionsInFlight.clear()
        cachedChatMessages.clear()
        cachedReports.clear()
        cachedRejoinRequests.clear()
    }

    fun serializeRangesJson(ranges: List<UnavailableRange>): String {
        val array = org.json.JSONArray()
        for (item in ranges) {
            val obj = org.json.JSONObject()
            item.toMap().forEach { (k, v) -> obj.put(k, v) }
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Adds or updates a member in the local in-memory roster cache.
     * Matches by UID or email, preserving existing unavailableRanges/photoUri/ministryRole when incoming fields are empty,
     * and optionally persists the member record to SharedPreferences.
     */
    @Synchronized
    fun cacheMember(member: TeamMember, preferences: android.content.SharedPreferences? = null) {
        val code = member.teamCode.trim().uppercase()
        if (code.isBlank() || member.uid.isBlank()) return
        val emailLower = member.email.trim().lowercase()
        val list = cachedRosters.getOrPut(code) { java.util.Collections.synchronizedList(mutableListOf()) }
        var effectiveMember = member
        synchronized(list) {
            val index = list.indexOfFirst {
                it.uid == member.uid || (it.email.isNotBlank() && it.email.equals(member.email, ignoreCase = true))
            }
            if (index >= 0) {
                val existing = list[index]
                val mergedUid = if (!member.uid.startsWith("user_") && member.uid.isNotBlank()) {
                    member.uid
                } else {
                    existing.uid.takeIf { it.isNotBlank() && !it.startsWith("user_") } ?: member.uid
                }
                val mergedRanges = when {
                    member.unavailableRanges.isNotEmpty() -> member.unavailableRanges
                    existing.unavailableRanges.isNotEmpty() -> existing.unavailableRanges
                    else -> cachedUserRanges[mergedUid]?.toList()
                        ?: cachedUserRanges[member.uid]?.toList()
                        ?: (if (emailLower.isNotBlank()) cachedUserRanges["email_$emailLower"]?.toList() else null)
                        ?: emptyList()
                }
                effectiveMember = member.copy(
                    uid = mergedUid,
                    ministryRole = member.ministryRole.ifBlank { existing.ministryRole },
                    photoUri = member.photoUri.ifBlank { existing.photoUri },
                    unavailableRanges = mergedRanges
                )
                list[index] = effectiveMember
            } else {
                val fallbackRanges = if (member.unavailableRanges.isNotEmpty()) {
                    member.unavailableRanges
                } else {
                    cachedUserRanges[member.uid]?.toList()
                        ?: (if (emailLower.isNotBlank()) cachedUserRanges["email_$emailLower"]?.toList() else null)
                        ?: emptyList()
                }
                effectiveMember = if (fallbackRanges.isNotEmpty() && member.unavailableRanges.isEmpty()) {
                    member.copy(unavailableRanges = fallbackRanges)
                } else {
                    member
                }
                list.add(effectiveMember)
            }
            sortRoster(list)
        }

        if (effectiveMember.unavailableRanges.isNotEmpty()) {
            cachedUserRanges[effectiveMember.uid] = java.util.Collections.synchronizedList(effectiveMember.unavailableRanges.toMutableList())
            if (emailLower.isNotBlank()) {
                cachedUserRanges["email_$emailLower"] = java.util.Collections.synchronizedList(effectiveMember.unavailableRanges.toMutableList())
            }
        }

        if (preferences != null) {
            if (emailLower.isNotBlank()) {
                preferences.edit()
                    .putString("email_code_$emailLower", code)
                    .putString("email_role_$emailLower", effectiveMember.role)
                    .putString("email_uid_$emailLower", effectiveMember.uid)
                    .apply()
                if (effectiveMember.fullName.isNotBlank()) {
                    preferences.edit().putString("email_fullname_$emailLower", effectiveMember.fullName).apply()
                }
                if (effectiveMember.username.isNotBlank()) {
                    preferences.edit().putString("email_username_$emailLower", effectiveMember.username).apply()
                }
                if (effectiveMember.ministryRole.isNotBlank()) {
                    preferences.edit()
                        .putString("email_ministry_$emailLower", effectiveMember.ministryRole)
                        .putString("user_ministry_${effectiveMember.uid}", effectiveMember.ministryRole)
                        .apply()
                }
            }
            if (effectiveMember.unavailableRanges.isNotEmpty()) {
                try {
                    val rangesJson = serializeRangesJson(effectiveMember.unavailableRanges)
                    preferences.edit().apply {
                        putString("user_unavailable_ranges_${effectiveMember.uid}", rangesJson)
                        if (emailLower.isNotBlank()) {
                            putString("email_unavailable_ranges_$emailLower", rangesJson)
                        }
                    }.apply()
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Synchronizes and restores any known team members for this teamCode from SharedPreferences
     * into the in-memory cache, ensuring members and their unavailableRanges are preserved across
     * logout/login and offline scenarios.
     */
    @Synchronized
    fun syncFromPreferences(preferences: android.content.SharedPreferences, teamCode: String) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return

        // Hydrate cachedRosters[code] from cached_roster_$code first if in-memory cache is empty
        getCachedRoster(code, preferences)

        val all = preferences.all
        val memberRolesMap = try {
            org.json.JSONObject(preferences.getString(com.example.syncedn.data.auth.SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty())
        } catch (_: Throwable) { null }

        for ((key, value) in all) {
            if (key.startsWith("email_code_") && value is String && value.trim().uppercase() == code) {
                val emailLower = key.removePrefix("email_code_").trim().lowercase()
                val role = (all["email_role_$emailLower"] as? String).orEmpty().ifBlank { "Member" }
                val fullName = (all["email_fullname_$emailLower"] as? String).orEmpty()
                val username = (all["email_username_$emailLower"] as? String).orEmpty().ifBlank { emailLower.substringBefore("@") }
                val rawUid = (all["email_uid_$emailLower"] as? String).orEmpty().ifBlank { "user_${emailLower.hashCode()}" }

                val existingInRoster = cachedRosters[code]?.let { list ->
                    synchronized(list) {
                        list.find {
                            it.uid == rawUid || (emailLower.isNotBlank() && it.email.equals(emailLower, ignoreCase = true))
                        }
                    }
                }
                val uid = existingInRoster?.uid?.takeIf { it.isNotBlank() && !it.startsWith("user_") } ?: rawUid

                val assignedRole = memberRolesMap?.let { map ->
                    val byUid = if (uid.isNotBlank()) map.optString(uid, "") else ""
                    val byEmail = if (emailLower.isNotBlank()) map.optString(emailLower, "") else ""
                    val byFull = if (fullName.isNotBlank()) map.optString(fullName, "") else ""
                    val byUser = if (username.isNotBlank()) map.optString(username, "") else ""
                    byUid.ifBlank { byEmail }.ifBlank { byFull }.ifBlank { byUser }
                }?.takeIf { it.isNotBlank() }

                val ministry = assignedRole
                    ?: (all["email_ministry_$emailLower"] as? String).orEmpty().takeIf { it.isNotBlank() }
                    ?: (all["user_ministry_$uid"] as? String).orEmpty().takeIf { it.isNotBlank() }
                    ?: existingInRoster?.ministryRole.orEmpty()

                val photo = (all["email_photo_$emailLower"] as? String).orEmpty().takeIf { it.isNotBlank() }
                    ?: (all["user_photo_$uid"] as? String).orEmpty().takeIf { it.isNotBlank() }
                    ?: existingInRoster?.photoUri.orEmpty()
                val memberName = fullName.ifBlank { existingInRoster?.fullName.orEmpty() }.ifBlank { username }
                if (memberName.isNotBlank()) {
                    val ranges = getMemberUnavailableRanges(uid, preferences, emailLower, memberName).ifEmpty {
                        existingInRoster?.unavailableRanges.orEmpty()
                    }
                    val member = TeamMember(
                        uid = uid,
                        fullName = memberName,
                        username = username,
                        email = emailLower,
                        role = role,
                        ministryRole = ministry,
                        teamCode = code,
                        photoUri = photo,
                        unavailableRanges = ranges
                    )
                    cacheMember(member)
                }
            }
        }
    }

    /**
     * Retrieves the current in-memory roster for a team code.
     */
    @Synchronized
    fun getCachedRoster(teamCode: String): List<TeamMember> {
        return getCachedRoster(teamCode, null)
    }

    @Synchronized
    fun getCachedRoster(teamCode: String, preferences: android.content.SharedPreferences?): List<TeamMember> {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return emptyList()

        val removedSet = mutableSetOf<String>()
        if (preferences != null) {
            preferences.getStringSet("team_removed_members_$code", null)?.let { s ->
                removedSet.addAll(s.map { it.trim().lowercase() })
            }
            preferences.getStringSet("removedMembers", null)?.let { s ->
                removedSet.addAll(s.map { it.trim().lowercase() })
            }
        }

        val list = cachedRosters[code]
        if (!list.isNullOrEmpty()) {
            return synchronized(list) {
                if (removedSet.isEmpty()) list.toList()
                else list.filter { m ->
                    m.uid.trim().lowercase() !in removedSet &&
                    (m.email.isBlank() || m.email.trim().lowercase() !in removedSet)
                }.toList()
            }
        }
        if (preferences != null) {
            val json = preferences.getString("$PREFS_KEY_ROSTER_PREFIX$code", null)
            if (!json.isNullOrBlank()) {
                try {
                    val arr = org.json.JSONArray(json)
                    val result = mutableListOf<TeamMember>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val map = mutableMapOf<String, Any?>()
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            map[k] = obj.opt(k)
                        }
                        val parsedMember = TeamMember.fromMap(map)
                        val mUid = parsedMember.uid.trim().lowercase()
                        val mEmail = parsedMember.email.trim().lowercase()
                        if (removedSet.isNotEmpty() && (mUid in removedSet || (mEmail.isNotBlank() && mEmail in removedSet))) {
                            continue
                        }
                        if (parsedMember.unavailableRanges.isNotEmpty()) {
                            if (parsedMember.uid.isNotBlank()) {
                                cachedUserRanges[parsedMember.uid] = java.util.Collections.synchronizedList(parsedMember.unavailableRanges.toMutableList())
                            }
                            if (mEmail.isNotBlank()) {
                                cachedUserRanges["email_$mEmail"] = java.util.Collections.synchronizedList(parsedMember.unavailableRanges.toMutableList())
                            }
                        }
                        result.add(parsedMember)
                    }
                    if (result.isNotEmpty()) {
                        sortRoster(result)
                        cachedRosters[code] = java.util.Collections.synchronizedList(result)
                        return result.toList()
                    }
                } catch (_: Exception) {}
            }
        }
        return emptyList()
    }

    fun saveRosterLocally(
        teamCode: String,
        members: List<TeamMember>,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return
        val sorted = members.map { m ->
            val emailLower = m.email.trim().lowercase()
            val effectiveRanges = if (m.unavailableRanges.isNotEmpty()) {
                m.unavailableRanges
            } else {
                cachedUserRanges[m.uid]?.toList()
                    ?: (if (emailLower.isNotBlank()) cachedUserRanges["email_$emailLower"]?.toList() else null)
                    ?: emptyList()
            }
            val updated = if (effectiveRanges.isNotEmpty() && m.unavailableRanges.isEmpty()) {
                m.copy(unavailableRanges = effectiveRanges)
            } else {
                m
            }
            if (updated.unavailableRanges.isNotEmpty()) {
                if (updated.uid.isNotBlank()) {
                    cachedUserRanges[updated.uid] = java.util.Collections.synchronizedList(updated.unavailableRanges.toMutableList())
                }
                if (emailLower.isNotBlank()) {
                    cachedUserRanges["email_$emailLower"] = java.util.Collections.synchronizedList(updated.unavailableRanges.toMutableList())
                }
            }
            updated
        }.toMutableList()
        sortRoster(sorted)
        cachedRosters[code] = java.util.Collections.synchronizedList(sorted)

        if (preferences != null) {
            try {
                val arr = org.json.JSONArray()
                for (m in sorted) {
                    val obj = org.json.JSONObject()
                    m.toMap().forEach { (k, v) ->
                        if (k == "unavailableRanges" && v is List<*>) {
                            val rArr = org.json.JSONArray()
                            v.forEach { r -> (r as? Map<*, *>)?.let { rArr.put(org.json.JSONObject(it)) } }
                            obj.put(k, rArr)
                        } else {
                            obj.put(k, v)
                        }
                    }
                    arr.put(obj)
                }
                preferences.edit().apply {
                    putString("$PREFS_KEY_ROSTER_PREFIX$code", arr.toString())
                    for (m in sorted) {
                        val emailLower = m.email.trim().lowercase()
                        if (emailLower.isNotBlank() && m.uid.isNotBlank()) {
                            putString("email_uid_$emailLower", m.uid)
                        }
                        if (m.photoUri.isNotBlank()) {
                            if (m.uid.isNotBlank()) putString("user_photo_${m.uid}", m.photoUri)
                            if (emailLower.isNotBlank()) putString("email_photo_$emailLower", m.photoUri)
                        }
                        if (m.unavailableRanges.isNotEmpty()) {
                            val rangesJson = serializeRangesJson(m.unavailableRanges)
                            if (m.uid.isNotBlank()) putString("user_unavailable_ranges_${m.uid}", rangesJson)
                            if (emailLower.isNotBlank()) putString("email_unavailable_ranges_$emailLower", rangesJson)
                        }
                    }
                }.apply()
            } catch (_: Exception) {}
        }
    }

    private fun sortRoster(list: MutableList<TeamMember>) {
        list.sortWith(compareByDescending<TeamMember> { it.role.equals("Leader", ignoreCase = true) }
            .thenBy { it.fullName.ifBlank { it.username } })
    }

    /**
     * Saves user profile details to Cloud Firestore (/users/{uid}) and updates cached roster entries.
     */
    fun updateUserProfile(profile: UserProfile, onComplete: (Result<Unit>) -> Unit = {}) {
        if (profile.uid.isBlank()) {
            onComplete(Result.failure(IllegalArgumentException("User UID cannot be blank")))
            return
        }

        // Update local roster cache if user belongs to a team
        if (profile.teamCode.isNotBlank()) {
            val member = TeamMember(
                uid = profile.uid,
                fullName = profile.fullName,
                username = profile.username,
                email = profile.email,
                role = profile.role,
                ministryRole = profile.ministryRole,
                teamCode = profile.teamCode,
                photoUri = profile.photoUri,
                unavailableRanges = profile.unavailableRanges
            )
            cacheMember(member)
        }

        val updates = mutableMapOf<String, Any>(
            "uid" to profile.uid,
            "fullName" to profile.fullName,
            "username" to profile.username,
            "phone" to profile.phone,
            "country" to profile.country,
            "language" to profile.language,
            "ministryRole" to profile.ministryRole
        )
        if (profile.photoUri.isNotBlank()) updates["photoUri"] = profile.photoUri
        if (profile.email.isNotBlank()) updates["email"] = profile.email
        if (profile.teamCode.isNotBlank()) updates["teamCode"] = profile.teamCode
        if (profile.teamName.isNotBlank()) updates["teamName"] = profile.teamName
        if (profile.role.isNotBlank()) updates["role"] = profile.role

        try {
            db.collection(USERS_COLLECTION)
                .document(profile.uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { onComplete(Result.success(Unit)) }
                .addOnFailureListener { onComplete(Result.failure(it)) }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Fetches the real team roster for a given teamCode from Cloud Firestore (/users where teamCode == teamCode).
     * Protected with a timeout guard, in-flight throttling, and fallback to cached roster.
     */
    fun getTeamRoster(
        teamCode: String,
        force: Boolean = false,
        preferences: android.content.SharedPreferences? = null,
        onComplete: (Result<List<TeamMember>>) -> Unit
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) {
            onComplete(Result.success(emptyList()))
            return
        }

        val cached = getCachedRoster(code, preferences)
        val now = System.currentTimeMillis()
        val lastFetch = lastRosterFetchTimes[code] ?: 0L

        // If not forced and recently fetched with a non-empty cache, return cache immediately
        if (!force && cached.isNotEmpty() && (now - lastFetch < 15_000L)) {
            onComplete(Result.success(cached))
            return
        }

        // If a request is already in-flight for this team, return cached roster immediately to avoid spam
        if (rosterInFlight.putIfAbsent(code, true) == true) {
            onComplete(Result.success(cached))
            return
        }

        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finishOnce(result: Result<List<TeamMember>>) {
            rosterInFlight.remove(code)
            lastRosterFetchTimes[code] = System.currentTimeMillis()
            if (completed.compareAndSet(false, true)) {
                onComplete(result)
            }
        }

        // Safety timeout guard (3 seconds)
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                finishOnce(Result.success(getCachedRoster(code, preferences)))
            }, 3000L)
        } catch (_: Exception) {}

        try {
            db.collection(TEAMS_COLLECTION).document(code).get()
                .addOnCompleteListener { tTask ->
                    val teamRemovedSet = mutableSetOf<String>()
                    if (tTask.isSuccessful && tTask.result != null) {
                        val list = (tTask.result.get("removedMembers") as? List<*>)?.mapNotNull { it?.toString()?.trim()?.lowercase() }.orEmpty()
                        teamRemovedSet.addAll(list)
                    }
                    if (preferences != null) {
                        preferences.getStringSet("team_removed_members_$code", null)?.let { s ->
                            teamRemovedSet.addAll(s.map { it.trim().lowercase() })
                        }
                    }

                    db.collection(USERS_COLLECTION)
                        .whereEqualTo("teamCode", code)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            val rawMembers = mutableListOf<TeamMember>()
                            if (snapshot != null) {
                                for (doc in snapshot.documents) {
                                    val data = doc.data ?: continue
                                    val mapWithId = data.toMutableMap()
                                    val uidFromDoc = (mapWithId["uid"] as? String)?.ifBlank { doc.id } ?: doc.id
                                    mapWithId["uid"] = uidFromDoc

                                    val isRemovedByLeader = data["removedByLeader"] == true
                                    val rTeam = (data["removedTeamCode"] as? String).orEmpty()
                                    if (isRemovedByLeader && (rTeam.isBlank() || rTeam.equals(code, ignoreCase = true))) {
                                        continue
                                    }
                                    val memberEmailLower = (data["email"] as? String).orEmpty().trim().lowercase()
                                    if (uidFromDoc.trim().lowercase() in teamRemovedSet || (memberEmailLower.isNotBlank() && memberEmailLower in teamRemovedSet)) {
                                        continue
                                    }

                                    rawMembers.add(TeamMember.fromMap(mapWithId))
                                }
                            }
                            // Merge any duplicate documents for the same email/uid so unavailableRanges and ministryRole are never lost
                            val mergedByIdentity = LinkedHashMap<String, TeamMember>()
                            for (m in rawMembers) {
                                val key = m.email.trim().lowercase().ifBlank { m.uid }
                                val existing = mergedByIdentity[key]
                                if (existing == null) {
                                    mergedByIdentity[key] = m
                                } else {
                                    val preferredUid = if (!m.uid.startsWith("user_") && m.uid.isNotBlank()) m.uid else existing.uid.ifBlank { m.uid }
                                    val combinedRanges = (existing.unavailableRanges + m.unavailableRanges)
                                        .distinctBy { it.id.ifBlank { "${it.startDate}_${it.endDate}_${it.reason}" } }
                                    mergedByIdentity[key] = existing.copy(
                                        uid = preferredUid,
                                        fullName = existing.fullName.ifBlank { m.fullName },
                                        username = existing.username.ifBlank { m.username },
                                        role = if (existing.role.equals("Leader", true) || m.role.equals("Leader", true)) "Leader" else existing.role.ifBlank { m.role },
                                        ministryRole = existing.ministryRole.ifBlank { m.ministryRole },
                                        photoUri = existing.photoUri.ifBlank { m.photoUri },
                                        unavailableRanges = combinedRanges
                                    )
                                }
                            }
                            val members = mergedByIdentity.values.map { m ->
                                if (m.email.trim().equals("ryanlloydgenturo@gmail.com", ignoreCase = true) || m.role.equals("Leader", ignoreCase = true)) {
                                    m.copy(
                                        role = "Leader",
                                        ministryRole = if (m.ministryRole.isBlank() || m.ministryRole.equals("Member", true)) "Worship Leader" else m.ministryRole
                                    )
                                } else {
                                    m
                                }
                            }.toList()
                            saveRosterLocally(code, members, preferences)
                            finishOnce(Result.success(getCachedRoster(code, preferences)))
                        }
                        .addOnFailureListener {
                            finishOnce(Result.success(getCachedRoster(code, preferences)))
                        }
                }
        } catch (_: Exception) {
            finishOnce(Result.success(getCachedRoster(code, preferences)))
        }
    }

    fun getTeamRoster(teamCode: String, onComplete: (Result<List<TeamMember>>) -> Unit) {
        getTeamRoster(teamCode, false, null, onComplete)
    }

    fun getTeamRoster(teamCode: String, force: Boolean, onComplete: (Result<List<TeamMember>>) -> Unit) {
        getTeamRoster(teamCode, force, null, onComplete)
    }

    /**
     * Updates a member's ministry role (e.g. "Vocalist", "Guitarist") in Firestore and local cache.
     * Matches in-memory and Firestore records by memberUid, memberEmail, and/or memberName (fullName / username).
     * Automatically persists to SharedPreferences if preferences is provided.
     */
    @Synchronized
    fun updateMemberMinistryRole(
        memberUid: String,
        newRole: String,
        memberName: String = "",
        memberEmail: String = "",
        preferences: android.content.SharedPreferences? = null,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        if (memberUid.isBlank() && memberName.isBlank() && memberEmail.isBlank()) {
            onComplete(Result.success(Unit))
            return
        }

        val cleanEmail = memberEmail.trim().lowercase()

        // Update in-memory caches
        for ((_, roster) in cachedRosters) {
            synchronized(roster) {
                val member = roster.find {
                    (memberUid.isNotBlank() && it.uid == memberUid) ||
                    (cleanEmail.isNotBlank() && it.email.equals(cleanEmail, ignoreCase = true)) ||
                    (memberName.isNotBlank() && (it.fullName.equals(memberName, true) || it.username.equals(memberName, true)))
                }
                if (member != null) {
                    val updated = member.copy(ministryRole = newRole)
                    val idx = roster.indexOf(member)
                    roster[idx] = updated
                }
            }
        }

        if (preferences != null) {
            preferences.edit {
                if (cleanEmail.isNotBlank()) putString("email_ministry_$cleanEmail", newRole)
                if (memberUid.isNotBlank()) putString("user_ministry_$memberUid", newRole)
                try {
                    val map = org.json.JSONObject(preferences.getString(com.example.syncedn.data.auth.SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty())
                    if (cleanEmail.isNotBlank()) map.put(cleanEmail, newRole)
                    if (memberUid.isNotBlank()) map.put(memberUid, newRole)
                    if (memberName.isNotBlank()) map.put(memberName, newRole)
                    putString(com.example.syncedn.data.auth.SessionManager.KEY_MEMBER_ROLES, map.toString())
                } catch (_: Throwable) {}
            }
        }

        try {
            if (cleanEmail.isNotBlank()) {
                db.collection(USERS_COLLECTION)
                    .whereEqualTo("email", cleanEmail)
                    .get()
                    .addOnSuccessListener { snap ->
                        if (snap != null && !snap.isEmpty) {
                            for (doc in snap.documents) {
                                doc.reference.update("ministryRole", newRole)
                            }
                            onComplete(Result.success(Unit))
                        } else if (memberUid.isNotBlank() && !memberUid.startsWith("user_")) {
                            db.collection(USERS_COLLECTION)
                                .document(memberUid)
                                .update("ministryRole", newRole)
                                .addOnSuccessListener { onComplete(Result.success(Unit)) }
                                .addOnFailureListener { onComplete(Result.failure(it)) }
                        } else {
                            onComplete(Result.success(Unit))
                        }
                    }
                    .addOnFailureListener {
                        if (memberUid.isNotBlank() && !memberUid.startsWith("user_")) {
                            db.collection(USERS_COLLECTION)
                                .document(memberUid)
                                .update("ministryRole", newRole)
                                .addOnSuccessListener { onComplete(Result.success(Unit)) }
                                .addOnFailureListener { onComplete(Result.failure(it)) }
                        } else {
                            onComplete(Result.failure(it))
                        }
                    }
            } else if (memberUid.isNotBlank() && !memberUid.startsWith("user_")) {
                db.collection(USERS_COLLECTION)
                    .document(memberUid)
                    .update("ministryRole", newRole)
                    .addOnSuccessListener { onComplete(Result.success(Unit)) }
                    .addOnFailureListener { onComplete(Result.failure(it)) }
            } else if (memberName.isNotBlank()) {
                db.collection(USERS_COLLECTION)
                    .whereEqualTo("fullName", memberName)
                    .get()
                    .addOnSuccessListener { snap ->
                        for (doc in snap.documents) {
                            doc.reference.update("ministryRole", newRole)
                        }
                        onComplete(Result.success(Unit))
                    }
                    .addOnFailureListener { onComplete(Result.failure(it)) }
            } else {
                onComplete(Result.success(Unit))
            }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Retrieves the list of ministry roles for the team.
     */
    fun getTeamCustomRoles(teamCode: String, onComplete: (Result<List<String>>) -> Unit) {
        val code = teamCode.trim().uppercase()
        val cached = cachedRoles[code]
        if (!cached.isNullOrEmpty()) {
            onComplete(Result.success(cached.toList()))
            return
        }

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val team = TeamData.fromMap(doc.data ?: emptyMap())
                        cachedRoles[code] = team.roles.toMutableList()
                        onComplete(Result.success(team.roles))
                    } else {
                        onComplete(Result.success(TeamData.DEFAULT_ROLES))
                    }
                }
                .addOnFailureListener {
                    onComplete(Result.success(TeamData.DEFAULT_ROLES))
                }
        } catch (_: Exception) {
            onComplete(Result.success(TeamData.DEFAULT_ROLES))
        }
    }

    /**
     * Saves customized roles to the team document in Cloud Firestore.
     */
    fun saveTeamCustomRoles(teamCode: String, roles: List<String>, onComplete: (Result<Unit>) -> Unit = {}) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || roles.isEmpty()) {
            onComplete(Result.success(Unit))
            return
        }
        cachedRoles[code] = roles.toMutableList()

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .set(mapOf("roles" to roles), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { onComplete(Result.success(Unit)) }
                .addOnFailureListener { onComplete(Result.failure(it)) }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Removes a member from a team, resetting their team fields in Firestore and cache.
     */
    @Synchronized
    fun removeMemberFromTeam(
        memberUid: String,
        teamCode: String = "",
        memberEmail: String = "",
        preferences: android.content.SharedPreferences? = null,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        if (memberUid.isBlank()) {
            onComplete(Result.success(Unit))
            return
        }

        val code = teamCode.trim().uppercase()
        val emailLower = memberEmail.trim().lowercase()

        // Remove from local memory cache
        for ((_, roster) in cachedRosters) {
            synchronized(roster) {
                roster.removeAll { it.uid == memberUid || (emailLower.isNotBlank() && it.email.equals(emailLower, true)) }
            }
        }

        FirestoreManager.clearUserRoleAndTeam(memberUid, emailLower)

        // Store into preferences removed set
        if (preferences != null) {
            if (code.isNotBlank()) {
                val set = preferences.getStringSet("team_removed_members_$code", null)?.toMutableSet() ?: mutableSetOf()
                set.add(memberUid)
                if (emailLower.isNotBlank()) set.add(emailLower)
                preferences.edit().putStringSet("team_removed_members_$code", set).apply()

                val currentRoster = getCachedRoster(code, preferences)
                saveRosterLocally(code, currentRoster, preferences)
            }
            val genRemoved = preferences.getStringSet("removedMembers", null)?.toMutableSet() ?: mutableSetOf()
            genRemoved.add(memberUid)
            if (emailLower.isNotBlank()) genRemoved.add(emailLower)
            val editor = preferences.edit().putStringSet("removedMembers", genRemoved)
            if (emailLower.isNotBlank()) {
                editor.remove("email_team_$emailLower")
                editor.remove("email_code_$emailLower")
                editor.remove("email_ministry_$emailLower")
                editor.putBoolean("email_removed_by_leader_$emailLower", true)
                if (code.isNotBlank()) editor.putString("email_removed_team_code_$emailLower", code)
            }
            if (memberUid.isNotBlank()) {
                editor.remove("user_team_$memberUid")
                editor.remove("user_code_$memberUid")
                editor.remove("user_ministry_$memberUid")
                editor.putBoolean("user_removed_by_leader_$memberUid", true)
                if (code.isNotBlank()) editor.putString("user_removed_team_code_$memberUid", code)
            }
            val activeUserEmail = preferences.getString("email", "").orEmpty().trim().lowercase()
            val activeUserId = preferences.getString("userId", "").orEmpty()
            if (activeUserEmail == emailLower || (memberUid.isNotBlank() && activeUserId == memberUid)) {
                editor.remove("teamName")
                editor.remove("teamCode")
                editor.remove("ministryRole")
                editor.putString("role", "Member")
                editor.putBoolean("removedByLeader", true)
                if (code.isNotBlank()) editor.putString("removedTeamCode", code)
            }
            editor.apply()
        }

        val updates = mutableMapOf<String, Any>(
            "teamCode" to "",
            "teamName" to "",
            "ministryRole" to "",
            "role" to "Member",
            "removedByLeader" to true
        )
        if (code.isNotBlank()) {
            updates["removedTeamCode"] = code
        }

        try {
            if (code.isNotBlank()) {
                val unionList = mutableListOf<Any>(memberUid)
                if (emailLower.isNotBlank()) unionList.add(emailLower)
                db.collection(TEAMS_COLLECTION).document(code)
                    .update("removedMembers", com.google.firebase.firestore.FieldValue.arrayUnion(*unionList.toTypedArray()))
            }
            db.collection(USERS_COLLECTION)
                .document(memberUid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    if (emailLower.isNotBlank()) {
                        db.collection(USERS_COLLECTION).whereEqualTo("email", emailLower).get()
                            .addOnSuccessListener { qSnap ->
                                qSnap.documents.forEach { doc ->
                                    doc.reference.set(updates, com.google.firebase.firestore.SetOptions.merge())
                                }
                                onComplete(Result.success(Unit))
                            }
                            .addOnFailureListener { onComplete(Result.success(Unit)) }
                    } else {
                        onComplete(Result.success(Unit))
                    }
                }
                .addOnFailureListener {
                    if (emailLower.isNotBlank()) {
                        db.collection(USERS_COLLECTION).whereEqualTo("email", emailLower).get()
                            .addOnSuccessListener { qSnap ->
                                qSnap.documents.forEach { doc ->
                                    doc.reference.set(updates, com.google.firebase.firestore.SetOptions.merge())
                                }
                                onComplete(Result.success(Unit))
                            }
                            .addOnFailureListener { onComplete(Result.failure(it)) }
                    } else {
                        onComplete(Result.failure(it))
                    }
                }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    fun removeMemberFromTeam(
        memberUid: String,
        teamCode: String = "",
        memberEmail: String = "",
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        removeMemberFromTeam(memberUid, teamCode, memberEmail, null, onComplete)
    }

    fun removeMemberFromTeam(
        memberUid: String,
        teamCode: String = "",
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        removeMemberFromTeam(memberUid, teamCode, "", null, onComplete)
    }

    /**
     * Resets a user's team membership in Firestore (voluntary leave).
     * Does NOT mark as removedByLeader, allowing them to rejoin with a valid code.
     */
    fun leaveTeam(uid: String, onComplete: (Result<Unit>) -> Unit = {}) {
        if (uid.isBlank()) {
            onComplete(Result.success(Unit))
            return
        }

        for ((_, roster) in cachedRosters) {
            synchronized(roster) {
                roster.removeAll { it.uid == uid }
            }
        }

        val updates = mapOf<String, Any>(
            "teamCode" to "",
            "teamName" to "",
            "ministryRole" to "",
            "role" to "Member",
            "removedByLeader" to false,
            "removedTeamCode" to ""
        )

        try {
            db.collection(USERS_COLLECTION)
                .document(uid)
                .update(updates)
                .addOnSuccessListener { onComplete(Result.success(Unit)) }
                .addOnFailureListener { onComplete(Result.failure(it)) }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Checks if a member was removed by the team leader from the specified team.
     */
    fun isMemberRemovedFromTeam(
        teamCode: String,
        uid: String,
        email: String = "",
        preferences: android.content.SharedPreferences? = null,
        onResult: (Boolean) -> Unit
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || (uid.isBlank() && email.isBlank())) {
            onResult(false)
            return
        }

        val uidLower = uid.trim().lowercase()
        val emailLower = email.trim().lowercase()

        preferences?.let { prefs ->
            if (emailLower.isNotBlank() && prefs.getBoolean("email_removed_by_leader_$emailLower", false)) {
                val rCode = prefs.getString("email_removed_team_code_$emailLower", "").orEmpty()
                if (rCode.isBlank() || rCode.equals(code, ignoreCase = true)) {
                    onResult(true)
                    return
                }
            }
            if (uid.isNotBlank() && prefs.getBoolean("user_removed_by_leader_$uid", false)) {
                val rCode = prefs.getString("user_removed_team_code_$uid", "").orEmpty()
                if (rCode.isBlank() || rCode.equals(code, ignoreCase = true)) {
                    onResult(true)
                    return
                }
            }
            prefs.getStringSet("team_removed_members_$code", null)?.let { set ->
                val lowerSet = set.map { it.trim().lowercase() }
                if (lowerSet.contains(uidLower) || (emailLower.isNotBlank() && lowerSet.contains(emailLower))) {
                    onResult(true)
                    return
                }
            }
            prefs.getStringSet("removedMembers", null)?.let { set ->
                val lowerSet = set.map { it.trim().lowercase() }
                if (lowerSet.contains(uidLower) || (emailLower.isNotBlank() && lowerSet.contains(emailLower))) {
                    onResult(true)
                    return
                }
            }
        }

        if (uid.isNotBlank()) {
            db.collection(USERS_COLLECTION).document(uid).get()
                .addOnSuccessListener { snap ->
                    val removed = snap.getBoolean("removedByLeader") ?: false
                    val rTeamCode = snap.getString("removedTeamCode").orEmpty()
                    if (removed && (rTeamCode.isBlank() || rTeamCode.equals(code, ignoreCase = true))) {
                        onResult(true)
                        return@addOnSuccessListener
                    }

                    db.collection(TEAMS_COLLECTION).document(code).get()
                        .addOnSuccessListener { tSnap ->
                            val list = (tSnap.get("removedMembers") as? List<*>)?.mapNotNull { it?.toString()?.trim()?.lowercase() }.orEmpty()
                            val isRemoved = list.contains(uidLower) || (emailLower.isNotBlank() && list.contains(emailLower))
                            onResult(isRemoved)
                        }
                        .addOnFailureListener {
                            onResult(false)
                        }
                }
                .addOnFailureListener {
                    onResult(false)
                }
        } else {
            db.collection(TEAMS_COLLECTION).document(code).get()
                .addOnSuccessListener { tSnap ->
                    val list = (tSnap.get("removedMembers") as? List<*>)?.mapNotNull { it?.toString()?.trim()?.lowercase() }.orEmpty()
                    val isRemoved = emailLower.isNotBlank() && list.contains(emailLower)
                    onResult(isRemoved)
                }
                .addOnFailureListener {
                    onResult(false)
                }
        }
    }

    /**
     * Sends a re-join request to the team leader after a member was removed.
     */
    fun sendRejoinRequest(
        teamCode: String,
        req: RejoinRequestData,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || req.id.isBlank()) {
            onComplete(Result.failure(IllegalArgumentException("Team code and request ID cannot be blank")))
            return
        }
        val list = cachedRejoinRequests.getOrPut(code) { java.util.Collections.synchronizedList(mutableListOf()) }
        synchronized(list) {
            list.removeAll { it.id == req.id }
            list.add(0, req)
        }
        try {
            db.collection(TEAMS_COLLECTION).document(code)
                .collection(REJOIN_REQUESTS_SUBCOLLECTION).document(req.id)
                .set(req.toMap())
                .addOnSuccessListener { onComplete(Result.success(Unit)) }
                .addOnFailureListener { onComplete(Result.failure(it)) }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Retrieves all pending and past re-join requests for a team.
     */
    fun getRejoinRequests(
        teamCode: String,
        onComplete: (Result<List<RejoinRequestData>>) -> Unit
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) {
            onComplete(Result.success(emptyList()))
            return
        }
        try {
            db.collection(TEAMS_COLLECTION).document(code)
                .collection(REJOIN_REQUESTS_SUBCOLLECTION)
                .get()
                .addOnSuccessListener { snap ->
                    val list = mutableListOf<RejoinRequestData>()
                    for (doc in snap.documents) {
                        val data = doc.data ?: continue
                        list.add(RejoinRequestData.fromMap(data))
                    }
                    list.sortByDescending { it.createdAt }
                    cachedRejoinRequests[code] = java.util.Collections.synchronizedList(list)
                    onComplete(Result.success(list))
                }
                .addOnFailureListener {
                    val cached = cachedRejoinRequests[code]?.toList().orEmpty()
                    onComplete(Result.success(cached))
                }
        } catch (e: Exception) {
            val cached = cachedRejoinRequests[code]?.toList().orEmpty()
            onComplete(Result.success(cached))
        }
    }

    /**
     * Synchronously returns cached re-join requests for a team.
     */
    fun getCachedRejoinRequests(teamCode: String): List<RejoinRequestData> {
        val code = teamCode.trim().uppercase()
        val list = cachedRejoinRequests[code] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    /**
     * Approves a member's re-join request:
     * - Removes them from /teams/{teamCode}.removedMembers
     * - Clears removedByLeader and sets teamCode in /users/{userUid}
     * - Updates rejoin request state to 'Approved'
     */
    fun approveRejoinRequest(
        teamCode: String,
        req: RejoinRequestData,
        teamName: String = "",
        preferences: android.content.SharedPreferences? = null,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val code = teamCode.trim().uppercase()
        val uid = req.userUid
        val email = req.userEmail.trim().lowercase()

        // 1. Remove from team document removedMembers array
        val removeTargets = mutableListOf<Any>(uid)
        if (email.isNotBlank()) removeTargets.add(email)
        try {
            db.collection(TEAMS_COLLECTION).document(code)
                .update("removedMembers", com.google.firebase.firestore.FieldValue.arrayRemove(*removeTargets.toTypedArray()))
        } catch (_: Throwable) {}

        // 2. Clear local preferences removed sets
        preferences?.let { prefs ->
            val curRemoved = prefs.getStringSet("team_removed_members_$code", null)?.toMutableSet()
            if (curRemoved != null) {
                curRemoved.remove(uid)
                if (email.isNotBlank()) curRemoved.remove(email)
                prefs.edit().putStringSet("team_removed_members_$code", curRemoved).apply()
            }
            val genRemoved = prefs.getStringSet("removedMembers", null)?.toMutableSet()
            if (genRemoved != null) {
                genRemoved.remove(uid)
                if (email.isNotBlank()) genRemoved.remove(email)
                prefs.edit().putStringSet("removedMembers", genRemoved).apply()
            }
        }

        preferences?.let { prefs ->
            val editor = prefs.edit()
            if (email.isNotBlank()) {
                editor.putBoolean("email_removed_by_leader_$email", false)
                editor.remove("email_removed_team_code_$email")
            }
            if (uid.isNotBlank()) {
                editor.putBoolean("user_removed_by_leader_$uid", false)
                editor.remove("user_removed_team_code_$uid")
            }
            val activeUserEmail = prefs.getString("email", "").orEmpty().trim().lowercase()
            val activeUserId = prefs.getString("userId", "").orEmpty()
            if (activeUserEmail == email || (uid.isNotBlank() && activeUserId == uid)) {
                editor.putBoolean("removedByLeader", false)
                editor.remove("removedTeamCode")
            }
            editor.apply()
        }

        // 3. Update user doc to restore membership
        val userUpdates = mutableMapOf<String, Any>(
            "removedByLeader" to false,
            "removedTeamCode" to "",
            "teamCode" to code,
            "role" to "Member"
        )
        if (teamName.isNotBlank()) userUpdates["teamName"] = teamName
        try {
            db.collection(USERS_COLLECTION).document(uid).set(userUpdates, com.google.firebase.firestore.SetOptions.merge())
            if (email.isNotBlank()) {
                db.collection(USERS_COLLECTION).whereEqualTo("email", email).get()
                    .addOnSuccessListener { qSnap ->
                        qSnap.documents.forEach { doc ->
                            doc.reference.set(userUpdates, com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
            }
        } catch (_: Throwable) {}

        // 4. Update request status in Firestore
        try {
            db.collection(TEAMS_COLLECTION).document(code)
                .collection(REJOIN_REQUESTS_SUBCOLLECTION).document(req.id)
                .update(mapOf("status" to "Approved", "updatedAt" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    cachedRejoinRequests[code]?.let { list ->
                        synchronized(list) {
                            val idx = list.indexOfFirst { it.id == req.id }
                            if (idx >= 0) list[idx] = list[idx].copy(status = "Approved")
                        }
                    }
                    onComplete(Result.success(Unit))
                }
                .addOnFailureListener { onComplete(Result.failure(it)) }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Declines a member's re-join request.
     */
    fun declineRejoinRequest(
        teamCode: String,
        requestId: String,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val code = teamCode.trim().uppercase()
        try {
            db.collection(TEAMS_COLLECTION).document(code)
                .collection(REJOIN_REQUESTS_SUBCOLLECTION).document(requestId)
                .update(mapOf("status" to "Declined", "updatedAt" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    cachedRejoinRequests[code]?.let { list ->
                        synchronized(list) {
                            val idx = list.indexOfFirst { it.id == requestId }
                            if (idx >= 0) list[idx] = list[idx].copy(status = "Declined")
                        }
                    }
                    onComplete(Result.success(Unit))
                }
                .addOnFailureListener { onComplete(Result.failure(it)) }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    private fun resolveMemberEmailAndAltUid(
        uid: String,
        email: String = "",
        name: String = "",
        preferences: android.content.SharedPreferences? = null
    ): Pair<String, String> {
        var emailLower = email.trim().lowercase()
        for ((_, roster) in cachedRosters) {
            synchronized(roster) {
                val found = roster.find {
                    (uid.isNotBlank() && it.uid == uid) ||
                    (emailLower.isNotBlank() && it.email.equals(emailLower, ignoreCase = true)) ||
                    (name.isNotBlank() && (it.fullName.equals(name, ignoreCase = true) || it.username.equals(name, ignoreCase = true)))
                }
                if (found != null) {
                    if (emailLower.isBlank() && found.email.isNotBlank()) {
                        emailLower = found.email.trim().lowercase()
                    }
                    if (found.uid.isNotBlank() && found.uid != uid) {
                        return Pair(emailLower, found.uid)
                    }
                }
            }
        }
        if (preferences != null) {
            if (emailLower.isBlank()) {
                val activeUid = preferences.getString("userId", "").orEmpty()
                if (uid.isNotBlank() && uid == activeUid) {
                    emailLower = preferences.getString("email", "").orEmpty().trim().lowercase()
                }
            }
            if (emailLower.isBlank() && uid.isNotBlank()) {
                for ((k, v) in preferences.all) {
                    if (k.startsWith("email_uid_") && v == uid) {
                        emailLower = k.removePrefix("email_uid_").trim().lowercase()
                        break
                    }
                }
            }
            val altUid = if (emailLower.isNotBlank()) {
                preferences.getString("email_uid_$emailLower", "").orEmpty()
            } else ""
            return Pair(emailLower, altUid)
        }
        return Pair(emailLower, "")
    }

    /**
     * Retrieves the unavailable date ranges for a user from in-memory cache, SharedPreferences, or roster cache.
     */
    @Synchronized
    @JvmOverloads
    fun getMemberUnavailableRanges(
        uid: String,
        preferences: android.content.SharedPreferences? = null,
        email: String = "",
        name: String = ""
    ): List<UnavailableRange> {
        val (emailLower, altUid) = resolveMemberEmailAndAltUid(uid, email, name, preferences)
        if (uid.isBlank() && emailLower.isBlank() && name.isBlank()) return emptyList()

        val collected = mutableListOf<UnavailableRange>()
        var hasMemoryCacheEntry = false

        if (uid.isNotBlank() && cachedUserRanges.containsKey(uid)) {
            hasMemoryCacheEntry = true
            cachedUserRanges[uid]?.let { synchronized(it) { collected.addAll(it) } }
        }
        if (altUid.isNotBlank() && altUid != uid && cachedUserRanges.containsKey(altUid)) {
            hasMemoryCacheEntry = true
            cachedUserRanges[altUid]?.let { synchronized(it) { collected.addAll(it) } }
        }
        if (emailLower.isNotBlank() && cachedUserRanges.containsKey("email_$emailLower")) {
            hasMemoryCacheEntry = true
            cachedUserRanges["email_$emailLower"]?.let { synchronized(it) { collected.addAll(it) } }
        }
        if (hasMemoryCacheEntry) {
            return collected.distinctBy { it.id.ifBlank { "${it.startDate}_${it.endDate}_${it.reason}" } }
        }

        if (preferences != null) {
            val keysToCheck = mutableListOf<String>()
            if (uid.isNotBlank()) keysToCheck.add("user_unavailable_ranges_$uid")
            if (altUid.isNotBlank() && altUid != uid) keysToCheck.add("user_unavailable_ranges_$altUid")
            if (emailLower.isNotBlank()) keysToCheck.add("email_unavailable_ranges_$emailLower")

            for (prefKey in keysToCheck) {
                val jsonStr = preferences.getString(prefKey, null)
                if (!jsonStr.isNullOrBlank()) {
                    collected.addAll(UnavailableRange.parseList(jsonStr))
                }
            }
            if (collected.isNotEmpty()) {
                val distinct = collected.distinctBy { it.id.ifBlank { "${it.startDate}_${it.endDate}_${it.reason}" } }
                if (uid.isNotBlank()) {
                    cachedUserRanges[uid] = java.util.Collections.synchronizedList(distinct.toMutableList())
                }
                if (emailLower.isNotBlank()) {
                    cachedUserRanges["email_$emailLower"] = java.util.Collections.synchronizedList(distinct.toMutableList())
                }
                return distinct
            }
        }

        for ((_, roster) in cachedRosters) {
            val found = synchronized(roster) {
                roster.find {
                    (uid.isNotBlank() && it.uid == uid) ||
                    (altUid.isNotBlank() && it.uid == altUid) ||
                    (emailLower.isNotBlank() && it.email.equals(emailLower, ignoreCase = true)) ||
                    (name.isNotBlank() && (it.fullName.equals(name, ignoreCase = true) || it.username.equals(name, ignoreCase = true)))
                }
            }
            if (found != null && found.unavailableRanges.isNotEmpty()) {
                if (uid.isNotBlank()) {
                    cachedUserRanges[uid] = java.util.Collections.synchronizedList(found.unavailableRanges.toMutableList())
                }
                if (emailLower.isNotBlank()) {
                    cachedUserRanges["email_$emailLower"] = java.util.Collections.synchronizedList(found.unavailableRanges.toMutableList())
                }
                return found.unavailableRanges
            }
        }
        return emptyList()
    }

    /**
     * Adds an unavailable date range for a member in local cache, preferences, and Firestore (/users/{uid}).
     */
    @Synchronized
    fun addUnavailableRange(
        uid: String,
        range: UnavailableRange,
        preferences: android.content.SharedPreferences? = null,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        if (uid.isBlank()) {
            onComplete(Result.failure(IllegalArgumentException("UID cannot be blank")))
            return
        }

        val (emailLower, altUid) = resolveMemberEmailAndAltUid(uid, preferences = preferences)
        val current = getMemberUnavailableRanges(uid, preferences, emailLower).toMutableList()
        current.removeAll { it.id == range.id }
        current.add(range)

        cachedUserRanges[uid] = java.util.Collections.synchronizedList(current.toMutableList())
        if (altUid.isNotBlank()) {
            cachedUserRanges[altUid] = java.util.Collections.synchronizedList(current.toMutableList())
        }
        if (emailLower.isNotBlank()) {
            cachedUserRanges["email_$emailLower"] = java.util.Collections.synchronizedList(current.toMutableList())
        }

        val updatedTeamCodes = mutableListOf<String>()
        for ((teamCode, roster) in cachedRosters) {
            var modified = false
            synchronized(roster) {
                for (idx in roster.indices) {
                    val m = roster[idx]
                    if (m.uid == uid || (altUid.isNotBlank() && m.uid == altUid) || (emailLower.isNotBlank() && m.email.equals(emailLower, ignoreCase = true))) {
                        roster[idx] = m.copy(unavailableRanges = current.toList())
                        modified = true
                    }
                }
            }
            if (modified) updatedTeamCodes.add(teamCode)
        }

        if (preferences != null) {
            try {
                val jsonStr = serializeRangesJson(current)
                preferences.edit().apply {
                    putString("user_unavailable_ranges_$uid", jsonStr)
                    if (altUid.isNotBlank()) putString("user_unavailable_ranges_$altUid", jsonStr)
                    if (emailLower.isNotBlank()) putString("email_unavailable_ranges_$emailLower", jsonStr)
                }.apply()
                for (teamCode in updatedTeamCodes) {
                    val list = cachedRosters[teamCode]
                    if (list != null) {
                        saveRosterLocally(teamCode, synchronized(list) { list.toList() }, preferences)
                    }
                }
            } catch (_: Throwable) {}
        }

        try {
            val serialized = current.map { it.toMap() }
            db.collection(USERS_COLLECTION)
                .document(uid)
                .set(mapOf("uid" to uid, "unavailableRanges" to serialized), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    if (emailLower.isNotBlank()) {
                        db.collection(USERS_COLLECTION)
                            .whereEqualTo("email", emailLower)
                            .get()
                            .addOnSuccessListener { snap ->
                                if (snap != null) {
                                    for (doc in snap.documents) {
                                        if (doc.id != uid) {
                                            doc.reference.set(mapOf("unavailableRanges" to serialized), com.google.firebase.firestore.SetOptions.merge())
                                        }
                                    }
                                }
                                onComplete(Result.success(Unit))
                            }
                            .addOnFailureListener { onComplete(Result.success(Unit)) }
                    } else {
                        onComplete(Result.success(Unit))
                    }
                }
                .addOnFailureListener { onComplete(Result.failure(it)) }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Removes an unavailable date range for a member from local cache, preferences, and Firestore.
     */
    @Synchronized
    fun removeUnavailableRange(
        uid: String,
        rangeId: String,
        preferences: android.content.SharedPreferences? = null,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        if (uid.isBlank() || rangeId.isBlank()) {
            onComplete(Result.success(Unit))
            return
        }

        val (emailLower, altUid) = resolveMemberEmailAndAltUid(uid, preferences = preferences)
        val current = getMemberUnavailableRanges(uid, preferences, emailLower).toMutableList()
        current.removeAll { it.id == rangeId }

        cachedUserRanges[uid] = java.util.Collections.synchronizedList(current.toMutableList())
        if (altUid.isNotBlank()) {
            cachedUserRanges[altUid] = java.util.Collections.synchronizedList(current.toMutableList())
        }
        if (emailLower.isNotBlank()) {
            cachedUserRanges["email_$emailLower"] = java.util.Collections.synchronizedList(current.toMutableList())
        }

        val updatedTeamCodes = mutableListOf<String>()
        for ((teamCode, roster) in cachedRosters) {
            var modified = false
            synchronized(roster) {
                for (idx in roster.indices) {
                    val m = roster[idx]
                    if (m.uid == uid || (altUid.isNotBlank() && m.uid == altUid) || (emailLower.isNotBlank() && m.email.equals(emailLower, ignoreCase = true))) {
                        roster[idx] = m.copy(unavailableRanges = current.toList())
                        modified = true
                    }
                }
            }
            if (modified) updatedTeamCodes.add(teamCode)
        }

        if (preferences != null) {
            try {
                val jsonStr = serializeRangesJson(current)
                preferences.edit().apply {
                    putString("user_unavailable_ranges_$uid", jsonStr)
                    if (altUid.isNotBlank()) putString("user_unavailable_ranges_$altUid", jsonStr)
                    if (emailLower.isNotBlank()) putString("email_unavailable_ranges_$emailLower", jsonStr)
                }.apply()
                for (teamCode in updatedTeamCodes) {
                    val list = cachedRosters[teamCode]
                    if (list != null) {
                        saveRosterLocally(teamCode, synchronized(list) { list.toList() }, preferences)
                    }
                }
            } catch (_: Throwable) {}
        }

        try {
            val serialized = current.map { it.toMap() }
            db.collection(USERS_COLLECTION)
                .document(uid)
                .set(mapOf("uid" to uid, "unavailableRanges" to serialized), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    if (emailLower.isNotBlank()) {
                        db.collection(USERS_COLLECTION)
                            .whereEqualTo("email", emailLower)
                            .get()
                            .addOnSuccessListener { snap ->
                                if (snap != null) {
                                    for (doc in snap.documents) {
                                        if (doc.id != uid) {
                                            doc.reference.set(mapOf("unavailableRanges" to serialized), com.google.firebase.firestore.SetOptions.merge())
                                        }
                                    }
                                }
                                onComplete(Result.success(Unit))
                            }
                            .addOnFailureListener { onComplete(Result.success(Unit)) }
                    } else {
                        onComplete(Result.success(Unit))
                    }
                }
                .addOnFailureListener { onComplete(Result.failure(it)) }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Checks team availability for a given target date.
     * Returns Pair(AvailableMembers, UnavailableMembersWithReason).
     */
    @Synchronized
    @JvmOverloads
    fun checkTeamAvailabilityForDate(
        teamCode: String,
        targetDate: String,
        preferences: android.content.SharedPreferences? = null
    ): Pair<List<TeamMember>, List<Pair<TeamMember, String>>> {
        val roster = getCachedRoster(teamCode, preferences)
        val available = mutableListOf<TeamMember>()
        val unavailable = mutableListOf<Pair<TeamMember, String>>()

        for (member in roster) {
            val ranges = (member.unavailableRanges + getMemberUnavailableRanges(member.uid, preferences, member.email, member.fullName))
                .distinctBy { it.id.ifBlank { "${it.startDate}_${it.endDate}_${it.reason}" } }
            val conflict = ranges.find { it.includes(targetDate) }
            if (conflict != null) {
                unavailable.add(Pair(member, conflict.reason.ifBlank { "Unavailable" }))
            } else {
                available.add(member)
            }
        }
        return Pair(available, unavailable)
    }

    // ===============================================================
    // Worship Services Management & Caching
    // ===============================================================

    private fun resolveFallbackCoverImageUrl(
        teamCode: String,
        serviceId: String,
        serviceTitle: String,
        preferences: android.content.SharedPreferences?
    ): String {
        val code = teamCode.trim().uppercase()
        cachedServices[code]?.let { mem ->
            val found = synchronized(mem) {
                mem.find { (serviceId.isNotBlank() && it.id == serviceId) || (serviceTitle.isNotBlank() && it.title.equals(serviceTitle, true)) }
            }
            if (found != null && found.coverImageUrl.isNotBlank()) return found.coverImageUrl
        }
        val prefs = preferences ?: return ""
        try {
            val draftStr = prefs.getString("leaderDraft", null)
            if (!draftStr.isNullOrBlank()) {
                val draftObj = org.json.JSONObject(draftStr)
                val dId = draftObj.optString("id")
                val dTitle = draftObj.optString("title")
                val dCover = draftObj.optString("coverImageUrl")
                if (dCover.isNotBlank() && ((serviceId.isNotBlank() && dId == serviceId) || (serviceTitle.isNotBlank() && dTitle.equals(serviceTitle, true)))) {
                    return dCover
                }
            }
        } catch (_: Throwable) {}
        try {
            val lStr = prefs.getString("leaderServices", null)
            if (!lStr.isNullOrBlank()) {
                val arr = org.json.JSONArray(lStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val oId = obj.optString("id")
                    val oTitle = obj.optString("title")
                    val oCover = obj.optString("coverImageUrl")
                    if (oCover.isNotBlank() && ((serviceId.isNotBlank() && oId == serviceId) || (serviceTitle.isNotBlank() && oTitle.equals(serviceTitle, true)))) {
                        return oCover
                    }
                }
            }
        } catch (_: Throwable) {}
        return ""
    }
    // ===============================================================

    /**
     * Saves or updates a service in the local in-memory cache and SharedPreferences.
     */
    @Synchronized
    fun saveServicesLocally(
        teamCode: String,
        services: List<ServiceData>,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return
        services.forEach { saveServiceLocally(code, it, preferences) }
    }

    fun saveServiceLocally(
        teamCode: String,
        service: ServiceData,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || service.id.isBlank()) return
        val list = cachedServices.getOrPut(code) { java.util.Collections.synchronizedList(mutableListOf()) }
        synchronized(list) {
            val index = list.indexOfFirst { it.id == service.id }
            if (index >= 0) {
                list[index] = service
            } else {
                list.add(service)
            }
            list.sortBy { it.date }
        }

        preferences?.let { prefs ->
            try {
                val array = org.json.JSONArray()
                synchronized(list) {
                    list.forEach { item ->
                        val obj = org.json.JSONObject()
                        item.toMap().forEach { (k, v) ->
                            if (v is List<*>) {
                                val childArray = org.json.JSONArray()
                                v.forEach { child ->
                                    if (child is Map<*, *>) childArray.put(org.json.JSONObject(child))
                                    else childArray.put(child)
                                }
                                obj.put(k, childArray)
                            } else if (v is Map<*, *>) {
                                obj.put(k, org.json.JSONObject(v))
                            } else {
                                obj.put(k, v)
                            }
                        }
                        array.put(obj)
                    }
                }
                prefs.edit().putString("team_services_$code", array.toString()).apply()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Retrieves cached services for a team from memory or SharedPreferences.
     */
    @Synchronized
    fun getCachedServices(
        teamCode: String,
        preferences: android.content.SharedPreferences? = null
    ): List<ServiceData> {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return emptyList()

        cachedServices[code]?.let { list ->
            synchronized(list) {
                if (list.isNotEmpty()) return list.toList()
            }
        }

        if (preferences != null) {
            try {
                var raw = preferences.getString("team_services_$code", null)
                if (raw.isNullOrBlank()) {
                    raw = preferences.getString("leaderServices", "[]")
                }
                val array = org.json.JSONArray(raw.orEmpty().ifBlank { "[]" })
                val list = mutableListOf<ServiceData>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val map = mutableMapOf<String, Any?>()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = obj.get(key)
                        if (value is org.json.JSONArray) {
                            val childList = mutableListOf<Any?>()
                            for (j in 0 until value.length()) {
                                val item = value.get(j)
                                if (item is org.json.JSONObject) {
                                    val childMap = mutableMapOf<String, Any?>()
                                    val childKeys = item.keys()
                                    while (childKeys.hasNext()) {
                                        val ck = childKeys.next()
                                        childMap[ck] = item.get(ck)
                                    }
                                    childList.add(childMap)
                                } else {
                                    childList.add(item)
                                }
                            }
                            map[key] = childList
                        } else if (value is org.json.JSONObject) {
                            val childMap = mutableMapOf<String, Any?>()
                            val childKeys = value.keys()
                            while (childKeys.hasNext()) {
                                val ck = childKeys.next()
                                childMap[ck] = value.get(ck)
                            }
                            map[key] = childMap
                        } else {
                            map[key] = value
                        }
                    }
                    var srv = ServiceData.fromMap(map)
                    if (srv.coverImageUrl.isBlank()) {
                        val fallbackCover = resolveFallbackCoverImageUrl(code, srv.id, srv.title, preferences)
                        if (fallbackCover.isNotBlank()) {
                            srv = srv.copy(coverImageUrl = fallbackCover)
                        }
                    }
                    if (srv.id.isNotBlank()) list.add(srv)
                }
                if (list.isNotEmpty()) {
                    val memList = cachedServices.getOrPut(code) { java.util.Collections.synchronizedList(mutableListOf()) }
                    synchronized(memList) {
                        memList.clear()
                        memList.addAll(list)
                        memList.sortBy { it.date }
                    }
                    return list
                }
            } catch (_: Throwable) {}
        }
        return emptyList()
    }

    /**
     * Retrieves services for a team with 15s throttling and offline caching.
     * When isLeader is false, filters out Draft services.
     */
    fun getServices(
        teamCode: String,
        isLeader: Boolean = true,
        preferences: android.content.SharedPreferences? = null,
        onComplete: (Result<List<ServiceData>>) -> Unit
    ) {
        getServices(teamCode, isLeader, force = false, preferences, onComplete)
    }

    fun getServices(
        teamCode: String,
        isLeader: Boolean = true,
        force: Boolean = false,
        preferences: android.content.SharedPreferences? = null,
        onComplete: (Result<List<ServiceData>>) -> Unit
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) {
            onComplete(Result.success(emptyList()))
            return
        }

        val cached = getCachedServices(code, preferences)
        val filteredCached = if (isLeader) cached else cached.filter { it.status == "Published" }

        val now = System.currentTimeMillis()
        val lastFetch = lastServicesFetchTimes[code] ?: 0L
        val inFlight = servicesInFlight[code] == true

        if (!force && (inFlight || (now - lastFetch < 15_000L && filteredCached.isNotEmpty()))) {
            onComplete(Result.success(filteredCached))
            return
        }

        servicesInFlight[code] = true
        var finished = false
        fun finishOnce(result: Result<List<ServiceData>>) {
            synchronized(this) {
                if (finished) return
                finished = true
                servicesInFlight[code] = false
            }
            onComplete(result)
        }

        try {
            val query = db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SERVICES_SUBCOLLECTION)

            val finalQuery = if (isLeader) query else query.whereEqualTo("status", "Published")

            finalQuery.get()
                .addOnSuccessListener { snapshot ->
                    lastServicesFetchTimes[code] = System.currentTimeMillis()
                    val remoteList = mutableListOf<ServiceData>()
                    for (doc in snapshot.documents) {
                        val data = doc.data ?: continue
                        var srv = ServiceData.fromMap(data).copy(id = doc.id)
                        if (srv.coverImageUrl.isBlank()) {
                            val fallbackCover = resolveFallbackCoverImageUrl(code, srv.id, srv.title, preferences)
                            if (fallbackCover.isNotBlank()) {
                                srv = srv.copy(coverImageUrl = fallbackCover)
                                if (isLeader) {
                                    try { doc.reference.set(mapOf("coverImageUrl" to fallbackCover), com.google.firebase.firestore.SetOptions.merge()) } catch (_: Throwable) {}
                                }
                            }
                        }
                        remoteList.add(srv)
                    }
                    val memList = cachedServices.getOrPut(code) { java.util.Collections.synchronizedList(mutableListOf()) }
                    synchronized(memList) {
                        if (isLeader) {
                            memList.clear()
                            memList.addAll(remoteList)
                        } else {
                            // For members, prune published services that are no longer published or were deleted remotely
                            memList.removeAll { it.status == "Published" && remoteList.none { r -> r.id == it.id } }
                            for (srv in remoteList) {
                                val idx = memList.indexOfFirst { it.id == srv.id }
                                if (idx >= 0) memList[idx] = srv else memList.add(srv)
                            }
                        }
                        memList.sortBy { it.date }
                    }
                    preferences?.let { prefs ->
                        try {
                            val array = org.json.JSONArray()
                            synchronized(memList) {
                                memList.forEach { item ->
                                    val obj = org.json.JSONObject()
                                    item.toMap().forEach { (k, v) ->
                                        if (v is List<*>) {
                                            val childArray = org.json.JSONArray()
                                            v.forEach { child ->
                                                if (child is Map<*, *>) childArray.put(org.json.JSONObject(child))
                                                else childArray.put(child)
                                            }
                                            obj.put(k, childArray)
                                        } else if (v is Map<*, *>) {
                                            obj.put(k, org.json.JSONObject(v))
                                        } else {
                                            obj.put(k, v)
                                        }
                                    }
                                    array.put(obj)
                                }
                            }
                            prefs.edit().putString("team_services_$code", array.toString()).apply()
                        } catch (_: Throwable) {}
                    }
                    val deliver = if (isLeader) {
                        getCachedServices(code, preferences)
                    } else {
                        getCachedServices(code, preferences).filter { it.status == "Published" }
                    }
                    finishOnce(Result.success(deliver.ifEmpty { filteredCached }))
                }
                .addOnFailureListener {
                    finishOnce(Result.success(filteredCached))
                }
        } catch (_: Throwable) {
            finishOnce(Result.success(filteredCached))
        }
    }

    /**
     * Saves a service to local cache and synchronizes to Cloud Firestore.
     */
    fun saveService(
        teamCode: String,
        service: ServiceData,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<ServiceData>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || service.id.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("teamCode and service.id cannot be blank")))
            return
        }

        saveServiceLocally(code, service, preferences)

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SERVICES_SUBCOLLECTION)
                .document(service.id)
                .set(service.toMap(), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    onComplete?.invoke(Result.success(service))
                }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "Firestore saveService failed: ${err.message}", err)
                    onComplete?.invoke(Result.failure(err))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "Firestore saveService exception: ${e.message}", e)
            onComplete?.invoke(Result.failure(e))
        }
    }

    /**
     * Deletes a service from local cache and Cloud Firestore.
     */
    fun deleteService(
        teamCode: String,
        serviceId: String,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<Boolean>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || serviceId.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("teamCode and serviceId cannot be blank")))
            return
        }

        cachedServices[code]?.let { list ->
            synchronized(list) {
                list.removeAll { it.id == serviceId }
            }
        }

        preferences?.let { prefs ->
            try {
                val cached = getCachedServices(code, prefs).filter { it.id != serviceId }
                val array = org.json.JSONArray()
                cached.forEach { item ->
                    val obj = org.json.JSONObject()
                    item.toMap().forEach { (k, v) ->
                        if (v is List<*>) {
                            val childArray = org.json.JSONArray()
                            v.forEach { child ->
                                if (child is Map<*, *>) childArray.put(org.json.JSONObject(child))
                                else childArray.put(child)
                            }
                            obj.put(k, childArray)
                        } else if (v is Map<*, *>) {
                            obj.put(k, org.json.JSONObject(v))
                        } else {
                            obj.put(k, v)
                        }
                    }
                    array.put(obj)
                }
                prefs.edit().putString("team_services_$code", array.toString()).apply()
            } catch (_: Throwable) {}
        }

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SERVICES_SUBCOLLECTION)
                .document(serviceId)
                .delete()
                .addOnSuccessListener { onComplete?.invoke(Result.success(true)) }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "Firestore deleteService failed: ${err.message}", err)
                    onComplete?.invoke(Result.failure(err))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "Firestore deleteService exception: ${e.message}", e)
            onComplete?.invoke(Result.failure(e))
        }
    }

    // ===============================================================
    // Songs Catalog Management (Group 4)
    // ===============================================================

    /**
     * Default seed worship catalog loaded for new teams or offline cold-start.
     */
    fun getDefaultSeedSongs(teamCode: String): List<SongData> {
        val code = teamCode.trim().uppercase()
        return listOf(
            SongData(
                id = "seed_praise",
                teamCode = code,
                title = "Praise",
                artist = "Elevation Worship",
                key = "G",
                bpm = 127,
                sheet = "[Verse 1]\n[G]Let everything that has breath\nPraise the Lord, [C]praise the Lord\n[G]I'll praise in the valley, praise on the mountain\n[C]I'll praise when I'm sure, praise when I'm doubting\n\n[Chorus]\n[G]Praise the Lord, oh my soul\n[Em]Praise the Lord, oh my soul\n[C]I won't be quiet, my [D]rock and salvation\n[G]Praise the Lord!",
                lyrics = "Let everything that has breath\nPraise the Lord, praise the Lord\nI'll praise in the valley, praise on the mountain\nI'll praise when I'm sure, praise when I'm doubting\n\nPraise the Lord, oh my soul\nPraise the Lord, oh my soul\nI won't be quiet, my rock and salvation\nPraise the Lord!",
                referenceUrl = "https://www.youtube.com/watch?v=f2OXgn6mHHY"
            ),
            SongData(
                id = "seed_trust_in_god",
                teamCode = code,
                title = "Trust in God",
                artist = "Elevation Worship",
                key = "C",
                bpm = 74,
                sheet = "[Verse 1]\n[C]Blessed assurance, [F]Jesus is [C]mine\n[Am]Oh what a [G]foretaste of [F]glory divine\n[C]Heir of salvation, [F]purchase of [C]God\n[Am]Born of His [G]Spirit, washed [F]in His blood\n\n[Chorus]\n[C]I trust in God, my Savior\n[Am]The One who will never fail\n[F]He will never fail, [G]He will never [C]fail",
                lyrics = "Blessed assurance, Jesus is mine\nOh what a foretaste of glory divine\nHeir of salvation, purchase of God\nBorn of His Spirit, washed in His blood\n\nI trust in God, my Savior\nThe One who will never fail\nHe will never fail, He will never fail",
                referenceUrl = "https://www.youtube.com/watch?v=y7495tY_k_8"
            ),
            SongData(
                id = "seed_way_maker",
                teamCode = code,
                title = "Way Maker",
                artist = "Sinach",
                key = "E",
                bpm = 68,
                sheet = "[Verse 1]\n[E]You are here, moving in our midst\n[B]I worship You, I worship You\n[C#m]You are here, working in this place\n[A]I worship You, I worship You\n\n[Chorus]\n[E]Way maker, miracle worker, [B]promise keeper\n[C#m]Light in the darkness, my God, [A]that is who You [E]are",
                lyrics = "You are here, moving in our midst\nI worship You, I worship You\nYou are here, working in this place\nI worship You, I worship You\n\nWay maker, miracle worker, promise keeper\nLight in the darkness, my God, that is who You are",
                referenceUrl = "https://www.youtube.com/watch?v=n4XWfwLHeLM"
            ),
            SongData(
                id = "seed_gratitude",
                teamCode = code,
                title = "Gratitude",
                artist = "Brandon Lake",
                key = "B",
                bpm = 78,
                sheet = "[Verse 1]\n[B]All my words fall short\n[E]I got nothing new\n[G#m]How could I express\n[F#]All my gratitude\n\n[Chorus]\n[B]So I throw up my hands and praise You again and again\n[G#m]'Cause all that I have is a hallelujah, [E]hallelujah\n[B]And I know it's not much, but I've nothing else fit for a King\n[G#m]Except for a heart singing hallelujah, [F#]hallelujah",
                lyrics = "All my words fall short\nI got nothing new\nHow could I express\nAll my gratitude\n\nSo I throw up my hands and praise You again and again\n'Cause all that I have is a hallelujah, hallelujah\nAnd I know it's not much, but I've nothing else fit for a King\nExcept for a heart singing hallelujah, hallelujah",
                referenceUrl = "https://www.youtube.com/watch?v=dQdfs5S6jyA"
            ),
            SongData(
                id = "seed_goodness_of_god",
                teamCode = code,
                title = "Goodness of God",
                artist = "Bethel Music",
                key = "Ab",
                bpm = 70,
                sheet = "[Verse 1]\n[Ab]I love You, Lord, for Your [Db]mercy never [Ab]fails me\n[Eb]All my [Fm]days, I've been [Db]held in Your [Eb]hands\nFrom the moment that I [Fm]wake up until I [Db]lay my [Ab]head\n[Db]I will sing of the [Eb]goodness of [Ab]God\n\n[Chorus]\n[Db]All my life You have been faithful\n[Ab]All my life You have been so, so good\n[Db]With every breath that I am [Ab]able\n[Db]I will sing of the [Eb]goodness of [Ab]God",
                lyrics = "I love You, Lord, for Your mercy never fails me\nAll my days, I've been held in Your hands\nFrom the moment that I wake up until I lay my head\nI will sing of the goodness of God\n\nAll my life You have been faithful\nAll my life You have been so, so good\nWith every breath that I am able\nI will sing of the goodness of God",
                referenceUrl = "https://www.youtube.com/watch?v=-f4MUKEVKaU"
            ),
            SongData(
                id = "seed_build_my_life",
                teamCode = code,
                title = "Build My Life",
                artist = "Pat Barrett",
                key = "G",
                bpm = 68,
                sheet = "[Verse 1]\n[G]Worthy of every song we could [C]ever sing\n[G]Worthy of all the praise we could [C]ever bring\n[G]Worthy of every breath we could [C]ever breathe\nWe live for [Am]You, [D]we live for [G]You\n\n[Chorus]\n[C]Holy, there is no one like [Am]You\nThere is none beside [G]You\nOpen up my eyes in [Em]wonder\nAnd [C]show me who You are and [Am]fill me\nWith Your heart and lead [G]me\nIn Your love to those [Em]around me",
                lyrics = "Worthy of every song we could ever sing\nWorthy of all the praise we could ever bring\nWorthy of every breath we could ever breathe\nWe live for You, we live for You\n\nHoly, there is no one like You\nThere is none beside You\nOpen up my eyes in wonder\nAnd show me who You are and fill me\nWith Your heart and lead me\nIn Your love to those around me",
                referenceUrl = "https://www.youtube.com/watch?v=Z32HiSuUY8o"
            ),
            SongData(
                id = "seed_amazing_grace",
                teamCode = code,
                title = "Amazing Grace",
                artist = "John Newton",
                key = "G",
                bpm = 72,
                sheet = "[Verse 1]\n[G]Amazing grace, how [C]sweet the [G]sound\nThat saved a [D]wretch like me\n[G]I once was lost, but [C]now am [G]found\nWas blind, but [D]now I [G]see\n\n[Verse 2]\n'Twas [G]grace that taught my [C]heart to [G]fear\nAnd grace my [D]fears relieved\nHow [G]precious did that [C]grace ap[G]pear\nThe hour I [D]first be[G]lieved",
                lyrics = "Amazing grace, how sweet the sound\nThat saved a wretch like me\nI once was lost, but now am found\nWas blind, but now I see\n\n'Twas grace that taught my heart to fear\nAnd grace my fears relieved\nHow precious did that grace appear\nThe hour I first believed",
                referenceUrl = "https://www.youtube.com/watch?v=Jbe7OruLk8I"
            )
        )
    }

    private fun getDeletedSongKeys(teamCode: String, preferences: android.content.SharedPreferences?): Set<String> {
        val code = teamCode.trim().uppercase()
        val raw = preferences?.getString("deleted_songs_$code", "[]").orEmpty()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it).trim().lowercase() }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    private fun markSongDeleted(teamCode: String, songId: String, songTitle: String = "", preferences: android.content.SharedPreferences?) {
        val code = teamCode.trim().uppercase()
        val current = getDeletedSongKeys(code, preferences).toMutableSet()
        if (songId.isNotBlank()) current.add(songId.trim().lowercase())
        if (songTitle.isNotBlank()) current.add(songTitle.trim().lowercase())
        preferences?.edit()?.putString("deleted_songs_$code", org.json.JSONArray(current).toString())?.apply()
    }

    /**
     * Retrieves cached songs for the team, falling back to SharedPreferences and seed catalog.
     */
    fun getCachedSongs(
        teamCode: String,
        preferences: android.content.SharedPreferences? = null
    ): List<SongData> {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return emptyList()

        val defaultSeeds = getDefaultSeedSongs(code)
        val deletedKeys = getDeletedSongKeys(code, preferences)
        val activeSeeds = defaultSeeds.filter { it.id.lowercase() !in deletedKeys && it.title.trim().lowercase() !in deletedKeys }

        val inMemory = cachedSongs[code]
        if (inMemory != null && inMemory.isNotEmpty()) {
            val mergedMem = synchronized(inMemory) { inMemory.filter { it.id.lowercase() !in deletedKeys && it.title.trim().lowercase() !in deletedKeys }.toMutableList() }
            activeSeeds.forEach { seed ->
                if (mergedMem.none { it.title.equals(seed.title, ignoreCase = true) }) {
                    mergedMem.add(seed)
                }
            }
            return mergedMem
        }

        preferences?.getString("team_songs_$code", null)?.let { jsonStr ->
            if (jsonStr.isNotBlank() && jsonStr != "[]") {
                try {
                    val array = org.json.JSONArray(jsonStr)
                    val list = mutableListOf<SongData>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val map = mutableMapOf<String, Any?>()
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            map[k] = obj.opt(k)
                        }
                        val parsed = SongData.fromMap(map)
                        if (parsed.id.lowercase() !in deletedKeys && parsed.title.trim().lowercase() !in deletedKeys) {
                            // If seed song was saved previously without referenceUrl or updated chords, backfill metadata
                            val seedMatch = defaultSeeds.find { it.title.equals(parsed.title, ignoreCase = true) }
                            val enriched = if (seedMatch != null && parsed.referenceUrl.isBlank()) {
                                parsed.copy(
                                    referenceUrl = seedMatch.referenceUrl,
                                    key = if (parsed.key.isBlank() || parsed.key == "C") seedMatch.key else parsed.key,
                                    bpm = if (parsed.bpm == 74) seedMatch.bpm else parsed.bpm,
                                    sheet = if (parsed.sheet.isBlank() || parsed.sheet.contains("Add your licensed")) seedMatch.sheet else parsed.sheet,
                                    lyrics = if (parsed.lyrics.isBlank()) seedMatch.lyrics else parsed.lyrics
                                )
                            } else parsed
                            list.add(enriched)
                        }
                    }
                    val merged = mutableListOf<SongData>()
                    merged.addAll(list)
                    activeSeeds.forEach { seed ->
                        if (merged.none { it.title.equals(seed.title, ignoreCase = true) }) {
                            merged.add(seed)
                        }
                    }
                    if (merged.isNotEmpty()) {
                        cachedSongs[code] = java.util.Collections.synchronizedList(merged)
                        return merged
                    }
                } catch (_: Throwable) {}
            }
        }

        cachedSongs[code] = java.util.Collections.synchronizedList(activeSeeds.toMutableList())
        return activeSeeds
    }

    /**
     * Saves a song into local in-memory cache and SharedPreferences.
     */
    fun saveSongLocally(
        teamCode: String,
        song: SongData,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || song.id.isBlank()) return

        val defaultSeeds = getDefaultSeedSongs(code)
        val deletedKeys = getDeletedSongKeys(code, preferences)
        val activeSeeds = defaultSeeds.filter { it.id.lowercase() !in deletedKeys && it.title.trim().lowercase() !in deletedKeys }

        val list = cachedSongs.getOrPut(code) {
            java.util.Collections.synchronizedList(getCachedSongs(code, preferences).toMutableList())
        }
        synchronized(list) {
            val idx = list.indexOfFirst { it.id == song.id || (it.title.isNotBlank() && it.title.equals(song.title, ignoreCase = true)) }
            if (idx >= 0) list[idx] = song
            else list.add(song)
            activeSeeds.forEach { seed ->
                if (list.none { it.title.equals(seed.title, ignoreCase = true) }) {
                    list.add(seed)
                }
            }
            list.sortBy { it.title.lowercase() }
        }

        preferences?.let { prefs ->
            try {
                val array = org.json.JSONArray()
                synchronized(list) {
                    list.forEach { item ->
                        val obj = org.json.JSONObject()
                        item.toMap().forEach { (k, v) -> obj.put(k, v) }
                        array.put(obj)
                    }
                }
                prefs.edit().putString("team_songs_$code", array.toString()).apply()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Removes a song from local cache and SharedPreferences.
     */
    fun removeSongLocally(
        teamCode: String,
        songId: String,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || songId.isBlank()) return

        cachedSongs[code]?.let { list ->
            synchronized(list) {
                list.removeAll { it.id == songId }
            }
        }

        preferences?.let { prefs ->
            try {
                val cached = getCachedSongs(code, prefs).filter { it.id != songId }
                val array = org.json.JSONArray()
                cached.forEach { item ->
                    val obj = org.json.JSONObject()
                    item.toMap().forEach { (k, v) -> obj.put(k, v) }
                    array.put(obj)
                }
                prefs.edit().putString("team_songs_$code", array.toString()).apply()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Saves a list of songs into local cache and SharedPreferences.
     */
    fun saveSongsLocally(
        teamCode: String,
        songs: List<SongData>,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return

        val sorted = songs.sortedBy { it.title.lowercase() }
        cachedSongs[code] = java.util.Collections.synchronizedList(sorted.toMutableList())

        preferences?.let { prefs ->
            try {
                val array = org.json.JSONArray()
                sorted.forEach { item ->
                    val obj = org.json.JSONObject()
                    item.toMap().forEach { (k, v) -> obj.put(k, v) }
                    array.put(obj)
                }
                prefs.edit().putString("team_songs_$code", array.toString()).apply()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Fetches songs from Firestore with 15-second debounce and offline cache fallback.
     */
    fun getSongs(
        teamCode: String,
        forceRefresh: Boolean = false,
        preferences: android.content.SharedPreferences? = null,
        onResult: (Result<List<SongData>>) -> Unit = {}
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) {
            onResult(Result.success(emptyList()))
            return
        }

        val cached = getCachedSongs(code, preferences)

        val now = System.currentTimeMillis()
        val lastFetch = lastSongsFetchTimes[code] ?: 0L
        if (!forceRefresh && (now - lastFetch < 15_000L)) {
            onResult(Result.success(cached))
            return
        }

        if (songsInFlight[code] == true) {
            onResult(Result.success(cached))
            return
        }
        songsInFlight[code] = true

        var hasFinished = false
        fun finishOnce(result: Result<List<SongData>>) {
            if (!hasFinished) {
                hasFinished = true
                songsInFlight[code] = false
                onResult(result)
            }
        }

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SONGS_SUBCOLLECTION)
                .orderBy("title")
                .get()
                .addOnSuccessListener { snapshot ->
                    lastSongsFetchTimes[code] = System.currentTimeMillis()
                    val fetched = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { SongData.fromMap(it) }
                    }
                    val defaultSeeds = getDefaultSeedSongs(code)
                    val deletedKeys = getDeletedSongKeys(code, preferences)
                    val activeSeeds = defaultSeeds.filter { it.id.lowercase() !in deletedKeys && it.title.trim().lowercase() !in deletedKeys }
                    val merged = mutableListOf<SongData>()
                    merged.addAll(fetched.filter { it.id.lowercase() !in deletedKeys && it.title.trim().lowercase() !in deletedKeys })
                    activeSeeds.forEach { seed ->
                        if (merged.none { it.title.equals(seed.title, ignoreCase = true) }) {
                            merged.add(seed)
                        }
                    }
                    saveSongsLocally(code, merged, preferences)
                    finishOnce(Result.success(merged))
                }
                .addOnFailureListener {
                    finishOnce(Result.success(cached))
                }
        } catch (_: Throwable) {
            finishOnce(Result.success(cached))
        }
    }

    /**
     * Saves a song to local cache and synchronizes to Cloud Firestore.
     */
    fun saveSong(
        teamCode: String,
        song: SongData,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<SongData>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || song.id.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("teamCode and song.id cannot be blank")))
            return
        }

        saveSongLocally(code, song, preferences)

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SONGS_SUBCOLLECTION)
                .document(song.id)
                .set(song.toMap(), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    onComplete?.invoke(Result.success(song))
                }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "Firestore saveSong failed: ${err.message}", err)
                    onComplete?.invoke(Result.failure(err))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "Firestore saveSong exception: ${e.message}", e)
            onComplete?.invoke(Result.failure(e))
        }
    }

    /**
     * Deletes a song from local cache and Cloud Firestore.
     */
    fun deleteSong(
        teamCode: String,
        songId: String,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<Boolean>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || songId.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("teamCode and songId cannot be blank")))
            return
        }

        val targetTitle = getCachedSongs(code, preferences).find { it.id == songId }?.title.orEmpty()
        markSongDeleted(code, songId, targetTitle, preferences)
        removeSongLocally(code, songId, preferences)

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SONGS_SUBCOLLECTION)
                .document(songId)
                .delete()
                .addOnSuccessListener { onComplete?.invoke(Result.success(true)) }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "Firestore deleteSong failed: ${err.message}", err)
                    onComplete?.invoke(Result.failure(err))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "Firestore deleteSong exception: ${e.message}", e)
            onComplete?.invoke(Result.failure(e))
        }
    }

    // ===============================================================
    // Group 5: Team Announcements & Urgent Alerts
    // ===============================================================

    /**
     * Retrieves cached announcements for a team, sorted with newest first (createdAt descending).
     */
    fun getCachedAnnouncements(
        teamCode: String,
        preferences: android.content.SharedPreferences? = null
    ): List<AnnouncementData> {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return emptyList()

        val inMem = cachedAnnouncements[code]
        if (!inMem.isNullOrEmpty()) {
            return inMem.sortedByDescending { it.createdAt }
        }

        preferences?.let { prefs ->
            val json = prefs.getString("cached_announcements_$code", null)
            if (!json.isNullOrBlank()) {
                try {
                    val array = org.json.JSONArray(json)
                    val list = mutableListOf<AnnouncementData>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val map = mutableMapOf<String, Any?>()
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            map[k] = obj.opt(k)
                        }
                        list.add(AnnouncementData.fromMap(map))
                    }
                    val sorted = list.sortedByDescending { it.createdAt }
                    cachedAnnouncements[code] = java.util.Collections.synchronizedList(sorted.toMutableList())
                    return sorted
                } catch (_: Throwable) {}
            }
        }

        return emptyList()
    }

    /**
     * Saves a list of announcements into local cache and SharedPreferences.
     */
    fun saveAnnouncementsLocally(
        teamCode: String,
        announcements: List<AnnouncementData>,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return

        val sorted = announcements.sortedByDescending { it.createdAt }
        cachedAnnouncements[code] = java.util.Collections.synchronizedList(sorted.toMutableList())

        preferences?.let { prefs ->
            try {
                val array = org.json.JSONArray()
                sorted.forEach { item ->
                    val obj = org.json.JSONObject()
                    item.toMap().forEach { (k, v) -> obj.put(k, v) }
                    array.put(obj)
                }
                prefs.edit().putString("cached_announcements_$code", array.toString()).apply()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Saves or updates a single announcement in the local cache.
     */
    fun saveAnnouncementLocally(
        teamCode: String,
        announcement: AnnouncementData,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || announcement.id.isBlank()) return

        val current = getCachedAnnouncements(code, preferences).filter { it.id != announcement.id }.toMutableList()
        current.add(announcement)
        saveAnnouncementsLocally(code, current, preferences)
    }

    /**
     * Removes an announcement from the local cache and SharedPreferences.
     */
    fun removeAnnouncementLocally(
        teamCode: String,
        announcementId: String,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || announcementId.isBlank()) return

        val current = getCachedAnnouncements(code, preferences).filter { it.id != announcementId }
        saveAnnouncementsLocally(code, current, preferences)
    }

    /**
     * Fetches announcements from Cloud Firestore with 15-second debounce and offline cache fallback.
     */
    fun getAnnouncements(
        teamCode: String,
        forceRefresh: Boolean = false,
        preferences: android.content.SharedPreferences? = null,
        onResult: (Result<List<AnnouncementData>>) -> Unit = {}
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) {
            onResult(Result.success(emptyList()))
            return
        }

        val cached = getCachedAnnouncements(code, preferences)
        val now = System.currentTimeMillis()
        val lastFetch = lastAnnouncementsFetchTimes[code] ?: 0L
        val isThrottled = !forceRefresh && (now - lastFetch < 15_000L) && cached.isNotEmpty()

        if (isThrottled) {
            onResult(Result.success(cached))
            return
        }

        if (announcementsInFlight[code] == true) {
            onResult(Result.success(cached))
            return
        }

        announcementsInFlight[code] = true
        var delivered = false
        fun finishOnce(result: Result<List<AnnouncementData>>) {
            announcementsInFlight[code] = false
            if (!delivered) {
                delivered = true
                onResult(result)
            }
        }

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(ANNOUNCEMENTS_SUBCOLLECTION)
                .limit(50)
                .get()
                .addOnSuccessListener { snapshot ->
                    lastAnnouncementsFetchTimes[code] = System.currentTimeMillis()
                    val remote = mutableListOf<AnnouncementData>()
                    if (snapshot != null && !snapshot.isEmpty) {
                        for (doc in snapshot.documents) {
                            val data = doc.data
                            if (data != null) {
                                remote.add(AnnouncementData.fromMap(data))
                            }
                        }
                    }
                    val sorted = remote.sortedByDescending { it.createdAt }
                    saveAnnouncementsLocally(code, sorted, preferences)
                    finishOnce(Result.success(sorted))
                }
                .addOnFailureListener {
                    finishOnce(Result.success(cached))
                }
        } catch (_: Throwable) {
            finishOnce(Result.success(cached))
        }
    }

    /**
     * Saves an announcement to local cache and synchronizes to Cloud Firestore.
     */
    fun saveAnnouncement(
        teamCode: String,
        announcement: AnnouncementData,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<AnnouncementData>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || announcement.id.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("teamCode and announcement.id cannot be blank")))
            return
        }

        saveAnnouncementLocally(code, announcement, preferences)

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(ANNOUNCEMENTS_SUBCOLLECTION)
                .document(announcement.id)
                .set(announcement.toMap(), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    onComplete?.invoke(Result.success(announcement))
                }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "Firestore saveAnnouncement failed: ${err.message}", err)
                    onComplete?.invoke(Result.failure(err))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "Firestore saveAnnouncement exception: ${e.message}", e)
            onComplete?.invoke(Result.failure(e))
        }
    }

    /**
     * Deletes an announcement from local cache and Cloud Firestore.
     */
    fun deleteAnnouncement(
        teamCode: String,
        announcementId: String,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<Boolean>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || announcementId.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("teamCode and announcementId cannot be blank")))
            return
        }

        removeAnnouncementLocally(code, announcementId, preferences)

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(ANNOUNCEMENTS_SUBCOLLECTION)
                .document(announcementId)
                .delete()
                .addOnSuccessListener { onComplete?.invoke(Result.success(true)) }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "Firestore deleteAnnouncement failed: ${err.message}", err)
                    onComplete?.invoke(Result.failure(err))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "Firestore deleteAnnouncement exception: ${e.message}", e)
            onComplete?.invoke(Result.failure(e))
        }
    }

    /**
     * Saves a list of substitutions into local in-memory cache and SharedPreferences.
     */
    fun saveSubstitutionsLocally(
        teamCode: String,
        substitutions: List<SubstitutionData>,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return

        val sorted = substitutions.sortedByDescending { it.createdAt }
        cachedSubstitutions[code] = java.util.Collections.synchronizedList(sorted.toMutableList())

        preferences?.let { prefs ->
            try {
                val array = org.json.JSONArray()
                sorted.forEach { item ->
                    val obj = org.json.JSONObject()
                    item.toMap().forEach { (k, v) -> obj.put(k, v) }
                    array.put(obj)
                }
                prefs.edit().putString("cached_substitutions_$code", array.toString()).apply()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Saves or updates a single substitution in the local cache.
     */
    fun saveSubstitutionLocally(
        teamCode: String,
        substitution: SubstitutionData,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || substitution.id.isBlank()) return

        val current = getCachedSubstitutions(code, preferences).filter { it.id != substitution.id }.toMutableList()
        current.add(substitution)
        saveSubstitutionsLocally(code, current, preferences)
    }

    /**
     * Updates the status of a cached substitution locally.
     */
    fun updateSubstitutionStatusLocally(
        teamCode: String,
        substitutionId: String,
        status: String,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || substitutionId.isBlank()) return

        val current = getCachedSubstitutions(code, preferences).toMutableList()
        val index = current.indexOfFirst { it.id == substitutionId }
        if (index >= 0) {
            current[index] = current[index].copy(status = status, updatedAt = System.currentTimeMillis())
            saveSubstitutionsLocally(code, current, preferences)
        }
    }

    /**
     * Removes a substitution from the local cache and SharedPreferences.
     */
    fun removeSubstitutionLocally(
        teamCode: String,
        substitutionId: String,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || substitutionId.isBlank()) return

        val current = getCachedSubstitutions(code, preferences).filter { it.id != substitutionId }
        saveSubstitutionsLocally(code, current, preferences)
    }

    /**
     * Retrieves cached substitutions for the specified team.
     */
    fun getCachedSubstitutions(
        teamCode: String,
        preferences: android.content.SharedPreferences? = null
    ): List<SubstitutionData> {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return emptyList()

        cachedSubstitutions[code]?.let { inMem ->
            if (inMem.isNotEmpty()) return inMem.toList().sortedByDescending { it.createdAt }
        }

        preferences?.let { prefs ->
            val json = prefs.getString("cached_substitutions_$code", null)
            if (!json.isNullOrBlank()) {
                try {
                    val array = org.json.JSONArray(json)
                    val list = mutableListOf<SubstitutionData>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val map = mutableMapOf<String, Any?>()
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            map[key] = obj.opt(key)
                        }
                        list.add(SubstitutionData.fromMap(map))
                    }
                    val sorted = list.sortedByDescending { it.createdAt }
                    cachedSubstitutions[code] = java.util.Collections.synchronizedList(sorted.toMutableList())
                    return sorted
                } catch (_: Throwable) {}
            }
        }

        return emptyList()
    }

    /**
     * Fetches substitutions from Cloud Firestore with 15-second debounce and offline cache fallback.
     */
    fun getSubstitutions(
        teamCode: String,
        forceRefresh: Boolean = false,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<List<SubstitutionData>>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) {
            onComplete?.invoke(Result.success(emptyList()))
            return
        }

        val cached = getCachedSubstitutions(code, preferences)
        val now = System.currentTimeMillis()
        val lastFetch = lastSubstitutionsFetchTimes[code] ?: 0L

        if (!forceRefresh && (now - lastFetch < 15_000L) && cached.isNotEmpty()) {
            onComplete?.invoke(Result.success(cached))
            return
        }

        if (substitutionsInFlight[code] == true) {
            onComplete?.invoke(Result.success(cached))
            return
        }

        substitutionsInFlight[code] = true

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SUBSTITUTIONS_SUBCOLLECTION)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener { snapshot ->
                    substitutionsInFlight[code] = false
                    lastSubstitutionsFetchTimes[code] = System.currentTimeMillis()
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { data ->
                            val mutable = data.toMutableMap()
                            if (!mutable.containsKey("id") || (mutable["id"] as? String).isNullOrBlank()) {
                                mutable["id"] = doc.id
                            }
                            SubstitutionData.fromMap(mutable)
                        }
                    }.sortedByDescending { it.createdAt }

                    // Merge remote substitutions with cached items so local requests are never wiped by empty snapshot
                    val merged = mutableListOf<SubstitutionData>()
                    merged.addAll(list)
                    cached.forEach { c ->
                        if (merged.none { it.id == c.id }) {
                            merged.add(c)
                        }
                    }
                    merged.sortByDescending { it.createdAt }

                    saveSubstitutionsLocally(code, merged, preferences)
                    onComplete?.invoke(Result.success(merged))
                }
                .addOnFailureListener { err ->
                    substitutionsInFlight[code] = false
                    android.util.Log.e("TeamRepository", "Firestore getSubstitutions failed: ${err.message}", err)
                    onComplete?.invoke(Result.success(cached))
                }
        } catch (e: Throwable) {
            substitutionsInFlight[code] = false
            android.util.Log.e("TeamRepository", "Firestore getSubstitutions exception: ${e.message}", e)
            onComplete?.invoke(Result.success(cached))
        }
    }

    /**
     * Creates a new substitution request in Firestore and updates the local cache.
     */
    fun createSubstitutionRequest(
        substitution: SubstitutionData,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<SubstitutionData>) -> Unit)? = null
    ) {
        val code = substitution.teamCode.trim().uppercase()
        if (code.isBlank() || substitution.id.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("teamCode and id cannot be blank")))
            return
        }

        saveSubstitutionLocally(code, substitution, preferences)

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SUBSTITUTIONS_SUBCOLLECTION)
                .document(substitution.id)
                .set(substitution.toMap())
                .addOnSuccessListener {
                    onComplete?.invoke(Result.success(substitution))
                }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "Firestore createSubstitutionRequest failed: ${err.message}", err)
                    // Substitution is safely saved in local cache, deliver success
                    onComplete?.invoke(Result.success(substitution))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "Firestore createSubstitutionRequest exception: ${e.message}", e)
            onComplete?.invoke(Result.success(substitution))
        }
    }

    /**
     * Updates the status of a substitution request in Firestore and the local cache.
     */
    fun updateSubstitutionStatus(
        teamCode: String,
        substitutionId: String,
        newStatus: String,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<Boolean>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || substitutionId.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("teamCode and substitutionId cannot be blank")))
            return
        }

        updateSubstitutionStatusLocally(code, substitutionId, newStatus, preferences)

        try {
            val updates = mapOf<String, Any>(
                "status" to newStatus,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SUBSTITUTIONS_SUBCOLLECTION)
                .document(substitutionId)
                .update(updates)
                .addOnSuccessListener {
                    onComplete?.invoke(Result.success(true))
                }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "Firestore updateSubstitutionStatus failed: ${err.message}", err)
                    // Already updated locally in SharedPreferences, deliver success
                    onComplete?.invoke(Result.success(true))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "Firestore updateSubstitutionStatus exception: ${e.message}", e)
            onComplete?.invoke(Result.success(true))
        }
    }

    /**
     * Deletes a substitution request from Firestore and the local cache.
     */
    fun deleteSubstitution(
        teamCode: String,
        substitutionId: String,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<Boolean>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || substitutionId.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("teamCode and substitutionId cannot be blank")))
            return
        }

        removeSubstitutionLocally(code, substitutionId, preferences)

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SUBSTITUTIONS_SUBCOLLECTION)
                .document(substitutionId)
                .delete()
                .addOnSuccessListener {
                    onComplete?.invoke(Result.success(true))
                }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "Firestore deleteSubstitution failed: ${err.message}", err)
                    // Already removed locally, deliver success
                    onComplete?.invoke(Result.success(true))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "Firestore deleteSubstitution exception: ${e.message}", e)
            onComplete?.invoke(Result.success(true))
        }
    }

    /**
     * Pure function that replaces the assignment matching the substitution request in a ServiceData record.
     */
    fun replaceAssignmentInService(service: ServiceData, sub: SubstitutionData): ServiceData {
        val isInclusion = sub.type.equals("Inclusion", ignoreCase = true)
        val targetUid = if (isInclusion) sub.substituteUid else sub.requesterUid
        val targetName = if (isInclusion) sub.substituteName else sub.requesterName
        val newUid = if (isInclusion) sub.requesterUid else sub.substituteUid
        val newName = if (isInclusion) sub.requesterName else sub.substituteName
        var replaced = false
        val updatedAssignments = service.assignments.map { assignment ->
            val matchesRole = assignment.role.equals(sub.role, ignoreCase = true)
            val matchesTarget = (targetUid.isNotBlank() && assignment.uid == targetUid) ||
                    (targetName.isNotBlank() && assignment.name.equals(targetName, ignoreCase = true))

            if (!replaced && (matchesTarget || (matchesRole && targetUid.isBlank()))) {
                replaced = true
                assignment.copy(
                    uid = newUid,
                    name = newName
                )
            } else {
                assignment
            }
        }.toMutableList()

        if (!replaced) {
            val roleIndex = updatedAssignments.indexOfFirst { it.role.equals(sub.role, ignoreCase = true) }
            if (roleIndex >= 0) {
                val old = updatedAssignments[roleIndex]
                updatedAssignments[roleIndex] = old.copy(
                    uid = newUid,
                    name = newName
                )
            }
        }

        return service.copy(
            assignments = updatedAssignments,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Accepts a substitution request and automatically updates the corresponding ServiceData schedule roster.
     */

    /**
     * Subscribes to real-time service updates for a team via Firestore addSnapshotListener.
     */
    fun subscribeToServices(
        teamCode: String,
        isLeader: Boolean = true,
        preferences: android.content.SharedPreferences? = null,
        onUpdate: (List<ServiceData>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return null

        val query = db.collection(TEAMS_COLLECTION)
            .document(code)
            .collection(SERVICES_SUBCOLLECTION)
        val finalQuery = if (isLeader) query else query.whereEqualTo("status", "Published")

        return try {
            finalQuery.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED && !isLeader && preferences != null) {
                        val uid = preferences.getString("userId", "").orEmpty()
                        val email = preferences.getString("email", "").orEmpty().trim().lowercase()
                        val isLead = email == "ryanlloydgenturo@gmail.com"
                        if (!isLead) {
                            preferences.edit()
                                .remove("teamName")
                                .remove("teamCode")
                                .remove("ministryRole")
                                .putString("role", "Member")
                                .remove("email_team_$email")
                                .remove("email_code_$email")
                                .remove("user_team_$uid")
                                .remove("user_code_$uid")
                                .putBoolean("leftTeam", true)
                                .putBoolean("removedByLeader", true)
                                .putString("removedTeamCode", code)
                                .apply()
                        }
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val list = mutableListOf<ServiceData>()
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    var srv = ServiceData.fromMap(data).copy(id = doc.id)
                    if (srv.coverImageUrl.isBlank()) {
                        val fallbackCover = resolveFallbackCoverImageUrl(code, srv.id, srv.title, preferences)
                        if (fallbackCover.isNotBlank()) {
                            srv = srv.copy(coverImageUrl = fallbackCover)
                            if (isLeader) {
                                try { doc.reference.set(mapOf("coverImageUrl" to fallbackCover), com.google.firebase.firestore.SetOptions.merge()) } catch (_: Throwable) {}
                            }
                        }
                    }
                    list.add(srv)
                }
                list.sortBy { it.date }
                saveServicesLocally(code, list, preferences)
                val memList = cachedServices.getOrPut(code) { java.util.Collections.synchronizedList(mutableListOf()) }
                synchronized(memList) {
                    memList.clear()
                    memList.addAll(list)
                }
                lastServicesFetchTimes[code] = System.currentTimeMillis()
                onUpdate(list)
            }
        } catch (_: Exception) { null }
    }

    /**
     * Subscribes to real-time substitution updates for a team via Firestore addSnapshotListener.
     */
    fun subscribeToSubstitutions(
        teamCode: String,
        preferences: android.content.SharedPreferences? = null,
        onUpdate: (List<SubstitutionData>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return null

        return try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SUBSTITUTIONS_SUBCOLLECTION)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED && preferences != null) {
                            val uid = preferences.getString("userId", "").orEmpty()
                            val email = preferences.getString("email", "").orEmpty().trim().lowercase()
                            val isLead = email == "ryanlloydgenturo@gmail.com"
                            if (!isLead) {
                                preferences.edit()
                                    .remove("teamName")
                                    .remove("teamCode")
                                    .remove("ministryRole")
                                    .putString("role", "Member")
                                    .remove("email_team_$email")
                                    .remove("email_code_$email")
                                    .remove("user_team_$uid")
                                    .remove("user_code_$uid")
                                    .putBoolean("leftTeam", true)
                                    .putBoolean("removedByLeader", true)
                                    .putString("removedTeamCode", code)
                                    .apply()
                            }
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    val list = mutableListOf<SubstitutionData>()
                    for (doc in snapshot.documents) {
                        val data = doc.data ?: continue
                        list.add(SubstitutionData.fromMap(data).copy(id = doc.id))
                    }
                    list.sortByDescending { it.createdAt }
                    saveSubstitutionsLocally(code, list, preferences)
                    val memList = cachedSubstitutions.getOrPut(code) { java.util.Collections.synchronizedList(mutableListOf()) }
                    synchronized(memList) {
                        memList.clear()
                        memList.addAll(list)
                    }
                    lastSubstitutionsFetchTimes[code] = System.currentTimeMillis()
                    onUpdate(list)
                }
        } catch (_: Exception) { null }
    }

    fun acceptSubstitutionAndUpdateService(
        teamCode: String,
        sub: SubstitutionData,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<Boolean>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        val acceptedSub = sub.copy(status = "Accepted", updatedAt = System.currentTimeMillis())

        if (acceptedSub.serviceId.isBlank()) {
            updateSubstitutionStatusLocally(code, sub.id, "Accepted", preferences)
            updateSubstitutionStatus(code, sub.id, "Accepted", preferences, onComplete)
            return
        }

        // Helper to commit substitution status after service update succeeds
        fun commitSubstitutionStatus() {
            updateSubstitutionStatusLocally(code, sub.id, "Accepted", preferences)
            updateSubstitutionStatus(code, sub.id, "Accepted", preferences) { subResult ->
                onComplete?.invoke(subResult)
            }
        }

        // Fetch latest ServiceData from Firestore to prevent stale cache overwrites
        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(SERVICES_SUBCOLLECTION)
                .document(acceptedSub.serviceId)
                .get()
                .addOnSuccessListener { doc ->
                    val svc = if (doc != null && doc.exists()) {
                        ServiceData.fromMap(doc.data ?: emptyMap())
                    } else {
                        getCachedServices(code, preferences).find { it.id == acceptedSub.serviceId }
                    }

                    if (svc != null) {
                        val updated = replaceAssignmentInService(svc, acceptedSub)
                        saveServiceLocally(code, updated, preferences)
                        lastServicesFetchTimes[code] = 0L
                        saveService(code, updated, preferences) { _ ->
                            commitSubstitutionStatus()
                        }
                    } else {
                        // If service record not found anywhere, commit substitution status directly
                        commitSubstitutionStatus()
                    }
                }
                .addOnFailureListener { fetchErr ->
                    // Network / fetch error: attempt fallback to locally cached service
                    val cached = getCachedServices(code, preferences).find { it.id == acceptedSub.serviceId }
                    if (cached != null) {
                        val updated = replaceAssignmentInService(cached, acceptedSub)
                        saveServiceLocally(code, updated, preferences)
                        saveService(code, updated, preferences) { svcResult ->
                            if (svcResult.isSuccess) {
                                commitSubstitutionStatus()
                            } else {
                                val err = svcResult.exceptionOrNull() ?: fetchErr
                                android.util.Log.e("TeamRepository", "saveService failed on substitution accept (offline fallback)", err)
                                onComplete?.invoke(Result.failure(err))
                            }
                        }
                    } else {
                        android.util.Log.e("TeamRepository", "Failed to fetch service for substitution accept", fetchErr)
                        onComplete?.invoke(Result.failure(fetchErr))
                    }
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "Exception in acceptSubstitutionAndUpdateService", e)
            onComplete?.invoke(Result.failure(e))
        }
    }

    // =========================================================================
    // Team Chat Messages Cache & Sync (Messenger Style)
    // =========================================================================

    /**
     * Retrieves cached team chat messages (in-memory first, falling back to SharedPreferences).
     * Returns messages sorted chronologically (oldest to newest) for natural chat flow.
     */
    fun getCachedChatMessages(
        teamCode: String,
        preferences: android.content.SharedPreferences? = null
    ): List<ChatMessage> {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return emptyList()

        cachedChatMessages[code]?.let { inMem ->
            if (inMem.isNotEmpty()) return inMem.toList().sortedBy { it.timestamp }
        }

        preferences?.let { prefs ->
            val json = prefs.getString("$PREFS_KEY_MESSAGES_PREFIX$code", null)
            if (!json.isNullOrBlank()) {
                try {
                    val array = org.json.JSONArray(json)
                    val list = mutableListOf<ChatMessage>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val map = mutableMapOf<String, Any?>()
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            map[key] = obj.opt(key)
                        }
                        list.add(ChatMessage.fromMap(map))
                    }
                    val sorted = list.sortedBy { it.timestamp }
                    cachedChatMessages[code] = sorted.toMutableList()
                    return sorted
                } catch (e: Throwable) {
                    android.util.Log.e("TeamRepository", "Error parsing cached chat messages: ${e.message}")
                }
            }
        }

        return emptyList()
    }

    /**
     * Saves a single chat message to in-memory and SharedPreferences cache.
     */
    fun saveChatMessageLocally(
        teamCode: String,
        message: ChatMessage,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || message.id.isBlank()) return

        val currentList = cachedChatMessages.getOrPut(code) {
            preferences?.let { prefs ->
                val json = prefs.getString("$PREFS_KEY_MESSAGES_PREFIX$code", null)
                if (!json.isNullOrBlank()) {
                    try {
                        val array = org.json.JSONArray(json)
                        val list = mutableListOf<ChatMessage>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val map = mutableMapOf<String, Any?>()
                            val keys = obj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                map[key] = obj.opt(key)
                            }
                            list.add(ChatMessage.fromMap(map))
                        }
                        list
                    } catch (e: Throwable) {
                        mutableListOf()
                    }
                } else mutableListOf()
            } ?: mutableListOf()
        }

        synchronized(currentList) {
            val existingIndex = currentList.indexOfFirst { it.id == message.id }
            if (existingIndex >= 0) {
                currentList[existingIndex] = message
            } else {
                currentList.add(message)
            }
            currentList.sortBy { it.timestamp }
        }

        saveAllChatMessagesLocally(code, currentList.toList(), preferences)
    }

    /**
     * Persists all chat messages for a team into SharedPreferences.
     */
    fun saveAllChatMessagesLocally(
        teamCode: String,
        messages: List<ChatMessage>,
        preferences: android.content.SharedPreferences? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return

        val sorted = messages.sortedBy { it.timestamp }
        cachedChatMessages[code] = sorted.toMutableList()

        preferences?.let { prefs ->
            try {
                val array = org.json.JSONArray()
                for (msg in sorted) {
                    val obj = org.json.JSONObject(msg.toMap())
                    array.put(obj)
                }
                prefs.edit {
                    putString("$PREFS_KEY_MESSAGES_PREFIX$code", array.toString())
                }
            } catch (e: Throwable) {
                android.util.Log.e("TeamRepository", "Error saving chat messages to SharedPreferences: ${e.message}")
            }
        }
    }

    /**
     * Sends a chat message to Firestore /teams/{teamCode}/messages/{messageId} and saves it locally.
     */
    fun sendChatMessage(
        teamCode: String,
        message: ChatMessage,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<ChatMessage>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || message.id.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("teamCode and id cannot be blank")))
            return
        }

        val authUid = com.example.syncedn.data.auth.AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
        val effectiveUid = if (authUid != null && (message.senderUid.isBlank() || message.senderUid.startsWith("user_"))) {
            authUid
        } else {
            message.senderUid.ifBlank { authUid ?: "anonymous" }
        }
        val sanitizedMsg = if (message.senderUid != effectiveUid) message.copy(senderUid = effectiveUid) else message

        saveChatMessageLocally(code, sanitizedMsg, preferences)

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(MESSAGES_SUBCOLLECTION)
                .document(sanitizedMsg.id)
                .set(sanitizedMsg.toMap())
                .addOnSuccessListener {
                    onComplete?.invoke(Result.success(sanitizedMsg))
                }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "Firestore sendChatMessage failed: ${err.message}", err)
                    // Safely saved locally, deliver success so UI isn't blocked
                    onComplete?.invoke(Result.success(sanitizedMsg))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "Firestore sendChatMessage exception: ${e.message}", e)
            onComplete?.invoke(Result.success(sanitizedMsg))
        }
    }

    /**
     * Attaches a real-time snapshot listener to the team messages subcollection.
     * Keeps local cache updated and triggers onUpdate callback on every new message.
     */
    fun editChatMessage(
        teamCode: String,
        messageId: String,
        newText: String,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<Unit>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || messageId.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("Invalid team code or message ID")))
            return
        }
        val cached = getCachedChatMessages(code, preferences)
        val updatedList = cached.map { msg ->
            if (msg.id == messageId) msg.copy(text = newText) else msg
        }
        saveAllChatMessagesLocally(code, updatedList, preferences)

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(MESSAGES_SUBCOLLECTION)
                .document(messageId)
                .update("text", newText)
                .addOnSuccessListener {
                    onComplete?.invoke(Result.success(Unit))
                }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "editChatMessage Firestore error: ${err.message}", err)
                    onComplete?.invoke(Result.success(Unit))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "editChatMessage exception: ${e.message}", e)
            onComplete?.invoke(Result.success(Unit))
        }
    }

    fun deleteChatMessage(
        teamCode: String,
        messageId: String,
        preferences: android.content.SharedPreferences? = null,
        onComplete: ((Result<Unit>) -> Unit)? = null
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank() || messageId.isBlank()) {
            onComplete?.invoke(Result.failure(IllegalArgumentException("Invalid team code or message ID")))
            return
        }
        val cached = getCachedChatMessages(code, preferences)
        val updatedList = cached.filter { it.id != messageId }
        saveAllChatMessagesLocally(code, updatedList, preferences)

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(MESSAGES_SUBCOLLECTION)
                .document(messageId)
                .delete()
                .addOnSuccessListener {
                    onComplete?.invoke(Result.success(Unit))
                }
                .addOnFailureListener { err ->
                    android.util.Log.e("TeamRepository", "deleteChatMessage Firestore error: ${err.message}", err)
                    onComplete?.invoke(Result.success(Unit))
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "deleteChatMessage exception: ${e.message}", e)
            onComplete?.invoke(Result.success(Unit))
        }
    }

    fun listenTeamMessages(
        teamCode: String,
        preferences: android.content.SharedPreferences? = null,
        onUpdate: (List<ChatMessage>) -> Unit
    ): ListenerRegistration? {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return null

        // Immediately emit cached messages so UI is never empty
        val initialCached = getCachedChatMessages(code, preferences)
        if (initialCached.isNotEmpty()) {
            onUpdate(initialCached)

            // Auto-sync any local unpushed messages to Firestore in background
            try {
                val dbRef = db.collection(TEAMS_COLLECTION).document(code).collection(MESSAGES_SUBCOLLECTION)
                for (localMsg in initialCached) {
                    if (localMsg.mediaUrl.isBlank() || localMsg.mediaUrl.startsWith("http://") || localMsg.mediaUrl.startsWith("https://")) {
                        dbRef.document(localMsg.id).set(localMsg.toMap()).addOnFailureListener {
                            // ignore background sync errors
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        return try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(MESSAGES_SUBCOLLECTION)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(200)
                .addSnapshotListener { snapshot, err ->
                    if (err != null) {
                        android.util.Log.e("TeamRepository", "listenTeamMessages error: ${err.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val remoteMessages = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { ChatMessage.fromMap(it) }
                        }

                        // Merge remote messages with local cached messages so un-synced messages are never wiped out
                        val localCached = getCachedChatMessages(code, preferences)
                        val mergedMap = LinkedHashMap<String, ChatMessage>()
                        localCached.forEach { mergedMap[it.id] = it }
                        remoteMessages.forEach { mergedMap[it.id] = it }

                        val mergedList = mergedMap.values.sortedBy { it.timestamp }
                        if (mergedList.isNotEmpty()) {
                            saveAllChatMessagesLocally(code, mergedList, preferences)
                            onUpdate(mergedList)
                        } else if (localCached.isNotEmpty()) {
                            onUpdate(localCached)
                        }
                    }
                }
        } catch (e: Throwable) {
            android.util.Log.e("TeamRepository", "listenTeamMessages exception: ${e.message}")
            null
        }
    }

    private fun formatRelativeNotificationTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Recently"
        val diffMs = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
        val mins = diffMs / 60_000L
        val hours = diffMs / 3_600_000L
        val days = diffMs / 86_400_000L
        return when {
            mins < 1L -> "Just now"
            mins < 60L -> "${mins}m ago"
            hours < 24L -> "${hours} hour${if (hours == 1L) "" else "s"} ago"
            days < 7L -> "${days} day${if (days == 1L) "" else "s"} ago"
            else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(timestamp))
        }
    }

    /**
     * Builds a dynamic recent notifications feed from real team services, announcements, and substitutions.
     */
    fun getRecentTeamNotifications(
        teamCode: String,
        preferences: android.content.SharedPreferences? = null
    ): List<TeamNotificationItem> {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return emptyList()

        val items = mutableListOf<TeamNotificationItem>()

        // 1. Published Services & Lineups
        val publishedServices = getCachedServices(code, preferences).filter { it.status == "Published" }
        for (srv in publishedServices) {
            val ts = srv.updatedAt.takeIf { it > 0L } ?: srv.createdAt
            val timePart = if (srv.start.isNotBlank()) " at ${srv.start}" else ""
            items.add(
                TeamNotificationItem(
                    id = "notif_srv_${srv.id}",
                    title = "New Service Scheduled",
                    message = "${srv.title} scheduled for ${srv.date}$timePart.",
                    timeLabel = formatRelativeNotificationTime(ts),
                    iconName = "calendar",
                    timestamp = ts
                )
            )
            if (srv.songs.isNotEmpty()) {
                val songCount = srv.songs.size
                items.add(
                    TeamNotificationItem(
                        id = "notif_lineup_${srv.id}",
                        title = "Lineup Published",
                        message = "$songCount song${if (songCount == 1) "" else "s"} attached to ${srv.title}.",
                        timeLabel = formatRelativeNotificationTime(ts - 1L),
                        iconName = "musical_notes",
                        timestamp = ts - 1L
                    )
                )
            }
        }

        // 2. Team Announcements
        val announcements = getCachedAnnouncements(code, preferences)
        for (ann in announcements) {
            val ts = ann.updatedAt.takeIf { it > 0L } ?: ann.createdAt
            val prefix = if (ann.urgent) "Urgent Alert: " else "Announcement: "
            items.add(
                TeamNotificationItem(
                    id = "notif_ann_${ann.id}",
                    title = "$prefix${ann.title}",
                    message = ann.message,
                    timeLabel = formatRelativeNotificationTime(ts),
                    iconName = "announcement",
                    timestamp = ts
                )
            )
        }

        // 3. Substitution Requests
        val substitutions = getCachedSubstitutions(code, preferences)
        for (sub in substitutions) {
            val ts = sub.updatedAt.takeIf { it > 0L } ?: sub.createdAt
            val statusDisplay = when (sub.status) {
                "Substitute Accepted" -> "Substitute Accepted"
                "Accepted", "Approved" -> "Approved"
                "Declined" -> "Declined"
                "Cancelled" -> "Cancelled"
                else -> "Pending"
            }
            val isInc = sub.type.equals("Inclusion", true) || sub.reason.startsWith("[Inclusion]")
            val title = if (isInc) "Inclusion Request ($statusDisplay)" else "Substitution Request ($statusDisplay)"
            val msg = if (isInc) {
                "${sub.requesterName} offered to step in for ${sub.substituteName} (${sub.role}) in ${sub.serviceTitle}."
            } else {
                "${sub.requesterName} requested ${sub.substituteName} to cover ${sub.role} in ${sub.serviceTitle}."
            }
            items.add(
                TeamNotificationItem(
                    id = "notif_sub_${sub.id}",
                    title = title,
                    message = msg,
                    timeLabel = formatRelativeNotificationTime(ts),
                    iconName = "people",
                    timestamp = ts
                )
            )
        }

        val deletedIds = preferences?.getStringSet("deleted_notifications", emptySet()).orEmpty()
        return items.filter { it.id !in deletedIds }.sortedByDescending { it.timestamp }.take(25)
    }

    /**
     * Deletes / dismisses an individual notification item from the user's notification feed.
     */
    fun deleteNotification(notificationId: String, preferences: android.content.SharedPreferences? = null) {
        if (notificationId.isBlank() || preferences == null) return
        val set = preferences.getStringSet("deleted_notifications", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(notificationId)
        preferences.edit().putStringSet("deleted_notifications", set).apply()
    }

    /**
     * Clears all recent notification items from the user's notification feed.
     */
    fun clearAllNotifications(teamCode: String, preferences: android.content.SharedPreferences? = null) {
        if (preferences == null) return
        val allItems = getRecentTeamNotifications(teamCode, null)
        val set = preferences.getStringSet("deleted_notifications", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.addAll(allItems.map { it.id })
        preferences.edit().putStringSet("deleted_notifications", set).apply()
    }

    /**
     * Submits a team issue report to Cloud Firestore (/teams/{teamCode}/reports/{reportId}) and caches it locally.
     */
    @Synchronized
    fun submitTeamReport(
        teamCode: String,
        reporterUid: String,
        reporterName: String,
        reason: String,
        preferences: android.content.SharedPreferences? = null,
        onComplete: (Result<Map<String, Any>>) -> Unit = {}
    ) {
        val code = teamCode.trim().uppercase()
        val cleanReason = reason.trim().take(1000)
        if (code.isBlank() || cleanReason.isBlank()) {
            onComplete(Result.failure(IllegalArgumentException("Team code and reason are required.")))
            return
        }

        val reportId = "rep_${System.currentTimeMillis()}"
        val effectiveUid = reporterUid.ifBlank {
            com.example.syncedn.data.auth.AuthManager.currentUser?.uid.orEmpty().ifBlank { "anonymous" }
        }
        val effectiveName = reporterName.ifBlank { "Team Member" }.take(100)
        val reportMap = mapOf<String, Any>(
            "id" to reportId,
            "teamCode" to code,
            "reporterUid" to effectiveUid,
            "reporterName" to effectiveName,
            "reason" to cleanReason,
            "createdAt" to System.currentTimeMillis()
        )

        val list = cachedReports.getOrPut(code) { mutableListOf() }
        synchronized(list) {
            list.add(0, reportMap)
        }

        preferences?.let { prefs ->
            try {
                val arr = org.json.JSONArray()
                synchronized(list) {
                    list.forEach { item -> arr.put(org.json.JSONObject(item)) }
                }
                prefs.edit {
                    putString("teamReport", cleanReason)
                    putString("$PREFS_KEY_REPORTS_PREFIX$code", arr.toString())
                }
            } catch (_: Throwable) {}
        }

        onComplete(Result.success(reportMap))

        try {
            db.collection(TEAMS_COLLECTION)
                .document(code)
                .collection(REPORTS_SUBCOLLECTION)
                .document(reportId)
                .set(reportMap)
        } catch (_: Throwable) {}
    }

    /**
     * Retrieves cached team reports for a team.
     */
    fun getCachedTeamReports(
        teamCode: String,
        preferences: android.content.SharedPreferences? = null
    ): List<Map<String, Any>> {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return emptyList()

        val mem = cachedReports[code]
        if (!mem.isNullOrEmpty()) {
            synchronized(mem) { return mem.toList() }
        }

        val prefs = preferences ?: return emptyList()
        val raw = prefs.getString("$PREFS_KEY_REPORTS_PREFIX$code", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val parsed = (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val map = mutableMapOf<String, Any>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    obj.opt(k)?.let { v -> map[k] = v }
                }
                map.toMap()
            }
            cachedReports[code] = parsed.toMutableList()
            parsed
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Fetches live team reports from Firestore (/teams/{teamCode}/reports) with local caching.
     */
    fun getTeamReports(
        teamCode: String,
        preferences: android.content.SharedPreferences? = null,
        onResult: (List<Map<String, Any>>) -> Unit
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) {
            onResult(emptyList())
            return
        }

        val cached = getCachedTeamReports(code, preferences)
        if (cached.isNotEmpty()) {
            onResult(cached)
        }

        db.collection(TEAMS_COLLECTION).document(code)
            .collection(REPORTS_SUBCOLLECTION)
            .get()
            .addOnSuccessListener { query ->
                val fetched = query.documents.mapNotNull { it.data }.sortedByDescending { doc ->
                    (doc["createdAt"] as? Number)?.toLong() ?: 0L
                }
                if (fetched.isNotEmpty()) {
                    cachedReports[code] = fetched.toMutableList()
                    preferences?.edit()?.putString(
                        "$PREFS_KEY_REPORTS_PREFIX$code",
                        org.json.JSONArray(fetched).toString()
                    )?.apply()
                    onResult(fetched)
                } else if (cached.isEmpty()) {
                    onResult(emptyList())
                }
            }
            .addOnFailureListener {
                if (cached.isEmpty()) onResult(emptyList())
            }
    }

    /**
     * Persists user notification and unmute preferences locally and syncs to Firestore /users/{uid}.
     */
    fun saveNotificationPreferences(
        uid: String,
        notificationsEnabled: Boolean,
        teamUnmuted: Boolean,
        preferences: android.content.SharedPreferences? = null,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        preferences?.edit {
            putBoolean("notifications", notificationsEnabled)
            putBoolean("teamUnmuted", teamUnmuted)
            if (uid.isNotBlank()) {
                putBoolean("user_notifications_$uid", notificationsEnabled)
                putBoolean("user_unmuted_$uid", teamUnmuted)
            }
        }

        onComplete(Result.success(Unit))

        if (uid.isNotBlank()) {
            try {
                val payload = mapOf(
                    "uid" to uid,
                    "notificationsEnabled" to notificationsEnabled,
                    "teamUnmuted" to teamUnmuted
                )
                db.collection(USERS_COLLECTION)
                    .document(uid)
                    .set(payload, com.google.firebase.firestore.SetOptions.merge())
            } catch (_: Throwable) {}
        }
    }
}


