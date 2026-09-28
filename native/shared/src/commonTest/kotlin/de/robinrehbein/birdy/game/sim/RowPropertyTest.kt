package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.Tuning
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Invariants of gate generation over thousands of seeded rows (main-a.md §12.3). */
class RowPropertyTest {
    private val eps = 1e-9

    private fun fitsSomewhere(p: GapSpec, spec: List<GapSpec?>, j: Int, score: Int): Boolean {
        val r = Reach.atScore(score)
        return spec.indices.any { i ->
            val g = spec[i] ?: return@any false
            r.reach(p.center, g.center - g.amp, abs(i - j)) && r.reach(p.center, g.center + g.amp, abs(i - j))
        }
    }

    /** Chains rows like a run: score grows, rushes every 10 rows clear prevGaps. */
    private fun forEachRow(seeds: Int, rows: Int, check: (GeneratedRow, prev: List<GapSpec?>?, score: Int, gatesSpawned: Int) -> Unit) {
        for (seed in 0 until seeds) {
            val rng = Random(seed)
            var prev: List<GapSpec?>? = null
            var score = 0
            for (row in 0 until rows) {
                if (row > 0 && row % Tuning.ZONE_ROWS == 0) prev = null // coin rush
                val gen = RowGenerator.gateSpec(score, row, prev, tutorialActive = false, rng = rng)
                check(gen, prev, score, row)
                prev = gen.spec
                // Scores vary between seeds so every difficulty band is covered.
                score += 1 + (seed % 4)
            }
        }
    }

    @Test
    fun neverAllLanesBlocked() {
        var blockedRows = 0
        forEachRow(300, 60) { gen, _, _, _ ->
            assertEquals(3, gen.spec.size)
            assertTrue(gen.spec.any { it != null }, "all lanes blocked")
            if (gen.spec.any { it == null }) blockedRows++
        }
        assertTrue(blockedRows > 1000, "blocking should happen regularly: $blockedRows")
    }

    @Test
    fun easyLaneHasNoObstacle() {
        var obstacles = 0
        forEachRow(300, 60) { gen, _, _, _ ->
            val open = gen.spec.indices.filter { gen.spec[it] != null }
            if (open.size > 1) {
                val g = gen.spec[gen.easy]!!
                assertFalse(g.pulse || g.amp != 0.0 || g.plant, "easy lane ${gen.easy} got an obstacle: $g")
            }
            obstacles += gen.spec.count { it != null && (it.pulse || it.amp != 0.0 || it.plant) }
        }
        assertTrue(obstacles > 1000, "obstacles should appear: $obstacles")
    }

    @Test
    fun gapsStayWithinBounds() {
        forEachRow(300, 60) { gen, _, _, _ ->
            for (g in gen.spec) {
                if (g == null) continue
                assertTrue(g.center - g.amp >= gen.lo - eps && g.center + g.amp <= gen.hi + eps, "gap $g outside [${gen.lo}, ${gen.hi}]")
                assertTrue(g.center - g.size / 2 >= 0 && g.center + g.size / 2 <= Tuning.CEILING, "gap $g outside the world")
            }
        }
    }

    /**
     * makeReachable fixes each previous lane on its own (main-a.md §8.2); a later fix for another
     * previous lane may move the shared target lane away again (JS behaviour, not "fixed" here).
     * Guaranteed: with one open previous lane, and for the last open previous lane (never moved
     * afterwards), a reachable lane exists. Overall misses stay rare.
     */
    @Test
    fun previousOpenLaneAlwaysHasAReachableLane() {
        var checked = 0
        var misses = 0
        forEachRow(300, 60) { gen, prev, score, _ ->
            if (prev == null) return@forEachRow
            val openPrev = prev.indices.filter { prev[it] != null }
            val last = openPrev.last()
            assertTrue(fitsSomewhere(prev[last]!!, gen.spec, last, score), "last prev lane $last unreachable: $prev -> ${gen.spec}")
            for (j in openPrev) {
                checked++
                if (!fitsSomewhere(prev[j]!!, gen.spec, j, score)) {
                    misses++
                    assertTrue(openPrev.size > 1, "single prev lane $j unreachable: $prev -> ${gen.spec}")
                }
            }
        }
        assertTrue(checked > 10000, "checked $checked")
        println("makeReachable: $misses of $checked previous lanes left unreachable by a later fix")
        assertTrue(misses * 50 < checked, "unreachable share too high: $misses / $checked")
    }

