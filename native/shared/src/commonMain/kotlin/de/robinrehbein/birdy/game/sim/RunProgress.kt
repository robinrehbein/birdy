package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GameEvent
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.meta.RunStats
import kotlin.math.max

internal inline fun GameSimulation.addToRun(change: (RunStats) -> RunStats) {
    state.run = change(state.run)
}

/** `checkMissions()`: celebrate daily missions / achievements the moment they are reached. */
internal fun GameSimulation.checkMissions() {
    val s = state
    val run = s.run.copy(coins = s.coins, score = s.score)
    for (id in progress.wouldComplete(run)) {
        if (!s.celebrated.add(id)) continue
        emit(GameEvent.MissionPreview(listOf(id)))
    }
    for (a in progress.wouldUnlock(run)) {
        if (!s.celebrated.add("a:${a.id}")) continue
        emit(GameEvent.AchievementPreview(listOf(a)))
        emit(GameEvent.Buzz(25))
    }
}

/** `addScore(gate)`: a row was passed. */
internal fun GameSimulation.addScore(gate: GateRow) {
    val s = state
    s.score++
    val best = progress.data.value.best
    if (s.score == best + 1 && best >= 5) {
        // Beat the record mid-run: celebrate right away, with a small coin bonus.
        s.coins += Tuning.RECORD_BONUS
        emit(GameEvent.Toast("recordBonus", mapOf("n" to Tuning.RECORD_BONUS)))
        emit(GameEvent.Fanfare())
        emit(GameEvent.Buzz(30))
    }
    val lane = gate.lanes[s.lane]
    val star = s.power(PowerType.Star) > 0
    addToRun {
        it.copy(
            plants = it.plants + if (lane.hasPlant) 1 else 0,
            moving = it.moving + if (lane.amp != 0.0) 1 else 0,
            starRows = it.starRows + if (star) 1 else 0,
        )
    }
    checkMissions()
    emit(GameEvent.Point(s.score))
}

/** `nearMiss()`: a close call is worth a coin; the chain grows with each consecutive one. */
internal fun GameSimulation.nearMiss() {
    val s = state
    s.nearChain++
    val chain = s.nearChain
    addToRun { it.copy(near = it.near + 1, bestChain = max(it.bestChain, chain)) }
    s.coins++
    emit(GameEvent.NearMiss(chain))
    emit(GameEvent.Buzz(15))
    checkMissions()
}

/** `updatePowers(dt)`: count down, power-down events, grace after the rainbow. */
internal fun GameSimulation.updatePowers(dt: Double) {
    val s = state
    for (type in PowerType.entries) {
        val left = s.power[type.ordinal]
        if (left <= 0) continue
        s.power[type.ordinal] = max(0.0, left - dt)
        if (s.power[type.ordinal] == 0.0) {
            emit(GameEvent.PowerDown(type))
            // Time to get clear of any pipe.
            if (type == PowerType.Star) s.grace = de.robinrehbein.birdy.game.Tuning.GRACE_TIME
        }
    }
    s.grace = max(0.0, s.grace - dt)
}

internal val GameSimulation.isPlaying: Boolean get() = state.mode == GameMode.Playing
