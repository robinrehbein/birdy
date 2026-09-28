package de.robinrehbein.birdy.view.fx

import kotlin.random.Random

/** scripts/native-golden/lib-prng.mjs `mulberry32`, the seeded `Math.random()` of the goldens. */
class Mulberry32(seed: Int) : Random() {
    private var a = seed

    override fun nextDouble(): Double {
        a += 0x6d2b79f5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }

    override fun nextBits(bitCount: Int): Int = (nextDouble() * 4294967296.0).toLong().toInt() ushr (32 - bitCount)
}
