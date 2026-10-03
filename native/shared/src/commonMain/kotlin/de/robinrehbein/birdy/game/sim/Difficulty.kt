package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.Tuning
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Difficulty and pacing curve (main.js:743-756, main-a.md §5). */
object Difficulty {
    /** 0..1, saturates at score 40. */
    fun difficulty(score: Int): Double = min(1.0, score / 40.0)

    /** Forward speed target; creeps up towards 42 after score 40. */
    fun baseSpeed(score: Int): Double {
        val over = max(0.0, score - 40.0)
        return 18 + 16 * difficulty(score) + 8 * (1 - exp(-over / 50))
    }

    /** Row spacing in world units: rows are spaced by time, not distance. */
    fun spacing(score: Int): Double = baseSpeed(score) * (1.7 - 0.6 * difficulty(score))

    // Native additions (not in main.js): rows breathe in waves per zone and keep getting a bit
    // harder after score 40. Speed and spacing above stay the JS curve (golden fixtures).

    /** Late-game extra on top of [difficulty]: approaches [LATE_MAX] slowly after score 40. */
    const val LATE_MAX = 0.2
    const val LATE_SCALE = 60.0
    /** Wave height at full difficulty; scaled down by [difficulty] before score 40. */
    const val WAVE_AMP = 0.2
    /** Smallest gap a row may get, whatever the intensity (bird hitbox is 0.8 high). */
    const val MIN_GAP = 3.6
    /** A cactus never sits in a gap smaller than the JS curve's minimum (3.8 at difficulty 1). */
    const val MIN_PLANT_GAP = 3.8

    /** 0 on the first row after a coin rush .. 1 on the last row before the next one. */
    fun zonePos(gatesSpawned: Int): Double = (gatesSpawned % Tuning.ZONE_ROWS).toDouble() / (Tuning.ZONE_ROWS - 1)

    /** Per-zone wave: dips after the coin rush, ramps up towards the next one; 0 in zone 1. */
    fun wave(score: Int, gatesSpawned: Int): Double {
        if (gatesSpawned < Tuning.ZONE_ROWS) return 0.0
        return WAVE_AMP * difficulty(score) * (zonePos(gatesSpawned) - 0.4)
    }

    /**
     * What row generation scales with: [difficulty] plus the zone [wave] and the late-game extra.
     * Equals [difficulty] for the whole first zone; may exceed 1 (gap size is floored at [MIN_GAP]).
     */
    fun intensity(score: Int, gatesSpawned: Int): Double {
        val over = max(0.0, score - 40.0)
        return difficulty(score) + LATE_MAX * (1 - exp(-over / LATE_SCALE)) + wave(score, gatesSpawned)
    }
}
