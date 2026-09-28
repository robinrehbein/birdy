package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GapSpec

/** Mutable gap while a row is generated (the JS spec objects are mutated in place). */
class Gap(
    var center: Double,
    var size: Double,
    var amp: Double = 0.0,
    var speed: Double = 0.0,
    var phase: Double = 0.0,
    var plant: Boolean = false,
    var plantOffset: Double = 0.0,
    var pulse: Boolean = false,
) {
    fun toSpec() = GapSpec(center, size, amp, speed, phase, plant, plantOffset, pulse)
}
