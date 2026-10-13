package com.openauto.dash

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

/*
 * Glyph skin, the dot-matrix type: the skin's own 5 x 7 dot font (digits,
 * capitals, the few signs a reading needs), drawn as round dots in one call
 * per colour. A text the font cannot write (Cyrillic, a symbol) falls back to
 * the skin's mono face at the same height, so nothing is ever lost.
 */

/** The dot font: each glyph seven rows tall, its rows written with '#' for a lit dot. */
internal object GlyphFont {
    const val ROWS = 7

    /** Columns left blank between two glyphs. */
    private const val GAP = 1

    private val table: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf(" ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### "),
        '1' to arrayOf("  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "),
        '2' to arrayOf(" ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####"),
        '3' to arrayOf("#####", "   # ", "  #  ", "   # ", "    #", "#   #", " ### "),
        '4' to arrayOf("   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # "),
        '5' to arrayOf("#####", "#    ", "#### ", "    #", "    #", "#   #", " ### "),
        '6' to arrayOf("  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### "),
        '7' to arrayOf("#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   "),
        '8' to arrayOf(" ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### "),
        '9' to arrayOf(" ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  "),
        'A' to arrayOf(" ### ", "#   #", "#   #", "#   #", "#####", "#   #", "#   #"),
        'B' to arrayOf("#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### "),
        'C' to arrayOf(" ### ", "#   #", "#    ", "#    ", "#    ", "#   #", " ### "),
        'D' to arrayOf("###  ", "#  # ", "#   #", "#   #", "#   #", "#  # ", "###  "),
        'E' to arrayOf("#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####"),
        'F' to arrayOf("#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    "),
        'G' to arrayOf(" ### ", "#   #", "#    ", "# ###", "#   #", "#   #", " ####"),
        'H' to arrayOf("#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"),
        'I' to arrayOf("###", " # ", " # ", " # ", " # ", " # ", "###"),
        'J' to arrayOf("  ###", "   # ", "   # ", "   # ", "   # ", "#  # ", " ##  "),
        'K' to arrayOf("#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #"),
        'L' to arrayOf("#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####"),
        'M' to arrayOf("#   #", "## ##", "# # #", "# # #", "#   #", "#   #", "#   #"),
        'N' to arrayOf("#   #", "#   #", "##  #", "# # #", "#  ##", "#   #", "#   #"),
        'O' to arrayOf(" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "),
        'P' to arrayOf("#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    "),
        'Q' to arrayOf(" ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #"),
        'R' to arrayOf("#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #"),
        'S' to arrayOf(" ####", "#    ", "#    ", " ### ", "    #", "    #", "#### "),
        'T' to arrayOf("#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  "),
        'U' to arrayOf("#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "),
        'V' to arrayOf("#   #", "#   #", "#   #", "#   #", "#   #", " # # ", "  #  "),
        'W' to arrayOf("#   #", "#   #", "#   #", "# # #", "# # #", "# # #", " # # "),
        'X' to arrayOf("#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #"),
        'Y' to arrayOf("#   #", "#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  "),
        'Z' to arrayOf("#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####"),
        ' ' to arrayOf("   ", "   ", "   ", "   ", "   ", "   ", "   "),
        ':' to arrayOf(" ", " ", "#", " ", "#", " ", " "),
        '.' to arrayOf(" ", " ", " ", " ", " ", " ", "#"),
        ',' to arrayOf(" ", " ", " ", " ", " ", "#", "#"),
        '\'' to arrayOf("#", "#", " ", " ", " ", " ", " "),
        '!' to arrayOf("#", "#", "#", "#", "#", " ", "#"),
        '·' to arrayOf(" ", " ", " ", "#", " ", " ", " "),
        '-' to arrayOf("    ", "    ", "    ", "####", "    ", "    ", "    "),
        '+' to arrayOf("     ", "  #  ", "  #  ", "#####", "  #  ", "  #  ", "     "),
        '/' to arrayOf("    #", "    #", "   # ", "  #  ", " #   ", "#    ", "#    "),
        '%' to arrayOf("##  #", "## # ", "   # ", "  #  ", " #   ", " # ##", "#  ##"),
        '°' to arrayOf(" # ", "# #", " # ", "   ", "   ", "   ", "   "),
        '?' to arrayOf(" ### ", "#   #", "    #", "   # ", "  #  ", "     ", "  #  "),
        '&' to arrayOf(" ##  ", "#  # ", "# #  ", " #   ", "# # #", "#  # ", " ## #"),
        '(' to arrayOf("  #", " # ", "#  ", "#  ", "#  ", " # ", "  #"),
        ')' to arrayOf("#  ", " # ", "  #", "  #", "  #", " # ", "#  ")
    )

    private val MARKS = Regex("\\p{Mn}+")

    /**
     * [text] as the font writes it (capitals, accents dropped, curly quotes
     * and dashes made plain), or null when a character is not in the font.
     */
    fun prepare(text: String): String? {
        val plain = Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "")
            .uppercase(Locale.ROOT)
            .replace('’', '\'').replace('‘', '\'')
            .replace('–', '-').replace('—', '-')
            .replace(' ', ' ').replace(' ', ' ')
        return if (plain.isNotEmpty() && plain.all { it in table }) plain else null
    }

    /** True when [text] can be written in dots. */
    fun writes(text: String): Boolean = prepare(text) != null

    /** Width of a [prepare]d text in dot columns. */
    fun columns(prepared: String): Int {
        if (prepared.isEmpty()) return 0
        var n = 0
        for (c in prepared) n += table.getValue(c)[0].length
        return n + GAP * (prepared.length - 1)
    }

    /** The lit dots of a [prepare]d text: x, y pairs in dot units (the centre of each dot). */
    fun cells(prepared: String): FloatArray {
        var count = 0
        for (c in prepared) for (row in table.getValue(c)) for (ch in row) if (ch == '#') count++
        val out = FloatArray(count * 2)
        var i = 0
        var x0 = 0
        for (c in prepared) {
            val g = table.getValue(c)
            for (y in 0 until ROWS) {
                val row = g[y]
                for (x in row.indices) if (row[x] == '#') {
                    out[i++] = x0 + x + 0.5f
                    out[i++] = y + 0.5f
                }
            }
            x0 += g[0].length + GAP
        }
        return out
    }
}

