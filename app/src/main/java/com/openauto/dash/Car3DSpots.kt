package com.openauto.dash

/*
 * Where each part of the car ([CarSpot]) is on the driver's 3D car (Car3D.kt)
 * and how the camera looks at it. The model is a glTF with the nose to +x, up
 * +y and the car's right to +z, its parts named the way tools/mycar/bake_car.py
 * documents (Lamp_tail_l, Wheel_fr, Hinge_bonnet...). A part with a node of its
 * own lights that node; any other is a point on the car's box. Pure, so it is
 * unit-tested.
 */

/**
 * A camera around a point: [yaw] degrees from the nose toward the car's right
 * (0 in front, 90 on the right, 180 behind, 270 on the left), [pitch] degrees
 * up, [zoom] the distance as a share of the car's length (the whole car:
 * however far it takes to fit).
 */
internal data class CarCamera(val yaw: Float, val pitch: Float, val zoom: Float)

/**
 * One part on the 3D car: the nodes that light up ([glow], names or their
 * beginnings, [strength] of the light: a lamp full, a door or a wheel a tint),
 * the hinges that open to show it ([open]), the point the camera looks at when
 * none of the nodes is there ([at]: along the car 0 nose .. 1 tail, across -1
 * left .. 1 right, up 0 ground .. 1 roof) and the camera.
 */
internal data class Spot3D(
    val glow: List<String>,
    val open: List<String>,
    val at: Triple<Float, Float, Float>,
    val camera: CarCamera,
    val strength: Float = 1f
)

internal object Car3DSpots {
    /** The whole car, from the front right, a little from above. */
    val WHOLE = CarCamera(35f, 14f, 1f)

    private fun spot(
        along: Float, across: Float, up: Float, yaw: Float, pitch: Float, zoom: Float,
        glow: List<String> = emptyList(), open: List<String> = emptyList(), strength: Float = 1f
    ) = Spot3D(glow, open, Triple(along, across, up), CarCamera(yaw, pitch, zoom), strength)

    private const val BONNET = "Hinge_bonnet"
    private const val TAILGATE = "Hinge_tailgate"

