package com.example.syncedn.data.auth

import com.example.syncedn.data.database.FirestoreManager
import com.example.syncedn.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

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
     * Registers a new user with Email and Password, and creates their profile in Cloud Firestore.
     */
    fun registerUser(
        username: String,
        email: String,
        password: String,
        onComplete: (Result<UserProfile>) -> Unit
    ) {
        try {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser ?: task.result?.user
                        if (user != null) {
                            val profile = UserProfile(
                                uid = user.uid,
                                username = username,
                                email = email,
                                role = "",
                                teamName = ""
                            )
                            // Save user profile to Cloud Firestore in background
                            FirestoreManager.saveUserProfile(profile) { /* saved in background */ }
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
     * Supports both Firebase Auth and Firestore-verified reset passwords.
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
                            FirestoreManager.getUserProfile(user.uid) { profileResult ->
                                val existing = profileResult.getOrNull()
                                if (existing != null && existing.teamName.isNotBlank()) {
                                    onComplete(Result.success(existing))
                                } else {
                                    // Also check by email to recover any pre-existing team/profile
                                    FirestoreManager.getUserByEmail(cleanEmail) { emailProfileRes ->
                                        val emailProf = emailProfileRes.getOrNull()
                                        val profile = (emailProf ?: existing ?: UserProfile(
                                            uid = user.uid,
                                            username = user.displayName ?: cleanEmail.substringBefore("@"),
                                            email = cleanEmail
                                        )).copy(uid = user.uid)
                                        if (emailProf != null && emailProf.teamName.isNotBlank()) {
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
                            return@addOnCompleteListener
                        }
                    }

                    // 2. Direct auth failed. Check if user has an OTP reset password in Firestore
                    FirestoreManager.getPasswordReset(cleanEmail) { resetResult ->
                        val storedNewPassword = resetResult.getOrNull()
                        if (!storedNewPassword.isNullOrBlank() && storedNewPassword == cleanPassword) {
                            // Password matches the newly reset password!
                            val onAuthReady = {
                                FirestoreManager.getUserByEmail(cleanEmail) { profileResult ->
                                    val realUid = auth.currentUser?.uid ?: "user_${cleanEmail.hashCode()}"
                                    val profile = profileResult.getOrNull() ?: UserProfile(
                                        uid = realUid,
                                        username = cleanEmail.substringBefore("@"),
                                        email = cleanEmail,
                                        resetPassword = cleanPassword
                                    )
                                    onComplete(Result.success(profile.copy(uid = realUid)))
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
                            val error = task.exception ?: Exception("Login failed. Check your email and password.")
                            onComplete(Result.failure(error))
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
