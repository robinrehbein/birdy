package de.robinrehbein.birdy.ui

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType

/**
 * A tiny SVG path-data parser covering the commands Birdy's icon set actually uses
 * (icons.js only ever emits `M`, `L`, `H`, `V`, `C`, `A`, `Z`, both absolute and relative —
 * see [Icons] / [de.robinrehbein.birdy.meta.Icons]). Not a general SVG parser.
 */
internal fun parseSvgPath(d: String): Path {
    val path = Path().apply { fillType = PathFillType.NonZero }
    var i = 0
    var cx = 0f
    var cy = 0f
    var startX = 0f
    var startY = 0f
    fun numbers(): MutableList<Float> {
        val out = mutableListOf<Float>()
        while (i < d.length) {
            while (i < d.length && (d[i] == ' ' || d[i] == ',' || d[i] == '\n' || d[i] == '\t')) i++
            val start = i
            if (i < d.length && (d[i] == '-' || d[i] == '+')) i++
            var seenDot = false
            while (i < d.length && (d[i].isDigit() || (d[i] == '.' && !seenDot))) {
                if (d[i] == '.') seenDot = true
                i++
            }
            if (i == start) break
            out.add(d.substring(start, i).toFloat())
        }
        return out
    }
    fun skipSep() {
        while (i < d.length && (d[i] == ' ' || d[i] == ',' || d[i] == '\n' || d[i] == '\t')) i++
    }
    fun one(): Float? {
        skipSep()
        val start = i
        if (i < d.length && (d[i] == '-' || d[i] == '+')) i++
        var seenDot = false
        while (i < d.length && (d[i].isDigit() || (d[i] == '.' && !seenDot))) {
            if (d[i] == '.') seenDot = true
            i++
        }
        return if (i == start) null else d.substring(start, i).toFloat()
    }
    fun flag(): Float? {
        skipSep()
        return if (i < d.length && (d[i] == '0' || d[i] == '1')) (d[i++] - '0').toFloat() else null
    }
    fun arcNumbers(): List<Float> {
        val out = mutableListOf<Float>()
        while (true) {
            val group = listOf(one(), one(), one(), flag(), flag(), one(), one())
            if (group.any { it == null }) break
            group.forEach { out.add(it!!) }
        }
        return out
    }
    while (i < d.length) {
        while (i < d.length && (d[i] == ' ' || d[i] == ',')) i++
        if (i >= d.length) break
        val cmd = d[i]
        i++
        when (cmd) {
            'M', 'm' -> {
                val n = numbers()
                var idx = 0
                while (idx + 1 < n.size) {
                    val x = if (cmd == 'm') cx + n[idx] else n[idx]
                    val y = if (cmd == 'm') cy + n[idx + 1] else n[idx + 1]
                    if (idx == 0) { path.moveTo(x, y); startX = x; startY = y } else path.lineTo(x, y)
                    cx = x; cy = y
                    idx += 2
                }
            }
            'L', 'l' -> {
                val n = numbers()
                var idx = 0
                while (idx + 1 < n.size) {
                    val x = if (cmd == 'l') cx + n[idx] else n[idx]
                    val y = if (cmd == 'l') cy + n[idx + 1] else n[idx + 1]
                    path.lineTo(x, y)
                    cx = x; cy = y
                    idx += 2
                }
            }
            'H', 'h' -> for (v in numbers()) { cx = if (cmd == 'h') cx + v else v; path.lineTo(cx, cy) }
            'V', 'v' -> for (v in numbers()) { cy = if (cmd == 'v') cy + v else v; path.lineTo(cx, cy) }
            'C', 'c' -> {
                val n = numbers()
                var idx = 0
                while (idx + 5 < n.size) {
                    val x1 = if (cmd == 'c') cx + n[idx] else n[idx]
                    val y1 = if (cmd == 'c') cy + n[idx + 1] else n[idx + 1]
                    val x2 = if (cmd == 'c') cx + n[idx + 2] else n[idx + 2]
                    val y2 = if (cmd == 'c') cy + n[idx + 3] else n[idx + 3]
                    val x = if (cmd == 'c') cx + n[idx + 4] else n[idx + 4]
                    val y = if (cmd == 'c') cy + n[idx + 5] else n[idx + 5]
                    path.cubicTo(x1, y1, x2, y2, x, y)
                    cx = x; cy = y
                    idx += 6
                }
            }
            'A', 'a' -> {
                val n = arcNumbers()
                var idx = 0
                while (idx + 6 < n.size) {
                    val x = if (cmd == 'a') cx + n[idx + 5] else n[idx + 5]
                    val y = if (cmd == 'a') cy + n[idx + 6] else n[idx + 6]
                    arcTo(path, cx, cy, n[idx], n[idx + 1], n[idx + 2], n[idx + 3] != 0f, n[idx + 4] != 0f, x, y)
                    cx = x; cy = y
                    idx += 7
                }
            }
            'Z', 'z' -> { path.close(); cx = startX; cy = startY }
        }
    }
    return path
}

