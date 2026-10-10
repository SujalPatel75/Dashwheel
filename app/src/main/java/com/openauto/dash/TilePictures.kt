package com.openauto.dash

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** What the templates screen settled on (TemplateShowroom.kt): the template, the pages it lays out, and whether those are replaced (else only the empty ones among them are filled). */
internal data class TemplateChoice(val template: DashTemplate, val pages: Set<Int>, val replace: Boolean)

/** One tile as a block in its category's colour: the small pictures of a dashboard (template cards, the Dashboards overview, Moments). */
internal fun DrawScope.drawTile(item: DashboardItem, origin: Offset, pw: Float, ph: Float, colors: TileColors) {
    val cw = pw / GRID_COLS
    val ch = ph / GRID_ROWS
    val inset = 1f
    val color = when (item) {
        is DashboardItem.BuiltinWidget -> colors.of(item.kind.category)
        else -> colors.dock
    }
    drawRoundRect(
        color,
        Offset(origin.x + item.x * cw + inset, origin.y + item.y * ch + inset),
        Size((item.w * cw - 2 * inset).coerceAtLeast(1f), (item.h * ch - 2 * inset).coerceAtLeast(1f)),
        CornerRadius(2f)
    )
}

internal class TileColors(
    val navigation: Color,
    val driving: Color,
    val vehicle: Color,
    val info: Color,
    val apps: Color,
    val dock: Color
) {
    fun of(category: WidgetCategory) = when (category) {
        WidgetCategory.NAVIGATION -> navigation
        WidgetCategory.DRIVING -> driving
        WidgetCategory.VEHICLE -> vehicle
        WidgetCategory.INFO -> info
        WidgetCategory.APPS -> apps
    }
}

/**
 * Fixed colours rather than the theme's: several themes share one hue across
 * accent / speed / warning, and the preview needs the categories apart.
 */
@Composable
internal fun categoryColors() = TileColors(
    navigation = Color(0xFF3B82F6),
    driving = Color(0xFF14B8A6),
    vehicle = Color(0xFFE0694A),
    info = DashColors.TextSecondary.copy(alpha = 0.7f),
    apps = Color(0xFF8B5CF6),
    dock = DashColors.Muted.copy(alpha = 0.6f)
)
