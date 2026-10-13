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
 * Paper skin (2026-10-13 car-free trends): Calm tech, e-paper: ink on grey, a dithered map, plain facts, nothing that moves.
 * Mockup: D:/dashwheel-design/v17-no-car/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val PaperPalette = DashPalette(
    Background = Color(0xFF141414), Bar = Color.Transparent, Card = Color(0xFF1E1E1E), CardHi = Color(0x14CFD0C8),
    Accent = Color(0xFFCFD0C8), Secondary = Color(0xFFA9AAA2), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFF8E8F88), TextPrimary = Color(0xFFCFD0C8), TextSecondary = Color(0xFFAEAFA7),
    Accent2 = Color(0xFFCFD0C8), Line = Color(0x66CFD0C8), Glow = 0f, Bare = true,
    BackgroundStops = listOf(Color(0xFF141414), Color(0xFF141414)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Bold,
    Skin = DashSkin.PAPER
)

internal val PaperLightPalette = DashPalette(
    Background = Color(0xFFCFD0C8), Bar = Color.Transparent, Card = Color(0xFFDCDDD5), CardHi = Color(0x14141414),
    Accent = Color(0xFF141414), Secondary = Color(0xFF444444), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF55564F), TextPrimary = Color(0xFF141414), TextSecondary = Color(0xFF33342F),
    Accent2 = Color(0xFF141414), Line = Color(0xFF141414), Glow = 0f, Bare = true, Light = true,
    // The standard day amber is too pale on this grey paper: a deeper one, still amber.
    Tacho = Color(0xFF9A5B00),
    BackgroundStops = listOf(Color(0xFFCFD0C8), Color(0xFFCFD0C8)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Bold,
    Skin = DashSkin.PAPER
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val PaperShapes = skinShapes(6, 4, 4)

/** The Home Paper was drawn around (SkinLayouts.kt). */
internal val PaperLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun PaperTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun PaperTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun paperBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun PaperWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.paperFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.paperStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
