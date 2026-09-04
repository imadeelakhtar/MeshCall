package com.example.meshcall

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import java.util.Locale

/**
 * MESH NETWORK — PHASE M1: DEVICE IDENTITY ONLY
 * Responsible for generating and persisting a unique Device ID for the mesh network.
 */
class MeshIdentityManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("meshcall_identity", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        
        @Volatile
        private var instance: MeshIdentityManager? = null
        
        fun getInstance(context: Context): MeshIdentityManager {
            return instance ?: synchronized(this) {
                instance ?: MeshIdentityManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Returns the persistent unique Device ID for this installation.
     * If one does not exist, it generates and saves a new one.
     */
    fun getDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = generateDeviceId()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }
    
    private fun generateDeviceId(): String {
        // Generate an ID like "MC-7F21A9C4"
        val randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase(Locale.ROOT)
        return "MC-$randomPart"
    }
}
