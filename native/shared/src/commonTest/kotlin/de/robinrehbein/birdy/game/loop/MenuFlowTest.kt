package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.game.UiEffect
import de.robinrehbein.birdy.game.ZonesHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuFlowTest {
    @Test
    fun backButtonMatrix() {
        // main.js handleBack: every (mode, paused, menu) combination.
        for (mode in GameMode.entries) for (paused in listOf(false, true)) for (menu in Menu.entries) {
            val expected = when {
                mode == GameMode.Playing && !paused -> BackAction.Pause
                paused || mode == GameMode.Over || mode == GameMode.Dead -> BackAction.ToMenu
                menu == Menu.Shop -> BackAction.CloseShop
                menu == Menu.Achievements -> BackAction.CloseAchievements
                else -> BackAction.Exit
            }
            assertEquals(expected, MenuFlow.backAction(mode, paused, menu), "$mode paused=$paused $menu")
        }
        assertEquals(BackAction.Exit, MenuFlow.backAction(GameMode.Ready, false, Menu.Start))
        assertEquals(BackAction.ToMenu, MenuFlow.backAction(GameMode.Playing, true, Menu.Start))
    }

    @Test
    fun backButtonDrivesTheGame() {
        val h = LoopHarness()
        val effects = ArrayList<UiEffect>()
        // Menu: shop -> start -> exit.
        h.post(UiCommand.OpenShop(true))
        assertEquals(Menu.Shop, h.ui.menu)
        assertNotNull(h.ui.shop)
        h.post(UiCommand.Back)
        assertEquals(Menu.Start, h.ui.menu)
        assertNull(h.ui.shop)
        h.post(UiCommand.OpenAchievements(true))
        assertEquals(Menu.Achievements, h.ui.menu)
        assertEquals(21, h.ui.achievements.list.size)
        h.post(UiCommand.Back)
        assertEquals(Menu.Start, h.ui.menu)
        // Run: back pauses, back again leaves to the menu.
        h.startRun()
        h.frames(1.0)
        h.post(UiCommand.Back)
        assertTrue(h.ui.paused)
        assertEquals(listOf("suspended:true"), h.audio.log.filter { it.startsWith("suspended") }.takeLast(1))
        h.post(UiCommand.Back)
        assertEquals(GameMode.Ready, h.ui.mode)
        assertFalse(h.ui.paused)
        // Start menu: exit.
        h.post(UiCommand.Back)
        while (true) effects += h.game.pollEffect() ?: break
        assertEquals(listOf<UiEffect>(UiEffect.ExitApp), effects)
    }

    @Test
    fun goToMenuChecklist() {
        val h = LoopHarness()
        h.startRun()
        h.frames(3.0)
        h.sim.debug.activatePower(PowerType.Star)
        h.sim.debug.activatePower(PowerType.Mini)
        h.frame()
        assertTrue(h.state.power(PowerType.Star) > 0)
        assertTrue(h.sim.gates.any { it.active })
        h.post(UiCommand.Back) // pause
        h.post(UiCommand.GoToMenu)
        val s = h.state
        assertFalse(s.paused)
        assertEquals(GameMode.Ready, s.mode)
        assertFalse(s.hold)
        assertTrue(s.power.all { it == 0.0 })
        assertEquals(0.0, s.grace)
        assertEquals(Menu.Start, s.menu)
        assertEquals(ZonesHint.None, s.zonesHint)
        assertTrue(s.pose.visible)
        assertTrue(h.sim.gates.none { it.active || it.visible })
        assertTrue(h.sim.coins.none { it.active })
        assertTrue(h.sim.pickups.none { it.active })
        assertTrue("hype:false" in h.audio.log)
        assertEquals("mode:Menu", h.audio.log.last { it.startsWith("mode") })
        assertNull(h.ui.tutorialHand)
        assertNull(h.ui.zonesHint)
        assertEquals(GameMode.Ready, h.ui.mode)
        // Score/coins are only reset by the next resetGame().
        h.post(UiCommand.Play)
        assertEquals(0, h.state.score)
    }

    @Test
    fun restartIsDebounced() {
        val h = LoopHarness()
        h.startRun(god = false)
        // Fly into the ground: stop flapping and wait for the crash and the game over.
        var t = 0.0
        while (h.state.mode != GameMode.Over && t < 20) {
            h.frame(1.0 / 60)
            t += 1.0 / 60
        }
        assertEquals(GameMode.Over, h.state.mode)
        assertNotNull(h.ui.gameOverUi)
        // A panicked tap right at the game over does not skip the screen.
        h.post(UiCommand.GameOverTap)
        assertEquals(GameMode.Over, h.state.mode)
        h.frames(0.2)
        h.post(UiCommand.Touch(0.5f, 0.5f))
        assertEquals(GameMode.Over, h.state.mode)
        h.frames(0.2)
        h.post(UiCommand.GameOverTap)
        assertEquals(GameMode.Playing, h.state.mode)
        assertTrue(h.state.hold)
    }

    @Test
    fun pauseAndVisibility() {
        val h = LoopHarness()
        // Menu: background only suspends audio.
        h.post(UiCommand.AppVisible(false))
        assertFalse(h.state.paused)
        assertTrue(h.audio.audioOff)
        h.post(UiCommand.AppVisible(true))
        assertFalse(h.audio.audioOff)
        // Run: background pauses; coming back keeps the pause (and the audio suspended).
        h.startRun()
        h.frames(0.5)
        h.post(UiCommand.AppVisible(false))
        assertTrue(h.state.paused)
        assertTrue(h.audio.audioOff)
        val time = h.state.time
        h.frames(0.5)
        assertEquals(time, h.state.time, "no simulation while paused")
        h.post(UiCommand.AppVisible(true))
        assertTrue(h.state.paused)
        assertTrue(h.audio.audioOff)
        // Any tap resumes.
        h.post(UiCommand.Touch(0.1f, 0.5f))
        assertFalse(h.state.paused)
        assertFalse(h.audio.audioOff)
        assertEquals(1, h.state.lane, "the resume tap does not also switch lanes")
    }

    @Test
    fun hapticsAreMutedWithTheAudio() {
        val h = LoopHarness()
        h.post(UiCommand.SetMuted(true))
        h.startRun(god = false)
        var t = 0.0
        while (h.state.mode == GameMode.Playing && t < 20) { h.frame(); t += 1.0 / 60 }
        assertTrue(h.haptics.calls.isEmpty())
        h.post(UiCommand.SetMuted(false))
        while (h.state.mode != GameMode.Over) h.frame()
        h.frames(0.5)
        h.post(UiCommand.GameOverTap)
        t = 0.0
        h.post(UiCommand.Touch(0.5f, 0.5f))
        while (h.state.mode == GameMode.Playing && t < 20) { h.frame(); t += 1.0 / 60 }
        assertTrue(70 in h.haptics.calls, "death buzz")
    }

    @Test
    fun firstLaunchStartsTheTutorialRun() {
        val h = LoopHarness(progressJson = "{}")
        assertEquals(GameMode.Playing, h.state.mode)
        assertTrue(h.state.tutorialActive)
        assertNotNull(h.ui.tutorialHand)
        assertEquals("flap", h.ui.tutorialHand!!.mode)
        // The hand hovers over the bird (screen centre) a bit below it.
        assertEquals(0.5f, h.ui.tutorialHand!!.x, 0.02f)
    }
}
