package com.example.meshcall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import java.util.UUID
import kotlinx.coroutines.launch

enum class AppConnectionState {
    DISCONNECTED,
    TRANSPORT_CONNECTED,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    SESSION_ACTIVE,
    HOME_DASHBOARD,
    CONVERSATIONS_LIST,
    MESH_CALL_PLACEHOLDER,
    DISCOVERY
}

interface MeshServiceObserver {
    fun onPeersChanged(peers: Collection<WifiP2pDevice>)
    fun onConnectionStateChanged(isConnected: Boolean, device: WifiP2pDevice?, role: String?)
    fun onAppConnectionStateChanged(peerId: String?, state: AppConnectionState, remoteProfile: UserProfile?)
    fun onMessageReceived(msg: ChatMessage)
    fun onMessageDeleted(messageId: String)
    fun onWifiDirectEnabled(isEnabled: Boolean)
    fun onCallStateChanged(state: CallState, remoteProfile: UserProfile?, isCaller: Boolean)
}

class MeshService : Service(), WifiDirectListener {
    companion object {
        const val ACTION_REPLY = "com.example.meshcall.ACTION_REPLY"
        const val EXTRA_REPLY_TEXT = "extra_reply_text"
        const val ACTION_END_CALL = "com.example.meshcall.ACTION_END_CALL"
        const val ACTION_ACCEPT_CALL = "com.example.meshcall.ACTION_ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.example.meshcall.ACTION_DECLINE_CALL"
        const val EXTRA_OPEN_ACTIVE_CALL = "extra_open_active_call"
    }

    private val TAG = "MeshService"
    private val CHANNEL_ID = "MeshCallChannel"
    private val NOTIFICATION_ID = 1
    
    private val MESSAGE_CHANNEL_ID = "MeshCallMessageChannel"
    private val MESSAGE_NOTIFICATION_ID = 2

    private val CALL_CHANNEL_ID = "MeshCallCallsChannelV3"
    private val CALL_NOTIFICATION_ID = 3

    private val binder = LocalBinder()
    private var observer: MeshServiceObserver? = null

    private lateinit var wifiDirectManager: WifiDirectManager
    private var socketManager: SocketManager? = null
    val routeManager = RouteManager()
    private lateinit var callAudioEngine: CallAudioEngine
    private lateinit var callRingtoneManager: CallRingtoneManager
    
    val callManager = CallManager { type, targetUserId ->
        val conn = peerManager.getConnectionByUserId(targetUserId)
        val transportPeerId = conn?.peerId ?: conn?.tempId ?: return@CallManager
        socketManager?.sendControlMessage(type, UUID.randomUUID().toString(), "", peerId = transportPeerId)
    }

    var isWifiDirectEnabled = false
        private set
    var isConnected = false
        private set
    var isInitiator = false
        private set

    // App-level state per peer
    private val appConnectionStates = java.util.concurrent.ConcurrentHashMap<String, AppConnectionState>()


    val peerManager = PeerManager()
    val profileManager: ProfileManager by lazy {
        ProfileManager(applicationContext)
    }
    
    var currentTempId: String? = null
        private set

    lateinit var dbHelper: DatabaseHelper

    var currentDevice: WifiP2pDevice? = null
        private set
    var currentRole: String? = null
        private set
    val currentPeers = mutableListOf<WifiP2pDevice>()
    var activeConversationId: String? = null

    data class IncomingFileState(
        val tempFile: java.io.File,
        val fileName: String,
        val mimeType: String,
        val fileSize: Long,
        var bytesReceived: Long
    )
    private val incomingFiles = mutableMapOf<String, IncomingFileState>()

    inner class LocalBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    private val debugReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.example.meshcall.DEBUG_SEND_MESH_PACKET") {
                Log.i(TAG, "DEBUG_SEND_MESH_PACKET triggered")
                val myDeviceId = MeshIdentityManager.getInstance(this@MeshService).getDeviceId()
                val peerId = currentTempId ?: "UNKNOWN"
                
                try {
                    val testPacket = MeshPacket(
                        messageId = java.util.UUID.randomUUID().toString(),
                        sourceId = myDeviceId,
                        destinationId = peerId,
                        packetType = SocketManager.TYPE_TEXT,
                        ttl = 5,
                        hopCount = 0,
                        payload = "MESH_TEST".toByteArray(Charsets.UTF_8)
                    )
                    socketManager?.sendMeshPacket(testPacket)
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating/sending test MeshPacket: ${e.message}", e)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        val filter = android.content.IntentFilter("com.example.meshcall.DEBUG_SEND_MESH_PACKET")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(debugReceiver, filter)
        }

        dbHelper = DatabaseHelper(this)
        
        socketManager = SocketManager(
            peerManager = peerManager,
            onDataReceived = { type, messageId, payload, peerConn ->
                handleSocketDataReceived(type, messageId, payload, peerConn)
            },
            onConnectionStatusChanged = { connected, newPeerConn ->
                if (connected) {
                    currentTempId = newPeerConn?.tempId
                    socketManager?.sendMessage(UUID.randomUUID().toString(), "HELLO_MESH_CALL", peerId = currentTempId)
                    setAppConnectionState(AppConnectionState.TRANSPORT_CONNECTED, currentTempId)
                    
                    val myDeviceId = MeshIdentityManager.getInstance(this@MeshService).getDeviceId()
                    socketManager?.sendControlMessage(SocketManager.TYPE_IDENTITY_EXCHANGE, "", myDeviceId, peerId = currentTempId)
                    
                    if (isInitiator) {
                        sendConnectionRequest(newPeerConn?.tempId)
                    }
                } else {
                    if (newPeerConn == null) {
                        // General failure before peer connection was even established
                        if (peerManager.getAllConnections().isEmpty()) {
                            this.isConnected = false
                            this.currentRole = null
                            this.isInitiator = false
                            currentTempId = null
                            appConnectionStates.clear()
                            observer?.onConnectionStateChanged(false, null, null)
                        }
                    } else {
                        // Delegate to appDisconnect to handle individual connection teardown
                        val targetId = newPeerConn.peerId ?: newPeerConn.tempId
                        appDisconnect(targetId)
                    }
                }
            }
        )
        createNotificationChannel()
        wifiDirectManager = WifiDirectManager(this, this)
        wifiDirectManager.registerReceiver()
        
