package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GameEvent
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.Tuning

/**
 * Screenshot/test hooks mirroring `window.__birdy` and scripts/store-shots.mjs (`state.god`,
 * `activatePower`, `enterZone`, `gate.configure`, `advance`, `__pilot`). Not used by the shipped UI.
 */
class SimDebug internal constructor(private val sim: GameSimulation) {
    /** Invincibility (`state.god`). */
    fun setGod(on: Boolean) {
        sim.state.god = on
    }

    fun activatePower(type: PowerType) = sim.activatePower(type)

    /**
     * Enters [zone] as if its banner had been passed (`enterZone`). With [alignRows] the row
     * generator also continues at that zone's first row (its obstacle speciality) and the track
     * ahead is rebuilt from FIRST_GATE_Z.
     */
    fun jumpToZone(zone: Int, alignRows: Boolean = true) {
        sim.enterZone(zone)
        if (!alignRows) return
        val s = sim.state
        sim.emit(GameEvent.SceneryTheme(zone))
        s.gatesSpawned = zone * Tuning.ZONE_ROWS
        s.rushAt = s.gatesSpawned
        s.prevGaps = null
        sim.zoneMarks.clear()
        sim.clearTrack()
        if (s.mode == GameMode.Playing) sim.prespawnRows()
    }

    /**
     * Configures a row directly (`gate.configure(z, spec)` in store-shots.mjs): reuses [row] or
     * the first inactive (else nearest unpassed) gate. Returns the configured row.
     */
    fun scriptedRow(z: Double, spec: List<GapSpec?>, row: GateRow? = null): GateRow {
        val gate = row
            ?: sim.gates.firstOrNull { !it.active }
            ?: sim.gates.filter { !it.passed }.maxBy { it.z }
        gate.active = true
        GateRows.configure(gate, z, spec, sim.rowClouds.next())
        return gate
    }

    /** JS `advance(seconds)`: `update(1/30)` steps (beat from game time), skipped while paused. */
    fun advance(seconds: Double, beforeEachStep: (() -> Unit)? = null) {
        var t = 0.0
        while (t < seconds) {
            beforeEachStep?.invoke()
            if (!sim.state.paused) sim.update(1.0 / 30, null)
            t += 1.0 / 30
        }
    }

    /** Runs [advance] in 1/30 steps with [pilot] until [until] holds or [seconds] pass. */
    fun runUntil(seconds: Double, pilot: Boolean = true, until: () -> Boolean): Boolean {
        var t = 0.0
        while (t < seconds) {
            if (pilot) pilotStep()
            if (!sim.state.paused) sim.update(1.0 / 30, null)
            if (until()) return true
            t += 1.0 / 30
        }
        return false
    }

    /** store-shots.mjs `__pilot`: a crude autopilot that pokes the state (use with god mode). */
    fun pilotStep() {
        val st = sim.state
        if (st.mode != GameMode.Playing || st.hold) return
        val nx = sim.gates.filter { it.active && !it.passed }.maxByOrNull { it.z }
        if (nx == null) {
            if (st.y < 5 && st.vy < 2) st.vy = 11.5
            return
        }
        if (nx.lanes[st.lane].blocked) st.lane = nx.lanes.indexOfFirst { !it.blocked }
        val l = nx.lanes[st.lane]
        val aim = (l.gapLow + l.gapHigh) / 2 + (if (l.hasPlant) 0.6 else 0.0)
        if (st.y < aim - 0.7 && st.vy < 2) {
            st.vy = 11.5
            st.squash = 1.0
        }
    }
}
