package com.example.syncedn.data.model

/**
 * Represents a user profile stored in Cloud Firestore under the "users" collection.
 * Designed for backend/database integration.
 */
data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val role: String = "Member",
    val teamName: String = "Worship Team",
    val resetPassword: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "username" to username,
        "email" to email,
        "role" to role,
        "teamName" to teamName,
        "resetPassword" to resetPassword,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): UserProfile {
            return UserProfile(
                uid = map["uid"] as? String ?: "",
                username = map["username"] as? String ?: "",
                email = map["email"] as? String ?: "",
                role = map["role"] as? String ?: "Member",
                teamName = map["teamName"] as? String ?: "Worship Team",
                resetPassword = map["resetPassword"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
