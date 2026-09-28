package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.UiCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToastQueueTest {
    @Test
    fun fifoOneAtATimeFor2_2Seconds() {
        val q = ToastQueue()
        q.push("a")
        q.push("b")
        assertNull(q.visible)
        q.update(1.0 / 60)
        assertEquals("a", q.visible!!.text)
        val idA = q.visible!!.id
        var t = 0.0
        while (q.visible?.text == "a") { q.update(0.01); t += 0.01 }
        assertEquals(2.2, t, 0.02)
        // The frame that hides "a" does not show "b" yet.
        assertNull(q.visible)
        q.update(0.01)
        assertEquals("b", q.visible!!.text)
        assertTrue(q.visible!!.id != idA)
        assertEquals(0.0f, q.visible!!.age)
    }

    @Test
    fun clearKeepsTheVisibleToast() {
        val q = ToastQueue()
        q.push("a")
        q.push("b")
        q.update(0.1)
        q.clear()
        assertEquals("a", q.visible!!.text)
        repeat(300) { q.update(0.1) }
        assertNull(q.visible)
    }

    @Test
    fun toastsSurviveDeathButNotANewRun() {
        val h = LoopHarness(progressJson = """{"coins":0,"best":5,"runs":1,"tutorialDone":true}""")
        // runs == 1: resetGame schedules the swipe hint 900 ms later (setTimeout, not cleared).
        h.startRun(god = false)
        h.frames(0.2)
        assertNull(h.ui.toast)
        h.frames(0.8)
        assertEquals("swipeHint", h.ui.toast?.text)
        // Queue two more; die; they keep draining over the game-over screen.
        h.game.game.toasts.push("x")
        h.game.game.toasts.push("y")
        var t = 0.0
        while (h.state.mode != GameMode.Over && t < 20) { h.frame(); t += 1.0 / 60 }
        val left = h.game.game.toasts.pending + listOfNotNull(h.ui.toast?.text)
        assertTrue("y" in left, "queued toasts keep draining after death: $left")
        // A new run clears what is still queued.
        h.game.game.toasts.push("z")
        h.frames(0.5)
        h.post(UiCommand.GameOverTap)
        assertEquals(GameMode.Playing, h.state.mode)
        assertTrue("z" !in h.game.game.toasts.pending)
    }

    @Test
    fun missionToastFormat() {
        val h = LoopHarness()
        h.startRun()
        val m = h.progress.missions().first()
        h.game.game.inject(de.robinrehbein.birdy.game.GameEvent.MissionPreview(listOf(m.id)))
        h.frames(0.1)
        val text = h.ui.toast?.text
        assertNotNull(text)
        assertTrue(text.startsWith("[check] ") && text.endsWith(" +${m.reward}"), text)
    }
}
