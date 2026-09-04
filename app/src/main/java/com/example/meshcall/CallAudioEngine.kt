package com.example.meshcall

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CallAudioEngine(private val context: Context) {
    private val TAG = "CallAudioEngine"

    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var isRunning = false
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    private val scope = CoroutineScope(Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var isMuted = false
        private set

    var isSpeakerOn = false
        private set

    // Callback for network streaming (to be implemented in Phase 5C)
    var onAudioFrameCaptured: ((ByteArray) -> Unit)? = null

    fun start() {
        if (isRunning) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted. Cannot start CallAudioEngine.")
            return
        }

        val minRecordBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
        if (minRecordBufferSize == AudioRecord.ERROR_BAD_VALUE || minRecordBufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size for AudioRecord")
            return
        }

        val minTrackBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
        if (minTrackBufferSize == AudioTrack.ERROR_BAD_VALUE || minTrackBufferSize == AudioTrack.ERROR) {
            Log.e(TAG, "Invalid buffer size for AudioTrack")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                minRecordBufferSize * 2
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(minTrackBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Audio components failed to initialize.")
                stop()
                return
            }

            previousAudioMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            isRunning = true

            // Start capture thread (Phase 5B placeholder, networking added in 5C)
            captureJob = scope.launch {
                audioRecord?.startRecording()
                Log.i(TAG, "AudioRecord started")
                val buffer = ByteArray(minRecordBufferSize)
                while (isActive && isRunning) {
                    if (isMuted) {
                        kotlinx.coroutines.delay(10) // Small delay to avoid busy waiting while muted
                        continue
                    }
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readResult > 0) {
                        // In 5C, we will send this over the network
                        onAudioFrameCaptured?.invoke(buffer.copyOf(readResult))
                    }
                }
            }

            // Start playback thread (Phase 5B placeholder, playing silence for now)
            playbackJob = scope.launch {
                audioTrack?.play()
                Log.i(TAG, "AudioTrack started")
                // Loop is active, ready to receive incoming bytes in 5C
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException initializing audio: ${e.message}", e)
            stop()
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing audio: ${e.message}", e)
            stop()
        }
    }

    // Called when remote audio frame is received (Phase 5C)
    fun playRemoteAudio(audioData: ByteArray) {
        if (isRunning && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack?.write(audioData, 0, audioData.size)
        }
    }

    fun setMute(muted: Boolean) {
        isMuted = muted
    }

    fun setSpeaker(speakerOn: Boolean) {
        isSpeakerOn = speakerOn
        audioManager.isSpeakerphoneOn = speakerOn
    }

    fun stop() {
        isRunning = false
        
        audioManager.mode = previousAudioMode
        
        captureJob?.cancel()
        captureJob = null
        
        playbackJob?.cancel()
        playbackJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }

        try {
            audioTrack?.stop()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
        }
        
        // Reset speaker state on stop
        setSpeaker(false)
        
        Log.i(TAG, "CallAudioEngine stopped and resources released")
    }
}
