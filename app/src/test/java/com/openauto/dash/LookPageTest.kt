package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Look page's families (LookPage.kt): every look in exactly one, in the right one. */
class LookPageTest {

    @Test
    fun everyLookIsInExactlyOneFamily() {
        val listed = LookFamilies.values.flatten()
        assertEquals("a look listed twice", listed.size, listed.toSet().size)
        DashThemeMode.entries.forEach { mode ->
            assertTrue("$mode has no family on the Look page", mode in listed)
        }
        // Nothing left over for the colours to pick up.
        assertEquals(LookFamilies[LookFamily.COLOURS], looksIn(LookFamily.COLOURS))
    }

    @Test
    fun colourOnlyLooksAndOnlyThemAreInColours() {
        DashThemeMode.entries.forEach { mode ->
            val colourOnly = paletteFor(mode, light = false).Skin == DashSkin.STANDARD
            assertEquals("$mode", colourOnly, lookFamily(mode) == LookFamily.COLOURS)
        }
    }

    @Test
    fun colourOnlyLooksLeaveHomeAlone() {
        looksIn(LookFamily.COLOURS).forEach { mode ->
            assertNull("$mode brings a Home", skinLayout(mode))
        }
    }

    @Test
    fun everyFamilyHoldsALook() {
        LookFamily.entries.forEach { family ->
            assertTrue("$family is empty", looksIn(family).isNotEmpty())
        }
    }
}
