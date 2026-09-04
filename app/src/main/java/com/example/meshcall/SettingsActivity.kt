package com.example.meshcall

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class SettingsActivity : AppCompatActivity() {
    private lateinit var profileManager: ProfileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbarSettings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        profileManager = ProfileManager(this)
        
        val buttonEditProfile = findViewById<Button>(R.id.buttonEditProfile)
        val launchEditProfile = android.view.View.OnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        buttonEditProfile.setOnClickListener(launchEditProfile)
        findViewById<android.widget.ImageView>(R.id.imageSettingsAvatar).setOnClickListener(launchEditProfile)
        findViewById<android.widget.ImageView>(R.id.imageSettingsEditPencil).setOnClickListener(launchEditProfile)
    }
    
    override fun onResume() {
        super.onResume()
        val profile = profileManager.getProfile()
        if (profile != null) {
            findViewById<TextView>(R.id.textSettingsDisplayName).text = profile.displayName
            findViewById<TextView>(R.id.textSettingsUsername).text = "@${profile.username}"
            findViewById<android.widget.ImageView>(R.id.imageSettingsAvatar).loadAvatar(profile.avatarUri)
        }
    }
}
