package com.example.meshcall

import android.os.Handler
import android.os.Looper
import android.util.Log

enum class CallState {
    IDLE,
    OUTGOING_CALL,
    INCOMING_CALL,
    CONNECTED,
    ENDING
}

interface CallManagerObserver {
    fun onCallStateChanged(state: CallState, isCaller: Boolean)
}

class CallManager(
    private val sendControlMessage: (type: Int, targetUserId: String) -> Unit
) {
    private val TAG = "CallManager"
    
    var currentState: CallState = CallState.IDLE
        private set
        
    var isCaller: Boolean = false
        private set

    private var observer: CallManagerObserver? = null
    
    var activeCallUserId: String? = null
        private set
    
    // Timeout handler for outgoing calls
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        if (currentState == CallState.OUTGOING_CALL) {
            Log.i(TAG, "Call request timed out")
            endCall()
        }
    }

    fun setObserver(observer: CallManagerObserver?) {
        this.observer = observer
        observer?.onCallStateChanged(currentState, isCaller)
    }

    private fun setState(newState: CallState) {
        if (currentState != newState) {
            Log.i(TAG, "State transition: $currentState -> $newState")
            currentState = newState
            observer?.onCallStateChanged(currentState, isCaller)
        }
    }

    // --- Outgoing Actions ---

    fun placeCall(targetUserId: String) {
        if (currentState != CallState.IDLE) return
        activeCallUserId = targetUserId
        isCaller = true
        setState(CallState.OUTGOING_CALL)
        sendControlMessage(SocketManager.TYPE_CALL_REQUEST, targetUserId)
        
        // Start 30 second timeout
        mainHandler.postDelayed(timeoutRunnable, 30_000)
    }

    fun acceptCall() {
        if (currentState != CallState.INCOMING_CALL) return
        val targetId = activeCallUserId ?: return
        setState(CallState.CONNECTED)
        sendControlMessage(SocketManager.TYPE_CALL_ACCEPT, targetId)
    }

    fun declineCall() {
        if (currentState != CallState.INCOMING_CALL) return
        val targetId = activeCallUserId ?: return
        sendControlMessage(SocketManager.TYPE_CALL_DECLINE, targetId)
        activeCallUserId = null
        setState(CallState.IDLE)
    }

    fun endCall() {
        if (currentState == CallState.IDLE) return
        mainHandler.removeCallbacks(timeoutRunnable)
        
        // If we are already connected or in the process of calling, tell the other side
        val targetId = activeCallUserId
        if ((currentState == CallState.OUTGOING_CALL || currentState == CallState.CONNECTED) && targetId != null) {
            sendControlMessage(SocketManager.TYPE_CALL_END, targetId)
        }
        
        activeCallUserId = null
        
        setState(CallState.ENDING)
        // Transition back to IDLE after a short delay for UI
        mainHandler.postDelayed({ setState(CallState.IDLE) }, 1000)
    }

    // --- Incoming Network Events ---

    fun onCallRequestReceived(userId: String) {
        if (currentState != CallState.IDLE) {
            // Already in a call or calling someone else
            sendControlMessage(SocketManager.TYPE_CALL_DECLINE, userId)
            return
        }
        activeCallUserId = userId
        isCaller = false
        setState(CallState.INCOMING_CALL)
    }

    fun onCallAcceptReceived(userId: String) {
        if (currentState != CallState.OUTGOING_CALL || activeCallUserId != userId) return
        mainHandler.removeCallbacks(timeoutRunnable)
        setState(CallState.CONNECTED)
    }

    fun onCallDeclineReceived(userId: String) {
        if (currentState != CallState.OUTGOING_CALL || activeCallUserId != userId) return
        mainHandler.removeCallbacks(timeoutRunnable)
        activeCallUserId = null
        setState(CallState.ENDING)
        mainHandler.postDelayed({ setState(CallState.IDLE) }, 1000)
    }

    fun onCallEndReceived(userId: String) {
        if (activeCallUserId != userId) return
        if (currentState != CallState.IDLE && currentState != CallState.ENDING) {
            mainHandler.removeCallbacks(timeoutRunnable)
            activeCallUserId = null
            setState(CallState.ENDING)
            mainHandler.postDelayed({ setState(CallState.IDLE) }, 1000)
        }
    }

    fun onConnectionLost(userId: String) {
        if (activeCallUserId == userId && currentState != CallState.IDLE) {
            Log.i(TAG, "Connection lost during call, transitioning to IDLE")
            mainHandler.removeCallbacks(timeoutRunnable)
            activeCallUserId = null
            setState(CallState.IDLE)
        }
    }
}
