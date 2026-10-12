package com.openauto.dash

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The paint of the drawn car (the one from above on the radar, in the
 * reverse view and the parking radar pop-up), for a driver who finds the
 * theme's grey too bright on a dark screen or wants their own car's colour.
 * [THEME] keeps each screen's own grey.
 */
internal enum class CarPaint(val titleRes: Int, val color: Color?) {
    THEME(R.string.car_paint_theme, null),
    BLACK(R.string.car_paint_black, Color(0xFF17181A)),
    GRAPHITE(R.string.car_paint_graphite, Color(0xFF3A3D42)),
    SILVER(R.string.car_paint_silver, Color(0xFFA7ACB2)),
    WHITE(R.string.car_paint_white, Color(0xFFE8E9EB)),
    RED(R.string.car_paint_red, Color(0xFF8E1C20)),
    BLUE(R.string.car_paint_blue, Color(0xFF1E4A86)),
    GREEN(R.string.car_paint_green, Color(0xFF2D5A3C)),
    BEIGE(R.string.car_paint_beige, Color(0xFFB5A283));

    /** The car's colours with this paint: body, windows, outline; [THEME]: the screen's own. */
    fun paint(body: Color, glass: Color, edge: Color): Triple<Color, Color, Color> {
        val c = color ?: return Triple(body, glass, edge)
        val light = c.luminance() > 0.35f
        // Windows darker than a light body, lighter than a dark one; the outline set off the same way.
        return Triple(
            c,
            if (light) Color(0x66000000) else Color(0x4DF1EFE8),
            if (light) Color(0xFF55585C) else Color(0xFF9A9C9F)
        )
    }
}

internal object CarPaintStore {
    private const val PREFS = "car_paint"
    private const val KEY = "paint"

    private val _paint = MutableStateFlow(CarPaint.THEME)
    val paint: StateFlow<CarPaint> = _paint.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _paint.value = CarPaint.entries.firstOrNull { it.name == name } ?: CarPaint.THEME
    }

    fun set(context: Context, paint: CarPaint) {
        _paint.value = paint
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, paint.name).apply()
    }
}
