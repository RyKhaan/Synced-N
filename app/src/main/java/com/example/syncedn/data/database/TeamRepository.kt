package com.example.syncedn.data.database

import com.example.syncedn.data.model.ServiceData
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
    private const val SERVICES_SUBCOLLECTION = "services"

    // In-memory cache for fast UI updates, offline fallback, and unit tests
    private val cachedRosters = java.util.concurrent.ConcurrentHashMap<String, MutableList<TeamMember>>()
    private val cachedRoles = java.util.concurrent.ConcurrentHashMap<String, MutableList<String>>()
    private val cachedUserRanges = java.util.concurrent.ConcurrentHashMap<String, MutableList<UnavailableRange>>()
    private val lastRosterFetchTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val rosterInFlight = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private val cachedServices = java.util.concurrent.ConcurrentHashMap<String, MutableList<ServiceData>>()
    private val lastServicesFetchTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val servicesInFlight = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Clears all in-memory roster, role, and service caches (primarily for unit tests).
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
     * Protected with a timeout guard, in-flight throttling, and fallback to cached roster.
     */
    fun getTeamRoster(
        teamCode: String,
        force: Boolean = false,
        onComplete: (Result<List<TeamMember>>) -> Unit
    ) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) {
            onComplete(Result.success(emptyList()))
            return
        }

        val cached = getCachedRoster(code)
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
                finishOnce(Result.success(getCachedRoster(code)))
            }, 3000L)
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

    fun getTeamRoster(teamCode: String, onComplete: (Result<List<TeamMember>>) -> Unit) {
        getTeamRoster(teamCode, false, onComplete)
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

    // ===============================================================
    // Worship Services Management & Caching
    // ===============================================================

    /**
     * Saves or updates a service in the local in-memory cache and SharedPreferences.
     */
    @Synchronized
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
                    val srv = ServiceData.fromMap(map)
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

        if (inFlight || (now - lastFetch < 15_000L && filteredCached.isNotEmpty())) {
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
                        val srv = ServiceData.fromMap(data).copy(id = doc.id)
                        remoteList.add(srv)
                    }
                    if (remoteList.isNotEmpty()) {
                        val memList = cachedServices.getOrPut(code) { java.util.Collections.synchronizedList(mutableListOf()) }
                        synchronized(memList) {
                            if (isLeader) {
                                memList.clear()
                                memList.addAll(remoteList)
                            } else {
                                // For members, merge published remote services
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
                .addOnFailureListener {
                    // Local cache already preserved the service
                    onComplete?.invoke(Result.success(service))
                }
        } catch (_: Throwable) {
            onComplete?.invoke(Result.success(service))
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
                .addOnFailureListener { onComplete?.invoke(Result.success(true)) }
        } catch (_: Throwable) {
            onComplete?.invoke(Result.success(true))
        }
    }
}
