package com.openauto.dash

import com.openauto.dash.BuiltinKind.CLOCK
import com.openauto.dash.BuiltinKind.FUEL_PRICES
import com.openauto.dash.BuiltinKind.MEDIA
import com.openauto.dash.BuiltinKind.NAVIGATION
import com.openauto.dash.BuiltinKind.NAVMAP
import com.openauto.dash.BuiltinKind.RANGE
import com.openauto.dash.BuiltinKind.SPEED_HUD
import com.openauto.dash.BuiltinKind.TELEMETRY
import com.openauto.dash.BuiltinKind.TRIP
import com.openauto.dash.BuiltinKind.TYRES
import com.openauto.dash.BuiltinKind.WEATHER

/*
 * Each whole-design look comes with the Home it was drawn around: choosing it
 * (Settings, Look) puts those widgets on Home, with Undo, unless the driver
 * turned that off ([DashThemeStore.loadLookLayout]). Colour-only looks have
 * none and leave Home as it is. Only Home changes; the other dashboards stay
 * the driver's.
 */

/**
 * The Home a look was drawn around. [home] lists its widgets, most important
 * first, for [TemplatePlacer] to fit any screen (upright, beside a Maps dock,
 * without an OBD adapter). [wide] is the look's own arrangement on the
 * 12 x 7 grid of a wide screen, as in its mockup; it is used as it is when
 * the screen is wide, nothing is docked beside the pages and every widget in
 * it has something to show, else [home] is placed instead.
 */
internal class SkinLayout(val home: TemplatePage, val wide: List<DashboardItem>? = null)

/** The look's Home, or null for a look that leaves Home alone. */
internal fun skinLayout(mode: DashThemeMode): SkinLayout? = when (mode) {
    DashThemeMode.LIQUID -> LiquidLayout
    DashThemeMode.EXPRESSIVE -> ExpressiveLayout
    DashThemeMode.KINETIC -> KineticLayout
    DashThemeMode.GLYPH -> GlyphLayout
    DashThemeMode.WINDSHIELD -> WindshieldLayout
    DashThemeMode.TERRAIN -> TerrainLayout
    DashThemeMode.AMBIENT -> AmbientLayout
    DashThemeMode.BRUTAL -> BrutalLayout
    DashThemeMode.CLAY -> ClayLayout
    DashThemeMode.CHROME -> ChromeLayout
    DashThemeMode.SPATIAL -> SpatialLayout
    DashThemeMode.PAPER -> PaperLayout
    // The earlier whole-design looks: their main widgets, placed for the screen.
    DashThemeMode.ORBIT -> SkinLayout(TemplatePage(listOf(MEDIA, SPEED_HUD, CLOCK, WEATHER), dock = true))
    DashThemeMode.COCKPIT -> SkinLayout(TemplatePage(listOf(TELEMETRY, SPEED_HUD, MEDIA, CLOCK), dock = true))
    DashThemeMode.HORIZON -> SkinLayout(TemplatePage(listOf(SPEED_HUD, NAVIGATION, CLOCK)))
    DashThemeMode.TAPE_DECK -> SkinLayout(TemplatePage(listOf(MEDIA, SPEED_HUD, CLOCK, WEATHER), dock = true))
    DashThemeMode.FUTURISTIC -> SkinLayout(TemplatePage(listOf(SPEED_HUD, TELEMETRY, NAVIGATION, MEDIA), dock = true))
    DashThemeMode.SIMPLE -> SkinLayout(TemplatePage(listOf(NAVIGATION, MEDIA, SPEED_HUD, RANGE)))
    DashThemeMode.MODERN -> SkinLayout(TemplatePage(listOf(NAVMAP, NAVIGATION, MEDIA, FUEL_PRICES), dock = true))
    DashThemeMode.MOTORSPORT -> SkinLayout(TemplatePage(listOf(SPEED_HUD, TELEMETRY, TRIP, NAVIGATION, TYRES)))
    DashThemeMode.ANIME -> SkinLayout(TemplatePage(listOf(NAVIGATION, MEDIA, WEATHER, SPEED_HUD), dock = true))
    DashThemeMode.RACING -> SkinLayout(TemplatePage(listOf(SPEED_HUD, NAVMAP, NAVIGATION, RANGE, MEDIA)))
    DashThemeMode.PANORAMA -> SkinLayout(TemplatePage(listOf(SPEED_HUD, RANGE, NAVIGATION, MEDIA), dock = true))
    else -> null
}

/**
 * The tiles [layout] puts on Home for [screen]: its wide arrangement where it
 * fits (see [SkinLayout.wide]), else its widgets placed for the screen.
 */
internal fun skinHome(layout: SkinLayout, screen: TemplateScreen): List<DashboardItem> {
    val wide = layout.wide
    if (wide != null && !ScreenShape.vertical && !screen.mapsDocked && fitsGrid(wide)) {
        val allowed = TemplatePlacer.kindsFor(layout.home, screen).toSet()
        val kinds = wide.filterIsInstance<DashboardItem.BuiltinWidget>().map { it.kind }
        if (kinds.all { it in allowed }) {
            // The look's dock row takes the driver's own apps.
            return wide.map { if (it is DashboardItem.LaunchBar) it.copy(packages = screen.dockApps) else it }
                .filter { it !is DashboardItem.LaunchBar || it.packages.isNotEmpty() }
        }
    }
    return TemplatePlacer.page(layout.home, screen)
}

/** Every tile inside the grid and none overlapping. */
internal fun fitsGrid(items: List<DashboardItem>): Boolean {
    val taken = HashSet<Int>()
    for (it in items) {
        if (it.x < 0 || it.y < 0 || it.w < 1 || it.h < 1 || it.x + it.w > GRID_COLS || it.y + it.h > GRID_ROWS) return false
        for (cx in it.x until it.x + it.w) for (cy in it.y until it.y + it.h) {
            if (!taken.add(cy * GRID_COLS + cx)) return false
        }
    }
    return true
}
