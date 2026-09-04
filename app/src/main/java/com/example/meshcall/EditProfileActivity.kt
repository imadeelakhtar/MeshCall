package com.example.meshcall

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import androidx.appcompat.widget.Toolbar

class EditProfileActivity : AppCompatActivity() {
    private lateinit var profileManager: ProfileManager
    private var currentAvatarUri: String = ""

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uri = result.uriContent
            if (uri != null) {
                val base64 = AvatarHelper.encodeImageToBase64(this, uri)
                if (base64 != null) {
                    currentAvatarUri = base64
                    findViewById<ImageView>(R.id.imageEditAvatar).loadAvatar(currentAvatarUri)
                } else {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Cropping failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        val toolbar = findViewById<Toolbar>(R.id.toolbarEditProfile)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        profileManager = ProfileManager(this)
        val profile = profileManager.getProfile()

        val editUsername = findViewById<EditText>(R.id.editUsername)
        val editDisplayName = findViewById<EditText>(R.id.editDisplayName)
        val editAbout = findViewById<EditText>(R.id.editAbout)
        val buttonSave = findViewById<Button>(R.id.buttonSaveProfile)
        val frameEditProfilePic = findViewById<FrameLayout>(R.id.frameEditProfilePic)
        val imageEditAvatar = findViewById<ImageView>(R.id.imageEditAvatar)

        if (profile != null) {
            editUsername.setText(profile.username)
            editDisplayName.setText(profile.displayName)
            editAbout.setText(profile.about)
            currentAvatarUri = profile.avatarUri
            imageEditAvatar.loadAvatar(currentAvatarUri)
        }

        val textChangePhoto = findViewById<android.widget.TextView>(R.id.textChangePhoto)
        
        val launchCrop = android.view.View.OnClickListener {
            cropImage.launch(
                CropImageContractOptions(
                    uri = null,
                    cropImageOptions = CropImageOptions(
                        imageSourceIncludeGallery = true,
                        imageSourceIncludeCamera = true,
                        fixAspectRatio = true,
                        aspectRatioX = 1,
                        aspectRatioY = 1,
                        activityTitle = "Crop Profile Photo",
                        cropMenuCropButtonTitle = "Done"
                    )
                )
            )
        }
        frameEditProfilePic.setOnClickListener(launchCrop)
        textChangePhoto?.setOnClickListener(launchCrop)

        buttonSave.setOnClickListener {
            val username = editUsername.text.toString().trim()
            val displayName = editDisplayName.text.toString().trim()
            val about = editAbout.text.toString().trim()

            if (username.isEmpty() || !username.matches(Regex("^[a-zA-Z0-9_]{3,20}\$"))) {
                Toast.makeText(this, "Invalid username (3-20 chars, letters/numbers/_)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            profileManager.saveProfile(username, displayName, about, currentAvatarUri)
            Toast.makeText(this, "Profile Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
