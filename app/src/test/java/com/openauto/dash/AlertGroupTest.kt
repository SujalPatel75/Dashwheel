package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertGroupTest {
    private val lines = AlertGroup.entries.flatMap { it.lines }

    @Test
    fun everyPopUpIsInTheGridOnce() {
        val shown = lines.filterIsInstance<AlertLine.Shown>().map { it.kind }
        assertEquals(AlertKind.entries.toSet(), shown.toSet())
        assertEquals(shown.size, shown.toSet().size)
    }

    @Test
    fun everySpokenEventIsInTheGridOnce() {
        val said = lines.filterIsInstance<AlertLine.Said>().map { it.event }
        assertEquals(SpokenEvent.entries.toSet(), said.toSet())
        assertEquals(said.size, said.toSet().size)
    }
}
