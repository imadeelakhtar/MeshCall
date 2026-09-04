package com.example.meshcall

import android.content.ContentValues
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector
    private val matrix = Matrix()
    private var scaleFactor = 1.0f
    
    private var localFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_viewer)

        val toolbar = findViewById<Toolbar>(R.id.toolbarPhotoViewer)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Photo"

        imageView = findViewById(R.id.imagePhotoViewer)
        localFilePath = intent.getStringExtra("localFilePath")

        if (localFilePath != null) {
            val uri = if (localFilePath!!.startsWith("content://")) {
                Uri.parse(localFilePath)
            } else {
                Uri.fromFile(File(localFilePath!!))
            }
            imageView.setImageURI(uri)
            
            // Adjust matrix to fit center initially
            imageView.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    imageView.viewTreeObserver.removeOnPreDrawListener(this)
                    val drawable = imageView.drawable ?: return true
                    val imageWidth = drawable.intrinsicWidth.toFloat()
                    val imageHeight = drawable.intrinsicHeight.toFloat()
                    val viewWidth = imageView.width.toFloat()
                    val viewHeight = imageView.height.toFloat()
                    val scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight)
                    val dx = (viewWidth - imageWidth * scale) / 2f
                    val dy = (viewHeight - imageHeight * scale) / 2f
                    matrix.setScale(scale, scale)
                    matrix.postTranslate(dx, dy)
                    imageView.imageMatrix = matrix
                    return true
                }
            })
        } else {
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                scaleFactor *= scale
                // Don't let the object get too small or too large
                scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f))
                matrix.postScale(scale, scale, detector.focusX, detector.focusY)
                imageView.imageMatrix = matrix
                return true
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                matrix.postTranslate(-distanceX, -distanceY)
                imageView.imageMatrix = matrix
                return true
            }
        })

        imageView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }

        findViewById<ImageButton>(R.id.buttonSavePhoto).setOnClickListener {
            savePhotoToGallery()
        }
    }

    private fun savePhotoToGallery() {
        if (localFilePath == null) return
        val sourceFile = File(localFilePath!!)
        if (!sourceFile.exists()) {
            Toast.makeText(this, "Original file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "MeshCall_${System.currentTimeMillis()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MeshCall")
                }
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                var outputStream: OutputStream? = null
                var inputStream: FileInputStream? = null
                try {
                    outputStream = contentResolver.openOutputStream(uri)
                    inputStream = FileInputStream(sourceFile)
                    if (outputStream != null) {
                        inputStream.copyTo(outputStream)
                        Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    inputStream?.close()
                    outputStream?.close()
                }
            } else {
                Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
