package com.openauto.dash

import androidx.compose.ui.geometry.Offset

/*
 * Where a problem is on the car: each of the car's own warnings ([PsaWarnings])
 * and each engine fault code goes to a part of the car ([CarSpot]), and each
 * part to its place on the views the driver's car pack has: the back (the
 * rear lamps face on), the front, and the view from above, which every car
 * has (its own picture, or the drawn one). A part seen better from an end
 * goes on that end's view when there is one, else on the view from above.
 * Places come from each view's own anchors (wheels, bumpers, lamps), so they
 * fall on the driver's own car. Pure, so it is unit-tested.
 */

/** A view of the car a problem can be shown on. */
internal enum class CarMapView { BACK, FRONT, TOP }

/** A part of the car a problem is about. */
internal enum class CarSpot(val views: List<CarMapView>) {
    HEADLIGHT_LEFT(listOf(CarMapView.FRONT, CarMapView.TOP)),
    HEADLIGHT_RIGHT(listOf(CarMapView.FRONT, CarMapView.TOP)),
    HEADLIGHTS(listOf(CarMapView.FRONT, CarMapView.TOP)),
    FRONT_FOGS(listOf(CarMapView.FRONT, CarMapView.TOP)),
    REAR_LIGHT_LEFT(listOf(CarMapView.BACK, CarMapView.TOP)),
    REAR_LIGHT_RIGHT(listOf(CarMapView.BACK, CarMapView.TOP)),
    REAR_LIGHTS(listOf(CarMapView.BACK, CarMapView.TOP)),
    THIRD_BRAKE(listOf(CarMapView.BACK, CarMapView.TOP)),
    REAR_FOGS(listOf(CarMapView.BACK, CarMapView.TOP)),
    REVERSING(listOf(CarMapView.BACK, CarMapView.TOP)),
    INDICATORS(listOf(CarMapView.TOP)),
    SIDELIGHTS(listOf(CarMapView.TOP)),
    ENGINE(listOf(CarMapView.TOP)),
    EXHAUST(listOf(CarMapView.BACK, CarMapView.TOP)),
    GEARBOX(listOf(CarMapView.TOP)),
    BRAKES(listOf(CarMapView.TOP)),
    PARKING_BRAKE(listOf(CarMapView.TOP)),
    SUSPENSION(listOf(CarMapView.TOP)),
    STEERING(listOf(CarMapView.TOP)),
    DASHBOARD(listOf(CarMapView.TOP)),
    AIRBAGS(listOf(CarMapView.TOP)),
    BATTERY(listOf(CarMapView.TOP)),
    DOOR_FL(listOf(CarMapView.TOP)),
    DOOR_FR(listOf(CarMapView.TOP)),
    DOOR_RL(listOf(CarMapView.TOP)),
    DOOR_RR(listOf(CarMapView.TOP)),
    DOORS(listOf(CarMapView.TOP)),
    BOOT(listOf(CarMapView.BACK, CarMapView.TOP)),
    FUEL_FLAP(listOf(CarMapView.TOP)),
    WASHER(listOf(CarMapView.TOP)),
    TYRE_FL(listOf(CarMapView.TOP)),
    TYRE_FR(listOf(CarMapView.TOP)),
    TYRE_RL(listOf(CarMapView.TOP)),
    TYRE_RR(listOf(CarMapView.TOP)),
    TYRES(listOf(CarMapView.TOP)),
    FRONT_SENSORS(listOf(CarMapView.FRONT, CarMapView.TOP)),
    REAR_SENSORS(listOf(CarMapView.BACK, CarMapView.TOP))
}

/**
 * A view from above, nose up, by its anchors (fractions of its picture):
 * the bumpers' middles and the wheels front left, front right, rear left,
 * rear right. [driverOnRight]: the steering wheel on the car's right.
 */
