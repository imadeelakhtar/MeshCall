package com.example.meshcall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.io.InputStream

object AvatarHelper {

    private const val MAX_IMAGE_SIZE = 512
    private const val BASE64_PREFIX = "data:image/jpeg;base64,"

    fun encodeImageToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return null

            val ratio = Math.min(
                MAX_IMAGE_SIZE.toFloat() / bitmap.width,
                MAX_IMAGE_SIZE.toFloat() / bitmap.height
            )

            val width = Math.round(ratio * bitmap.width)
            val height = Math.round(ratio * bitmap.height)

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()

            BASE64_PREFIX + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

fun ImageView.loadAvatar(avatarUri: String?) {
    // Save original tint and padding on first call
    if (this.getTag(R.id.tag_avatar_tint) == null) {
        this.setTag(R.id.tag_avatar_tint, this.imageTintList)
        this.setTag(R.id.tag_avatar_padding, this.paddingLeft) // Assuming symmetric padding
    }

    if (avatarUri.isNullOrEmpty()) {
        this.setImageResource(R.drawable.ic_baseline_person_24)
        this.imageTintList = this.getTag(R.id.tag_avatar_tint) as? android.content.res.ColorStateList
        val padding = this.getTag(R.id.tag_avatar_padding) as? Int ?: 0
        this.setPadding(padding, padding, padding, padding)
        return
    }

    if (avatarUri.startsWith("data:image")) {
        try {
            val base64 = avatarUri.substringAfter(",")
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            this.setImageBitmap(bitmap)
            this.imageTintList = null
            this.setPadding(0, 0, 0, 0)
        } catch (e: Exception) {
            e.printStackTrace()
            this.setImageResource(R.drawable.ic_baseline_person_24)
            this.imageTintList = this.getTag(R.id.tag_avatar_tint) as? android.content.res.ColorStateList
            val padding = this.getTag(R.id.tag_avatar_padding) as? Int ?: 0
            this.setPadding(padding, padding, padding, padding)
        }
    } else {
        try {
            this.setImageURI(Uri.parse(avatarUri))
            this.imageTintList = null
            this.setPadding(0, 0, 0, 0)
        } catch (e: Exception) {
            this.setImageResource(R.drawable.ic_baseline_person_24)
            this.imageTintList = this.getTag(R.id.tag_avatar_tint) as? android.content.res.ColorStateList
            val padding = this.getTag(R.id.tag_avatar_padding) as? Int ?: 0
            this.setPadding(padding, padding, padding, padding)
        }
    }
}
