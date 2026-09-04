package com.example.meshcall

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import kotlinx.coroutines.sync.Mutex

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

class PeerConnection(
    val socket: Socket,
    // Temporary ID for tracking before identity is established
    val tempId: String = java.util.UUID.randomUUID().toString()
) {
    private val TAG = "PeerConnection"
    
    var peerId: String? = null
        private set

    var profile: UserProfile? = null
        
    var state: ConnectionState = ConnectionState.CONNECTING
        private set

    var dataInputStream: DataInputStream? = null
        private set
        
    var dataOutputStream: DataOutputStream? = null
        private set
        
    val writeMutex = Mutex()

    init {
        try {
            dataInputStream = DataInputStream(socket.getInputStream())
            dataOutputStream = DataOutputStream(socket.getOutputStream())
            state = ConnectionState.CONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize streams for socket", e)
            state = ConnectionState.DISCONNECTED
            safeClose()
        }
    }
    
    fun setPeerId(id: String) {
        peerId = id
    }

    fun safeClose() {
        if (state == ConnectionState.DISCONNECTED) return
        
        state = ConnectionState.DISCONNECTED
        try {
            dataOutputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing output stream", e)
        }
        try {
            dataInputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing input stream", e)
        }
        try {
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket", e)
        }
        
        dataOutputStream = null
        dataInputStream = null
    }
}
