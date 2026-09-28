package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.audio.Sfx
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.HandMode
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.UiCommand
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Swipe controls: a touch flaps wherever it lands, a sideways swipe moves one lane and sticks. */
class InputTest {
    private fun LoopHarness.flaps() = audio.sfxLog.count { it == Sfx.Flap }
    private fun LoopHarness.swooshes() = audio.sfxLog.count { it == Sfx.Swoosh }

    /** One gesture: down at ([x], [y]), moves along [path], up. */
    private fun LoopHarness.gesture(x: Float, y: Float, vararg path: Pair<Float, Float>) {
        post(UiCommand.Touch(x, y))
        for ((px, py) in path) post(UiCommand.TouchMove(px, py))
        post(UiCommand.TouchUp)
    }

    private fun running(): LoopHarness {
        val h = LoopHarness()
        h.game.displayDensity = 2.625
        h.startRun()
        h.post(UiCommand.TouchUp)
        h.frames(1.0)
        return h
    }

    @Test
    fun swipeThresholds() {
        // 1080 px wide at density 2.625: max(18 * 2.625 = 47.25, 54) = 54 px.
        assertEquals(0, InputMapper.swipeDirection(53.0, 0.0, 1080.0, 2.625))
        assertEquals(1, InputMapper.swipeDirection(54.0, 0.0, 1080.0, 2.625))
        assertEquals(-1, InputMapper.swipeDirection(-60.0, 10.0, 1080.0, 2.625))
        // Must be mostly sideways: |dx| >= 1.2 |dy|.
        assertEquals(0, InputMapper.swipeDirection(60.0, 51.0, 1080.0, 2.625))
        assertEquals(1, InputMapper.swipeDirection(60.0, 50.0, 1080.0, 2.625))
        // Narrow screen: the 18 CSS px floor wins.
        assertEquals(0, InputMapper.swipeDirection(40.0, 0.0, 300.0, 2.625))
        assertEquals(1, InputMapper.swipeDirection(48.0, 0.0, 300.0, 2.625))
    }

    @Test
    fun tapAnywhereFlapsWithoutLaneChange() {
        val h = running()
        for (x in listOf(0.03f, 0.2f, 0.5f, 0.8f, 0.97f)) {
            val flaps = h.flaps()
            h.state.vy = -5.0
            h.post(UiCommand.Touch(x, 0.7f))
            assertEquals(1, h.state.lane, "tap at x=$x keeps the lane")
            assertEquals(flaps + 1, h.flaps(), "tap at x=$x flaps at once")
            assertTrue(h.state.vy > Tuning.SWITCH_HOP, "full flap, not a hop (x=$x)")
            // Ripple ▲ at the bird.
            assertEquals(0, h.ui.tapFx.last().dir)
            assertEquals(0.5f, h.ui.tapFx.last().x, 0.02f)
            h.post(UiCommand.TouchUp)
            h.frames(0.4)
        }
        assertEquals(GameMode.Playing, h.state.mode)
        assertEquals(0, h.swooshes())
    }

    @Test
    fun swipeUpAndDownOnlyFlap() {
        val h = running()
        val flaps = h.flaps()
        h.gesture(0.5f, 0.7f, 0.5f to 0.6f, 0.51f to 0.45f)
        assertEquals(1, h.state.lane)
        assertEquals(flaps + 1, h.flaps(), "swipe up = one flap (on touch-down)")
        h.frames(0.4)
        h.gesture(0.3f, 0.4f, 0.3f to 0.5f, 0.31f to 0.7f)
        assertEquals(1, h.state.lane)
        assertEquals(flaps + 2, h.flaps(), "swipe down: the touch-down flap stands")
        assertEquals(0, h.swooshes())
    }

