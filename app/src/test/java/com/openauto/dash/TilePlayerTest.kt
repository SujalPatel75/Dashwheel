package com.openauto.dash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TilePlayerTest {
    private val ytm = "com.google.android.apps.youtube.music"
    private val song = listOf(ytm, "Bohemian Rhapsody", "Queen")
    private val next = listOf(ytm, "Under Pressure", "Queen")

    @Test
    fun aNewSongInATileOpensThePlayer() {
        assertTrue(TilePlayer.opens(null, song, playing = true, inTile = true, touched = false))
        assertTrue(TilePlayer.opens(song, next, playing = true, inTile = true, touched = false))
    }

    @Test
    fun theSameSongPausedAndPlayedAgainDoesNot() {
        assertFalse(TilePlayer.opens(song, song, playing = true, inTile = true, touched = false))
    }

    @Test
    fun notWhileTheDriverUsesTheTileNorOutOfATileNorPaused() {
        assertFalse(TilePlayer.opens(song, next, playing = true, inTile = true, touched = true))
        assertFalse(TilePlayer.opens(song, next, playing = true, inTile = false, touched = false))
        assertFalse(TilePlayer.opens(song, next, playing = false, inTile = true, touched = false))
    }

    @Test
    fun aSongWithoutTitleNorArtistDoesNot() {
        assertFalse(TilePlayer.opens(song, listOf(ytm, null, ""), playing = true, inTile = true, touched = false))
    }
}
