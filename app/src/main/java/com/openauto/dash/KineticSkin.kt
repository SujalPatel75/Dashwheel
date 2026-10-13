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
 * Kinetic skin (2026-10-13 trends): Kinetic type and the zero layer: the one thing that matters now, in words that move.
 * Mockup: D:/dashwheel-design/v16-trends/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val KineticPalette = DashPalette(
    Background = Color(0xFF06070B), Bar = Color.Transparent, Card = Color(0xFF111218), CardHi = Color(0x14FFFFFF),
    Accent = Color(0xFFB48CFF), Secondary = Color(0xFF7AB8FF), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFF8D93A3), TextPrimary = Color(0xFFF4F2EE), TextSecondary = Color(0xFFC9CCD6),
    Accent2 = Color(0xFFFFC46B), Line = Color(0x1AFFFFFF), Glow = 0.4f, Bare = true,
    BackgroundStops = listOf(Color(0xFF0A0B11), Color(0xFF06070B), Color(0xFF040408)),
    Font = DashFont.CONDENSED,
    HeroWeight = FontWeight.ExtraBold,
    Skin = DashSkin.KINETIC
)

internal val KineticLightPalette = DashPalette(
    Background = Color(0xFFF4F2EE), Bar = Color.Transparent, Card = Color(0xFFFFFFFF), CardHi = Color(0x10000000),
    Accent = Color(0xFF6A3FD0), Secondary = Color(0xFF2F6FC0), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF6C7283), TextPrimary = Color(0xFF06070B), TextSecondary = Color(0xFF3A3F4C),
    Accent2 = Color(0xFFB86E0E), Line = Color(0x1A000000), Glow = 0f, Bare = true, Light = true,
    BackgroundStops = listOf(Color(0xFFF8F6F2), Color(0xFFF4F2EE), Color(0xFFEAE7E0)),
    Font = DashFont.CONDENSED,
    HeroWeight = FontWeight.ExtraBold,
    Skin = DashSkin.KINETIC
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val KineticShapes = skinShapes(26, 24, 23)

/** The Home Kinetic was drawn around (SkinLayouts.kt). */
internal val KineticLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun KineticTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun KineticTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun kineticBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun KineticWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.kineticFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.kineticStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
