package com.example.meshcall

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.app.NotificationManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.view.MotionEvent
import java.io.File
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), MeshServiceObserver {
    private val TAG = "MainActivity"

    private var meshService: MeshService? = null
    private var isBound = false

    private lateinit var toolbar: Toolbar
    private lateinit var conversationsContainer: View
    private lateinit var discoveryContainer: View
    private lateinit var connectionRequestContainer: View
    private lateinit var connectedMenuContainer: View
    private lateinit var homeDashboardContainer: View
    private lateinit var meshCallPlaceholderContainer: View
    private lateinit var chatContainer: View
    private lateinit var appBarLayout: View
    
    private lateinit var textHomeDisplayName: TextView
    private lateinit var cardHomeChat: View
    private lateinit var cardHomeCall: View
    private lateinit var buttonCallFindPeople: Button
    
    private lateinit var recyclerViewConversations: RecyclerView
    private lateinit var textEmptyConversations: TextView
    private lateinit var fabNewChat: View
    
    private lateinit var buttonDiscover: Button
    private lateinit var textDiscoveryStatus: TextView
    private lateinit var recyclerViewPeers: RecyclerView
    
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: ImageButton
    
    // Call UI
    private lateinit var callIncomingContainer: View
    private lateinit var callActiveContainer: View
    private lateinit var textIncomingCallName: TextView
    private lateinit var textIncomingCallUsername: TextView
    private lateinit var buttonCallAccept: Button
    private lateinit var buttonCallDecline: Button
    private lateinit var textActiveCallName: TextView
    private lateinit var textActiveCallStatus: TextView
    private lateinit var textActiveCallDuration: TextView
    private lateinit var toggleMute: android.widget.ToggleButton
    private lateinit var toggleSpeaker: android.widget.ToggleButton
    private lateinit var buttonEndCall: Button
    
    private var callDurationSeconds = 0
    private var callDurationRunnable: Runnable? = null

    // Adapters
    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var peerAdapter: PeerAdapter
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var callHistoryAdapter: CallHistoryAdapter
    private lateinit var recyclerViewCallHistory: RecyclerView
    private lateinit var layoutEmptyCallHistory: View
    
    private var activeConversationId: String? = null
    
    // Voice
    private lateinit var voiceRecorder: VoiceRecorder
    private lateinit var voicePlayer: VoicePlayer
    private var isRecording = false
    private lateinit var textRecordingStatus: TextView
    private lateinit var buttonMic: ImageButton

    private val PERMISSIONS_REQUEST_CODE = 123
    private val wifiStateHelper = WifiStateHelper(this)

    private val pickAttachmentLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            showAttachmentPreviewDialog(uri)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MeshService.LocalBinder
            meshService = binder.getService()
            isBound = true
            meshService?.updateActiveConversationId(activeConversationId)
            meshService?.setObserver(this@MainActivity)
            
            checkActiveCallIntent(intent)
            
            // Initial load of conversations
            loadConversations()
            
            // Ensure correct initial state
            val state = meshService?.getAppConnectionState(meshService?.peerManager?.getConnectionByUserId(activeConversationId ?: "")?.peerId)
            if (state == AppConnectionState.SESSION_ACTIVE) {
                updateUIState(AppConnectionState.SESSION_ACTIVE)
            } else if (state == AppConnectionState.DISCONNECTED || state == AppConnectionState.HOME_DASHBOARD) {
                updateUIState(AppConnectionState.HOME_DASHBOARD)
            } else {
                updateUIState(state ?: AppConnectionState.HOME_DASHBOARD)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            meshService?.setObserver(null)
            meshService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // M1 Integration: Initialize and ensure the persistent device ID is generated
        val deviceId = MeshIdentityManager.getInstance(applicationContext).getDeviceId()
        Log.i("MeshCall_Identity", "Device ID initialized: $deviceId")
        
        if (savedInstanceState != null) {
            Log.i("MeshCall_Nav", "ACTIVITY_RECREATED")
        }
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentState = when {
                    chatContainer.visibility == View.VISIBLE -> "ACTIVE_CHAT"
                    discoveryContainer.visibility == View.VISIBLE -> "DISCOVERY"
                    conversationsContainer.visibility == View.VISIBLE -> "CONVERSATIONS_LIST"
                    meshCallPlaceholderContainer.visibility == View.VISIBLE -> "MESH_CALL_PLACEHOLDER"
                    else -> "HOME_DASHBOARD"
                }
                
                val convIdLog = if (activeConversationId != null) " conversationId=$activeConversationId" else ""
                Log.i("MeshCall_Nav", "BACK_PRESSED state=$currentState$convIdLog")
                
                when {
                    callActiveContainer.visibility == View.VISIBLE -> {
                        Log.i("MeshCall_Nav", "CALL_UI_HIDE")
                        callActiveContainer.visibility = View.GONE
                    }
                    chatContainer.visibility == View.VISIBLE -> {
                        Log.i("MeshCall_Nav", "CHAT_CLOSE")
                        activeConversationId = null
                        meshService?.updateActiveConversationId(null)
                        Log.i("MeshCall_Nav", "NAVIGATE_TO_CONVERSATIONS")
                        updateUIState(AppConnectionState.CONVERSATIONS_LIST)
                        loadConversations()
                    }
                    discoveryContainer.visibility == View.VISIBLE || 
                    conversationsContainer.visibility == View.VISIBLE || 
                    meshCallPlaceholderContainer.visibility == View.VISIBLE -> {
                        Log.i("MeshCall_Nav", "NAVIGATE_TO_HOME")
                        updateUIState(AppConnectionState.HOME_DASHBOARD)
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
        
        val profileManager = ProfileManager(this)
        if (profileManager.getProfile() == null) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        
        setContentView(R.layout.activity_main)

        initViews()
        voiceRecorder = VoiceRecorder(this)
        voicePlayer = VoicePlayer()
        setupAdapters()
        setupListeners()

        if (!hasPermissions()) {
            requestPermissions()
        } else {
            startMeshService()
        }
    }

    private fun startMeshService() {
        val intent = Intent(this, MeshService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasPermissions()) {
            val intent = Intent(this, MeshService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    override fun onResume() {
        super.onResume()
        meshService?.updateActiveConversationId(activeConversationId)
        
        // Clear message notifications for active chat if open
        if (activeConversationId != null) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(activeConversationId!!.hashCode())
        }
        
        checkActiveCallIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent
        checkActiveCallIntent(intent)
    }

    private fun checkActiveCallIntent(currentIntent: Intent?) {
        if (currentIntent?.getBooleanExtra(MeshService.EXTRA_OPEN_ACTIVE_CALL, false) == true) {
            Log.i(TAG, "OPEN_ACTIVE_CALL requested via intent")
            val state = meshService?.callManager?.currentState
            if (state != null && state != CallState.IDLE) {
                val activeUserId = meshService?.callManager?.activeCallUserId
                val profile = activeUserId?.let { meshService?.peerManager?.getConnectionByUserId(it)?.profile }
                onCallStateChanged(state, profile, meshService?.callManager?.isCaller ?: false)
            }
            // Clear the extra so we don't reopen it on every orientation change
            currentIntent.removeExtra(MeshService.EXTRA_OPEN_ACTIVE_CALL)
        }
    }
    
    override fun onPause() {
        super.onPause()
        meshService?.updateActiveConversationId(null)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            meshService?.setObserver(null)
            unbindService(serviceConnection)
            isBound = false
        }
    }
    
    override fun onDestroy() {
        Log.i("MeshCall_Nav", "ACTIVITY_FINISH")
        super.onDestroy()
        voiceRecorder.release()
        voicePlayer.release()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        appBarLayout = findViewById(R.id.appBarLayout)
        setSupportActionBar(toolbar)
        
        conversationsContainer = findViewById(R.id.conversationsContainer)
        recyclerViewConversations = findViewById(R.id.recyclerViewConversations)
        textEmptyConversations = findViewById(R.id.textEmptyConversations)
        fabNewChat = findViewById(R.id.fabNewChat)
        
        homeDashboardContainer = findViewById(R.id.homeDashboardContainer)
        meshCallPlaceholderContainer = findViewById(R.id.meshCallPlaceholderContainer)
        val rvHistory = findViewById<RecyclerView>(R.id.recyclerViewCallHistory)
        if (rvHistory != null) {
            recyclerViewCallHistory = rvHistory
            layoutEmptyCallHistory = findViewById(R.id.layoutEmptyCallHistory)
        }
        textHomeDisplayName = findViewById(R.id.textHomeDisplayName)
        cardHomeChat = findViewById(R.id.cardHomeChat)
        cardHomeCall = findViewById(R.id.cardHomeCall)
        buttonCallFindPeople = findViewById(R.id.buttonCallFindPeople)
        
        val profileManager = ProfileManager(this)
        textHomeDisplayName.text = profileManager.getProfile()?.displayName ?: "User"
        
        discoveryContainer = findViewById(R.id.discoveryContainer)
        connectionRequestContainer = findViewById(R.id.includeConnectionRequest)
        connectedMenuContainer = findViewById(R.id.includeConnectedMenu)
        chatContainer = findViewById(R.id.chatContainer)
        
        buttonDiscover = findViewById(R.id.buttonDiscover)
        textDiscoveryStatus = findViewById(R.id.textDiscoveryStatus)
        recyclerViewPeers = findViewById(R.id.recyclerViewPeers)
        
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        textRecordingStatus = findViewById(R.id.textRecordingStatus)
        buttonMic = findViewById(R.id.buttonMic)
        
        findViewById<View>(R.id.buttonChatBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        findViewById<View>(R.id.buttonChatAttachment).setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            popup.menu.add("Photo")
            popup.menu.add("Document")
            popup.menu.add("Location")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Photo" -> pickAttachmentLauncher.launch("image/*")
                    "Document" -> pickAttachmentLauncher.launch("*/*")
                    "Location" -> {
                        val locationHelper = LocationHelper(this@MainActivity)
                        if (!locationHelper.hasLocationPermission()) {
                            locationHelper.requestLocationPermission()
                        } else {
                            fetchAndSendLocation(locationHelper)
                        }
                    }
                }
                true
            }
            popup.show()
        }
        
        findViewById<View>(R.id.buttonChatCall).setOnClickListener {
            if (activeConversationId != null) {
                meshService?.callManager?.placeCall(activeConversationId!!)
            }
        }
        
        findViewById<View>(R.id.buttonChatMore).setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            popup.menu.add("Delete Chat")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Delete Chat" -> {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Delete Chat")
                            .setMessage("Delete this entire chat?")
                            .setPositiveButton("Delete") { _, _ ->
                                activeConversationId?.let { convId ->
                                    meshService?.dbHelper?.deleteConversation(convId)
                                    activeConversationId = null
                                    meshService?.updateActiveConversationId(null)
                                    updateUIState(AppConnectionState.CONVERSATIONS_LIST)
                                    loadConversations()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
        
        callIncomingContainer = findViewById(R.id.includeCallIncoming)
        callActiveContainer = findViewById(R.id.includeCallActive)
        textIncomingCallName = findViewById(R.id.textIncomingCallName)
        textIncomingCallUsername = findViewById(R.id.textIncomingCallUsername)
        buttonCallAccept = findViewById(R.id.buttonCallAccept)
        buttonCallDecline = findViewById(R.id.buttonCallDecline)
        textActiveCallName = findViewById(R.id.textActiveCallName)
        textActiveCallStatus = findViewById(R.id.textActiveCallStatus)
        textActiveCallDuration = findViewById(R.id.textActiveCallDuration)
        toggleMute = findViewById(R.id.toggleMute)
        toggleSpeaker = findViewById(R.id.toggleSpeaker)
        buttonEndCall = findViewById(R.id.buttonEndCall)
    }

    private fun setupAdapters() {
        conversationAdapter = ConversationAdapter { conversation ->
            Log.i("MeshCall_Diag", "Clicked conversation: ${conversation.peerUserId}")
            openChat(conversation.peerUserId, conversation.peerDisplayName.ifEmpty { conversation.peerUsername }, conversation.peerAvatarUri)
        }
        recyclerViewConversations.layoutManager = LinearLayoutManager(this)
        recyclerViewConversations.adapter = conversationAdapter

        peerAdapter = PeerAdapter { device ->
            val statusStr = when (device.status) {
                android.net.wifi.p2p.WifiP2pDevice.AVAILABLE   -> "AVAILABLE"
                android.net.wifi.p2p.WifiP2pDevice.INVITED     -> "INVITED"
                android.net.wifi.p2p.WifiP2pDevice.CONNECTED   -> "CONNECTED"
                android.net.wifi.p2p.WifiP2pDevice.FAILED      -> "FAILED"
                android.net.wifi.p2p.WifiP2pDevice.UNAVAILABLE -> "UNAVAILABLE"
                else -> "UNKNOWN(${device.status})"
            }
            Log.i("MeshCall", "CONNECT_BUTTON_CLICKED: name=${device.deviceName} addr=${device.deviceAddress} status=$statusStr")
            textDiscoveryStatus.text = "Connecting to ${device.deviceName}..."
            Toast.makeText(this, "Connecting to ${device.deviceName}", Toast.LENGTH_SHORT).show()
            meshService?.connect(device)
        }
        recyclerViewPeers.layoutManager = LinearLayoutManager(this)
        recyclerViewPeers.adapter = peerAdapter

        chatAdapter = ChatAdapter(voicePlayer)
        setupChatAdapter(chatAdapter)
        val chatLayoutManager = LinearLayoutManager(this)
        chatLayoutManager.stackFromEnd = true
        recyclerViewChat.layoutManager = chatLayoutManager
        recyclerViewChat.adapter = chatAdapter
        
        if (::recyclerViewCallHistory.isInitialized) {
            callHistoryAdapter = CallHistoryAdapter { item ->
                meshService?.callManager?.placeCall(item.message.conversationId)
            }
            recyclerViewCallHistory.layoutManager = LinearLayoutManager(this)
            recyclerViewCallHistory.adapter = callHistoryAdapter
        }
    }

    private fun setupListeners() {
        cardHomeChat.setOnClickListener {
            updateUIState(AppConnectionState.CONVERSATIONS_LIST)
        }
        
        cardHomeCall.setOnClickListener {
            updateUIState(AppConnectionState.MESH_CALL_PLACEHOLDER)
        }

        fabNewChat.setOnClickListener {
            startDiscoveryFlow()
        }
        
        buttonDiscover.setOnClickListener {
            startDiscoveryFlow()
        }
        
        buttonCallFindPeople.setOnClickListener {
            val state = meshService?.getAppConnectionState(meshService?.peerManager?.getConnectionByUserId(activeConversationId ?: "")?.peerId)
            if (state == AppConnectionState.SESSION_ACTIVE && activeConversationId != null) {
                meshService?.callManager?.placeCall(activeConversationId!!)
            } else {
                startDiscoveryFlow()
            }
        }
        
        buttonCallAccept.setOnClickListener {
            meshService?.callManager?.acceptCall()
        }
        
        buttonCallDecline.setOnClickListener {
            meshService?.callManager?.declineCall()
        }
        
        buttonEndCall.setOnClickListener {
            meshService?.callManager?.endCall()
        }
        
        toggleMute.setOnCheckedChangeListener { _, isChecked ->
            meshService?.setCallMuted(isChecked)
        }
        
        toggleSpeaker.setOnCheckedChangeListener { _, isChecked ->
            meshService?.setCallSpeaker(isChecked)
        }

        buttonSend.setOnClickListener {
            val convId = activeConversationId
            if (convId != null) {
                val state = meshService?.getAppConnectionState(meshService?.peerManager?.getConnectionByUserId(convId)?.peerId)
                val isDirectlyConnected = state == AppConnectionState.SESSION_ACTIVE
                val hasMeshRoute = meshService?.routeManager?.hasRouteTo(convId) == true

                if (isDirectlyConnected || hasMeshRoute) {
                    val messageText = editTextMessage.text.toString().trim()
                    if (messageText.isNotEmpty()) {
                        android.util.Log.i("MeshCall_CrashTrace", "TEXT_SEND_CLICK")
                        try {
                            meshService?.sendMessage(messageText, convId)
                            editTextMessage.text.clear()
                        } catch (e: Exception) {
                            android.util.Log.e("MeshCall_CrashTrace", "FATAL EXCEPTION in MainActivity: ${e.message}\n${android.util.Log.getStackTraceString(e)}")
                            throw e
                        }
                    }
                }
            }
        }
        
        buttonMic.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startRecording()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 999)
                }
                true
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                stopRecordingAndSend()
                true
            } else {
                false
            }
        }
    }
    
    private fun startDiscoveryFlow() {
        android.util.Log.i("MeshCall_UI", "DISCOVERY_BUTTON_CLICKED")
        wifiStateHelper.promptEnableWifi {
            updateUIState(AppConnectionState.DISCOVERY)
            if (hasPermissions()) {
                android.util.Log.i("MeshCall_UI", "DISCOVERY_START")
                textDiscoveryStatus.text = "Discovering peers..."
                meshService?.discoverPeers()
            } else {
                requestPermissions()
            }
        }
    }
    
    private var recordingStartTime = 0L
    
    private fun startRecording() {
        if (isRecording) return
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        voiceRecorder.startRecording()
        
        editTextMessage.visibility = View.GONE
        buttonSend.visibility = View.GONE
        textRecordingStatus.visibility = View.VISIBLE
    }
    
    private fun stopRecordingAndSend() {
        if (!isRecording) return
        isRecording = false
        
        val duration = System.currentTimeMillis() - recordingStartTime
        
        editTextMessage.visibility = View.VISIBLE
        buttonSend.visibility = View.VISIBLE
        textRecordingStatus.visibility = View.GONE
        
        if (duration < 500) {
            // Tap was too quick, cancel the recording
            Log.i("MeshCall_CRASH", "VOICE_RECORD_CANCEL: Duration ($duration ms) < 500ms")
            voiceRecorder.cancelRecording()
            return
        }
        
        val recordedFile = voiceRecorder.stopRecording()
        
        if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
            Log.i("MeshCall_CRASH", "VOICE_RECORD_SEND: ${recordedFile.absolutePath}")
            val bytes = recordedFile.readBytes()
            meshService?.sendVoiceMessage(bytes, recordedFile.absolutePath, duration, activeConversationId)
            
            // After successfully sending/grabbing the bytes, we MUST clear the state
            voiceRecorder.clearCurrentFile()
        } else {
            Log.w("MeshCall_CRASH", "VOICE_RECORD_FILE: file was null, empty, or missing.")
            voiceRecorder.clearCurrentFile()
        }
    }

    private fun loadConversations() {
        Log.i("MeshCall_Nav", "CONVERSATIONS_LOADED")
        val dbHelper = meshService?.dbHelper ?: return
        val allConvs = dbHelper.getAllConversations()
        
        // Filter out legacy conversations (e.g. "MC-1234") to prevent duplicate chatboxes
        val convs = allConvs.filter { it.peerUserId.length > 20 }
        
        for (conv in convs) {
            if (meshService?.peerManager?.getConnectionByUserId(conv.peerUserId) != null) {
                conv.isConnected = true
            }
        }
        
        conversationAdapter.setConversations(convs)
        
        if (convs.isEmpty()) {
            textEmptyConversations.visibility = View.VISIBLE
            recyclerViewConversations.visibility = View.GONE
        } else {
            textEmptyConversations.visibility = View.GONE
            recyclerViewConversations.visibility = View.VISIBLE
        }
    }

    private fun openChat(peerUserId: String, displayName: String, avatarUri: String) {
        Log.i("MeshCall_CrashTrace", "CHAT_CLICK_1")
        Log.i("MeshCall_Diag", "M2D10_OPEN_CHAT: peerUserId=$peerUserId, displayName=$displayName")
        Log.i("MeshCall_CrashTrace", "CHAT_STATE_BEFORE")
        activeConversationId = peerUserId
        meshService?.updateActiveConversationId(peerUserId)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(peerUserId.hashCode())
        
        val textDisplayName = findViewById<TextView>(R.id.textChatDisplayName)
        val imageAvatar = findViewById<ImageView>(R.id.imageChatAvatar)
        textDisplayName.text = displayName
        imageAvatar.loadAvatar(avatarUri)
        appBarLayout.visibility = View.GONE
        
        val isConnected = meshService?.peerManager?.getConnectionByUserId(peerUserId) != null
        findViewById<TextView>(R.id.textChatStatus).text = if (isConnected) "Connected" else "Disconnected"
        
        Log.i("MeshCall_CrashTrace", "CHAT_STATE_AFTER")
        
        val openProfileListener = View.OnClickListener {
            val intent = Intent(this, RemoteProfileActivity::class.java).apply {
                putExtra("peerUserId", peerUserId)
                val connection = meshService?.peerManager?.getConnectionByUserId(peerUserId) ?: meshService?.peerManager?.getConnection(peerUserId)
                val about = connection?.profile?.about
                if (!about.isNullOrEmpty()) {
                    putExtra("about", about)
                }
            }
            startActivity(intent)
        }
        imageAvatar.setOnClickListener(openProfileListener)
        textDisplayName.setOnClickListener(openProfileListener)
        
        val dbHelper = meshService?.dbHelper
        if (dbHelper != null) {
            dbHelper.clearUnread(peerUserId)
            Log.i("MeshCall_CrashTrace", "CHAT_DB_START")
            val msgs = dbHelper.getMessagesForConversation(peerUserId)
            Log.i("MeshCall_CrashTrace", "CHAT_DB_END")
            
            Log.i("MeshCall_CrashTrace", "CHAT_ADAPTER_START")
            chatAdapter = ChatAdapter(voicePlayer)
            setupChatAdapter(chatAdapter)
            Log.i("MeshCall_CrashTrace", "CHAT_ADAPTER_END")
            
            Log.i("MeshCall_CrashTrace", "CHAT_RECYCLER_START")
            recyclerViewChat.adapter = chatAdapter
            msgs.forEach { chatAdapter.addMessage(it) }
            recyclerViewChat.scrollToPosition(chatAdapter.itemCount - 1)
            Log.i("MeshCall_CrashTrace", "CHAT_RECYCLER_END")
        }
        
        conversationsContainer.visibility = View.GONE
        discoveryContainer.visibility = View.GONE
        connectionRequestContainer.visibility = View.GONE
        connectedMenuContainer.visibility = View.GONE
        chatContainer.visibility = View.VISIBLE
        Log.i("MeshCall_CrashTrace", "CHAT_SCREEN_SHOWN")
    }

    // onBackPressed() logic has been moved to OnBackPressedDispatcher in onCreate()

    private fun updateUIState(state: AppConnectionState) {
        conversationsContainer.visibility = View.GONE
        discoveryContainer.visibility = View.GONE
        connectionRequestContainer.visibility = View.GONE
        connectedMenuContainer.visibility = View.GONE
        chatContainer.visibility = View.GONE
        homeDashboardContainer.visibility = View.GONE
        meshCallPlaceholderContainer.visibility = View.GONE
        appBarLayout.visibility = View.VISIBLE
        
        when (state) {
            AppConnectionState.HOME_DASHBOARD -> {
                android.util.Log.i("MeshCall_UI", "HOME_SCREEN_SHOWN")
                homeDashboardContainer.visibility = View.VISIBLE
                toolbar.subtitle = ""
            }
            AppConnectionState.CONVERSATIONS_LIST -> {
                conversationsContainer.visibility = View.VISIBLE
                toolbar.subtitle = "Chats"
            }
            AppConnectionState.MESH_CALL_PLACEHOLDER -> {
                meshCallPlaceholderContainer.visibility = View.VISIBLE
                
                if (::callHistoryAdapter.isInitialized) {
                    val dbHelper = meshService?.dbHelper
                    if (dbHelper != null) {
                        val calls = dbHelper.getAllCalls()
                        val conversations = dbHelper.getAllConversations().associateBy { it.peerUserId }
                        val historyItems = calls.mapNotNull { callMsg ->
                            val conv = conversations[callMsg.conversationId]
                            if (conv != null) {
                                CallHistoryItem(callMsg, conv.peerDisplayName.ifEmpty { conv.peerUsername }, conv.peerAvatarUri)
                            } else {
                                null
                            }
                        }
                        callHistoryAdapter.setItems(historyItems)
                        
                        if (historyItems.isEmpty()) {
                            layoutEmptyCallHistory.visibility = View.VISIBLE
                            recyclerViewCallHistory.visibility = View.GONE
                        } else {
                            layoutEmptyCallHistory.visibility = View.GONE
                            recyclerViewCallHistory.visibility = View.VISIBLE
                        }
                    }
                }
                
                val activeProfile = activeConversationId?.let { meshService?.peerManager?.getConnection(it)?.profile }
                val state = meshService?.getAppConnectionState(meshService?.peerManager?.getConnectionByUserId(activeConversationId ?: "")?.peerId)
                if (state == AppConnectionState.SESSION_ACTIVE || activeProfile != null) {
                    toolbar.subtitle = "Call @${activeProfile?.username ?: "User"}"
                    buttonCallFindPeople.text = "Start Call"
                } else {
                    toolbar.subtitle = "Mesh Call"
                    buttonCallFindPeople.text = "Find Nearby People"
                }
            }
            AppConnectionState.DISCOVERY -> {
                android.util.Log.i("MeshCall_UI", "DISCOVERY_STATE_ENTER")
                discoveryContainer.visibility = View.VISIBLE
                toolbar.subtitle = "Nearby People"
            }
            AppConnectionState.DISCONNECTED -> {
                if (activeConversationId != null) {
                    chatContainer.visibility = View.VISIBLE
                    appBarLayout.visibility = View.GONE
                    val isConnected = activeConversationId?.let { meshService?.peerManager?.getConnectionByUserId(it) != null } == true
                    findViewById<TextView>(R.id.textChatStatus).text = if (isConnected) "Connected" else "Disconnected"
                } else {
                    android.util.Log.i("MeshCall_UI", "HOME_SCREEN_SHOWN")
                    homeDashboardContainer.visibility = View.VISIBLE
                    toolbar.subtitle = ""
                }
            }
            AppConnectionState.TRANSPORT_CONNECTED -> {
                // If we are quietly connected in background, don't force discovery open unless we were already there.
                // We'll just show the Home Dashboard if no active chat.
                if (activeConversationId != null) {
                    chatContainer.visibility = View.VISIBLE
                    appBarLayout.visibility = View.GONE
                    val isConnected = activeConversationId?.let { meshService?.peerManager?.getConnectionByUserId(it) != null } == true
                    findViewById<TextView>(R.id.textChatStatus).text = if (isConnected) "Connected" else "Disconnected"
                } else {
                    homeDashboardContainer.visibility = View.VISIBLE
                }
            }
            AppConnectionState.REQUEST_SENT -> {
                discoveryContainer.visibility = View.VISIBLE
                textDiscoveryStatus.text = "Waiting for ${meshService?.currentDevice?.deviceName} to accept..."
            }
            AppConnectionState.REQUEST_RECEIVED -> {
                connectionRequestContainer.visibility = View.VISIBLE
            }
            AppConnectionState.SESSION_ACTIVE -> {
                android.util.Log.i("MeshCall_Diag", "M2D9_UI_STATE: SESSION_ACTIVE. activeConversationId=$activeConversationId")
                val connection = activeConversationId?.let { meshService?.peerManager?.getConnectionByUserId(it) } ?: activeConversationId?.let { meshService?.peerManager?.getConnection(it) }
                val profile = connection?.profile
                android.util.Log.i("MeshCall_Diag", "M2D9_UI_STATE: Resolved profile=${profile?.userId}, username=${profile?.username}")
                if (profile != null) {
                    android.util.Log.i("MeshCall_Diag", "M2D9_UI_STATE: SESSION_ACTIVE. Showing chatbox for ${profile.userId}")
                    conversationsContainer.visibility = View.GONE
                    openChat(profile.userId, profile.displayName.ifEmpty { profile.username }, profile.avatarUri)
                    val isConnected = activeConversationId?.let { meshService?.peerManager?.getConnectionByUserId(it) != null } == true
                    findViewById<TextView>(R.id.textChatStatus).text = if (isConnected) "Connected" else "Disconnected"
                } else {
                    android.util.Log.i("MeshCall_Diag", "M2D9_UI_STATE: profile is null, showing conversations list")
                    conversationsContainer.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showAttachmentPreviewDialog(uri: android.net.Uri) {
        var fileName = "attachment"
        var fileSize = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIndex != -1 && !cursor.isNull(nameIndex)) fileName = cursor.getString(nameIndex)
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) fileSize = cursor.getLong(sizeIndex)
            }
        }
        val sizeKb = fileSize / 1024
        val sizeStr = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Send Attachment")
            .setMessage("File: $fileName\nSize: $sizeStr")
            .setPositiveButton("Send") { _, _ ->
                meshService?.sendAttachment(uri, activeConversationId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hasPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startMeshService()
                val intent = Intent(this, MeshService::class.java)
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            } else {
                Toast.makeText(this, "Permissions required for MeshCall", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == LocationHelper.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchAndSendLocation(LocationHelper(this))
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchAndSendLocation(locationHelper: LocationHelper) {
        Toast.makeText(this, "Acquiring location...", Toast.LENGTH_SHORT).show()
        locationHelper.getCurrentLocation(object : LocationHelper.LocationCallback {
            override fun onLocationResult(location: android.location.Location?) {
                if (location != null && activeConversationId != null) {
                    val payload = LocationMessageUtils.createLocationPayload(location.latitude, location.longitude)
                    try {
                        meshService?.sendMessage(payload, activeConversationId)
                        runOnUiThread { Toast.makeText(this@MainActivity, "Location sent", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                        runOnUiThread { Toast.makeText(this@MainActivity, "Failed to send location", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            override fun onError(error: String) {
                runOnUiThread { Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show() }
            }
        })
    }

    // --- MeshServiceObserver ---

    override fun onPeersChanged(peers: Collection<WifiP2pDevice>) {
        peerAdapter.setPeers(peers)
        
        if (peers.isEmpty()) {
            textDiscoveryStatus.text = "No peers found"
        } else {
            textDiscoveryStatus.text = "Found ${peers.size} peers"
        }
    }

    override fun onConnectionStateChanged(isConnected: Boolean, device: WifiP2pDevice?, role: String?) {
        if (isConnected) {
            // Wait for AppConnectionState.TRANSPORT_CONNECTED to drive the UI/send request.
        } else {
            runOnUiThread {
                loadConversations()
                if (activeConversationId != null) {
                    toolbar.subtitle = "Disconnected"
                } else {
                    updateUIState(AppConnectionState.HOME_DASHBOARD)
                }
            }
        }
    }

    override fun onAppConnectionStateChanged(peerId: String?, state: AppConnectionState, remoteProfile: UserProfile?) {
        runOnUiThread {
            android.util.Log.i("MeshCall_Diag", "M2D9_STATE_CHANGED_EVENT: peerId=$peerId, state=$state, remoteProfile.userId=${remoteProfile?.userId}")
            
            if (state == AppConnectionState.SESSION_ACTIVE && remoteProfile != null) {
                android.util.Log.i("MeshCall_Diag", "M2D10_SESSION_ACTIVE_PROFILE: userId=${remoteProfile.userId}, username=${remoteProfile.username}")
                activeConversationId = remoteProfile.userId
                android.util.Log.i("MeshCall_Diag", "M2D10_ACTIVE_CONVERSATION: set to $activeConversationId")
            }
            
            if (state == AppConnectionState.REQUEST_RECEIVED && remoteProfile != null) {
                if (activeConversationId == null || activeConversationId == remoteProfile.userId) {
                    updateUIState(state)
                    findViewById<TextView>(R.id.requestDisplayName).text = remoteProfile.displayName.ifEmpty { remoteProfile.username }
                    findViewById<TextView>(R.id.requestUsername).text = "@${remoteProfile.username}"
                    findViewById<TextView>(R.id.requestAbout).text = remoteProfile.about
                    
                    findViewById<Button>(R.id.buttonAcceptRequest).setOnClickListener {
                        meshService?.acceptConnectionRequest(remoteProfile.userId)
                    }
                    findViewById<Button>(R.id.buttonDeclineRequest).setOnClickListener {
                        meshService?.declineConnectionRequest(remoteProfile.userId)
                    }
                } else {
                    Toast.makeText(this@MainActivity, "${remoteProfile.username} wants to connect", Toast.LENGTH_LONG).show()
                }
            } else {
                // If it's the active conversation or there is no active conversation, update the UI
                if (activeConversationId == null || activeConversationId == remoteProfile?.userId) {
                    updateUIState(state)
                }
            }
        }
    }


    override fun onMessageReceived(msg: ChatMessage) {
        runOnUiThread {
            // Only reload conversation list if it's not an intermediate file transfer progress update
            val isProgressUpdate = msg.progress in 1..99 && msg.state != MessageState.DELIVERED && msg.state != MessageState.SEEN
            if (!isProgressUpdate) {
                loadConversations() // Update last message in the list
            }
            
            if (activeConversationId == msg.conversationId) {
                val existingIndex = chatAdapter.getMessages().indexOfFirst { it.id == msg.id }
                if (existingIndex != -1) {
                    chatAdapter.updateMessage(existingIndex, msg)
                } else {
                    chatAdapter.addMessage(msg)
                    recyclerViewChat.scrollToPosition(chatAdapter.itemCount - 1)
                }
                meshService?.markAsSeen(msg.id)
            }
        }
    }

    override fun onMessageDeleted(messageId: String) {
        runOnUiThread {
            chatAdapter.removeMessage(messageId)
            loadConversations()
        }
    }

    override fun onCallStateChanged(state: CallState, remoteProfile: UserProfile?, isCaller: Boolean) {
        runOnUiThread {
            when (state) {
                CallState.IDLE -> {
                    callIncomingContainer.visibility = View.GONE
                    callActiveContainer.visibility = View.GONE
                    stopCallDurationTimer()
                }
                CallState.OUTGOING_CALL -> {
                    callIncomingContainer.visibility = View.GONE
                    callActiveContainer.visibility = View.VISIBLE
                    textActiveCallName.text = remoteProfile?.displayName?.ifEmpty { remoteProfile.username } ?: "Unknown"
                    textActiveCallStatus.text = "● Calling..."
                    textActiveCallStatus.setTextColor(android.graphics.Color.parseColor("#BBBBBB"))
                    textActiveCallDuration.text = ""
                    toggleMute.isChecked = false
                    toggleSpeaker.isChecked = false
                    stopCallDurationTimer()
                }
                CallState.INCOMING_CALL -> {
                    callActiveContainer.visibility = View.GONE
                    callIncomingContainer.visibility = View.VISIBLE
                    textIncomingCallName.text = remoteProfile?.displayName?.ifEmpty { remoteProfile.username } ?: "Unknown"
                    textIncomingCallUsername.text = "@${remoteProfile?.username ?: "unknown"}"
                }
                CallState.CONNECTED -> {
                    callIncomingContainer.visibility = View.GONE
                    callActiveContainer.visibility = View.VISIBLE
                    textActiveCallName.text = remoteProfile?.displayName?.ifEmpty { remoteProfile.username } ?: "Unknown"
                    textActiveCallStatus.text = "● Connected"
                    textActiveCallStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    
                    if (isCaller) {
                        // Keep the toggles as they were set during outgoing call
                    } else {
                        // Reset for incoming accepted call
                        toggleMute.isChecked = false
                        toggleSpeaker.isChecked = false
                    }
                    
                    startCallDurationTimer()
                }
                CallState.ENDING -> {
                    textActiveCallStatus.text = "● Ending Call..."
                    textActiveCallStatus.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
                    stopCallDurationTimer()
                }
            }
        }
    }

    private val callDurationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private fun startCallDurationTimer() {
        callDurationSeconds = 0
        callDurationRunnable = object : Runnable {
            override fun run() {
                val mins = callDurationSeconds / 60
                val secs = callDurationSeconds % 60
                textActiveCallDuration.text = String.format("%02d:%02d", mins, secs)
                callDurationSeconds++
                callDurationHandler.postDelayed(this, 1000)
            }
        }
        callDurationRunnable?.run()
    }

    private fun stopCallDurationTimer() {
        callDurationRunnable?.let { callDurationHandler.removeCallbacks(it) }
        callDurationRunnable = null
    }

    override fun onWifiDirectEnabled(isEnabled: Boolean) {
        if (!isEnabled) {
            toolbar.subtitle = "Wi-Fi Direct Disabled"
            textDiscoveryStatus.text = "Wi-Fi Direct is disabled"
        } else if (meshService?.isConnected != true) {
            toolbar.subtitle = "Disconnected"
        }
    }

    private fun setupChatAdapter(adapter: ChatAdapter) {
        adapter.isPeerConnected = meshService?.isConnected == true
        adapter.onReactionSelected = { messageId, reaction ->
            meshService?.sendReaction(messageId, reaction, activeConversationId)
        }
        adapter.onMessageAction = { action, msg, _ ->
            when (action) {
                "INFO" -> showMessageInfo(msg)
                "DELETE_FOR_ME" -> {
                    meshService?.deleteMessageLocalOnly(msg.id)
                    adapter.removeMessage(msg.id)
                }
                "DELETE_FOR_EVERYONE" -> {
                    meshService?.sendMessageDelete(msg.id, activeConversationId)
                    adapter.removeMessage(msg.id)
                }
            }
        }
    }

    private fun showMessageInfo(msg: ChatMessage) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_message_info, null)
        bottomSheet.setContentView(view)

        val formatTime = { timeMs: Long ->
            if (timeMs > 0) java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(timeMs))
            else "-"
        }

        view.findViewById<TextView>(R.id.infoMessagePreview).text = when (msg.type) {
            MessageType.TEXT -> msg.text
            MessageType.IMAGE -> "📷 Photo"
            MessageType.FILE -> "📄 Document"
            MessageType.VOICE -> "🎤 Voice message"
            MessageType.CALL -> "📞 Call"
        }

        view.findViewById<TextView>(R.id.infoSentTime).text = formatTime(msg.timestamp)
        view.findViewById<TextView>(R.id.infoDeliveredTime).text = formatTime(msg.deliveredAt)
        view.findViewById<TextView>(R.id.infoReadTime).text = formatTime(msg.seenAt)

        (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        bottomSheet.show()
    }
}
