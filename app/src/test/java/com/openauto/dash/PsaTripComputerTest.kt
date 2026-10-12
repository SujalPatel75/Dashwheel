package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Peugeot / Citroën CAN box's trip computer frames, as the car app reads them. */
class PsaTripComputerTest {

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    /** A whole frame with its sum: FD LEN TYPE data… sum. */
    private fun frame(type: Int, vararg data: Int): ByteArray {
        val len = data.size + 3
        val sum = (len + type + data.sum()) and 0xFF
        return bytes(0xFD, len, type, *data, sum)
    }

    private fun collect(vararg pieces: ByteArray): List<Pair<Int, List<Int>>> {
        val out = ArrayList<Pair<Int, List<Int>>>()
        val frames = RzcFrames()
        // Each piece comes as an MCU message: cmd 65 first.
        for (p in pieces) frames.push(byteArrayOf(65) + p, 1) { t, d -> out += t to d.map { it.toInt() and 0xFF } }
        return out
    }

    @Test
    fun sumMatchesTheCarAppsQueries() {
        // The car app's own questions, as the firmware writes them.
        assertTrue(RzcFrames.sumOk(bytes(0xFD, 0x04, 0x8F, 0x33, 0xC6)))
        assertTrue(RzcFrames.sumOk(bytes(0xFD, 0x04, 0x8F, 0x3A, 0xCD)))
        assertTrue(RzcFrames.sumOk(bytes(0xFD, 0x06, 0x82, 0x41, 0x00, 0x00, 0xC9)))
        assertFalse(RzcFrames.sumOk(bytes(0xFD, 0x04, 0x8F, 0x33, 0xC7)))
    }

    @Test
    fun readsFramesSplitAcrossMessagesAndSkipsNoise() {
        val angle = frame(0x29, 0xDD, 0xFF)
        val page = frame(0x33, 0x00, 0x34, 0x01, 0xC2, 0x00, 0x2A)
        val joined = bytes(0x00, 0x13) + angle + page
        val got = collect(joined.copyOfRange(0, 5), joined.copyOfRange(5, 11), joined.copyOfRange(11, joined.size))
        assertEquals(listOf(0x29, 0x33), got.map { it.first })
        assertEquals(listOf(0x00, 0x34, 0x01, 0xC2, 0x00, 0x2A), got[1].second)
    }

    @Test
    fun dropsAFrameWithAWrongSum() {
        val bad = frame(0x34, 0, 52, 0, 60, 0, 120).also { it[it.size - 1] = (it.last() + 1).toByte() }
        val good = frame(0x35, 0, 48, 0, 70, 1, 0)
        assertEquals(listOf(0x35), collect(bad + good).map { it.first })
    }

    @Test
    fun decodesPageZero() {
        // 5.2 L/100, 450 km to empty, 42 km to the destination.
        val t = PsaTripComputer.decode(PsaTrip(), 0x33, bytes(0x00, 0x34, 0x01, 0xC2, 0x00, 0x2A))!!
        assertEquals(5.2f, t.instantL100!!, 0.001f)
        assertEquals(450, t.rangeKm)
        assertEquals(42, t.toDestinationKm)
    }

    @Test
    fun pageZeroDashesAreNull() {
        val t = PsaTripComputer.decode(PsaTrip(), 0x33, bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF))!!
        assertNull(t.instantL100)
        assertNull(t.rangeKm)
        assertNull(t.toDestinationKm)
    }

    @Test
    fun decodesBothTrips() {
        val one = PsaTripComputer.decode(PsaTrip(), 0x34, bytes(0, 58, 0, 63, 0x01, 0x2C))!!
        val two = PsaTripComputer.decode(one, 0x35, bytes(0, 49, 0xFF, 0xFF, 0x27, 0x10))!!
        assertEquals(PsaTripPage(5.8f, 63, 300), two.trip1)
        assertEquals(4.9f, two.trip2!!.avgL100!!, 0.001f)
        assertNull(two.trip2!!.avgKmh)
        // 10000 km is past what the car counts.
        assertNull(two.trip2!!.distanceKm)
    }

    @Test
    fun decodesWarningsLeavingOutNoMessage() {
        val t = PsaTripComputer.decode(PsaTrip(), 0x3A, bytes(3, 0x0C, 0xF0, 0x34))!!
        assertEquals(listOf(0x0C, 0x34), t.warnings)
        assertEquals(emptyList<Int>(), PsaTripComputer.decode(t, 0x3A, bytes(1, 0x00))!!.warnings)
    }

    @Test
    fun otherFramesAreNotTripPages() {
        assertNull(PsaTripComputer.decode(PsaTrip(), 0x29, bytes(0xDD, 0xFF)))
        assertNull(PsaTripComputer.decode(PsaTrip(), 0x34, bytes(0, 58)))
    }

    @Test
    fun queryFrameIsTheCarAppsOwn() {
        val q = PsaTripComputer.queryFrame(0x34)
        assertEquals(listOf(0xFD, 0x04, 0x8F, 0x34, 0x00), q.map { it.toInt() and 0xFF })
    }

    @Test
    fun knowsPsaMakes() {
        assertTrue(PsaTripComputer.isPsa("Citroën"))
        assertTrue(PsaTripComputer.isPsa("CITROEN"))
        assertTrue(PsaTripComputer.isPsa("Peugeot"))
        assertTrue(PsaTripComputer.isPsa("DS"))
        assertFalse(PsaTripComputer.isPsa("Dacia"))
        assertFalse(PsaTripComputer.isPsa(""))
    }

    @Test
    fun readsRds() {
        assertEquals("FIP", McuFeed.rdsName(bytes(0x46, 0x49, 0x50, 0x20, 0x20, 0x20, 0x20, 0x20)))
        assertNull(McuFeed.rdsName("        ".toByteArray()))
        assertNull(McuFeed.rdsName("A,B     ".toByteArray()))
        assertEquals("Now: Daft Punk", McuFeed.rdsText("Now:  Daft Punk\r ".toByteArray()))
    }

    @Test
    fun theWarningsOnTheCarAppsScreenAreKnown() {
        // As the car app showed them on the C4: a brake light bulb, then the engine.
        val t = PsaTripComputer.decode(PsaTrip(), 0x3A, bytes(2, 0x84, 0x77))!!
        assertEquals(listOf(0x84, 0x77), t.warnings)
        assertEquals(R.string.car_warn_bulb_brake_left_rear, PsaWarnings.of(0x84)!!.textRes)
        assertEquals(PsaWarnings.Level.SERVICE, PsaWarnings.of(0x84)!!.level)
        assertEquals(PsaWarnings.Level.SERVICE, PsaWarnings.of(0x77)!!.level)
        assertEquals(PsaWarnings.Level.STOP, PsaWarnings.of(0x0E)!!.level)
    }

    @Test
    fun noticesAreNoWarnings() {
        // "Door locked", "automatic wipers on", "diagnosis complete": the car says them, nothing to act on.
        val t = PsaTripComputer.decode(PsaTrip(), 0x3A, bytes(4, 0x07, 0x40, 0x3B, 0x34))!!
        assertEquals(listOf(0x34), t.warnings)
    }

    @Test
    fun everyCodeIsAWarningOrANotice() {
        for (code in 0x01..0x87) {
            assertTrue("code ${code.toString(16)}", PsaWarnings.of(code) != null || code in PsaWarnings.notices)
        }
    }
}
