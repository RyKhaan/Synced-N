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
}
