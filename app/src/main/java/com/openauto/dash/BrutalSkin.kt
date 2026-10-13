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
 * Brutal skin (2026-10-13 car-free trends): Neo-brutalism: loud flat colours, thick black outlines, hard shadows, huge type for full sun.
 * Mockup: D:/dashwheel-design/v17-no-car/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val BrutalPalette = DashPalette(
    Background = Color(0xFF0B0B0B), Bar = Color.Transparent, Card = Color(0xFF1A1A1A), CardHi = Color(0x22FFE14D),
    Accent = Color(0xFFFFE14D), Secondary = Color(0xFFFF6B9A), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFFB5B2A8), TextPrimary = Color(0xFFF2EFE6), TextSecondary = Color(0xFFD6D2C6),
    Accent2 = Color(0xFF3D5AFE), Line = Color(0xFFF2EFE6), Glow = 0f, Bare = true,
    BackgroundStops = listOf(Color(0xFF0B0B0B), Color(0xFF0B0B0B)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Black,
    Skin = DashSkin.BRUTAL
)

internal val BrutalLightPalette = DashPalette(
    Background = Color(0xFFF2EFE6), Bar = Color.Transparent, Card = Color(0xFFFFFFFF), CardHi = Color(0x140B0B0B),
    Accent = Color(0xFF3D5AFE), Secondary = Color(0xFFE0457A), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF5A574F), TextPrimary = Color(0xFF0B0B0B), TextSecondary = Color(0xFF2E2C27),
    Accent2 = Color(0xFFFFE14D), Line = Color(0xFF0B0B0B), Glow = 0f, Bare = true, Light = true,
    BackgroundStops = listOf(Color(0xFFF2EFE6), Color(0xFFF2EFE6)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Black,
    Skin = DashSkin.BRUTAL
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val BrutalShapes = skinShapes(8, 6, 4)

/** The Home Brutal was drawn around (SkinLayouts.kt). */
internal val BrutalLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun BrutalTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun BrutalTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun brutalBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun BrutalWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.brutalFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.brutalStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
