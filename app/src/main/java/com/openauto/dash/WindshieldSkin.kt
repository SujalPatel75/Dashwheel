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
 * Windshield skin (2026-10-13 trends): Panoramic head-up band (BMW Neue Klasse) over a drawn road, the turn arrows on the lane.
 * Mockup: D:/dashwheel-design/v16-trends/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val WindshieldPalette = DashPalette(
    Background = Color(0xFF07070C), Bar = Color.Transparent, Card = Color(0xFF0A1022), CardHi = Color(0x2246C8FF),
    Accent = Color(0xFF7FE3FF), Secondary = Color(0xFF46C8FF), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFF8AA0BC), TextPrimary = Color(0xFFEEF6FF), TextSecondary = Color(0xFFA9BDD6),
    Accent2 = Color(0xFFFFD27A), Line = Color(0x478CDCFF), Glow = 0.6f, Bare = true,
    BackgroundStops = listOf(Color(0xFF04050D), Color(0xFF0B0E2A), Color(0xFF1A1022)),
    Font = DashFont.CONDENSED,
    HeroWeight = FontWeight.SemiBold,
    Skin = DashSkin.WINDSHIELD
)

internal val WindshieldLightPalette = DashPalette(
    Background = Color(0xFFDFE7EF), Bar = Color.Transparent, Card = Color(0xFFF7FAFD), CardHi = Color(0x14006A99),
    Accent = Color(0xFF00739E), Secondary = Color(0xFF0A8FC4), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF566B82), TextPrimary = Color(0xFF0C1726), TextSecondary = Color(0xFF384B62),
    Accent2 = Color(0xFFB8730A), Line = Color(0x33006A99), Glow = 0.1f, Bare = true, Light = true,
    BackgroundStops = listOf(Color(0xFFCFE2F3), Color(0xFFE6EEF5), Color(0xFFB9C2CC)),
    Font = DashFont.CONDENSED,
    HeroWeight = FontWeight.SemiBold,
    Skin = DashSkin.WINDSHIELD
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val WindshieldShapes = skinShapes(24, 22, 12)

/** The Home Windshield was drawn around (SkinLayouts.kt). */
internal val WindshieldLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun WindshieldTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun WindshieldTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun windshieldBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun WindshieldWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.windshieldFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.windshieldStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
