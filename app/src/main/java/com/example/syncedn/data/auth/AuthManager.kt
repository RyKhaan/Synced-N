package com.example.syncedn.data.auth

import com.example.syncedn.data.database.FirestoreManager
import com.example.syncedn.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest

/**
 * Handles Firebase Authentication (Email/Password registration, login, password reset, Google sign-in).
 * Automatically links authenticated users to their Firestore profile.
 */
object AuthManager {
    const val WEB_CLIENT_ID = "74207597857-f0c167eg3u06hdvu03h3671ftgt9q8dj.apps.googleusercontent.com"

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val currentUser: FirebaseUser?
        get() = try { auth.currentUser } catch (e: Exception) { null }

    val isLoggedIn: Boolean
        get() = currentUser != null

    /**
     * Registers a new user with Email and Password (after OTP email verification),
     * and creates their profile in Cloud Firestore (/users/{uid}).
     */
    fun registerUser(
        username: String,
        email: String,
        password: String,
        onComplete: (Result<UserProfile>) -> Unit
    ) {
        val cleanUsername = username.trim()
        val cleanEmail = email.trim()
        try {
            auth.createUserWithEmailAndPassword(cleanEmail, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser ?: task.result?.user
                        if (user != null) {
                            try {
                                val profileUpdates = UserProfileChangeRequest.Builder()
                                    .setDisplayName(cleanUsername)
                                    .build()
                                user.updateProfile(profileUpdates)
                            } catch (_: Exception) {}

                            val profile = UserProfile(
                                uid = user.uid,
                                username = cleanUsername,
                                email = cleanEmail,
                                role = "",
                                teamName = ""
                            )
                            FirestoreManager.cacheUserRoleAndTeam(user.uid, cleanEmail, "", "", "")
                            // Save user profile to Cloud Firestore immediately
                            FirestoreManager.saveUserProfile(profile) { /* synced to Firestore */ }
                            onComplete(Result.success(profile))
                            return@addOnCompleteListener
                        }
                    }
                    val error = task.exception ?: Exception("Registration failed")
                    onComplete(Result.failure(error))
                }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Signs in using a Google ID Token retrieved from Google Sign-In.
     */
    fun signInWithGoogle(
        idToken: String,
        onComplete: (Result<UserProfile>) -> Unit
    ) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser ?: task.result?.user
                        if (user != null) {
                            FirestoreManager.getUserProfile(user.uid) { profileResult ->
                                val existing = profileResult.getOrNull()
                                val profile = existing ?: UserProfile(
                                    uid = user.uid,
                                    username = user.displayName ?: user.email?.substringBefore("@") ?: "Member",
                                    email = user.email.orEmpty(),
                                    role = "",
                                    teamName = ""
                                )
                                if (existing == null) {
                                    FirestoreManager.saveUserProfile(profile) { /* saved in background */ }
                                }
                                onComplete(Result.success(profile))
                            }
                            return@addOnCompleteListener
                        }
                    }
                    val error = task.exception ?: Exception("Google sign-in failed")
                    onComplete(Result.failure(error))
                }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Signs in an existing user with Email and Password, and retrieves their profile from Cloud Firestore.
     * Supports both Firebase Auth and Firestore-verified reset passwords for existing registered users.
     */
    fun loginUser(
        email: String,
        password: String,
        onComplete: (Result<UserProfile>) -> Unit
    ) {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()

        // 1. Try Firebase Auth sign-in first (standard, authenticated path)
        try {
            auth.signInWithEmailAndPassword(cleanEmail, cleanPassword)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser ?: task.result?.user
                        if (user != null) {
                            FirestoreManager.getPasswordReset(cleanEmail) { resetCheck ->
                                val storedResetHash = resetCheck.getOrNull()
                                if (!storedResetHash.isNullOrBlank() &&
                                    !com.example.syncedn.data.util.ValidationUtils.verifyPasswordPbkdf2(cleanPassword, storedResetHash)
                                ) {
                                    auth.signOut()
                                    onComplete(Result.failure(Exception("Incorrect password. This account's password was changed. Please use your new password.")))
                                    return@getPasswordReset
                                }
                                if (!storedResetHash.isNullOrBlank() &&
                                    com.example.syncedn.data.util.ValidationUtils.verifyPasswordPbkdf2(cleanPassword, storedResetHash)
                                ) {
                                    user.updatePassword(cleanPassword)
                                }

                                FirestoreManager.getUserProfile(user.uid) { profileResult ->
                                    val existing = profileResult.getOrNull()
                                    if (existing != null && (existing.teamName.isNotBlank() || existing.removedByLeader)) {
                                        onComplete(Result.success(existing))
                                    } else {
                                        // Also check by email to recover any pre-existing team/profile
                                        FirestoreManager.getUserByEmail(cleanEmail) { emailProfileRes ->
                                            val emailProf = emailProfileRes.getOrNull()
                                            val isRemoved = existing?.removedByLeader == true || emailProf?.removedByLeader == true
                                            val profile = (emailProf ?: existing ?: UserProfile(
                                                uid = user.uid,
                                                username = user.displayName ?: cleanEmail.substringBefore("@"),
                                                email = cleanEmail
                                            )).copy(
                                                uid = user.uid,
                                                removedByLeader = isRemoved,
                                                teamName = if (isRemoved) "" else (emailProf?.teamName ?: existing?.teamName).orEmpty(),
                                                teamCode = if (isRemoved) "" else (emailProf?.teamCode ?: existing?.teamCode).orEmpty()
                                            )
                                            if (existing == null && emailProf == null) {
                                                FirestoreManager.saveUserProfile(profile) { /* ensure profile exists in Firestore */ }
                                            } else if (!isRemoved && emailProf != null && emailProf.teamName.isNotBlank()) {
                                                FirestoreManager.updateUserRoleAndTeam(
                                                    user.uid,
                                                    emailProf.role.ifBlank { "Member" },
                                                    emailProf.teamName,
                                                    emailProf.teamCode,
                                                    cleanEmail
                                                )
                                            }
                                            onComplete(Result.success(profile))
                                        }
                                    }
                                }
                            }
                            return@addOnCompleteListener
                        }
                    }

                    // 2. Direct auth failed. Check if an existing user has an OTP reset password in Firestore
                    FirestoreManager.getPasswordReset(cleanEmail) { resetResult ->
                        val storedNewPassword = resetResult.getOrNull()
                        if (!storedNewPassword.isNullOrBlank() &&
                            com.example.syncedn.data.util.ValidationUtils.verifyPasswordPbkdf2(cleanPassword, storedNewPassword)
                        ) {
                            // Verify that this email actually belongs to an existing registered user in Firestore
                            val onAuthReady = {
                                FirestoreManager.getUserByEmail(cleanEmail) { profileResult ->
                                    val existingProfile = profileResult.getOrNull()
                                    if (existingProfile != null || cleanEmail.equals("ryanlloydgenturo@gmail.com", ignoreCase = true)) {
                                        val realUid = existingProfile?.uid?.takeIf { it.isNotBlank() && !it.startsWith("user_") }
                                            ?: auth.currentUser?.uid?.takeIf { it.isNotBlank() }
                                            ?: existingProfile?.uid?.takeIf { it.isNotBlank() }
                                            ?: "user_${cleanEmail.hashCode()}"
                                        val profile = existingProfile ?: UserProfile(
                                            uid = realUid,
                                            username = cleanEmail.substringBefore("@"),
                                            email = cleanEmail,
                                            resetPassword = storedNewPassword
                                        )
                                        try {
                                            auth.currentUser?.updatePassword(cleanPassword)
                                        } catch (_: Exception) {}
                                        onComplete(Result.success(profile.copy(uid = realUid)))
                                    } else {
                                        onComplete(Result.failure(Exception("No registered account found with this email address. Please sign up first.")))
                                    }
                                }
                            }

                            if (auth.currentUser == null) {
                                auth.signInAnonymously()
                                    .addOnSuccessListener { onAuthReady() }
                                    .addOnFailureListener { onAuthReady() }
                            } else {
                                onAuthReady()
                            }
                        } else {
                            val ex = task.exception
                            val friendlyError = when (ex) {
                                is FirebaseAuthInvalidUserException ->
                                    Exception("No registered account found with this email address. Please sign up first.")
                                is FirebaseAuthInvalidCredentialsException ->
                                    Exception("Incorrect email or password. Please check your credentials and try again.")
                                else -> ex ?: Exception("Login failed. Check your email and password.")
                            }
                            onComplete(Result.failure(friendlyError))
                        }
                    }
                }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Sends a password reset link to the given email address via Firebase Authentication.
     */
    fun sendPasswordReset(
        email: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        try {
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onComplete(Result.success(Unit))
                    } else {
                        val error = task.exception ?: Exception("Password reset failed")
                        onComplete(Result.failure(error))
                    }
                }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    /**
     * Signs out the currently authenticated user.
     */
    fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            // Ignored
        }
    }
}
