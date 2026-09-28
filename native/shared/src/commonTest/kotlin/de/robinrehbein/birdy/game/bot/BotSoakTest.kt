package de.robinrehbein.birdy.game.bot

import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.game.sim.FakeProgress
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Seed-fixed bot playtest (like scripts/playtest.mjs, 150 s limit). JS reference (docs/PROGRESS.md,
 * 100 runs): novice median 14.5 s / 7 points, good 40.2 s / 23 points, pro 88 % reach the limit
 * with a median of 117 points.
 */
class BotSoakTest {
    private fun play(skill: BotSkill, runs: Int = 30, seed: Int = 2024): PlaytestReport {
        val sim = GameSimulation(FakeProgress(), Random(seed)) { }
        val results = simulate(sim, runs, skill, maxTime = 150.0)
        assertEquals(runs, results.size)
        assertEquals(GameMode.Over, sim.state.mode)
        return PlaytestReport.of(results).also { println("playtest ${skill.name}: $it") }
    }

    @Test
    fun novice() {
        val r = play(BotSkill.NOVICE)
        assertTrue(r.timeMedian in 5.0..40.0, "novice median ${r.timeMedian}")
        assertTrue(r.scoreMedian in 2..25, "novice score ${r.scoreMedian}")
    }

    @Test
    fun good() {
        val r = play(BotSkill.GOOD)
        assertTrue(r.timeMedian in 15.0..100.0, "good median ${r.timeMedian}")
        assertTrue(r.zonesReached[0] >= 0.6, "good zone 2 share ${r.zonesReached}")
    }

    @Test
    fun pro() {
        val r = play(BotSkill.PRO)
        val survived = (r.causes["timeout"] ?: 0).toDouble() / r.runs
        assertTrue(survived >= 0.6, "pro survival $survived, causes ${r.causes}")
        assertTrue(r.scoreMedian >= 80, "pro score ${r.scoreMedian}")
    }

    @Test
    fun deterministicForASeed() {
        val a = simulate(GameSimulation(FakeProgress(), Random(9)) { }, 5, BotSkill.GOOD, 60.0)
        val b = simulate(GameSimulation(FakeProgress(), Random(9)) { }, 5, BotSkill.GOOD, 60.0)
        assertEquals(a, b)
    }
}