internal data class TopFrame(
    val nose: Offset, val tail: Offset,
    val fl: Offset, val fr: Offset, val rl: Offset, val rr: Offset,
    val driverOnRight: Boolean = false
) {
    /**
     * A place on the car: [u] along it (0 the front bumper, 1 the rear one),
     * [v] across (-1 the left wheels' middle, +1 the right ones', beyond for the sides).
     */
    fun at(u: Float, v: Float): Offset {
        val along = tail - nose
        val halfTrack = ((fr - fl) + (rr - rl)) / 4f
        val centre = nose + along * u
        return centre + halfTrack * v
    }

    companion object {
        /** The drawn car's anchors (DoorAlertOverlay.drawCar: 140 x 260, the body 35..105 x 35..225). */
        val DRAWN = TopFrame(
            nose = Offset(70f / VIEW_W, 35f / VIEW_H), tail = Offset(70f / VIEW_W, 225f / VIEW_H),
            fl = Offset(35f / VIEW_W, 77f / VIEW_H), fr = Offset(105f / VIEW_W, 77f / VIEW_H),
            rl = Offset(35f / VIEW_W, 187f / VIEW_H), rr = Offset(105f / VIEW_W, 187f / VIEW_H)
        )
    }
}

/**
 * The car seen from one end by its lamps (fractions of its picture): [left]
 * and [right] are the car's own left and right lamp. From behind the car's
 * left is on the picture's left; from the front, on its right.
 */
internal data class EndFrame(val left: Offset, val right: Offset)

internal object CarProblemMap {

    /** Where [spot] is on [view] (fractions of its picture); empty when that view doesn't show it. */
    fun places(spot: CarSpot, view: CarMapView, top: TopFrame?, back: EndFrame?, front: EndFrame?): List<Offset> = when (view) {
        CarMapView.TOP -> top?.let { onTop(spot, it) }.orEmpty()
        CarMapView.BACK -> back?.let { onBack(spot, it) }.orEmpty()
        CarMapView.FRONT -> front?.let { onFront(spot, it) }.orEmpty()
    }

    /** The first of [spot]'s views there is a picture or a frame for. */
    fun viewFor(spot: CarSpot, available: Set<CarMapView>): CarMapView? = spot.views.firstOrNull { it in available }

    private fun onTop(spot: CarSpot, f: TopFrame): List<Offset> {
        // The driver's side, for the steering and the dashboard's airbag.
        val driver = if (f.driverOnRight) 0.45f else -0.45f
        return when (spot) {
            CarSpot.HEADLIGHT_LEFT -> listOf(f.at(0.05f, -0.8f))
            CarSpot.HEADLIGHT_RIGHT -> listOf(f.at(0.05f, 0.8f))
            CarSpot.HEADLIGHTS, CarSpot.FRONT_FOGS -> listOf(f.at(0.05f, -0.8f), f.at(0.05f, 0.8f))
            CarSpot.REAR_LIGHT_LEFT -> listOf(f.at(0.95f, -0.85f))
            CarSpot.REAR_LIGHT_RIGHT -> listOf(f.at(0.95f, 0.85f))
            CarSpot.REAR_LIGHTS, CarSpot.REAR_FOGS, CarSpot.REVERSING -> listOf(f.at(0.95f, -0.85f), f.at(0.95f, 0.85f))
            CarSpot.THIRD_BRAKE -> listOf(f.at(0.9f, 0f))
            CarSpot.INDICATORS, CarSpot.SIDELIGHTS -> listOf(f.at(0.05f, -0.8f), f.at(0.05f, 0.8f), f.at(0.95f, -0.85f), f.at(0.95f, 0.85f))
            CarSpot.ENGINE -> listOf(f.at(0.12f, 0f))
            CarSpot.BATTERY -> listOf(f.at(0.1f, -0.55f))
            CarSpot.WASHER -> listOf(f.at(0.1f, 0.6f))
            CarSpot.GEARBOX -> listOf(f.at(0.2f, -0.25f))
            CarSpot.STEERING -> listOf(f.at(0.33f, driver))
            CarSpot.DASHBOARD -> listOf(f.at(0.3f, 0f))
            CarSpot.AIRBAGS -> listOf(f.at(0.33f, driver), f.at(0.33f, -driver))
            CarSpot.EXHAUST -> listOf(f.at(0.98f, -0.45f))
            CarSpot.BRAKES, CarSpot.SUSPENSION, CarSpot.TYRES -> listOf(f.fl, f.fr, f.rl, f.rr)
            CarSpot.PARKING_BRAKE -> listOf(f.rl, f.rr)
            CarSpot.TYRE_FL -> listOf(f.fl)
            CarSpot.TYRE_FR -> listOf(f.fr)
            CarSpot.TYRE_RL -> listOf(f.rl)
            CarSpot.TYRE_RR -> listOf(f.rr)
            CarSpot.DOOR_FL -> listOf(f.at(0.42f, -1.05f))
            CarSpot.DOOR_FR -> listOf(f.at(0.42f, 1.05f))
            CarSpot.DOOR_RL -> listOf(f.at(0.62f, -1.05f))
            CarSpot.DOOR_RR -> listOf(f.at(0.62f, 1.05f))
            CarSpot.DOORS -> listOf(f.at(0.42f, -1.05f), f.at(0.42f, 1.05f), f.at(0.62f, -1.05f), f.at(0.62f, 1.05f))
            CarSpot.BOOT -> listOf(f.at(0.94f, 0f))
            // On the right, behind the rear door, on most cars of this size.
            CarSpot.FUEL_FLAP -> listOf(f.at(0.78f, 1.08f))
            CarSpot.FRONT_SENSORS -> listOf(f.at(0.0f, -0.5f), f.at(0.0f, 0.5f))
            CarSpot.REAR_SENSORS -> listOf(f.at(1.0f, -0.5f), f.at(1.0f, 0.5f))
        }
    }

