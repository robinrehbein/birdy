package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.DeathCause
import de.robinrehbein.birdy.game.GameEvent
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.WorldConst
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** `updatePlaying(dt)` (main.js:1616-1794): physics, rows, zones, coins, pickups, ground. */
internal fun GameSimulation.updatePlaying(dt: Double) {
    val s = state
    if (s.hold) {
        // Get ready: hover in place, ground and scenery keep scrolling.
        moveWorld(8 * dt)
        s.y = s.holdY + sin(s.time * 3) * 0.35
        s.vy = cos(s.time * 3) * 1.05
        return
    }
    if (updateTutorial()) return
    s.runTime += dt
    val boost = if (s.power(PowerType.Star) > 0) Tuning.STAR_SPEED_BOOST else 1.0
    s.speed = SimMath.lerp(s.speed, baseSpeed() * boost, dt * (if (boost > 1) 3.0 else 0.8))
    val dz = s.speed * dt
    moveWorld(dz)
    updatePowers(dt)

    val targetRadius = if (s.power(PowerType.Mini) > 0) Tuning.MINI_RADIUS else Tuning.BIRD_RADIUS
    s.radius = SimMath.lerp(s.radius, targetRadius, min(1.0, dt * 8))
    val r = s.radius

    // Bird physics.
    s.vy = max(Tuning.MAX_FALL, s.vy - Tuning.GRAVITY * dt)
    s.y += s.vy * dt
    if (s.y > Tuning.CEILING) {
        s.y = Tuning.CEILING
        s.vy = min(s.vy, 0.0)
    }
    val targetX = WorldConst.LANES[s.lane]
    s.x += (targetX - s.x) * min(1.0, dt * Tuning.LANE_SWITCH_SPEED)

    updateGates(dt, dz, r)

    s.lastGateZ += dz
    spawnAhead()

    for (i in zoneMarks.indices.reversed()) {
        val m = zoneMarks[i]
        val z = m.z + dz
        if (z > 0) {
            zoneMarks.removeAt(i)
            enterZone(m.zone)
        } else {
            zoneMarks[i] = m.copy(z = z)
        }
    }

    updateCoins(dt, dz, r)
    updatePickups(dz)

    if (s.y - r < 0) {
        s.y = r
        if (s.invincible) {
            s.vy = Tuning.FLAP_VELOCITY * 0.9
            emit(GameEvent.Bounce)
        } else {
            die(DeathCause.Ground)
        }
    }
}

/** Gates: move, animate, score, fade, collide, recycle. */
private fun GameSimulation.updateGates(dt: Double, dz: Double, r: Double) {
    val s = state
    var next: GateRow? = null
    for (gate in gates) {
        if (!gate.active) continue
        gate.z += dz
        val gz = gate.z
        if (gz > 20) {
            gate.active = false
            gate.visible = false
            continue
        }
        GateRows.wander(gate, s.speed)
        GateRows.update(gate, s.time, s.beat)
        if (!gate.passed && gz > WorldConst.PIPE_RADIUS + r) {
            gate.passed = true
            addScore(gate)
            val clear = gate.minClear
            if (s.mode == GameMode.Playing && !s.invincible && clear != null && clear < Tuning.NEAR_MISS) nearMiss()
            else s.nearChain = 0
            gate.minClear = null
        }
        // Passed rows vanish within a short distance behind the bird (looks only).
        val behind = gz - (WorldConst.PIPE_RADIUS + r)
        GateRows.setOpacity(gate, if (gate.passed) SimMath.clamp(1 - behind / 1.5, 0.0, 1.0) else 1.0)
        if (gate.passed && gate.opacity <= 0) gate.visible = false
        if (!gate.passed && (next == null || gz > next.z)) next = gate

        if (!s.invincible && abs(gz) < WorldConst.PIPE_RADIUS + 0.25 + r) {
            for (lane in gate.lanes) {
                if (hypot(s.x - lane.x, gz) > WorldConst.PIPE_RADIUS + 0.15 + r) continue
                if (!lane.blocked) {
                    // Track the tightest clearance while inside the pipe (for "Knapp!").
                    val clear = min(s.y - r * 0.8 - lane.hitLow, lane.hitHigh - (s.y + r * 0.8))
                    gate.minClear = min(gate.minClear ?: Double.POSITIVE_INFINITY, clear)
                }
                if (lane.blocked) die(DeathCause.Blocked)
                else if (s.y + r * 0.8 >= lane.hitHigh) die(DeathCause.PipeTop)
                else if (s.y - r * 0.8 <= lane.hitLow) die(if (lane.hitLow > lane.gapLow + 0.01) DeathCause.Plant else DeathCause.PipeBottom)
            }
        }
    }
    nextGate = if (s.mode == GameMode.Playing) next else null
}

