package com.example.syncedn.data.model

/**
 * Represents team metadata stored under the "teams" collection in Cloud Firestore.
 */
data class TeamData(
    val teamCode: String = "",
    val teamName: String = "",
    val leaderUid: String = "",
    val leaderName: String = "",
    val roles: List<String> = DEFAULT_ROLES,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "teamCode" to teamCode,
        "teamName" to teamName,
        "leaderUid" to leaderUid,
        "leaderName" to leaderName,
        "roles" to roles,
        "createdAt" to createdAt
    )

    companion object {
        val DEFAULT_ROLES = listOf(
            "Worship Leader",
            "Vocalist",
            "Guitarist",
            "Keyboardist",
            "Bassist",
            "Drummer",
            "Sound Operator"
        )

        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): TeamData {
            val rolesList = (map["roles"] as? List<*>)?.filterIsInstance<String>()
            return TeamData(
                teamCode = map["teamCode"] as? String ?: "",
                teamName = map["teamName"] as? String ?: "",
                leaderUid = map["leaderUid"] as? String ?: "",
                leaderName = map["leaderName"] as? String ?: "",
                roles = if (!rolesList.isNullOrEmpty()) rolesList else DEFAULT_ROLES,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
