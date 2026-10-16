package com.example.syncedn.data.model

/**
 * Represents a member's unavailable date range and reason.
 */
data class UnavailableRange(
    val id: String = "",
    val startDate: String = "", // Format: YYYY-MM-DD
    val endDate: String = "",   // Format: YYYY-MM-DD
    val reason: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "startDate" to startDate,
        "endDate" to endDate,
        "reason" to reason,
        "createdAt" to createdAt
    )

    fun includes(date: String): Boolean {
        if (startDate.isBlank() || endDate.isBlank() || date.isBlank()) return false
        val cleanDate = date.trim()
        val cleanStart = startDate.trim()
        val cleanEnd = endDate.trim()
        return cleanDate >= cleanStart && cleanDate <= cleanEnd
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): UnavailableRange {
            return UnavailableRange(
                id = map["id"] as? String ?: "",
                startDate = map["startDate"] as? String ?: "",
                endDate = map["endDate"] as? String ?: "",
                reason = map["reason"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
