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
 * Liquid skin (2026-10-13 trends): Liquid Glass (iOS 26, CarPlay Ultra): lens-like glass over a bright wallpaper, your car turning in the middle.
 * Mockup: D:/dashwheel-design/v16-trends/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val LiquidPalette = DashPalette(
    Background = Color(0xFF140F33), Bar = Color.Transparent, Card = Color(0xFF2A2160), CardHi = Color(0x33FFFFFF),
    Accent = Color(0xFF9FD0FF), Secondary = Color(0xFFFF5EA0), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFFB9B3E0), TextPrimary = Color(0xFFFFFFFF), TextSecondary = Color(0xFFD9D4FF),
    Accent2 = Color(0xFFFF5EA0), Line = Color(0x55FFFFFF), Glow = 0.5f, Bare = true,
    BackgroundStops = listOf(Color(0xFF2A1A6E), Color(0xFF1B1247), Color(0xFF120C2E)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.SemiBold,
    Skin = DashSkin.LIQUID
)

internal val LiquidLightPalette = DashPalette(
    Background = Color(0xFFEFF2FF), Bar = Color.Transparent, Card = Color(0xFFFFFFFF), CardHi = Color(0x14304BFF),
    Accent = Color(0xFF3A4BD8), Secondary = Color(0xFFD8306E), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF5E6390), TextPrimary = Color(0xFF161A3A), TextSecondary = Color(0xFF444A78),
    Accent2 = Color(0xFFD8306E), Line = Color(0x33304BFF), Glow = 0.1f, Bare = true, Light = true,
    BackgroundStops = listOf(Color(0xFFFFF1F7), Color(0xFFEFF2FF), Color(0xFFE2F4FF)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.SemiBold,
    Skin = DashSkin.LIQUID
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val LiquidShapes = skinShapes(34, 30, 22)

/** The Home Liquid was drawn around (SkinLayouts.kt). */
internal val LiquidLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun LiquidTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun LiquidTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun liquidBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun LiquidWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.liquidFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.liquidStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
