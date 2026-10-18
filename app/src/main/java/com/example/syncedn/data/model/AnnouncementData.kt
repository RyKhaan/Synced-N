package com.example.syncedn.data.model

import com.google.firebase.Timestamp

data class AnnouncementData(
    val id: String = "",
    val teamCode: String = "",
    val title: String = "",
    val message: String = "",
    val urgent: Boolean = false,
    val author: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to id,
            "teamCode" to teamCode,
            "title" to title,
            "message" to message,
            "urgent" to urgent,
            "author" to author,
            "createdBy" to createdBy,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
        return map
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): AnnouncementData {
            fun parseTime(value: Any?): Long {
                return when (value) {
                    is Number -> value.toLong()
                    is Timestamp -> value.toDate().time
                    is String -> value.toLongOrNull() ?: System.currentTimeMillis()
                    else -> System.currentTimeMillis()
                }
            }

            return AnnouncementData(
                id = (map["id"] as? String).orEmpty(),
                teamCode = (map["teamCode"] as? String).orEmpty(),
                title = (map["title"] as? String).orEmpty(),
                message = (map["message"] as? String).orEmpty(),
                urgent = (map["urgent"] as? Boolean) ?: false,
                author = (map["author"] as? String).orEmpty(),
                createdBy = (map["createdBy"] as? String).orEmpty(),
                createdAt = parseTime(map["createdAt"]),
                updatedAt = parseTime(map["updatedAt"])
            )
        }
    }
}