/** SVG elliptical arc (implementation notes F.6.5) appended as cubic Béziers of at most 90°. */
private fun arcTo(
    path: Path, x1: Float, y1: Float, rxIn: Float, ryIn: Float, rotDeg: Float,
    large: Boolean, sweep: Boolean, x2: Float, y2: Float,
) {
    if (x1 == x2 && y1 == y2) return
    var rx = kotlin.math.abs(rxIn).toDouble()
    var ry = kotlin.math.abs(ryIn).toDouble()
    if (rx == 0.0 || ry == 0.0) { path.lineTo(x2, y2); return }
    val phi = rotDeg.toDouble() * kotlin.math.PI / 180
    val cosP = kotlin.math.cos(phi)
    val sinP = kotlin.math.sin(phi)
    val dx = (x1 - x2) / 2.0
    val dy = (y1 - y2) / 2.0
    val x1p = cosP * dx + sinP * dy
    val y1p = -sinP * dx + cosP * dy
    val lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
    if (lambda > 1) { val k = kotlin.math.sqrt(lambda); rx *= k; ry *= k }
    val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    var coef = kotlin.math.sqrt(kotlin.math.max(0.0, num / den))
    if (large == sweep) coef = -coef
    val cxp = coef * rx * y1p / ry
    val cyp = -coef * ry * x1p / rx
    val cxa = cosP * cxp - sinP * cyp + (x1 + x2) / 2.0
    val cya = sinP * cxp + cosP * cyp + (y1 + y2) / 2.0
    fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val a = kotlin.math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a
    }
    val theta1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    var delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if (!sweep && delta > 0) delta -= 2 * kotlin.math.PI
    if (sweep && delta < 0) delta += 2 * kotlin.math.PI
    val segs = kotlin.math.ceil(kotlin.math.abs(delta) / (kotlin.math.PI / 2)).toInt().coerceAtLeast(1)
    val step = delta / segs
    val t = 4.0 / 3.0 * kotlin.math.tan(step / 4)
    var a0 = theta1
    fun pt(a: Double): Pair<Double, Double> {
        val ex = rx * kotlin.math.cos(a)
        val ey = ry * kotlin.math.sin(a)
        return (cosP * ex - sinP * ey + cxa) to (sinP * ex + cosP * ey + cya)
    }
    fun dv(a: Double): Pair<Double, Double> {
        val ex = -rx * kotlin.math.sin(a)
        val ey = ry * kotlin.math.cos(a)
        return (cosP * ex - sinP * ey) to (sinP * ex + cosP * ey)
    }
    repeat(segs) {
        val a1 = a0 + step
        val (px0, py0) = pt(a0)
        val (px1, py1) = pt(a1)
        val (dx0, dy0) = dv(a0)
        val (dx1, dy1) = dv(a1)
        path.cubicTo(
            (px0 + t * dx0).toFloat(), (py0 + t * dy0).toFloat(),
            (px1 - t * dx1).toFloat(), (py1 - t * dy1).toFloat(),
            px1.toFloat(), py1.toFloat(),
        )
        a0 = a1
    }
}
