package com.example.syncedn.data.model

/**
 * Represents a member of a team in Cloud Firestore.
 * Used for team roster display, ministry role assignment, and scheduling.
 */
data class TeamMember(
    val uid: String = "",
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val role: String = "Member",
    val ministryRole: String = "",
    val teamCode: String = "",
    val joinedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "fullName" to fullName,
        "username" to username,
        "email" to email,
        "role" to role,
        "ministryRole" to ministryRole,
        "teamCode" to teamCode,
        "joinedAt" to joinedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): TeamMember {
            return TeamMember(
                uid = map["uid"] as? String ?: "",
                fullName = map["fullName"] as? String ?: "",
                username = map["username"] as? String ?: "",
                email = map["email"] as? String ?: "",
                role = map["role"] as? String ?: "Member",
                ministryRole = map["ministryRole"] as? String ?: "",
                teamCode = map["teamCode"] as? String ?: "",
                joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
