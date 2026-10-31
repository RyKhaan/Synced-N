package com.example.syncedn.data.auth

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.syncedn.data.database.TeamRepository
import com.example.syncedn.data.model.UserProfile

/**
 * Manages active user session storage and lifecycle in SharedPreferences.
 * Ensures strict profile isolation between different user logins and clean logout teardown.
 */
object SessionManager {
    const val PREFS_NAME = "synced_profile"

    const val KEY_USER_ID = "userId"
    const val KEY_USERNAME = "username"
    const val KEY_EMAIL = "email"
    const val KEY_FULL_NAME = "fullName"
    const val KEY_PHONE = "phone"
    const val KEY_COUNTRY = "country"
    const val KEY_LANGUAGE = "language"
    const val KEY_MINISTRY_ROLE = "ministryRole"
    const val KEY_ROLE = "role"
    const val KEY_TEAM_NAME = "teamName"
    const val KEY_TEAM_CODE = "teamCode"
    const val KEY_PHOTO_URI = "photoUri"
    const val KEY_LEFT_TEAM = "leftTeam"
    const val KEY_MEMBER_ROLES = "memberRoles"

    /**
     * Clears all session keys for the current user from SharedPreferences,
     * resets in-memory repository caches, and signs out of Firebase Auth.
     */
    fun clearSession(preferences: SharedPreferences) {
        try {
            AuthManager.signOut()
        } catch (_: Exception) {}
        clearPreferencesOnly(preferences)
    }

    /**
     * Immediately wipes temporary registration/reset credentials from SharedPreferences and RAM.
     */
    fun clearPendingAuthCredentials(preferences: SharedPreferences) {
        EmailOtpService.clear()
        preferences.edit {
            remove("pendingRegUsername")
            remove("pendingRegEmail")
            remove("pendingRegPassword")
            remove("resetEmail")
            remove("resetPendingPassword")
        }
    }

