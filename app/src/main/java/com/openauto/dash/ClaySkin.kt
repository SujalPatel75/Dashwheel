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
 * Clay skin (2026-10-13 car-free trends): Claymorphism: soft pastel shapes you could squeeze, big round keys, a puffy 3D turn arrow.
 * Mockup: D:/dashwheel-design/v17-no-car/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val ClayPalette = DashPalette(
    Background = Color(0xFF1E1933), Bar = Color.Transparent, Card = Color(0xFF2A2347), CardHi = Color(0x22A48CFF),
    Accent = Color(0xFFA48CFF), Secondary = Color(0xFFFF9A80), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFFA49CC4), TextPrimary = Color(0xFFEFEAFF), TextSecondary = Color(0xFFCFC6EE),
    Accent2 = Color(0xFFFFC266), Line = Color(0x22A48CFF), Glow = 0.2f, Bare = true,
    BackgroundStops = listOf(Color(0xFF2A2147), Color(0xFF1E1933), Color(0xFF2B1F33)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Bold,
    Skin = DashSkin.CLAY
)

internal val ClayLightPalette = DashPalette(
    Background = Color(0xFFECE6FF), Bar = Color.Transparent, Card = Color(0xFFF4EFFF), CardHi = Color(0x147B5CFF),
    Accent = Color(0xFF6A4BE0), Secondary = Color(0xFFE0603F), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF6E6590), TextPrimary = Color(0xFF3A2E5C), TextSecondary = Color(0xFF54477E),
    Accent2 = Color(0xFFC98400), Line = Color(0x227B5CFF), Glow = 0f, Bare = true, Light = true,
    BackgroundStops = listOf(Color(0xFFE3DBFF), Color(0xFFFFE0D4), Color(0xFFFFEEC7)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Bold,
    Skin = DashSkin.CLAY
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val ClayShapes = skinShapes(44, 40, 30)

/** The Home Clay was drawn around (SkinLayouts.kt). */
internal val ClayLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun ClayTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun ClayTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun clayBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun ClayWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.clayFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.clayStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
