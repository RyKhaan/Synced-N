package com.example.syncedn.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Utility functions for input validation, network connectivity, and character constraints across Synced N.
 */
object ValidationUtils {
    // Standard Philippine Mobile Number format:
    // - 09XXXXXXXXX (11 digits, begins with 09)
    // - +639XXXXXXXXX (13 characters, begins with +639)
    // - 639XXXXXXXXX (12 digits, begins with 639)
    private val PH_PHONE_REGEX = Regex("""^(\+639\d{9}|639\d{9}|09\d{9})$""")

    const val MAX_NAME_LENGTH = 50
    const val MAX_USERNAME_LENGTH = 30
    const val MAX_PHONE_LENGTH = 13
    const val MAX_EMAIL_LENGTH = 60
    const val MAX_TITLE_LENGTH = 100
    const val MAX_REASON_LENGTH = 100

    /**
     * Checks if a phone number is a valid Philippine mobile number.
     * If allowBlank is true, an empty or blank string returns true (optional field).
     */
    fun isValidPhilippinePhone(phone: String, allowBlank: Boolean = true): Boolean {
        val clean = phone.trim()
        if (clean.isBlank()) return allowBlank
        return clean.matches(PH_PHONE_REGEX)
    }

    /**
     * Checks if a name is valid (non-blank and within maximum character limit).
     */
    fun isValidName(name: String): Boolean {
        val clean = name.trim()
        return clean.isNotBlank() && clean.length <= MAX_NAME_LENGTH
    }

    /**
     * Checks if a username is valid (non-blank and within maximum character limit).
     */
    fun isValidUsername(username: String): Boolean {
        val clean = username.trim()
        return clean.isNotBlank() && clean.length <= MAX_USERNAME_LENGTH
    }

    /**
     * Verifies if the device has an active internet connection.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = cm.activeNetwork ?: return false
                    val caps = cm.getNetworkCapabilities(network) ?: return false
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } else {
                    @Suppress("DEPRECATION")
                    val info = cm.activeNetworkInfo
                    info != null && info.isConnected
                }
            } else false
        } catch (_: Throwable) {
            true
        }
    }
}
