package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.bot.Bot
import de.robinrehbein.birdy.game.bot.BotSkill
import de.robinrehbein.birdy.game.GameState
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Native additions: per-zone difficulty waves, the late-game term and wandering gaps. */
class WaveAndWanderTest {
    @Test
    fun firstZoneIsTheJsCurve() {
        for (row in 0 until Tuning.ZONE_ROWS) for (score in 0..12) {
            assertEquals(Difficulty.difficulty(score), Difficulty.intensity(score, row), "score $score row $row")
        }
    }

    @Test
    fun zonesDipAfterTheRushAndRampUp() {
        for (zone in 1..6) {
            val first = zone * Tuning.ZONE_ROWS
            val waves = (first until first + Tuning.ZONE_ROWS).map { Difficulty.wave(40, it) }
            assertTrue(waves.first() < 0, "dip after the rush: $waves")
            assertTrue(waves.last() > 0, "peak before the next rush: $waves")
            assertTrue(waves.zipWithNext().all { (a, b) -> b > a }, "ramps up: $waves")
        }
        // Before score 40 the wave is scaled down with the difficulty.
        assertTrue(abs(Difficulty.wave(10, 19)) < abs(Difficulty.wave(40, 19)) / 3)
        assertEquals(0.0, Difficulty.wave(0, 15))
    }

    @Test
    fun lateGameKeepsGrowingButStaysSafe() {
        var last = 0.0
        for (score in 40..400 step 20) {
            val i = Difficulty.intensity(score, 15) // same zone position
            assertTrue(i > last, "intensity grows at $score")
            last = i
        }
        assertTrue(last < 1 + Difficulty.LATE_MAX + Difficulty.WAVE_AMP)
        // Below 40 the extra term is off.
        assertEquals(Difficulty.difficulty(30) + Difficulty.wave(30, 15), Difficulty.intensity(30, 15))
    }

    @Test
    fun gapsNeverBelowTheSafeMinimum() {
        for (seed in 0 until 50) {
            val rng = Random(seed)
            for (row in 0 until 200) {
                val gen = RowGenerator.gateSpec(row * 3, row, null, false, rng)
                for (g in gen.spec) if (g != null) assertTrue(g.size >= Difficulty.MIN_GAP, "gap $g at row $row")
            }
        }
    }

    @Test
    fun wanderingGapsOnlyLateAndNeverOnTheEasyLane() {
        var wanders = 0
        var rows = 0
        for (seed in 0 until 300) {
            val rng = Random(seed)
            var prev: List<GapSpec?>? = null
            for (row in 0 until 60) {
                if (row > 0 && row % Tuning.ZONE_ROWS == 0) prev = null
                val score = row * 2
                val gen = RowGenerator.gateSpec(score, row, prev, false, rng)
                val w = gen.spec.indices.filter { (gen.spec[it]?.wanderFrom ?: -1) >= 0 }
                assertTrue(w.size <= 1, "one wandering gap per row: ${gen.spec}")
                for (to in w) {
                    val g = gen.spec[to]!!
                    assertTrue(score >= RowGenerator.WANDER_SCORE, "wander at score $score")
                    assertEquals(1, abs(g.wanderFrom - to), "slides into a neighbour: ${gen.spec}")
                    assertTrue(to != gen.easy && g.wanderFrom != gen.easy, "easy lane ${gen.easy} touched: ${gen.spec}")
                    assertEquals(null, gen.spec[g.wanderFrom], "the start lane ends as pipe")
                    assertFalse(g.pulse || g.plant || g.amp != 0.0, "plain gap only: $g")
                    assertTrue(gen.spec[gen.easy] != null)
                    wanders++
                }
                if (score >= RowGenerator.WANDER_SCORE) rows++
                prev = gen.spec
            }
        }
        val share = wanders.toDouble() / rows
        println("wandering gaps: $wanders in $rows late rows")
        assertTrue(share in 0.02..0.2, "modest share: $share")
    }

