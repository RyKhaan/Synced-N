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

        fun fromJsonObject(obj: org.json.JSONObject): UnavailableRange {
            val map = mutableMapOf<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.opt(k)
            }
            return fromMap(map)
        }

        fun parseList(raw: Any?): List<UnavailableRange> {
            val result = mutableListOf<UnavailableRange>()
            when (raw) {
                is List<*> -> {
                    for (item in raw) {
                        when (item) {
                            is UnavailableRange -> result.add(item)
                            is Map<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                result.add(fromMap(item as Map<String, Any?>))
                            }
                            is org.json.JSONObject -> result.add(fromJsonObject(item))
                        }
                    }
                }
                is org.json.JSONArray -> {
                    for (i in 0 until raw.length()) {
                        val obj = raw.optJSONObject(i)
                        if (obj != null) {
                            result.add(fromJsonObject(obj))
                        }
                    }
                }
                is String -> {
                    if (raw.isNotBlank()) {
                        try {
                            val arr = org.json.JSONArray(raw)
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i)
                                if (obj != null) result.add(fromJsonObject(obj))
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
            return result
        }
    }
}
