package com.openauto.dash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight

/*
 * Spatial skin (2026-10-13 car-free trends): Spatial windows after visionOS: tilted glass at different depths over a 3D night city, the route glowing.
 * Mockup: D:/dashwheel-design/v17-no-car/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val SpatialPalette = DashPalette(
    Background = Color(0xFF0A0D16), Bar = Color.Transparent, Card = Color(0xFF222838), CardHi = Color(0x2EFFFFFF),
    Accent = Color(0xFF5AD1FF), Secondary = Color(0xFF8F6BFF), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFFA2AABF), TextPrimary = Color(0xFFFFFFFF), TextSecondary = Color(0xFFCCD3E3),
    Accent2 = Color(0xFF8F6BFF), Line = Color(0x47FFFFFF), Glow = 0.4f, Bare = true,
    BackgroundStops = listOf(Color(0xFF121828), Color(0xFF0A0D16), Color(0xFF06080E)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Light,
    Skin = DashSkin.SPATIAL
)

internal val SpatialLightPalette = DashPalette(
    Background = Color(0xFFE8EDF5), Bar = Color.Transparent, Card = Color(0xFFFFFFFF), CardHi = Color(0x140D1220),
    Accent = Color(0xFF0A8FC4), Secondary = Color(0xFF6A4BE0), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF5A6378), TextPrimary = Color(0xFF0D1220), TextSecondary = Color(0xFF384055),
    Accent2 = Color(0xFF6A4BE0), Line = Color(0x260D1220), Glow = 0.1f, Bare = true, Light = true,
    BackgroundStops = listOf(Color(0xFFF4F7FC), Color(0xFFE8EDF5), Color(0xFFDCE3EE)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Light,
    Skin = DashSkin.SPATIAL
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val SpatialShapes = skinShapes(36, 32, 24)

/** The Home Spatial was drawn around (SkinLayouts.kt). */
internal val SpatialLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun SpatialTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun SpatialTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun spatialBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun SpatialWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.spatialFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.spatialStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
