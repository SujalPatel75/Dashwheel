package com.openauto.unitproto.mtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MtcSyncTest {

    private val frame = ByteArray(MtcSync.LENGTH).also {
        it[1] = 57          // speed
        it[2] = 0x09; it[3] = 0x92.toByte()   // 2450 rpm
        it[4] = 139.toByte()  // 13.9 V
        it[5] = 0xF6.toByte() // -10 °C
        it[6] = 0x01; it[7] = 0xE2.toByte(); it[8] = 0x40  // 123456 km
    }

    private val layout = MtcSync.Layout(speedAt = 1, rpmAt = 2, voltsTenthsAt = 4, tempAt = 5, mileageAt = 6)

    @Test
    fun aKnownLayout_decodesEveryField() {
        val d = MtcSync.decode(frame, layout)!!
        assertEquals(57, d.speedKmh)
        assertEquals(2450, d.rpm)
        assertEquals(13.9, d.volts!!, 0.001)
        assertEquals(-10, d.tempC)
        assertEquals(123456, d.mileageKm)
    }

    @Test
    fun theUnknownLayout_decodesNothing_butStillTakesTheFrame() {
        val d = MtcSync.decode(frame)!!
        assertTrue(d.isEmpty)
    }

    @Test
    fun aShortOrMissingFrame_isNull() {
        assertNull(MtcSync.decode(ByteArray(19), layout))
        assertNull(MtcSync.decode(null, layout))
    }

    @Test
    fun implausibleValues_areDropped() {
        val odd = frame.copyOf().also { it[4] = 10; it[2] = 0x7F; it[3] = 0xFF.toByte() }
        val d = MtcSync.decode(odd, layout)!!
        assertNull(d.volts)
        assertNull(d.rpm)
        assertEquals(57, d.speedKmh)
    }

    @Test
    fun irKeys_haveNamesForTheWheelOnes() {
        assertEquals("volume_up", MtcIrKeys.name(273))
        assertEquals("hang_up", MtcIrKeys.name(317))
        assertEquals("voice", MtcIrKeys.name(366))
        assertNull(MtcIrKeys.name(999))
        assertTrue(MtcProtocol.ACTION_SYNC in MtcProtocol.BROADCASTS)
    }
}
