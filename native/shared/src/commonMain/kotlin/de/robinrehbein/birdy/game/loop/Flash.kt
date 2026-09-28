package de.robinrehbein.birdy.game.loop

/**
 * The white hit flash (`#flash`): snapped to 0.55 in `die()`, then a CSS `opacity 0.35s`
 * transition (default `ease` timing) back to 0.
 */
class Flash {
    private var from = 0.0
    private var t = 1.0

    fun hit(alpha: Double = 0.55) {
        from = alpha
        t = 0.0
    }

    fun update(dt: Double) {
        if (t < 1) t = minOf(1.0, t + dt / DURATION)
    }

    val alpha: Float get() = if (t >= 1) 0f else (from * (1 - CubicBezier.EASE.y(t))).toFloat()

    companion object {
        const val DURATION = 0.35
    }
}

/** CSS `cubic-bezier(x1, y1, x2, y2)` timing function. */
class CubicBezier(private val x1: Double, private val y1: Double, private val x2: Double, private val y2: Double) {
    private fun bez(t: Double, a: Double, b: Double): Double {
        val u = 1 - t
        return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t
    }

    /** Output progress for input progress [x] (bisection on the x curve). */
    fun y(x: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        var lo = 0.0
        var hi = 1.0
        repeat(40) {
            val mid = (lo + hi) / 2
            if (bez(mid, x1, x2) < x) lo = mid else hi = mid
        }
        return bez((lo + hi) / 2, y1, y2)
    }

    companion object {
        val EASE = CubicBezier(0.25, 0.1, 0.25, 1.0)
    }
}
