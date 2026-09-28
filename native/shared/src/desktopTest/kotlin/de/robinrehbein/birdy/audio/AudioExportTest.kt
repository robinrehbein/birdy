package de.robinrehbein.birdy.audio

import java.io.File
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Writes every theme (game + calm) and every SFX to native/build/audio (WAV) for listening, and
 * checks the real-time budget (10 s of music must render in < 1 s on the JVM).
 */
class AudioExportTest {
    private val dir = File("../build/audio")

    private fun stats(pcm: FloatArray): String {
        var peak = 0f
        var sum = 0.0
        for (v in pcm) { peak = maxOf(peak, abs(v)); sum += v.toDouble() * v }
        return "peak=%.3f rms=%.4f".format(peak, kotlin.math.sqrt(sum / pcm.size))
    }

    @Test
    fun exportThemesAndSfx() {
        val names = listOf("stadtpark", "herbstwald", "canyon", "bluetenhain")
        for (i in 0..3) {
            for (calm in listOf(false, true)) {
                val pcm = renderMusic(seconds = 31.0, themeIndex = i, calm = calm, rng = Random(i))
                val name = "theme$i-${names[i]}${if (calm) "-calm" else ""}"
                WavWriter.write(File(dir, "$name.wav"), pcm)
                println("audio: $name ${stats(pcm)}")
            }
        }
        val hype = renderMusic(seconds = 16.0, themeIndex = 0, rng = Random(9), hype = true)
        WavWriter.write(File(dir, "theme0-hype.wav"), hype)
        val sfxNames = listOf("flap", "swoosh", "point", "coin", "powerup", "powerdown", "near", "zone", "bounce", "hit")
        for ((id, name) in sfxNames.withIndex()) {
            val pcm = renderSfx(id, if (id == SfxId.NEAR) 0.0 else 1.0, rng = Random(id))
            WavWriter.write(File(dir, "sfx-$name.wav"), pcm)
            println("audio: sfx-$name ${stats(pcm)}")
        }
        // Coin combo ladder and near chain ladder.
        val e = SynthEngine(44100, Random(3))
        val combo = CoinCombo()
        for (k in 0 until 9) SfxBank.play(SfxId.COIN, combo.hit(k * 250.0 + 1000), 0.1 + k * 0.25, e)
        for (c in 0 until 7) SfxBank.play(SfxId.NEAR, c.toDouble(), 2.6 + c * 0.3, e)
        val ladder = FloatArray(44100 * 5)
        e.render(ladder, ladder.size)
        WavWriter.write(File(dir, "sfx-coin-near-ladders.wav"), ladder)
        assertTrue(File(dir, "theme0-stadtpark.wav").length() > 44)
    }

    @Test
    fun rendersTenSecondsWellUnderRealTime() {
        repeat(2) { renderMusic(seconds = 10.0, themeIndex = it, rng = Random(it)) } // JIT warm-up
        var best = Long.MAX_VALUE
        for (theme in 0..3) {
            val t0 = System.nanoTime()
            renderMusic(seconds = 10.0, themeIndex = theme, rng = Random(theme), hype = true)
            val ms = (System.nanoTime() - t0) / 1_000_000
            println("audio: render 10 s theme $theme (hype) took $ms ms")
            best = minOf(best, ms)
            assertTrue(ms < 1000, "10 s of music took $ms ms")
        }
    }
}
