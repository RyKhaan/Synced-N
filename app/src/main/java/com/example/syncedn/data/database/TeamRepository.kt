package com.example.syncedn.data.database

import com.example.syncedn.data.model.TeamData
import com.example.syncedn.data.model.TeamMember
import com.example.syncedn.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore

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

    /**
     * Clears all in-memory roster and role caches (primarily for unit tests).
     */
    @Synchronized
    fun clearCache() {
        cachedRosters.clear()
        cachedRoles.clear()
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
                    preferences.edit().putString("email_ministry_$emailLower", member.ministryRole).apply()
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
        for ((key, value) in all) {
            if (key.startsWith("email_code_") && value is String && value.trim().uppercase() == code) {
                val emailLower = key.removePrefix("email_code_").trim().lowercase()
                val role = (all["email_role_$emailLower"] as? String).orEmpty().ifBlank { "Member" }
                val fullName = (all["email_fullname_$emailLower"] as? String).orEmpty()
                val username = (all["email_username_$emailLower"] as? String).orEmpty().ifBlank { emailLower.substringBefore("@") }
                val ministry = (all["email_ministry_$emailLower"] as? String).orEmpty()
                val uid = (all["email_uid_$emailLower"] as? String).orEmpty().ifBlank { "user_${emailLower.hashCode()}" }
                val memberName = fullName.ifBlank { username }
                if (memberName.isNotBlank()) {
                    val member = TeamMember(
                        uid = uid,
                        fullName = memberName,
                        username = username,
                        email = emailLower,
                        role = role,
                        ministryRole = ministry,
                        teamCode = code
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
     * Matches in-memory and Firestore records by memberUid and/or memberName (fullName / username).
     */
    @Synchronized
    fun updateMemberMinistryRole(
        memberUid: String,
        newRole: String,
        memberName: String = "",
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        if (memberUid.isBlank() && memberName.isBlank()) {
            onComplete(Result.success(Unit))
            return
        }

        // Update in-memory caches
        for ((_, roster) in cachedRosters) {
            synchronized(roster) {
                val member = roster.find {
                    (memberUid.isNotBlank() && it.uid == memberUid) ||
                    (memberName.isNotBlank() && (it.fullName.equals(memberName, true) || it.username.equals(memberName, true)))
                }
                if (member != null) {
                    val updated = member.copy(ministryRole = newRole)
                    val idx = roster.indexOf(member)
                    roster[idx] = updated
                }
            }
        }

        try {
            if (memberUid.isNotBlank()) {
                db.collection(USERS_COLLECTION)
                    .document(memberUid)
                    .update("ministryRole", newRole)
                    .addOnSuccessListener { onComplete(Result.success(Unit)) }
                    .addOnFailureListener { onComplete(Result.failure(it)) }
            } else {
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
}
