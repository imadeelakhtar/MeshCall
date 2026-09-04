package com.example.meshcall

import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class ImageViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val toolbar = findViewById<Toolbar>(R.id.toolbarImageViewer)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        val title = intent.getStringExtra("title") ?: "Profile Photo"
        supportActionBar?.title = title

        val avatarUri = intent.getStringExtra("avatarUri")
        val imageViewerLarge = findViewById<ImageView>(R.id.imageViewerLarge)
        
        if (!avatarUri.isNullOrEmpty()) {
            imageViewerLarge.loadAvatar(avatarUri)
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
