package com.example.syncedn.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility functions for input validation, password policy enforcement, email security,
 * brute-force rate limiting, network connectivity, and character constraints across Synced N.
 */
object ValidationUtils {
    // Standard Philippine Mobile Number format:
    // - 09XXXXXXXXX (11 digits, begins with 09)
    // - +639XXXXXXXXX (13 characters, begins with +639)
    // - 639XXXXXXXXX (12 digits, begins with 639)
    private val PH_PHONE_REGEX = Regex("""^(\+639\d{9}|639\d{9}|09\d{9})$""")
    private val STRICT_EMAIL_REGEX = Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,10}$""")

    private val DISPOSABLE_OR_FAKE_DOMAINS = setOf(
        "tempmail.com", "temp-mail.org", "mailinator.com", "10minutemail.com",
        "guerrillamail.com", "yopmail.com", "trashmail.com", "fakeinbox.com",
        "sharklasers.com", "getnada.com", "throwawaymail.com", "maildrop.cc",
        "dispostable.com", "fake.com", "test.invalid", "invalid.com",
        "mailnesia.com", "mohmal.com", "mytemp.email"
    )

    const val MAX_NAME_LENGTH = 50
    const val MAX_USERNAME_LENGTH = 30
    const val MAX_PHONE_LENGTH = 13
    const val MAX_EMAIL_LENGTH = 60
    const val MAX_TITLE_LENGTH = 100
    const val MAX_REASON_LENGTH = 100
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 128

    const val MAX_LOGIN_ATTEMPTS = 5
    const val LOGIN_LOCKOUT_DURATION_MS = 60_000L

    private data class LoginAttemptState(
        val failedCount: Int,
        val lockedUntilMs: Long
    )

    private val loginAttempts = ConcurrentHashMap<String, LoginAttemptState>()

    /**
     * Detailed result of evaluating a password against Play Store / App Store production security criteria.
     */
    data class PasswordStrength(
        val hasMinLength: Boolean,
        val hasUpper: Boolean,
        val hasLower: Boolean,
        val hasDigit: Boolean,
        val hasSpecial: Boolean,
        val withinMaxLength: Boolean = true
    ) {
        val score: Int
            get() = listOf(hasMinLength, hasUpper, hasLower, hasDigit, hasSpecial).count { it }

        val isStrong: Boolean
            get() = hasMinLength && hasUpper && hasLower && hasDigit && hasSpecial && withinMaxLength

        val label: String
            get() = when {
                isStrong -> "Strong"
                score >= 4 -> "Good"
                score >= 2 -> "Fair"
                else -> "Weak"
            }

        fun errorMessage(): String? {
            if (isStrong) return null
            if (!withinMaxLength) return "Password cannot exceed $MAX_PASSWORD_LENGTH characters."
            val missing = mutableListOf<String>()
            if (!hasMinLength) missing.add("at least $MIN_PASSWORD_LENGTH characters")
            if (!hasUpper) missing.add("an uppercase letter (A-Z)")
            if (!hasLower) missing.add("a lowercase letter (a-z)")
            if (!hasDigit) missing.add("a number (0-9)")
            if (!hasSpecial) missing.add("a special character (!@#$%^&*)")
            return "Password must include ${missing.joinToString(", ")}."
        }
    }

    /**
     * Evaluates a password against the required security rules:
     * - Minimum 8 characters (up to 128)
     * - At least 1 uppercase letter (A-Z)
     * - At least 1 lowercase letter (a-z)
     * - At least 1 number (0-9)
     * - At least 1 special character (!@#$%^&* etc.)
     */
    fun evaluatePassword(password: String): PasswordStrength {
        val hasMin = password.length >= MIN_PASSWORD_LENGTH
        val withinMax = password.length <= MAX_PASSWORD_LENGTH
        val hasUp = password.any { it.isUpperCase() }
        val hasLow = password.any { it.isLowerCase() }
        val hasNum = password.any { it.isDigit() }
        val hasSpec = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        return PasswordStrength(
            hasMinLength = hasMin,
            hasUpper = hasUp,
            hasLower = hasLow,
            hasDigit = hasNum,
            hasSpecial = hasSpec,
            withinMaxLength = withinMax
        )
    }

    /**
     * Returns true if the password satisfies all strong password requirements.
     */
    fun isValidStrongPassword(password: String): Boolean {
        return evaluatePassword(password).isStrong
    }

    /**
     * Checks if an email address belongs to a known disposable, temporary, or fake domain.
     */
    fun isDisposableOrFakeEmail(email: String): Boolean {
        val clean = email.trim().lowercase()
        val domain = clean.substringAfterLast("@", "")
        if (domain.isBlank()) return true
        return domain in DISPOSABLE_OR_FAKE_DOMAINS ||
            domain.endsWith(".invalid") ||
            domain.endsWith(".test") ||
            domain.startsWith(".") ||
            domain.endsWith(".")
    }

    /**
     * Validates an email address for authentication and registration.
     * Returns null if valid, or a user-friendly error message if invalid or disposable.
     */
    fun validateEmailForAuth(email: String): String? {
        val clean = email.trim()
        if (clean.isBlank()) return "Enter your email address."
        if (clean.length > MAX_EMAIL_LENGTH) return "Email address cannot exceed $MAX_EMAIL_LENGTH characters."
        if (clean.contains("..")) return "Enter a valid email address."
        val localPart = clean.substringBefore("@", "")
        val domainPart = clean.substringAfter("@", "")
        if (localPart.isBlank() || localPart.startsWith(".") || localPart.endsWith(".")) {
            return "Enter a valid email address."
        }
        if (domainPart.isBlank() || domainPart.startsWith(".") || domainPart.endsWith(".") || !domainPart.contains(".")) {
            return "Enter a valid email address."
        }
        if (!clean.matches(STRICT_EMAIL_REGEX)) {
            return "Enter a valid email address."
        }
        if (isDisposableOrFakeEmail(clean)) {
            return "Disposable or temporary email addresses are not allowed. Please use an active email."
        }
        return null
    }

    /**
     * Checks whether login attempts for the given email are currently locked out due to brute-force protection.
     */
    fun isLoginLocked(email: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val key = email.trim().lowercase()
        val state = loginAttempts[key] ?: return false
        if (state.lockedUntilMs > nowMs) return true
        if (state.lockedUntilMs > 0L && nowMs >= state.lockedUntilMs) {
            loginAttempts.remove(key)
        }
        return false
    }

    /**
     * Returns the remaining lockout time in seconds for the given email, or 0 if not locked.
     */
    fun getLoginLockRemainingSeconds(email: String, nowMs: Long = System.currentTimeMillis()): Int {
        val key = email.trim().lowercase()
        val state = loginAttempts[key] ?: return 0
        val remainingMs = state.lockedUntilMs - nowMs
        if (remainingMs <= 0L) return 0
        return ((remainingMs + 999L) / 1000L).toInt()
    }

    /**
     * Records a failed login attempt for the given email.
     * Returns the number of remaining attempts before a 60-second lockout (0 if now locked).
     */
    fun recordLoginFailure(email: String, nowMs: Long = System.currentTimeMillis()): Int {
        val key = email.trim().lowercase()
        val current = loginAttempts[key]
        val baseCount = if (current != null && current.lockedUntilMs > 0L && nowMs >= current.lockedUntilMs) {
            0
        } else {
            current?.failedCount ?: 0
        }
        val nextCount = baseCount + 1
        return if (nextCount >= MAX_LOGIN_ATTEMPTS) {
            loginAttempts[key] = LoginAttemptState(nextCount, nowMs + LOGIN_LOCKOUT_DURATION_MS)
            0
        } else {
            loginAttempts[key] = LoginAttemptState(nextCount, 0L)
            MAX_LOGIN_ATTEMPTS - nextCount
        }
    }

    /**
     * Clears any recorded failed login attempts for the given email after a successful login.
     */
    fun resetLoginAttempts(email: String) {
        val key = email.trim().lowercase()
        if (key.isNotBlank()) {
            loginAttempts.remove(key)
        }
    }

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
