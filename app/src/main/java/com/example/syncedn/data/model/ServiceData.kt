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
                @Suppress("UNCHECKED_CAST")
                (item as? Map<String, Any?>)?.let { ServiceSong.fromMap(it) }
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
                assignments = assignmentsList,
                rehearsal = RehearsalData.fromMap(rehearsalMap),
                songs = songsList,
                createdBy = map["createdBy"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
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
        fun fromMap(map: Map<String, Any?>): ServiceSong = ServiceSong(
            title = map["title"] as? String ?: "",
            artist = map["artist"] as? String ?: "",
            key = map["key"] as? String ?: "",
            bpm = (map["bpm"] as? Number)?.toInt() ?: 0
        )
    }
}