        callAudioEngine = CallAudioEngine(this)
        var lastAudioLogTime = 0L
        callAudioEngine.onAudioFrameCaptured = { audioBytes ->
            val targetUserId = callManager.activeCallUserId
            if (targetUserId != null) {
                val targetConnection = peerManager.getConnectionByUserId(targetUserId)
                val transportPeerId = targetConnection?.peerId ?: targetConnection?.tempId
                
                if (transportPeerId != null) {
                    socketManager?.sendCallAudio(audioBytes, peerId = transportPeerId)
                    val now = System.currentTimeMillis()
                    if (now - lastAudioLogTime > 2000) {
                        Log.d(TAG, "CALL_AUDIO_SEND:\ntargetUserId=$targetUserId\ntransportPeerId=$transportPeerId\naudioBytes=${audioBytes.size}")
                        lastAudioLogTime = now
                    }
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastAudioLogTime > 2000) {
                        Log.e(TAG, "CALL_AUDIO_SEND_FAILED:\nreason=No transport peer found for user $targetUserId")
                        lastAudioLogTime = now
                    }
                }
            } else {
                val now = System.currentTimeMillis()
                if (now - lastAudioLogTime > 2000) {
                    Log.w(TAG, "CALL_AUDIO_SEND_FAILED:\nreason=No active call userId")
                    lastAudioLogTime = now
                }
            }
        }
        
        callRingtoneManager = CallRingtoneManager(this)
        
        var callStartTime = 0L
        var callHasConnected = false
        var lastHandledCallState = CallState.IDLE
        var currentCallUserId: String? = null

        callManager.setObserver(object : CallManagerObserver {
            override fun onCallStateChanged(state: CallState, isCaller: Boolean) {
                val userId = callManager.activeCallUserId ?: currentCallUserId
                if (callManager.activeCallUserId != null) {
                    currentCallUserId = callManager.activeCallUserId
                }
                val profile = userId?.let { peerManager.getConnectionByUserId(it)?.profile }
                
                if (state == CallState.CONNECTED) {
                    callHasConnected = true
                    callStartTime = System.currentTimeMillis()
                    callRingtoneManager.stopAll()
                    callAudioEngine.start()
                    startCallForegroundNotification(profile)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(CALL_NOTIFICATION_ID)
                } else if (state == CallState.IDLE || state == CallState.ENDING) {
                    if (lastHandledCallState != CallState.IDLE && lastHandledCallState != CallState.ENDING) {
                        if (userId != null) {
                            val duration = if (callHasConnected) System.currentTimeMillis() - callStartTime else 0L
                            val callStatus = if (callHasConnected) "COMPLETED" else if (isCaller) "DECLINED" else "MISSED"
                            val callRecord = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                conversationId = userId,
                                text = callStatus,
                                isSent = isCaller,
                                type = MessageType.CALL,
                                durationMs = duration,
                                state = MessageState.SEEN
                            )
                            dbHelper.insertMessage(callRecord)
                            val prefix = if (isCaller) "📞 Outgoing call" else "📞 Incoming call"
                            dbHelper.insertOrUpdateConversation(
                                userId, profile?.username ?: "", profile?.displayName ?: "", profile?.avatarUri ?: "",
                                prefix, MessageType.CALL.ordinal, callRecord.timestamp
                            )
                            observer?.onMessageReceived(callRecord)
                        }
                        callStartTime = 0L
                        callHasConnected = false
                        currentCallUserId = null
                    }
                    callRingtoneManager.stopAll()
                    callAudioEngine.stop()
                    startForegroundServiceNotification()
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(CALL_NOTIFICATION_ID)
                } else if (state == CallState.INCOMING_CALL) {
                    callRingtoneManager.startRingtone()
                    showIncomingCallNotification(profile)
                } else if (state == CallState.OUTGOING_CALL) {
                    callRingtoneManager.startRingback()
                }
                lastHandledCallState = state
                observer?.onCallStateChanged(state, profile, isCaller)
            }
        })
    }

    fun setCallMuted(muted: Boolean) {
        if (::callAudioEngine.isInitialized) {
            callAudioEngine.setMute(muted)
        }
    }

    fun setCallSpeaker(speakerOn: Boolean) {
        if (::callAudioEngine.isInitialized) {
            callAudioEngine.setSpeaker(speakerOn)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "MeshService onStartCommand, action: ${intent?.action}")
        
        if (intent?.action == ACTION_REPLY) {
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val replyText = remoteInput?.getCharSequence(EXTRA_REPLY_TEXT)?.toString()
            val targetPeerId = intent.getStringExtra("EXTRA_PEER_ID")
            if (!replyText.isNullOrBlank() && targetPeerId != null) {
                sendMessage(replyText, targetPeerId)
                
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val repliedNotification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
                    .setContentTitle("MeshCall")
                    .setContentText("Reply sent: $replyText")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setTimeoutAfter(2000)
                    .build()
                manager.notify(MESSAGE_NOTIFICATION_ID, repliedNotification)
            }
        } else if (intent?.action == ACTION_END_CALL) {
            Log.i(TAG, "ACTION_END_CALL received via notification")
            callManager.endCall()
        } else if (intent?.action == ACTION_ACCEPT_CALL) {
            Log.i(TAG, "ACTION_ACCEPT_CALL received via notification")
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(CALL_NOTIFICATION_ID)
            callManager.acceptCall()
            
            val openIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_OPEN_ACTIVE_CALL, true)
            }
            startActivity(openIntent)
        } else if (intent?.action == ACTION_DECLINE_CALL) {
            Log.i(TAG, "ACTION_DECLINE_CALL received via notification")
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(CALL_NOTIFICATION_ID)
            callManager.declineCall()
        }
        
        startForegroundServiceNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "MeshService onBind")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "MeshService onDestroy")
        try {
            unregisterReceiver(debugReceiver)
        } catch (e: Exception) {
        }
        wifiDirectManager.unregisterReceiver()
        socketManager?.stop()
    }

    fun setObserver(newObserver: MeshServiceObserver?) {
        observer = newObserver
        if (newObserver != null) {
            newObserver.onWifiDirectEnabled(isWifiDirectEnabled)
            newObserver.onPeersChanged(currentPeers)
            newObserver.onConnectionStateChanged(isConnected, currentDevice, currentRole)
        }
    }

    fun discoverPeers() {
        wifiDirectManager.discoverPeers()
    }

    fun connect(device: WifiP2pDevice) {
        val mac = device.deviceAddress
        val existingUserId = routeManager.getUserIdForMac(mac)
        
        if (existingUserId != null) {
            Log.i(TAG, "INDIRECT ROUTE SELECTED: Discovered device $mac mapped to userId $existingUserId")
            Log.i(TAG, "INDIRECT CONNECTION REQUEST SENT via indirect route instead of physical Wi-Fi Direct.")
            sendIndirectConnectionRequest(existingUserId)
            return
        }

        currentDevice = device
        isInitiator = true
        wifiDirectManager.connect(device)
    }

    fun sendMessage(msg: String, targetPeerId: String?) {
        try {
            val messageId = UUID.randomUUID().toString()
            val convId = targetPeerId ?: return
            
            val targetConnection = peerManager.getConnectionByUserId(convId)
            if (targetConnection == null) {
                // Try indirect route
                if (routeManager.hasRouteTo(convId)) {
                    val msgBytes = msg.toByteArray(Charsets.UTF_8)
                    sendViaMeshRoute(
                        destinationUserId = convId,
                        type = SocketManager.TYPE_TEXT,
                        messageId = messageId,
                        payload = msgBytes
                    )
                    val chatMsg = ChatMessage(id = messageId, conversationId = convId, text = msg, isSent = true, type = MessageType.TEXT, state = MessageState.SENT)
                    dbHelper.insertMessage(chatMsg)
                    dbHelper.insertOrUpdateConversation(
                        convId, "", "", "", // dbHelper preserves existing profile if empty
                        msg, MessageType.TEXT.ordinal, chatMsg.timestamp
                    )
                    observer?.onMessageReceived(chatMsg)
                } else {
                    Log.w(TAG, "SEND_FAILED: No direct or indirect route found for userId: $convId")
                }
                return
            }
            val transportPeerId = targetConnection.peerId ?: targetConnection.tempId
            
            socketManager?.sendMessage(messageId, msg, peerId = transportPeerId)
            val chatMsg = ChatMessage(id = messageId, conversationId = convId, text = msg, isSent = true, type = MessageType.TEXT, state = MessageState.SENT)
            dbHelper.insertMessage(chatMsg)
            val peerProfile = peerManager.getConnectionByUserId(convId)?.profile
            dbHelper.insertOrUpdateConversation(
                convId, peerProfile?.username ?: "", peerProfile?.displayName ?: "", peerProfile?.avatarUri ?: "",
                msg, MessageType.TEXT.ordinal, chatMsg.timestamp
            )
            observer?.onMessageReceived(chatMsg)
        } catch (e: Exception) {
            throw e
        }
    }

    fun sendVoiceMessage(audioBytes: ByteArray, audioPath: String, durationMs: Long, targetPeerId: String?) {
        val messageId = UUID.randomUUID().toString()
        val convId = targetPeerId ?: return
        
        val targetConnection = peerManager.getConnectionByUserId(convId)
        if (targetConnection == null) {
            return
        }
        val transportPeerId = targetConnection.peerId ?: targetConnection.tempId
        
        socketManager?.sendVoiceMessage(messageId, audioBytes, peerId = transportPeerId)
        val chatMsg = ChatMessage(
            id = messageId,
            conversationId = convId,
            isSent = true,
            type = MessageType.VOICE,
            audioPath = audioPath,
            durationMs = durationMs,
            state = MessageState.SENT
        )
        dbHelper.insertMessage(chatMsg)
        val peerProfile = peerManager.getConnection(convId)?.profile
        dbHelper.insertOrUpdateConversation(
            convId, peerProfile?.username ?: "", peerProfile?.displayName ?: "", peerProfile?.avatarUri ?: "",
            "🎙️ Voice message", MessageType.VOICE.ordinal, chatMsg.timestamp
        )
        observer?.onMessageReceived(chatMsg)
    }

    fun sendAttachment(uri: android.net.Uri, targetPeerId: String?) {
        val convId = targetPeerId ?: return
        
        val targetConnection = peerManager.getConnectionByUserId(convId)
        if (targetConnection == null) {
            return
        }
        val transportPeerId = targetConnection.peerId ?: targetConnection.tempId
        
        val messageId = UUID.randomUUID().toString()
        
        var fileName = "attachment"
        var mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        var fileSize = 0L
        
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIndex != -1 && !cursor.isNull(nameIndex)) fileName = cursor.getString(nameIndex)
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) fileSize = cursor.getLong(sizeIndex)
            }
        }
        
        if (fileSize <= 0) {
            return
        }
        
        val type = if (mimeType.startsWith("image/")) MessageType.IMAGE else MessageType.FILE
        
        val chatMsg = ChatMessage(
            id = messageId,
            conversationId = convId,
            isSent = true,
            type = type,
            fileName = fileName,
            mimeType = mimeType,
            fileSize = fileSize,
            localFilePath = uri.toString(),
            state = MessageState.SENDING,
            progress = 0
        )
        dbHelper.insertMessage(chatMsg)
        val peerProfile = peerManager.getConnection(convId)?.profile
        dbHelper.insertOrUpdateConversation(
            convId, peerProfile?.username ?: "", peerProfile?.displayName ?: "", peerProfile?.avatarUri ?: "",
            if (type == MessageType.IMAGE) "📷 Photo" else "📄 Document", type.ordinal, chatMsg.timestamp
        )
        observer?.onMessageReceived(chatMsg)
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val json = org.json.JSONObject().apply {
                    put("fileName", fileName)
                    put("mimeType", mimeType)
                    put("fileSize", fileSize)
                }
                socketManager?.sendControlMessageSync(SocketManager.TYPE_FILE_METADATA, messageId, json.toString(), peerId = transportPeerId)
                
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val buffer = ByteArray(256 * 1024)
                    var bytesSent = 0L
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read == -1) break
                        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                        socketManager?.sendFileChunk(SocketManager.TYPE_FILE_CHUNK, messageId, chunk, peerId = transportPeerId)
                        bytesSent += read
                        val progress = ((bytesSent.toDouble() / fileSize) * 100).toInt()
                        chatMsg.progress = progress
                        observer?.onMessageReceived(chatMsg)
                    }
                }
                chatMsg.state = MessageState.SENT
                dbHelper.updateMessageState(messageId, MessageState.SENT)
                observer?.onMessageReceived(chatMsg)
            } catch (e: Exception) {
                chatMsg.state = MessageState.FAILED
                dbHelper.updateMessageState(messageId, MessageState.FAILED)
                observer?.onMessageReceived(chatMsg)
            }
        }
    }
    
    fun sendReaction(messageId: String, reaction: String, targetPeerId: String?) {
        val convId = targetPeerId ?: return
        
        val targetConnection = peerManager.getConnectionByUserId(convId)
        if (targetConnection == null) {
            if (routeManager.hasRouteTo(convId)) {
                sendViaMeshRoute(
                    destinationUserId = convId,
                    type = SocketManager.TYPE_REACTION_ADD,
                    messageId = messageId,
                    payload = reaction.toByteArray(Charsets.UTF_8)
                )
                dbHelper.addReaction(messageId, reaction)
            }
            return
        }
        val transportPeerId = targetConnection.peerId ?: targetConnection.tempId
        
        socketManager?.sendReaction(SocketManager.TYPE_REACTION_ADD, messageId, reaction, peerId = transportPeerId)
        dbHelper.addReaction(messageId, reaction)
    }

    fun sendMessageDelete(messageId: String, targetPeerId: String?) {
        val convId = targetPeerId ?: return
        
        val targetConnection = peerManager.getConnectionByUserId(convId)
        if (targetConnection == null) {
            if (routeManager.hasRouteTo(convId)) {
                sendViaMeshRoute(
                    destinationUserId = convId,
                    type = SocketManager.TYPE_MESSAGE_DELETE,
                    messageId = messageId,
                    payload = ByteArray(0)
                )
                deleteMessageLocalOnly(messageId)
            } else {
                Log.w(TAG, "SEND_FAILED: No connection found for userId: $convId")
            }
            return
        }
        val transportPeerId = targetConnection.peerId ?: targetConnection.tempId
        
        socketManager?.sendControlMessage(SocketManager.TYPE_MESSAGE_DELETE, messageId, "", peerId = transportPeerId)
        deleteMessageLocalOnly(messageId)
    }

    fun deleteMessageLocalOnly(messageId: String) {
        val msg = dbHelper.getMessage(messageId)
        if (msg != null) {
            safeDeleteLocalFile(msg)
            dbHelper.deleteMessage(messageId)
            observer?.onMessageDeleted(messageId)
        }
    }
    
    private fun safeDeleteLocalFile(msg: ChatMessage) {
        val path = msg.localFilePath ?: msg.audioPath ?: return
        val count = dbHelper.getMessageCountWithFile(path)
        if (count <= 1) {
            try {
                val f = java.io.File(path)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
            }
        }
    }

    fun markAsSeen(messageId: String) {
        val msg = dbHelper.getMessage(messageId)
        if (msg != null && !msg.isSent && msg.state != MessageState.SEEN) {
            dbHelper.updateMessageState(messageId, MessageState.SEEN)
            msg.state = MessageState.SEEN
            
            val targetConnection = peerManager.getConnectionByUserId(msg.conversationId)
            val transportPeerId = targetConnection?.peerId ?: targetConnection?.tempId
            if (transportPeerId != null) {
                socketManager?.sendAck(SocketManager.TYPE_SEEN_ACK, messageId, peerId = transportPeerId)
            }
            observer?.onMessageReceived(msg)
        }
    }
    
    fun updateActiveConversationId(convId: String?) {
        this.activeConversationId = convId
        if (convId != null) {
            val msgs = dbHelper.getMessagesForConversation(convId)
            val unreadReceived = msgs.filter { !it.isSent && it.state == MessageState.DELIVERED }
            val targetConnection = peerManager.getConnectionByUserId(convId)
            val transportPeerId = targetConnection?.peerId ?: targetConnection?.tempId
            
            unreadReceived.forEach {
                dbHelper.updateMessageState(it.id, MessageState.SEEN)
                it.state = MessageState.SEEN
                if (transportPeerId != null) {
                    socketManager?.sendAck(SocketManager.TYPE_SEEN_ACK, it.id, peerId = transportPeerId)
                }
                observer?.onMessageReceived(it)
            }
            dbHelper.clearUnread(convId)
        }
    }

    override fun onWifiDirectEnabled(isEnabled: Boolean) {
        isWifiDirectEnabled = isEnabled
        observer?.onWifiDirectEnabled(isEnabled)
    }

    override fun onPeersChanged(peers: Collection<WifiP2pDevice>) {
        currentPeers.clear()
        currentPeers.addAll(peers)
        observer?.onPeersChanged(peers)
    }

    override fun onConnectionChanged(
        isConnected: Boolean,
        device: WifiP2pDevice?,
        info: WifiP2pInfo?
    ) {
        if (this.isConnected == isConnected && isConnected) {
            return
        }

        this.isConnected = isConnected
        if (device != null) {
            currentDevice = device
        }

        if (isConnected && info != null) {
            currentRole = if (info.isGroupOwner) "Server" else "Client"
            observer?.onConnectionStateChanged(true, currentDevice, currentRole)

            if (info.isGroupOwner) {
                socketManager?.startServer(8888)
            } else {
                val hostAddress = info.groupOwnerAddress?.hostAddress
                if (hostAddress != null) {
                    socketManager?.startClient(hostAddress, 8888)
                }
            }
        } else {
            currentRole = null
            observer?.onConnectionStateChanged(false, null, null)
            socketManager?.stop()
        }
    }
