package com.example.syncedn.data.model

/**
 * Data model for Team Chat messages exchanged in Synced N.
 * Supports text, Cloudinary photo attachments, sender metadata, timestamps, and edit tracking.
 */
data class ChatMessage(
    val id: String = "",
    val teamCode: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val senderPhotoUri: String = "",
    val text: String = "",
    val mediaUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isEdited: Boolean = false,
    val editedAt: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "teamCode" to teamCode,
        "senderUid" to senderUid,
        "senderName" to senderName,
        "senderRole" to senderRole,
        "senderPhotoUri" to senderPhotoUri,
        "text" to text,
        "mediaUrl" to mediaUrl,
        "timestamp" to timestamp,
        "isEdited" to isEdited,
        "editedAt" to editedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ChatMessage {
            val rawTs = map["timestamp"]
            val parsedTs = when (rawTs) {
                is Number -> rawTs.toLong()
                is com.google.firebase.Timestamp -> rawTs.toDate().time
                is String -> rawTs.toLongOrNull() ?: System.currentTimeMillis()
                else -> System.currentTimeMillis()
            }
            val rawEditedAt = map["editedAt"]
            val parsedEditedAt = when (rawEditedAt) {
                is Number -> rawEditedAt.toLong()
                is com.google.firebase.Timestamp -> rawEditedAt.toDate().time
                is String -> rawEditedAt.toLongOrNull() ?: 0L
                else -> 0L
            }
            return ChatMessage(
                id = map["id"] as? String ?: "",
                teamCode = map["teamCode"] as? String ?: "",
                senderUid = map["senderUid"] as? String ?: "",
                senderName = map["senderName"] as? String ?: "",
                senderRole = map["senderRole"] as? String ?: "",
                senderPhotoUri = map["senderPhotoUri"] as? String ?: "",
                text = map["text"] as? String ?: "",
                mediaUrl = map["mediaUrl"] as? String ?: "",
                timestamp = parsedTs,
                isEdited = (map["isEdited"] as? Boolean) ?: false,
                editedAt = parsedEditedAt
            )
        }
    }
}
