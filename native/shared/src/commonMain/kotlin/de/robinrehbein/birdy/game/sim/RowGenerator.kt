package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.Tuning
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** A generated row plus the values the property tests check. */
class GeneratedRow(
    val spec: List<GapSpec?>,
    /** Vertical band for gap centres; NaN for scripted tutorial rows. */
    val lo: Double,
    val hi: Double,
    /** The obstacle-free lane (`easy`), or -1 for tutorial rows. */
    val easy: Int,
)

/**
 * `gateSpec()` (main.js:862-921, main-a.md §8.1). Draws from [rng] at exactly the JS
 * `Math.random()` call sites, except the lane-blocking shuffle, which is Fisher-Yates instead of
 * `[0,1,2].sort(() => Math.random() - 0.5)` (main-a.md §12.3: harmless, documented deviation).
 */
object RowGenerator {
    /** Scripted first-run rows: middle only, then middle blocked; null afterwards. */
    fun tutorialSpec(gatesSpawned: Int): List<GapSpec?>? {
        val gap = GapSpec(center = 5.2, size = 6.2)
        if (gatesSpawned < Tuning.TUT_SWITCH_ROW) return listOf(null, gap.copy(), null)
        if (gatesSpawned == Tuning.TUT_SWITCH_ROW) return listOf(gap.copy(), null, gap.copy())
        return null
    }

    fun gateSpec(
        score: Int,
        gatesSpawned: Int,
        prevGaps: List<GapSpec?>?,
        tutorialActive: Boolean,
        rng: Random,
    ): GeneratedRow {
        if (tutorialActive) {
            val t = tutorialSpec(gatesSpawned)
            if (t != null) return GeneratedRow(t, Double.NaN, Double.NaN, -1)
        }
        val d = Difficulty.difficulty(score)
        // Warm-up: the first rows are extra wide and near the start height.
        val warm = max(0.0, 1 - gatesSpawned.toDouble() / Tuning.WARMUP_GATES)
        val size = 5.2 - 1.4 * d + 1.8 * warm
        val lo = max(size / 2 + 1.2, SimMath.lerp(0.0, 4.2, warm))
        val hi = max(lo, min(Tuning.CEILING - 2.5 - size / 2, SimMath.lerp(99.0, 6.5, warm)))
        val spec = arrayOfNulls<Gap>(3)
        for (i in 0 until 3) spec[i] = Gap(center = lo + rng.nextDouble() * (hi - lo), size = size)

        // After a short warm-up, block some lanes (always keep at least one open).
        if (score >= 3 && gatesSpawned >= Tuning.WARMUP_GATES) {
            val pBlock = 0.2 + 0.3 * d
            val order = intArrayOf(0, 1, 2)
            for (i in 2 downTo 1) {
                val j = floor(rng.nextDouble() * (i + 1)).toInt()
                val tmp = order[i]; order[i] = order[j]; order[j] = tmp
            }
            var open = 3
            for (i in order) {
                if (open > 1 && rng.nextDouble() < pBlock) {
                    spec[i] = null
                    open--
                }
            }
        }

        // Each zone has a speciality (the zone this row will be in).
        val zone = (gatesSpawned / Tuning.ZONE_ROWS) % Tuning.BIOME_COUNT
        val moveBoost = if (zone == 2) 1.6 else 1.0
        val plantBoost = if (zone == 3) 1.8 else 1.0
        val pulseChance = if (zone == 1) 0.45 else if (gatesSpawned > Tuning.ZONE_ROWS * Tuning.BIOME_COUNT) 0.15 else 0.0

        val open = (0 until 3).filter { spec[it] != null }
        // Keep one open lane "easy" (no plant, no movement).
        val easy = open[floor(rng.nextDouble() * open.size).toInt()]
        for (i in open) {
            if (i == easy && open.size > 1) continue
            val g = spec[i]!!
            if (pulseChance != 0.0 && rng.nextDouble() < pulseChance) {
                // Breathing gap: opens and narrows with the beat.
                g.pulse = true
                g.size = max(g.size, 4.6)
                g.plantOffset = if (rng.nextDouble() < 0.5) 0.0 else 1.0
            } else if (score >= 6 && rng.nextDouble() < (0.25 + 0.3 * d) * moveBoost) {
                // Moving gap: slides up and down within the playable range.
                g.amp = min(1.2 + rng.nextDouble() * 1.3, (hi - lo) / 2)
                g.center = SimMath.clamp(g.center, lo + g.amp, hi - g.amp)
                g.speed = (1.2 + rng.nextDouble() * 1.2) * (if (zone == 2) 1.25 else 1.0)
                g.phase = rng.nextDouble() * PI * 2
            } else if (score >= 10 && rng.nextDouble() < (0.25 + 0.25 * d) * plantBoost) {
                // Spiky cactus: pops out of the lower pipe in time with the music.
                g.plant = true
                g.plantOffset = if (rng.nextDouble() < 0.5) 0.0 else 2.0
            }
        }
        makeReachable(spec, lo, hi, prevGaps, score)
        return GeneratedRow(spec.map { it?.toSpec() }, lo, hi, easy)
    }
}
