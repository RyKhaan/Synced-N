package com.example.syncedn.data.model

data class SubstitutionData(
    val id: String = "",                     // Unique substitution ID (matches document ID)
    val teamCode: String = "",               // 6-character team invite code
    val serviceId: String = "",              // Linked ServiceData ID (if scheduled service)
    val serviceTitle: String = "",           // Name of the service (e.g. "Sunday Worship Service")
    val serviceDate: String = "",            // Date of the service ("YYYY-MM-DD")
    val requesterUid: String = "",           // UID of the requesting member
    val requesterName: String = "",         // Full name of the requester
    val role: String = "",                   // Ministry role needing substitution (e.g. "Keyboardist")
    val substituteUid: String = "",          // UID of the chosen substitute teammate
    val substituteName: String = "",         // Name of the chosen substitute teammate
    val reason: String = "",                 // Member's reason for substitution (1-500 chars)
    val status: String = "Pending",          // "Pending", "Accepted", "Declined", "Cancelled"
    val createdBy: String = "",              // UID of creator (matches requesterUid)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "teamCode" to teamCode,
        "serviceId" to serviceId,
        "serviceTitle" to serviceTitle,
        "serviceDate" to serviceDate,
        "requesterUid" to requesterUid,
        "requesterName" to requesterName,
        "role" to role,
        "substituteUid" to substituteUid,
        "substituteName" to substituteName,
        "reason" to reason,
        "status" to status,
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): SubstitutionData {
            return SubstitutionData(
                id = map["id"] as? String ?: "",
                teamCode = map["teamCode"] as? String ?: "",
                serviceId = map["serviceId"] as? String ?: "",
                serviceTitle = map["serviceTitle"] as? String ?: "",
                serviceDate = map["serviceDate"] as? String ?: "",
                requesterUid = map["requesterUid"] as? String ?: "",
                requesterName = map["requesterName"] as? String ?: "",
                role = map["role"] as? String ?: "",
                substituteUid = map["substituteUid"] as? String ?: "",
                substituteName = map["substituteName"] as? String ?: "",
                reason = map["reason"] as? String ?: "",
                status = map["status"] as? String ?: "Pending",
                createdBy = map["createdBy"] as? String ?: "",
                createdAt = parseTimestamp(map["createdAt"]),
                updatedAt = parseTimestamp(map["updatedAt"])
            )
        }

        private fun parseTimestamp(raw: Any?): Long {
            return when (raw) {
                is Number -> raw.toLong()
                is java.util.Date -> raw.time
                else -> try {
                    val method = raw?.javaClass?.getMethod("toDate")
                    (method?.invoke(raw) as? java.util.Date)?.time ?: System.currentTimeMillis()
                } catch (_: Throwable) {
                    System.currentTimeMillis()
                }
            }
        }
    }
}
