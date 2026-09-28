package de.robinrehbein.birdy.game.sim

import kotlin.math.PI
import kotlin.math.cos

/** Beat-driven gap/plant timing (world.js:854-872, world.md §7.4). */
object PlantTiming {
    /** Breathing gap size factor: open (1) -> narrow (0.7) -> open over two beats. */
    fun pulseScale(beat: Double): Double = 0.85 + 0.15 * cos(beat * PI)

    /** The flower peeks out a beat before the plant rises (no hitbox). */
    fun plantPeek(beat: Double): Boolean {
        val p = ((beat % 4) + 4) % 4
        return p >= 1.1 && p < 2
    }

    /** Rise 0..1 over a 4-beat cycle: hidden, pop up, chomp, retreat (smoothstep edges). */
    fun plantRise(beat: Double): Double {
        val p = ((beat % 4) + 4) % 4
        if (p < 2) return 0.0
        if (p < 2.4) return SimMath.smoothstep(p, 2.0, 2.4)
        if (p < 3.5) return 1.0
        if (p < 3.9) return 1 - SimMath.smoothstep(p, 3.5, 3.9)
        return 0.0
    }
}
