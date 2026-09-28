package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.audio.NullAudioOut
import de.robinrehbein.birdy.game.BirdyGame
import de.robinrehbein.birdy.game.GameBoot
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameParts
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.meta.KeyEchoStrings
import de.robinrehbein.birdy.meta.LocalProgressRepository
import de.robinrehbein.birdy.platform.FakeClock
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import de.robinrehbein.birdy.platform.PlatformServices
import de.robinrehbein.birdy.platform.StorageKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BootBackTest {
    @Test
    fun backDuringBootIsDroppedInsteadOfExitingOnceBooted() {
        val store = MemoryKeyValueStore(mapOf(StorageKeys.PROGRESS to """{"coins":500,"best":20,"runs":6,"tutorialDone":true}"""))
        val clock = FakeClock(millis = 1_790_000_000_000)
        val services = PlatformServices(store, clock, RecordingHaptics(), NullAudioOut, null, null, deviceLanguage = "de-DE")
        var ready = false
        val game = BirdyGame(services, FakeRenderer(), KeyEchoStrings(), GameBoot {
            if (ready) GameParts(LocalProgressRepository(store, clock), FakeAudio()) else null
        })
        game.onSurfaceChanged(1080, 2400)
        game.frame(1)
        assertFalse(game.ui.value.booted)

        game.post(UiCommand.Back) // system back while the splash is up
        game.frame(2)
        ready = true
        game.frame(3)
        game.frame(4)

        assertTrue(game.ui.value.booted)
        assertEquals(GameMode.Ready, game.ui.value.mode)
        assertEquals(Menu.Start, game.ui.value.menu)
        assertNull(game.pollEffect())
    }
}
