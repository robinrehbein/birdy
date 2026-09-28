package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.audio.dsp.Biquad
import de.robinrehbein.birdy.audio.dsp.Compressor
import de.robinrehbein.birdy.audio.dsp.FilterType
import de.robinrehbein.birdy.audio.dsp.Oscillator
import de.robinrehbein.birdy.audio.dsp.PartitionedConvolver
import de.robinrehbein.birdy.audio.dsp.Reverb
import de.robinrehbein.birdy.audio.dsp.Wave
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DspTest {
    @Test
    fun partitionedConvolutionEqualsDirect() {
        val rng = Random(3)
        val ir = FloatArray(700) { (rng.nextDouble() * 2 - 1).toFloat() }
        val x = FloatArray(64 * 20) { (rng.nextDouble() * 2 - 1).toFloat() }
        val conv = PartitionedConvolver(ir, 0, ir.size, 64)
        val y = FloatArray(x.size)
        for (b in 0 until 20) conv.process(x, b * 64, y, b * 64)
        for (n in x.indices) {
            var acc = 0.0
            for (k in ir.indices) if (n - k >= 0) acc += ir[k].toDouble() * x[n - k]
            assertEquals(acc, y[n].toDouble(), 1e-3, "n=$n")
        }
    }

    @Test
    fun reverbHeadPlusTailEqualsDirectConvolution() {
        val rate = 8000 // short IR (9600 taps) keeps the direct reference cheap
        val rv = Reverb(rate, Random(5), 256)
        val ir = rv.ir
        val n = 256 * 60
        val x = FloatArray(n)
        x[10] = 1f; x[3000] = -0.5f; x[9000] = 0.25f
        val y = FloatArray(n)
        val inQ = FloatArray(256); val outQ = FloatArray(256)
        for (b in 0 until n / 256) {
            x.copyInto(inQ, 0, b * 256, b * 256 + 256)
            rv.process(inQ, outQ)
            outQ.copyInto(y, b * 256)
        }
        for (i in 0 until n) {
            var acc = 0.0
            for (p in intArrayOf(10, 3000, 9000)) if (i - p in ir.indices) acc += ir[i - p].toDouble() * x[p]
            assertEquals(acc, y[i].toDouble(), 1e-5, "i=$i")
        }
    }

    @Test
    fun reverbNormalizationMatchesChromium() {
        val rv = Reverb(44100, Random(9), 256)
        assertEquals((44100 * 1.2).toInt(), rv.ir.size)
        // Normalized IR RMS: 10^(-58/20) (sample-rate calibration is 1 at 44.1 kHz).
        var sum = 0.0
        for (v in rv.ir) sum += v.toDouble() * v
        assertEquals(10.0.pow(-58.0 / 20), sqrt(sum / rv.ir.size), 1e-6)
    }

    @Test
    fun biquadUsesWebAudioDbQ() {
        val lp = Biquad().apply { set(FilterType.LOWPASS, 6500.0, 0.5, 44100) }
        // Resonance at cutoff for Q in dB: |H(f0)| = 10^(Q/20).
        assertEquals(10.0.pow(0.5 / 20), lp.magnitude(6500.0, 44100), 1e-9)
        assertEquals(1.0, lp.magnitude(10.0, 44100), 1e-4)
        val hp = Biquad().apply { set(FilterType.HIGHPASS, 1500.0, 1.0, 44100) }
        assertEquals(10.0.pow(1.0 / 20), hp.magnitude(1500.0, 44100), 1e-9)
        assertTrue(hp.magnitude(50.0, 44100) < 0.01)
        val bp = Biquad().apply { set(FilterType.BANDPASS, 1000.0, 2.0, 44100) }
        assertEquals(1.0, bp.magnitude(1000.0, 44100), 1e-9)
    }

    @Test
    fun compressorCurve() {
        val c = Compressor(44100)
        assertEquals(1.0, c.staticGain(0.1))
        assertEquals(1.0, c.staticGain(0.199))
        // Above the knee the slope is 1/ratio in dB.
        val g1 = c.staticGain(10.0.pow(-1.0 / 20)) * 10.0.pow(-1.0 / 20)
        val g2 = c.staticGain(10.0.pow(-0.0 / 20))
        val yDb1 = 20 * kotlin.math.log10(g1)
        val yDb2 = 20 * kotlin.math.log10(g2)
        assertEquals(1.0 / 3, yDb2 - yDb1, 1e-6)
        // Chromium makeup: (1 / curve(0 dBFS))^0.6 is a few dB.
        assertTrue(c.makeupGain > 1.3 && c.makeupGain < 2.0, "makeup ${c.makeupGain}")
    }

    @Test
    fun oscillatorsStartAtZeroRising() {
        for (w in Wave.entries) {
            val v0 = Oscillator.sample(w, 0.1, 0.0)
            assertTrue(v0 > 0, "$w rises")
            assertTrue(abs(Oscillator.sample(w, 0.6, 0.0)) > 0)
        }
        assertEquals(0.0, Oscillator.sample(Wave.SINE, 0.0, 0.01), 1e-12)
        assertEquals(0.0, Oscillator.sample(Wave.TRIANGLE, 0.0, 0.01), 1e-12)
        assertEquals(0.0, Oscillator.sample(Wave.SAWTOOTH, 0.0, 0.01), 1e-12)
        assertEquals(1.0, Oscillator.sample(Wave.TRIANGLE, 0.25, 0.01), 1e-12)
    }
}