private fun handleSocketDataReceived(type: Int, messageId: String, payload: ByteArray, peerConn: PeerConnection?, sourceUserId: String? = null) {
        when (type) {
            SocketManager.TYPE_CONNECTION_REQUEST -> {
                val jsonStr = String(payload, Charsets.UTF_8)
                Log.i("MeshCall_Diag", "M2D8_REQUEST_RECEIVED: jsonStr=$jsonStr")
                val profile = UserProfile.fromJson(jsonStr)
                peerConn?.profile = profile
                if (profile != null) {
                    dbHelper.updateConversationProfile(
                        profile.userId,
                        profile.username,
                        profile.displayName,
                        profile.avatarUri
                    )
                }
                val targetPeerId = peerConn?.peerId ?: peerConn?.tempId
                if (targetPeerId != null) {
                    setAppConnectionState(AppConnectionState.REQUEST_RECEIVED, targetPeerId, profile)
                } else if (profile != null) {
                    // Indirect route: logically track by userId
                    setAppConnectionState(AppConnectionState.REQUEST_RECEIVED, profile.userId, profile)
                }
            }
            SocketManager.TYPE_CONNECTION_ACCEPT -> {
                val jsonStr = String(payload, Charsets.UTF_8)
                val profile = UserProfile.fromJson(jsonStr)
                Log.i("MeshCall_Diag", "M2D9_ACCEPT_PROFILE_PARSED: profile.userId=${profile?.userId}, profile.username=${profile?.username}")
                peerConn?.profile = profile
                if (profile != null) {
                    dbHelper.updateConversationProfile(
                        profile.userId,
                        profile.username,
                        profile.displayName,
                        profile.avatarUri
                    )
                }
                val targetPeerId = peerConn?.peerId ?: peerConn?.tempId
                if (targetPeerId != null) {
                    setAppConnectionState(AppConnectionState.SESSION_ACTIVE, targetPeerId, profile)
                } else if (profile != null) {
                    // Indirect route
                    setAppConnectionState(AppConnectionState.SESSION_ACTIVE, profile.userId, profile)
                }
            }
            SocketManager.TYPE_CONNECTION_DECLINE -> {
                appDisconnect(peerConn?.peerId ?: peerConn?.tempId)
            }
            SocketManager.TYPE_DISCONNECT -> {
                appDisconnect(peerConn?.peerId ?: peerConn?.tempId)
            }
            SocketManager.TYPE_CALL_REQUEST -> {
                val convId = peerConn?.profile?.userId ?: return
                callManager.onCallRequestReceived(convId)
            }
            SocketManager.TYPE_CALL_ACCEPT -> {
                val convId = peerConn?.profile?.userId ?: return
                callManager.onCallAcceptReceived(convId)
            }
            SocketManager.TYPE_CALL_DECLINE -> {
                val convId = peerConn?.profile?.userId ?: return
                callManager.onCallDeclineReceived(convId)
            }
            SocketManager.TYPE_CALL_END -> {
                val convId = peerConn?.profile?.userId ?: return
                callManager.onCallEndReceived(convId)
            }
            SocketManager.TYPE_IDENTITY_EXCHANGE -> {
                val peerId = String(payload, Charsets.UTF_8)
                peerConn?.let {
                    peerManager.bindPeerId(it.tempId, peerId)
                }
                // When we learn the real peerId, if we are active, we should broadcast routes
                val state = getAppConnectionState(peerId)
                if (state == AppConnectionState.SESSION_ACTIVE) {
                    broadcastRoutingUpdate()
                }
            }
            SocketManager.TYPE_ROUTING_UPDATE -> {
                val jsonStr = String(payload, Charsets.UTF_8)
                val incomingPeerId = peerConn?.peerId ?: peerConn?.tempId ?: return
                
                try {
                    val jsonArray = org.json.JSONArray(jsonStr)
                    Log.i(TAG, "INDIRECT ROUTING UPDATE RECEIVED: from $incomingPeerId with ${jsonArray.length()} routes")
                    
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val userId = obj.getString("userId")
                        val mac = obj.getString("mac")
                        
                        if (userId != profileManager.getProfile()?.userId) {
                            routeManager.addOrUpdateRoute(userId, mac, incomingPeerId, 2)
                            Log.i(TAG, "INDIRECT ROUTING MAPPED: Discovered device $mac mapped to userId $userId")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse TYPE_ROUTING_UPDATE", e)
                }
            }
            SocketManager.TYPE_CALL_AUDIO -> {
                callAudioEngine.playRemoteAudio(payload)
            }
            SocketManager.TYPE_TEXT, SocketManager.TYPE_TEXT_V2 -> {
                val msg = String(payload, Charsets.UTF_8)
                if (msg != "HELLO_MESH_CALL") {
                    val transportPeerId = peerConn?.peerId ?: peerConn?.tempId
                    val convId = peerConn?.profile?.userId ?: sourceUserId ?: return
                    val isChatOpen = (activeConversationId == convId)
                    val chatMsg = ChatMessage(
                        id = messageId,
                        conversationId = convId,
                        text = msg,
                        isSent = false,
                        type = MessageType.TEXT,
                        state = if (isChatOpen) MessageState.SEEN else MessageState.DELIVERED
                    )
                    dbHelper.insertMessage(chatMsg)
                    dbHelper.insertOrUpdateConversation(
                        convId, peerConn?.profile?.username ?: "", peerConn?.profile?.displayName ?: "", peerConn?.profile?.avatarUri ?: "",
                        msg, MessageType.TEXT.ordinal, chatMsg.timestamp,
                        unreadIncrement = if (isChatOpen) 0 else 1
                    )
                    observer?.onMessageReceived(chatMsg)
                    
                    if (isChatOpen) {
                        if (transportPeerId != null) {
                            socketManager?.sendAck(SocketManager.TYPE_SEEN_ACK, messageId, peerId = transportPeerId)
                        } else {
                            sendViaMeshRoute(convId, SocketManager.TYPE_SEEN_ACK, messageId, ByteArray(0))
                        }
                    } else {
                        if (transportPeerId != null) {
                            socketManager?.sendAck(SocketManager.TYPE_DELIVERY_ACK, messageId, peerId = transportPeerId)
                        } else {
                            sendViaMeshRoute(convId, SocketManager.TYPE_DELIVERY_ACK, messageId, ByteArray(0))
                        }
                        showIncomingMessageNotification(msg, convId)
                    }
                }
            }
            SocketManager.TYPE_VOICE -> {
                try {
                    val audioFile = java.io.File(cacheDir, "voice_rx_${System.currentTimeMillis()}.m4a")
                    audioFile.writeBytes(payload)
                    val transportPeerId = peerConn?.peerId ?: peerConn?.tempId
                    val convId = peerConn?.profile?.userId ?: return
                    val isChatOpen = (activeConversationId == convId)
                    val chatMsg = ChatMessage(
                        id = messageId,
                        conversationId = convId,
                        isSent = false,
                        type = MessageType.VOICE,
                        audioPath = audioFile.absolutePath,
                        durationMs = 0L,
                        state = if (isChatOpen) MessageState.SEEN else MessageState.DELIVERED
                    )
                    dbHelper.insertMessage(chatMsg)
                    dbHelper.insertOrUpdateConversation(
                        convId, peerConn?.profile?.username ?: "", peerConn?.profile?.displayName ?: "", peerConn?.profile?.avatarUri ?: "",
                        "🎙️ Voice message", MessageType.VOICE.ordinal, chatMsg.timestamp,
                        unreadIncrement = if (isChatOpen) 0 else 1
                    )
                    observer?.onMessageReceived(chatMsg)
                    
                    if (isChatOpen) {
                        if (transportPeerId != null) socketManager?.sendAck(SocketManager.TYPE_SEEN_ACK, messageId, peerId = transportPeerId)
                        else sendViaMeshRoute(convId, SocketManager.TYPE_SEEN_ACK, messageId, ByteArray(0))
                    } else {
                        if (transportPeerId != null) socketManager?.sendAck(SocketManager.TYPE_DELIVERY_ACK, messageId, peerId = transportPeerId)
                        else sendViaMeshRoute(convId, SocketManager.TYPE_DELIVERY_ACK, messageId, ByteArray(0))
                        showIncomingMessageNotification("🎙️ Voice message", convId)
                    }
                } catch (e: Exception) {
                }
            }
            SocketManager.TYPE_DELIVERY_ACK -> {
                Log.i(TAG, "RECEIVED_DELIVERY_ACK for message: $messageId")
                dbHelper.updateMessageState(messageId, MessageState.DELIVERED)
                val convId = peerConn?.profile?.userId ?: sourceUserId ?: return
                val msgs = dbHelper.getMessagesForConversation(convId)
                val chatMsg = msgs.find { it.id == messageId }
                if (chatMsg != null) {
                    chatMsg.state = MessageState.DELIVERED
                    observer?.onMessageReceived(chatMsg)
                }
            }
            SocketManager.TYPE_SEEN_ACK -> {
                Log.i(TAG, "RECEIVED_SEEN_ACK for message: $messageId")
                dbHelper.updateMessageState(messageId, MessageState.SEEN)
                val convId = peerConn?.profile?.userId ?: sourceUserId ?: return
                val msgs = dbHelper.getMessagesForConversation(convId)
                val chatMsg = msgs.find { it.id == messageId }
                if (chatMsg != null) {
                    chatMsg.state = MessageState.SEEN
                    observer?.onMessageReceived(chatMsg)
                }
            }
            SocketManager.TYPE_REACTION_ADD -> {
                val reaction = String(payload, Charsets.UTF_8)
                val convId = peerConn?.profile?.userId ?: sourceUserId ?: return
                val msgs = dbHelper.getMessagesForConversation(convId)
                val chatMsg = msgs.find { it.id == messageId }
                if (chatMsg != null) {
                    dbHelper.addReaction(messageId, reaction)
                    observer?.onMessageReceived(chatMsg)
                }
            }
            SocketManager.TYPE_REACTION_REMOVE -> {
                val reaction = String(payload, Charsets.UTF_8)
                dbHelper.removeReaction(messageId, reaction)
                val convId = peerConn?.profile?.userId ?: sourceUserId ?: return
                val msgs = dbHelper.getMessagesForConversation(convId)
                val msg = msgs.find { it.id == messageId }
                if (msg != null) observer?.onMessageReceived(msg)
            }
            SocketManager.TYPE_MESSAGE_DELETE -> {
                val msg = dbHelper.getMessage(messageId)
                if (msg != null && !msg.isSent) {
                    safeDeleteLocalFile(msg)
                    dbHelper.deleteMessage(messageId)
                    observer?.onMessageDeleted(messageId)
                }
            }
            SocketManager.TYPE_FILE_METADATA -> {
                val jsonStr = String(payload, Charsets.UTF_8)
                val json = org.json.JSONObject(jsonStr)
                val fileName = json.getString("fileName")
                val mimeType = json.getString("mimeType")
                val fileSize = json.getLong("fileSize")
                val finalDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                val tempFile = java.io.File(finalDir, "rx_${messageId}.tmp")
                incomingFiles[messageId] = IncomingFileState(tempFile, fileName, mimeType, fileSize, 0L)
                val type = if (mimeType.startsWith("image/")) MessageType.IMAGE else MessageType.FILE
                val convId = peerConn?.profile?.userId ?: return
                val chatMsg = ChatMessage(
                    id = messageId,
                    conversationId = convId,
                    isSent = false,
                    type = type,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    state = MessageState.SENDING,
                    progress = 0
                )
                dbHelper.insertMessage(chatMsg)
                observer?.onMessageReceived(chatMsg)
            }
            SocketManager.TYPE_FILE_CHUNK -> {
                val state = incomingFiles[messageId] ?: return
                try {
                    java.io.FileOutputStream(state.tempFile, true).use { fos -> fos.write(payload) }
                    state.bytesReceived += payload.size
                    val convId = peerConn?.profile?.userId ?: return
                    if (state.bytesReceived >= state.fileSize) {
                        incomingFiles.remove(messageId)
                        val finalDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                        val finalFile = java.io.File(finalDir, "${System.currentTimeMillis()}_${state.fileName}")
                        state.tempFile.renameTo(finalFile)
                        val type = if (state.mimeType.startsWith("image/")) MessageType.IMAGE else MessageType.FILE
                        val isChatOpen = (activeConversationId == convId)
                        val finalMsg = ChatMessage(
                            id = messageId,
                            conversationId = convId,
                            isSent = false,
                            type = type,
                            fileName = state.fileName,
                            mimeType = state.mimeType,
                            fileSize = state.fileSize,
                            localFilePath = finalFile.absolutePath,
                            state = if (isChatOpen) MessageState.SEEN else MessageState.DELIVERED,
                            progress = 100
                        )
                        dbHelper.insertMessage(finalMsg)
                        dbHelper.insertOrUpdateConversation(
                            convId, peerConn?.profile?.username ?: "", peerConn?.profile?.displayName ?: "", peerConn?.profile?.avatarUri ?: "",
                            if (type == MessageType.IMAGE) "📷 Photo" else "📄 Document", type.ordinal, finalMsg.timestamp,
                            unreadIncrement = if (isChatOpen) 0 else 1
                        )
                        observer?.onMessageReceived(finalMsg)
                        if (isChatOpen) socketManager?.sendAck(SocketManager.TYPE_SEEN_ACK, messageId, peerId = convId)
                        else socketManager?.sendAck(SocketManager.TYPE_DELIVERY_ACK, messageId, peerId = convId)
                    } else {
                        val msgs = dbHelper.getMessagesForConversation(convId)
                        val chatMsg = msgs.find { it.id == messageId }
                        if (chatMsg != null) {
                            chatMsg.progress = ((state.bytesReceived.toDouble() / state.fileSize) * 100).toInt()
                            observer?.onMessageReceived(chatMsg)
                        }
                    }
                } catch (e: Exception) {
                    incomingFiles.remove(messageId)
                    state.tempFile.delete()
                }
            }
            SocketManager.TYPE_MESH_PACKET -> {
                handleMeshPacket(payload, peerConn)
            }
        }
    }
    
    private fun handleMeshPacket(payload: ByteArray, peerConn: PeerConnection?) {
        val jsonStr = String(payload, Charsets.UTF_8)
        val packet = MeshPacket.fromJson(jsonStr) ?: return
        
        Log.i(TAG, "Handling MeshPacket: ${packet.packetId} from ${packet.sourceId} to ${packet.destinationId}")
        
        // 1. Loop and duplicate protection
        if (routeManager.isDuplicatePacket(packet.packetId)) {
            Log.i(TAG, "Dropped duplicate MeshPacket: ${packet.packetId}")
            return
        }
        
        val incomingPeerId = peerConn?.peerId ?: peerConn?.tempId
        
        // 2. Are we the destination?
        val myUserId = profileManager.getProfile()?.userId
        if (packet.destinationId == myUserId) {
            Log.i(TAG, "MeshPacket reached final destination: $myUserId")
            
            // Learn route to source from this packet
            if (incomingPeerId != null && packet.sourceId.isNotEmpty()) {
                routeManager.addOrUpdateRoute(packet.sourceId, nextHopPeerId = incomingPeerId, hopCount = packet.hopCount + 1)
            }
            
            // Process the encapsulated payload exactly as if it came natively
            // Special handling for DECLINE since it lacks payload source info for indirect
            if (packet.packetType == SocketManager.TYPE_CONNECTION_DECLINE) {
                setAppConnectionState(AppConnectionState.DISCONNECTED, packet.sourceId)
            } else {
                handleSocketDataReceived(packet.packetType, packet.messageId, packet.payload, null, packet.sourceId)
            }
            return
        }
        
        // 3. We are a forwarding node (Relay)
        val decrementedTtl = packet.ttl - 1
        if (decrementedTtl <= 0) {
            Log.i(TAG, "Dropped MeshPacket due to TTL expiration: ${packet.packetId}")
            return
        }
        
        val nextHopPeerId = routeManager.getNextHopFor(packet.destinationId) 
            ?: peerManager.getConnectionByUserId(packet.destinationId)?.let { it.peerId ?: it.tempId }
            
        if (nextHopPeerId == null) {
            Log.i(TAG, "Dropped MeshPacket: No route to destination ${packet.destinationId}")
            return
        }
        
        if (nextHopPeerId == incomingPeerId) {
            Log.i(TAG, "Dropped MeshPacket: Prevented reverse-hop loop to $incomingPeerId")
            return
        }
        
        val forwardedPacket = packet.copy(
            ttl = decrementedTtl,
            hopCount = packet.hopCount + 1
        )
        
        Log.i(TAG, "Forwarding MeshPacket to $nextHopPeerId")
        socketManager?.sendMeshPacket(forwardedPacket, peerId = nextHopPeerId)
    }

    private fun setAppConnectionState(state: AppConnectionState, peerId: String?, profile: UserProfile? = null) {
        if (peerId == null) return
        val currentState = appConnectionStates[peerId] ?: AppConnectionState.DISCONNECTED
        if (currentState != state) {
            appConnectionStates[peerId] = state
            observer?.onAppConnectionStateChanged(peerId, state, profile)
            
            if (state == AppConnectionState.SESSION_ACTIVE || state == AppConnectionState.DISCONNECTED) {
                broadcastRoutingUpdate()
            }
        }
    }

    private fun broadcastRoutingUpdate() {
        val myProfile = profileManager.getProfile() ?: return
        val directConns = peerManager.getAllConnections().filter { getAppConnectionState(it.peerId ?: it.tempId) == AppConnectionState.SESSION_ACTIVE }
        
        val routingDataList = mutableListOf<org.json.JSONObject>()
        for (conn in directConns) {
            val profile = conn.profile ?: continue
            val mac = conn.peerId ?: continue // We need MAC to resolve WifiP2pDevice
            val routeObj = org.json.JSONObject().apply {
                put("userId", profile.userId)
                put("mac", mac)
            }
            routingDataList.add(routeObj)
        }
        
        if (routingDataList.isEmpty()) return
        
        val updatePayload = org.json.JSONArray(routingDataList).toString()
        
        for (conn in directConns) {
            val peerId = conn.peerId ?: conn.tempId ?: continue
            socketManager?.sendControlMessage(SocketManager.TYPE_ROUTING_UPDATE, UUID.randomUUID().toString(), updatePayload, peerId)
            Log.i(TAG, "INDIRECT ROUTING ADVERTISEMENT SENT: Sent routing update to $peerId with ${routingDataList.size} destinations")
        }
    }

    fun getAppConnectionState(peerId: String?): AppConnectionState {
        if (peerId == null) return AppConnectionState.DISCONNECTED
        return appConnectionStates[peerId] ?: AppConnectionState.DISCONNECTED
    }
    
    fun sendConnectionRequest(targetPeerId: String?) {
        if (targetPeerId == null) return
        val profileJson = profileManager.getProfile()?.toJson() ?: return
        setAppConnectionState(AppConnectionState.REQUEST_SENT, targetPeerId)
        socketManager?.sendControlMessage(SocketManager.TYPE_CONNECTION_REQUEST, UUID.randomUUID().toString(), profileJson, peerId = targetPeerId)
    }

    fun sendViaMeshRoute(destinationUserId: String, type: Int, messageId: String, payload: ByteArray) {
        val nextHopPeerId = routeManager.getNextHopFor(destinationUserId) ?: return
        val myUserId = profileManager.getProfile()?.userId ?: return
        
        val meshPacket = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            messageId = messageId,
            sourceId = myUserId,
            destinationId = destinationUserId,
            packetType = type,
            ttl = 10,
            hopCount = 0,
            payload = payload
        )
        socketManager?.sendMeshPacket(meshPacket, peerId = nextHopPeerId)
    }

    fun sendIndirectConnectionRequest(targetUserId: String) {
        val profileJson = profileManager.getProfile()?.toJson() ?: return
        // Track logical state using userId instead of peerId
        setAppConnectionState(AppConnectionState.REQUEST_SENT, targetUserId)
        sendViaMeshRoute(
            destinationUserId = targetUserId,
            type = SocketManager.TYPE_CONNECTION_REQUEST,
            messageId = UUID.randomUUID().toString(),
            payload = profileJson.toByteArray(Charsets.UTF_8)
        )
    }

    fun acceptConnectionRequest(targetUserId: String?) {
        if (targetUserId == null) return
        val profileJson = profileManager.getProfile()?.toJson() ?: return
        val targetConn = peerManager.getAllConnections().find { it.profile?.userId == targetUserId }
        val targetPeerId = targetConn?.peerId
        if (targetPeerId != null) {
            setAppConnectionState(AppConnectionState.SESSION_ACTIVE, targetPeerId, targetConn.profile)
            socketManager?.sendControlMessage(SocketManager.TYPE_CONNECTION_ACCEPT, UUID.randomUUID().toString(), profileJson, peerId = targetPeerId)
        } else {
            acceptIndirectConnectionRequest(targetUserId)
        }
    }

    private fun acceptIndirectConnectionRequest(targetUserId: String) {
        val profileJson = profileManager.getProfile()?.toJson() ?: return
        setAppConnectionState(AppConnectionState.SESSION_ACTIVE, targetUserId)
        sendViaMeshRoute(
            destinationUserId = targetUserId,
            type = SocketManager.TYPE_CONNECTION_ACCEPT,
            messageId = UUID.randomUUID().toString(),
            payload = profileJson.toByteArray(Charsets.UTF_8)
        )
    }

    fun declineConnectionRequest(targetUserId: String?) {
        if (targetUserId == null) return
        val targetConn = peerManager.getAllConnections().find { it.profile?.userId == targetUserId }
        val targetPeerId = targetConn?.peerId
        if (targetPeerId != null) {
            setAppConnectionState(AppConnectionState.DISCONNECTED, targetPeerId)
            socketManager?.sendControlMessage(SocketManager.TYPE_CONNECTION_DECLINE, UUID.randomUUID().toString(), "", peerId = targetPeerId)
            appDisconnect(targetPeerId)
        } else {
            declineIndirectConnectionRequest(targetUserId)
        }
    }

    private fun declineIndirectConnectionRequest(targetUserId: String) {
        setAppConnectionState(AppConnectionState.DISCONNECTED, targetUserId)
        sendViaMeshRoute(
            destinationUserId = targetUserId,
            type = SocketManager.TYPE_CONNECTION_DECLINE,
            messageId = UUID.randomUUID().toString(),
            payload = ByteArray(0)
        )
    }

    fun appDisconnect(targetPeerId: String?) {
        if (targetPeerId == null) {
            peerManager.closeAll()
            appConnectionStates.clear()
            callManager.activeCallUserId?.let { callManager.onConnectionLost(it) }
            isInitiator = false
            socketManager?.stop()
            wifiDirectManager.disconnect()
            return
        }

        val conn = peerManager.getConnection(targetPeerId)
        val userId = conn?.profile?.userId
        if (conn != null) {
            try {
                conn.socket.close()
            } catch (e: Exception) {}
            peerManager.removeConnection(conn)
        }
        
        if (userId != null) {
            callManager.onConnectionLost(userId)
        }
        
        // If there are no more connections, clean up
        if (peerManager.getAllConnections().isEmpty()) {
            appConnectionStates.clear()
            isInitiator = false
            socketManager?.stop()
            wifiDirectManager.disconnect()
        }
    }

    private fun startForegroundServiceNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeshCall")
            .setContentText("MeshCall — Connected")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCallForegroundNotification(profile: UserProfile?) {
        val name = profile?.displayName?.ifEmpty { profile.username } ?: "User"
        
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPEN_ACTIVE_CALL, true)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 1, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val endCallIntent = Intent(this, MeshService::class.java).apply {
            action = ACTION_END_CALL
        }
        val endCallPendingIntent = android.app.PendingIntent.getService(
            this, 2, endCallIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val endCallAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "End Call",
            endCallPendingIntent
        ).build()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeshCall")
            .setContentText("Call with $name")
            .setSubText("Tap to return to call")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(endCallAction)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "MeshCall Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "MeshCall Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "MeshCall Calls",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(messageChannel)
            manager?.createNotificationChannel(callChannel)
        }
    }

    private fun showIncomingMessageNotification(msg: String, peerId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(EXTRA_REPLY_TEXT)
            .setLabel("Type a message...")
            .build()

        val replyIntent = Intent(this, MeshService::class.java).apply {
            action = ACTION_REPLY
            putExtra("EXTRA_PEER_ID", peerId)
        }
        val replyPendingIntent = android.app.PendingIntent.getService(
            this,
            0,
            replyIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_send,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val senderName = peerManager.getConnectionByUserId(peerId)?.profile?.displayName ?: "MeshCall"

        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setContentTitle(senderName)
            .setContentText(msg)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(replyAction)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(peerId.hashCode(), notification)
    }

    private fun showIncomingCallNotification(profile: UserProfile?) {
        val callerName = profile?.displayName?.ifEmpty { profile.username } ?: "Unknown Caller"
        val callerUsername = profile?.username ?: "unknown"

        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_CALLER_NAME", callerName)
            putExtra("EXTRA_CALLER_USERNAME", callerUsername)
            putExtra("EXTRA_CALLER_AVATAR", profile?.avatarUri)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(this, MeshService::class.java).apply {
            action = ACTION_ACCEPT_CALL
        }
        val acceptPendingIntent = android.app.PendingIntent.getService(
            this, 1, acceptIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(this, MeshService::class.java).apply {
            action = ACTION_DECLINE_CALL
        }
        val declinePendingIntent = android.app.PendingIntent.getService(
            this, 2, declineIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setContentTitle("Incoming Call")
            .setContentText(callerName)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_call_accept, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_call_end, "Decline", declinePendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(CALL_NOTIFICATION_ID, notification)
    }
}
