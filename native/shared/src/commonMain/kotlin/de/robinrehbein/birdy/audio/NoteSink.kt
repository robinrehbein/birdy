package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.audio.dsp.Wave

/** Mixer bus a voice feeds (audio.js `dest`). */
enum class Bus { MUSIC, SFX }

/** What a scheduled voice is (for traces/tests; the synth ignores it). */
enum class Role { KICK, SNARE, HAT, DESERT_BLIP, BREAKDOWN_TICK, BASS, ARP, SPARKLE, LEAD, HARMONY, SFX }

/**
 * Receiver of audio.js `tone()` / `noiseHit()` calls with absolute start times in seconds.
 * The live synth schedules pooled voices; tests record the calls. Called on the audio thread,
 * so implementations must not block.
 */
interface NoteSink {
    fun tone(role: Role, freq: Double, to: Double, dur: Double, wave: Wave, vol: Double, at: Double, bus: Bus)
    fun noise(role: Role, at: Double, dur: Double, vol: Double, cutoff: Double, bus: Bus)
}
