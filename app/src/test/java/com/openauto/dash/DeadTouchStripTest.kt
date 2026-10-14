package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeadTouchStripTest {

    /** A slide every 10 ms in [step] px from [from] to [to], skipping what lies in [gap]. */
    private fun slide(from: Int, to: Int, step: Int, gap: IntRange? = null, startMs: Long = 0): List<Pair<Long, Float>> {
        var t = startMs
        return (from..to step step).filter { gap == null || it !in gap }.map { x -> (t.also { t += 10 }) to x.toFloat() }
    }

    @Test
    fun theWidestJumpInOneSlideIsTheStrip() {
        // The K706 measured on 2026-10-14: felt up to 262 px, again from 437 px.
        val reports = slide(0, 1280, 4, gap = 263..436)
        assertEquals(DeadTouchStrip.Strip(266, 434), DeadTouchStrip.find(reports, minJumpPx = 40f))
    }

    @Test
    fun aFingerDroppedAtTheStripAndFoundPastItIsOneSlide() {
        // The panel ends the touch at 262 px and starts a new one at 437 px, 20 ms later.
        val first = slide(0, 262, 4)
        val second = slide(438, 1280, 4, startMs = first.last().first + 20)
        assertEquals(DeadTouchStrip.Strip(266, 432), DeadTouchStrip.find(first + second, minJumpPx = 40f))
    }

    @Test
    fun separateTapsAreNoStrip() {
        // Two touches far apart in time and place: no strip between them.
        val taps = listOf(0L to 100f, 10L to 102f, 20L to 104f, 2_000L to 900f, 2_010L to 902f)
        assertNull(DeadTouchStrip.find(taps, minJumpPx = 40f))
    }

    @Test
    fun aWholeScreenIsNoStrip() {
        assertNull(DeadTouchStrip.find(slide(0, 1280, 4), minJumpPx = 40f))
        assertNull(DeadTouchStrip.find(emptyList(), minJumpPx = 40f))
    }

    @Test
    fun theSideBarGrowsOnlyWhenTheStripCoversThePaneStart() {
        val strip = DeadTouchStrip.Strip(265, 435)
        // Settings on the K706: side bar from 10 px, 260 px wide, pane at 270 px.
        assertEquals(437f, DeadTouchStrip.sideBarWidth(strip, 10f, 260f, reachPx = 240f, gapPx = 12f, maxPx = 520f))
        // A strip inside the side bar, or far into the pane: nothing moves.
        assertEquals(260f, DeadTouchStrip.sideBarWidth(DeadTouchStrip.Strip(100, 200), 10f, 260f, 240f, 12f, 520f))
        assertEquals(260f, DeadTouchStrip.sideBarWidth(DeadTouchStrip.Strip(700, 800), 10f, 260f, 240f, 12f, 520f))
        // Never wider than the cap.
        assertEquals(520f, DeadTouchStrip.sideBarWidth(DeadTouchStrip.Strip(300, 900), 10f, 260f, 240f, 12f, 520f))
    }
}
