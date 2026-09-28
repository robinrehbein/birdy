package de.robinrehbein.birdy.game.bot

import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.game.LastRun
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.jsName
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

/**
 * Headless playtest (main.js `simulate()`): [runs] games with a bot at a fixed 1/60 step.
 * A run ends at death or [maxTime] seconds (cause null = timeout). Like JS, runs are not
 * finished through `progress.finishRun` (the loop sets mode = over directly).
 */
fun simulate(
    sim: GameSimulation,
    runs: Int = 50,
    skill: BotSkill = BotSkill.GOOD,
    maxTime: Double = 240.0,
    botRng: Random = sim.rng,
): List<LastRun> {
    val results = ArrayList<LastRun>(runs)
    val dt = 1.0 / 60
    val s = sim.state
    repeat(runs) {
        val bot = Bot(skill, hop = Tuning.SWITCH_HOP, rng = botRng)
        s.lastRun = null
        sim.resetGame()
        sim.flap()
        while (s.mode == GameMode.Playing && s.runTime < maxTime) {
            val act = bot.decide(dt, s, sim.gates)
            if (act != null) sim.tap(act)
            sim.step(dt, null)
        }
        results += s.lastRun ?: LastRun(s.score, s.coins, s.runTime, null, s.zone)
        s.mode = GameMode.Over
    }
    return results
}

/** The per-skill summary printed by scripts/playtest.mjs. */
data class PlaytestReport(
    val runs: Int,
    val timeMedian: Double,
    val timeP10: Double,
    val timeP90: Double,
    val scoreMedian: Int,
    val scoreP10: Int,
    val scoreP90: Int,
    val scoreMax: Int,
    val coinsMedian: Int,
    /** Share (0..1) of runs reaching zone index 1, 2, 3. */
    val zonesReached: List<Double>,
    val causes: Map<String, Int>,
) {
    companion object {
        fun of(results: List<LastRun>): PlaytestReport {
            fun <T : Comparable<T>> median(a: List<T>) = a.sorted()[a.size / 2]
            fun <T : Comparable<T>> pct(a: List<T>, p: Double) = a.sorted()[min(a.size - 1, floor(a.size * p).toInt())]
            val times = results.map { it.time }
            val scores = results.map { it.score }
            return PlaytestReport(
                runs = results.size,
                timeMedian = median(times),
                timeP10 = pct(times, 0.1),
                timeP90 = pct(times, 0.9),
                scoreMedian = median(scores),
                scoreP10 = pct(scores, 0.1),
                scoreP90 = pct(scores, 0.9),
                scoreMax = scores.max(),
                coinsMedian = median(results.map { it.coins }),
                zonesReached = (1..3).map { z -> results.count { it.zone >= z }.toDouble() / results.size },
                causes = results.groupingBy { it.cause?.jsName ?: "timeout" }.eachCount(),
            )
        }
    }
}
