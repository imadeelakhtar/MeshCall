package com.example.meshcall

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class SocketManager(
    var peerManager: PeerManager?,
    private val onDataReceived: (type: Int, messageId: String, payload: ByteArray, peerConn: PeerConnection?) -> Unit,
    private val onConnectionStatusChanged: (Boolean, PeerConnection?) -> Unit
) {
    companion object {
        const val TYPE_TEXT = 0x00
        const val TYPE_VOICE = 0x01
        const val TYPE_FILE = 0x02
        const val TYPE_DELIVERY_ACK = 0x03
        const val TYPE_SEEN_ACK = 0x04
        const val TYPE_REACTION_ADD = 0x05
        const val TYPE_REACTION_REMOVE = 0x06
        const val TYPE_MESSAGE_DELETE = 0x07
        const val TYPE_TEXT_V2 = 0x08
        const val TYPE_IDENTITY_EXCHANGE = 0x09
        
        const val TYPE_CONNECTION_REQUEST = 0x0A
        const val TYPE_CONNECTION_ACCEPT = 0x0B
        const val TYPE_CONNECTION_DECLINE = 0x0C
        const val TYPE_DISCONNECT = 0x0D
        
        // Phase 5 Call Signaling
        const val TYPE_CALL_REQUEST = 0x10
        const val TYPE_CALL_ACCEPT = 0x11
        const val TYPE_CALL_DECLINE = 0x12
        const val TYPE_CALL_END = 0x13
        const val TYPE_CALL_AUDIO = 0x14
        
        const val TYPE_FILE_METADATA = 0x20
        const val TYPE_FILE_CHUNK = 0x21

        const val TYPE_MESH_PACKET = 0x30
        const val TYPE_ROUTING_UPDATE = 0x31
    }

    private val TAG = "SocketManager"
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val dummyMutex = Mutex()

    private fun getStreamAndMutex(peerId: String?): Pair<DataOutputStream?, Mutex> {
        if (peerId != null) {
            val conn = peerManager?.getConnection(peerId)
            if (conn != null) {
                Log.i(TAG, "M2D9_CONNECTION_FOUND: Found connection for $peerId")
                return conn.dataOutputStream to conn.writeMutex
            }
            Log.w(TAG, "M2D9_CONNECTION_FAILED: No connection found for peerId/tempId: $peerId.")
        } else {
            Log.w(TAG, "Cannot send message: peerId is null.")
        }
        return null to dummyMutex
    }

    fun startServer(port: Int) {
        if (job?.isActive == true && serverSocket?.isClosed == false) {
            Log.d(TAG, "Server is already running on port $port")
            return
        }
        
        stop()
        job = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.i("MeshCall", "SOCKET_SERVER_START: port=$port")
                Log.d(TAG, "Server socket started on port $port, waiting for connection...")
                
                while (true) {
                    val socket = serverSocket!!.accept()
                    Log.i("MeshCall", "SOCKET_CONNECTED: role=SERVER, client=${socket.inetAddress.hostAddress}")
                    Log.d(TAG, "Client connected: ${socket.inetAddress.hostAddress}")
                    
                    scope.launch {
                        handleSocketConnection(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}", e)
                withContext(Dispatchers.Main) { onConnectionStatusChanged(false, null) }
            }
        }
    }

    fun startClient(hostAddress: String, port: Int) {
        // A client socket only needs to be launched once. But we don't have a reliable way to check if we're already connected to THIS specific host here. 
        // We'll trust MeshService to only call startClient once per connection.
        // However, we should still not call stop() unconditionally because it might kill existing server sockets if we transition (though unlikely in WifiDirect without group teardown).
        // For simplicity, we just launch the client coroutine without stopping the server, in case they ever coexist, or just let it run.
        job = scope.launch {
            try {
                Log.i("MeshCall", "SOCKET_CLIENT_CONNECT: host=$hostAddress, port=$port")
                Log.d(TAG, "Client connecting to $hostAddress:$port...")
                val socket = Socket()
                var connected = false
                for (i in 1..5) {
                    try {
                        socket.connect(InetSocketAddress(hostAddress, port), 5000)
                        connected = true
                        break
                    } catch (e: Exception) {
                        Log.d(TAG, "Connection attempt $i failed, retrying in 1s...")
                        kotlinx.coroutines.delay(1000)
                    }
                }
                
                if (connected) {
                    Log.i("MeshCall", "SOCKET_CONNECTED: role=CLIENT, server=$hostAddress")
                    Log.d(TAG, "Connected to server successfully.")
                    handleSocketConnection(socket)
                } else {
                    Log.e(TAG, "Failed to connect to server after multiple attempts.")
                    withContext(Dispatchers.Main) { onConnectionStatusChanged(false, null) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client error: ${e.message}", e)
                withContext(Dispatchers.Main) { onConnectionStatusChanged(false, null) }
            }
        }
    }

    private suspend fun handleSocketConnection(socket: Socket) {
        val peerConn = PeerConnection(socket)
        peerManager?.addConnection(peerConn)
        
        try {
            val dataInputStream = DataInputStream(socket.getInputStream())

            Log.i("MeshCall", "M2D7_SOCKET_CONNECTED: ${socket.inetAddress}")
            withContext(Dispatchers.Main) { onConnectionStatusChanged(true, peerConn) }

            while (true) {
                val type = dataInputStream.read()
                if (type == TYPE_IDENTITY_EXCHANGE) Log.i(TAG, "IDENTITY_FRAME_RECEIVE")
                if (type == -1) {
                    Log.d(TAG, "Socket closed by remote host.")
                    break
                }
                
                val length = dataInputStream.readInt()
                if (length > 10 * 1024 * 1024) { // 10MB sanity check
                    Log.e(TAG, "Message too large: $length bytes. Aborting connection to prevent OOM.")
                    break
                }
                if (length < 0) {
                    Log.e(TAG, "Negative payload length received.")
                    break
                }
                
                // For HELLO_MESH_CALL handshake (legacy text packet), it doesn't have the 4-byte ID length.
                // We need to handle this to not break the handshake.
                // However, wait, in sendMessage we added ID. Let's just create a separate handshake method
                // or handle the HELLO_MESH_CALL string specifically if length is exactly 15 bytes.
                
                // Actually, let's just make the handshake use the new sendMessage method with a fixed ID.
                // For now, let's read the first 4 bytes as idLength.
                
                // Actually, let's just make the handshake use the new sendMessage method with a fixed ID.
                // For now, let's read the first 4 bytes as idLength.
                
                if (type == TYPE_MESH_PACKET) {
                    Log.i(TAG, "MESH_PACKET_RECEIVE_START")
                    if (length > 0 && length < 5_000_000) {
                        val payload = ByteArray(length)
                        dataInputStream.readFully(payload)
                        try {
                            val jsonStr = String(payload, Charsets.UTF_8)
                            val packet = MeshPacket.fromJson(jsonStr)
                            if (packet != null) {
                                Log.i(TAG, "MESH_PACKET_RECEIVED")
                                Log.i(TAG, "MESH_PACKET_DESERIALIZED: packetId=${packet.packetId}, messageId=${packet.messageId}, sourceId=${packet.sourceId}, destinationId=${packet.destinationId}, packetType=${packet.packetType}, ttl=${packet.ttl}, hopCount=${packet.hopCount}")
                                onDataReceived(type, packet.packetId, payload, peerConn)
                            } else {
                                Log.e(TAG, "Malformed MeshPacket received (null)")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deserializing MeshPacket: ${e.message}", e)
                        }
                    } else {
                        Log.e(TAG, "Invalid MeshPacket length: $length")
                        if (length > 0) {
                            dataInputStream.skipBytes(length)
                        }
                    }
                    continue
                }

                if (type == TYPE_TEXT) {
                    val payload = ByteArray(length)
                    if (length > 0) {
                        dataInputStream.readFully(payload)
                    }
                    
                    // We must generate a messageId for backwards compatibility since old text format lacks it
                    val messageId = java.util.UUID.randomUUID().toString()
                    Log.i(TAG, "RECEIVE_FRAME: type=$type, id=$messageId, length=$length bytes")
                    onDataReceived(type, messageId, payload, peerConn)
                    continue
                }
                
                val idLength = dataInputStream.readInt()
                if (idLength < 0 || idLength > (length - 4)) {
                    Log.e("MeshCall_CRASH", "Invalid idLength: $idLength for payload length $length. Disconnecting to prevent crash.")
                    break
                }
                
                val idBytes = ByteArray(idLength)
                dataInputStream.readFully(idBytes)
                val messageId = String(idBytes, Charsets.UTF_8)
                
                val remainingLength = length - 4 - idLength
                if (remainingLength < 0) {
                    Log.e("MeshCall_CRASH", "Invalid remainingLength: $remainingLength. Disconnecting.")
                    break
                }
                
                val payload = ByteArray(remainingLength)
                if (remainingLength > 0) {
                    dataInputStream.readFully(payload)
                }
                
                if (type == TYPE_IDENTITY_EXCHANGE) Log.i(TAG, "IDENTITY_PAYLOAD_RECEIVED")
                Log.i(TAG, "RECEIVE_FRAME: type=$type, id=$messageId, length=$remainingLength bytes")
                onDataReceived(type, messageId, payload, peerConn)
            }
        } catch (e: Exception) {
            Log.e("MeshCall_CRASH", "Socket read/write error: ${e.message}\n${Log.getStackTraceString(e)}")
            Log.e(TAG, "SOCKET_ERROR: ${e.message}", e)
        } finally {
            Log.i(TAG, "SOCKET_CLOSE: Closing active socket")
            withContext(Dispatchers.Main) { onConnectionStatusChanged(false, peerConn) }
            peerManager?.removeConnection(peerConn)
            peerConn.safeClose()
        }
    }

    fun sendMessage(messageId: String, message: String, peerId: String? = null) {
        scope.launch {
            try {
                val textBytes = message.toByteArray(Charsets.UTF_8)
                val (outStream, mutex) = getStreamAndMutex(peerId)
                
                android.util.Log.i("MeshCall_CrashTrace", "TEXT_FRAME_WRITE_START")
                mutex.withLock {
                    outStream?.apply {
                        if (message == "HELLO_MESH_CALL") {
                            write(TYPE_TEXT)
                            writeInt(textBytes.size)
                            write(textBytes)
                        } else {
                            val idBytes = messageId.toByteArray(Charsets.UTF_8)
                            val payloadSize = 4 + idBytes.size + textBytes.size
                            write(TYPE_TEXT_V2)
                            writeInt(payloadSize)
                            writeInt(idBytes.size)
                            write(idBytes)
                            write(textBytes)
                        }
                        flush()
                    }
                }
                android.util.Log.i("MeshCall_CrashTrace", "TEXT_FRAME_WRITE_COMPLETE")
                Log.i(TAG, "Sent text message -> $message")
            } catch (e: Exception) {
                android.util.Log.e("MeshCall_CrashTrace", "FATAL EXCEPTION in SocketManager (Coroutine): ${e.message}\n${android.util.Log.getStackTraceString(e)}")
                Log.e(TAG, "Failed to send text message: ${e.message}", e)
            }
        }
    }

    fun sendVoiceMessage(messageId: String, audioBytes: ByteArray, peerId: String? = null) {
        scope.launch {
            try {
                val idBytes = messageId.toByteArray(Charsets.UTF_8)
                val payloadSize = 4 + idBytes.size + audioBytes.size
                val (outStream, mutex) = getStreamAndMutex(peerId)
                
                mutex.withLock {
                    outStream?.apply {
                        write(TYPE_VOICE)
                        writeInt(payloadSize)
                        writeInt(idBytes.size)
                        write(idBytes)
                        write(audioBytes)
                        flush()
                    }
                }
                Log.i(TAG, "Sent voice message -> ${audioBytes.size} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send voice message: ${e.message}", e)
            }
        }
    }

    fun sendAck(type: Int, messageId: String, peerId: String? = null) {
        scope.launch {
            try {
                Log.i(TAG, "SEND_ACK: type=$type for messageId=$messageId")
                val idBytes = messageId.toByteArray(Charsets.UTF_8)
                val payloadSize = 4 + idBytes.size
                val (outStream, mutex) = getStreamAndMutex(peerId)
                
                mutex.withLock {
                    outStream?.apply {
                        write(type)
                        writeInt(payloadSize)
                        writeInt(idBytes.size)
                        write(idBytes)
                        flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send ACK ($type): ${e.message}", e)
            }
        }
    }

    fun sendReaction(type: Int, messageId: String, reaction: String, peerId: String? = null) {
        scope.launch {
            try {
                val idBytes = messageId.toByteArray(Charsets.UTF_8)
                val reactionBytes = reaction.toByteArray(Charsets.UTF_8)
                val payloadSize = 4 + idBytes.size + reactionBytes.size
                val (outStream, mutex) = getStreamAndMutex(peerId)
                
                mutex.withLock {
                    outStream?.apply {
                        write(type)
                        writeInt(payloadSize)
                        writeInt(idBytes.size)
                        write(idBytes)
                        write(reactionBytes)
                        flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send reaction ($type): ${e.message}", e)
            }
        }
    }

    fun sendMeshPacket(packet: MeshPacket, peerId: String? = null) {
        scope.launch {
            try {
                Log.i(TAG, "MESH_PACKET_SEND_START")
                val jsonPayload = packet.toJson()
                val payloadBytes = jsonPayload.toByteArray(Charsets.UTF_8)
                val length = payloadBytes.size
                val (outStream, mutex) = getStreamAndMutex(peerId)
                
                mutex.withLock {
                    outStream?.apply {
                        write(TYPE_MESH_PACKET)
                        writeInt(length)
                        write(payloadBytes)
                        flush()
                    }
                }
                Log.i(TAG, "MESH_PACKET_SENT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send MeshPacket: ${e.message}", e)
            }
        }
    }

    fun sendControlMessage(type: Int, messageId: String, jsonPayload: String, peerId: String? = null) {
        scope.launch {
            try {
                if (type == TYPE_IDENTITY_EXCHANGE) Log.i(TAG, "IDENTITY_FRAME_WRITE_START")
                Log.i(TAG, "SEND_CONTROL: type=$type, payload=$jsonPayload")
                val idBytes = messageId.toByteArray(Charsets.UTF_8)
                val payloadBytes = jsonPayload.toByteArray(Charsets.UTF_8)
                val totalSize = 4 + idBytes.size + payloadBytes.size
                val (outStream, mutex) = getStreamAndMutex(peerId)
                
                mutex.withLock {
                    outStream?.apply {
                        write(type)
                        writeInt(totalSize)
                        writeInt(idBytes.size)
                        write(idBytes)
                        write(payloadBytes)
                        flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send control message ($type): ${e.message}", e)
            }
        }
    }

    suspend fun sendControlMessageSync(type: Int, messageId: String, jsonPayload: String, peerId: String? = null) {
        try {
            Log.i(TAG, "SEND_CONTROL_SYNC: type=$type, payload=$jsonPayload")
            val idBytes = messageId.toByteArray(Charsets.UTF_8)
            val payloadBytes = jsonPayload.toByteArray(Charsets.UTF_8)
            val totalSize = 4 + idBytes.size + payloadBytes.size
            val (outStream, mutex) = getStreamAndMutex(peerId)
            
            mutex.withLock {
                outStream?.apply {
                    write(type)
                    writeInt(totalSize)
                    writeInt(idBytes.size)
                    write(idBytes)
                    write(payloadBytes)
                    flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send control message sync ($type): ${e.message}", e)
        }
    }

    fun sendCallAudio(audioBytes: ByteArray, peerId: String? = null) {
        scope.launch {
            try {
                // payloadSize = idLength(4) + idBytes(0) + audioBytes
                val payloadSize = 4 + 0 + audioBytes.size
                val (outStream, mutex) = getStreamAndMutex(peerId)
                
                mutex.withLock {
                    outStream?.apply {
                        write(TYPE_CALL_AUDIO)
                        writeInt(payloadSize)
                        writeInt(0) // 0-length ID
                        // no ID bytes written
                        write(audioBytes)
                        flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send call audio: ${e.message}", e)
            }
        }
    }

    suspend fun sendFileChunk(type: Int, messageId: String, chunkBytes: ByteArray, peerId: String? = null) {
        try {
            val idBytes = messageId.toByteArray(Charsets.UTF_8)
            val payloadSize = 4 + idBytes.size + chunkBytes.size
            val (outStream, mutex) = getStreamAndMutex(peerId)
            
            mutex.withLock {
                outStream?.apply {
                    write(type)
                    writeInt(payloadSize)
                    writeInt(idBytes.size)
                    write(idBytes)
                    write(chunkBytes)
                    flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send file chunk: ${e.message}", e)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        closeSocket()
    }

    private fun closeSocket() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}", e)
        }
    }
}