private fun dist(ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double): Double {
    val dx = ax - bx
    val dy = ay - by
    val dz = az - bz
    return sqrt(dx * dx + dy * dy + dz * dz)
}

/** Forward shrink just behind the bird (looks only; items stay collectable). */
internal fun shrinkBehind(z: Double): Double = SimMath.clamp(1 - (z - 1.5) / 1.5, 0.0, 1.0)

/** Coins, pulled in by the magnet. */
private fun GameSimulation.updateCoins(dt: Double, dz: Double, r: Double) {
    val s = state
    val magnet = s.power(PowerType.Magnet) > 0
    val bx = s.x
    val by = s.y
    for (c in coins) {
        if (!c.active) continue
        c.z += dz
        c.spin += dt * 4
        if (magnet && dist(c.x, c.y, c.z, bx, by, 0.0) < magnetRange() && c.z > -magnetRange()) {
            val a = min(1.0, dt * 7)
            c.x = SimMath.vlerp(c.x, bx, a)
            c.y = SimMath.vlerp(c.y, by, a)
            c.z = SimMath.vlerp(c.z, 0.0, a)
        }
        val k = shrinkBehind(c.z)
        c.scale = k
        c.visible = k > 0
        if (c.z > 15) {
            c.active = false
            c.visible = false
        } else if (dist(c.x, c.y, c.z, bx, by, 0.0) < Tuning.COIN_RADIUS + (r - Tuning.BIRD_RADIUS)) {
            c.active = false
            c.visible = false
            s.coins++
            checkMissions()
            emit(GameEvent.CoinCollected(s.coins, c.x, c.y, c.z))
        }
    }
}

/** Power-up pickups (collection radius 1.4). */
private fun GameSimulation.updatePickups(dz: Double) {
    val s = state
    for (pu in pickups) {
        if (!pu.active) continue
        pu.z += dz
        val k = shrinkBehind(pu.z)
        pu.scale = k
        pu.visible = k > 0
        if (pu.z > 15) {
            pu.active = false
            pu.visible = false
        } else if (dist(pu.x, pu.y, pu.z, s.x, s.y, 0.0) < Tuning.PICKUP_RADIUS) {
            pu.active = false
            pu.visible = false
            activatePower(pu.type)
        }
    }
}

/** `updateDead(dt)`: the bird keeps falling (spinning) behind the game-over panel. */
internal fun GameSimulation.updateDead(dt: Double) {
    val s = state
    s.deadTimer += dt
    if (s.y > s.radius) {
        s.vy = max(Tuning.MAX_FALL, s.vy - Tuning.GRAVITY * dt)
        s.y = max(s.radius, s.y + s.vy * dt)
        s.pose.rotZ += dt * 6
    }
    // Game over comes quickly; the fall keeps playing behind the panel. Near the record the
    // revive offer comes first and counts down to the game over unless accepted.
    val offer = s.revive
    if (s.mode == GameMode.Dead && offer != null) {
        if (!offer.pending) {
            offer.timeLeft -= dt
            if (offer.timeLeft <= 0) declineRevive()
        }
    } else if (s.mode == GameMode.Dead && s.deadTimer > Tuning.DEAD_TO_OVER && !offerRevive()) {
        showGameOver()
    }
    for (gate in gates) if (gate.active) GateRows.update(gate, s.time, s.beat)
}
