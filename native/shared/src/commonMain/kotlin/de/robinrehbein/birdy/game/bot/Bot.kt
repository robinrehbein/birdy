package de.robinrehbein.birdy.game.bot

import de.robinrehbein.birdy.game.GameState
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.LaneState
import de.robinrehbein.birdy.game.WorldConst
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Port of bot.js `createBot`: plays with the same one-tap controls as a touch player (every
 * action picks a lane AND flaps). [decide] returns the lane to tap, or null.
 */
class Bot(private val skill: BotSkill, private val hop: Double = BotConst.HOP, private val rng: Random) {
    private var cooldown = 0.0
    private var target: GateRow? = null
    private var lane = 1
    private var aimOffset = 0.0

    private class Pending(val lane: Int, var t: Double)

    // Taps take effect after the player's reaction delay.
    private val queue = ArrayDeque<Pending>()

    fun decide(dt: Double, state: GameState, gates: List<GateRow>): Int? {
        for (a in queue) a.t -= dt
        val due = if (queue.isNotEmpty() && queue.first().t <= 0) queue.removeFirst() else null
        // The player already "pressed" these; don't decide them again.
        val pending = queue.isNotEmpty() || due != null
        val act = if (pending) null else think(dt, state, gates)
        if (act != null) {
            if (skill.delay > 0) queue.addLast(Pending(act, skill.delay)) else return act
        }
        return due?.lane
    }

    /** Lowest cost among open lanes, or a random open lane on a mistake. */
    fun pickLane(gate: GateRow, state: GameState): Int {
        val options = gate.lanes.indices.filter { !gate.lanes[it].blocked }
        if (rng.nextDouble() < skill.mistake) return options[floor(rng.nextDouble() * options.size).toInt()]
        var bestI = options[0]
        var bestCost = Double.POSITIVE_INFINITY
        for (i in options) {
            val cost = laneCost(gate.lanes[i], i, state.lane, state.y)
            if (cost < bestCost) {
                bestCost = cost
                bestI = i
            }
        }
        return bestI
    }

    /** Safe band [lo, hi] for the bird's centre when it reaches the gate. */
    fun band(gate: GateRow, state: GameState): DoubleArray {
        val l = gate.lanes[lane]
        val r = state.radius * 0.8 + 0.15
        var low = l.gapLow
        var high = l.gapHigh
        if (skill.predict) {
            val t = max(0.0, -gate.z / max(1.0, state.speed))
            if (l.pulse) {
                // Plan for the narrowest point of a breathing gap.
                val c = (l.gapLow + l.gapHigh) / 2
                low = max(low, c - l.size * 0.35)
                high = min(high, c + l.size * 0.35)
            }
            if (l.amp != 0.0) {
                val c = l.center + sin((state.time + t) * l.speed + l.phase) * l.amp
                low = c - l.size / 2
                high = c + l.size / 2
            }
            if (l.hasPlant) {
                val beat = (state.time + t) * BotConst.BPM / 60 + l.plantOffset
                // Worst case over the crossing window.
                var rise = 0.0
                for (k in -2..2) rise = max(rise, BotConst.plantRise(beat + k * 0.12))
                if (rise > 0) low = max(low, low + WorldConst.PLANT_REACH - WorldConst.PLANT_HEIGHT * (1 - rise))
            }
        } else if (l.hasPlant) {
            low = max(low, l.hitLow)
        }
        return doubleArrayOf(low + r, high - r)
    }

    private fun think(dt: Double, state: GameState, gates: List<GateRow>): Int? {
        cooldown -= dt
        val ahead = gates
            .filter { it.active && !it.passed && it.z > -skill.lookAhead }
            .sortedByDescending { it.z }
        val next = ahead.getOrNull(0)
        val after = ahead.getOrNull(1)
        if (next !== target) {
            target = next
            if (next != null) {
                lane = pickLane(next, state)
                aimOffset = (rng.nextDouble() - 0.5) * 2 * skill.noise
            }
        }

        var aim = 6.0
        var ceiling = 13.0
        val tgt = target
        if (tgt != null) {
            val (lo, hi) = band(tgt, state).let { it[0] to it[1] }
            var want = (lo + hi) / 2
            // Skilled players already line up for the row after this one.
            if (skill.plan2 && after != null) {
                val open = after.lanes.filter { !it.blocked }
                val l2 = if (after.lanes[lane].blocked) open.firstOrNull() else after.lanes[lane]
                if (l2 != null) want = (l2.gapLow + l2.gapHigh) / 2
            }
            // Keep the flap oscillation (about ±0.9) inside the band.
            aim = aim(lo, hi, want, aimOffset)
            ceiling = hi
        }
        if (cooldown > 0) return null

        val react = max(skill.delay * skill.anticipate, 0.05)
        val yPred = predictY(state.y, state.vy, react)
        val apex = flapApex(state.y)
        val wantsSwitch = lane != state.lane
        val close = tgt != null && tgt.z > -14
        // Would a flap now carry the bird into the upper pipe?
        val apexSafe = apex < ceiling + 0.12 || rng.nextDouble() > skill.apexCheck
        val needFlap = yPred < aim - 0.9 && apexSafe

        if (wantsSwitch) {
            // Switching lanes costs only a small hop.
            val hopApex = hopApex(state.y, state.vy, hop)
            val hopSafe = hopApex < ceiling + 0.12 || rng.nextDouble() > skill.apexCheck
            if (!skill.lateSwitch || close || needFlap || hopSafe || state.vy < 0) {
                cooldown = skill.interval
                return lane
            }
            return null
        }
        if (needFlap) {
            cooldown = skill.interval
            return lane
        }
        return null
    }

    companion object {
        /** Height after [react] seconds without a tap. */
        fun predictY(y: Double, vy: Double, react: Double): Double = y + vy * react - 0.5 * BotConst.GRAVITY * react * react

        /** "If I flapped right now": uses the fixed flap impulse, not the current vy. */
        fun flapApex(y: Double): Double = y + (BotConst.FLAP * BotConst.FLAP) / (2 * BotConst.GRAVITY)

        /** Apex of a lane-switch hop (`v = max(vy, hop)`). */
        fun hopApex(y: Double, vy: Double, hop: Double = BotConst.HOP): Double {
            val v = max(vy, hop)
            return y + (if (v > 0) (v * v) / (2 * BotConst.GRAVITY) else 0.0)
        }

        /** The aim clamp that keeps the flap oscillation inside [lo, hi]. */
        fun aim(lo: Double, hi: Double, want: Double, aimOffset: Double): Double {
            val a = min(lo + 1, (lo + hi) / 2)
            val b = max(hi - 1, (lo + hi) / 2)
            return max(lo, min(hi, max(a, min(b, want)) + aimOffset))
        }

        /** pickLane's cost for lane [i] seen from [birdLane] at height [y] (lower is better). */
        fun laneCost(l: LaneState, i: Int, birdLane: Int, y: Double): Double =
            abs(i - birdLane) * 1.2 +
                abs((l.gapLow + l.gapHigh) / 2 - y) * 0.25 +
                (if (l.hasPlant) 2.5 else 0.0) +
                (if (l.amp != 0.0) 1.5 else 0.0) -
                (l.gapHigh - l.gapLow) * 0.3
    }
}
