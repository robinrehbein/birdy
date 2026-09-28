package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.audio.dsp.Automation
import de.robinrehbein.birdy.audio.dsp.Wave
import de.robinrehbein.birdy.audio.music.MusicData
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** audio-timing.json: transport constants, beat formula, envelope shapes; plus the duck curve. */
class TimingGoldenTest {
    private val g = Golden.json("audio-timing.json").jsonObject
    private val rate = 44100

    @Test
    fun constantsAndBeatFormula() {
        assertEquals(g["BPM"]!!.jsonPrimitive.int, MusicData.BPM)
        assertEquals(g["STEP"]!!.jsonPrimitive.double, MusicData.STEP)
        assertEquals(g["loopLengthSteps"]!!.jsonPrimitive.int, MusicData.LOOP_STEPS)
        assertEquals(g["startOffsetSec"]!!.jsonPrimitive.double, MusicData.START_OFFSET)
        assertEquals(g["quarterNoteSec"]!!.jsonPrimitive.double, MusicData.STEP * 4)
        for (s in g["beatSamples"]!!.jsonArray) {
            val o = s.jsonObject
            val beat = SynthEngine.beatAt(o["elapsedSec"]!!.jsonPrimitive.double)
            assertEquals(o["beat"]!!.jsonPrimitive.double, beat, 1e-6)
        }
    }

    @Test
    fun liveTransportPublishesBeatFromSampleClock() {
        val e = SynthEngine(rate, Random(1))
        val buf = FloatArray(1000)
        e.render(buf, 1000) // advance the clock a bit before starting
        e.commands.push(SynthEngine.Op.MUSIC_START, 0.0, 7)
        e.render(buf, 1000)
        assertEquals(7, e.publishedGeneration)
        val start = e.startTime
        // Started at a quantum boundary + 0.05 s.
        assertEquals(0.0, (start - 0.05) * rate % SynthEngine.QUANTUM, 1e-6)
        repeat(200) { e.render(buf, 1000) }
        val expected = SynthEngine.beatAt(e.currentTime - start)
        assertEquals(expected, e.publishedBeat, 1e-12)
        assertTrue(e.publishedBeat > 8)
    }

    @Test
    fun toneEnvelopeFollowsExponentialRamps() {
        val env = g["toneEnvelope"]!!.jsonObject
        assertEquals(env["attackSec"]!!.jsonPrimitive.double, Automation.ATTACK)
        assertEquals(env["attackFrom"]!!.jsonPrimitive.double, Automation.FLOOR)
        val v = Voice().apply { envelopeProbe = true }
        val at = 0.0123
        val dur = 0.2
        val vol = 0.3
        v.startTone(440.0, 440.0, dur, Wave.SQUARE, vol, at, Bus.SFX, rate)
        val out = FloatArray(20000)
        v.render(out, 0, out.size, rate, FloatArray(1))
        val first = Voice.frameAt(at, rate).toInt()
        val stop = Voice.frameAt(at + dur + 0.02, rate).toInt()
        assertEquals(0f, out[first - 1])
        for (f in first until stop) {
            val expected = Automation.toneGain(vol, dur, f.toDouble() / rate - at)
            assertEquals(expected, out[f].toDouble(), 2e-6 + expected * 1e-4, "frame $f")
        }
        assertEquals(0f, out[stop])
        // Peak at the end of the attack, exactly vol.
        assertEquals(vol, Automation.toneGain(vol, dur, 0.006), 1e-12)
        assertEquals(Automation.FLOOR, Automation.toneGain(vol, dur, dur), 1e-12)
        assertTrue(!v.active)
    }

    @Test
    fun noiseEnvelopeAndStop() {
        val env = g["noiseHitEnvelope"]!!.jsonObject
        assertEquals(env["decayToFloor"]!!.jsonPrimitive.double, Automation.FLOOR)
        val v = Voice().apply { envelopeProbe = true }
        val at = 0.5
        v.startNoise(at, 0.12, 0.35, 1500.0, Bus.MUSIC, 0, rate)
        val out = FloatArray(40000)
        v.render(out, 0, out.size, rate, FloatArray(rate))
        val first = Voice.frameAt(at, rate).toInt()
        assertEquals(0.35, out[first].toDouble(), 1e-6)
        for (f in first until Voice.frameAt(at + 0.14, rate).toInt()) {
            val expected = Automation.noiseGain(0.35, 0.12, f.toDouble() / rate - at)
            assertEquals(expected, out[f].toDouble(), 2e-6 + expected * 1e-4)
        }
        assertEquals(0f, out[Voice.frameAt(at + 0.14, rate).toInt()])
    }

    @Test
    fun glideIsExponential() {
        // Frequency glide: count zero crossings of a sine 150 -> 45 Hz kick over 0.14 s.
        val v = Voice()
        v.startTone(150.0, 45.0, 0.14, Wave.SINE, 0.9, 0.0, Bus.MUSIC, rate)
        val out = FloatArray(rate)
        v.render(out, 0, out.size, rate, FloatArray(1))
        // Phase integral of f0*(r)^(t/d): d*(f1-f0)/ln(f1/f0) cycles.
        val cycles = 0.14 * (45.0 - 150.0) / kotlin.math.ln(45.0 / 150.0)
        var crossings = 0
        for (i in 1 until (0.14 * rate).toInt()) if (out[i - 1] < 0 != out[i] < 0) crossings++
        assertEquals(cycles * 2, crossings.toDouble(), 2.0)
    }

    @Test
    fun duckCurveMatchesSetTargetAtTime() {
        val p = DuckParam(0.3, rate)
        val t = 2.0
        p.duck(t)
        for (dt in listOf(0.0, 0.01, 0.05, 0.15, 0.5, 1.0, 1.39)) {
            val expected = 0.06 + (0.3 - 0.06) * exp(-dt / 0.05)
            assertEquals(expected, p.valueAt(t + dt), 1e-12, "dt $dt")
        }
        val v1 = 0.06 + (0.3 - 0.06) * exp(-1.4 / 0.05)
        for (dt in listOf(1.4, 1.5, 2.0, 2.6, 4.0)) {
            val expected = 0.3 + (v1 - 0.3) * exp(-(dt - 1.4) / 0.4)
            assertEquals(expected, p.valueAt(t + dt), 1e-12, "dt $dt")
        }
        // Re-duck mid-recovery starts from the current value (cancelScheduledValues semantics).
        val mid = p.valueAt(t + 2.0)
        p.duck(t + 2.0)
        assertEquals(mid, p.valueAt(t + 2.0), 1e-12)
        assertEquals(0.06 + (mid - 0.06) * exp(-0.1 / 0.05), p.valueAt(t + 2.1), 1e-12)
        // Settles back to exactly MUSIC_VOLUME.
        assertEquals(0.3, p.valueAtFrame(((t + 60) * rate).toLong()))
        assertTrue(abs(p.valueAt(t + 20) - 0.3) < 1e-9)
    }
}
