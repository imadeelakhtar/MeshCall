package com.example.meshcall

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.util.Log

class CallRingtoneManager(private val context: Context) {
    private val TAG = "CallRingtoneManager"
    
    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null
    
    fun startRingtone() {
        stopAll()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
            Log.i(TAG, "Started incoming ringtone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone: ${e.message}")
        }
    }
    
    fun startRingback() {
        stopAll()
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100)
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE)
            Log.i(TAG, "Started outgoing ringback tone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringback tone: ${e.message}")
        }
    }
    
    fun stopAll() {
        try {
            ringtone?.takeIf { it.isPlaying }?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone: ${e.message}")
        }
        
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tone generator: ${e.message}")
        }
        Log.i(TAG, "Stopped all ringing sounds")
    }
}
