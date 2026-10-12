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

    @Test
    fun theStationAsTheAlbumOrTheArtist() {
        val r = RadioStations()
        // The song as the title, the station as the album.
        assertEquals("Radio Swiss Pop", r.note("dab", "Africa", "Toto", album = "Radio Swiss Pop", live = true))
        // "Artist - Song" as the title, the station as the artist.
        assertEquals("Antenne Bayern", r.note("dab2", "Toto - Africa", "Antenne Bayern", live = true))
        // The station in both fields at first: it names itself, then a song comes.
        assertEquals("", r.note("dab3", "NRJ", "NRJ", live = true))
        assertEquals("NRJ", r.note("dab3", "Africa", "Toto", live = true))
    }

    @Test
    fun theGpsSpeedIsCorrectedOnceMoving() {
        assertEquals(3, SpeedCorrection.correctGps(3, 4))
        assertEquals(54, SpeedCorrection.correctGps(50, 4))
        assertEquals(0, SpeedCorrection.correctGps(0, 4))
    }
}
