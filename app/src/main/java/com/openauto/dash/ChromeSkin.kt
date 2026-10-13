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
 * Chrome skin (2026-10-13 car-free trends): Y2K chrome: liquid metal lettering, holographic chips, a metal drop that moves with the music.
 * Mockup: D:/dashwheel-design/v17-no-car/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val ChromePalette = DashPalette(
    Background = Color(0xFF050506), Bar = Color.Transparent, Card = Color(0xFF14141A), CardHi = Color(0x1AFFFFFF),
    Accent = Color(0xFFC9CCD6), Secondary = Color(0xFF9AD8FF), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFF9AA0B0), TextPrimary = Color(0xFFE8E8EE), TextSecondary = Color(0xFFB8BDCC),
    Accent2 = Color(0xFFFF9AD5), Line = Color(0x2EFFFFFF), Glow = 0.5f, Bare = true,
    BackgroundStops = listOf(Color(0xFF1B1B24), Color(0xFF0B0B0F), Color(0xFF050506)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Black,
    Skin = DashSkin.CHROME
)

internal val ChromeLightPalette = DashPalette(
    Background = Color(0xFFE9EAEF), Bar = Color.Transparent, Card = Color(0xFFFFFFFF), CardHi = Color(0x14000000),
    Accent = Color(0xFF4A4E5C), Secondary = Color(0xFF2F7FB0), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF62667A), TextPrimary = Color(0xFF121218), TextSecondary = Color(0xFF3A3D4A),
    Accent2 = Color(0xFFB0407F), Line = Color(0x26000000), Glow = 0f, Bare = true, Light = true,
    BackgroundStops = listOf(Color(0xFFF6F7FA), Color(0xFFE9EAEF), Color(0xFFD8DAE2)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Black,
    Skin = DashSkin.CHROME
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val ChromeShapes = skinShapes(30, 24, 30)

/** The Home Chrome was drawn around (SkinLayouts.kt). */
internal val ChromeLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun ChromeTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun ChromeTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun chromeBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun ChromeWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.chromeFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.chromeStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
