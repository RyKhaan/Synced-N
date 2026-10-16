package com.example.syncedn.data.database

import com.example.syncedn.data.model.TeamData
import com.example.syncedn.data.model.TeamMember
import com.example.syncedn.data.model.UnavailableRange
import com.example.syncedn.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.content.edit

/**
 * Repository managing team roster, user profiles, and ministry role operations in Cloud Firestore.
 * Provides caching and fallback resilience for offline and testing scenarios.
 */
object TeamRepository {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val USERS_COLLECTION = "users"
    private const val TEAMS_COLLECTION = "teams"

    // In-memory cache for fast UI updates, offline fallback, and unit tests
    private val cachedRosters = java.util.concurrent.ConcurrentHashMap<String, MutableList<TeamMember>>()
    private val cachedRoles = java.util.concurrent.ConcurrentHashMap<String, MutableList<String>>()
    private val cachedUserRanges = java.util.concurrent.ConcurrentHashMap<String, MutableList<UnavailableRange>>()

    /**
     * Clears all in-memory roster and role caches (primarily for unit tests).
     */
    @Synchronized
    fun clearCache() {
        cachedRosters.clear()
        cachedRoles.clear()
        cachedUserRanges.clear()
    }

    /**
     * Adds or updates a member in the local in-memory roster cache.
     * Matches by UID or email, and optionally persists the member record to SharedPreferences.
     */
    @Synchronized
    fun cacheMember(member: TeamMember, preferences: android.content.SharedPreferences? = null) {
        val code = member.teamCode.trim().uppercase()
        if (code.isBlank() || member.uid.isBlank()) return
        val list = cachedRosters.getOrPut(code) { java.util.Collections.synchronizedList(mutableListOf()) }
        synchronized(list) {
            val index = list.indexOfFirst {
                it.uid == member.uid || (it.email.isNotBlank() && it.email.equals(member.email, ignoreCase = true))
            }
            if (index >= 0) {
                list[index] = member
            } else {
                list.add(member)
            }
            sortRoster(list)
        }

        if (member.unavailableRanges.isNotEmpty()) {
            cachedUserRanges[member.uid] = java.util.Collections.synchronizedList(member.unavailableRanges.toMutableList())
        }

        if (preferences != null) {
            val emailLower = member.email.trim().lowercase()
            if (emailLower.isNotBlank()) {
                preferences.edit()
                    .putString("email_code_$emailLower", code)
                    .putString("email_role_$emailLower", member.role)
                    .putString("email_uid_$emailLower", member.uid)
                    .apply()
                if (member.fullName.isNotBlank()) {
                    preferences.edit().putString("email_fullname_$emailLower", member.fullName).apply()
                }
                if (member.username.isNotBlank()) {
                    preferences.edit().putString("email_username_$emailLower", member.username).apply()
                }
                if (member.ministryRole.isNotBlank()) {
                    preferences.edit()
                        .putString("email_ministry_$emailLower", member.ministryRole)
                        .putString("user_ministry_${member.uid}", member.ministryRole)
                        .apply()
                }
            }
        }
    }

    /**
     * Synchronizes and restores any known team members for this teamCode from SharedPreferences
     * into the in-memory cache, ensuring members are preserved across logout/login and offline scenarios.
     */
    @Synchronized
    fun syncFromPreferences(preferences: android.content.SharedPreferences, teamCode: String) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return

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
                val uid = (all["email_uid_$emailLower"] as? String).orEmpty().ifBlank { "user_${emailLower.hashCode()}" }

                val assignedRole = memberRolesMap?.let { map ->
                    val byUid = if (uid.isNotBlank()) map.optString(uid, "") else ""
                    val byEmail = if (emailLower.isNotBlank()) map.optString(emailLower, "") else ""
                    val byFull = if (fullName.isNotBlank()) map.optString(fullName, "") else ""
                    val byUser = if (username.isNotBlank()) map.optString(username, "") else ""
                    byUid.ifBlank { byEmail }.ifBlank { byFull }.ifBlank { byUser }
                }?.takeIf { it.isNotBlank() }

