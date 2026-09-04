package com.example.meshcall

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class PeerManager {
    private val TAG = "PeerManager"
    
    // Map of known peerId -> PeerConnection
    private val identifiedConnections = ConcurrentHashMap<String, PeerConnection>()
    
    // Map of tempId -> PeerConnection for sockets that haven't completed identity exchange
    private val unidentifiedConnections = ConcurrentHashMap<String, PeerConnection>()

    fun addConnection(connection: PeerConnection) {
        if (connection.peerId != null) {
            identifiedConnections[connection.peerId!!] = connection
            Log.i(TAG, "Added identified connection: ${connection.peerId}")
        } else {
            unidentifiedConnections[connection.tempId] = connection
            Log.i(TAG, "Added unidentified connection: ${connection.tempId}")
        }
    }

    fun bindPeerId(tempId: String, peerId: String): Boolean {
        val connection = unidentifiedConnections.remove(tempId)
        if (connection != null) {
            connection.setPeerId(peerId)
            identifiedConnections[peerId] = connection
            Log.i(TAG, "M2D7_PEER_BOUND: Bound tempId $tempId to peerId $peerId")
            return true
        }
        Log.w(TAG, "Failed to bind: tempId $tempId not found in unidentified connections")
        return false
    }

    fun removeConnection(connection: PeerConnection) {
        connection.peerId?.let { 
            identifiedConnections.remove(it) 
            Log.i(TAG, "Removed identified connection: $it")
        }
        unidentifiedConnections.remove(connection.tempId)
        Log.i(TAG, "Removed unidentified connection: ${connection.tempId}")
    }

    fun removeConnectionByPeerId(peerId: String) {
        val conn = identifiedConnections.remove(peerId)
        if (conn != null) {
            Log.i(TAG, "Removed identified connection by peerId: $peerId")
        } else {
            Log.w(TAG, "Attempted to remove unknown connection: $peerId")
        }
    }

    fun getConnection(peerIdOrTempId: String): PeerConnection? {
        return identifiedConnections[peerIdOrTempId] 
            ?: unidentifiedConnections[peerIdOrTempId]
            ?: identifiedConnections.values.find { it.tempId == peerIdOrTempId }
    }

    fun getConnectionByUserId(userId: String): PeerConnection? {
        return getAllConnections().firstOrNull {
            it.profile?.userId == userId
        }
    }

    fun getAllConnections(): List<PeerConnection> {
        val all = mutableListOf<PeerConnection>()
        all.addAll(identifiedConnections.values)
        all.addAll(unidentifiedConnections.values)
        return all
    }

    fun closeAll() {
        Log.i(TAG, "Closing all connections")
        val all = getAllConnections()
        for (conn in all) {
            conn.safeClose()
        }
        identifiedConnections.clear()
        unidentifiedConnections.clear()
    }
}
