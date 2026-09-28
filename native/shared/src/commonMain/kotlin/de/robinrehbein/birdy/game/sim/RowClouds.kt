package de.robinrehbein.birdy.game.sim

/**
 * world.js pipe-row cloud LCGs (world.md §6.2). [RowCloudPicker] is `pickRowCloud()`: one draw
 * per `gate.configure()`, -1 = no cloud, 0..2 = bank seed 1..3, 3..5 = collar seed 1..3.
 */
class RowCloudPicker(seed: Int = 12345) {
    // Long: h * 9301 exceeds Int.MAX_VALUE for h near 233280.
    private var state = seed.toLong()

    fun next(): Int {
        state = (state * 9301 + 49297) % 233280
        val r = state / 233280.0
        return if (r < 1.0 / 3) -1 else kotlin.math.floor(((r - 1.0 / 3) * 1.5) * VARIANTS).toInt() % VARIANTS
    }

    companion object {
        const val VARIANTS = 6

        /** makeBankGeometry's per-geometry `rnd()` for [seed] 1..3. */
        fun bankRandom(seed: Int): () -> Double = lcg(seed * 9301 + 49297)

        /** makeCollarGeometry's per-geometry `rnd()` for [seed] 1..3. */
        fun collarRandom(seed: Int): () -> Double = lcg(seed * 7919 + 104729)

        private fun lcg(start: Int): () -> Double {
            var h = start.toLong()
            return {
                h = (h * 9301 + 49297) % 233280
                h / 233280.0
            }
        }
    }
}
