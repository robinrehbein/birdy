package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.audio.dsp.Biquad
import de.robinrehbein.birdy.audio.dsp.Compressor
import de.robinrehbein.birdy.audio.dsp.FilterType
import de.robinrehbein.birdy.audio.dsp.Reverb
import de.robinrehbein.birdy.audio.dsp.Wave
import de.robinrehbein.birdy.audio.music.MusicData
import de.robinrehbein.birdy.audio.music.Sequencer
import kotlin.concurrent.Volatile
import kotlin.random.Random

/**
 * The audio-thread side: the audio.js `buildGraph` mix (§4), a pooled voice scheduler, the music
 * transport running off the sample clock and the command ring from the game thread. Renders in
 * fixed [QUANTUM]-frame blocks; commands and steps are handled at block starts, voices start
 * sample-accurately inside blocks. Nothing allocates after construction.
 */
class SynthEngine(val sampleRate: Int, rng: Random, masterGain: Double = 1.0) : NoteSink {
    val commands = CommandRing()
    val sequencer = Sequencer()

    private val random = rng
    private val noiseBuf = FloatArray(sampleRate) { (rng.nextDouble() * 2 - 1).toFloat() }
    private val reverb = Reverb(sampleRate, rng, QUANTUM)
    private val tone = Biquad().apply { set(FilterType.LOWPASS, 6500.0, 0.5, sampleRate) }
    private val comp = Compressor(sampleRate)
    val musicGain = DuckParam(MUSIC_VOLUME, sampleRate)
    private var master = masterGain

    private val voices = Array(MAX_VOICES) { Voice() }
    private val musicBuf = FloatArray(QUANTUM)
    private val sfxBuf = FloatArray(QUANTUM)
    private val sendBuf = FloatArray(QUANTUM)
    private val wetBuf = FloatArray(QUANTUM)
    private val outBuf = FloatArray(QUANTUM)
    private var outPos = QUANTUM

    /** Frames rendered so far (the AudioContext clock). */
    var frame = 0L
        private set
    val currentTime: Double get() = frame.toDouble() / sampleRate

    // Transport (audio.js music.start/schedule).
    var playing = false
        private set
    private var step = 0
    private var nextTime = 0.0
    var startTime = 0.0
        private set
    private var scheduleUntil = Double.POSITIVE_INFINITY
    private var generation = 0

    /** Beat published for the game thread; valid when [publishedGeneration] matches its start. */
    @Volatile var publishedBeat = 0.0
        private set
    @Volatile var publishedGeneration = 0
        private set

    /** Voices stolen because the pool was full (diagnostics). */
    var stolen = 0
        private set

    /** Starts the transport with step 0 at [at] (live: now + 0.05). */
    fun startTransport(at: Double, generation: Int = this.generation + 1, until: Double = Double.POSITIVE_INFINITY) {
        playing = true
        step = 0
        nextTime = at
        startTime = at
        scheduleUntil = until
        this.generation = generation
    }

    fun stopTransport() { playing = false }

    fun duck() = musicGain.duck(currentTime)

    fun setMaster(gain: Double) { master = gain }

    /** Fills `out[0 until frames]` with mono samples; the AudioOut render callback. */
    fun render(out: FloatArray, frames: Int) {
        var i = 0
        while (i < frames) {
            if (outPos == QUANTUM) { renderQuantum(); outPos = 0 }
            val n = minOf(frames - i, QUANTUM - outPos)
            outBuf.copyInto(out, i, outPos, outPos + n)
            outPos += n
            i += n
        }
    }

    private fun renderQuantum() {
        commands.drain { op, a, b -> handle(op, a, b) }
        val qEnd = (frame + QUANTUM).toDouble() / sampleRate
        while (playing && nextTime < qEnd && nextTime < scheduleUntil) {
            sequencer.playStep(step, nextTime, this)
            nextTime += MusicData.STEP
            step = (step + 1) % MusicData.LOOP_STEPS
        }

        musicBuf.fill(0f)
        sfxBuf.fill(0f)
        for (v in voices) {
            if (!v.active) continue
            v.render(if (v.bus == Bus.MUSIC) musicBuf else sfxBuf, frame, QUANTUM, sampleRate, noiseBuf)
        }

        for (i in 0 until QUANTUM) {
            val g = musicGain.valueAtFrame(frame + i)
            val lp = tone.process(musicBuf[i] * g).toFloat()
            musicBuf[i] = lp
            sendBuf[i] = lp * MUSIC_SEND + sfxBuf[i] * SFX_SEND
        }
        reverb.process(sendBuf, wetBuf)
        val m = master.toFloat()
        for (i in 0 until QUANTUM) {
            val mix = musicBuf[i] + sfxBuf[i] + wetBuf[i] * WET
            val y = comp.process(mix) * m
            outBuf[i] = if (y > 1f) 1f else if (y < -1f) -1f else y
        }
        frame += QUANTUM
        if (playing) {
            publishedBeat = beatAt(currentTime - startTime)
            publishedGeneration = generation
        }
    }

    private fun handle(op: Int, a: Double, b: Int) {
        when (op) {
            Op.SFX -> SfxBank.play(b, a, currentTime, this)
            Op.MUSIC_START -> startTransport(currentTime + MusicData.START_OFFSET, b)
            Op.MUSIC_STOP -> stopTransport()
            Op.HYPE -> sequencer.hype = b != 0
            Op.THEME -> sequencer.setTheme(b)
            Op.MODE -> sequencer.setMode(if (b != 0) MusicMode.Menu else MusicMode.Game)
            Op.DUCK -> duck()
            Op.MASTER -> master = a
        }
    }

    private fun allocVoice(): Voice {
        var victim = voices[0]
        for (v in voices) {
            if (!v.active) return v
            if (v.endFrame < victim.endFrame) victim = v
        }
        stolen++
        return victim
    }

    override fun tone(role: Role, freq: Double, to: Double, dur: Double, wave: Wave, vol: Double, at: Double, bus: Bus) {
        allocVoice().startTone(freq, to, dur, wave, vol, at, bus, sampleRate)
    }

    override fun noise(role: Role, at: Double, dur: Double, vol: Double, cutoff: Double, bus: Bus) {
        val offset = (random.nextDouble() * 0.5 * sampleRate).toInt()
        allocVoice().startNoise(at, dur, vol, cutoff, bus, offset, sampleRate)
    }

    internal fun activeVoices(): Int = voices.count { it.active }

    object Op {
        const val SFX = 1
        const val MUSIC_START = 2
        const val MUSIC_STOP = 3
        const val HYPE = 4
        const val THEME = 5
        const val MODE = 6
        const val DUCK = 7
        const val MASTER = 8
    }

    companion object {
        const val QUANTUM = 256
        const val MAX_VOICES = 128
        const val MUSIC_VOLUME = 0.3
        const val MUSIC_SEND = 0.22f
        const val SFX_SEND = 0.12f
        const val WET = 0.35f

        /** music.beat(): elapsed seconds since transport start in quarter notes. */
        fun beatAt(elapsed: Double): Double = elapsed / (MusicData.STEP * 4)
    }
}
