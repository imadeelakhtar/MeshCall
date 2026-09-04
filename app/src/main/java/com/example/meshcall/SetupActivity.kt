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

class SetupActivity : AppCompatActivity() {
    private lateinit var profileManager: ProfileManager
    private var currentAvatarUri: String = ""

    private lateinit var permissionManager: PermissionManager

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uri = result.uriContent
            if (uri != null) {
                val base64 = AvatarHelper.encodeImageToBase64(this, uri)
                if (base64 != null) {
                    currentAvatarUri = base64
                    findViewById<ImageView>(R.id.imageProfileAvatar).loadAvatar(currentAvatarUri)
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
        profileManager = ProfileManager(this)

        permissionManager = PermissionManager(this)
        permissionManager.startPermissionFlow {
            if (profileManager.getProfile() != null) {
                startMainActivity()
            } else {
                initSetupUI()
            }
        }
    }

    private fun initSetupUI() {
        setContentView(R.layout.activity_setup)

        val editUsername = findViewById<EditText>(R.id.editUsername)
        val editDisplayName = findViewById<EditText>(R.id.editDisplayName)
        val editAbout = findViewById<EditText>(R.id.editAbout)
        val buttonContinue = findViewById<Button>(R.id.buttonContinue)
        val frameProfilePic = findViewById<FrameLayout>(R.id.frameProfilePic)
        val textAddPhoto = findViewById<android.widget.TextView>(R.id.textAddPhoto)

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
        frameProfilePic.setOnClickListener(launchCrop)
        textAddPhoto.setOnClickListener(launchCrop)

        buttonContinue.setOnClickListener {
            try {
                android.util.Log.i("MeshCall_PROFILE", "PROFILE_CONTINUE_CLICKED")
                val username = editUsername.text.toString().trim()
                val displayName = editDisplayName.text.toString().trim()
                val about = editAbout.text.toString().trim()

                android.util.Log.i("MeshCall_PROFILE", "PROFILE_VALIDATION")
                if (username.isEmpty()) {
                    Toast.makeText(this, "Username is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (!username.matches(Regex("^[a-zA-Z0-9_]{3,20}\$"))) {
                    Toast.makeText(this, "Username must be 3-20 chars (letters, numbers, underscores)", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                android.util.Log.i("MeshCall_PROFILE", "PROFILE_SAVE_START")
                profileManager.saveProfile(username, displayName, about, currentAvatarUri)
                android.util.Log.i("MeshCall_PROFILE", "PROFILE_SAVE_SUCCESS")
                
                android.util.Log.i("MeshCall_PROFILE", "PROFILE_NAVIGATION")
                startMainActivity()
            } catch (e: Exception) {
                android.util.Log.e("MeshCall_PROFILE", "PROFILE_SAVE_FAILURE", e)
                throw e // Don't hide the crash, but ensure we log it!
            }
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
