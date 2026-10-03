package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GameEvent
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.WorldConst
import de.robinrehbein.birdy.game.ZoneMark
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Rows, coins, pickups and coin rushes (main.js:957-1035, main-a.md §8.3). */

/** First inactive pooled coin; pool exhaustion silently drops the coin. */
internal fun GameSimulation.placeCoin(x: Double, y: Double, z: Double) {
    val c = coins.firstOrNull { !it.active } ?: return
    c.active = true
    c.visible = true
    c.x = x
    c.y = y
    c.z = z
}

/** Random type, then the first inactive pickup of that type (2 each); false if none is free. */
internal fun GameSimulation.placePickup(x: Double, y: Double, z: Double): Boolean {
    val types = PowerType.entries
    val type = types[floor(rng.nextDouble() * types.size).toInt()]
    val p = pickups.firstOrNull { !it.active && it.type == type } ?: return false
    p.active = true
    p.visible = true
    p.x = x
    p.y = y
    p.z = z
    return true
}

private val RUSH_PATTERN = intArrayOf(1, 1, 0, 0, 1, 2, 2, 1, 1)

/** Zone change: a pipe-free stretch with a wave of coins across the lanes. */
internal fun GameSimulation.spawnRush(z: Double) {
    val gap = spacing()
    RUSH_PATTERN.forEachIndexed { k, lane ->
        val t = k.toDouble() / (RUSH_PATTERN.size - 1)
        placeCoin(WorldConst.LANES[lane], 5 + sin(t * PI * 2) * 1.6, z + gap * 0.45 - t * gap * 0.9)
    }
    val zone = state.zone + zoneMarks.size + 1
    zoneMarks.add(ZoneMark(z + gap * 0.45, zone))
    // Switch the scenery now: the new place starts right where the banner appears.
    emit(GameEvent.SceneryTheme(zone))
    state.prevGaps = null // plenty of time after the rush: no reach limit
}

/** Generates the next row's spec from the current state (`gateSpec()`). */
internal fun GameSimulation.gateSpec(): List<GapSpec?> =
    RowGenerator.gateSpec(state.score, state.gatesSpawned, state.prevGaps, state.tutorialActive, rng).spec

/**
 * The row with index [best] is the one that, once passed, scores best + 1 (a new record), so it
 * gets the golden marker; only for records worth chasing (best >= 5, like the record toast).
 */
internal fun isRecordRow(rowIndex: Int, best: Int): Boolean = best >= 5 && rowIndex == best

/** `spawnGate(z)`: a rush every [Tuning.ZONE_ROWS] rows, else a pooled gate row with extras. */
internal fun GameSimulation.spawnGate(z: Double) {
    val s = state
    s.lastGateZ = z // always advance, even if the pool is exhausted
    if (s.gatesSpawned > 0 && s.gatesSpawned % Tuning.ZONE_ROWS == 0 && s.rushAt != s.gatesSpawned) {
        s.rushAt = s.gatesSpawned
        spawnRush(z)
        return
    }
    val gate = gates.firstOrNull { !it.active } ?: return
    val spec = gateSpec()
    gate.active = true
    GateRows.configure(gate, z, spec, rowClouds.next())
    gate.record = isRecordRow(s.gatesSpawned, progress.data.value.best)
    if (s.tutorialActive && s.gatesSpawned == Tuning.TUT_SWITCH_ROW) tutorialGate = gate
    s.gatesSpawned++

    // Coins inside some (static) gaps; a wandering gap would leave its coin floating in pipe.
    spec.forEachIndexed { i, g ->
        if (g != null && g.amp == 0.0 && g.wanderFrom < 0 && rng.nextDouble() < 0.3) placeCoin(WorldConst.LANES[i], g.center, z)
    }

    val prev = s.prevGaps
    val calm = (0 until 3).filter { i ->
        prev != null && prev[i] != null && spec[i] != null && prev[i]!!.amp == 0.0 && spec[i]!!.amp == 0.0 &&
            spec[i]!!.wanderFrom < 0
    }

    // Now and then a power-up floats between two rows.
    var pickupPlaced = false
    if (--s.gatesToPower <= 0 && calm.isNotEmpty()) {
        val i = calm[floor(rng.nextDouble() * calm.size).toInt()]
        pickupPlaced = placePickup(WorldConst.LANES[i], (prev!![i]!!.center + spec[i]!!.center) / 2, z + spacing() / 2)
        s.gatesToPower = 6 + floor(rng.nextDouble() * 4).toInt() - progress.level("luck")
    }

    // A guiding trail of coins leading from the previous row into this one.
    if (!pickupPlaced && calm.isNotEmpty() && rng.nextDouble() < 0.55) {
        val i = calm[floor(rng.nextDouble() * calm.size).toInt()]
        val gap = spacing()
        for (k in 1..4) {
            val t = k / 5.0
            placeCoin(WorldConst.LANES[i], SimMath.lerp(prev!![i]!!.center, spec[i]!!.center, t), z + gap * (1 - t))
        }
    }

    s.prevGaps = spec
    s.lastGateZ = z
}

/** Keeps rows spawned out to [Tuning.SPAWN_DISTANCE] ahead. */
internal fun GameSimulation.spawnAhead() {
    while (state.lastGateZ > -Tuning.SPAWN_DISTANCE) spawnGate(state.lastGateZ - spacing())
}

/** resetGame's pre-spawn: the first row at FIRST_GATE_Z, then out to SPAWN_DISTANCE. */
internal fun GameSimulation.prespawnRows() {
    state.lastGateZ = Tuning.FIRST_GATE_Z + spacing()
    spawnAhead()
}
