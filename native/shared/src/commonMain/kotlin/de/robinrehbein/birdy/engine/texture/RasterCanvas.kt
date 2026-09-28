package de.robinrehbein.birdy.engine.texture

import de.robinrehbein.birdy.engine.scene.Texture
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal software 2D canvas (RGBA8, premultiplied internally like browser canvases) covering what
 * world.js draws into its `CanvasTexture`s: `clearRect`, `fillRect`, paths with `moveTo`/`lineTo`/`arc`/
 * `rect`, `fill` (non-zero) and `stroke` (butt caps, no joins), solid colours, linear gradients and
 * `globalAlpha`, blended source-over. Anti-aliasing uses exact area coverage, so axis-aligned integer
 * rectangles are pixel-exact and edges match browser output within a few 8-bit steps.
 *
 * Coordinates are canvas pixels with y down; row 0 is the top row, as in [Texture.pixels].
 */
class RasterCanvas(val width: Int, val height: Int) {
    /** Premultiplied RGBA, quantized to 8-bit levels after every draw (like a 2D canvas backing store). */
    private val px = FloatArray(width * height * 4)
    private val raster = CoverageRaster(width, height)

    var fillStyle: CanvasPaint = CanvasColor(0.0, 0.0, 0.0)
    var strokeStyle: CanvasPaint = CanvasColor(0.0, 0.0, 0.0)
    var lineWidth = 1.0
    var globalAlpha = 1.0

    private val subpaths = ArrayList<MutableList<DoubleArray>>()
    private var current: MutableList<DoubleArray>? = null

    /** `fillStyle = '#rrggbb'` / `'rgba(...)'`. */
    fun setFillStyle(css: String) { fillStyle = CanvasColor.parse(css) }
    fun setFillStyle(hex: Int) { fillStyle = CanvasColor.hex(hex) }
    fun setStrokeStyle(css: String) { strokeStyle = CanvasColor.parse(css) }
    fun setStrokeStyle(hex: Int) { strokeStyle = CanvasColor.hex(hex) }

    fun createLinearGradient(x0: Double, y0: Double, x1: Double, y1: Double) = LinearGradient(x0, y0, x1, y1)

    /** Sets the covered pixels to transparent black (area-weighted at fractional edges). */
    fun clearRect(x: Double, y: Double, w: Double, h: Double) {
        addRect(x, y, w, h)
        raster.forEachCoverage { ix, iy, c ->
            val o = (iy * width + ix) * 4
            for (k in 0..3) px[o + k] = q(px[o + k] * (1 - c))
        }
    }

    fun fillRect(x: Double, y: Double, w: Double, h: Double) {
        addRect(x, y, w, h)
        composite(fillStyle)
    }

    fun strokeRect(x: Double, y: Double, w: Double, h: Double) {
        val saved = ArrayList(subpaths); val savedCur = current
        subpaths.clear(); current = null
        rect(x, y, w, h)
        stroke()
        subpaths.clear(); subpaths.addAll(saved); current = savedCur
    }

    fun beginPath() { subpaths.clear(); current = null }

    fun moveTo(x: Double, y: Double) {
        current = mutableListOf(doubleArrayOf(x, y)).also { subpaths.add(it) }
    }

    fun lineTo(x: Double, y: Double) {
        val c = current
        if (c == null) moveTo(x, y) else c.add(doubleArrayOf(x, y))
    }

    fun closePath() {
        val c = current ?: return
        if (c.size > 1) c.add(c[0].copyOf())
        current = mutableListOf(c[0].copyOf()).also { subpaths.add(it) }
    }

