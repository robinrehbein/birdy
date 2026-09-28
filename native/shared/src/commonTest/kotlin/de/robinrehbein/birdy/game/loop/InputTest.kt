package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.UiCommand
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputTest {
    @Test
    fun laneAtBoundsAndNearBird() {
        // Bird drawn at 0.5 in lane 1: the centre band always flaps in place.
        assertEquals(0, InputMapper.laneAt(0.1, 0.35, 0.65, 0.5, 1))
        assertEquals(1, InputMapper.laneAt(0.5, 0.35, 0.65, 0.5, 1))
        assertEquals(2, InputMapper.laneAt(0.9, 0.35, 0.65, 0.5, 1))
        // Exactly NEAR_BIRD away still counts as the bird's lane.
        assertEquals(1, InputMapper.laneAt(0.62, 0.35, 0.65, 0.5, 1))
        assertEquals(1, InputMapper.laneAt(0.38, 0.35, 0.65, 0.5, 1))
        // Mid-slide to lane 2: a tap on the drawn bird (x 0.6) keeps the target lane.
        assertEquals(2, InputMapper.laneAt(0.6, 0.35, 0.65, 0.6, 2))
        // Boundaries: x < b1 -> 0, x < b2 -> 1, else 2 (bird far away).
        assertEquals(0, InputMapper.laneAt(0.3499, 0.35, 0.65, 0.95, 2))
        assertEquals(1, InputMapper.laneAt(0.35, 0.35, 0.65, 0.0, 0))
        assertEquals(2, InputMapper.laneAt(0.65, 0.35, 0.65, 0.0, 0))
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
    fun projectedLaneBandsInARun() {
        val h = LoopHarness()
        h.startRun()
        h.frames(2.0)
        val b = h.game.game.input.laneBounds(h.state.y)
        // The chase cam never pans: symmetric bands, lanes near 20 / 50 / 80 %.
        assertTrue(abs(b[0] + b[1] - 1) < 1e-4, "symmetric ${b.toList()}")
        assertTrue(b[0] in 0.25..0.45, "b1=${b[0]}")
        val mid = h.game.game.input.screenX(0.0, h.state.y)
        assertEquals(0.5, mid, 1e-4)
        // Tap the left band -> lane 0, with a hop, not a flap.
        h.post(UiCommand.Touch(0.08f, 0.7f))
        assertEquals(0, h.state.lane)
        assertEquals(1, h.ui.tapFx.size)
        assertEquals(-1, h.ui.tapFx[0].dir)
        // Once the bird sits in lane 0, the middle band moves it back (one lane per tap).
        h.frames(0.5)
        h.post(UiCommand.Touch(0.5f, 0.7f))
        assertEquals(1, h.state.lane)
        assertEquals(GameMode.Playing, h.state.mode)
    }

    @Test
    fun swipeFromTouchMoves() {
        val h = LoopHarness()
        h.game.displayDensity = 2.625
        h.startRun()
        h.frames(1.0)
        h.post(UiCommand.TouchUp)
        // Tap the bird (flap) and drag right: the swipe wins, the bird goes to lane 2.
        h.post(UiCommand.Touch(0.5f, 0.6f))
        assertEquals(1, h.state.lane)
        h.post(UiCommand.TouchMove(0.52f, 0.6f))
        assertEquals(1, h.state.lane)
        h.post(UiCommand.TouchMove(0.56f, 0.6f))
        assertEquals(2, h.state.lane)
        // Only one swipe per touch.
        h.post(UiCommand.TouchMove(0.9f, 0.6f))
        assertEquals(2, h.state.lane)
        h.post(UiCommand.TouchUp)
    }
}
