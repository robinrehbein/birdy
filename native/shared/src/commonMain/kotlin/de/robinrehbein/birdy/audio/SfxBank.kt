package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.audio.dsp.Wave
import de.robinrehbein.birdy.audio.music.MusicData.midi
import kotlin.math.min
import kotlin.math.pow

/** Resolved sound-effect ids carried through the command ring. */
object SfxId {
    const val FLAP = 0
    const val SWOOSH = 1
    const val POINT = 2
    const val COIN = 3
    const val POWERUP = 4
    const val POWERDOWN = 5
    const val NEAR = 6
    const val ZONE = 7
    const val BOUNCE = 8
    const val HIT = 9
}

/**
 * audio.js `sfx.*` (audio.js:338-388) as `tone()`/`noiseHit()` calls relative to [now].
 * [arg] is the game-thread-resolved parameter: flap pitch jitter k, coin pitch ratio k, near chain.
 */
object SfxBank {
    private val POWERUP_NOTES = intArrayOf(60, 64, 67, 72, 76, 79, 84)
    private val POWERDOWN_NOTES = intArrayOf(79, 74, 67, 62)
    private val NEAR_OFFSETS = intArrayOf(0, 4, 7, 12)
    private val ZONE_NOTES = intArrayOf(72, 76, 79, 84, 88)

    fun play(id: Int, arg: Double, now: Double, sink: NoteSink) {
        when (id) {
            SfxId.FLAP -> {
                tone(sink, now, 380 * arg, 620 * arg, 0.09, Wave.TRIANGLE, 0.18)
                sink.noise(Role.SFX, now, 0.06, 0.05, 3000.0, Bus.SFX)
            }
            SfxId.SWOOSH -> sink.noise(Role.SFX, now, 0.18, 0.12, 1200.0, Bus.SFX)
            SfxId.POINT -> {
                tone(sink, now, 988.0, 988.0, 0.08, Wave.SQUARE, 0.06)
                tone(sink, now, 1319.0, 1319.0, 0.16, Wave.SQUARE, 0.06, 0.07)
            }
            SfxId.COIN -> {
                tone(sink, now, 1568 * arg, 1568 * arg, 0.06, Wave.SQUARE, 0.05)
                tone(sink, now, 2093 * arg, 2093 * arg, 0.12, Wave.SQUARE, 0.05, 0.05)
            }
            SfxId.POWERUP -> for (i in POWERUP_NOTES.indices) {
                val f = midi(POWERUP_NOTES[i])
                tone(sink, now, f, f, 0.1, Wave.SQUARE, 0.06, i * 0.045)
            }
            SfxId.POWERDOWN -> for (i in POWERDOWN_NOTES.indices) {
                val f = midi(POWERDOWN_NOTES[i])
                tone(sink, now, f, f, 0.1, Wave.TRIANGLE, 0.12, i * 0.06)
            }
            SfxId.NEAR -> {
                val base = nearBase(arg.toInt())
                for (i in NEAR_OFFSETS.indices) {
                    val f = midi(base + NEAR_OFFSETS[i])
                    tone(sink, now, f, f, 0.07, Wave.SQUARE, 0.05, i * 0.035)
                }
            }
            SfxId.ZONE -> {
                tone(sink, now, 300.0, 900.0, 0.35, Wave.TRIANGLE, 0.12)
                for (i in ZONE_NOTES.indices) {
                    val f = midi(ZONE_NOTES[i])
                    tone(sink, now, f, f, 0.14, Wave.SQUARE, 0.05, 0.2 + i * 0.07)
                }
            }
            SfxId.BOUNCE -> tone(sink, now, 200.0, 500.0, 0.15, Wave.TRIANGLE, 0.2)
            SfxId.HIT -> {
                tone(sink, now, 220.0, 60.0, 0.35, Wave.SAWTOOTH, 0.18)
                tone(sink, now, 120.0, 40.0, 0.5, Wave.SQUARE, 0.1, 0.05)
                sink.noise(Role.SFX, now, 0.25, 0.3, 400.0, Bus.SFX)
            }
        }
    }

    /** near(): 76 + min(chain, 6) * 2. */
    fun nearBase(chain: Int): Int = 76 + min(chain, 6) * 2

    /** flap(): k = 0.94 + random * 0.12. */
    fun flapK(random: Double): Double = 0.94 + random * 0.12

    private fun tone(sink: NoteSink, now: Double, freq: Double, to: Double, dur: Double, wave: Wave, vol: Double, delay: Double = 0.0) =
        sink.tone(Role.SFX, freq, to, dur, wave, vol, now + delay, Bus.SFX)
}

/** sfx.coin combo: rapid coins (< 700 ms apart) climb a semitone each, up to 7 (audio.js:354-362). */
class CoinCombo {
    var combo = 0
        private set
    private var lastCoin = 0.0

    /** Registers a coin at monotonic [nowMs]; returns the pitch ratio k = 2^(combo/12). */
    fun hit(nowMs: Double): Double {
        combo = if (nowMs - lastCoin < 700) min(combo + 1, 7) else 0
        lastCoin = nowMs
        return 2.0.pow(combo / 12.0)
    }
}
