package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.audio.music.MusicData
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderTest {
    private fun check(pcm: FloatArray, what: String) {
        var peak = 0f
        for (v in pcm) {
            assertTrue(v.isFinite(), "$what finite")
            peak = maxOf(peak, abs(v))
        }
        assertTrue(peak <= 1f, "$what peak $peak")
        assertTrue(peak > 0.05f, "$what audible (peak $peak)")
    }

    private fun rms(pcm: FloatArray, from: Double, to: Double): Double {
        val a = (from * 44100).toInt(); val b = (to * 44100).toInt()
        var s = 0.0
        for (i in a until b) s += pcm[i].toDouble() * pcm[i]
        return sqrt(s / (b - a))
    }

    @Test
    fun allThemesRenderCleanly() {
        for (theme in 0..3) for (calm in listOf(false, true)) {
            val pcm = renderMusic(seconds = 5.0, themeIndex = theme, calm = calm, rng = Random(theme))
            assertEquals(kotlin.math.ceil(44100 * 5.0).toInt(), pcm.size)
            check(pcm, "theme $theme calm $calm")
            // Silence before the first step (0.05 s) and sound while notes play.
            for (i in 0 until (0.05 * 44100).toInt()) assertEquals(0f, pcm[i])
            assertTrue(rms(pcm, 0.1, 4.0) > 0.01, "theme $theme non-silent")
        }
    }

    @Test
    fun deterministicForFixedSeed() {
        val a = renderMusic(seconds = 3.0, themeIndex = 1, rng = Random(42))
        val b = renderMusic(seconds = 3.0, themeIndex = 1, rng = Random(42))
        assertContentEquals(a, b)
        val c = renderMusic(seconds = 3.0, themeIndex = 1, rng = Random(43))
        assertTrue(!a.contentEquals(c), "seed changes noise/reverb")
    }

    @Test
    fun calmIsQuieterThanGame() {
        val game = renderMusic(seconds = 4.0, themeIndex = 0, rng = Random(1))
        val calm = renderMusic(seconds = 4.0, themeIndex = 0, calm = true, rng = Random(1))
        assertTrue(rms(calm, 0.1, 3.8) < rms(game, 0.1, 3.8))
    }

    @Test
    fun tailStopsSchedulingBeforeEnd() {
        val pcm = renderMusic(seconds = 1.0, themeIndex = 0, rng = Random(2))
        // Last step starts before 0.8 s; the final 60 ms hold only decays/reverb.
        val last = (0.05 + MusicData.STEP * 6)
        assertTrue(last < 0.8)
        check(pcm, "short")
    }

    @Test
    fun everySfxRendersCleanly() {
        for (id in 0..9) {
            val pcm = renderSfx(id, if (id == SfxId.NEAR) 3.0 else 1.0, rng = Random(id))
            check(pcm, "sfx $id")
        }
    }

    @Test
    fun hitPlusDenseMusicStaysBelowFullScale() {
        val e = SynthEngine(44100, Random(4))
        e.sequencer.reset(1, MusicMode.Game, hype = true)
        e.startTransport(0.05)
        val buf = FloatArray(44100 * 3)
        for (k in 0 until 10) SfxBank.play(k % 10, 1.0, 0.5 + k * 0.01, e)
        e.render(buf, buf.size)
        check(buf, "dense")
    }
}
