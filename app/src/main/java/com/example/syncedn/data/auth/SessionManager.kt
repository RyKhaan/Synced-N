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
            remove(KEY_MEMBER_ROLES)
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
        val uid = profile?.uid.orEmpty()
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

        val ministryRole = profile?.ministryRole?.takeIf { it.isNotBlank() }
            ?: preferences.getString("email_ministry_$emailLower", "").orEmpty()

        preferences.edit {
            putString(KEY_USER_ID, uid)
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
                if (username.isNotBlank()) putString("email_username_$emailLower", username)
                if (fullName.isNotBlank()) putString("email_fullname_$emailLower", fullName)
                if (phone.isNotBlank()) putString("email_phone_$emailLower", phone)
                putString("email_country_$emailLower", country)
                putString("email_language_$emailLower", language)
                if (ministryRole.isNotBlank()) putString("email_ministry_$emailLower", ministryRole)
            }
        }
    }
}
