package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.audio.music.MusicData
import de.robinrehbein.birdy.audio.music.Sequencer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SequencerStateTest {
    private val sink = RecordingSink()

    @Test
    fun themeAndModeSwitchOnBarLine() {
        val seq = Sequencer()
        for (s in 0 until 5) seq.playStep(s, 0.0, sink)
        seq.setTheme(2)
        seq.setMode(MusicMode.Menu)
        for (s in 5 until 16) seq.playStep(s, 0.0, sink)
        assertEquals(0, seq.theme)
        assertEquals(MusicMode.Game, seq.mode)
        seq.playStep(16, 0.0, sink)
        assertEquals(2, seq.theme)
        assertEquals(MusicMode.Menu, seq.mode)
        assertEquals(-1, seq.pendingTheme)
    }

    @Test
    fun setThemeNormalizesAndKeepsJsQuirk() {
        val seq = Sequencer()
        seq.setTheme(-3) // ((-3 % 4) + 4) % 4 = 1
        assertEquals(1, seq.pendingTheme)
        seq.setTheme(6)
        assertEquals(2, seq.pendingTheme)
        // Asking for the current theme does not clear a pending switch (audio.js:274).
        seq.setTheme(4)
        assertEquals(2, seq.pendingTheme)
        seq.setMode(MusicMode.Game)
        assertEquals(null, seq.pendingMode)
    }

    @Test
    fun hypeIsImmediate() {
        val seq = Sequencer()
        seq.playStep(1, 0.0, sink)
        assertTrue(sink.take().none { it.role == Role.SPARKLE })
        seq.hype = true
        seq.playStep(2, 0.0, sink)
        assertEquals(1, sink.take().count { it.role == Role.SPARKLE })
    }

    @Test
    fun blossomArpPlaysEverySixteenthWithOctaveSentinel() {
        val seq = Sequencer()
        seq.reset(3, MusicMode.Game)
        val arps = (0 until 4).map { s -> seq.playStep(s, 0.0, sink); sink.take().single { it.role == Role.ARP } }
        // Theme 3: key 2, C chord -> [62, 66, 69]; arp [0,1,2,3] where 3 = root + 12; all + 12.
        assertEquals(listOf(74, 78, 81, 86).map { MusicData.midi(it) }, arps.map { it.freq })
    }
}
