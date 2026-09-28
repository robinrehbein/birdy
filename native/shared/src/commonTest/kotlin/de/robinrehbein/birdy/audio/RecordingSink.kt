package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.audio.dsp.Wave

/** Records `tone()`/`noiseHit()` calls for trace comparisons. */
class RecordingSink : NoteSink {
    data class Call(
        val role: Role, val noise: Boolean, val freq: Double, val to: Double, val dur: Double,
        val wave: Wave?, val vol: Double, val at: Double, val cutoff: Double, val bus: Bus,
    )

    val calls = mutableListOf<Call>()

    override fun tone(role: Role, freq: Double, to: Double, dur: Double, wave: Wave, vol: Double, at: Double, bus: Bus) {
        calls += Call(role, false, freq, to, dur, wave, vol, at, 0.0, bus)
    }

    override fun noise(role: Role, at: Double, dur: Double, vol: Double, cutoff: Double, bus: Bus) {
        calls += Call(role, true, 0.0, 0.0, dur, null, vol, at, cutoff, bus)
    }

    fun take(): List<Call> = calls.toList().also { calls.clear() }
}

fun waveName(w: Wave?): String? = w?.name?.lowercase()
