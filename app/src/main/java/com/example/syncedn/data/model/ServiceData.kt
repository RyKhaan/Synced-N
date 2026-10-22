package com.example.syncedn.data.model

data class ServiceData(
    val id: String = "",
    val teamCode: String = "",
    val title: String = "",
    val date: String = "",              // "YYYY-MM-DD"
    val start: String = "",             // "HH:mm"
    val end: String = "",               // "HH:mm"
    val venue: String = "",
    val status: String = "Draft",       // "Draft" or "Published"
    val coverImageUrl: String = "",
    val assignments: List<ServiceAssignment> = emptyList(),
    val rehearsal: RehearsalData = RehearsalData(),
    val songs: List<ServiceSong> = emptyList(),
    val createdBy: String = "",         // Leader UID
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "teamCode" to teamCode,
        "title" to title,
        "date" to date,
        "start" to start,
        "end" to end,
        "venue" to venue,
        "status" to status,
        "coverImageUrl" to coverImageUrl,
        "assignments" to assignments.map { it.toMap() },
        "rehearsal" to rehearsal.toMap(),
        "songs" to songs.map { it.toMap() },
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ServiceData {
            val rawAssignments = map["assignments"] as? List<*>
            val assignmentsList = rawAssignments?.mapNotNull { item ->
                @Suppress("UNCHECKED_CAST")
                (item as? Map<String, Any?>)?.let { ServiceAssignment.fromMap(it) }
            } ?: emptyList()

            val rawSongs = map["songs"] as? List<*>
            val songsList = rawSongs?.mapNotNull { item ->
                when (item) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        ServiceSong.fromMap(item as Map<String, Any?>)
                    }
                    else -> item?.let { ServiceSong.unpack(it) }
                }
            } ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val rehearsalMap = (map["rehearsal"] as? Map<String, Any?>) ?: emptyMap()

            return ServiceData(
                id = map["id"] as? String ?: "",
                teamCode = map["teamCode"] as? String ?: "",
                title = map["title"] as? String ?: "",
                date = map["date"] as? String ?: "",
                start = map["start"] as? String ?: "",
                end = map["end"] as? String ?: "",
                venue = map["venue"] as? String ?: "",
                status = map["status"] as? String ?: "Draft",
                coverImageUrl = map["coverImageUrl"] as? String ?: "",
                assignments = assignmentsList,
                rehearsal = RehearsalData.fromMap(rehearsalMap),
                songs = songsList,
                createdBy = map["createdBy"] as? String ?: "",
                createdAt = when (val c = map["createdAt"]) {
                    is Number -> c.toLong()
                    is java.util.Date -> c.time
                    else -> try {
                        val toDate = c?.javaClass?.getMethod("toDate")
                        (toDate?.invoke(c) as? java.util.Date)?.time ?: System.currentTimeMillis()
                    } catch (_: Throwable) { System.currentTimeMillis() }
                },
                updatedAt = when (val u = map["updatedAt"]) {
                    is Number -> u.toLong()
                    is java.util.Date -> u.time
                    else -> try {
                        val toDate = u?.javaClass?.getMethod("toDate")
                        (toDate?.invoke(u) as? java.util.Date)?.time ?: System.currentTimeMillis()
                    } catch (_: Throwable) { System.currentTimeMillis() }
                }
            )
        }
    }
}

data class ServiceAssignment(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "email" to email,
        "name" to name,
        "role" to role
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ServiceAssignment = ServiceAssignment(
            uid = map["uid"] as? String ?: "",
            email = map["email"] as? String ?: "",
            name = map["name"] as? String ?: "",
            role = map["role"] as? String ?: ""
        )
    }
}

data class RehearsalData(
    val date: String = "",
    val start: String = "",
    val end: String = "",
    val venue: String = "",
    val notes: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "date" to date,
        "start" to start,
        "end" to end,
        "venue" to venue,
        "notes" to notes
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): RehearsalData = RehearsalData(
            date = map["date"] as? String ?: "",
            start = map["start"] as? String ?: "",
            end = map["end"] as? String ?: "",
            venue = map["venue"] as? String ?: "",
            notes = map["notes"] as? String ?: ""
        )
    }
}

data class ServiceSong(
    val title: String = "",
    val artist: String = "",
    val key: String = "",
    val bpm: Int = 0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "title" to title,
        "artist" to artist,
        "key" to key,
        "bpm" to bpm
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ServiceSong = unpack(map)

        fun unpack(raw: Any?): ServiceSong {
            if (raw == null) return ServiceSong()
            var title = ""
            var artist = ""
            var key = "C"
            var bpm = 74

            when (raw) {
                is ServiceSong -> return raw
                is Map<*, *> -> {
                    title = raw["title"]?.toString().orEmpty()
                    artist = raw["artist"]?.toString().orEmpty()
                    key = raw["key"]?.toString().orEmpty().ifBlank { "C" }
                    bpm = (raw["bpm"] as? Number)?.toInt() ?: 74
                }
                is org.json.JSONObject -> {
                    title = raw.optString("title")
                    artist = raw.optString("artist")
                    key = raw.optString("key", "C").ifBlank { "C" }
                    bpm = raw.optInt("bpm", 74).takeIf { it > 0 } ?: 74
                }
                is String -> {
                    val trimmed = raw.trim()
                    if (trimmed.startsWith("{") && trimmed.contains("\"title\"")) {
                        try {
                            val obj = org.json.JSONObject(trimmed)
                            return unpack(obj)
                        } catch (_: Exception) {
                            title = trimmed
                        }
                    } else {
                        title = trimmed
                    }
                }
                else -> {
                    val str = raw.toString().trim()
                    if (str.startsWith("{") && str.contains("\"title\"")) {
                        try {
                            val obj = org.json.JSONObject(str)
                            return unpack(obj)
                        } catch (_: Exception) {
                            title = str
                        }
                    } else {
                        title = str
                    }
                }
            }

            var unwindLimit = 0
            while (unwindLimit < 5 && title.startsWith("{") && title.contains("\"title\"")) {
                try {
                    val nested = org.json.JSONObject(title)
                    title = nested.optString("title", title).trim()
                    if (artist.isBlank()) artist = nested.optString("artist", "")
                    if (key.isBlank() || key == "C") key = nested.optString("key", key).ifBlank { "C" }
                    if (bpm <= 0 || bpm == 74) bpm = nested.optInt("bpm", bpm).takeIf { it > 0 } ?: bpm
                    unwindLimit++
                } catch (_: Exception) {
                    break
                }
            }

            return ServiceSong(
                title = title.trim(),
                artist = artist.trim(),
                key = key.trim().ifBlank { "C" },
                bpm = if (bpm > 0) bpm else 74
            )
        }
    }
}
