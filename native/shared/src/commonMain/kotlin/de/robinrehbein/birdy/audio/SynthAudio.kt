package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.audio.SynthEngine.Op
import de.robinrehbein.birdy.platform.KeyValueStore
import de.robinrehbein.birdy.platform.StorageKeys
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * [GameAudio] on the procedural synth. Game-thread calls resolve their random/timing parameters
 * here (flap jitter, coin combo) and are queued to the audio thread via [SynthEngine.commands];
 * nothing blocks. Like audio.js, sounds and music.start() before [unlock] are skipped, while
 * theme/mode/hype changes are kept.
 */
class SynthAudio(
    private val out: AudioOut,
    private val storage: KeyValueStore,
    private val rng: Random,
    private val nowMillis: () -> Double = monotonicMillis(),
) : GameAudio {
    override var muted: Boolean = runCatching { storage.getString(StorageKeys.MUTED) == "1" }.getOrDefault(false)
        private set

    val engine = SynthEngine(out.sampleRate, Random(rng.nextLong()), if (muted) 0.0 else 1.0)

    private var unlocked = false
    private var suspended = false
    private var playing = false
    private var generation = 0
    private val coins = CoinCombo()

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        runCatching { storage.putString(StorageKeys.MUTED, if (muted) "1" else "0") }
        engine.commands.push(Op.MASTER, if (muted) 0.0 else 1.0)
    }

    override fun setSuspended(suspended: Boolean) {
        this.suspended = suspended
        if (unlocked) out.setPaused(suspended)
    }

    override fun unlock() {
        if (unlocked) return
        unlocked = true
        out.start(engine::render)
        if (suspended) out.setPaused(true)
    }

    override fun sfx(effect: Sfx) {
        if (!unlocked) return
        when (effect) {
            Sfx.Flap -> push(SfxId.FLAP, SfxBank.flapK(rng.nextDouble()))
            Sfx.Swoosh -> push(SfxId.SWOOSH)
            Sfx.Point -> push(SfxId.POINT)
            Sfx.Coin -> push(SfxId.COIN, coins.hit(nowMillis()))
            is Sfx.PowerUp -> push(SfxId.POWERUP)
            Sfx.PowerDown -> push(SfxId.POWERDOWN)
            is Sfx.Near -> push(SfxId.NEAR, effect.chain.toDouble())
            Sfx.Zone -> push(SfxId.ZONE)
            Sfx.Bounce -> push(SfxId.BOUNCE)
            Sfx.Hit -> push(SfxId.HIT)
        }
    }

    private fun push(id: Int, arg: Double = 0.0) {
        engine.commands.push(Op.SFX, arg, id)
    }

    override fun musicStart() {
        if (!unlocked || playing) return
        generation++
        if (engine.commands.push(Op.MUSIC_START, 0.0, generation)) playing = true
    }

    override fun musicStop() {
        playing = false
        engine.commands.push(Op.MUSIC_STOP)
    }

    /**
     * Beats since transport start from the audio clock. Null while stopped and until the audio
     * thread has started this transport (one render block after [musicStart]).
     */
    override fun musicBeat(): Double? {
        if (!playing) return null
        if (engine.publishedGeneration != generation) return null
        return engine.publishedBeat
    }

    override fun setHype(hype: Boolean) {
        engine.commands.push(Op.HYPE, 0.0, if (hype) 1 else 0)
    }

    override fun setTheme(index: Int) {
        engine.commands.push(Op.THEME, 0.0, index)
    }

    override fun setMode(mode: MusicMode) {
        engine.commands.push(Op.MODE, 0.0, if (mode == MusicMode.Menu) 1 else 0)
    }

    override fun duck() {
        if (unlocked) engine.commands.push(Op.DUCK)
    }

    /** Stops the output thread (app teardown). */
    fun release() {
        out.stop()
    }

    companion object {
        fun monotonicMillis(): () -> Double {
            val origin = TimeSource.Monotonic.markNow()
            return { origin.elapsedNow().inWholeMicroseconds / 1000.0 }
        }
    }
}
