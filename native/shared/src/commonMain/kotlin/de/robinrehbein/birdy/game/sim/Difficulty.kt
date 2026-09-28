package de.robinrehbein.birdy.game.sim

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
}
