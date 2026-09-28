package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.audio.music.MusicData
import kotlin.math.ceil
import kotlin.random.Random

/**
 * audio.js `renderMusic`: renders a theme offline as mono PCM through the same engine, sequencer
 * and transport as live playback. Steps start at 0.05 s and stop being scheduled at
 * `seconds - 0.2`; master gain is always 1.
 */
fun renderMusic(
    seconds: Double = 31.0,
    themeIndex: Int = 0,
    calm: Boolean = false,
    sampleRate: Int = 44100,
    rng: Random = Random.Default,
    hype: Boolean = false,
): FloatArray {
    val engine = SynthEngine(sampleRate, rng, 1.0)
    engine.sequencer.reset(themeIndex, if (calm) MusicMode.Menu else MusicMode.Game, hype)
    engine.startTransport(MusicData.START_OFFSET, until = seconds - 0.2)
    val out = FloatArray(ceil(sampleRate * seconds).toInt())
    engine.render(out, out.size)
    return out
}

/** Renders one sound effect offline (dev/QA tool; [id] from [SfxId]). */
fun renderSfx(id: Int, arg: Double = 1.0, seconds: Double = 1.5, sampleRate: Int = 44100, rng: Random = Random.Default): FloatArray {
    val engine = SynthEngine(sampleRate, rng, 1.0)
    SfxBank.play(id, arg, 0.0, engine)
    val out = FloatArray(ceil(sampleRate * seconds).toInt())
    engine.render(out, out.size)
    return out
}
