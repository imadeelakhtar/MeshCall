package com.example.meshcall

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class WifiStateHelper(private val activity: ComponentActivity) {

    private var onWifiEnabledCallback: (() -> Unit)? = null

    private val wifiEnableLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // When they return from the settings/panel, check if it's enabled now
        if (isWifiEnabled()) {
            onWifiEnabledCallback?.invoke()
        }
        // If they cancelled or didn't turn it on, we do nothing as requested.
        onWifiEnabledCallback = null
    }

    fun isWifiEnabled(): Boolean {
        val wifiManager = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled == true
    }

    fun promptEnableWifi(onSuccess: () -> Unit) {
        if (isWifiEnabled()) {
            onSuccess()
            return
        }

        onWifiEnabledCallback = onSuccess
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        wifiEnableLauncher.launch(intent)
    }
}
