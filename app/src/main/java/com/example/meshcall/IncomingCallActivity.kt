package com.example.meshcall

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class IncomingCallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Wake the screen and show over lock screen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_incoming_call)

        val callerName = intent.getStringExtra("EXTRA_CALLER_NAME") ?: "Unknown Caller"
        val callerUsername = intent.getStringExtra("EXTRA_CALLER_USERNAME") ?: "unknown"
        val callerAvatarUri = intent.getStringExtra("EXTRA_CALLER_AVATAR")

        val textCallerName = findViewById<TextView>(R.id.textCallerName)
        val textCallerUsername = findViewById<TextView>(R.id.textCallerUsername)
        val imageCallerAvatar = findViewById<ImageView>(R.id.imageCallerAvatar)

        textCallerName.text = callerName
        textCallerUsername.text = "@$callerUsername"

        if (!callerAvatarUri.isNullOrEmpty()) {
            imageCallerAvatar.loadAvatar(callerAvatarUri)
        } else {
            imageCallerAvatar.setImageResource(R.drawable.ic_baseline_person_24)
        }

        findViewById<FloatingActionButton>(R.id.buttonAccept).setOnClickListener {
            // Trigger the same flow as the notification Accept button
            val acceptIntent = Intent(this, MeshService::class.java).apply {
                action = MeshService.ACTION_ACCEPT_CALL
            }
            startService(acceptIntent)
            
            // Replicate the behavior of the notification action by opening MainActivity
            val openIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MeshService.EXTRA_OPEN_ACTIVE_CALL, true)
            }
            startActivity(openIntent)
            
            finish()
        }

        findViewById<FloatingActionButton>(R.id.buttonDecline).setOnClickListener {
            // Trigger the same flow as the notification Decline button
            val declineIntent = Intent(this, MeshService::class.java).apply {
                action = MeshService.ACTION_DECLINE_CALL
            }
            startService(declineIntent)
            finish()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing to prevent dismissing the incoming call screen accidentally.
        // The user must explicitly accept or decline.
    }
}
