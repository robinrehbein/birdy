package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.audio.dsp.Automation
import de.robinrehbein.birdy.audio.dsp.Biquad
import de.robinrehbein.birdy.audio.dsp.FilterType
import de.robinrehbein.birdy.audio.dsp.Oscillator
import de.robinrehbein.birdy.audio.dsp.Wave
import kotlin.math.ceil
import kotlin.math.pow

/**
 * One pooled `tone()` (oscillator -> gain) or `noiseHit()` (noise buffer -> highpass -> gain)
 * voice. Times are converted to frame indices once; envelopes advance by per-frame ratios and
 * are re-anchored to the exact Web Audio formula at every segment boundary.
 */
internal class Voice {
    var active = false
    var isNoise = false
    var bus = Bus.SFX
    var startFrame = 0L
    var endFrame = 0L

    // Timeline in seconds (absolute) for re-anchoring.
    private var t0 = 0.0
    private var dur = 0.0
    private var vol = 0.0

    // Gain envelope segments: 0 = attack (tone only), 1 = decay, 2 = floor hold.
    private var seg = 0
    private var segEnd = 0L
    private var gain = 0.0
    private var gainMul = 1.0

    // Oscillator
    private var wave = Wave.SINE
    private var phase = 0.0
    private var freq = 0.0
    private var freqTo = 0.0
    private var freqMul = 1.0
    private var glideEnd = 0L

    // Noise
    private var noisePos = 0
    private val filter = Biquad()

    /** Test hook: output the gain envelope instead of the waveform. */
    var envelopeProbe = false

    fun startTone(freq: Double, to: Double, dur: Double, wave: Wave, vol: Double, at: Double, bus: Bus, sampleRate: Int) {
        isNoise = false
        this.bus = bus
        this.wave = wave
        t0 = at; this.dur = dur; this.vol = vol
        startFrame = frameAt(at, sampleRate)
        endFrame = frameAt(at + dur + Automation.TAIL, sampleRate)
        phase = 0.0
        freqTo = to
        val first = startFrame.toDouble() / sampleRate
        if (to != freq) {
            glideEnd = frameAt(at + dur, sampleRate)
            this.freq = Automation.expRamp(freq, at, to, at + dur, first)
            freqMul = (to / freq).pow(1.0 / (dur * sampleRate))
        } else {
            glideEnd = startFrame
            this.freq = freq
            freqMul = 1.0
        }
        enterSegment(if (first < at + Automation.ATTACK) 0 else 1, startFrame, sampleRate)
        active = true
    }

    fun startNoise(at: Double, dur: Double, vol: Double, cutoff: Double, bus: Bus, offsetFrames: Int, sampleRate: Int) {
        isNoise = true
        this.bus = bus
        t0 = at; this.dur = dur; this.vol = vol
        startFrame = frameAt(at, sampleRate)
        endFrame = frameAt(at + dur + Automation.TAIL, sampleRate)
        noisePos = offsetFrames
        filter.set(FilterType.HIGHPASS, cutoff, 1.0, sampleRate)
        filter.reset()
        enterSegment(1, startFrame, sampleRate)
        active = true
    }

    private fun enterSegment(s: Int, frame: Long, sampleRate: Int) {
        seg = s
        val t = frame.toDouble() / sampleRate
        val attackEnd = if (isNoise) t0 else t0 + Automation.ATTACK
        when (s) {
            0 -> {
                segEnd = frameAt(attackEnd, sampleRate)
                gain = Automation.expRamp(Automation.FLOOR, t0, vol, attackEnd, t)
                gainMul = (vol / Automation.FLOOR).pow(1.0 / (Automation.ATTACK * sampleRate))
                if (segEnd <= frame) { enterSegment(1, frame, sampleRate); return }
            }
            1 -> {
                val end = t0 + dur
                segEnd = frameAt(end, sampleRate)
                if (segEnd <= frame) { enterSegment(2, frame, sampleRate); return }
                gain = Automation.expRamp(vol, attackEnd, Automation.FLOOR, end, t)
                gainMul = (Automation.FLOOR / vol).pow(1.0 / ((end - attackEnd) * sampleRate))
            }
            else -> {
                segEnd = Long.MAX_VALUE
                gain = Automation.FLOOR
                gainMul = 1.0
            }
        }
    }

    /**
     * Adds this voice into `out[0 until frames]`, which covers frames [blockStart, blockStart+frames).
     * Returns false once the voice has finished.
     */
    fun render(out: FloatArray, blockStart: Long, frames: Int, sampleRate: Int, noise: FloatArray): Boolean {
        val blockEnd = blockStart + frames
        if (startFrame >= blockEnd) return true
        val from = maxOf(startFrame, blockStart)
        val to = minOf(endFrame, blockEnd)
        var f = from
        val invRate = 1.0 / sampleRate
        while (f < to) {
            if (f >= segEnd) enterSegment(seg + 1, f, sampleRate)
            val i = (f - blockStart).toInt()
            val s: Double
            if (isNoise) {
                s = filter.process(noise[noisePos].toDouble())
                if (++noisePos >= noise.size) noisePos = 0
            } else {
                val dt = freq * invRate
                s = Oscillator.sample(wave, phase, dt)
                phase += dt
                if (phase >= 1.0) phase -= phase.toInt()
                if (f + 1 < glideEnd) freq *= freqMul else freq = freqTo
            }
            out[i] += (if (envelopeProbe) gain else s * gain).toFloat()
            gain *= gainMul
            f++
        }
        if (to >= endFrame) { active = false; return false }
        return true
    }

    companion object {
        /** First frame whose time is >= [t] (tolerant to float noise in t * rate). */
        fun frameAt(t: Double, sampleRate: Int): Long = ceil(t * sampleRate - 1e-7).toLong()
    }
}
