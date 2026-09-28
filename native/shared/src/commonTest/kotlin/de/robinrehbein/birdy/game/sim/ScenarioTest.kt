package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.DeathCause
import de.robinrehbein.birdy.game.GameEvent
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.HandMode
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.TutorialStep
import de.robinrehbein.birdy.game.ZonesHint
import de.robinrehbein.birdy.meta.Achievement
import de.robinrehbein.birdy.meta.LocalizedText
import de.robinrehbein.birdy.meta.RunStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScenarioTest {
    private val dt = 1.0 / 60

    /** A run past the hover with an empty track and no automatic spawning. */
    private fun emptyRun(h: SimHarness, y: Double = 5.0) {
        h.sim.resetGame()
        h.sim.flap()
        h.sim.clearTrack()
        h.sim.zoneMarks.clear()
        h.state.lastGateZ = -1000.0
        h.state.y = y
        h.state.vy = 0.0
        h.events.clear()
    }

    private fun row(h: SimHarness, z: Double, vararg spec: GapSpec?): GateRow = h.sim.debug.scriptedRow(z, spec.toList())

    /** Steps with the bird pinned at [y] (no gravity drift) until [until] or [maxSteps]. */
    private fun pinned(h: SimHarness, y: Double, maxSteps: Int = 120, until: () -> Boolean = { false }) {
        repeat(maxSteps) {
            h.state.y = y
            h.state.vy = 0.0
            h.sim.step(dt, null)
            if (until()) return
        }
    }

    @Test
    fun menuTapStartsRunWithResetOrder() {
        val h = SimHarness(progress = FakeProgress(runs = 3))
        val s = h.state
        s.power[PowerType.Magnet.ordinal] = 3.0
        s.score = 12
        h.sim.step(dt, null)
        assertEquals(GameMode.Ready, s.mode)
        h.sim.flap() // from the menu: resetGame
        assertEquals(GameMode.Playing, s.mode)
        assertTrue(s.hold)
        assertEquals(0, s.score)
        assertEquals(18.0, s.speed)
        assertEquals(1, s.lane)
        assertEquals(0.0, s.power(PowerType.Magnet))
        assertEquals(RunStats(), s.run)
        assertEquals(ZonesHint.Hold, s.zonesHint)
        assertEquals(Menu.Start, s.menu)
        assertTrue(s.lastGateZ <= -Tuning.SPAWN_DISTANCE)
        assertEquals(5, s.gatesSpawned, "pre-spawn at score 0 fills five rows")
        val active = h.sim.gates.filter { it.active }.sortedByDescending { it.z }
        assertEquals(-60.0, active.first().z)
        assertEquals(-60.0 - Difficulty.spacing(0), active[1].z, 1e-9)
        assertTrue(active.all { r -> r.lanes.none { it.blocked } }, "warm-up rows are fully open")
        // Events: RunStarted first, then the swipe hint for the 4th run.
        assertTrue(h.events[0] is GameEvent.RunStarted)
        val hint = h.eventsOf<GameEvent.Toast>().single()
        assertEquals("swipeHint", hint.key)
        assertEquals(900, hint.delayMs)

        // Hover: no falling, no run time while holding.
        h.run(1.0)
        assertEquals(0.0, s.runTime)
        assertTrue(s.y in 4.6..5.4)
        h.sim.flap()
        assertFalse(s.hold)
        assertEquals(Tuning.FLAP_VELOCITY, s.vy)
        assertEquals(ZonesHint.Show, s.zonesHint)
        assertTrue(h.events.any { it is GameEvent.HoldEnded })
    }

    @Test
    fun noSwipeHintOnOtherRuns() {
        val h = SimHarness(progress = FakeProgress(runs = 2))
        h.sim.flap()
        assertTrue(h.eventsOf<GameEvent.Toast>().isEmpty())
    }

    @Test
    fun tutorialSequence() {
        val h = SimHarness(progress = FakeProgress(runs = 0))
        val s = h.state
        h.sim.startTutorialRun()
        assertEquals(TutorialStep.Flap, s.tutorialStep)
        assertEquals(HandMode.Flap, s.hand)
        assertEquals(ZonesHint.None, s.zonesHint)
        val rows = h.sim.gates.filter { it.active }.sortedByDescending { it.z }
        for (r in rows.take(3)) assertEquals(listOf(true, false, true), r.lanes.map { it.blocked })
        assertEquals(listOf(false, true, false), rows[3].lanes.map { it.blocked })
        assertEquals(rows[3], h.sim.tutorialGate)

        // Touches only flap while learning to flap; sideways swipes are ignored.
        assertFalse(h.sim.pointerDown())
        assertEquals(1, s.lane)
        assertEquals(TutorialStep.Fly, s.tutorialStep)
        assertEquals(HandMode.None, s.hand)
        h.sim.pointerUp()
        h.sim.pointerDown()
        assertFalse(h.sim.swipe(+1))
        assertEquals(1, s.lane)
        h.sim.pointerUp()

        // Fly the middle gaps (5.2 ± 3.1) until the dodge lesson freezes the bird.
        var steps = 0
        while (s.tutorialStep == TutorialStep.Fly && steps++ < 2000) {
            if (s.y < 4.6 && s.vy < 0) {
                h.sim.pointerDown()
                h.sim.pointerUp()
            }
            h.sim.step(dt, null)
        }
        assertEquals(GameMode.Playing, s.mode)
        assertEquals(TutorialStep.Switch, s.tutorialStep)
        assertEquals(HandMode.Side, s.hand)
        assertEquals(ZonesHint.Show, s.zonesHint)
        assertEquals(3, s.score)
        val frozenZ = h.sim.tutorialGate!!.z
        assertTrue(frozenZ > -13)
        // Frozen: the bird hovers and the rows wait (JS returns before moveWorld; main-b.md §9 says the world scrolls).
        val runTime = s.runTime
        h.run(1.0)
        assertEquals(runTime, s.runTime)
        assertTrue(kotlin.math.abs(s.y - s.freezeY) <= 0.15 + 1e-9)
        assertEquals(frozenZ, h.sim.tutorialGate!!.z, "rows wait while frozen")
        // A plain touch is swallowed: no flap, no lane change.
        val flaps = h.eventsOf<GameEvent.Flap>().size
        assertFalse(h.sim.pointerDown())
        h.sim.pointerUp()
        assertEquals(TutorialStep.Switch, s.tutorialStep)
        assertEquals(flaps, h.eventsOf<GameEvent.Flap>().size)
        assertEquals(1, s.lane)
        // A sideways swipe dodges (either side works; here left), with a hop.
        h.sim.pointerDown()
        assertTrue(h.sim.swipe(-1))
        h.sim.pointerUp()
        assertEquals(TutorialStep.Go, s.tutorialStep)
        assertEquals(HandMode.None, s.hand)
        assertEquals(0, s.lane)
        assertEquals(Tuning.SWITCH_HOP, s.vy)
        assertEquals(flaps, h.eventsOf<GameEvent.Flap>().size)
        steps = 0
        while (s.tutorialActive && steps++ < 600) {
            if (s.y < 4.6 && s.vy < 0) {
                h.sim.pointerDown()
                h.sim.pointerUp()
            }
            h.sim.step(dt, null)
        }
        assertEquals(0, s.lane, "taps keep the dodged lane")
        assertEquals(GameMode.Playing, s.mode)
        assertFalse(s.tutorialActive)
        assertNull(s.tutorialStep)
        assertEquals(1, h.progress.tutorialDoneCalls)
        assertTrue(h.eventsOf<GameEvent.Toast>().any { it.key == "tutDone" })
    }

    @Test
    fun collisionCauses() {
        val gap = GapSpec(center = 5.0, size = 5.0)
        // Blocked lane.
        SimHarness().let { h ->
            emptyRun(h)
            row(h, -0.5, gap, null, gap)
            h.sim.step(dt, null)
            assertEquals(DeathCause.Blocked, h.state.deathCause)
        }
        // Upper pipe: gap 5 ± 2.5, bird at 7.3 (7.3 + 0.4 >= 7.5).
        SimHarness().let { h ->
            emptyRun(h, y = 7.3)
            row(h, -0.5, gap, gap, gap)
            pinned(h, 7.3, 1)
            assertEquals(DeathCause.PipeTop, h.state.deathCause)
        }
        // Lower pipe.
        SimHarness().let { h ->
            emptyRun(h, y = 2.8)
            row(h, -0.5, gap, gap, gap)
            pinned(h, 2.8, 1)
            assertEquals(DeathCause.PipeBottom, h.state.deathCause)
        }
        // Risen cactus: beat 3.0 (rise 1) lifts hitLow to 3.4.
        SimHarness().let { h ->
            emptyRun(h, y = 3.6)
            row(h, -0.5, gap, gap.copy(plant = true), gap)
            h.state.time = 3.0 * 60 / Tuning.FALLBACK_BPM - dt
            pinned(h, 3.6, 1)
            assertEquals(DeathCause.Plant, h.state.deathCause)
            assertEquals(3.4, h.sim.gates.first { it.active }.lanes[1].hitLow, 1e-9)
        }
        // Same height with the cactus hidden: passes.
        SimHarness().let { h ->
            emptyRun(h, y = 3.6)
            row(h, -0.5, gap, gap.copy(plant = true), gap)
            h.state.time = 0.5 * 60 / Tuning.FALLBACK_BPM - dt
            pinned(h, 3.6, 1)
            assertNull(h.state.deathCause)
        }
        // Ground.
        SimHarness().let { h ->
            emptyRun(h, y = 0.3)
            h.state.vy = -5.0
            h.sim.step(dt, null)
            assertEquals(DeathCause.Ground, h.state.deathCause)
            assertEquals(h.state.radius, h.state.y)
        }
    }

    @Test
    fun nearMissChain() {
        val h = SimHarness()
        emptyRun(h)
        val tight = GapSpec(center = 5.0, size = 2 * (0.4 + 0.3)) // clearance 0.3 < 0.45
        repeat(3) { k ->
            row(h, -2.0, tight, tight, tight)
            pinned(h, 5.0) { h.sim.gates.none { it.active && !it.passed } }
            assertEquals(k + 1, h.state.nearChain)
            h.sim.clearTrack()
        }
        assertEquals(listOf(1, 2, 3), h.eventsOf<GameEvent.NearMiss>().map { it.chain })
        assertEquals(3, h.state.coins)
        assertEquals(3, h.state.run.near)
        assertEquals(3, h.state.run.bestChain)
        assertEquals(listOf(15, 15, 15), h.eventsOf<GameEvent.Buzz>().map { it.millis })
        // A comfortable pass breaks the chain (on a fresh pooled row: see staleMinClear).
        h.sim.debug.scriptedRow(-2.0, List(3) { GapSpec(5.0, 5.0) }, h.sim.gates[5])
        pinned(h, 5.0) { h.sim.gates.none { it.active && !it.passed } }
        assertEquals(0, h.state.nearChain)
        assertEquals(4, h.state.score)
        assertEquals(3, h.state.run.bestChain)
    }

    /**
     * JS quirk kept on purpose: the frame after a pass can still record `minClear` (collision zone
     * 1.85 > pass line 1.6), and `configure()` never clears it, so a reused row inherits it.
     */
    @Test
    fun staleMinClear() {
        val h = SimHarness()
        emptyRun(h)
        val tight = GapSpec(center = 5.0, size = 1.4)
        val gate = row(h, -2.0, tight, tight, tight)
        pinned(h, 5.0) { gate.z > 1.9 }
        assertEquals(1, h.state.nearChain)
        assertNotNull(gate.minClear, "recorded after the pass")
        h.sim.clearTrack()
        h.sim.debug.scriptedRow(-2.0, List(3) { GapSpec(5.0, 5.0) }, gate)
        pinned(h, 5.0) { gate.passed }
        assertEquals(2, h.state.nearChain, "the wide row inherits the old clearance")
    }

    @Test
    fun starThenGraceInvincibility() {
        val h = SimHarness()
        emptyRun(h)
        h.sim.activatePower(PowerType.Star)
        assertEquals(6.0, h.state.power(PowerType.Star))
        assertTrue(h.state.invincible)
        assertEquals(1, h.state.run.powerups)
        // Flies through a blocked row.
        row(h, -2.0, null, null, GapSpec(5.0, 5.0))
        pinned(h, 5.0) { h.sim.gates.none { it.active && !it.passed } }
        assertEquals(GameMode.Playing, h.state.mode)
        assertEquals(1, h.state.run.starRows)
        // Speed boost towards baseSpeed * 1.35.
        assertTrue(h.state.speed > 18.0)
        // Ground bounce while invincible.
        h.state.y = 0.2
        h.state.vy = -10.0
        h.sim.step(dt, null)
        assertEquals(Tuning.FLAP_VELOCITY * 0.9, h.state.vy)
        assertTrue(h.events.any { it is GameEvent.Bounce })
        // Star runs out: grace follows, then vulnerability.
        pinned(h, 5.0, 2000) { h.state.power(PowerType.Star) == 0.0 }
        assertEquals(GameEvent.PowerDown(PowerType.Star), h.events.last { it is GameEvent.PowerDown })
        assertEquals(Tuning.GRACE_TIME - dt, h.state.grace, 1e-12)
        assertTrue(h.state.invincible)
        h.sim.updateBirdVisual(dt)
        pinned(h, 5.0, 200) { h.state.grace == 0.0 }
        assertFalse(h.state.invincible)
        // Star pickup resets grace.
        h.state.grace = 1.0
        h.sim.activatePower(PowerType.Star)
        assertEquals(0.0, h.state.grace)
    }

    @Test
    fun graceBlinks() {
        val h = SimHarness()
        emptyRun(h)
        h.state.grace = 1.0
        val seen = HashSet<Boolean>()
        repeat(30) {
            h.state.time += dt
            h.sim.updateBirdVisual(dt)
            seen += h.state.pose.visible
        }
        assertEquals(setOf(true, false), seen)
    }

    @Test
    fun miniPickup() {
        val h = SimHarness(progress = FakeProgress(levels = mutableMapOf("mini" to 1)))
        emptyRun(h)
        val p = h.sim.pickups.first { it.type == PowerType.Mini }
        p.active = true; p.x = 0.0; p.y = 5.0; p.z = -0.5
        pinned(h, 5.0, 1)
        assertFalse(p.active)
        // updatePowers runs before the pickups in the same frame: full duration (9 + 3).
        assertEquals(12.0, h.state.power(PowerType.Mini))
        pinned(h, 5.0, 60)
        assertTrue(h.state.radius < 0.32, "mini radius eases to 0.3: ${h.state.radius}")
    }

    @Test
    fun magnetPullsSideLaneCoin() {
        val h = SimHarness()
        emptyRun(h)
        h.sim.activatePower(PowerType.Magnet)
        h.sim.placeCoin(3.0, 5.0, -8.0)
        pinned(h, 5.0, 60) { h.state.coins > 0 }
        assertEquals(1, h.state.coins)
        assertEquals(1, h.eventsOf<GameEvent.CoinCollected>().size)
        // Without the magnet the same coin flies by.
        val h2 = SimHarness()
        emptyRun(h2)
        h2.sim.placeCoin(3.0, 5.0, -8.0)
        pinned(h2, 5.0, 60)
        assertEquals(0, h2.state.coins)
    }

    @Test
    fun zonesEveryTenRowsWithRush() {
        val h = SimHarness(seed = 7)
        h.sim.debug.setGod(true)
        h.sim.resetGame()
        h.sim.flap()
        val themeAt = ArrayList<Pair<Int, Int>>()
        var seen = 0
        h.run(200.0) {
            val themes = h.eventsOf<GameEvent.SceneryTheme>()
            if (themes.size > seen) {
                themeAt += themes.last().zone to h.state.gatesSpawned
                // The rush replaced a row: no reach limit, one banner per rush.
                assertNull(h.state.prevGaps)
                seen = themes.size
            }
            h.pilot()
            h.state.zone >= 3
        }
        assertEquals(3, h.state.zone)
        assertTrue(themeAt.size >= 3)
        for ((zone, rows) in themeAt) assertEquals(zone * Tuning.ZONE_ROWS, rows, "rush for zone $zone at row $rows")
        assertEquals(listOf(1, 2, 3), h.eventsOf<GameEvent.ZoneEntered>().map { it.zone })
        assertEquals(3, h.state.run.zone)
    }

    @Test
    fun deathThenDeadThenOver() {
        val h = SimHarness(progress = FakeProgress(best = 3))
        emptyRun(h)
        h.state.score = 4
        h.state.coins = 2
        h.state.runTime = 12.0
        h.sim.die(DeathCause.PipeTop)
        val s = h.state
        assertEquals(GameMode.Dead, s.mode)
        assertEquals(Tuning.HIT_STOP, s.hitStop)
        assertEquals(0.5, s.shake)
        assertTrue(s.vy >= 6.0)
        assertNotNull(s.lastRun)
        assertEquals(DeathCause.PipeTop, s.lastRun!!.cause)
        assertEquals(Tuning.BIRD_SCALE * 0.72, s.pose.scaleY, 1e-12)
        assertTrue(h.events.contains(GameEvent.Buzz(70)))
        // Dying again is ignored.
        h.sim.die(DeathCause.Ground)
        assertEquals(DeathCause.PipeTop, s.deathCause)

        // Hit-stop freezes the simulation for 0.14 s.
        val t0 = s.time
        var frames = 0
        while (s.hitStop > 0) { h.sim.update(dt, null); frames++ }
        assertEquals(t0, s.time)
        assertEquals(9, frames)
        // Then 0.45 s of falling until the game-over screen.
        var dead = 0.0
        while (s.mode == GameMode.Dead) { h.sim.update(dt, null); dead += dt }
        assertEquals(GameMode.Over, s.mode)
        assertTrue(dead in 0.45..(0.45 + 2 * dt), "dead for $dead s")
        val over = h.eventsOf<GameEvent.GameOver>().single().summary
        assertEquals(4, over.score)
        assertEquals(2, over.coins)
        assertEquals(12.0, over.time)
        assertEquals(DeathCause.PipeTop, over.cause)
        assertTrue(over.result.isBest)
        assertEquals(RunStats(score = 4, coins = 2), h.progress.finished.single())
        assertTrue(h.events.contains(GameEvent.Fanfare(250)))
        // The bird keeps falling behind the panel; wings stop in 'over'.
        val phase = s.wingPhase
        h.sim.update(dt, null)
        assertEquals(phase, s.wingPhase)
    }

    @Test
    fun restartDebounce() {
        val h = SimHarness()
        var now = 10_000L
        h.sim.clockMillis = { now }
        emptyRun(h)
        h.sim.die(DeathCause.Ground)
        while (h.state.mode != GameMode.Over) h.sim.update(dt, null)
        assertEquals(10_000L, h.state.overAt)
        now += 350
        assertFalse(h.sim.tryRestart())
        // A plain tap on the game-over screen does nothing either.
        h.sim.tapLane(1)
        assertEquals(GameMode.Over, h.state.mode)
        now += 1
        assertTrue(h.sim.tryRestart())
        assertEquals(GameMode.Playing, h.state.mode)
    }

    @Test
    fun tutorialDodgeSwipeRight() {
        val h = SimHarness(progress = FakeProgress(runs = 0))
        val s = h.state
        h.sim.startTutorialRun()
        h.sim.pointerDown() // ends the hover
        h.sim.pointerUp()
        var steps = 0
        while (s.tutorialStep != TutorialStep.Switch && steps++ < 3000) {
            if (s.y < 4.6 && s.vy < 0) {
                h.sim.pointerDown()
                h.sim.pointerUp()
            }
            h.sim.step(dt, null)
        }
        assertEquals(TutorialStep.Switch, s.tutorialStep)
        // Into the other side works as well; a swipe needs an armed touch.
        assertFalse(h.sim.swipe(+1))
        h.sim.pointerDown()
        assertTrue(h.sim.swipe(+1))
        assertEquals(2, s.lane)
        assertEquals(TutorialStep.Go, s.tutorialStep)
    }

    @Test
    fun touchFlapsInPlaceAndSwipesStick() {
        val h = SimHarness()
        emptyRun(h)
        val s = h.state
        // A touch flaps in the current lane, never changes lanes on touch-down.
        s.vy = -2.0
        assertTrue(h.sim.pointerDown())
        assertEquals(1, s.lane)
        assertEquals(Tuning.FLAP_VELOCITY, s.vy)
        // Sideways swipe: one lane from the gesture start, the flap becomes a hop.
        assertTrue(h.sim.swipe(+1))
        assertEquals(2, s.lane)
        assertEquals(Tuning.SWITCH_HOP, s.vy)
        assertEquals(Tuning.WING_SWITCH, s.wingSpeed)
        assertEquals(0.5, s.squash)
        assertEquals(GameEvent.LaneSwitch(1, 2), h.events.last())
        assertFalse(h.sim.swipe(+1), "one swipe per touch")
        assertFalse(h.sim.swipe(-1), "one action per gesture")
        assertEquals(2, s.lane)
        h.sim.pointerUp()
        // Following touches flap in lane 2.
        repeat(3) {
            s.vy = -2.0
            h.sim.pointerDown()
            h.sim.pointerUp()
            assertEquals(2, s.lane)
            assertEquals(Tuning.FLAP_VELOCITY, s.vy)
        }
        // Swipe into the edge keeps the flap.
        s.vy = -2.0
        h.sim.pointerDown()
        assertFalse(h.sim.swipe(+1))
        assertEquals(2, s.lane)
        assertEquals(Tuning.FLAP_VELOCITY, s.vy)
        h.sim.pointerUp()
        // A swipe after the touch ended does nothing.
        assertFalse(h.sim.swipe(-1))
        assertEquals(2, s.lane)
        // Swipe left, twice: lane 1, then lane 0; a high vy before the touch is kept.
        s.vy = 9.0
        h.sim.pointerDown()
        assertTrue(h.sim.swipe(-1))
        assertEquals(1, s.lane)
        assertEquals(9.0, s.vy)
        h.sim.pointerUp()
        h.sim.pointerDown()
        assertTrue(h.sim.swipe(-1))
        h.sim.pointerUp()
        assertEquals(0, s.lane)
        h.sim.pointerDown()
        assertFalse(h.sim.swipe(-1))
        assertEquals(0, s.lane)
        h.sim.pointerUp()
        // Paused: a touch only resumes.
        h.sim.setPaused(true)
        assertFalse(h.sim.pointerDown())
        assertFalse(s.paused)
        assertFalse(h.sim.swipe(+1))
        assertEquals(0, s.lane)
    }

    @Test
    fun laneInputAndSwipe() {
        val h = SimHarness()
        emptyRun(h)
        val s = h.state
        // Bot/keyboard lane action: two lanes away moves one lane with a small hop.
        s.lane = 0; s.x = -3.0; s.vy = -4.0
        h.sim.tapLane(2)
        assertEquals(1, s.lane)
        assertEquals(Tuning.SWITCH_HOP, s.vy)
        assertEquals(Tuning.WING_SWITCH, s.wingSpeed)
        assertEquals(GameEvent.LaneSwitch(0, 1), h.events.last())
        // Own lane flaps.
        h.sim.tapLane(1)
        assertEquals(Tuning.FLAP_VELOCITY, s.vy)
        // Touch flaps in place, then a right swipe takes it back as a hop to lane 2.
        s.vy = -2.0
        assertTrue(h.sim.pointerDown())
        assertEquals(Tuning.FLAP_VELOCITY, s.vy)
        assertTrue(h.sim.swipe(+1))
        assertEquals(2, s.lane)
        assertEquals(Tuning.SWITCH_HOP, s.vy)
        assertFalse(h.sim.swipe(+1), "one swipe per touch")
        h.sim.pointerUp()
        // Swipe into the edge keeps the tap.
        s.vy = -2.0
        h.sim.pointerDown()
        assertFalse(h.sim.swipe(+1))
        assertEquals(Tuning.FLAP_VELOCITY, s.vy)
        // Keyboard.
        h.sim.setLane(s.lane - 1)
        assertEquals(1, s.lane)
        h.sim.setLane(-5)
        assertEquals(0, s.lane)
    }

    @Test
    fun pauseAndMenu() {
        val h = SimHarness()
        emptyRun(h)
        h.sim.activatePower(PowerType.Magnet)
        h.sim.setPaused(true)
        h.sim.flap() // a tap while paused only resumes
        assertFalse(h.state.paused)
        assertEquals(0.0, h.state.vy)
        h.sim.goToMenu()
        assertEquals(GameMode.Ready, h.state.mode)
        assertEquals(0.0, h.state.power(PowerType.Magnet))
        assertEquals(5.0, h.state.y)
        assertTrue(h.sim.gates.none { it.active } && h.sim.coins.none { it.active })
        assertTrue(h.events.any { it is GameEvent.WentToMenu })
    }

    @Test
    fun missionAndAchievementPreviewsOncePerRun() {
        val h = SimHarness()
        h.progress.missionRules["coins3"] = { it.coins >= 3 }
        val ach = Achievement("near1", "trophy", LocalizedText("a", "a"), LocalizedText("b", "b"), "near", 1, 50)
        h.progress.achievementRules[ach] = { it.near >= 1 }
        emptyRun(h)
        repeat(5) { k -> h.sim.placeCoin(0.0, 5.0, -0.3 - k * 0.01) }
        pinned(h, 5.0, 1)
        assertEquals(5, h.state.coins)
        assertEquals(listOf(listOf("coins3")), h.eventsOf<GameEvent.MissionPreview>().map { it.missionIds })
        h.sim.nearMiss()
        h.sim.nearMiss()
        assertEquals(1, h.eventsOf<GameEvent.AchievementPreview>().size)
        assertTrue(h.events.contains(GameEvent.Buzz(25)))
    }

    @Test
    fun recordToastMidRun() {
        val h = SimHarness(progress = FakeProgress(best = 5))
        emptyRun(h)
        repeat(6) {
            row(h, -2.0, GapSpec(5.0, 5.0), GapSpec(5.0, 5.0), GapSpec(5.0, 5.0))
            pinned(h, 5.0) { h.sim.gates.none { it.active && !it.passed } }
            h.sim.clearTrack()
        }
        assertEquals(6, h.state.score)
        assertEquals(listOf("recordToast"), h.eventsOf<GameEvent.Toast>().map { it.key })
    }
}
