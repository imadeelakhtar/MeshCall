package com.example.meshcall

import android.media.MediaPlayer
import android.util.Log
import java.io.IOException

class VoicePlayer {
    private var mediaPlayer: MediaPlayer? = null
    var currentPlayingPath: String? = null
        private set
    private val TAG = "VoicePlayer"
    var isPaused: Boolean = false
        private set

    fun play(audioPath: String, onCompletion: () -> Unit) {
        if (currentPlayingPath == audioPath) {
            if (isPaused) {
                mediaPlayer?.start()
                isPaused = false
                return
            } else if (mediaPlayer?.isPlaying == true) {
                return
            }
        }
        
        stop()

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(audioPath)
                prepare()
                start()
                currentPlayingPath = audioPath
                isPaused = false
                
                setOnCompletionListener {
                    currentPlayingPath = null
                    isPaused = false
                    onCompletion()
                }
            } catch (e: IOException) {
                Log.e(TAG, "prepare() failed for path: $audioPath", e)
            }
        }
    }

    fun pause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            isPaused = true
        }
    }

    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying || isPaused) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        currentPlayingPath = null
        isPaused = false
    }

    fun isPlaying(audioPath: String): Boolean {
        return currentPlayingPath == audioPath && (mediaPlayer?.isPlaying == true || isPaused)
    }

    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun release() {
        stop()
    }
}
