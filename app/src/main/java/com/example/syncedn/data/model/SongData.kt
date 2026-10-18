package com.example.syncedn.data.model

import java.util.Date

/**
 * Data model for a worship song in the team's song catalog (/teams/{teamCode}/songs/{songId}).
 */
data class SongData(
    val id: String = "",
    val teamCode: String = "",
    val title: String = "",
    val artist: String = "",
    val key: String = "C",
    val bpm: Int = 74,
    val sheet: String = "",
    val lyrics: String = "",
    val referenceUrl: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "teamCode" to teamCode,
        "title" to title,
        "artist" to artist,
        "key" to key,
        "bpm" to bpm,
        "sheet" to sheet,
        "lyrics" to lyrics,
        "referenceUrl" to referenceUrl,
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): SongData {
            fun parseTimestamp(value: Any?): Long = when (value) {
                is Number -> value.toLong()
                is Date -> value.time
                is String -> value.toLongOrNull() ?: System.currentTimeMillis()
                else -> {
                    // Handle Firestore Timestamp through reflection to keep unit tests mock-free
                    if (value != null && value.javaClass.name.contains("Timestamp")) {
                        try {
                            val toDateMethod = value.javaClass.getMethod("toDate")
                            (toDateMethod.invoke(value) as? Date)?.time ?: System.currentTimeMillis()
                        } catch (_: Exception) {
                            System.currentTimeMillis()
                        }
                    } else {
                        System.currentTimeMillis()
                    }
                }
            }

            return SongData(
                id = map["id"] as? String ?: "",
                teamCode = map["teamCode"] as? String ?: "",
                title = map["title"] as? String ?: "",
                artist = map["artist"] as? String ?: "",
                key = (map["key"] as? String)?.ifBlank { "C" } ?: "C",
                bpm = when (val b = map["bpm"]) {
                    is Number -> b.toInt()
                    is String -> b.toIntOrNull() ?: 74
                    else -> 74
                },
                sheet = map["sheet"] as? String ?: "",
                lyrics = map["lyrics"] as? String ?: "",
                referenceUrl = map["referenceUrl"] as? String ?: "",
                createdBy = map["createdBy"] as? String ?: "",
                createdAt = parseTimestamp(map["createdAt"]),
                updatedAt = parseTimestamp(map["updatedAt"])
            )
        }

        fun normalizeReferenceUrl(url: String): String {
            val trimmed = url.trim()
            return when {
                trimmed.isBlank() -> ""
                trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> "https://$trimmed"
            }
        }

        fun resolveCreatorUid(existingCreator: String?, fallbackUid: String): String {
            return existingCreator?.takeIf { it.isNotBlank() } ?: fallbackUid
        }
    }
}
