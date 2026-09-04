package com.example.meshcall

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class RemoteProfileActivity : AppCompatActivity() {
    private var peerUserId: String? = null
    private var avatarUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_profile)

        val toolbar = findViewById<Toolbar>(R.id.toolbarProfile)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Profile"

        peerUserId = intent.getStringExtra("peerUserId")
        if (peerUserId == null) {
            finish()
            return
        }

        val textDisplayName = findViewById<TextView>(R.id.textProfileDisplayName)
        val textUsername = findViewById<TextView>(R.id.textProfileUsername)
        val textAbout = findViewById<TextView>(R.id.textProfileAbout)
        val imageProfileLarge = findViewById<ImageView>(R.id.imageProfileLarge)

        // Find the profile from the database
        val dbHelper = DatabaseHelper(this)
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT ${DatabaseHelper.COL_CONV_DISPLAY_NAME}, ${DatabaseHelper.COL_CONV_USERNAME}, ${DatabaseHelper.COL_CONV_AVATAR_URI} FROM ${DatabaseHelper.TABLE_CONVERSATIONS} WHERE ${DatabaseHelper.COL_CONV_ID} = ?",
            arrayOf(peerUserId)
        )

        var displayName = ""
        var username = ""

        if (cursor.moveToFirst()) {
            displayName = cursor.getString(0)
            username = cursor.getString(1)
            avatarUri = cursor.getString(2)
        }
        cursor.close()

        textDisplayName.text = displayName
        textUsername.text = "@$username"
        imageProfileLarge.loadAvatar(avatarUri)

        val aboutExtra = intent.getStringExtra("about")
        if (!aboutExtra.isNullOrEmpty()) {
            textAbout.text = aboutExtra
        } else {
            textAbout.text = "Hey there! I am using MeshCall."
        }

        imageProfileLarge.setOnClickListener {
            if (!avatarUri.isNullOrEmpty()) {
                val intent = Intent(this, ImageViewerActivity::class.java).apply {
                    putExtra("avatarUri", avatarUri)
                    putExtra("title", displayName)
                }
                startActivity(intent)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
