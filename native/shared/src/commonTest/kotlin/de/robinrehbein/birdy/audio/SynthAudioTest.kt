package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test AudioOut whose "audio thread" is driven manually by [pump]. */
private class ManualOut : AudioOut {
    override val sampleRate = 44100
    var render: ((FloatArray, Int) -> Unit)? = null
    var pausedNow = false
    var stopped = false
    val buf = FloatArray(1024)
    override fun start(render: (FloatArray, Int) -> Unit) { this.render = render }
    override fun setPaused(paused: Boolean) { pausedNow = paused }
    override fun stop() { stopped = true }
    fun pump(blocks: Int = 1): FloatArray { repeat(blocks) { if (!pausedNow) render?.invoke(buf, buf.size) }; return buf }
}

class SynthAudioTest {
    @Test
    fun muteIsPersistedAsOneZero() {
        val store = MemoryKeyValueStore(mapOf("birdy-muted" to "1"))
        val a = SynthAudio(ManualOut(), store, Random(1))
        assertTrue(a.muted)
        a.setMuted(false)
        assertEquals("0", store.map["birdy-muted"])
        a.setMuted(true)
        assertEquals("1", store.map["birdy-muted"])
        assertFalse(SynthAudio(ManualOut(), MemoryKeyValueStore(), Random(1)).muted)
    }

    @Test
    fun mutedEngineIsSilent() {
        val out = ManualOut()
        val a = SynthAudio(out, MemoryKeyValueStore(mapOf("birdy-muted" to "1")), Random(1))
        a.unlock()
        a.sfx(Sfx.Hit)
        repeat(20) { assertTrue(out.pump().all { it == 0f }) }
        a.setMuted(false)
        a.sfx(Sfx.Hit)
        var loud = false
        repeat(20) { if (out.pump().any { it != 0f }) loud = true }
        assertTrue(loud)
    }

    @Test
    fun soundsBeforeUnlockAreSkipped() {
        val out = ManualOut()
        val a = SynthAudio(out, MemoryKeyValueStore(), Random(1))
        a.sfx(Sfx.PowerUp(PowerType.entries.first()))
        a.musicStart()
        a.duck()
        assertNull(a.musicBeat())
        assertNull(out.render)
        a.unlock()
        assertNotNull(out.render)
        repeat(10) { assertTrue(out.pump().all { it == 0f }) }
        assertNull(a.musicBeat())
    }

    @Test
    fun beatComesFromAudioClock() {
        val out = ManualOut()
        val a = SynthAudio(out, MemoryKeyValueStore(), Random(1))
        a.unlock()
        a.musicStart()
        assertNull(a.musicBeat(), "not yet acknowledged by the audio thread")
        out.pump()
        val b0 = a.musicBeat()!!
        // First block: currentTime = 1024/44100, start = 0.05.
        assertEquals((1024.0 / 44100 - 0.05) / (0.12096774193548387 * 4), b0, 1e-9)
        out.pump(100)
        val b1 = a.musicBeat()!!
        assertEquals(100 * 1024.0 / 44100 / (0.12096774193548387 * 4), b1 - b0, 1e-9)
        // Suspend freezes the clock (AudioContext.suspend).
        a.setSuspended(true)
        out.pump(10)
        assertEquals(b1, a.musicBeat())
        a.setSuspended(false)
        out.pump()
        assertTrue(a.musicBeat()!! > b1)
        a.musicStop()
        assertNull(a.musicBeat())
        // Restart resets the transport.
        a.musicStart()
        out.pump()
        assertTrue(a.musicBeat()!! < 0.1)
    }

    @Test
    fun themeBeforeUnlockIsKept() {
        val out = ManualOut()
        val a = SynthAudio(out, MemoryKeyValueStore(), Random(1))
        a.setTheme(3)
        a.setMode(MusicMode.Menu)
        a.unlock()
        a.musicStart()
        out.pump(3)
        assertEquals(3, a.engine.sequencer.theme)
        assertEquals(MusicMode.Menu, a.engine.sequencer.mode)
    }

    @Test
    fun coinComboUsesInjectedClock() {
        var now = 0.0
        val out = ManualOut()
        val a = SynthAudio(out, MemoryKeyValueStore(), Random(1)) { now }
        a.unlock()
        now = 10_000.0
        a.sfx(Sfx.Coin)
        now += 300
        a.sfx(Sfx.Coin)
        now += 300
        a.sfx(Sfx.Coin)
        out.pump()
        assertTrue(a.engine.activeVoices() >= 6)
    }

    @Test
    fun ringDropsWhenFullInsteadOfBlocking() {
        val ring = CommandRing(8)
        repeat(7) { assertTrue(ring.push(1, it.toDouble(), it)) }
        assertFalse(ring.push(1))
        assertEquals(1, ring.dropped)
        val seen = mutableListOf<Int>()
        ring.drain { _, _, b -> seen += b }
        assertEquals((0 until 7).toList(), seen)
        assertTrue(ring.isEmpty)
        repeat(20) { i -> ring.push(2, 0.0, i); ring.drain { _, _, b -> assertEquals(i, b) } }
    }
}