                val ministry = assignedRole
                    ?: (all["email_ministry_$emailLower"] as? String).orEmpty().takeIf { it.isNotBlank() }
                    ?: (all["user_ministry_$uid"] as? String).orEmpty()

                val memberName = fullName.ifBlank { username }
                if (memberName.isNotBlank()) {
                    val ranges = getMemberUnavailableRanges(uid, preferences)
                    val member = TeamMember(
                        uid = uid,
                        fullName = memberName,
                        username = username,
                        email = emailLower,
                        role = role,
                        ministryRole = ministry,
                        teamCode = code,
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
        val code = teamCode.trim().uppercase()
        val list = cachedRosters[code] ?: return emptyList()
        return synchronized(list) { list.toList() }
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
                teamCode = profile.teamCode
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
     * Protected with a timeout guard and fallback to cached roster.
     */
    fun getTeamRoster(teamCode: String, onComplete: (Result<List<TeamMember>>) -> Unit) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) {
            onComplete(Result.success(emptyList()))
            return
        }

        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finishOnce(result: Result<List<TeamMember>>) {
            if (completed.compareAndSet(false, true)) {
                onComplete(result)
            }
        }

        // Safety timeout guard (5 seconds)
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                finishOnce(Result.success(getCachedRoster(code)))
            }, 5000L)
        } catch (_: Exception) {}

        try {
            db.collection(USERS_COLLECTION)
                .whereEqualTo("teamCode", code)
                .get()
                .addOnSuccessListener { snapshot ->
                    val members = mutableListOf<TeamMember>()
                    if (snapshot != null) {
                        for (doc in snapshot.documents) {
                            val data = doc.data ?: continue
                            val mapWithId = data.toMutableMap()
                            val uidFromDoc = (mapWithId["uid"] as? String)?.ifBlank { doc.id } ?: doc.id
                            mapWithId["uid"] = uidFromDoc
                            members.add(TeamMember.fromMap(mapWithId))
                        }
                    }
                    if (members.isNotEmpty()) {
                        synchronized(cachedRosters) {
                            cachedRosters[code] = java.util.Collections.synchronizedList(members)
                        }
                        sortRoster(members)
                    }
                    finishOnce(Result.success(members.ifEmpty { getCachedRoster(code) }))
                }
                .addOnFailureListener {
                    finishOnce(Result.success(getCachedRoster(code)))
                }
        } catch (_: Exception) {
            finishOnce(Result.success(getCachedRoster(code)))
        }
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
    fun removeMemberFromTeam(memberUid: String, onComplete: (Result<Unit>) -> Unit = {}) {
        if (memberUid.isBlank()) {
            onComplete(Result.success(Unit))
            return
        }

        // Remove from local cache
        for ((_, roster) in cachedRosters) {
            synchronized(roster) {
                roster.removeAll { it.uid == memberUid }
            }
        }

        val updates = mapOf(
            "teamCode" to "",
            "teamName" to "",
            "ministryRole" to "",
            "role" to "Member"
        )

        try {
            db.collection(USERS_COLLECTION)
                .document(memberUid)
                .update(updates)
                .addOnSuccessListener { onComplete(Result.success(Unit)) }
                .addOnFailureListener { onComplete(Result.failure(it)) }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Resets a user's team membership in Firestore.
     */
    fun leaveTeam(uid: String, onComplete: (Result<Unit>) -> Unit = {}) {
        removeMemberFromTeam(uid, onComplete)
    }

    /**
     * Retrieves the unavailable date ranges for a user from in-memory cache, SharedPreferences, or roster cache.
     */
    @Synchronized
    fun getMemberUnavailableRanges(
        uid: String,
        preferences: android.content.SharedPreferences? = null
    ): List<UnavailableRange> {
        if (uid.isBlank()) return emptyList()
        val fromMemory = cachedUserRanges[uid]
        if (fromMemory != null) {
            return synchronized(fromMemory) { fromMemory.toList() }
        }

        if (preferences != null) {
            val jsonStr = preferences.getString("user_unavailable_ranges_$uid", null)
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val array = org.json.JSONArray(jsonStr)
                    val list = mutableListOf<UnavailableRange>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val map = mutableMapOf<String, Any?>()
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            map[k] = obj.opt(k)
                        }
                        list.add(UnavailableRange.fromMap(map))
                    }
                    cachedUserRanges[uid] = java.util.Collections.synchronizedList(list)
                    return list
                } catch (_: Throwable) {}
            }
        }

        for ((_, roster) in cachedRosters) {
            val found = synchronized(roster) { roster.find { it.uid == uid } }
            if (found != null && found.unavailableRanges.isNotEmpty()) {
                cachedUserRanges[uid] = java.util.Collections.synchronizedList(found.unavailableRanges.toMutableList())
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

        val current = getMemberUnavailableRanges(uid, preferences).toMutableList()
        current.removeAll { it.id == range.id }
        current.add(range)
        cachedUserRanges[uid] = java.util.Collections.synchronizedList(current)

        for ((_, roster) in cachedRosters) {
            synchronized(roster) {
                val idx = roster.indexOfFirst { it.uid == uid }
                if (idx >= 0) {
                    roster[idx] = roster[idx].copy(unavailableRanges = current)
                }
            }
        }

        if (preferences != null) {
            try {
                val array = org.json.JSONArray()
                current.forEach { item ->
                    val obj = org.json.JSONObject()
                    item.toMap().forEach { (k, v) -> obj.put(k, v) }
                    array.put(obj)
                }
                preferences.edit().putString("user_unavailable_ranges_$uid", array.toString()).apply()
            } catch (_: Throwable) {}
        }

        try {
            val serialized = current.map { it.toMap() }
            db.collection(USERS_COLLECTION)
                .document(uid)
                .set(mapOf("uid" to uid, "unavailableRanges" to serialized), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { onComplete(Result.success(Unit)) }
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

        val current = getMemberUnavailableRanges(uid, preferences).toMutableList()
        current.removeAll { it.id == rangeId }
        cachedUserRanges[uid] = java.util.Collections.synchronizedList(current)

        for ((_, roster) in cachedRosters) {
            synchronized(roster) {
                val idx = roster.indexOfFirst { it.uid == uid }
                if (idx >= 0) {
                    roster[idx] = roster[idx].copy(unavailableRanges = current)
                }
            }
        }

        if (preferences != null) {
            try {
                val array = org.json.JSONArray()
                current.forEach { item ->
                    val obj = org.json.JSONObject()
                    item.toMap().forEach { (k, v) -> obj.put(k, v) }
                    array.put(obj)
                }
                preferences.edit().putString("user_unavailable_ranges_$uid", array.toString()).apply()
            } catch (_: Throwable) {}
        }

        try {
            val serialized = current.map { it.toMap() }
            db.collection(USERS_COLLECTION)
                .document(uid)
                .set(mapOf("uid" to uid, "unavailableRanges" to serialized), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { onComplete(Result.success(Unit)) }
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
    fun checkTeamAvailabilityForDate(
        teamCode: String,
        targetDate: String
    ): Pair<List<TeamMember>, List<Pair<TeamMember, String>>> {
        val roster = getCachedRoster(teamCode)
        val available = mutableListOf<TeamMember>()
        val unavailable = mutableListOf<Pair<TeamMember, String>>()

        for (member in roster) {
            val ranges = if (member.unavailableRanges.isNotEmpty()) {
                member.unavailableRanges
            } else {
                getMemberUnavailableRanges(member.uid)
            }
            val conflict = ranges.find { it.includes(targetDate) }
            if (conflict != null) {
                unavailable.add(Pair(member, conflict.reason.ifBlank { "Unavailable" }))
            } else {
                available.add(member)
            }
        }
        return Pair(available, unavailable)
    }
}
