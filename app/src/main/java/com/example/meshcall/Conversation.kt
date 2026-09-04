package com.example.meshcall

data class Conversation(
    val peerUserId: String,
    val peerUsername: String,
    val peerDisplayName: String,
    val peerAvatarUri: String,
    val lastMessage: String,
    val lastMessageType: Int,
    val lastMessageTimestamp: Long,
    val unreadCount: Int,
    var isConnected: Boolean = false // Transient property
)
