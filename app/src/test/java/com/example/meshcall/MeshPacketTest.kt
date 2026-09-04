package com.example.meshcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

/**
 * Lightweight unit validation for MeshPacket.
 * NOTE: If running as a standard local unit test, android.util.Base64 and org.json.JSONObject 
 * might throw "Method not mocked". To execute successfully, run as an Instrumented Test,
 * or configure testOptions.unitTests.isReturnDefaultValues = true in build.gradle.
 */
class MeshPacketTest {

    @Test
    fun verifyMeshPacketCreationAndUniqueness() {
        // Verify creation
        val payloadData = "Hello Mesh".toByteArray(Charsets.UTF_8)
        val packet1 = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            sourceId = "MC-11111111",
            destinationId = "MC-22222222",
            packetType = 0,
            ttl = 10,
            hopCount = 0,
            payload = payloadData
        )
        
        val packet2 = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            sourceId = "MC-33333333",
            destinationId = "MC-44444444",
            packetType = 0,
            ttl = 10,
            hopCount = 0,
            payload = payloadData
        )

        // Verify packetId remains unique between two newly created packets
        assertNotNull(packet1.packetId)
        assertNotNull(packet2.packetId)
        assertNotEquals(packet1.packetId, packet2.packetId)
        
        // Verify basic properties
        assertEquals("MC-11111111", packet1.sourceId)
        assertEquals(10, packet1.ttl)
        assertEquals(0, packet1.hopCount)
    }
}
