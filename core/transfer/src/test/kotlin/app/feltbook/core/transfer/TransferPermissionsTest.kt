package app.feltbook.core.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferPermissionsTest {
    @Test
    fun `Android 13+ — Bluetooth и Wi-Fi рядом, без геолокации`() {
        val expected = listOf(
            TransferPermissions.BLUETOOTH_SCAN,
            TransferPermissions.BLUETOOTH_ADVERTISE,
            TransferPermissions.BLUETOOTH_CONNECT,
            TransferPermissions.NEARBY_WIFI_DEVICES,
        )
        assertEquals(expected, TransferPermissions.runtimeFor(33))
        assertEquals(expected, TransferPermissions.runtimeFor(36))
    }

    @Test
    fun `Android 12 — Bluetooth рядом и точная геолокация вместе с приблизительной`() {
        val permissions = TransferPermissions.runtimeFor(31)
        assertEquals(permissions, TransferPermissions.runtimeFor(32))
        assertTrue(TransferPermissions.BLUETOOTH_SCAN in permissions)
        assertTrue(TransferPermissions.ACCESS_FINE_LOCATION in permissions)
        assertTrue(TransferPermissions.ACCESS_COARSE_LOCATION in permissions)
        assertFalse(TransferPermissions.NEARBY_WIFI_DEVICES in permissions)
    }

    @Test
    fun `Android 8–11 — только геолокация, и служба геолокации должна быть включена`() {
        listOf(26, 29, 30).forEach { sdk ->
            assertEquals(
                listOf(TransferPermissions.ACCESS_FINE_LOCATION, TransferPermissions.ACCESS_COARSE_LOCATION),
                TransferPermissions.runtimeFor(sdk),
            )
            assertTrue(TransferPermissions.needsLocationService(sdk))
        }
        assertFalse(TransferPermissions.needsLocationService(31))
    }

    @Test
    fun `ACK и DECLINE туда-обратно, мусор и чужой протокол — null`() {
        assertEquals(ControlMessage.ack("abc"), ControlMessage.decode(ControlMessage.ack("abc").encode()))
        assertEquals(ControlMessage.decline(), ControlMessage.decode(ControlMessage.decline().encode()))
        assertEquals(null, ControlMessage.decode("not json".encodeToByteArray()))
        assertEquals(null, ControlMessage.decode("""{"protocol":2,"type":"ACK"}""".encodeToByteArray()))
        assertEquals(null, ControlMessage.decode("""{"protocol":1,"type":"HELLO"}""".encodeToByteArray()))
    }
}
