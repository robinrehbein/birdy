package de.robinrehbein.birdy.audio

/**
 * Platform PCM sink (AudioTrack on Android, AVAudioEngine source node on iOS). Pulls mono float
 * samples in [-1, 1] from a render callback on its own high-priority audio thread.
 */
interface AudioOut {
    val sampleRate: Int

    /** Starts the audio thread; [render] must fill `buffer[0 until frames]` without blocking. */
    fun start(render: (buffer: FloatArray, frames: Int) -> Unit)

    /** Pauses output (AudioContext.suspend equivalent); [render] is not called while paused. */
    fun setPaused(paused: Boolean)

    fun stop()
}

/** Discards audio; used by tests, desktop screenshots and when output init fails. */
object NullAudioOut : AudioOut {
    override val sampleRate = 44100
    override fun start(render: (buffer: FloatArray, frames: Int) -> Unit) = Unit
    override fun setPaused(paused: Boolean) = Unit
    override fun stop() = Unit
}
