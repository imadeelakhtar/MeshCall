package com.example.meshcall

enum class MessageType { TEXT, VOICE, IMAGE, FILE, CALL }
enum class MessageState { SENDING, SENT, DELIVERED, SEEN, FAILED }

data class ChatMessage(
    val id: String,
    val conversationId: String = "",
    val text: String = "",
    val isSent: Boolean,
    val type: MessageType = MessageType.TEXT,
    val audioPath: String? = null,
    var durationMs: Long = 0,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val localFilePath: String? = null,
    var state: MessageState = MessageState.SENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val reactions: MutableList<String> = mutableListOf(),
    var isPlaying: Boolean = false,
    var progress: Int = 0, // UI state for file transfer progress
    var deliveredAt: Long = 0,
    var seenAt: Long = 0
)
