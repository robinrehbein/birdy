package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.DeathCause
import de.robinrehbein.birdy.game.GameEvent
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.RevivePay
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.ZonesHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviveTest {
    private val dt = 1.0 / 60
    private val gap = GapSpec(center = 5.0, size = 5.0)

    private fun harness(best: Int = 20, pay: RevivePay? = RevivePay.Ad): SimHarness {
        val h = SimHarness(progress = FakeProgress(best = best))
        h.sim.reviveOption = { pay }
        return h
    }

    /** A run past the hover with an empty track, no spawning and [score] already scored. */
    private fun emptyRun(h: SimHarness, score: Int, coins: Int = 7) {
        h.sim.resetGame()
        h.sim.flap()
        h.sim.clearTrack()
        h.sim.zoneMarks.clear()
        h.state.lastGateZ = -1000.0
        h.state.y = 5.0
        h.state.vy = 0.0
        h.state.score = score
        h.state.coins = coins
        h.events.clear()
    }

    /** Crashes into a row blocked in the bird's lane, then runs the death freeze out. */
    private fun crash(h: SimHarness) {
        h.sim.debug.scriptedRow(-0.5, listOf(null, null, null))
        h.sim.step(dt, null)
        assertEquals(GameMode.Dead, h.state.mode)
        assertEquals(DeathCause.Blocked, h.state.deathCause)
        h.run(Tuning.DEAD_TO_OVER + 0.05)
    }

    @Test
    fun crashNearRecordOffersReviveAndAcceptContinuesTheRun() {
        val h = harness(best = 20)
        val s = h.state
        emptyRun(h, score = 17)
        crash(h)
        // 17 >= 0.8 * 20: offer instead of the game over.
        assertEquals(GameMode.Dead, s.mode)
        val offer = assertNotNull(s.revive)
        assertEquals(RevivePay.Ad, offer.pay)
        assertTrue(h.events.none { it is GameEvent.GameOver })

        // Accepting freezes the countdown while the ad runs.
        assertEquals(RevivePay.Ad, h.sim.acceptRevive())
        assertNull(h.sim.acceptRevive(), "one payment per offer")
        h.run(Tuning.REVIVE_OFFER_TIME + 1)
        assertEquals(GameMode.Dead, s.mode)
        h.sim.declineRevive() // a stray tap while paying changes nothing
        assertNotNull(s.revive)

        h.sim.reviveResult(true)
        assertEquals(GameMode.Playing, s.mode)
        assertNull(s.revive)
        assertTrue(s.revived)
        assertEquals(17, s.score)
        assertEquals(7, s.coins)
        assertTrue(s.hold, "hover until the first tap")
        assertEquals(ZonesHint.Hold, s.zonesHint)
        assertEquals(Tuning.REVIVE_GRACE, s.grace)
        assertTrue(s.invincible)
        assertTrue(h.events.any { it is GameEvent.Revived })
        val crashed = h.sim.gates.single { it.active }
        assertTrue(crashed.passed, "the colliding row counts as cleared")
        assertFalse(crashed.visible)

        // Hovering: grace waits for the first tap, no point for the cleared row.
        h.run(1.0)
        assertEquals(GameMode.Playing, s.mode)
        assertEquals(Tuning.REVIVE_GRACE, s.grace)
        h.sim.flap()
        assertFalse(s.hold)
        h.run(0.5)
        assertEquals(GameMode.Playing, s.mode)
        assertEquals(17, s.score)
        assertTrue(s.grace > 0 && s.grace < Tuning.REVIVE_GRACE)

        // Second crash after the grace: straight to game over, no second offer.
        h.run(Tuning.REVIVE_GRACE) { s.y = 5.0; s.vy = 0.0; false }
        assertEquals(0.0, s.grace)
        h.events.clear()
        s.y = 5.0
        s.vy = 0.0
        crash(h)
        assertNull(s.revive)
        assertEquals(GameMode.Over, s.mode)
        assertEquals(17, h.progress.finished.single().score)
        assertEquals(7, h.progress.finished.single().coins)
    }

    @Test
    fun crashBelowEightyPercentGoesStraightToGameOver() {
        val h = harness(best = 20)
        emptyRun(h, score = 15) // 15 < 16
        crash(h)
        assertNull(h.state.revive)
        assertEquals(GameMode.Over, h.state.mode)
    }

    @Test
    fun eligibilityRules() {
        // Record below 10: never.
        harness(best = 9).let { h ->
            emptyRun(h, score = 9)
            crash(h)
            assertEquals(GameMode.Over, h.state.mode)
        }
        // Exactly 80 % of the record (and a new record) qualify.
        harness(best = 10).let { h ->
            emptyRun(h, score = 8)
            assertTrue(h.sim.reviveEligible())
            h.state.score = 12
            assertTrue(h.sim.reviveEligible())
            h.state.score = 7
            assertFalse(h.sim.reviveEligible())
        }
        // No way to pay (no ad loaded, ads not removed): no offer.
        harness(best = 20, pay = null).let { h ->
            emptyRun(h, score = 19)
            crash(h)
            assertNull(h.state.revive)
            assertEquals(GameMode.Over, h.state.mode)
        }
    }

    @Test
    fun timeoutDeclineAndFailedPaymentEndTheRun() {
        // Countdown runs out: normal game over.
        harness().let { h ->
            emptyRun(h, score = 18)
            crash(h)
            assertNotNull(h.state.revive)
            h.run(Tuning.REVIVE_OFFER_TIME + 0.1)
            assertNull(h.state.revive)
            assertEquals(GameMode.Over, h.state.mode)
            assertEquals(1, h.progress.finished.size)
        }
        // "No thanks".
        harness().let { h ->
            emptyRun(h, score = 18)
            crash(h)
            h.sim.declineRevive()
            assertEquals(GameMode.Over, h.state.mode)
        }
        // Ad dismissed without the reward.
        harness(pay = RevivePay.Coins).let { h ->
            emptyRun(h, score = 18)
            crash(h)
            assertEquals(RevivePay.Coins, h.sim.acceptRevive())
            h.sim.reviveResult(false)
            assertEquals(GameMode.Over, h.state.mode)
            assertFalse(h.state.revived)
        }
    }

    @Test
    fun reviveHoversAtTheNextGapInTheLane() {
        val h = harness()
        emptyRun(h, score = 18)
        h.sim.debug.scriptedRow(-30.0, listOf(gap, GapSpec(center = 9.0, size = 4.0), gap))
        crash(h)
        h.sim.acceptRevive()
        h.sim.reviveResult(true)
        assertEquals(9.0, h.state.holdY, 0.5)
        h.run(0.5)
        assertTrue(h.state.y in 8.0..10.0)
    }

    @Test
    fun acceptedReviveWithoutAdResultTimesOutToGameOver() {
        val h = harness()
        emptyRun(h, score = 18)
        crash(h)
        h.sim.acceptRevive()
        h.run(Tuning.REVIVE_OFFER_TIME + 0.5)
        assertNotNull(h.state.revive, "the countdown is frozen while waiting for the ad")
        h.run(Tuning.REVIVE_PENDING_TIMEOUT)
        assertNull(h.state.revive)
        assertEquals(GameMode.Over, h.state.mode)
        assertEquals(1, h.progress.finished.size)
    }

    @Test
    fun reviveHandsTheRecordMarkerToTheNextRow() {
        val h = harness()
        emptyRun(h, score = 18)
        val near = h.sim.debug.scriptedRow(-30.0, listOf(gap, gap, gap))
        val far = h.sim.debug.scriptedRow(-60.0, listOf(gap, gap, gap))
        near.record = true
        crash(h)
        h.sim.acceptRevive()
        h.sim.reviveResult(true)
        // The crash row is cleared without a point, so the record now falls one row later.
        assertEquals(1, h.state.unscoredRows)
        assertFalse(near.record)
        assertTrue(far.record)
    }
}
