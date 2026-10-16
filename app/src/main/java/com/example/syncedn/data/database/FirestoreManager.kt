package com.example.syncedn.data.database

import com.example.syncedn.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Manages database operations in Cloud Firestore.
 * Handles user profiles, teams, and worship data.
 */
object FirestoreManager {
    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    const val USERS_COLLECTION = "users"

    /**
     * Saves or updates a user profile document in Firestore: /users/{uid}
     */
    fun saveUserProfile(profile: UserProfile, onComplete: (Result<Unit>) -> Unit) {
        if (profile.uid.isBlank()) {
            onComplete(Result.failure(IllegalArgumentException("User UID cannot be blank")))
            return
        }

        try {
            db.collection(USERS_COLLECTION)
                .document(profile.uid)
                .set(profile.toMap())
                .addOnSuccessListener {
                    onComplete(Result.success(Unit))
                }
                .addOnFailureListener { exception ->
                    onComplete(Result.failure(exception))
                }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Retrieves a user profile document from Firestore by user UID.
     * Includes a timeout guard and merges any locally cached role/team updates.
     */
    fun getUserProfile(uid: String, onComplete: (Result<UserProfile?>) -> Unit) {
        if (uid.isBlank()) {
            onComplete(Result.success(null))
            return
        }

        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finishOnce(profile: UserProfile?) {
            if (completed.compareAndSet(false, true)) {
                val emailKey = profile?.email?.trim()?.lowercase().orEmpty()
                val cachedRoleTeam = localUserRoleTeam[uid] ?: localUserRoleTeamByEmail[emailKey]
                val merged = if (profile != null && cachedRoleTeam != null) {
                    profile.copy(
                        role = cachedRoleTeam.first,
                        teamName = cachedRoleTeam.second,
                        teamCode = cachedRoleTeam.third
                    )
                } else if (profile == null && cachedRoleTeam != null) {
                    UserProfile(
                        uid = uid,
                        role = cachedRoleTeam.first,
                        teamName = cachedRoleTeam.second,
                        teamCode = cachedRoleTeam.third
                    )
                } else {
                    profile
                }
                onComplete(Result.success(merged))
            }
        }

        try {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                finishOnce(null)
            }, 2000L)
        } catch (_: Exception) {
            // Ignored in unit test environment without MainLooper
        }

        try {
            db.collection(USERS_COLLECTION)
                .document(uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val data = snapshot.data
                        if (data != null) {
                            finishOnce(UserProfile.fromMap(data))
                            return@addOnSuccessListener
                        }
                    }
                    finishOnce(null)
                }
                .addOnFailureListener {
                    finishOnce(null)
                }
        } catch (_: Exception) {
            finishOnce(null)
        }
    }

    /**
     * Updates the user's team in Firestore.
     */
    fun updateUserTeam(uid: String, teamName: String, onComplete: (Result<Unit>) -> Unit = {}) {
        if (uid.isBlank()) {
            onComplete(Result.success(Unit))
            return
        }

        try {
            db.collection(USERS_COLLECTION)
                .document(uid)
                .update("teamName", teamName)
        } catch (_: Exception) {
            // Ignored
        }
        onComplete(Result.success(Unit))
    }

    const val TEAMS_COLLECTION = "teams"
    private const val TEAM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val localTeams = mutableMapOf<String, String>()
    private val localUserRoleTeam = mutableMapOf<String, Triple<String, String, String>>()
    private val localUserRoleTeamByEmail = mutableMapOf<String, Triple<String, String, String>>()

    /**
     * Registers a team code and team name in the local in-memory registry.
     */
    fun registerLocalTeam(teamCode: String, teamName: String) {
        val cleanCode = teamCode.trim().uppercase()
        val cleanName = teamName.trim()
        if (isValidTeamCodeFormat(cleanCode) && cleanName.isNotBlank()) {
            localTeams[cleanCode] = cleanName
        }
    }

    /**
     * Looks up a team name by its 6-character code in the local in-memory registry.
     */
    fun getLocalTeamName(teamCode: String): String? {
        val cleanCode = teamCode.trim().uppercase()
        return localTeams[cleanCode]
    }

    /**
     * Looks up a 6-character team code by team name in the local in-memory registry.
     */
    fun findCodeByTeamName(teamName: String): String? {
        val cleanName = teamName.trim()
        if (cleanName.isBlank()) return null
        return localTeams.entries.firstOrNull { it.value.equals(cleanName, ignoreCase = true) }?.key
    }

    /**
     * Registers a user's role, teamName, and teamCode in the in-memory cache by UID and/or email.
     */
    fun cacheUserRoleAndTeam(uid: String, email: String, role: String, teamName: String, teamCode: String) {
        val entry = Triple(role, teamName, teamCode)
        if (uid.isNotBlank()) {
            localUserRoleTeam[uid] = entry
        }
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isNotBlank()) {
            localUserRoleTeamByEmail[cleanEmail] = entry
        }
    }

    /**
     * Clears local in-memory teams (used in unit tests).
     */
    fun clearLocalTeams() {
        localTeams.clear()
        localUserRoleTeam.clear()
        localUserRoleTeamByEmail.clear()
    }

    /**
     * Generates a random 6-character uppercase alphanumeric invite code for a team.
     */
    fun generateTeamCode(): String {
        return (1..6)
            .map { TEAM_CODE_CHARS.random() }
            .joinToString("")
    }

    /**
     * Checks whether a given team code string matches the expected 6-character alphanumeric format.
     */
    fun isValidTeamCodeFormat(code: String): Boolean {
        val cleaned = code.trim().uppercase()
        return cleaned.length == 6 && cleaned.all { it.isLetterOrDigit() }
    }

    /**
     * Creates a new team with a unique 6-character teamCode, caches it locally for immediate access,
     * and syncs to Firestore (/teams/{teamCode} and /users/{leaderUid}) in the background so UI never hangs.
     */
    fun createTeamWithCode(
        leaderUid: String,
        leaderName: String,
        teamName: String,
        onComplete: (Result<String>) -> Unit
    ) {
        val cleanTeamName = teamName.trim()
        if (cleanTeamName.isBlank()) {
            onComplete(Result.failure(IllegalArgumentException("Team name cannot be blank.")))
            return
        }

        val existingCode = findCodeByTeamName(cleanTeamName)
        var generatedCode = existingCode ?: generateTeamCode()
        if (existingCode == null) {
            while (localTeams.containsKey(generatedCode)) {
                generatedCode = generateTeamCode()
            }
        }
        registerLocalTeam(generatedCode, cleanTeamName)

        val teamData = mapOf(
            "teamCode" to generatedCode,
            "teamName" to cleanTeamName,
            "leaderUid" to leaderUid,
            "leaderName" to leaderName,
            "createdAt" to System.currentTimeMillis()
        )

        try {
            db.collection(TEAMS_COLLECTION)
                .document(generatedCode)
                .set(teamData)
            updateUserRoleAndTeam(leaderUid, "Leader", cleanTeamName, generatedCode)
        } catch (_: Exception) {
            // Synced in background when online; local registry is already updated
        }

        onComplete(Result.success(generatedCode))
    }

    /**
     * Verifies if a team code exists in the local registry or in Firestore (/teams/{teamCode}).
     * If valid, returns Pair(teamCode, teamName). Otherwise returns a failure.
     * Protected with a timeout guard so the UI never hangs if offline.
     */
    fun getTeamByCode(
        rawCode: String,
        onComplete: (Result<Pair<String, String>>) -> Unit
    ) {
        val cleanCode = rawCode.trim().uppercase()
        if (!isValidTeamCodeFormat(cleanCode)) {
            onComplete(Result.failure(IllegalArgumentException("Enter a valid 6-character team code.")))
            return
        }

        // 1. Check local team registry first for instant verification
        val cachedTeamName = getLocalTeamName(cleanCode)
        if (!cachedTeamName.isNullOrBlank()) {
            onComplete(Result.success(cleanCode to cachedTeamName))
            return
        }

        // 2. Check Firestore with a 1.5-second timeout guard so UI never hangs
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finishOnce(result: Result<Pair<String, String>>) {
            if (completed.compareAndSet(false, true)) {
                onComplete(result)
            }
        }

        try {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                finishOnce(
                    Result.failure(
                        IllegalArgumentException("Invalid team code. Please check with your Team Leader.")
                    )
                )
            }, 1500L)
        } catch (_: Exception) {
            // Ignored in unit tests without MainLooper
        }

        try {
            db.collection(TEAMS_COLLECTION)
                .document(cleanCode)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val foundTeamName = snapshot.getString("teamName").orEmpty()
                        if (foundTeamName.isNotBlank()) {
                            registerLocalTeam(cleanCode, foundTeamName)
                            finishOnce(Result.success(cleanCode to foundTeamName))
                            return@addOnSuccessListener
                        }
                    }
                    finishOnce(
                        Result.failure(
                            IllegalArgumentException("Invalid team code. Please check with your Team Leader.")
                        )
                    )
                }
                .addOnFailureListener {
                    finishOnce(
                        Result.failure(
                            IllegalArgumentException("Invalid team code. Please check with your Team Leader.")
                        )
                    )
                }
        } catch (_: Exception) {
            finishOnce(
                Result.failure(
                    IllegalArgumentException("Invalid team code. Please check with your Team Leader.")
                )
            )
        }
    }

    /**
     * Updates the user's role, team, and optional teamCode in Firestore: /users/{uid}
     * Uses SetOptions.merge() in the background and completes immediately so UI never blocks.
     */
    fun updateUserRoleAndTeam(
        uid: String,
        role: String,
        teamName: String,
        teamCode: String = "",
        email: String = "",
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        cacheUserRoleAndTeam(uid, email, role, teamName, teamCode)
        val cleanEmail = email.trim()
        val updates = mutableMapOf<String, Any>(
            "role" to role,
            "teamName" to teamName
        )
        if (uid.isNotBlank()) {
            updates["uid"] = uid
        }
        if (cleanEmail.isNotBlank()) {
            updates["email"] = cleanEmail
        }
        if (teamCode.isNotBlank()) {
            updates["teamCode"] = teamCode
        }
        if (uid.isNotBlank()) {
            try {
                db.collection(USERS_COLLECTION)
                    .document(uid)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
            } catch (_: Exception) {
                // Synced in background
            }
        }
        if (cleanEmail.isNotBlank()) {
            try {
                db.collection(USERS_COLLECTION)
                    .whereEqualTo("email", cleanEmail)
                    .get()
                    .addOnSuccessListener { querySnap ->
                        for (doc in querySnap.documents) {
                            doc.reference.set(updates, com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
            } catch (_: Exception) {
                // Synced in background
            }
        }
        onComplete(Result.success(Unit))
    }

    const val PASSWORD_RESETS_COLLECTION = "password_resets"

    /**
     * Records an updated password in Firestore under /password_resets/{email}.
     * Also updates any matching user document in /users/.
     */
    fun savePasswordReset(email: String, newPassword: String, onComplete: (Result<Unit>) -> Unit = {}) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || newPassword.isBlank()) {
            onComplete(Result.failure(IllegalArgumentException("Email and password cannot be blank")))
            return
        }

        try {
            val data = mapOf(
                "email" to cleanEmail,
                "password" to newPassword,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(PASSWORD_RESETS_COLLECTION)
                .document(cleanEmail)
                .set(data)
                .addOnSuccessListener {
                    // Also query /users where email == cleanEmail to update profile resetPassword
                    db.collection(USERS_COLLECTION)
                        .whereEqualTo("email", email.trim())
                        .get()
                        .addOnSuccessListener { querySnap ->
                            for (doc in querySnap.documents) {
                                doc.reference.update("resetPassword", newPassword)
                            }
                            onComplete(Result.success(Unit))
                        }
                        .addOnFailureListener {
                            onComplete(Result.success(Unit))
                        }
                }
                .addOnFailureListener { exception ->
                    onComplete(Result.failure(exception))
                }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Checks if a password reset document exists for this email.
     */
    fun getPasswordReset(email: String, onComplete: (Result<String?>) -> Unit) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) {
            onComplete(Result.success(null))
            return
        }

        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finishOnce(result: Result<String?>) {
            if (completed.compareAndSet(false, true)) {
                onComplete(result)
            }
        }

        try {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                finishOnce(Result.success(null))
            }, 1500L)
        } catch (_: Exception) {
            // Ignored in unit tests
        }

        try {
            db.collection(PASSWORD_RESETS_COLLECTION)
                .document(cleanEmail)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val pass = snapshot.getString("password")
                        finishOnce(Result.success(pass))
                    } else {
                        finishOnce(Result.success(null))
                    }
                }
                .addOnFailureListener {
                    finishOnce(Result.success(null))
                }
        } catch (_: Exception) {
            finishOnce(Result.success(null))
        }
    }

    /**
     * Finds a user profile by email from the /users collection.
     */
    fun getUserByEmail(email: String, onComplete: (Result<UserProfile?>) -> Unit) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            onComplete(Result.success(null))
            return
        }

        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finishOnce(profile: UserProfile?) {
            if (completed.compareAndSet(false, true)) {
                val cached = localUserRoleTeamByEmail[cleanEmail.lowercase()]
                    ?: profile?.uid?.let { localUserRoleTeam[it] }
                val merged = if (profile != null && cached != null) {
                    profile.copy(
                        role = cached.first,
                        teamName = cached.second,
                        teamCode = cached.third
                    )
                } else if (profile == null && cached != null) {
                    UserProfile(
                        uid = "user_${cleanEmail.hashCode()}",
                        username = cleanEmail.substringBefore("@"),
                        email = cleanEmail,
                        role = cached.first,
                        teamName = cached.second,
                        teamCode = cached.third
                    )
                } else {
                    profile
                }
                onComplete(Result.success(merged))
            }
        }

        try {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                finishOnce(null)
            }, 2000L)
        } catch (_: Exception) {
            // Ignored in unit tests
        }

        try {
            db.collection(USERS_COLLECTION)
                .whereEqualTo("email", cleanEmail)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnap ->
                    if (querySnap != null && !querySnap.isEmpty) {
                        val doc = querySnap.documents[0]
                        val data = doc.data
                        if (data != null) {
                            finishOnce(UserProfile.fromMap(data))
                            return@addOnSuccessListener
                        }
                    }
                    finishOnce(null)
                }
                .addOnFailureListener {
                    finishOnce(null)
                }
        } catch (_: Exception) {
            finishOnce(null)
        }
    }
}
