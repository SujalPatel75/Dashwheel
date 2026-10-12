package com.openauto.dash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** The camera's picture turned the way the car app turns it. */
class ReverseCameraTurnTest {

    @Test
    fun unisocCountsLeftRightTheOtherWay() {
        // The K706 (UIS7862): 0 is turned left to right.
        assertEquals(1, romMirror(0, "ums512_7862"))
        assertEquals(0, romMirror(1, "7862"))
        assertEquals(3, romMirror(2, "8581"))
        assertEquals(2, romMirror(3, "7870"))
        // Elsewhere as set; nonsense as nothing.
        assertEquals(0, romMirror(0, "8953"))
        assertEquals(2, romMirror(2, ""))
        assertEquals(1, romMirror(9, "7862"))
    }

    @Test
    fun pixelsTurnInPlace() {
        val p = intArrayOf(1, 2, 3, 4, 5, 6)
        turnPixels(p, 3, 2, flipX = true, flipY = false)
        assertArrayEquals(intArrayOf(3, 2, 1, 6, 5, 4), p)
        turnPixels(p, 3, 2, flipX = false, flipY = true)
        assertArrayEquals(intArrayOf(6, 5, 4, 3, 2, 1), p)
        turnPixels(p, 3, 2, flipX = true, flipY = true)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6), p)
    }
}
