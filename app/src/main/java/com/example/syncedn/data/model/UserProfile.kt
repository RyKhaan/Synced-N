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
    val teamName: String = "",
    val teamCode: String = "",
    val resetPassword: String = "",
    val fullName: String = "",
    val phone: String = "",
    val country: String = "Philippines",
    val language: String = "English",
    val ministryRole: String = "",
    val photoUri: String = "",
    val unavailableRanges: List<UnavailableRange> = emptyList(),
    val fcmToken: String = "",
    val removedByLeader: Boolean = false,
    val removedTeamCode: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "username" to username,
        "email" to email,
        "role" to role,
        "teamName" to teamName,
        "teamCode" to teamCode,
        "resetPassword" to resetPassword,
        "fullName" to fullName,
        "phone" to phone,
        "country" to country,
        "language" to language,
        "ministryRole" to ministryRole,
        "photoUri" to photoUri,
        "unavailableRanges" to unavailableRanges.map { it.toMap() },
        "fcmToken" to fcmToken,
        "removedByLeader" to removedByLeader,
        "removedTeamCode" to removedTeamCode,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): UserProfile {
            val ranges = UnavailableRange.parseList(map["unavailableRanges"])

            return UserProfile(
                uid = map["uid"] as? String ?: "",
                username = map["username"] as? String ?: "",
                email = map["email"] as? String ?: "",
                role = map["role"] as? String ?: "Member",
                teamName = map["teamName"] as? String ?: "",
                teamCode = map["teamCode"] as? String ?: "",
                resetPassword = map["resetPassword"] as? String ?: "",
                fullName = map["fullName"] as? String ?: "",
                phone = map["phone"] as? String ?: "",
                country = map["country"] as? String ?: "Philippines",
                language = map["language"] as? String ?: "English",
                ministryRole = map["ministryRole"] as? String ?: "",
                photoUri = map["photoUri"] as? String ?: "",
                unavailableRanges = ranges,
                fcmToken = map["fcmToken"] as? String ?: "",
                removedByLeader = map["removedByLeader"] as? Boolean ?: false,
                removedTeamCode = map["removedTeamCode"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
