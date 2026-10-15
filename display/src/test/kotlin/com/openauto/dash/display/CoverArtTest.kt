package com.openauto.dash.display

import com.openauto.dash.link.ClusterArt
import com.openauto.dash.link.ClusterState
import com.openauto.dash.link.LinkCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/** The cover of the track playing on the display's own media page: drawn when its key matches, the note otherwise. */
class CoverArtTest {

    private fun redCover(): BufferedImage = BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB).also {
        val g = it.createGraphics(); g.color = Color.RED; g.fillRect(0, 0, 120, 120); g.dispose()
    }

    private fun media(key: String?) = ClusterState(
        clock = 1_700_000_000_000, page = "MEDIA", night = true,
        media = ClusterState.Media("Song", "Band", "App", true, 1_000, 200_000, artKey = key)
    )

    private fun redPixels(image: BufferedImage): Int {
        var n = 0
        for (y in 0 until image.height step 4) for (x in 0 until image.width step 4) {
            val rgb = image.getRGB(x, y)
            if ((rgb shr 16 and 0xFF) > 200 && (rgb shr 8 and 0xFF) < 60 && (rgb and 0xFF) < 60) n++
        }
        return n
    }

    @Test
    fun theCoverIsDrawnWhenItsKeyMatchesTheReadings_elseTheNote() {
        val painter = Painter(1024, 600, overscanPct = 0)
        val without = redPixels(painter.paintCluster(media("abc"), 0))
        painter.coverArt = "abc" to redCover()
        val with = redPixels(painter.paintCluster(media("abc"), 0))
        assertTrue("the cover's red fills the square", with > without + 200)
        // Another track's cover is not shown for this one.
        painter.coverArt = "other" to redCover()
        assertEquals(without, redPixels(painter.paintCluster(media("abc"), 0)))
    }

    @Test
    fun theMessageCarriesAJpegTheDisplayCanRead() {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(redCover(), "jpeg", it) }.toByteArray()
        val sent = ClusterArt("k1", Base64.getEncoder().encodeToString(bytes))
        val back = LinkCodec.decode(LinkCodec.encode(sent)) as ClusterArt
        assertEquals("k1", back.key)
        val image = ImageIO.read(java.io.ByteArrayInputStream(Base64.getDecoder().decode(back.jpegBase64)))
        assertEquals(120, image.width)
        assertTrue(bytes.size < ClusterArt.MAX_BYTES)
    }
}
