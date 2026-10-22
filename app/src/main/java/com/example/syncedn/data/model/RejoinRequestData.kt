package com.example.syncedn.data.model

/**
 * Data model for member re-join requests sent to the Team Leader after removal.
 * Stored in /teams/{teamCode}/rejoin_requests/{requestId}.
 */
data class RejoinRequestData(
    val id: String = "",
    val teamCode: String = "",
    val userUid: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val reason: String = "",
    val status: String = "Pending", // 'Pending', 'Approved', 'Declined', 'Cancelled'
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to id,
            "teamCode" to teamCode,
            "userUid" to userUid,
            "userName" to userName,
            "status" to status,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
        if (userEmail.isNotBlank()) map["userEmail"] = userEmail
        if (reason.isNotBlank()) map["reason"] = reason
        return map
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): RejoinRequestData {
            val id = (map["id"] as? String).orEmpty()
            val teamCode = (map["teamCode"] as? String).orEmpty()
            val userUid = (map["userUid"] as? String).orEmpty()
            val userName = (map["userName"] as? String).orEmpty()
            val userEmail = (map["userEmail"] as? String).orEmpty()
            val reason = (map["reason"] as? String).orEmpty()
            val status = (map["status"] as? String) ?: "Pending"
            val createdAt = when (val c = map["createdAt"]) {
                is Number -> c.toLong()
                is com.google.firebase.Timestamp -> c.toDate().time
                else -> System.currentTimeMillis()
            }
            val updatedAt = when (val u = map["updatedAt"]) {
                is Number -> u.toLong()
                is com.google.firebase.Timestamp -> u.toDate().time
                else -> createdAt
            }
            return RejoinRequestData(
                id = id,
                teamCode = teamCode,
                userUid = userUid,
                userName = userName,
                userEmail = userEmail,
                reason = reason,
                status = status,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}