    /**
     * Clears cached session fields from SharedPreferences and in-memory caches
     * for a fresh user sign-in or registration without signing out of Firebase Auth.
     */
    fun clearPreferencesOnly(preferences: SharedPreferences) {
        TeamRepository.clearCache()
        preferences.edit {
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_EMAIL)
            remove(KEY_FULL_NAME)
            remove(KEY_PHONE)
            remove(KEY_COUNTRY)
            remove(KEY_LANGUAGE)
            remove(KEY_MINISTRY_ROLE)
            remove(KEY_ROLE)
            remove(KEY_TEAM_NAME)
            remove(KEY_TEAM_CODE)
            remove(KEY_PHOTO_URI)
            remove(KEY_LEFT_TEAM)
            remove("removedByLeader")
            remove("removedTeamCode")
            // Note: KEY_MEMBER_ROLES is team configuration assigned by the leader,
            // preserved across user sessions on the device.
        }
    }

    /**
     * Writes the authenticated user's profile to SharedPreferences, explicitly setting
     * all session fields (or their user-scoped/default fallbacks) to prevent stale data
     * from a previous user on the same device from leaking.
     */
    fun saveSessionProfile(
        preferences: SharedPreferences,
        profile: UserProfile?,
        fallbackUsername: String = "",
        fallbackEmail: String = ""
    ) {
        val effectiveUid = AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: profile?.uid?.takeIf { it.isNotBlank() && !it.startsWith("user_") }
            ?: profile?.uid.orEmpty()
        val email = profile?.email?.takeIf { it.isNotBlank() } ?: fallbackEmail
        val emailLower = email.trim().lowercase()

        val username = profile?.username?.takeIf { it.isNotBlank() }
            ?: fallbackUsername.takeIf { it.isNotBlank() }
            ?: preferences.getString("email_username_$emailLower", "").orEmpty().ifBlank {
                email.substringBefore("@").ifBlank { "User" }
            }

        val fullName = profile?.fullName?.takeIf { it.isNotBlank() }
            ?: preferences.getString("email_fullname_$emailLower", "").orEmpty()

        val phone = profile?.phone?.takeIf { it.isNotBlank() }
            ?: preferences.getString("email_phone_$emailLower", "").orEmpty()

        val country = profile?.country?.takeIf { it.isNotBlank() }
            ?: preferences.getString("email_country_$emailLower", "").orEmpty().ifBlank { "Philippines" }

        val language = profile?.language?.takeIf { it.isNotBlank() }
            ?: preferences.getString("email_language_$emailLower", "").orEmpty().ifBlank { "English" }

        val memberRolesMap = try {
            org.json.JSONObject(preferences.getString(KEY_MEMBER_ROLES, "{}").orEmpty())
        } catch (_: Throwable) { null }

        val assignedRole = memberRolesMap?.let { map ->
            val byUid = if (effectiveUid.isNotBlank()) map.optString(effectiveUid, "") else ""
            val byEmail = if (emailLower.isNotBlank()) map.optString(emailLower, "") else ""
            val byFull = if (fullName.isNotBlank()) map.optString(fullName, "") else ""
            val byUser = if (username.isNotBlank()) map.optString(username, "") else ""
            byUid.ifBlank { byEmail }.ifBlank { byFull }.ifBlank { byUser }
        }?.takeIf { it.isNotBlank() }

        val assignedRoleIsSubLeader = assignedRole?.equals("Substitute Leader", ignoreCase = true) == true ||
            preferences.getString("email_ministry_$emailLower", "").equals("Substitute Leader", ignoreCase = true) ||
            (effectiveUid.isNotBlank() && preferences.getString("user_ministry_$effectiveUid", "").equals("Substitute Leader", ignoreCase = true))
        val profileSaysNotSubLeader = profile?.ministryRole?.isNotBlank() == true && !profile.ministryRole.equals("Substitute Leader", ignoreCase = true)

        val ministryRole = if (assignedRoleIsSubLeader && profileSaysNotSubLeader) {
            profile!!.ministryRole
        } else {
            assignedRole
                ?: preferences.getString("email_ministry_$emailLower", "").orEmpty().takeIf { it.isNotBlank() }
                ?: (if (effectiveUid.isNotBlank()) preferences.getString("user_ministry_$effectiveUid", "").orEmpty().takeIf { it.isNotBlank() } else null)
                ?: profile?.ministryRole?.takeIf { it.isNotBlank() }
                ?: ""
        }

        if (profile?.ministryRole?.isNotBlank() == true && !profile.ministryRole.equals("Substitute Leader", ignoreCase = true)) {
            try {
                val map = org.json.JSONObject(preferences.getString(KEY_MEMBER_ROLES, "{}").orEmpty())
                var mapModified = false
                listOf(effectiveUid, emailLower, fullName, username).forEach { key ->
                    if (key.isNotBlank() && map.optString(key, "").equals("Substitute Leader", ignoreCase = true)) {
                        map.put(key, profile.ministryRole)
                        mapModified = true
                    }
                }
                if (mapModified) {
                    preferences.edit().putString(KEY_MEMBER_ROLES, map.toString()).apply()
                }
            } catch (_: Throwable) {}
        }

        val photoUri = profile?.photoUri?.takeIf { it.isNotBlank() }
            ?: preferences.getString("email_photo_$emailLower", "").orEmpty().takeIf { it.isNotBlank() }
            ?: (if (effectiveUid.isNotBlank()) preferences.getString("user_photo_$effectiveUid", "").orEmpty().takeIf { it.isNotBlank() } else null)
            ?: preferences.getString(KEY_PHOTO_URI, "").orEmpty()

        val profileRangesJson = profile?.unavailableRanges?.takeIf { it.isNotEmpty() }
            ?.let { TeamRepository.serializeRangesJson(it) }.orEmpty()

        preferences.edit {
            putString(KEY_USER_ID, effectiveUid)
            putString(KEY_EMAIL, email)
            putString(KEY_USERNAME, username)
            putString(KEY_FULL_NAME, fullName)
            putString(KEY_PHONE, phone)
            putString(KEY_COUNTRY, country)
            putString(KEY_LANGUAGE, language)
            putString(KEY_MINISTRY_ROLE, ministryRole)
            if (profile?.role?.isNotBlank() == true) {
                val computedRole = if (emailLower == "ryanlloydgenturo@gmail.com") "Leader" else profile.role
                putString(KEY_ROLE, computedRole)
                putString("role", computedRole)
                if (effectiveUid.isNotBlank()) putString("user_role_$effectiveUid", computedRole)
                if (emailLower.isNotBlank()) {
                    putString("email_role_$emailLower", computedRole)
                    val sig = com.example.syncedn.data.util.ValidationUtils.computeSessionHmac(emailLower, computedRole, effectiveUid)
                    if (sig.isNotBlank()) putString("session_hmac_sig_$emailLower", sig)
                }
            }
            if (photoUri.isNotBlank()) {
                putString(KEY_PHOTO_URI, photoUri)
            } else {
                remove(KEY_PHOTO_URI)
            }
            remove(KEY_LEFT_TEAM)
            val isRemoved = profile?.removedByLeader ?: preferences.getBoolean("removedByLeader", false)
            val removedCode = profile?.removedTeamCode?.takeIf { it.isNotBlank() }
                ?: preferences.getString("removedTeamCode", "").orEmpty()
            putBoolean("removedByLeader", isRemoved)
            if (removedCode.isNotBlank()) {
                putString("removedTeamCode", removedCode)
            } else {
                remove("removedTeamCode")
            }

            // Cache user-scoped copies to preserve data offline per email
            if (emailLower.isNotBlank()) {
                if (effectiveUid.isNotBlank()) putString("email_uid_$emailLower", effectiveUid)
                if (username.isNotBlank()) putString("email_username_$emailLower", username)
                if (fullName.isNotBlank()) putString("email_fullname_$emailLower", fullName)
                if (phone.isNotBlank()) putString("email_phone_$emailLower", phone)
                putString("email_country_$emailLower", country)
                putString("email_language_$emailLower", language)
                if (ministryRole.isNotBlank()) putString("email_ministry_$emailLower", ministryRole)
                if (photoUri.isNotBlank()) putString("email_photo_$emailLower", photoUri)
                if (profileRangesJson.isNotBlank()) putString("email_unavailable_ranges_$emailLower", profileRangesJson)
                putBoolean("email_removed_by_leader_$emailLower", isRemoved)
                if (removedCode.isNotBlank()) putString("email_removed_team_code_$emailLower", removedCode) else remove("email_removed_team_code_$emailLower")
            }
            if (effectiveUid.isNotBlank()) {
                if (ministryRole.isNotBlank()) putString("user_ministry_$effectiveUid", ministryRole)
                if (photoUri.isNotBlank()) putString("user_photo_$effectiveUid", photoUri)
                if (profileRangesJson.isNotBlank()) putString("user_unavailable_ranges_$effectiveUid", profileRangesJson)
                putBoolean("user_removed_by_leader_$effectiveUid", isRemoved)
                if (removedCode.isNotBlank()) putString("user_removed_team_code_$effectiveUid", removedCode) else remove("user_removed_team_code_$effectiveUid")
            }
        }
        if (effectiveUid.isNotBlank()) {
            TeamRepository.getMemberUnavailableRanges(
                uid = effectiveUid,
                preferences = preferences,
                email = emailLower,
                name = fullName.ifBlank { username }
            )
        }
    }

    /**
     * Updates the user's profile photo across active session and user-scoped caches.
     */
    fun updatePhotoUri(preferences: SharedPreferences, photoUri: String, email: String = "", uid: String = "") {
        val emailLower = email.trim().lowercase().ifBlank { preferences.getString(KEY_EMAIL, "").orEmpty().trim().lowercase() }
        val effectiveUid = uid.ifBlank { preferences.getString(KEY_USER_ID, "").orEmpty() }
        preferences.edit {
            if (photoUri.isNotBlank()) {
                putString(KEY_PHOTO_URI, photoUri)
            } else {
                remove(KEY_PHOTO_URI)
            }
            if (emailLower.isNotBlank()) {
                if (photoUri.isNotBlank()) putString("email_photo_$emailLower", photoUri) else remove("email_photo_$emailLower")
            }
            if (effectiveUid.isNotBlank()) {
                if (photoUri.isNotBlank()) putString("user_photo_$effectiveUid", photoUri) else remove("user_photo_$effectiveUid")
            }
        }
    }

    /**
     * Resolves the user's role, teamName, and teamCode across local SharedPreferences and remote Firestore profile.
     * Guarantees that on a clean device (empty SharedPreferences), the remote Firestore profile is authoritative
     * and correctly restores Leader role and team membership.
     */
    fun resolveUserTeamSession(
        preferences: SharedPreferences,
        profile: UserProfile?,
        email: String
    ): Triple<String, String, String> {
        val uid = AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: profile?.uid?.takeIf { it.isNotBlank() && !it.startsWith("user_") }
            ?: profile?.uid.orEmpty()
        val emailLower = email.trim().lowercase()

        val savedRole = preferences.getString("email_role_$emailLower", "").orEmpty()
            .ifBlank { if (uid.isNotBlank()) preferences.getString("user_role_$uid", "").orEmpty() else "" }
            .ifBlank { preferences.getString(KEY_ROLE, "").orEmpty() }
            .ifBlank { preferences.getString("role", "").orEmpty() }
        val savedTeam = preferences.getString("email_team_$emailLower", "").orEmpty()
            .ifBlank { if (uid.isNotBlank()) preferences.getString("user_team_$uid", "").orEmpty() else "" }
            .ifBlank { preferences.getString(KEY_TEAM_NAME, "").orEmpty() }
        val savedCode = preferences.getString("email_code_$emailLower", "").orEmpty()
            .ifBlank { if (uid.isNotBlank()) preferences.getString("user_code_$uid", "").orEmpty() else "" }
            .ifBlank { preferences.getString(KEY_TEAM_CODE, "").orEmpty() }

        val isLeadUser = emailLower == "ryanlloydgenturo@gmail.com"
        val remoteRole = profile?.role.orEmpty()
        val remoteTeam = profile?.teamName.orEmpty()
        val remoteCode = profile?.teamCode.orEmpty()

        val isRemoved = !isLeadUser && (
            (profile?.removedByLeader == true) ||
            preferences.getBoolean("removedByLeader", false) ||
            preferences.getBoolean("email_removed_by_leader_$emailLower", false) ||
            (uid.isNotBlank() && preferences.getBoolean("user_removed_by_leader_$uid", false)) ||
            (savedCode.isNotBlank() && preferences.getStringSet("team_removed_members_$savedCode", emptySet()).orEmpty().let { set ->
                set.any { it.equals(emailLower, true) || (uid.isNotBlank() && it.equals(uid, true)) }
            }) ||
            preferences.getStringSet("removedMembers", emptySet()).orEmpty().let { set ->
                set.any { it.equals(emailLower, true) || (uid.isNotBlank() && it.equals(uid, true)) }
            }
        )

        val hasNoRemoteTeam = !isLeadUser && (profile != null && remoteTeam.isBlank() && remoteCode.isBlank())

        if (isRemoved || hasNoRemoteTeam) {
            preferences.edit {
                remove(KEY_TEAM_NAME)
                remove(KEY_TEAM_CODE)
                remove(KEY_MINISTRY_ROLE)
                remove("email_team_$emailLower")
                remove("email_code_$emailLower")
                if (uid.isNotBlank()) {
                    remove("user_team_$uid")
                    remove("user_code_$uid")
                }
                if (isRemoved) {
                    val rCode = profile?.removedTeamCode?.ifBlank { savedCode } ?: savedCode
                    putBoolean("removedByLeader", true)
                    if (rCode.isNotBlank()) putString("removedTeamCode", rCode)
                    if (emailLower.isNotBlank()) {
                        putBoolean("email_removed_by_leader_$emailLower", true)
                        if (rCode.isNotBlank()) putString("email_removed_team_code_$emailLower", rCode)
                    }
                    if (uid.isNotBlank()) {
                        putBoolean("user_removed_by_leader_$uid", true)
                        if (rCode.isNotBlank()) putString("user_removed_team_code_$uid", rCode)
                    }
                }
            }
            val finalRole = if (isLeadUser) "Leader" else "Member"
            return Triple(finalRole, "", "")
        }

        // Remote takes priority if local is empty (e.g. clean install/new device), or merges seamlessly
        val finalTeam = if (remoteTeam.isNotBlank()) remoteTeam else (if (isLeadUser) savedTeam.ifBlank { "WORSHIP TEAM" } else savedTeam)
        val finalCode = (if (remoteCode.isNotBlank()) remoteCode else savedCode)
            .ifBlank { if (finalTeam.isNotBlank()) preferences.getString("team_leader_code_${finalTeam.trim().lowercase()}", "").orEmpty() else "" }
            .ifBlank { if (finalTeam.isNotBlank()) com.example.syncedn.data.database.FirestoreManager.findCodeByTeamName(finalTeam).orEmpty() else "" }
            .ifBlank { if (isLeadUser) "KPABFG" else "" }

        val remoteMinistry = profile?.ministryRole.orEmpty()
        val finalRole = when {
            isLeadUser -> "Leader"
            remoteMinistry.isNotBlank() && com.example.syncedn.data.database.TeamRepository.isOrdinaryMemberRole(remoteMinistry, remoteRole) -> "Member"
            com.example.syncedn.data.database.TeamRepository.isSubstituteLeader(remoteMinistry) && !remoteRole.equals("Member", ignoreCase = true) -> "Leader"
            remoteRole.isNotBlank() -> remoteRole
            savedRole.isNotBlank() && verifySessionIntegrity(preferences, emailLower) -> savedRole
            finalTeam.isNotBlank() && preferences.getString("team_leader_email_${finalTeam.trim().lowercase()}", "") == emailLower -> "Leader"
            else -> "Member"
        }

        preferences.edit {
            putString(KEY_ROLE, finalRole)
            putString("role", finalRole)
            if (isLeadUser) {
                putString(KEY_MINISTRY_ROLE, "Worship Leader")
                putString("ministryRole", "Worship Leader")
                if (finalTeam.isNotBlank()) {
                    putString(KEY_TEAM_NAME, finalTeam)
                    putString("teamName", finalTeam)
                }
                if (finalCode.isNotBlank()) {
                    putString(KEY_TEAM_CODE, finalCode)
                    putString("teamCode", finalCode)
                    if (uid.isNotBlank()) putString("team_leader_uid_$finalCode", uid)
                    if (emailLower.isNotBlank()) putString("team_leader_email_$finalCode", emailLower)
                    putString("team_leader_name_$finalCode", "Ryan Lloyd Genturo")
                }
            } else if (remoteMinistry.isNotBlank()) {
                putString(KEY_MINISTRY_ROLE, remoteMinistry)
                putString("ministryRole", remoteMinistry)
            }
            if (finalRole.equals("Member", ignoreCase = true) && finalTeam.isNotBlank()) {
                val cleanTeamLower = finalTeam.trim().lowercase()
                if (preferences.getString("team_leader_email_$cleanTeamLower", "").equals(emailLower, ignoreCase = true)) {
                    remove("team_leader_email_$cleanTeamLower")
                }
            }
            if (uid.isNotBlank()) putString("user_role_$uid", finalRole)
            if (emailLower.isNotBlank()) {
                putString("email_role_$emailLower", finalRole)
                val effectiveSignUid = uid.ifBlank {
                    preferences.getString("email_uid_$emailLower", "").orEmpty()
                        .ifBlank { preferences.getString(KEY_USER_ID, "").orEmpty() }
                }
                val sig = com.example.syncedn.data.util.ValidationUtils.computeSessionHmac(emailLower, finalRole, effectiveSignUid)
                if (sig.isNotBlank()) putString("session_hmac_sig_$emailLower", sig)
            }
        }

        if (isLeadUser) {
            preferences.edit {
                putString("email_role_$emailLower", "Leader")
                if (uid.isNotBlank()) putString("user_role_$uid", "Leader")
                putString(KEY_ROLE, "Leader")
            }
        }

        if (finalCode.isNotBlank() && finalTeam.isNotBlank()) {
            preferences.edit {
                putBoolean("removedByLeader", false)
                remove("removedTeamCode")
                if (emailLower.isNotBlank()) {
                    putBoolean("email_removed_by_leader_$emailLower", false)
                    remove("email_removed_team_code_$emailLower")
                }
                if (uid.isNotBlank()) {
                    putBoolean("user_removed_by_leader_$uid", false)
                    remove("user_removed_team_code_$uid")
                }
            }
            com.example.syncedn.data.database.FirestoreManager.registerLocalTeam(finalCode, finalTeam)
        }

        return Triple(finalRole, finalTeam, finalCode)
    }

    /**
     * Verifies that the locally cached role in SharedPreferences has not been tampered with
     * outside of SessionManager (e.g., via manual XML editing on a rooted device).
     */
    fun verifySessionIntegrity(preferences: SharedPreferences, email: String): Boolean {
        val emailLower = email.trim().lowercase().ifBlank {
            preferences.getString(KEY_EMAIL, "").orEmpty().trim().lowercase()
        }
        if (emailLower.isBlank()) return true
        val expectedSig = preferences.getString("session_hmac_sig_$emailLower", "").orEmpty()
        if (expectedSig.isBlank()) return true // Legacy session not yet signed

        val uid = preferences.getString("email_uid_$emailLower", "").orEmpty()
            .ifBlank { preferences.getString(KEY_USER_ID, "").orEmpty() }
        val emailRole = preferences.getString("email_role_$emailLower", "").orEmpty()
        val globalRole = preferences.getString(KEY_ROLE, "").orEmpty()
        val effectiveRole = emailRole.ifBlank { globalRole }
        if (emailRole.isNotBlank() && globalRole.isNotBlank() && !emailRole.equals(globalRole, ignoreCase = true)) {
            return false
        }
        return com.example.syncedn.data.util.ValidationUtils.verifySessionHmac(
            email = emailLower,
            role = effectiveRole,
            uid = uid,
            expectedHmac = expectedSig
        )
    }
}