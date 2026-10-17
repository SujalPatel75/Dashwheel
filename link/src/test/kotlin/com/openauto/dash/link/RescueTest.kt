package com.openauto.dash.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RescueTest {
    @Test
    fun everyActionRoundTripsThroughTheCodec() {
        RescueCommand.Action.entries.forEachIndexed { i, action ->
            val sent = RescueCommand(action, id = i + 1L)
            assertEquals(sent, LinkCodec.decode(LinkCodec.encode(sent)))
        }
    }

    @Test
    fun theAnswerRoundTrips() {
        val done = RescueResult(id = 42, ok = true)
        assertEquals(done, LinkCodec.decode(LinkCodec.encode(done)))
        val failed = RescueResult(id = 43, ok = false)
        assertEquals(failed, LinkCodec.decode(LinkCodec.encode(failed)))
    }

    @Test
    fun readsTheWireFormat() {
        val read = LinkCodec.decode("""{"t":"rescue","action":"LEAVE_SPLIT","id":7}""".encodeToByteArray())
        assertEquals(RescueCommand(RescueCommand.Action.LEAVE_SPLIT, 7), read)
    }

    @Test
    fun anActionFromANewerPhoneIsSkippedNotRun() {
        assertNull(LinkCodec.decode("""{"t":"rescue","action":"WIPE_EVERYTHING","id":1}""".encodeToByteArray()))
    }
}
