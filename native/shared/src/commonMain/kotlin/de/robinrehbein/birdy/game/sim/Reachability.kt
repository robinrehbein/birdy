package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.Tuning
import kotlin.math.abs
import kotlin.math.pow

/**
 * The fairness budgets of `makeReachable` (main.js:926-955) for [t] seconds between rows:
 * falling is quicker than climbing, and every lane step multiplies both budgets by 0.6.
 */
class Reach(val t: Double) {
    val maxDrop = 1 + 3.5 * t
    val maxRise = 0.8 + 3 * t

    fun reach(from: Double, to: Double, steps: Int): Boolean {
        val k = Tuning.SWITCH_FACTOR.pow(steps)
        val d = to - from
        return d <= maxRise * k && -d <= maxDrop * k
    }

    /** A moving gap only counts if its whole travel range is in reach. */
    fun fits(prev: GapSpec, gap: Gap, steps: Int): Boolean =
        reach(prev.center, gap.center - gap.amp, steps) && reach(prev.center, gap.center + gap.amp, steps)

    companion object {
        /** `t = spacing() / baseSpeed()` at [score]. */
        fun atScore(score: Int): Reach = Reach(Difficulty.spacing(score) / Difficulty.baseSpeed(score))
    }
}

/**
 * `makeReachable(spec, lo, hi)`: from every open gap of [prev] at least one gap of [spec] must be
 * reachable; otherwise the closest open lane is pulled into 90 % of the reach and stops moving.
 * Each previous lane is checked independently (JS behaviour, not a joint solution).
 */
fun makeReachable(spec: Array<Gap?>, lo: Double, hi: Double, prev: List<GapSpec?>?, score: Int) {
    if (prev == null) return
    val r = Reach.atScore(score)
    prev.forEachIndexed { j, p ->
        if (p == null) return@forEachIndexed
        val open = spec.indices.filter { spec[it] != null }
        if (open.any { r.fits(p, spec[it]!!, abs(it - j)) }) return@forEachIndexed
        // Stable sort: ties keep the lower lane index, like Array.prototype.sort.
        val i = open.sortedBy { abs(it - j) }[0]
        val g = spec[i]!!
        val k = Tuning.SWITCH_FACTOR.pow(abs(i - j))
        g.amp = 0.0
        g.center = SimMath.clamp(g.center, p.center - r.maxDrop * k * 0.9, p.center + r.maxRise * k * 0.9)
        g.center = SimMath.clamp(g.center, lo, hi)
    }
}
