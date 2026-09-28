package de.robinrehbein.birdy.audio.dsp

import kotlin.math.PI
import kotlin.math.sin

/** OscillatorNode types (audio.js uses all four built-ins, no custom PeriodicWave). */
enum class Wave { SINE, SQUARE, SAWTOOTH, TRIANGLE }

/**
 * Waveforms with Web Audio phase conventions (all start at 0 rising, i.e. sine-series based).
 * Square and sawtooth are band-limited with PolyBLEP and scaled like Chromium's normalized
 * built-in wave tables (peak of the full-band Fourier series incl. Gibbs overshoot = 1).
 */
object Oscillator {
    /** 1 / (1 + 2 * Wilbraham-Gibbs overshoot), Chromium's normalization for square/sawtooth. */
    const val EDGE_WAVE_GAIN = 0.8481869

    /** [phase] in [0, 1), [dt] = frequency / sampleRate. */
    fun sample(wave: Wave, phase: Double, dt: Double): Double = when (wave) {
        Wave.SINE -> sin(2 * PI * phase)
        Wave.SQUARE -> {
            var v = if (phase < 0.5) 1.0 else -1.0
            v += polyBlep(phase, dt)
            var p2 = phase + 0.5
            if (p2 >= 1) p2 -= 1
            v -= polyBlep(p2, dt)
            v * EDGE_WAVE_GAIN
        }
        Wave.SAWTOOTH -> {
            var q = phase + 0.5
            if (q >= 1) q -= 1
            (2 * q - 1 - polyBlep(q, dt)) * EDGE_WAVE_GAIN
        }
        Wave.TRIANGLE -> when {
            phase < 0.25 -> 4 * phase
            phase < 0.75 -> 2 - 4 * phase
            else -> 4 * phase - 4
        }
    }

    private fun polyBlep(t: Double, dt: Double): Double {
        if (dt <= 0) return 0.0
        return when {
            t < dt -> { val x = t / dt; x + x - x * x - 1 }
            t > 1 - dt -> { val x = (t - 1) / dt; x * x + x + x + 1 }
            else -> 0.0
        }
    }
}
