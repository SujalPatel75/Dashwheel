package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Test

/** A radio's station name kept for the turns with its songs. */
class RadioStationsTest {

    @Test
    fun theStationNamedFirstTakesTurnsWithTheSong() {
        val r = RadioStations()
        // Tuned: the app names the station.
        assertEquals("", r.note("dab", "France Inter", "", live = true))
        // Then the song, with or without an artist field.
        assertEquals("France Inter", r.note("dab", "Bohemian Rhapsody", "Queen", live = true))
        assertEquals("France Inter", r.note("dab", "Queen - Bohemian Rhapsody", "", live = true))
        // Another station.
        assertEquals("", r.note("dab", "FIP", "", live = true))
        assertEquals("FIP", r.note("dab", "So What", "Miles Davis", live = true))
    }

    @Test
    fun noTurnsForTracksOrUnknownStations() {
        val r = RadioStations()
        // A song with no station named before it.
        assertEquals("", r.note("dab", "So What", "Miles Davis", live = true))
        // A track with a length is no radio.
        r.note("music", "Album intro", "", live = false)
        assertEquals("", r.note("music", "So What", "Miles Davis", live = false))
        // The song already says the station's name.
        r.note("web", "Nova", "", live = true)
        assertEquals("", r.note("web", "Nova - Le Grand Mix", "", live = true))
    }
}
