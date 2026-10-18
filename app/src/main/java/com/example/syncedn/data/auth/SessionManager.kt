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

        val ministryRole = assignedRole
            ?: preferences.getString("email_ministry_$emailLower", "").orEmpty().takeIf { it.isNotBlank() }
            ?: (if (effectiveUid.isNotBlank()) preferences.getString("user_ministry_$effectiveUid", "").orEmpty().takeIf { it.isNotBlank() } else null)
            ?: profile?.ministryRole?.takeIf { it.isNotBlank() }
            ?: ""

        preferences.edit {
            putString(KEY_USER_ID, effectiveUid)
            putString(KEY_EMAIL, email)
            putString(KEY_USERNAME, username)
            putString(KEY_FULL_NAME, fullName)
            putString(KEY_PHONE, phone)
            putString(KEY_COUNTRY, country)
            putString(KEY_LANGUAGE, language)
            putString(KEY_MINISTRY_ROLE, ministryRole)
            remove(KEY_PHOTO_URI)
            remove(KEY_LEFT_TEAM)

            // Cache user-scoped copies to preserve data offline per email
            if (emailLower.isNotBlank()) {
                if (effectiveUid.isNotBlank()) putString("email_uid_$emailLower", effectiveUid)
                if (username.isNotBlank()) putString("email_username_$emailLower", username)
                if (fullName.isNotBlank()) putString("email_fullname_$emailLower", fullName)
                if (phone.isNotBlank()) putString("email_phone_$emailLower", phone)
                putString("email_country_$emailLower", country)
                putString("email_language_$emailLower", language)
                if (ministryRole.isNotBlank()) putString("email_ministry_$emailLower", ministryRole)
            }
            if (effectiveUid.isNotBlank() && ministryRole.isNotBlank()) {
                putString("user_ministry_$effectiveUid", ministryRole)
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
        val savedTeam = preferences.getString("email_team_$emailLower", "").orEmpty()
            .ifBlank { if (uid.isNotBlank()) preferences.getString("user_team_$uid", "").orEmpty() else "" }
            .ifBlank { preferences.getString(KEY_TEAM_NAME, "").orEmpty() }
        val savedCode = preferences.getString("email_code_$emailLower", "").orEmpty()
            .ifBlank { if (uid.isNotBlank()) preferences.getString("user_code_$uid", "").orEmpty() else "" }
            .ifBlank { preferences.getString(KEY_TEAM_CODE, "").orEmpty() }

        val remoteRole = profile?.role.orEmpty()
        val remoteTeam = profile?.teamName.orEmpty()
        val remoteCode = profile?.teamCode.orEmpty()

        // Remote takes priority if local is empty (e.g. clean install/new device), or merges seamlessly
        val finalTeam = if (remoteTeam.isNotBlank()) remoteTeam else savedTeam
        val finalCode = (if (remoteCode.isNotBlank()) remoteCode else savedCode)
            .ifBlank { if (finalTeam.isNotBlank()) preferences.getString("team_leader_code_${finalTeam.trim().lowercase()}", "").orEmpty() else "" }
            .ifBlank { if (finalTeam.isNotBlank()) com.example.syncedn.data.database.FirestoreManager.findCodeByTeamName(finalTeam).orEmpty() else "" }

        val finalRole = when {
            remoteRole.equals("Leader", ignoreCase = true) || savedRole.equals("Leader", ignoreCase = true) -> "Leader"
            savedRole.isNotBlank() -> savedRole
            finalTeam.isNotBlank() && preferences.getString("team_leader_email_${finalTeam.trim().lowercase()}", "") == emailLower -> "Leader"
            remoteRole.isNotBlank() -> remoteRole
            else -> "Member"
        }

        if (finalCode.isNotBlank() && finalTeam.isNotBlank()) {
            com.example.syncedn.data.database.FirestoreManager.registerLocalTeam(finalCode, finalTeam)
        }

        return Triple(finalRole, finalTeam, finalCode)
    }
}
