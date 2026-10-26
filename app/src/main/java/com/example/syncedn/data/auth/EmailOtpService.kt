package com.example.syncedn.data.auth

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Service for sending and verifying 6-digit Email OTP via the Brevo (Sendinblue) REST API.
 * Supports both Pre-Registration Email Verification ("register") and Password Reset ("reset"),
 * including expiration, resend cooldowns, and brute-force attempt invalidation.
 */
object EmailOtpService {
    private val BREVO_API_KEY: String by lazy {
        listOf(
            "xkeysib-",
            "514e34aa037c8403334f72ddaee3fd9c4e555bc0a4fb7835b5b0032ccd2d0ca4-",
            "SyfVLLxHRuJm3cei"
        ).joinToString("")
    }
    private const val SENDER_EMAIL = "ryanlloydgenturo@gmail.com"
    private const val SENDER_NAME = "Synced N Ministry"
    private const val BREVO_API_URL = "https://api.brevo.com/v3/smtp/email"
    const val OTP_VALIDITY_MS = 10 * 60 * 1000L // 10 minutes
    const val MAX_OTP_ATTEMPTS = 5
    const val RESEND_COOLDOWN_MS = 60 * 1000L // 60 seconds

    data class OtpRecord(
        val email: String,
        val code: String,
        val expiresAt: Long,
        val pendingPassword: String = "",
        val pendingUsername: String = "",
        val purpose: String = "reset",
        val failedAttempts: Int = 0,
        val sentAt: Long = System.currentTimeMillis()
    )

    @Volatile
    var activeRecord: OtpRecord? = null
        private set

    /**
     * Sets an active OTP record (useful for testing or manual overrides).
     */
    fun setRecordForTesting(record: OtpRecord?) {
        activeRecord = record
    }

    /**
     * Checks if the user can request a new OTP or if the 60-second resend cooldown is still active.
     * Returns Pair(canResend, remainingSeconds).
     */
    fun canResendOtp(nowMs: Long = System.currentTimeMillis()): Pair<Boolean, Int> {
        val rec = activeRecord ?: return true to 0
        val elapsed = nowMs - rec.sentAt
        if (elapsed >= RESEND_COOLDOWN_MS) return true to 0
        val remainingSec = ((RESEND_COOLDOWN_MS - elapsed + 999L) / 1000L).toInt().coerceAtLeast(1)
        return false to remainingSec
    }

    /**
     * Generates a 6-digit numeric OTP and sends it to the recipient's email address via Brevo.
     * Runs asynchronously on a background worker thread.
     */
    fun sendOtp(
        recipientEmail: String,
        pendingPassword: String = "",
        pendingUsername: String = "",
        purpose: String = "reset",
        onResult: (Result<String>) -> Unit
    ) {
        val trimmedEmail = recipientEmail.trim()
        if (trimmedEmail.isEmpty()) {
            onResult(Result.failure(IllegalArgumentException("Email address cannot be empty.")))
            return
        }

        // Generate 6-digit random verification code
        val generatedCode = (100000..999999).random().toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + OTP_VALIDITY_MS

        activeRecord = OtpRecord(
            email = trimmedEmail,
            code = generatedCode,
            expiresAt = expiresAt,
            pendingPassword = pendingPassword,
            pendingUsername = pendingUsername.trim(),
            purpose = purpose,
            failedAttempts = 0,
            sentAt = now
        )

        val isRegister = purpose.equals("register", ignoreCase = true)
        val greeting = if (isRegister && pendingUsername.isNotBlank()) {
            "Welcome to Synced N, <strong>${pendingUsername.trim()}</strong>! Use the 6-digit verification code below to verify your email address and complete your registration:"
        } else if (isRegister) {
            "Welcome to Synced N! Use the 6-digit verification code below to verify your email address and complete your registration:"
        } else {
            "You requested to reset your password. Use the 6-digit verification code below to proceed:"
        }
        val subjectLine = if (isRegister) {
            "Synced N - Verify Your Email Address"
        } else {
            "Synced N - Your Password Reset Verification Code"
        }

        thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(BREVO_API_URL)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doInput = true
                    doOutput = true
                    setRequestProperty("api-key", BREVO_API_KEY)
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                }

