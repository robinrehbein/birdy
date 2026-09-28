package de.robinrehbein.birdy.audio.music

import de.robinrehbein.birdy.audio.dsp.Wave
import kotlin.math.pow

/** Song data from audio.js:117-158. */
object MusicData {
    const val BPM = 124
    const val STEP = 60.0 / BPM / 4
    const val LOOP_STEPS = 256
    const val START_OFFSET = 0.05

    fun midi(m: Int): Double = 440.0 * 2.0.pow((m - 69) / 12.0)
    fun midi(m: Double): Double = 440.0 * 2.0.pow((m - 69) / 12.0)

    enum class Chord(val notes: IntArray) {
        C(intArrayOf(60, 64, 67)), Am(intArrayOf(57, 60, 64)), F(intArrayOf(53, 57, 60)),
        G(intArrayOf(55, 59, 62)), Em(intArrayOf(52, 55, 59)), Dm(intArrayOf(50, 53, 57)),
    }

    /** [step, midi note, length in steps] over 4 bars (64 steps). */
    val MELODY_A = arrayOf(
        intArrayOf(0, 72, 2), intArrayOf(2, 76, 2), intArrayOf(4, 79, 2), intArrayOf(6, 76, 2),
        intArrayOf(8, 77, 2), intArrayOf(10, 76, 2), intArrayOf(12, 74, 2), intArrayOf(14, 72, 2),
        intArrayOf(16, 72, 4), intArrayOf(20, 69, 2), intArrayOf(22, 72, 2), intArrayOf(24, 76, 4), intArrayOf(28, 74, 4),
        intArrayOf(32, 72, 2), intArrayOf(34, 77, 2), intArrayOf(36, 81, 2), intArrayOf(38, 77, 2),
        intArrayOf(40, 76, 2), intArrayOf(42, 74, 2), intArrayOf(44, 72, 2), intArrayOf(46, 69, 2),
        intArrayOf(48, 71, 4), intArrayOf(52, 74, 4), intArrayOf(56, 79, 2), intArrayOf(58, 77, 2),
        intArrayOf(60, 74, 2), intArrayOf(62, 71, 2),
    )
    val MELODY_B = arrayOf(
        intArrayOf(0, 77, 4), intArrayOf(4, 76, 2), intArrayOf(6, 74, 2), intArrayOf(8, 72, 4), intArrayOf(12, 69, 4),
        intArrayOf(16, 74, 2), intArrayOf(18, 76, 2), intArrayOf(20, 77, 2), intArrayOf(22, 79, 2),
        intArrayOf(24, 76, 4), intArrayOf(28, 74, 4),
        intArrayOf(32, 76, 4), intArrayOf(36, 79, 2), intArrayOf(38, 76, 2), intArrayOf(40, 74, 2),
        intArrayOf(42, 72, 2), intArrayOf(44, 71, 4),
        intArrayOf(48, 72, 2), intArrayOf(50, 74, 2), intArrayOf(52, 76, 4), intArrayOf(56, 72, 2),
        intArrayOf(58, 71, 2), intArrayOf(60, 69, 4),
    )

    /** Step-indexed melody lookup (JS `toMap`): note[s] / len[s], note 0 = none. */
    class Melody(entries: Array<IntArray>) {
        val note = IntArray(64)
        val length = IntArray(64)
        init { for (e in entries) { note[e[0]] = e[1]; length[e[0]] = e[2] } }
    }

    class Section(val name: String, val chords: Array<Chord>, val melody: Melody)

    val SECTION_A = Section("A", arrayOf(Chord.C, Chord.Am, Chord.F, Chord.G), Melody(MELODY_A))
    val SECTION_B = Section("B", arrayOf(Chord.F, Chord.G, Chord.Em, Chord.Am), Melody(MELODY_B))
    /** theme.minor replacement for section A (audio.js:208). */
    val MINOR_A = arrayOf(Chord.Am, Chord.F, Chord.C, Chord.G)

    class Part(val section: Section, val harmony: Boolean = false, val breakdown: Boolean = false)

    /** 16-bar song form: A, A with a harmony voice, B, A as a breakdown. */
    val FORM = arrayOf(Part(SECTION_A), Part(SECTION_A, harmony = true), Part(SECTION_B), Part(SECTION_A, breakdown = true))

    enum class Drums { POP, SHUFFLE, DESERT, LIGHT }

    class Theme(
        val key: Int,
        val lead: Wave,
        val leadVol: Double,
        val arp: IntArray,
        val arpType: Wave,
        val drums: Drums,
        val minor: Boolean = false,
        val octave: Int = 0,
    )

    val THEMES = arrayOf(
        Theme(0, Wave.SQUARE, 0.07, intArrayOf(0, 1, 2, 1), Wave.SQUARE, Drums.POP), // Stadtpark
        Theme(-3, Wave.TRIANGLE, 0.16, intArrayOf(0, 2, 1, 2), Wave.SQUARE, Drums.SHUFFLE), // Herbstwald
        Theme(-5, Wave.SAWTOOTH, 0.04, intArrayOf(0, 1, 0, 2), Wave.TRIANGLE, Drums.DESERT, minor = true), // Canyon
        Theme(2, Wave.SINE, 0.12, intArrayOf(0, 1, 2, 3), Wave.SINE, Drums.LIGHT, octave = 12), // Blütenhain
    )

    const val NO_BASS = Int.MIN_VALUE

    /** BASS: inBar -> semitone offset, [NO_BASS] where no bass note plays (audio.js:158). */
    val BASS = IntArray(16) { NO_BASS }.also {
        it[0] = 0; it[3] = 0; it[6] = 12; it[8] = 0; it[11] = 0; it[14] = 7
    }

    val SHUFFLE_HATS = intArrayOf(2, 3, 6, 7, 10, 11, 14, 15)
    val SPARKLE = intArrayOf(0, 1, 2, 1)
}
