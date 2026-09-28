package de.robinrehbein.birdy.game.sim

import kotlin.math.max
import kotlin.math.min

/** three.js MathUtils formulas, kept bit-identical (the simulation does not depend on engine). */
object SimMath {
    /** `THREE.MathUtils.lerp`: `(1 - t) * x + t * y`. */
    fun lerp(x: Double, y: Double, t: Double): Double = (1 - t) * x + t * y

    /** `THREE.MathUtils.clamp`: `max(lo, min(hi, v))` (no exception when lo > hi). */
    fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))

    /** `THREE.MathUtils.smoothstep(x, min, max)`. */
    fun smoothstep(x: Double, min: Double, max: Double): Double {
        if (x <= min) return 0.0
        if (x >= max) return 1.0
        val t = (x - min) / (max - min)
        return t * t * (3 - 2 * t)
    }

    /** `Vector3.lerp` component: `a + (b - a) * alpha` (differs from [lerp] in rounding). */
    fun vlerp(a: Double, b: Double, alpha: Double): Double = a + (b - a) * alpha

    /** `Math.sign` for lane steps. */
    fun sign(v: Int): Int = if (v > 0) 1 else if (v < 0) -1 else 0
}