/** A dot's diameter as a share of the pitch: round dots that nearly touch, as on a dot-matrix display. */
internal const val GLYPH_DOT = 0.8f

/** A paint that draws points as round dots of [diameter] px. */
internal fun glyphDotPaint(color: Color, diameter: Float): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        strokeWidth = diameter
        strokeCap = android.graphics.Paint.Cap.ROUND
        style = android.graphics.Paint.Style.STROKE
    }

/** [cells] (dot units) laid out from ([left], [top]) at [pitch] px: points to hand to [drawGlyphPoints]. */
internal fun glyphPoints(cells: FloatArray, left: Float, top: Float, pitch: Float): FloatArray =
    FloatArray(cells.size) { i -> if (i % 2 == 0) left + cells[i] * pitch else top + cells[i] * pitch }

/** Every point of [points] as a dot in [paint], in one call. */
internal fun DrawScope.drawGlyphPoints(points: FloatArray, paint: android.graphics.Paint) {
    if (points.isEmpty()) return
    drawIntoCanvas { it.nativeCanvas.drawPoints(points, paint) }
}

/** [count] floats of [points] from [offset] (both even) as dots in [paint]. */
internal fun DrawScope.drawGlyphPoints(points: FloatArray, offset: Int, count: Int, paint: android.graphics.Paint) {
    if (count <= 0) return
    drawIntoCanvas { it.nativeCanvas.drawPoints(points, offset, count, paint) }
}

/** [text] in dots straight onto a canvas (the theme stamp), its top left at ([left], [top]). */
internal fun DrawScope.drawGlyphText(text: String, left: Float, top: Float, pitch: Float, color: Color) {
    val prepared = GlyphFont.prepare(text) ?: return
    drawGlyphPoints(glyphPoints(GlyphFont.cells(prepared), left, top, pitch), glyphDotPaint(color, pitch * GLYPH_DOT))
}

/** Width in dot columns [text] takes, its mono fallback counted as six columns a character. */
internal fun glyphColumns(text: String): Int {
    val prepared = GlyphFont.prepare(text)
    return if (prepared != null) GlyphFont.columns(prepared) else text.length * 6
}

/**
 * The pitch (dp from one dot to the next) that fits [text] into [maxW] x
 * [maxH], at most [max] and at least [min] dp.
 */
internal fun glyphPitch(text: String, maxW: Dp, maxH: Dp, max: Float = 16f, min: Float = 2f): Dp {
    val cols = glyphColumns(text).coerceAtLeast(1)
    return min(min(maxW.value / cols, maxH.value / GlyphFont.ROWS), max).coerceAtLeast(min).dp
}

/** The mono fallback's size for a dot text of [pitch]: its capitals as tall as the dots. */
private fun fallbackSize(pitch: Dp): Float = pitch.value * GlyphFont.ROWS / 0.74f

/**
 * [text] in the dot font at [pitch], lit in [color]; a text the font cannot
 * write is set in the mono face at the same height instead. [maxColumns]
 * cuts a long text to that many columns, ending it in two dots.
 */
@Composable
internal fun GlyphDotText(text: String, pitch: Dp, color: Color, modifier: Modifier = Modifier, maxColumns: Int = Int.MAX_VALUE) {
    val prepared = remember(text, maxColumns) { GlyphFont.prepare(text)?.let { cutToColumns(it, maxColumns) } }
    if (prepared == null) {
        Text(
            text.uppercase(Locale.getDefault()),
            modifier = modifier,
            style = TextStyle(color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = fixedSp(fallbackSize(pitch)), letterSpacing = 0.02.em),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    val cols = GlyphFont.columns(prepared)
    Spacer(
        modifier
            .size(pitch * cols, pitch * GlyphFont.ROWS)
            .cachedDraw(prepared, pitch, color) {
                val p = pitch.toPx()
                val points = glyphPoints(GlyphFont.cells(prepared), 0f, 0f, p)
                val paint = glyphDotPaint(color, p * GLYPH_DOT)
                onDrawBehind { drawGlyphPoints(points, paint) }
            }
    )
}

/** [prepared] cut to [maxColumns], ending in two dots when it was cut. */
internal fun cutToColumns(prepared: String, maxColumns: Int): String {
    if (GlyphFont.columns(prepared) <= maxColumns) return prepared
    var end = prepared.length
    while (end > 1 && GlyphFont.columns(prepared.substring(0, end).trimEnd() + "..") > maxColumns) end--
    return prepared.substring(0, end).trimEnd() + ".."
}
