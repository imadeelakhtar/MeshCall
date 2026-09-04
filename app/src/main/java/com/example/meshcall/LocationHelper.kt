package com.example.meshcall

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class LocationHelper(private val activity: Activity) {

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "LocationHelper"
    }

    interface LocationCallback {
        fun onLocationResult(location: Location?)
        fun onError(error: String)
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    fun getCurrentLocation(callback: LocationCallback) {
        if (!hasLocationPermission()) {
            callback.onError("Location permission not granted")
            return
        }

        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            callback.onError("Location services are disabled")
            return
        }

        try {
            // Fast fallback: try to get last known location first
            var location: Location? = null
            if (isGpsEnabled) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (location == null && isNetworkEnabled) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (location != null) {
                callback.onLocationResult(location)
                return
            }

            // If no last known location, request a single update
            val provider = if (isGpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            
            val locationListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    locationManager.removeUpdates(this)
                    callback.onLocationResult(loc)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            locationManager.requestSingleUpdate(provider, locationListener, null)
            
            // Timeout mechanism could be added here, but for simplicity we rely on the listener
        } catch (e: SecurityException) {
            callback.onError("SecurityException: ${e.message}")
        } catch (e: Exception) {
            callback.onError("Error getting location: ${e.message}")
        }
    }
}
