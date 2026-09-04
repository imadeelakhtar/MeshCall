package com.example.meshcall

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

interface WifiDirectListener {
    fun onPeersChanged(peers: Collection<WifiP2pDevice>)
    fun onConnectionChanged(isConnected: Boolean, device: WifiP2pDevice?, info: android.net.wifi.p2p.WifiP2pInfo?)
    fun onWifiDirectEnabled(isEnabled: Boolean)
}

class WifiDirectManager(
    private val context: Context,
    private val listener: WifiDirectListener
) {
    private val TAG = "WifiDirectManager"
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private val receiver = WifiDirectBroadcastReceiver()

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    init {
        Log.i("MeshCall_Nav", "WIFI_P2P_MANAGER_INIT")
        channel = manager?.initialize(context, Looper.getMainLooper(), null)
        Log.i("MeshCall_Nav", "WIFI_P2P_CHANNEL_READY")
    }

    fun registerReceiver() {
        Log.i("MeshCall_Nav", "P2P_RECEIVER_REGISTERED")
        context.registerReceiver(receiver, intentFilter)
    }

    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Unregister receiver failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        val currentChannel = channel
        if (currentChannel == null) {
            Log.e(TAG, "discoverPeers: channel is null!")
            return
        }

        // Check permission before calling discoverPeers
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermission) {
            Log.e(TAG, "P2P_PERMISSION_DENIED: cannot call discoverPeers")
            return
        }
        Log.i("MeshCall_Nav", "P2P_PERMISSION_GRANTED")
        Log.i("MeshCall_Nav", "P2P_STATE_CHECK")

        // NOTE: Do NOT call requestPeers() here before discoverPeers().
        // Any peer objects returned by requestPeers() before a live scan has completed
        // may have a zeroed deviceAddress ("00:00:00:00:00:00"), which causes connect() to fail.
        // The peer list must only be delivered via WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers().

        Log.i("MeshCall_Nav", "DISCOVER_PEERS_START")
        manager?.discoverPeers(currentChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i("MeshCall_Nav", "DISCOVER_PEERS_SUCCESS")
            }

            override fun onFailure(reasonCode: Int) {
                Log.i("MeshCall_Nav", "DISCOVER_PEERS_FAILURE reasonCode=$reasonCode Android=${Build.VERSION.SDK_INT}")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        val currentChannel = channel
        if (currentChannel == null) {
            Log.e(TAG, "P2P_CONNECT_FAILURE: channel is null")
            return
        }

        // Log full device state before attempting connection
        val statusStr = when (device.status) {
            WifiP2pDevice.AVAILABLE  -> "AVAILABLE"
            WifiP2pDevice.INVITED    -> "INVITED"
            WifiP2pDevice.CONNECTED  -> "CONNECTED"
            WifiP2pDevice.FAILED     -> "FAILED"
            WifiP2pDevice.UNAVAILABLE-> "UNAVAILABLE"
            else                     -> "UNKNOWN(${device.status})"
        }
        Log.i("MeshCall", "P2P_CONNECT_START: name=${device.deviceName} addr=${device.deviceAddress} status=$statusStr isGroupOwner=${device.isGroupOwner} Android=${Build.VERSION.SDK_INT}")

        // Validate device address — a zeroed address means the device object is stale
        val addr = device.deviceAddress
        if (addr.isNullOrBlank() || addr == "00:00:00:00:00:00") {
            Log.e(TAG, "P2P_CONNECT_FAILURE: deviceAddress is invalid ('$addr') — stale peer object. Wait for PEERS_CHANGED before connecting.")
            return
        }

        // Check permission
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermission) {
            Log.e(TAG, "P2P_PERMISSION_DENIED: cannot call connect()")
            return
        }
        Log.i("MeshCall", "P2P_PERMISSION_GRANTED (connect)")

        val config = WifiP2pConfig().apply {
            deviceAddress = addr
            wps.setup = WpsInfo.PBC
        }

        manager?.connect(currentChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i("MeshCall", "P2P_CONNECT_SUCCESS: invitation sent to ${device.deviceName} (${device.deviceAddress})")
            }

            override fun onFailure(reason: Int) {
                val reasonStr = when (reason) {
                    WifiP2pManager.ERROR          -> "ERROR(0)"
                    WifiP2pManager.P2P_UNSUPPORTED-> "P2P_UNSUPPORTED(1)"
                    WifiP2pManager.BUSY           -> "BUSY(2)"
                    WifiP2pManager.NO_SERVICE_REQUESTS -> "NO_SERVICE_REQUESTS(3)"
                    else                          -> "UNKNOWN($reason)"
                }
                Log.e(TAG, "P2P_CONNECT_FAILURE: device=${device.deviceName} addr=${device.deviceAddress} reason=$reasonStr Android=${Build.VERSION.SDK_INT}")
            }
        })
    }

    fun disconnect() {
        val currentChannel = channel ?: return
        manager?.removeGroup(currentChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Successfully disconnected from Wi-Fi Direct group")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to disconnect from Wi-Fi Direct group. Reason: $reason")
            }
        })
    }

    inner class WifiDirectBroadcastReceiver : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "BroadcastReceiver onReceive: action = $action")
            val currentChannel = channel
            if (currentChannel == null) {
                Log.e(TAG, "BroadcastReceiver onReceive: channel is null!")
                return
            }
            when (action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.d(TAG, "WIFI_P2P_STATE_CHANGED_ACTION: state=$state (isEnabled=$isEnabled)")
                    listener.onWifiDirectEnabled(isEnabled)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    Log.i("MeshCall_Nav", "PEERS_CHANGED")
                    Log.i("MeshCall_Nav", "REQUEST_PEERS")
                    manager?.requestPeers(currentChannel) { peers ->
                        Log.i("MeshCall_Nav", "PEERS_RECEIVED")
                        listener.onPeersChanged(peers.deviceList)
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO) as? NetworkInfo
                    }

                    val p2pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, android.net.wifi.p2p.WifiP2pInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO) as? android.net.wifi.p2p.WifiP2pInfo
                    }

                    Log.i("MeshCall", "P2P_CONNECTION_CHANGED: isConnected=${networkInfo?.isConnected} groupFormed=${p2pInfo?.groupFormed} isGroupOwner=${p2pInfo?.isGroupOwner} ownerAddr=${p2pInfo?.groupOwnerAddress?.hostAddress}")

                    if (networkInfo?.isConnected == true || p2pInfo?.groupFormed == true) {
                        Log.i("MeshCall", "GROUP_FORMED: requesting connection info")
                        manager?.requestConnectionInfo(currentChannel) { info ->
                            Log.i("MeshCall", "GROUP_INFO: groupFormed=${info?.groupFormed} isGroupOwner=${info?.isGroupOwner} ownerAddr=${info?.groupOwnerAddress?.hostAddress}")
                            if (info != null && info.groupFormed) {
                                listener.onConnectionChanged(true, null, info)
                            } else {
                                listener.onConnectionChanged(false, null, null)
                            }
                        }
                    } else {
                        Log.i("MeshCall", "GROUP_DISSOLVED: notifying disconnect")
                        listener.onConnectionChanged(false, null, null)
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Update local device info if needed
                }
            }
        }
    }
}
