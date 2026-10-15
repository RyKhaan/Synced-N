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
     */
    fun getUserProfile(uid: String, onComplete: (Result<UserProfile?>) -> Unit) {
        if (uid.isBlank()) {
            onComplete(Result.success(null))
            return
        }

        try {
            db.collection(USERS_COLLECTION)
                .document(uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val data = snapshot.data
                        if (data != null) {
                            onComplete(Result.success(UserProfile.fromMap(data)))
                            return@addOnSuccessListener
                        }
                    }
                    onComplete(Result.success(null))
                }
                .addOnFailureListener { exception ->
                    onComplete(Result.failure(exception))
                }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
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
     * Updates the user's role and team in Firestore: /users/{uid}
     * Uses SetOptions.merge() so fields are merged safely without clobbering other data.
     */
    fun updateUserRoleAndTeam(uid: String, role: String, teamName: String, onComplete: (Result<Unit>) -> Unit = {}) {
        if (uid.isBlank()) {
            onComplete(Result.success(Unit))
            return
        }

        try {
            val updates = mapOf(
                "role" to role,
                "teamName" to teamName
            )
            db.collection(USERS_COLLECTION)
                .document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
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

        try {
            db.collection(PASSWORD_RESETS_COLLECTION)
                .document(cleanEmail)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val pass = snapshot.getString("password")
                        onComplete(Result.success(pass))
                    } else {
                        onComplete(Result.success(null))
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
     * Finds a user profile by email from the /users collection.
     */
    fun getUserByEmail(email: String, onComplete: (Result<UserProfile?>) -> Unit) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            onComplete(Result.success(null))
            return
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
                            onComplete(Result.success(UserProfile.fromMap(data)))
                            return@addOnSuccessListener
                        }
                    }
                    onComplete(Result.success(null))
                }
                .addOnFailureListener { exception ->
                    onComplete(Result.failure(exception))
                }
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }
}