    /**
     * The 3D place of [spot]. [driverOnRight]: the steering wheel on the car's
     * right, for the steering and the dashboard.
     */
    fun of(spot: CarSpot, driverOnRight: Boolean = false): Spot3D {
        val driver = if (driverOnRight) 0.45f else -0.45f
        return when (spot) {
            CarSpot.HEADLIGHT_LEFT -> spot(0.02f, -0.75f, 0.55f, 330f, 10f, 0.42f, listOf("Lamp_head_l"))
            CarSpot.HEADLIGHT_RIGHT -> spot(0.02f, 0.75f, 0.55f, 30f, 10f, 0.42f, listOf("Lamp_head_r"))
            CarSpot.HEADLIGHTS -> spot(0.02f, 0f, 0.55f, 15f, 12f, 0.75f, listOf("Lamp_head_l", "Lamp_head_r"))
            CarSpot.FRONT_FOGS -> spot(0.01f, 0f, 0.25f, 12f, 6f, 0.6f)
            CarSpot.REAR_LIGHT_LEFT -> spot(0.98f, -0.8f, 0.62f, 220f, 12f, 0.42f, listOf("Lamp_tail_l"))
            CarSpot.REAR_LIGHT_RIGHT -> spot(0.98f, 0.8f, 0.62f, 140f, 12f, 0.42f, listOf("Lamp_tail_r"))
            CarSpot.REAR_LIGHTS -> spot(0.98f, 0f, 0.62f, 195f, 12f, 0.75f, listOf("Lamp_tail_l", "Lamp_tail_r"))
            CarSpot.THIRD_BRAKE -> spot(0.93f, 0f, 0.95f, 190f, 18f, 0.55f)
            CarSpot.REAR_FOGS -> spot(0.99f, 0f, 0.3f, 195f, 6f, 0.65f)
            CarSpot.REVERSING -> spot(0.98f, 0f, 0.6f, 195f, 10f, 0.75f, listOf("Lamp_tail_l", "Lamp_tail_r"))
            CarSpot.INDICATORS, CarSpot.SIDELIGHTS ->
                spot(0.5f, 0f, 0.5f, 35f, 14f, 1.3f, listOf("Lamp_head_l", "Lamp_head_r", "Lamp_tail_l", "Lamp_tail_r"))
            CarSpot.ENGINE -> spot(0.12f, 0f, 0.7f, 30f, 32f, 0.8f, open = listOf(BONNET))
            CarSpot.BATTERY -> spot(0.12f, -0.5f, 0.7f, 330f, 32f, 0.7f, open = listOf(BONNET))
            CarSpot.WASHER -> spot(0.1f, 0.55f, 0.7f, 30f, 32f, 0.7f, open = listOf(BONNET))
            CarSpot.GEARBOX -> spot(0.15f, -0.3f, 0.5f, 330f, 30f, 0.75f, open = listOf(BONNET))
            CarSpot.EXHAUST -> spot(1f, -0.5f, 0.12f, 210f, 4f, 0.5f)
            CarSpot.BRAKES, CarSpot.SUSPENSION, CarSpot.TYRES -> spot(0.5f, 0f, 0.3f, 35f, 10f, 1.3f, listOf("Wheel_"), strength = 0.35f)
            CarSpot.PARKING_BRAKE -> spot(0.78f, 0f, 0.3f, 150f, 10f, 1f, listOf("Wheel_rl", "Wheel_rr"), strength = 0.35f)
            CarSpot.TYRE_FL -> spot(0.22f, -1f, 0.15f, 300f, 8f, 0.5f, listOf("Wheel_fl"), strength = 0.35f)
            CarSpot.TYRE_FR -> spot(0.22f, 1f, 0.15f, 60f, 8f, 0.5f, listOf("Wheel_fr"), strength = 0.35f)
            CarSpot.TYRE_RL -> spot(0.78f, -1f, 0.15f, 240f, 8f, 0.5f, listOf("Wheel_rl"), strength = 0.35f)
            CarSpot.TYRE_RR -> spot(0.78f, 1f, 0.15f, 120f, 8f, 0.5f, listOf("Wheel_rr"), strength = 0.35f)
            // Inside: through the windscreen, from above the bonnet.
            CarSpot.STEERING -> spot(0.36f, driver, 0.7f, 15f, 30f, 0.7f)
            CarSpot.DASHBOARD -> spot(0.33f, 0f, 0.7f, 15f, 30f, 0.75f)
            CarSpot.AIRBAGS -> spot(0.36f, 0f, 0.7f, 15f, 30f, 0.75f)
            CarSpot.DOOR_FL -> spot(0.4f, -1f, 0.5f, 300f, 14f, 0.8f, listOf("Door_fl"), listOf("Hinge_door_fl"), 0.25f)
            CarSpot.DOOR_FR -> spot(0.4f, 1f, 0.5f, 60f, 14f, 0.8f, listOf("Door_fr"), listOf("Hinge_door_fr"), 0.25f)
            CarSpot.DOOR_RL -> spot(0.6f, -1f, 0.5f, 250f, 14f, 0.8f, listOf("Door_rl"), listOf("Hinge_door_rl"), 0.25f)
            CarSpot.DOOR_RR -> spot(0.6f, 1f, 0.5f, 110f, 14f, 0.8f, listOf("Door_rr"), listOf("Hinge_door_rr"), 0.25f)
            CarSpot.DOORS -> spot(0.5f, 0f, 0.5f, 35f, 24f, 1.3f, listOf("Door_"), listOf("Hinge_door_"), 0.25f)
            CarSpot.BOOT -> spot(0.95f, 0f, 0.7f, 200f, 22f, 0.85f, listOf("Tailgate"), listOf(TAILGATE), 0.25f)
            // On the right, behind the rear door, on most cars of this size (as on CarProblemMap's).
            CarSpot.FUEL_FLAP -> spot(0.78f, 1f, 0.6f, 110f, 10f, 0.5f)
            CarSpot.FRONT_SENSORS -> spot(0f, 0f, 0.3f, 10f, 8f, 0.6f)
            CarSpot.REAR_SENSORS -> spot(1f, 0f, 0.3f, 190f, 8f, 0.6f)
        }
    }

    /** Whether node [name] is one of [glow]'s: a whole name, or a beginning ending in "_" (Wheel_, Door_). */
    fun matches(name: String, glow: List<String>): Boolean = glow.any { g ->
        if (g.endsWith("_")) name.startsWith(g) else name == g || name.startsWith("$g.")
    }

    /**
     * The opening angle (degrees) of hinge [name] about its own axis, by the
     * rig's convention: doors swing out about the vertical, the tailgate up,
     * the bonnet up. Null for anything else.
     */
    fun hingeAngle(name: String): Float? = when {
        name == "Hinge_door_fl" || name == "Hinge_door_rl" -> -62f
        name == "Hinge_door_fr" || name == "Hinge_door_rr" -> 62f
        name == TAILGATE -> 80f
        name == BONNET -> -48f
        else -> null
    }
}
