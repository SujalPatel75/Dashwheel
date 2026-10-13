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
 * Terrain skin (2026-10-13 trends): Game-engine car view (RivianOS 2): your car on the contour map of where you are, problems pinned on it.
 * Mockup: D:/dashwheel-design/v16-trends/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val TerrainPalette = DashPalette(
    Background = Color(0xFF141611), Bar = Color.Transparent, Card = Color(0xFF1A1C15), CardHi = Color(0x22E8D6AA),
    Accent = Color(0xFFFFAD33), Secondary = Color(0xFF6FB2D8), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFFA59C84), TextPrimary = Color(0xFFF2ECDF), TextSecondary = Color(0xFFE5DCC4),
    Accent2 = Color(0xFFFFD27A), Line = Color(0x29E8D6AA), Glow = 0.3f, Bare = true,
    BackgroundStops = listOf(Color(0xFF1A1C15), Color(0xFF141611), Color(0xFF0D0E0A)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Light,
    Skin = DashSkin.TERRAIN
)

internal val TerrainLightPalette = DashPalette(
    Background = Color(0xFFEFE9DC), Bar = Color.Transparent, Card = Color(0xFFFBF8F1), CardHi = Color(0x14403A28),
    Accent = Color(0xFFB0600A), Secondary = Color(0xFF2F78A8), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF6D6650), TextPrimary = Color(0xFF1F1D16), TextSecondary = Color(0xFF4A4535),
    Accent2 = Color(0xFF8A6A1A), Line = Color(0x29403A28), Glow = 0f, Bare = true, Light = true,
    BackgroundStops = listOf(Color(0xFFF5F0E5), Color(0xFFEFE9DC), Color(0xFFE2DAC8)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Light,
    Skin = DashSkin.TERRAIN
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val TerrainShapes = skinShapes(22, 22, 16)

/** The Home Terrain was drawn around (SkinLayouts.kt). */
internal val TerrainLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun TerrainTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun TerrainTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun terrainBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun TerrainWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.terrainFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.terrainStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
