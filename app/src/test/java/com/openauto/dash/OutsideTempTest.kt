package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The K706 CAN app's outside temperature text, as its status bar gets it. */
class OutsideTempTest {

    @Test
    fun readsCelsius() {
        assertEquals(12.5, UnitSignals.parseOutsideTemp("12.5℃")!!, 0.001)
        assertEquals(-3.0, UnitSignals.parseOutsideTemp("-3℃")!!, 0.001)
        assertEquals(21.0, UnitSignals.parseOutsideTemp(" 21°C ")!!, 0.001)
        assertEquals(7.5, UnitSignals.parseOutsideTemp("7,5°C")!!, 0.001)
    }

    @Test
    fun convertsFahrenheit() {
        assertEquals(12.5, UnitSignals.parseOutsideTemp("54.5℉")!!, 0.001)
        assertEquals(0.0, UnitSignals.parseOutsideTemp("32°F")!!, 0.001)
    }

    @Test
    fun nothingWhenOffOrImplausible() {
        assertNull(UnitSignals.parseOutsideTemp(""))
        assertNull(UnitSignals.parseOutsideTemp(null))
        assertNull(UnitSignals.parseOutsideTemp("--℃"))
        assertNull(UnitSignals.parseOutsideTemp("-40℃"))
        assertNull(UnitSignals.parseOutsideTemp("120℃"))
    }
}
