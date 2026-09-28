package de.robinrehbein.birdy.engine.texture

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.engine.get
import de.robinrehbein.birdy.engine.i
import de.robinrehbein.birdy.engine.ints
import de.robinrehbein.birdy.engine.isNull
import de.robinrehbein.birdy.engine.list
import de.robinrehbein.birdy.engine.obj
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** world.js ground/grass canvases against Chromium's 2D canvas (docs/native/golden/engine-texture.json). */
@OptIn(ExperimentalEncodingApi::class)
class RasterCanvasTest {
    private val images = Golden.json("engine-texture.json")["images"].list

    /** world.js drawGround, line for line. */
    private fun drawGround(g: RasterCanvas, road: Int, stripe: Int, edge: Int) {
        g.clearRect(0.0, 0.0, 512.0, 128.0)
        val x0 = 150.0; val x1 = 362.0
        g.setFillStyle(road)
        g.fillRect(x0, 0.0, x1 - x0, 128.0)
        g.setStrokeStyle(stripe)
        g.lineWidth = 14.0
        var i = -128
        while (i < 256) {
            g.beginPath()
            g.moveTo(x0, i.toDouble())
            g.lineTo(x1, i + 128.0)
            g.stroke()
            i += 32
        }
        g.setFillStyle("rgba(255, 252, 235, 0.75)")
        for (x in doubleArrayOf(226.5, 285.5)) g.fillRect(x - 2.5, 8.0, 5.0, 48.0)
        g.setFillStyle(edge)
        g.fillRect(x0 - 10, 0.0, 10.0, 128.0)
        g.fillRect(x1, 0.0, 10.0, 128.0)
        g.setFillStyle("#543847")
        g.fillRect(x0 - 14, 0.0, 4.0, 128.0)
        g.fillRect(x1 + 10, 0.0, 4.0, 128.0)
    }

    private fun grass(g: RasterCanvas) {
        g.setFillStyle("#ffffff")
        g.fillRect(0.0, 0.0, 4.0, 128.0)
        g.setFillStyle("#e0e6d8")
        var y = 0
        while (y < 128) { g.fillRect(0.0, y.toDouble(), 4.0, 16.0); y += 32 }
    }

    private fun shapes(g: RasterCanvas) {
        val gr = g.createLinearGradient(0.0, 0.0, 64.0, 32.0)
        gr.addColorStop(0.0, "#ff0000")
        gr.addColorStop(0.5, "rgba(0, 255, 0, 0.5)")
        gr.addColorStop(1.0, "#0000ff")
        g.fillStyle = gr
        g.fillRect(0.0, 0.0, 64.0, 32.0)
        g.globalAlpha = 0.6
        g.setFillStyle("#ffffff")
        g.beginPath()
        g.arc(40.0, 40.0, 17.5, 0.0, PI * 2)
        g.fill()
        g.globalAlpha = 1.0
    }

    private class Diff(var max: Int = 0, var total: Long = 0, var count: Int = 0, var off: Int = 0)

    private fun compare(img: kotlinx.serialization.json.JsonElement, canvas: RasterCanvas): Diff {
        val w = img["width"].i
        val diff = Diff()
        val tex = canvas.toTexture()
        for ((row, b64) in img["rows"].obj) {
            val y = row.toInt()
            val expected = Base64.decode(b64.jsonPrimitive.content)
            for (x in 0 until w) for (c in 0..3) {
                val e = expected[x * 4 + c].toInt() and 0xFF
                val a = tex.pixels[(y * w + x) * 4 + c].toInt() and 0xFF
                val d = abs(e - a)
                diff.max = max(diff.max, d); diff.total += d; diff.count++
                if (d > 2) diff.off++
            }
        }
        // Whole-image channel sums.
        val sums = LongArray(4)
        for (i in tex.pixels.indices) sums[i % 4] += (tex.pixels[i].toInt() and 0xFF).toLong()
        val exp = img["channelSums"].ints()
        for (c in 0..3) {
            val rel = abs(sums[c] - exp[c]).toDouble() / max(1, exp[c])
            assertTrue(rel < 2e-3, "channel $c sum ${sums[c]} vs ${exp[c]}")
        }
        return diff
    }

    @Test
    fun groundTexturesMatchChromium() {
        for (img in images.filter { it["kind"].jsonPrimitive.content == "ground" }) {
            val p = img["palette"].ints()
            val canvas = RasterCanvas(512, 128)
            drawGround(canvas, p[0], p[1], p[2])
            val d = compare(img, canvas)
            // Rectangles are pixel-exact; only anti-aliased stripe edges may differ slightly.
            assertTrue(d.max <= 12, "max channel diff ${d.max}")
            assertTrue(d.off.toDouble() / d.count < 0.01, "pixels off by >2: ${d.off} of ${d.count}")
            assertTrue(d.total.toDouble() / d.count < 0.2, "mean diff ${d.total.toDouble() / d.count}")
        }
    }

    @Test
    fun grassTextureIsExact() {
        val img = images.first { it["kind"].jsonPrimitive.content == "grass" }
        val canvas = RasterCanvas(4, 128)
        grass(canvas)
        assertEquals(0, compare(img, canvas).max)
        assertTrue(img["palette"].isNull)
    }

    @Test
    fun gradientArcAndGlobalAlpha() {
        val img = images.first { it["kind"].jsonPrimitive.content == "shapes" }
        val canvas = RasterCanvas(64, 64)
        shapes(canvas)
        val d = compare(img, canvas)
        // Chromium's software rasterizer supersamples curved edges (4 sub-scanlines), this canvas
        // uses exact area coverage: arc edge pixels may differ by up to ~1/3 coverage.
        assertTrue(d.total.toDouble() / d.count < 1.0, "mean diff ${d.total.toDouble() / d.count}")
        assertTrue(d.max <= 80, "max diff ${d.max}")
        assertTrue(d.off.toDouble() / d.count < 0.03, "pixels off by >2: ${d.off} of ${d.count}")
    }

    @Test
    fun repaintMarksTextureDirty() {
        val canvas = RasterCanvas(4, 4)
        val tex = canvas.toTexture()
        val v = tex.version
        canvas.setFillStyle(0xff0000)
        canvas.fillRect(0.0, 0.0, 2.0, 2.0)
        canvas.writeTo(tex)
        assertTrue(tex.version > v)
        assertEquals(0xff0000ff.toInt(), canvas.getPixel(0, 0))
        assertEquals(0, canvas.getPixel(3, 3))
    }
}
