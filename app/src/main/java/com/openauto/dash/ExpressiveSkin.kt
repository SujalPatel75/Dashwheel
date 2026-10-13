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
 * Expressive skin (2026-10-13 trends): Material 3 Expressive (Android Auto): bold shapes, springy motion, colours from the album art.
 * Mockup: D:/dashwheel-design/v16-trends/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val ExpressivePalette = DashPalette(
    Background = Color(0xFF141A26), Bar = Color.Transparent, Card = Color(0xFF1F2738), CardHi = Color(0x22C3D6FF),
    Accent = Color(0xFFFF9C80), Secondary = Color(0xFFD5F26C), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFF9AA6BF), TextPrimary = Color(0xFFF1F4FB), TextSecondary = Color(0xFFB8C2D8),
    Accent2 = Color(0xFFC3D6FF), Line = Color(0x22C3D6FF), Glow = 0f, Bare = true,
    BackgroundStops = listOf(Color(0xFF1A2232), Color(0xFF141A26), Color(0xFF0F141E)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Black,
    Skin = DashSkin.EXPRESSIVE
)

internal val ExpressiveLightPalette = DashPalette(
    Background = Color(0xFFE8EDF6), Bar = Color.Transparent, Card = Color(0xFFFFFFFF), CardHi = Color(0x141D2433),
    Accent = Color(0xFFE0613F), Secondary = Color(0xFF6C8A00), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF5B6478), TextPrimary = Color(0xFF141A26), TextSecondary = Color(0xFF475069),
    Accent2 = Color(0xFF3F63C8), Line = Color(0x221D2433), Glow = 0f, Bare = true, Light = true,
    BackgroundStops = listOf(Color(0xFFEFF3FA), Color(0xFFE8EDF6), Color(0xFFDDE5F2)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Black,
    Skin = DashSkin.EXPRESSIVE
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val ExpressiveShapes = skinShapes(40, 36, 28)

/** The Home Expressive was drawn around (SkinLayouts.kt). */
internal val ExpressiveLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun ExpressiveTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun ExpressiveTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun expressiveBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun ExpressiveWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.expressiveFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.expressiveStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
