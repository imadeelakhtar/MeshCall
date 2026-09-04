package com.example.meshcall

/**
 * Utility for formatting and parsing offline location messages.
 * We use a deterministic text format to easily piggyback on the existing TYPE_TEXT transport.
 */
object LocationMessageUtils {

    const val LOCATION_PREFIX = "[LOCATION]"

    /**
     * Converts a latitude and longitude into a standard string payload.
     * Example output: "[LOCATION]geo:37.7749,-122.4194"
     */
    fun createLocationPayload(latitude: Double, longitude: Double): String {
        return "$LOCATION_PREFIX geo:$latitude,$longitude"
    }

    /**
     * Checks if a text message is a location payload.
     */
    fun isLocationMessage(text: String): Boolean {
        return text.startsWith(LOCATION_PREFIX)
    }

    /**
     * Parses a location payload back into a Pair(latitude, longitude).
     * Returns null if parsing fails.
     */
    fun parseLocation(text: String): Pair<Double, Double>? {
        if (!isLocationMessage(text)) return null
        
        try {
            val geoPart = text.removePrefix(LOCATION_PREFIX).trim()
            if (geoPart.startsWith("geo:")) {
                val coords = geoPart.removePrefix("geo:").split(",")
                if (coords.size >= 2) {
                    val lat = coords[0].toDoubleOrNull()
                    val lon = coords[1].toDoubleOrNull()
                    if (lat != null && lon != null) {
                        return Pair(lat, lon)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return null
    }
}