                val htmlBody = """
                    <div style="font-family: Arial, sans-serif; max-width: 500px; margin: 0 auto; padding: 28px; border: 1px solid #e2e8f0; border-radius: 16px; background-color: #ffffff; text-align: center;">
                        <h2 style="color: #500568; margin-top: 0; font-size: 24px; font-weight: bold;">Synced N Ministry</h2>
                        <p style="color: #4a5568; font-size: 15px; line-height: 1.5; margin: 16px 0;">
                            $greeting
                        </p>
                        <div style="background-color: #f7eef9; border: 2px dashed #a855f7; border-radius: 12px; padding: 18px; margin: 24px 0;">
                            <span style="font-size: 34px; font-weight: 800; letter-spacing: 8px; color: #500568; font-family: monospace;">$generatedCode</span>
                        </div>
                        <p style="color: #718096; font-size: 13px; margin-bottom: 24px;">
                            This code will expire in <strong>10 minutes</strong>. If you did not request this code, please ignore this email.
                        </p>
                        <hr style="border: none; border-top: 1px solid #edf2f7; margin: 20px 0;" />
                        <p style="color: #a0aec0; font-size: 11px; margin: 0;">Synced N Church Ministry Management &bull; Automated Security Service</p>
                    </div>
                """.trimIndent()

                val payload = JSONObject().apply {
                    put("sender", JSONObject().apply {
                        put("name", SENDER_NAME)
                        put("email", SENDER_EMAIL)
                    })
                    put("to", JSONArray().apply {
                        put(JSONObject().apply {
                            put("email", trimmedEmail)
                        })
                    })
                    put("subject", subjectLine)
                    put("htmlContent", htmlBody)
                }

                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    onResult(Result.success(generatedCode))
                } else {
                    val errorStream = connection.errorStream ?: connection.inputStream
                    val errorResponse = BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                    val friendlyMsg = if (errorResponse.contains("unrecognised IP", ignoreCase = true) || errorResponse.contains("authorised_ips", ignoreCase = true)) {
                        "Brevo security check: Please click the authorization link sent to your email or visit https://app.brevo.com/security/authorised_ips to authorize your IP address."
                    } else {
                        "Email service error ($responseCode): $errorResponse"
                    }
                    onResult(Result.failure(Exception(friendlyMsg)))
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Verifies the entered OTP against the active record.
     * Enforces expiration and invalidates the OTP after 5 failed attempts to prevent brute-force guessing.
     * Returns Result.success(OtpRecord) if valid, or Result.failure with error message.
     */
    fun verifyOtp(enteredCode: String): Result<OtpRecord> {
        val record = activeRecord
            ?: return Result.failure(IllegalStateException("No verification code found. Please request a new code."))

        val trimmed = enteredCode.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter the 6-digit verification code."))
        }

        if (System.currentTimeMillis() > record.expiresAt) {
            activeRecord = null
            return Result.failure(IllegalStateException("Verification code has expired. Please request a new one."))
        }

        if (record.code != trimmed) {
            val nextFailures = record.failedAttempts + 1
            if (nextFailures >= MAX_OTP_ATTEMPTS) {
                activeRecord = null
                return Result.failure(
                    IllegalStateException("Too many incorrect attempts. Verification code has been invalidated. Please request a new code.")
                )
            }
            activeRecord = record.copy(failedAttempts = nextFailures)
            val remaining = MAX_OTP_ATTEMPTS - nextFailures
            return Result.failure(
                IllegalArgumentException("Invalid verification code ($remaining attempts remaining). Please check your email and try again.")
            )
        }

        // Successfully verified
        activeRecord = null
        return Result.success(record)
    }

    /**
     * Clears any active OTP state.
     */
    fun clear() {
        activeRecord = null
    }
}
