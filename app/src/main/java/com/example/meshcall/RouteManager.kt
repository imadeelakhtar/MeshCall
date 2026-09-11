package com.example.meshcall

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

data class RouteInfo(
    val destinationUserId: String,
    val destinationMacAddress: String,
    val nextHopPeerId: String,
    val hopCount: Int,
    val lastUpdateTime: Long
)

class RouteManager {
    private val TAG = "RouteManager"
    
    // Maps destinationUserId -> RouteInfo
    private val routingTable = ConcurrentHashMap<String, RouteInfo>()
    
    // Prevents forwarding loops and duplicates
    // Maps packetId -> timestamp received
    private val packetCache = ConcurrentHashMap<String, Long>()
    
    // Time-to-live for a route in milliseconds (30 seconds)
    private val ROUTE_TIMEOUT_MS = 45_000L

    fun addOrUpdateRoute(destinationUserId: String, destinationMacAddress: String = "", nextHopPeerId: String, hopCount: Int = 1) {
        val existing = routingTable[destinationUserId]
        // Prefer direct or fewer hops, but always update the timestamp if it's the same nextHop
        if (existing == null || existing.hopCount > hopCount || existing.nextHopPeerId == nextHopPeerId) {
            routingTable[destinationUserId] = RouteInfo(destinationUserId, destinationMacAddress, nextHopPeerId, hopCount, System.currentTimeMillis())
            Log.i(TAG, "Route added/updated: dest=$destinationUserId (mac=$destinationMacAddress) via nextHop=$nextHopPeerId (hops=$hopCount)")
        }
    }

    fun hasRouteTo(destinationUserId: String): Boolean {
        cleanupStaleRoutes()
        return routingTable.containsKey(destinationUserId)
    }

    fun getNextHopFor(destinationUserId: String): String? {
        cleanupStaleRoutes()
        return routingTable[destinationUserId]?.nextHopPeerId
    }

    fun getUserIdForMac(macAddress: String): String? {
        cleanupStaleRoutes()
        return routingTable.values.find { it.destinationMacAddress == macAddress }?.destinationUserId
    }

    fun removeRoutesVia(nextHopPeerId: String) {
        val toRemove = routingTable.filterValues { it.nextHopPeerId == nextHopPeerId }.keys
        for (key in toRemove) {
            routingTable.remove(key)
            Log.i(TAG, "Route removed: dest=$key due to nextHop $nextHopPeerId disconnect")
        }
    }
    
    fun removeRouteTo(destinationUserId: String) {
        routingTable.remove(destinationUserId)
    }

    private fun cleanupStaleRoutes() {
        val now = System.currentTimeMillis()
        val staleKeys = routingTable.filterValues { now - it.lastUpdateTime > ROUTE_TIMEOUT_MS }.keys
        for (key in staleKeys) {
            routingTable.remove(key)
            Log.i(TAG, "Route removed: dest=$key due to timeout")
        }
    }

    fun isDuplicatePacket(packetId: String): Boolean {
        // Cleanup old cache entries (older than 60s)
        val now = System.currentTimeMillis()
        val stalePackets = packetCache.filterValues { now - it > 60_000L }.keys
        for (key in stalePackets) {
            packetCache.remove(key)
        }

        if (packetCache.containsKey(packetId)) {
            return true
        }
        packetCache[packetId] = now
        return false
    }
    
    fun getAllReachableUserIds(): List<String> {
        cleanupStaleRoutes()
        return routingTable.keys().toList()
    }
}
