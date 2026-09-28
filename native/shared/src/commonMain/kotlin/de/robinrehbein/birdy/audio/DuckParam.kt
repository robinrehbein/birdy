package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.audio.dsp.Automation

/**
 * The music bus gain AudioParam: value [base] (MUSIC_VOLUME) plus `music.duck()`'s automation:
 * cancelScheduledValues(t); setTargetAtTime(0.06, t, 0.05); setTargetAtTime(0.3, t + 1.4, 0.4).
 */
class DuckParam(private val base: Double, private val sampleRate: Int) {
    private var active = false
    private var t0 = 0.0
    private var v0 = base
    private var t1 = 0.0
    private var v1 = base

    /** Starts a duck at absolute time [t] from the param's current value. */
    fun duck(t: Double) {
        v0 = valueAt(t)
        t0 = t
        t1 = t + RECOVER_DELAY
        v1 = Automation.target(v0, DUCK_LEVEL, t0, DUCK_TC, t1)
        active = true
    }

    fun valueAt(t: Double): Double {
        if (!active) return base
        return if (t < t1) Automation.target(v0, DUCK_LEVEL, t0, DUCK_TC, t)
        else Automation.target(v1, base, t1, RECOVER_TC, t)
    }

    /** Value at [frame]; snaps back to the steady value once the curve is within float precision. */
    fun valueAtFrame(frame: Long): Double {
        if (!active) return base
        val t = frame.toDouble() / sampleRate
        if (t > t1 + RECOVER_TC * 40) { active = false; return base }
        return valueAt(t)
    }

    companion object {
        const val DUCK_LEVEL = 0.06
        const val DUCK_TC = 0.05
        const val RECOVER_DELAY = 1.4
        const val RECOVER_TC = 0.4
    }
}
