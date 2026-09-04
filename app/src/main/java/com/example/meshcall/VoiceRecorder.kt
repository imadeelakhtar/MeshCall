package com.example.meshcall

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private val TAG = "VoiceRecorder"

    fun startRecording(): File? {
        val outputFile = File(context.cacheDir, "voice_record_${System.currentTimeMillis()}.m4a")
        currentOutputFile = outputFile
        Log.i("MeshCall_CRASH", "VOICE_RECORD_START: ${outputFile.absolutePath}")

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)
            
            try {
                prepare()
                start()
                Log.d(TAG, "Recording started: ${outputFile.absolutePath}")
            } catch (e: IOException) {
                Log.e(TAG, "prepare() failed", e)
                return null
            }
        }
        return outputFile
    }

    fun stopRecording(): File? {
        var resultFile: File? = null
        try {
            recorder?.apply {
                stop()
                release()
            }
            Log.d(TAG, "Recording stopped")
            Log.i("MeshCall_CRASH", "VOICE_RECORD_STOP: ${currentOutputFile?.absolutePath}")
            resultFile = currentOutputFile
        } catch (e: RuntimeException) {
            // MediaRecorder throws RuntimeException if stop() is called immediately after start()
            Log.e(TAG, "Failed to stop recording cleanly (likely too short)", e)
            Log.i("MeshCall_CRASH", "VOICE_RECORD_CANCEL (exception): ${currentOutputFile?.absolutePath}")
            currentOutputFile?.delete()
            resultFile = null
        }
        recorder = null
        return resultFile
    }

    fun clearCurrentFile() {
        Log.i("MeshCall_CRASH", "VOICE_RECORD_CLEAR: ${currentOutputFile?.absolutePath}")
        currentOutputFile = null
    }

    fun cancelRecording() {
        Log.i("MeshCall_CRASH", "VOICE_RECORD_CANCEL (manual): ${currentOutputFile?.absolutePath}")
        stopRecording()
        currentOutputFile?.delete()
        clearCurrentFile()
    }
    
    fun release() {
        recorder?.release()
        recorder = null
        clearCurrentFile()
    }
}
