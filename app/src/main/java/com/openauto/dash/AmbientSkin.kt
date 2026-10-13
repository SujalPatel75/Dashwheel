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
 * Ambient skin (2026-10-13 car-free trends): Light as information: a field of light with few words, a warm glow from the side of the next turn.
 * Mockup: D:/dashwheel-design/v17-no-car/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val AmbientPalette = DashPalette(
    Background = Color(0xFF05070F), Bar = Color.Transparent, Card = Color(0xFF10141F), CardHi = Color(0x14FFFFFF),
    Accent = Color(0xFFFFB347), Secondary = Color(0xFF3B6CFF), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFF9AA3BD), TextPrimary = Color(0xFFF3F6FF), TextSecondary = Color(0xFFC3CAE0),
    Accent2 = Color(0xFF8F7BFF), Line = Color(0x1AFFFFFF), Glow = 0.6f, Bare = true,
    BackgroundStops = listOf(Color(0xFF0A0D1A), Color(0xFF05070F), Color(0xFF030409)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.ExtraLight,
    Skin = DashSkin.AMBIENT
)

internal val AmbientLightPalette = DashPalette(
    Background = Color(0xFFF3F4FA), Bar = Color.Transparent, Card = Color(0xFFFFFFFF), CardHi = Color(0x10101C40),
    Accent = Color(0xFFC2620F), Secondary = Color(0xFF2F55D4), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF5B6380), TextPrimary = Color(0xFF10121C), TextSecondary = Color(0xFF3C4258),
    Accent2 = Color(0xFF6A4FD8), Line = Color(0x1A10121C), Glow = 0.1f, Bare = true, Light = true,
    BackgroundStops = listOf(Color(0xFFFFF6EC), Color(0xFFF3F4FA), Color(0xFFE9ECFA)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.ExtraLight,
    Skin = DashSkin.AMBIENT
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val AmbientShapes = skinShapes(28, 26, 20)

/** The Home Ambient was drawn around (SkinLayouts.kt). */
internal val AmbientLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun AmbientTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun AmbientTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun ambientBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun AmbientWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.ambientFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.ambientStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
