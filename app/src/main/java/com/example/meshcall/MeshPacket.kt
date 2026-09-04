package com.example.meshcall

import android.util.Base64
import org.json.JSONObject
import java.util.UUID

/**
 * MESH NETWORK — PHASE M2-B: MESHPACKET MODEL
 * 
 * Represents a mesh-routable packet used to encapsulate messages 
 * for forwarding and multi-hop delivery.
 * 
 * NOTE: This is an isolated model and is NOT yet connected to SocketManager.
 */
data class MeshPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val messageId: String,
    val sourceId: String,
    val destinationId: String,
    val packetType: Int,
    val ttl: Int = 10,
    val hopCount: Int = 0,
    val payload: ByteArray
) {
    /**
     * Serializes this MeshPacket to a JSON string.
     * Uses Base64 to safely encode the arbitrary binary payload.
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put("packetId", packetId)
        json.put("messageId", messageId)
        json.put("sourceId", sourceId)
        json.put("destinationId", destinationId)
        json.put("packetType", packetType)
        json.put("ttl", ttl)
        json.put("hopCount", hopCount)
        json.put("payload", Base64.encodeToString(payload, Base64.NO_WRAP))
        return json.toString()
    }
    
    companion object {
        /**
         * Deserializes a MeshPacket from a JSON string.
         * Returns null if parsing fails.
         */
        fun fromJson(jsonStr: String): MeshPacket? {
            return try {
                val json = JSONObject(jsonStr)
                MeshPacket(
                    packetId = json.optString("packetId", UUID.randomUUID().toString()),
                    messageId = json.optString("messageId", ""),
                    sourceId = json.optString("sourceId", ""),
                    destinationId = json.optString("destinationId", ""),
                    packetType = json.optInt("packetType", 0),
                    ttl = json.optInt("ttl", 10),
                    hopCount = json.optInt("hopCount", 0),
                    payload = Base64.decode(json.optString("payload", ""), Base64.NO_WRAP)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // Auto-generated equals and hashCode for safe structural comparisons, 
    // explicitly handling the ByteArray payload content equality.
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MeshPacket

        if (packetId != other.packetId) return false
        if (messageId != other.messageId) return false
        if (sourceId != other.sourceId) return false
        if (destinationId != other.destinationId) return false
        if (packetType != other.packetType) return false
        if (ttl != other.ttl) return false
        if (hopCount != other.hopCount) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = packetId.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + sourceId.hashCode()
        result = 31 * result + destinationId.hashCode()
        result = 31 * result + packetType
        result = 31 * result + ttl
        result = 31 * result + hopCount
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
