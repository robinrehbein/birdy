package de.robinrehbein.birdy.engine.texture

import de.robinrehbein.birdy.engine.math.MathUtil

/** A fill/stroke style for [RasterCanvas] (canvas 2D `fillStyle`/`strokeStyle`). */
sealed class CanvasPaint

/**
 * Non-premultiplied sRGB colour, components in [0, 1] (canvas colours are sRGB, as in CSS).
 */
class CanvasColor(val r: Double, val g: Double, val b: Double, val a: Double = 1.0) : CanvasPaint() {
    companion object {
        /** `#rrggbb`-style hex int with alpha 1 (world.js `css(hex)`). */
        fun hex(hex: Int, alpha: Double = 1.0): CanvasColor =
            CanvasColor(((hex shr 16) and 0xFF) / 255.0, ((hex shr 8) and 0xFF) / 255.0, (hex and 0xFF) / 255.0, alpha)

        /** Parses `#rgb`, `#rrggbb`, `#rrggbbaa`, `rgb(r, g, b)` and `rgba(r, g, b, a)` CSS colours. */
        fun parse(css: String): CanvasColor {
            val s = css.trim().lowercase()
            if (s.startsWith("#")) {
                val h = s.substring(1)
                return when (h.length) {
                    3 -> hex(h.map { "$it$it" }.joinToString("").toInt(16))
                    6 -> hex(h.toInt(16))
                    8 -> hex(h.substring(0, 6).toInt(16), h.substring(6).toInt(16) / 255.0)
                    else -> throw IllegalArgumentException("bad colour $css")
                }
            }
            val open = s.indexOf('(')
            require(open > 0 && s.endsWith(")")) { "bad colour $css" }
            val parts = s.substring(open + 1, s.length - 1).split(',').map { it.trim() }
            fun ch(p: String) = MathUtil.clamp(p.toDouble(), 0.0, 255.0) / 255.0
            val a = if (parts.size > 3) MathUtil.clamp(parts[3].toDouble(), 0.0, 1.0) else 1.0
            return CanvasColor(ch(parts[0]), ch(parts[1]), ch(parts[2]), a)
        }
    }
}

/**
 * Canvas `createLinearGradient(x0, y0, x1, y1)` with colour stops; colours interpolate in
 * non-premultiplied sRGB as the canvas spec requires.
 */
class LinearGradient(val x0: Double, val y0: Double, val x1: Double, val y1: Double) : CanvasPaint() {
    private val stops = ArrayList<Pair<Double, CanvasColor>>()

    fun addColorStop(offset: Double, color: CanvasColor): LinearGradient {
        // Stops with equal offsets keep insertion order (spec behaviour).
        val i = stops.indexOfFirst { it.first > offset }.let { if (it < 0) stops.size else it }
        stops.add(i, offset to color)
        return this
    }

    fun addColorStop(offset: Double, css: String): LinearGradient = addColorStop(offset, CanvasColor.parse(css))

    internal fun colorAt(px: Double, py: Double, out: DoubleArray) {
        if (stops.isEmpty()) { out.fill(0.0); return }
        val dx = x1 - x0; val dy = y1 - y0
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((px - x0) * dx + (py - y0) * dy) / len2
        val first = stops.first(); val last = stops.last()
        val c: CanvasColor
        if (t <= first.first) c = first.second
        else if (t >= last.first) c = last.second
        else {
            var k = 1
            while (stops[k].first < t) k++
            val (o0, c0) = stops[k - 1]; val (o1, c1) = stops[k]
            val f = if (o1 == o0) 1.0 else (t - o0) / (o1 - o0)
            out[0] = c0.r + (c1.r - c0.r) * f
            out[1] = c0.g + (c1.g - c0.g) * f
            out[2] = c0.b + (c1.b - c0.b) * f
            out[3] = c0.a + (c1.a - c0.a) * f
            return
        }
        out[0] = c.r; out[1] = c.g; out[2] = c.b; out[3] = c.a
    }
}