    @Test
    fun warmUpRowsAreOpenAndWide() {
        forEachRow(100, Tuning.WARMUP_GATES) { gen, _, _, row ->
            assertTrue(gen.spec.all { it != null })
            assertTrue(gen.spec.all { it!!.size >= 5.2 - 1.4 + 1.8 * (1 - row / 6.0) - eps })
        }
    }

    @Test
    fun tutorialRowsAreScripted() {
        val rng = Random(3)
        for (row in 0..2) {
            val spec = RowGenerator.gateSpec(50, row, null, true, rng).spec
            assertNull(spec[0]); assertNull(spec[2])
            assertEquals(GapSpec(5.2, 6.2), spec[1])
        }
        val sw = RowGenerator.gateSpec(50, 3, null, true, rng).spec
        assertEquals(GapSpec(5.2, 6.2), sw[0]); assertNull(sw[1]); assertEquals(GapSpec(5.2, 6.2), sw[2])
        // Past the switch row the normal generator runs.
        assertEquals(3, RowGenerator.gateSpec(0, 4, null, true, rng).spec.count { it != null })
    }

    @Test
    fun gatesToPowerAlwaysReseedsPositive() {
        for (luck in 0..3) {
            val h = SimHarness(seed = 10 + luck, progress = FakeProgress(levels = mutableMapOf("luck" to luck)))
            h.sim.debug.setGod(true)
            repeat(40) {
                h.sim.resetGame()
                h.sim.flap()
                var last = h.state.gatesToPower
                var reseeds = 0
                h.run(60.0) {
                    val now = h.state.gatesToPower
                    if (now > last) {
                        reseeds++
                        assertTrue(now in (6 - luck)..(9 - luck), "re-seed $now with luck $luck")
                        assertTrue(now > 0)
                    }
                    last = now
                    h.pilot()
                    false
                }
                assertTrue(reseeds > 0, "no power-up spawned in a minute")
                h.state.mode = GameMode.Over
            }
        }
    }

    @Test
    fun firstCountdownUsesResetFormula() {
        val seen = HashSet<Int>()
        for (seed in 0 until 60) {
            val h = SimHarness(seed = seed)
            // Pre-spawned rows already count down: add back one per spawned row.
            h.sim.resetGame()
            seen += h.state.gatesToPower + h.state.gatesSpawned
        }
        // 5 + {0,1,2} - luck(0); a pickup during the pre-spawn re-seeds to {6..9} first.
        assertTrue(seen.containsAll(listOf(6, 7)), "initial countdown values $seen")
        assertTrue(seen.all { it in 5..7 || it in 11..14 }, "initial countdown values $seen")
    }

    @Test
    fun poolExhaustionSilentlyNoOps() {
        val h = SimHarness()
        val sim = h.sim
        sim.resetGame()
        for (g in sim.gates) g.active = true
        val before = sim.state.gatesSpawned
        sim.spawnGate(-500.0)
        assertEquals(before, sim.state.gatesSpawned)
        assertEquals(-500.0, sim.state.lastGateZ, "frontier advances even when the pool is full")

        for (c in sim.coins) c.active = true
        sim.placeCoin(0.0, 5.0, -10.0)
        assertTrue(sim.coins.none { it.z == -10.0 && it.y == 5.0 && it.x == 0.0 })

        for (p in sim.pickups) p.active = true
        assertFalse(sim.placePickup(0.0, 5.0, -10.0))
        for (p in sim.pickups) p.active = p.type != PowerType.Magnet
        // Only magnets are free now: other draws fail silently, a magnet draw succeeds.
        var placed = 0
        repeat(30) { if (sim.placePickup(0.0, 5.0, -10.0)) placed++ }
        assertEquals(2, placed)
        assertNotNull(sim.pickups.firstOrNull { it.type == PowerType.Magnet && it.active })
    }

    @Test
    fun liveRunRowsKeepInvariants() {
        // Rows as spawned by the simulation itself (tutorial off, god mode, crude autopilot).
        for (seed in 0 until 20) {
            val h = SimHarness(seed = seed)
            h.sim.debug.setGod(true)
            h.sim.resetGame()
            h.sim.flap()
            h.run(90.0) {
                for (g in h.sim.gates) if (g.active) assertTrue(g.lanes.any { !it.blocked })
                h.pilot()
                false
            }
            assertTrue(h.state.score > 30, "god run scored ${h.state.score}")
        }
    }
}

/** Crude store-shots autopilot for god-mode runs. */
fun SimHarness.pilot() = sim.debug.pilotStep()
