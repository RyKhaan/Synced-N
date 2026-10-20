package com.example.syncedn.data.model

/**
 * Data model for Team Chat messages exchanged in Synced N.
 * Supports text, Cloudinary photo attachments, sender metadata, and timestamps.
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
    val timestamp: Long = System.currentTimeMillis()
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
        "timestamp" to timestamp
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ChatMessage {
            return ChatMessage(
                id = map["id"] as? String ?: "",
                teamCode = map["teamCode"] as? String ?: "",
                senderUid = map["senderUid"] as? String ?: "",
                senderName = map["senderName"] as? String ?: "",
                senderRole = map["senderRole"] as? String ?: "",
                senderPhotoUri = map["senderPhotoUri"] as? String ?: "",
                text = map["text"] as? String ?: "",
                mediaUrl = map["mediaUrl"] as? String ?: "",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
