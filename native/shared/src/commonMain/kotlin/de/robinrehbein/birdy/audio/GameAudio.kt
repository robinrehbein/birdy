package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.game.PowerType

/** Sound effects (audio.js `sfx.*`). Parameters are the JS arguments. */
sealed class Sfx {
    data object Flap : Sfx()
    data object Swoosh : Sfx()
    data object Point : Sfx()
    /** Combo pitch is tracked inside the synth from call timing, as in JS. */
    data object Coin : Sfx()
    data class PowerUp(val type: PowerType) : Sfx()
    data object PowerDown : Sfx()
    data class Near(val chain: Int) : Sfx()
    data object Zone : Sfx()
    data object Bounce : Sfx()
    data object Hit : Sfx()
}

enum class MusicMode { Game, Menu }

/**
 * Procedural music + SFX facade (audio.js `audio`, `music`, `sfx`). Called from the game thread;
 * implementations hand commands to the audio thread through a lock-free queue and never block.
 * Mute is persisted under `birdy-muted` ("1"/"0").
 */
interface GameAudio {
    val muted: Boolean
    fun setMuted(muted: Boolean)
    /** App background / pause screen (AudioContext suspend/resume). */
    fun setSuspended(suspended: Boolean)
    /** First user gesture: starts output (JS unlock gate). */
    fun unlock()

    fun sfx(effect: Sfx)

    fun musicStart()
    fun musicStop()
    /** Beats since transport start (quarter notes, monotonic), or null when music isn't playing. */
    fun musicBeat(): Double?
    fun setHype(hype: Boolean)
    /** Zone theme 0..3, applied at the next bar line. */
    fun setTheme(index: Int)
    /** Applied at the next bar line. */
    fun setMode(mode: MusicMode)
    fun duck()
}

/** No-op [GameAudio] for tests and headless screenshots. */
class SilentAudio : GameAudio {
    override var muted = false
        private set
    override fun setMuted(muted: Boolean) { this.muted = muted }
    override fun setSuspended(suspended: Boolean) = Unit
    override fun unlock() = Unit
    override fun sfx(effect: Sfx) = Unit
    override fun musicStart() = Unit
    override fun musicStop() = Unit
    override fun musicBeat(): Double? = null
    override fun setHype(hype: Boolean) = Unit
    override fun setTheme(index: Int) = Unit
    override fun setMode(mode: MusicMode) = Unit
    override fun duck() = Unit
}
