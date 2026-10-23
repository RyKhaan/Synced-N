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
    val photoUri: String = "",
    val unavailableRanges: List<UnavailableRange> = emptyList(),
    val joinedAt: Long = System.currentTimeMillis(),
    val removedByLeader: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "fullName" to fullName,
        "username" to username,
        "email" to email,
        "role" to role,
        "ministryRole" to ministryRole,
        "teamCode" to teamCode,
        "photoUri" to photoUri,
        "unavailableRanges" to unavailableRanges.map { it.toMap() },
        "joinedAt" to joinedAt,
        "removedByLeader" to removedByLeader
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): TeamMember {
            val ranges = UnavailableRange.parseList(map["unavailableRanges"])

            return TeamMember(
                uid = map["uid"] as? String ?: "",
                fullName = map["fullName"] as? String ?: "",
                username = map["username"] as? String ?: "",
                email = map["email"] as? String ?: "",
                role = map["role"] as? String ?: "Member",
                ministryRole = map["ministryRole"] as? String ?: "",
                teamCode = map["teamCode"] as? String ?: "",
                photoUri = map["photoUri"] as? String ?: "",
                unavailableRanges = ranges,
                joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                removedByLeader = map["removedByLeader"] as? Boolean ?: false
            )
        }
    }
}