    @Test
    fun swipeRightSticksUntilSwipeLeft() {
        val h = running()
        // Swipe right starting on the far left: one lane right, the flap becomes a hop.
        h.state.vy = -3.0
        h.post(UiCommand.Touch(0.1f, 0.6f))
        assertEquals(1, h.state.lane, "never changes lanes on touch-down")
        h.post(UiCommand.TouchMove(0.12f, 0.6f))
        assertEquals(1, h.state.lane, "below the threshold")
        h.post(UiCommand.TouchMove(0.18f, 0.6f))
        assertEquals(2, h.state.lane)
        assertTrue(h.state.vy <= Tuning.SWITCH_HOP, "flap taken back: vy=${h.state.vy}")
        assertEquals(1, h.swooshes())
        assertEquals(1, h.ui.tapFx.last().dir, "▶ ripple")
        // One action per gesture.
        h.post(UiCommand.TouchMove(0.6f, 0.6f))
        assertEquals(2, h.state.lane)
        h.post(UiCommand.TouchUp)

        // Later taps and swipes up anywhere (even on the left or in the middle) stay in lane 2.
        for (x in listOf(0.1f, 0.5f, 0.3f)) {
            h.frames(0.4)
            val flaps = h.flaps()
            h.gesture(x, 0.7f, x to 0.5f)
            assertEquals(2, h.state.lane, "tap/swipe up at x=$x keeps lane 2")
            assertEquals(flaps + 1, h.flaps())
        }

        // Swipe right into the edge: no lane change, the flap stays.
        h.frames(0.4)
        h.state.vy = -3.0
        h.post(UiCommand.Touch(0.5f, 0.6f))
        h.post(UiCommand.TouchMove(0.7f, 0.6f))
        assertEquals(2, h.state.lane)
        assertTrue(h.state.vy > Tuning.SWITCH_HOP, "edge swipe keeps the flap")
        assertEquals(1, h.swooshes())
        h.post(UiCommand.TouchUp)

        // Swipe left: back to the middle, then once more to lane 0 (one lane per swipe).
        h.frames(0.4)
        h.gesture(0.9f, 0.6f, 0.6f to 0.62f)
        assertEquals(1, h.state.lane)
        assertEquals(-1, h.ui.tapFx.last().dir, "◀ ripple")
        h.frames(0.4)
        h.gesture(0.9f, 0.6f, 0.2f to 0.6f)
        assertEquals(0, h.state.lane)
        h.frames(0.4)
        h.gesture(0.5f, 0.6f, 0.2f to 0.6f)
        assertEquals(0, h.state.lane, "left edge")
        assertEquals(3, h.swooshes())
        assertEquals(GameMode.Playing, h.state.mode)
    }

    @Test
    fun tutorialDodgeNeedsASwipe() {
        val h = LoopHarness(progressJson = """{"coins":0,"best":0,"runs":0,"tutorialDone":false}""")
        h.game.displayDensity = 2.625
        val s = h.state
        assertTrue(s.tutorialActive)
        assertEquals(HandMode.Flap, s.hand)
        assertEquals("flap", h.ui.tutorialHand?.mode)
        // Learning to flap: sideways swipes are ignored, touches flap.
        h.gesture(0.5f, 0.7f, 0.9f to 0.7f)
        assertEquals(1, s.lane)
        assertEquals(HandMode.None, s.hand)
        var t = 0.0
        while (s.hand != HandMode.Side && t < 30) {
            if (s.y < 4.6 && s.vy < 0) {
                h.post(UiCommand.Touch(0.8f, 0.7f))
                h.post(UiCommand.TouchUp)
            }
            h.frame()
            t += 1.0 / 60
        }
        assertEquals(HandMode.Side, s.hand)
        assertEquals(1, s.lane)
        assertEquals("side", h.ui.tutorialHand?.mode)
        // Frozen in front of the blocked middle row: a tap does not flap.
        val flaps = h.flaps()
        h.gesture(0.2f, 0.7f)
        h.frames(0.5)
        assertEquals(flaps, h.flaps())
        assertEquals(1, s.lane)
        assertEquals(HandMode.Side, s.hand)
        // A swipe left dodges.
        h.gesture(0.5f, 0.7f, 0.3f to 0.7f)
        assertEquals(0, s.lane)
        assertEquals(HandMode.None, s.hand)
        assertNull(h.ui.tutorialHand)
        assertEquals(flaps, h.flaps())
        assertEquals(GameMode.Playing, s.mode)
    }

    @Test
    fun sideHandSwipesFromTheBird() {
        val flap = TutorialHand.ui(HandMode.Flap, 0.5, 0.4, 3.0, "f")!!
        assertEquals("flap", flap.mode)
        assertEquals(0.5f, flap.x)
        assertEquals(0f, flap.dx)
        // Start of a cycle: at the bird; late in the cycle: slid right; the next cycle slides left.
        val start = TutorialHand.ui(HandMode.Side, 0.5, 0.4, 0.0, "s")!!
        assertEquals("side", start.mode)
        assertEquals(0.5f, start.x)
        assertEquals(0f, start.dx)
        val slid = TutorialHand.ui(HandMode.Side, 0.5, 0.4, TutorialHand.SWIPE_S * 0.75, "s")!!
        assertEquals(TutorialHand.SWIPE_DX.toFloat(), slid.dx, 1e-4f)
        assertEquals(0.5f, slid.x, "label stays under the bird")
        val back = TutorialHand.ui(HandMode.Side, 0.5, 0.4, TutorialHand.SWIPE_S * 1.75, "s")!!
        assertEquals(-TutorialHand.SWIPE_DX.toFloat(), back.dx, 1e-4f)
        assertNull(TutorialHand.ui(HandMode.None, 0.5, 0.4, 0.0, ""))
    }

    @Test
    fun projectedLaneBandsForTheHint() {
        val h = running()
        val b = h.game.game.input.laneBounds(h.state.y)
        // The chase cam never pans: symmetric bands, lanes near 20 / 50 / 80 %.
        assertTrue(abs(b[0] + b[1] - 1) < 1e-4, "symmetric ${b.toList()}")
        assertTrue(b[0] in 0.25..0.45, "b1=${b[0]}")
        val mid = h.game.game.input.screenX(0.0, h.state.y)
        assertEquals(0.5, mid, 1e-4)
    }
}
