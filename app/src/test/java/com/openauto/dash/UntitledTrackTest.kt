package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the media tile shows for a player whose session plays with no title (factory music players). */
class UntitledTrackTest {

    private fun said(title: String, text: String = "") = PlayerNotifications.Said("key", title, text)

    @Test
    fun displayTitleFirst() {
        assertEquals("Song" to "Band", untitledTrack(" Song ", "Band", said("Other"), "Music", playing = true))
    }

    @Test
    fun thenTheNotification() {
        assertEquals("Song" to "Band", untitledTrack(null, null, said("Song", "Band"), "Music", playing = true))
        assertEquals("song.mp3" to "", untitledTrack("", null, said("", "song.mp3"), "Music", playing = true))
    }

    @Test
    fun thenThePlayersNameWhilePlaying() {
        assertEquals("Zene" to "", untitledTrack(null, null, null, "Zene", playing = true))
        assertNull(untitledTrack(null, null, null, "Zene", playing = false))
        assertNull(untitledTrack(null, null, null, null, playing = true))
    }
}
