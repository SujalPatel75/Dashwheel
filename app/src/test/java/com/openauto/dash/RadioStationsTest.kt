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

    @Test
    fun dabZAsTheTesterSentIt() {
        // From a K706 tester's unit log, 2026-10-13: (title, artist, album) as DAB-Z published them.
        val r = RadioStations()
        fun dab(t: String, a: String, b: String) = r.note("com.zoulou.dab", t, a, b, live = true)
        assertEquals("", dab("Radio Salomon", "Radio Salomon", "SLO DAB+ R1"))
        // The radio text, the station as the artist: already on the second line.
        assertEquals("Radio Salomon", dab("HEAVY D & THE BOYZ/NOW THAT WE FOUND LOVE", "Radio Salomon", "SLO DAB+ R1"))
        assertEquals("Radio Salomon", dab("Your Home of Music", "Radio Salomon", "SLO DAB+ R1"))
        // The song and its singer, "station - multiplex" as the album.
        assertEquals("Radio Salomon", dab("NOW THAT WE FOUND LOVE", "HEAVY D & THE BOYZ", "Radio Salomon - SLO DAB+ R1"))
        // Another station: tuned, then its song.
        assertEquals("", dab("Radio Si", "Radio Si", "SLO DAB+ R1"))
        assertEquals("Radio Si", dab("DAI DAI", "SHAKIRA IN BURNA BOY", "Radio Si - SLO DAB+ R1"))
    }

    @Test
    fun theStationGoesAfterTheArtistOnce() {
        val song = MediaState(title = "DAI DAI", artist = "SHAKIRA IN BURNA BOY", station = "Radio Si")
        assertEquals("SHAKIRA IN BURNA BOY · Radio Si", withStation(song).artist)
        // Already there (the radio text with the station as the artist): left as it is.
        val text = MediaState(title = "Your Home of Music", artist = "Radio Si", station = "Radio Si")
        assertEquals("Radio Si", withStation(text).artist)
        assertEquals("Radio Si", withStation(MediaState(title = "x", artist = "", station = "Radio Si")).artist)
    }
}
