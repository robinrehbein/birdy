package de.robinrehbein.birdy.engine.math

import kotlin.math.PI
import kotlin.math.exp

/** Scalar helpers matching three.js `MathUtils` formulas exactly (Double for simulation, Float for rendering). */
object MathUtil {
    const val DEG2RAD: Double = PI / 180.0
    const val RAD2DEG: Double = 180.0 / PI

    fun clamp(v: Double, lo: Double, hi: Double): Double = if (v < lo) lo else if (v > hi) hi else v
    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v
    fun clamp(v: Int, lo: Int, hi: Int): Int = if (v < lo) lo else if (v > hi) hi else v

    fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** three.js `MathUtils.smoothstep(x, min, max)`: Hermite 3t²-2t³ after clamping t to [0,1]. */
    fun smoothstep(x: Double, min: Double, max: Double): Double {
        if (x <= min) return 0.0
        if (x >= max) return 1.0
        val t = (x - min) / (max - min)
        return t * t * (3 - 2 * t)
    }

    fun smoothstep(x: Float, min: Float, max: Float): Float = smoothstep(x.toDouble(), min.toDouble(), max.toDouble()).toFloat()

    /** three.js `MathUtils.smootherstep`: 6t⁵-15t⁴+10t³. */
    fun smootherstep(x: Double, min: Double, max: Double): Double {
        if (x <= min) return 0.0
        if (x >= max) return 1.0
        val t = (x - min) / (max - min)
        return t * t * t * (t * (t * 6 - 15) + 10)
    }

    /** JS-style positive modulo (`((a % n) + n) % n`), identical to three.js `euclideanModulo`. */
    fun mod(a: Double, n: Double): Double = ((a % n) + n) % n
    fun euclideanModulo(a: Double, n: Double): Double = ((a % n) + n) % n

    fun inverseLerp(x: Double, y: Double, value: Double): Double = if (x != y) (value - x) / (y - x) else 0.0
    fun mapLinear(x: Double, a1: Double, a2: Double, b1: Double, b2: Double): Double = b1 + (x - a1) * (b2 - b1) / (a2 - a1)

    /** three.js `MathUtils.damp`: frame-rate independent lerp. */
    fun damp(x: Double, y: Double, lambda: Double, dt: Double): Double = lerp(x, y, 1 - exp(-lambda * dt))

    fun degToRad(deg: Double): Double = deg * DEG2RAD
    fun radToDeg(rad: Double): Double = rad * RAD2DEG
}
