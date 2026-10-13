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
 * Glyph skin (2026-10-13 trends): Dot-matrix industrial (Nothing, Teenage Engineering): mono labels, real keys, light strips that signal.
 * Mockup: D:/dashwheel-design/v16-trends/index.html (see SkinLayouts.kt for its Home).
 * PLACEHOLDER until the skin is drawn: standard tiles on the palette.
 */

internal val GlyphPalette = DashPalette(
    Background = Color(0xFF0D0D0D), Bar = Color.Transparent, Card = Color(0xFF181818), CardHi = Color(0x14FFFFFF),
    Accent = Color(0xFFD71921), Secondary = Color(0xFFEDEDEA), Critical = Color(0xFFFF5A5F), Good = Color(0xFF52E3A4),
    Muted = Color(0xFF8A8A84), TextPrimary = Color(0xFFEDEDEA), TextSecondary = Color(0xFFB5B5AF),
    Accent2 = Color(0xFFEDEDEA), Line = Color(0x22FFFFFF), Glow = 0.3f, Bare = true,
    BackgroundStops = listOf(Color(0xFF121212), Color(0xFF0D0D0D), Color(0xFF080808)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Bold,
    Skin = DashSkin.GLYPH
)

internal val GlyphLightPalette = DashPalette(
    Background = Color(0xFFE9E9E5), Bar = Color.Transparent, Card = Color(0xFFF6F6F2), CardHi = Color(0x10000000),
    Accent = Color(0xFFD71921), Secondary = Color(0xFF111111), Critical = Color(0xFFC62828), Good = Color(0xFF0E8A55),
    Muted = Color(0xFF77776F), TextPrimary = Color(0xFF111111), TextSecondary = Color(0xFF555555),
    Accent2 = Color(0xFF111111), Line = Color(0x33000000), Glow = 0f, Bare = true, Light = true,
    BackgroundStops = listOf(Color(0xFFEFEFEB), Color(0xFFE9E9E5), Color(0xFFDEDED9)),
    Font = DashFont.SANS,
    HeroWeight = FontWeight.Bold,
    Skin = DashSkin.GLYPH
)

/** The skin's corners (dialogs, menus, tiles being arranged). */
internal val GlyphShapes = skinShapes(22, 22, 12)

/** The Home Glyph was drawn around (SkinLayouts.kt). */
internal val GlyphLayout = SkinLayout(TemplatePage(listOf(BuiltinKind.NAVIGATION, BuiltinKind.SPEED_HUD, BuiltinKind.MEDIA, BuiltinKind.RANGE), dock = true))

@Composable
internal fun GlyphTile(item: DashboardItem, env: SkinTileEnv) = StandardSkinnedTile(item, env)

@Composable
internal fun GlyphTopBar(m: TopBarModel) = ModernTopBar(m)

@Composable
internal fun glyphBackground(): Modifier = Modifier.background(Brush.verticalGradient(DashColors.BackgroundStops))

@Composable
internal fun GlyphWindowFrame(modifier: Modifier) { Box(modifier) }

@Composable
internal fun Modifier.glyphFallbackPanel(): Modifier = background(DashColors.Card.copy(alpha = 0.85f), skinChrome().shapes.medium)

/** The theme card's small picture (ThemePane). */
internal fun DrawScope.glyphStamp(p: DashPalette, number: StampNumber) {
    number("87", size.width * 0.5f, size.height * 0.5f, 16f, p.Accent)
}
