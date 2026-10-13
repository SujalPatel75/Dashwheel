package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Car3DSpotsTest {

    @Test
    fun everyPartHasAPlaceOnTheCar() {
        for (spot in CarSpot.entries) {
            val s = Car3DSpots.of(spot)
            val (along, across, up) = s.at
            assertTrue("$spot along", along in 0f..1f)
            assertTrue("$spot across", across in -1f..1f)
            assertTrue("$spot up", up in 0f..1f)
            assertTrue("$spot zoom", s.camera.zoom in 0.3f..1.5f)
            assertTrue("$spot yaw", s.camera.yaw in 0f..360f)
            for (h in s.open) assertTrue("$spot hinge $h", h.startsWith("Hinge_"))
        }
    }

    @Test
    fun theLeftRearLampIsSeenFromBehindOnTheLeft() {
        val s = Car3DSpots.of(CarSpot.REAR_LIGHT_LEFT)
        assertEquals(listOf("Lamp_tail_l"), s.glow)
        // Behind (yaw past 90 and before 270) and on the left (sin(yaw) < 0: toward -z).
        assertTrue(s.camera.yaw > 180f && s.camera.yaw < 270f)
        assertTrue(s.at.second < 0f)
        val right = Car3DSpots.of(CarSpot.REAR_LIGHT_RIGHT)
        assertTrue(right.camera.yaw > 90f && right.camera.yaw < 180f)
    }

    @Test
    fun engineFaultsOpenTheBonnet() {
        for (spot in listOf(CarSpot.ENGINE, CarSpot.BATTERY, CarSpot.GEARBOX, CarSpot.WASHER)) {
            assertEquals(listOf("Hinge_bonnet"), Car3DSpots.of(spot).open)
        }
        assertEquals(listOf("Hinge_tailgate"), Car3DSpots.of(CarSpot.BOOT).open)
        assertTrue(Car3DSpots.of(CarSpot.REAR_LIGHT_LEFT).open.isEmpty())
    }

    @Test
    fun theSteeringFollowsTheDriversSide() {
        assertTrue(Car3DSpots.of(CarSpot.STEERING, driverOnRight = false).at.second < 0f)
        assertTrue(Car3DSpots.of(CarSpot.STEERING, driverOnRight = true).at.second > 0f)
    }

    @Test
    fun namesMatchWholeOrByTheirBeginning() {
        assertTrue(Car3DSpots.matches("Lamp_tail_l", listOf("Lamp_tail_l")))
        // Blender's copies of a part (".001") belong to it.
        assertTrue(Car3DSpots.matches("Lamp_tail_l.001", listOf("Lamp_tail_l")))
        assertFalse(Car3DSpots.matches("Lamp_tail_lx", listOf("Lamp_tail_l")))
        assertTrue(Car3DSpots.matches("Wheel_rr", listOf("Wheel_")))
        assertFalse(Car3DSpots.matches("Wheelbase", listOf("Wheel_")))
    }

    @Test
    fun hingesOpenTheRigsWay() {
        assertEquals(-62f, Car3DSpots.hingeAngle("Hinge_door_fl"))
        assertEquals(62f, Car3DSpots.hingeAngle("Hinge_door_rr"))
        assertEquals(80f, Car3DSpots.hingeAngle("Hinge_tailgate"))
        assertEquals(-48f, Car3DSpots.hingeAngle("Hinge_bonnet"))
        assertNull(Car3DSpots.hingeAngle("Hinge_spoiler"))
    }
}
