package de.robinrehbein.birdy.audio.dsp

import kotlin.math.exp
import kotlin.math.pow

/** AudioParam automation curves exactly as the Web Audio spec defines them. */
object Automation {
    /** exponentialRampToValueAtTime: v0 * (v1/v0)^((t - t0) / (t1 - t0)), holding v1 after t1. */
    fun expRamp(v0: Double, t0: Double, v1: Double, t1: Double, t: Double): Double = when {
        t <= t0 -> v0
        t >= t1 -> v1
        else -> v0 * (v1 / v0).pow((t - t0) / (t1 - t0))
    }

    /** setTargetAtTime: target + (v0 - target) * e^(-(t - t0) / timeConstant). */
    fun target(v0: Double, target: Double, t0: Double, timeConstant: Double, t: Double): Double =
        if (t <= t0) v0 else target + (v0 - target) * exp(-(t - t0) / timeConstant)

    /** tone() gain: 0.0001 -> vol over 6 ms, then -> 0.0001 at dur, then held (audio.js:95-97). */
    fun toneGain(vol: Double, dur: Double, sinceStart: Double): Double =
        if (sinceStart < ATTACK) expRamp(FLOOR, 0.0, vol, ATTACK, sinceStart)
        else expRamp(vol, ATTACK, FLOOR, dur, sinceStart)

    /** noiseHit() gain: vol at start, -> 0.0001 at dur (audio.js:110-111). */
    fun noiseGain(vol: Double, dur: Double, sinceStart: Double): Double = expRamp(vol, 0.0, FLOOR, dur, sinceStart)

    const val FLOOR = 0.0001
    const val ATTACK = 0.006
    /** osc.stop / src.stop tail past the nominal duration. */
    const val TAIL = 0.02
}
