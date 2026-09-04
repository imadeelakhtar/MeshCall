package com.example.meshcall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: ComponentActivity) {

    private val requiredPermissions: List<String>
        get() {
            val perms = mutableListOf<String>()
            perms.add(Manifest.permission.RECORD_AUDIO)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            return perms
        }

    private var onFlowComplete: (() -> Unit)? = null
    private var currentPermissionIndex = 0

    private val requestPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            currentPermissionIndex++
            requestNextPermission()
        } else {
            val currentPerm = requiredPermissions[currentPermissionIndex]
            if (activity.shouldShowRequestPermissionRationale(currentPerm)) {
                showRationaleDialog(currentPerm)
            } else {
                showSettingsDialog(currentPerm)
            }
        }
    }

    fun startPermissionFlow(onComplete: () -> Unit) {
        onFlowComplete = onComplete
        currentPermissionIndex = 0
        requestNextPermission()
    }

    private fun requestNextPermission() {
        while (currentPermissionIndex < requiredPermissions.size) {
            val permission = requiredPermissions[currentPermissionIndex]
            if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
                currentPermissionIndex++
            } else {
                requestPermissionLauncher.launch(permission)
                return
            }
        }
        onFlowComplete?.invoke()
    }

    private fun showRationaleDialog(permission: String) {
        val message = getRationaleMessage(permission)
        AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage(message)
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionLauncher.launch(permission)
            }
            .setNegativeButton("Skip") { _, _ ->
                currentPermissionIndex++
                requestNextPermission()
            }
            .setCancelable(false)
            .show()
    }

    private fun showSettingsDialog(permission: String) {
        val message = getSettingsMessage(permission)
        AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
                currentPermissionIndex++
                requestNextPermission()
            }
            .setNegativeButton("Skip") { _, _ ->
                currentPermissionIndex++
                requestNextPermission()
            }
            .setCancelable(false)
            .show()
    }

    private fun getRationaleMessage(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Microphone access is required to make audio calls."
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications are used to alert you of incoming calls and messages."
            Manifest.permission.NEARBY_WIFI_DEVICES -> "Nearby Devices permission is required to discover and connect to peers."
            Manifest.permission.ACCESS_FINE_LOCATION -> "Location permission is required for Wi-Fi Direct to discover nearby peers."
            else -> "This permission is required for the app to function properly."
        }
    }

    private fun getSettingsMessage(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Microphone access is permanently denied. Please enable it in Settings to make calls."
            Manifest.permission.POST_NOTIFICATIONS -> "Notification access is permanently denied. Please enable it in Settings."
            Manifest.permission.NEARBY_WIFI_DEVICES -> "Nearby Devices access is permanently denied. Please enable it in Settings to connect."
            Manifest.permission.ACCESS_FINE_LOCATION -> "Location access is permanently denied. Please enable it in Settings to connect."
            else -> "A required permission is permanently denied. Please enable it in Settings."
        }
    }
}
