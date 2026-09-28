package de.robinrehbein.birdy.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

enum class FilterType { LOWPASS, HIGHPASS, BANDPASS }

/**
 * BiquadFilterNode with the Web Audio spec coefficient formulas (RBJ cookbook). As in Web Audio,
 * `Q` of lowpass/highpass is in dB (alpha = sin(w0) / (2 * 10^(Q/20))); bandpass uses linear Q.
 * Transposed direct form II; reusable via [set] without allocation.
 */
class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0

    fun set(type: FilterType, frequency: Double, q: Double, sampleRate: Int) {
        val nyquist = sampleRate / 2.0
        val f = (frequency / nyquist).coerceIn(0.0, 1.0)
        val w0 = PI * f
        val cw = cos(w0)
        val alpha = when (type) {
            FilterType.BANDPASS -> sin(w0) / (2 * q)
            else -> sin(w0) / (2 * 10.0.pow(q / 20))
        }
        val nb0: Double; val nb1: Double; val nb2: Double
        when (type) {
            FilterType.LOWPASS -> { nb0 = (1 - cw) / 2; nb1 = 1 - cw; nb2 = (1 - cw) / 2 }
            FilterType.HIGHPASS -> { nb0 = (1 + cw) / 2; nb1 = -(1 + cw); nb2 = (1 + cw) / 2 }
            FilterType.BANDPASS -> { nb0 = alpha; nb1 = 0.0; nb2 = -alpha }
        }
        val a0 = 1 + alpha
        b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0
        a1 = -2 * cw / a0; a2 = (1 - alpha) / a0
    }

    fun reset() { z1 = 0.0; z2 = 0.0 }

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    /** Magnitude response at [frequency] (for tests). */
    fun magnitude(frequency: Double, sampleRate: Int): Double {
        val w = 2 * PI * frequency / sampleRate
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2; val ni = -(b1 * s1 + b2 * s2)
        val dr = 1 + a1 * c1 + a2 * c2; val di = -(a1 * s1 + a2 * s2)
        return kotlin.math.sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
    }
}
