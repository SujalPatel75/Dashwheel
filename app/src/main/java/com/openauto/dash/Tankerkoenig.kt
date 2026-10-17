package com.openauto.dash

import org.json.JSONObject
import java.util.Locale
import kotlin.math.min

/**
 * Fuel prices in Germany from Tankerkönig (creativecommons.tankerkoenig.de),
 * which relays the federal MTS-K feed every station must report to. Free,
 * CC BY 4.0, with a key the driver gets for themselves and pastes under
 * Settings → Expert → Your API keys ([ApiService.TANKERKOENIG]). Asked
 * before the French feed when a key is set ([FuelPriceRepo]); outside
 * Germany it knows no station and the French feed takes over. Pure: the
 * address and the reading, unit-tested.
 */
object Tankerkoenig {
    const val SIGN_UP = "https://creativecommons.tankerkoenig.de/"
    private const val BASE = "https://creativecommons.tankerkoenig.de/json/list.php"
    /** The farthest the service looks, in km. */
    private const val MAX_RADIUS_KM = 25

    /** The stations within [FuelPrices.RADIUS_KM] of the car, every grade, nearest first. */
    fun url(lat: Double, lng: Double, key: String): String = String.format(
        Locale.US, "%s?lat=%.4f&lng=%.4f&rad=%d&sort=dist&type=all&apikey=%s",
        BASE, lat, lng, min(FuelPrices.RADIUS_KM, MAX_RADIUS_KM), key.trim()
    )

    /**
     * The stations in the answer, as [FuelStation]s: named by the service
     * itself (brand, else name), their Tankerkönig ids folded into the
     * station's number, closed ones left out (a driver wants a pump now).
     * Throws with the service's own message when it refused (a bad key).
     */
    fun parse(json: String): List<FuelStation> {
        val o = JSONObject(json)
        if (!o.optBoolean("ok", false)) error(o.optString("message").ifBlank { "Tankerkönig refused" })
        val stations = o.optJSONArray("stations") ?: return emptyList()
        return (0 until stations.length()).mapNotNull { i ->
            val s = stations.optJSONObject(i) ?: return@mapNotNull null
            if (!s.optBoolean("isOpen", true)) return@mapNotNull null
            val lat = s.optDouble("lat", Double.NaN)
            val lng = s.optDouble("lng", Double.NaN)
            if (lat.isNaN() || lng.isNaN()) return@mapNotNull null
            val prices = GRADES.mapNotNull { (field, grade) ->
                if (s.isNull(field)) null else s.optDouble(field).takeIf { it > 0 }?.let { grade to it }
            }.toMap()
            if (prices.isEmpty()) return@mapNotNull null
            val street = listOf(s.optString("street"), s.optString("houseNumber")).filter { it.isNotBlank() }.joinToString(" ")
            FuelStation(
                id = stationId(s.optString("id")),
                address = FuelPrices.tidy(street),
                town = FuelPrices.tidy(s.optString("place")),
                lat = lat, lng = lng,
                prices = prices,
                name = s.optString("brand").ifBlank { s.optString("name") }.trim()
            )
        }
    }

    /** Tankerkönig's grades by their field: diesel, Super E5 (SP95 here) and E10. */
    private val GRADES = listOf("diesel" to FuelGrade.GAZOLE, "e5" to FuelGrade.SP95, "e10" to FuelGrade.E10)

    /** A station's UUID folded into a number, so the same station keeps the same id between fetches. */
    internal fun stationId(uuid: String): Long =
        runCatching { java.util.UUID.fromString(uuid).mostSignificantBits }.getOrElse { uuid.hashCode().toLong() }
}
