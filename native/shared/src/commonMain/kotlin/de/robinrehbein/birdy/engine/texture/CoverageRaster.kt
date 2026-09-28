package de.robinrehbein.birdy.engine.texture

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Exact-area anti-aliased polygon coverage (signed-area accumulation, as in font-rs). Edges are added
 * with [line]; [coverage] then yields per-pixel coverage in [0, 1] with the non-zero rule for
 * same-orientation overlaps (clamped). Coordinates are canvas pixels, y down.
 */
internal class CoverageRaster(val width: Int, val height: Int) {
    private val stride = width + 2
    private val acc = FloatArray(stride * height)

    fun clear() = acc.fill(0f)

    /** Adds the directed edge (x0,y0)->(x1,y1), clipping it horizontally to [0, width]. */
    fun line(x0: Double, y0: Double, x1: Double, y1: Double) {
        if (y0 == y1) return
        // Split at x = 0 and x = width so clamping keeps the winding exact for visible pixels.
        val cuts = ArrayList<Double>(4)
        cuts.add(0.0)
        for (bx in doubleArrayOf(0.0, width.toDouble())) {
            if ((x0 < bx && x1 > bx) || (x0 > bx && x1 < bx)) cuts.add((bx - x0) / (x1 - x0))
        }
        cuts.add(1.0)
        cuts.sort()
        for (i in 0 until cuts.size - 1) {
            val ta = cuts[i]; val tb = cuts[i + 1]
            val ax = (x0 + (x1 - x0) * ta).coerceIn(0.0, width.toDouble())
            val ay = y0 + (y1 - y0) * ta
            val bx = (x0 + (x1 - x0) * tb).coerceIn(0.0, width.toDouble())
            val by = y0 + (y1 - y0) * tb
            drawLine(ax, ay, bx, by)
        }
    }

    private fun drawLine(px0: Double, py0: Double, px1: Double, py1: Double) {
        if (abs(py0 - py1) <= 1e-12) return
        val dir: Double
        val ax: Double; val ay: Double; val bx: Double; val by: Double
        if (py0 < py1) { dir = 1.0; ax = px0; ay = py0; bx = px1; by = py1 } else { dir = -1.0; ax = px1; ay = py1; bx = px0; by = py0 }
        val dxdy = (bx - ax) / (by - ay)
        var x = ax
        if (ay < 0) x -= ay * dxdy
        val yStart = max(0, floor(ay).toInt())
        val yEnd = min(height, ceil(by).toInt())
        for (y in yStart until yEnd) {
            val line = y * stride
            val dy = min((y + 1).toDouble(), by) - max(y.toDouble(), ay)
            val xnext = x + dxdy * dy
            val d = dy * dir
            val xa = min(x, xnext); val xb = max(x, xnext)
            val xaFloor = floor(xa)
            val xai = xaFloor.toInt()
            val xbCeil = ceil(xb)
            val xbi = xbCeil.toInt()
            if (xbi <= xai + 1) {
                val xmf = 0.5 * (x + xnext) - xaFloor
                add(line + xai, d - d * xmf)
                add(line + xai + 1, d * xmf)
            } else {
                val s = 1.0 / (xb - xa)
                val xaf = xa - xaFloor
                val a0 = 0.5 * s * (1.0 - xaf) * (1.0 - xaf)
                val xbf = xb - xbCeil + 1.0
                val am = 0.5 * s * xbf * xbf
                add(line + xai, d * a0)
                if (xbi == xai + 2) {
                    add(line + xai + 1, d * (1.0 - a0 - am))
                } else {
                    val a1 = s * (1.5 - xaf)
                    add(line + xai + 1, d * (a1 - a0))
                    for (xi in xai + 2 until xbi - 1) add(line + xi, d * s)
                    val a2 = a1 + (xbi - xai - 3) * s
                    add(line + xbi - 1, d * (1.0 - a2 - am))
                }
                add(line + xbi, d * am)
            }
            x = xnext
        }
    }

    private fun add(i: Int, v: Double) { acc[i] = (acc[i] + v).toFloat() }

    /** Visits every pixel with non-zero coverage; resets the accumulator. */
    fun forEachCoverage(visit: (x: Int, y: Int, coverage: Float) -> Unit) {
        for (y in 0 until height) {
            var sum = 0f
            val line = y * stride
            for (x in 0 until width) {
                sum += acc[line + x]
                val c = min(abs(sum), 1f)
                if (c > 1e-6f) visit(x, y, c)
            }
        }
        clear()
    }
}