    fun rect(x: Double, y: Double, w: Double, h: Double) {
        moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h); closePath()
    }

    /** Canvas `arc(x, y, r, start, end, counterclockwise)`, flattened to short segments. */
    fun arc(x: Double, y: Double, r: Double, startAngle: Double, endAngle: Double, counterclockwise: Boolean = false) {
        var sweep = endAngle - startAngle
        if (!counterclockwise) {
            if (sweep >= 2 * PI) sweep = 2 * PI else { sweep %= (2 * PI); if (sweep < 0) sweep += 2 * PI }
        } else {
            if (sweep <= -2 * PI) sweep = -2 * PI else { sweep %= (2 * PI); if (sweep > 0) sweep -= 2 * PI }
        }
        val segments = max(8, ceil(abs(sweep) * max(r, 1.0) / 0.25).toInt().coerceAtMost(4096))
        for (i in 0..segments) {
            val a = startAngle + sweep * i / segments
            val px = x + r * cos(a); val py = y + r * sin(a)
            if (i == 0 && current != null) lineTo(px, py) else if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
    }

    /** Fills the current path with [fillStyle] (non-zero rule, subpaths implicitly closed). */
    fun fill() {
        for (sp in subpaths) {
            if (sp.size < 2) continue
            for (i in sp.indices) {
                val a = sp[i]; val b = sp[(i + 1) % sp.size]
                raster.line(a[0], a[1], b[0], b[1])
            }
        }
        composite(fillStyle)
    }

    /** Strokes every segment of the current path as a butt-capped quad of [lineWidth]. */
    fun stroke() {
        val hw = lineWidth / 2
        for (sp in subpaths) for (i in 0 until sp.size - 1) {
            val a = sp[i]; val b = sp[i + 1]
            val dx = b[0] - a[0]; val dy = b[1] - a[1]
            val len = sqrt(dx * dx + dy * dy)
            if (len == 0.0) continue
            val nx = -dy / len * hw; val ny = dx / len * hw
            quad(a[0] + nx, a[1] + ny, b[0] + nx, b[1] + ny, b[0] - nx, b[1] - ny, a[0] - nx, a[1] - ny)
        }
        composite(strokeStyle)
    }

    /** Convenience for `beginPath(); arc(x, y, r, 0, 2π); fill()`. */
    fun fillCircle(x: Double, y: Double, r: Double) {
        beginPath(); arc(x, y, r, 0.0, 2 * PI); fill()
    }

    private fun quad(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double, dx: Double, dy: Double) {
        raster.line(ax, ay, bx, by); raster.line(bx, by, cx, cy); raster.line(cx, cy, dx, dy); raster.line(dx, dy, ax, ay)
    }

    private fun addRect(x: Double, y: Double, w: Double, h: Double) {
        quad(x, y, x + w, y, x + w, y + h, x, y + h)
    }

    private val tmp = DoubleArray(4)

    private fun composite(paint: CanvasPaint) {
        val solid = paint as? CanvasColor
        raster.forEachCoverage { ix, iy, c ->
            val sr: Double; val sg: Double; val sb: Double; val sa: Double
            if (solid != null) {
                sr = solid.r; sg = solid.g; sb = solid.b; sa = solid.a
            } else {
                (paint as LinearGradient).colorAt(ix + 0.5, iy + 0.5, tmp)
                sr = tmp[0]; sg = tmp[1]; sb = tmp[2]; sa = tmp[3]
            }
            val a = sa * globalAlpha * c
            val o = (iy * width + ix) * 4
            val inv = 1 - a
            px[o] = q(sr * a + px[o] * inv)
            px[o + 1] = q(sg * a + px[o + 1] * inv)
            px[o + 2] = q(sb * a + px[o + 2] * inv)
            px[o + 3] = q(a + px[o + 3] * inv)
        }
    }

    private fun q(v: Double): Float = ((v * 255).roundToInt().coerceIn(0, 255) / 255.0).toFloat()
    private fun q(v: Float): Float = q(v.toDouble())

    /** Non-premultiplied RGBA8 of pixel ([x], [y]) as `0xRRGGBBAA` (like `getImageData`). */
    fun getPixel(x: Int, y: Int): Int {
        val o = (y * width + x) * 4
        val a = px[o + 3]
        val ai = (a * 255).roundToInt()
        if (ai == 0) return 0
        fun un(c: Float) = min(255, (c * 255 * 255 / (a * 255)).roundToInt())
        return (un(px[o]) shl 24) or (un(px[o + 1]) shl 16) or (un(px[o + 2]) shl 8) or ai
    }

    /** Writes the canvas into [target] (non-premultiplied, sRGB) and marks it dirty (`needsUpdate`). */
    fun writeTo(target: Texture): Texture {
        require(target.width == width && target.height == height) { "texture size mismatch" }
        val out = target.pixels
        for (y in 0 until height) for (x in 0 until width) {
            val p = getPixel(x, y)
            val o = (y * width + x) * 4
            out[o] = (p ushr 24).toByte(); out[o + 1] = (p ushr 16).toByte()
            out[o + 2] = (p ushr 8).toByte(); out[o + 3] = p.toByte()
        }
        target.markDirty()
        return target
    }

    /** New [Texture] with this canvas' pixels (three.js `new CanvasTexture(canvas)`). */
    fun toTexture(): Texture = writeTo(Texture(width, height))
}