    private fun onBack(spot: CarSpot, f: EndFrame): List<Offset> {
        val mid = (f.left.x + f.right.x) / 2f
        val y = (f.left.y + f.right.y) / 2f
        // Between a lamp and the middle: the inside of the lamp, where the reversing light sits.
        fun inner(o: Offset, by: Float) = Offset(o.x + (mid - o.x) * by, o.y)
        val lower = (y + 0.5f).coerceAtMost(0.93f)
        return when (spot) {
            CarSpot.REAR_LIGHT_LEFT -> listOf(f.left)
            CarSpot.REAR_LIGHT_RIGHT -> listOf(f.right)
            CarSpot.REAR_LIGHTS -> listOf(f.left, f.right)
            CarSpot.REVERSING -> listOf(inner(f.left, 0.12f), inner(f.right, 0.12f))
            CarSpot.THIRD_BRAKE -> listOf(Offset(mid, 0.05f))
            CarSpot.REAR_FOGS -> listOf(Offset(f.left.x + 0.08f, lower), Offset(f.right.x - 0.08f, lower))
            CarSpot.EXHAUST -> listOf(Offset(mid - (mid - f.left.x) * 0.5f, 0.95f))
            CarSpot.BOOT -> listOf(Offset(mid, y + 0.1f))
            CarSpot.REAR_SENSORS -> listOf(Offset(mid - (mid - f.left.x) * 0.6f, lower - 0.04f), Offset(mid + (f.right.x - mid) * 0.6f, lower - 0.04f))
            else -> emptyList()
        }
    }

    private fun onFront(spot: CarSpot, f: EndFrame): List<Offset> {
        val mid = (f.left.x + f.right.x) / 2f
        val y = (f.left.y + f.right.y) / 2f
        val lower = (y + 0.35f).coerceAtMost(0.9f)
        return when (spot) {
            CarSpot.HEADLIGHT_LEFT -> listOf(f.left)
            CarSpot.HEADLIGHT_RIGHT -> listOf(f.right)
            CarSpot.HEADLIGHTS -> listOf(f.left, f.right)
            CarSpot.FRONT_FOGS -> listOf(Offset(f.left.x + (mid - f.left.x) * 0.1f, lower), Offset(f.right.x + (mid - f.right.x) * 0.1f, lower))
            CarSpot.FRONT_SENSORS -> listOf(Offset(mid, 0.12f))
            else -> emptyList()
        }
    }