    private fun wanderRow(z: Double): GateRow = GateRow().also {
        GateRows.configure(it, z, listOf(GapSpec(5.0, 4.0), GapSpec(7.0, 4.0, wanderFrom = 2), null), -1)
    }

    @Test
    fun wanderingGapSlidesBeforeTheBirdArrives() {
        val speed = 35.0
        val row = wanderRow(-100.0)
        assertEquals(2, row.wanderFrom); assertEquals(1, row.wanderTo)
        assertFalse(row.lanes[2].blocked, "starts open in its origin lane")
        assertTrue(row.lanes[1].blocked)
        assertEquals(5.0, row.lanes[2].gapLow, 1e-12)
        GateRows.wander(row, speed)
        assertEquals(0.0, row.wanderT, "still far away (> ${GateRows.WANDER_START} s)")

        var z = -100.0
        var swappedAt = Double.NaN
        var last = 0.0
        while (z < 0) {
            z += speed / 60
            row.z = z
            GateRows.wander(row, speed)
            GateRows.update(row, 0.0, 0.0)
            assertTrue(row.wanderT >= last); last = row.wanderT
            if (swappedAt.isNaN() && row.lanes[1].blocked.not()) swappedAt = -z / speed
            if (-z / speed <= GateRows.WANDER_END) assertEquals(1.0, row.wanderT, "settled ${GateRows.WANDER_END} s before")
        }
        assertEquals((GateRows.WANDER_START + GateRows.WANDER_END) / 2, swappedAt, 0.05)
        assertTrue(row.lanes[2].blocked, "origin lane is pipe now")
        assertEquals(5.0, row.lanes[1].hitLow, 1e-12)
        assertEquals(9.0, row.lanes[1].hitHigh, 1e-12)
        assertEquals(0.0, row.lanes[2].hitHigh)
        // A slow-down never moves it back.
        row.z = -80.0
        GateRows.wander(row, speed)
        assertEquals(1.0, row.wanderT)
    }

    @Test
    fun reconfiguringClearsTheWander() {
        val row = wanderRow(-50.0)
        GateRows.configure(row, -50.0, listOf(GapSpec(5.0, 4.0), null, GapSpec(6.0, 4.0)), -1)
        assertEquals(-1, row.wanderTo)
        assertTrue(row.lanes[1].blocked); assertFalse(row.lanes[2].blocked)
    }

    @Test
    fun reachBudgetsAWanderingGapAsOneMoreStep() {
        val r = Reach.atScore(60)
        val prev = GapSpec(5.0, 4.0)
        val d = r.maxRise * Tuning.SWITCH_FACTOR * 0.95 // reachable with one step, not with two
        assertTrue(r.fits(prev, Gap(5.0 + d, 4.0), 1))
        assertFalse(r.fits(prev, Gap(5.0 + d, 4.0, wanderFrom = 0), 1))
        // makeReachable pulls a lone wandering target into the stricter budget.
        val spec = arrayOf<Gap?>(null, Gap(12.0, 4.0, wanderFrom = 2), null)
        makeReachable(spec, 2.0, 12.0, listOf(prev, null, null), 60)
        assertTrue(r.fits(prev, spec[1]!!, 1), "pulled to ${spec[1]!!.center}")
    }

    @Test
    fun predictingBotPlansForTheWanderDestination() {
        val state = GameState().apply { lane = 2; y = 7.0; radius = 0.5; speed = 35.0 }
        val row = wanderRow(-60.0)
        val pro = Bot(BotSkill.PRO, rng = Random(1))
        val novice = Bot(BotSkill.NOVICE, rng = Random(1))
        assertTrue(!pro.closed(row, 2) && pro.closed(row, 1), "not moving yet: what you see")
        row.wanderT = 0.1
        assertTrue(pro.closed(row, 2) && !pro.closed(row, 1), "moving: plan for where it goes")
        assertFalse(novice.closed(row, 2), "novices wait for the swap")
        repeat(20) { assertTrue(pro.pickLane(row, state) != 2, "never the lane the gap leaves") }
    }
}
