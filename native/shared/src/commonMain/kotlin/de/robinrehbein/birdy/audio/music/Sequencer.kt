package de.robinrehbein.birdy.audio.music

import de.robinrehbein.birdy.audio.Bus
import de.robinrehbein.birdy.audio.MusicMode
import de.robinrehbein.birdy.audio.NoteSink
import de.robinrehbein.birdy.audio.Role
import de.robinrehbein.birdy.audio.dsp.Wave
import de.robinrehbein.birdy.audio.music.MusicData.BASS
import de.robinrehbein.birdy.audio.music.MusicData.Drums
import de.robinrehbein.birdy.audio.music.MusicData.FORM
import de.robinrehbein.birdy.audio.music.MusicData.MINOR_A
import de.robinrehbein.birdy.audio.music.MusicData.NO_BASS
import de.robinrehbein.birdy.audio.music.MusicData.SECTION_A
import de.robinrehbein.birdy.audio.music.MusicData.SHUFFLE_HATS
import de.robinrehbein.birdy.audio.music.MusicData.SPARKLE
import de.robinrehbein.birdy.audio.music.MusicData.STEP
import de.robinrehbein.birdy.audio.music.MusicData.THEMES
import de.robinrehbein.birdy.audio.music.MusicData.midi

/**
 * audio.js `playStep` / `drums` plus the theme/mode/hype state. Pure note selection: every call
 * goes to a [NoteSink]. Owned by the audio thread (or a test); allocation-free.
 */
class Sequencer {
    var theme = 0
        private set
    var mode = MusicMode.Game
        private set
    /** -1 = none. */
    var pendingTheme = -1
        private set
    var pendingMode: MusicMode? = null
        private set
    var hype = false

    private val chord = IntArray(3)

    /** Resets to the given state and clears pending switches (renderMusic setup). */
    fun reset(themeIndex: Int, mode: MusicMode, hype: Boolean = false) {
        theme = themeIndex
        this.mode = mode
        this.hype = hype
        pendingTheme = -1
        pendingMode = null
    }

    /** music.setTheme: normalized index, deferred to the next bar line (audio.js:272-275). */
    fun setTheme(i: Int) {
        val n = THEMES.size
        val next = ((i % n) + n) % n
        if (next != theme) pendingTheme = next
    }

    fun setMode(m: MusicMode) {
        if (m != mode) pendingMode = m
    }

    fun playStep(s: Int, t: Double, sink: NoteSink) {
        val bar = s / 16
        val inBar = s % 16
        if (inBar == 0) {
            if (pendingTheme >= 0) { theme = pendingTheme; pendingTheme = -1 }
            pendingMode?.let { mode = it; pendingMode = null }
        }
        val th = THEMES[theme]
        val part = FORM[bar / 4]
        val sec = part.section
        val barInSec = bar % 4
        val chordName = if (th.minor && sec === SECTION_A) MINOR_A[barInSec] else sec.chords[barInSec]
        val k = th.key
        for (i in 0..2) chord[i] = chordName.notes[i] + k
        val calm = mode == MusicMode.Menu
        val breakdown = part.breakdown && barInSec < 2

        if (!calm && !breakdown) drums(th.drums, inBar, t, sink)
        else if (!calm && breakdown && inBar % 4 == 2) sink.noise(Role.BREAKDOWN_TICK, t, 0.03, 0.06, 7000.0, Bus.MUSIC)

        val bass = BASS[inBar]
        if (bass != NO_BASS && !(breakdown && barInSec == 0)) {
            val f = midi(chord[0] - 12 + bass)
            sink.tone(Role.BASS, f, f, STEP * 1.6, Wave.TRIANGLE, if (calm) 0.3 else 0.55, t, Bus.MUSIC)
        }

        val arpEvery = if (th.arp.size == 4 && th.drums == Drums.LIGHT) 1 else 2
        if (s % arpEvery == 0) {
            val i = th.arp[(s / arpEvery) % th.arp.size]
            val n = if (i == 3) chord[0] + 12 else chord[i]
            val f = midi(n + 12)
            sink.tone(Role.ARP, f, f, STEP * 0.9, th.arpType, if (th.arpType == Wave.SQUARE) 0.05 else 0.09, t, Bus.MUSIC)
        }
        if (hype && !calm) {
            val f = midi(chord[SPARKLE[s % 4]] + 24)
            sink.tone(Role.SPARKLE, f, f, STEP * 0.7, Wave.SQUARE, 0.035, t, Bus.MUSIC)
        }

        val m = s % 64
        val note = sec.melody.note[m]
        if (note != 0 && !(part.breakdown && barInSec < 2)) {
            val len = sec.melody.length[m]
            val n = note + k + th.octave
            val vol = th.leadVol * (if (calm) 0.6 else 1.0)
            val f = midi(n)
            sink.tone(Role.LEAD, f, f, STEP * len * 0.95, th.lead, vol, t, Bus.MUSIC)
            if (part.harmony) {
                val h = midi(n - 4)
                sink.tone(Role.HARMONY, h, h, STEP * len * 0.9, th.lead, vol * 0.5, t, Bus.MUSIC)
            }
        }
    }

    private fun drums(kind: Drums, inBar: Int, t: Double, sink: NoteSink) {
        fun kick(v: Double = 0.9) = sink.tone(Role.KICK, 150.0, 45.0, 0.14, Wave.SINE, v, t, Bus.MUSIC)
        fun snare(v: Double = 0.35) = sink.noise(Role.SNARE, t, 0.12, v, 1500.0, Bus.MUSIC)
        fun hat(v: Double) = sink.noise(Role.HAT, t, 0.03, v, 7000.0, Bus.MUSIC)
        when (kind) {
            Drums.POP -> {
                if (inBar % 8 == 0) kick()
                if (inBar == 4 || inBar == 12) snare()
                if (inBar % 2 == 0) hat(if (inBar % 4 == 2) 0.14 else 0.07)
            }
            Drums.SHUFFLE -> {
                if (inBar == 0 || inBar == 10) kick()
                if (inBar == 4 || inBar == 12) snare()
                if (inBar == 14) snare(0.12)
                if (SHUFFLE_HATS.contains(inBar)) hat(if (inBar % 2 != 0) 0.05 else 0.12)
            }
            Drums.DESERT -> {
                if (inBar == 0 || inBar == 6 || inBar == 12) kick(0.85)
                if (inBar == 8) snare(0.3)
                if (inBar % 4 == 2) hat(0.1)
                if (inBar == 3 || inBar == 11) sink.tone(Role.DESERT_BLIP, 220.0, 110.0, 0.12, Wave.SINE, 0.3, t, Bus.MUSIC)
            }
            Drums.LIGHT -> {
                if (inBar == 0 || inBar == 8) kick(0.7)
                if (inBar == 12) snare(0.22)
                if (inBar % 2 == 1) hat(0.05)
            }
        }
    }
}