    /** The part of the car app's warning [code] (0x3A), or null when it isn't about one part. */
    fun spotOf(code: Int): CarSpot? = when (code) {
        0x29, 0x2A, 0x66, 0x6E -> CarSpot.HEADLIGHTS
        0x2B -> CarSpot.REAR_LIGHTS
        0x61 -> CarSpot.THIRD_BRAKE
        0x83 -> CarSpot.REAR_LIGHT_RIGHT
        0x84 -> CarSpot.REAR_LIGHT_LEFT
        0x2C -> CarSpot.REAR_FOGS
        0x2D -> CarSpot.INDICATORS
        0x2E -> CarSpot.REVERSING
        0x2F -> CarSpot.SIDELIGHTS
        0x0B, 0x0C, 0x0D, 0x0E, 0x76, 0x77, 0x64, 0x65, 0x67 -> CarSpot.ENGINE
        0x13, 0x23, 0x79, 0x85, 0x86, 0x5B -> CarSpot.EXHAUST
        0x0F, 0x1A, 0x1B, 0x1E, 0x1F, 0x4A -> CarSpot.BRAKES
        0x1C, 0x6F -> CarSpot.PARKING_BRAKE
        0x14, 0x15 -> CarSpot.SUSPENSION
        0x16, 0x60 -> CarSpot.STEERING
        0x20, 0x21 -> CarSpot.GEARBOX
        0x22, 0x24, 0x5F -> CarSpot.DASHBOARD
        0x5A -> CarSpot.AIRBAGS
        0x27 -> CarSpot.BATTERY
        0x6D, 0x70 -> CarSpot.FRONT_SENSORS
        0x25, 0x26, 0x78 -> CarSpot.REAR_SENSORS
        0x11 -> CarSpot.DOORS
        0x53 -> CarSpot.BOOT
        0x54 -> CarSpot.FUEL_FLAP
        0x55 -> CarSpot.DOOR_RR
        0x56 -> CarSpot.DOOR_RL
        0x57 -> CarSpot.DOOR_FR
        0x58 -> CarSpot.DOOR_FL
        // Punctures and low pressures, tyre by tyre (the car app's own order).
        0x7F, 0x7D, 0x71 -> CarSpot.TYRE_RL
        0x80, 0x7E, 0x72 -> CarSpot.TYRE_RR
        0x81, 0x7C, 0x73 -> CarSpot.TYRE_FR
        0x82, 0x7B, 0x74 -> CarSpot.TYRE_FL
        0x12, 0x28, 0x39, 0x38, 0x37, 0x5C, 0x6A, 0x75 -> CarSpot.TYRES
        0x33 -> CarSpot.WASHER
        0x34 -> CarSpot.FUEL_FLAP
        else -> null
    }

    /** The part an engine fault code (OBD) is about, by its system. */
    fun spotOfCode(code: String): CarSpot? {
        val c = code.trim().uppercase()
        if (c.length < 5) return null
        return when (c[0]) {
            'P' -> when {
                // Emissions: catalyst, EGR, particle filter and its additive.
                c.startsWith("P04") || c.startsWith("P20") || c.startsWith("P24") || c.startsWith("P1") && c[2] == '4' -> CarSpot.EXHAUST
                c.startsWith("P07") || c.startsWith("P08") || c.startsWith("P09") || c.startsWith("P27") || c.startsWith("P28") -> CarSpot.GEARBOX
                c.startsWith("P056") || c.startsWith("P0A") -> CarSpot.BATTERY
                else -> CarSpot.ENGINE
            }
            'C' -> CarSpot.BRAKES
            'B' -> if (c.startsWith("B00")) CarSpot.AIRBAGS else CarSpot.DASHBOARD
            'U' -> CarSpot.DASHBOARD
            else -> null
        }
    }
}
