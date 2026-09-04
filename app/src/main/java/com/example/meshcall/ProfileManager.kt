package com.example.meshcall

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import org.json.JSONObject

data class UserProfile(
    val userId: String,
    val username: String,
    val displayName: String,
    val about: String,
    val avatarUri: String
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("userId", userId)
        json.put("username", username)
        json.put("displayName", displayName)
        json.put("about", about)
        json.put("avatarUri", avatarUri)
        return json.toString()
    }
    
    companion object {
        fun fromJson(jsonStr: String): UserProfile? {
            return try {
                val json = JSONObject(jsonStr)
                UserProfile(
                    userId = json.optString("userId", ""),
                    username = json.optString("username", ""),
                    displayName = json.optString("displayName", ""),
                    about = json.optString("about", ""),
                    avatarUri = json.optString("avatarUri", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

class ProfileManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("meshcall_profile", Context.MODE_PRIVATE)

    fun getProfile(): UserProfile? {
        val userId = prefs.getString("userId", null)
        val username = prefs.getString("username", null)
        
        if (userId == null || username == null) {
            return null
        }
        
        return UserProfile(
            userId = userId,
            username = username,
            displayName = prefs.getString("displayName", "") ?: "",
            about = prefs.getString("about", "") ?: "",
            avatarUri = prefs.getString("avatarUri", "") ?: ""
        )
    }

    fun saveProfile(username: String, displayName: String, about: String, avatarUri: String) {
        val currentUserId = prefs.getString("userId", null) ?: UUID.randomUUID().toString()
        
        prefs.edit().apply {
            putString("userId", currentUserId)
            putString("username", username)
            putString("displayName", displayName)
            putString("about", about)
            putString("avatarUri", avatarUri)
            apply()
        }
    }
    
    fun clearProfile() {
        prefs.edit().clear().apply()
    }
}
