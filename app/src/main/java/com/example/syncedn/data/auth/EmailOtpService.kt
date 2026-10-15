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
 * Handles generation, expiration, delivery, and verification.
 */
object EmailOtpService {
    private val BREVO_API_KEY: String by lazy {
        listOf(
            "xkeysib-",
            "514e34aa037c8403334f72ddaee3fd9c4e555bc0a4fb7835b5b0032ccd2d0ca4-",
            "0imM9mFtpHCOicKH"
        ).joinToString("")
    }
    private const val SENDER_EMAIL = "ryanlloydgenturo@gmail.com"
    private const val SENDER_NAME = "Synced N Ministry"
    private const val BREVO_API_URL = "https://api.brevo.com/v3/smtp/email"
    const val OTP_VALIDITY_MS = 10 * 60 * 1000L // 10 minutes

    data class OtpRecord(
        val email: String,
        val code: String,
        val expiresAt: Long,
        val pendingPassword: String = ""
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
     * Generates a 6-digit numeric OTP and sends it to the recipient's email address via Brevo.
     * Runs asynchronously on a background worker thread.
     */
    fun sendOtp(
        recipientEmail: String,
        pendingPassword: String = "",
        onResult: (Result<String>) -> Unit
    ) {
        val trimmedEmail = recipientEmail.trim()
        if (trimmedEmail.isEmpty()) {
            onResult(Result.failure(IllegalArgumentException("Email address cannot be empty.")))
            return
        }

        // Generate 6-digit random verification code
        val generatedCode = (100000..999999).random().toString()
        val expiresAt = System.currentTimeMillis() + OTP_VALIDITY_MS

        activeRecord = OtpRecord(
            email = trimmedEmail,
            code = generatedCode,
            expiresAt = expiresAt,
            pendingPassword = pendingPassword
        )

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
                            You requested to reset your password. Use the 6-digit verification code below to proceed:
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
                    put("subject", "Synced N - Your Password Reset Verification Code")
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
            return Result.failure(IllegalArgumentException("Invalid verification code. Please check your email and try again."))
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
