package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.WorldConst
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The state part of main.js `updateBirdVisual(dt)` (bird-fx.md §2): pitch/roll, wing speed and
 * phase, mini scale easing, squash & stretch and the grace blink, written to [GameState.pose].
 */
fun GameSimulation.updateBirdVisual(dt: Double) {
    val s = state
    val pose = s.pose
    if (s.mode == GameMode.Playing || s.mode == GameMode.Ready) {
        val pitch = SimMath.clamp(s.vy * 0.06, -0.9, 0.5)
        pose.rotX = SimMath.lerp(pose.rotX, pitch, min(1.0, dt * 10))
        val roll = (WorldConst.LANES[s.lane] - s.x) * -0.25
        pose.rotZ = SimMath.lerp(pose.rotZ, roll, min(1.0, dt * 10))
    } else {
        pose.rotX = SimMath.lerp(pose.rotX, -1.2, min(1.0, dt * 5))
    }
    if (s.mode != GameMode.Over) {
        s.wingSpeed = SimMath.lerp(s.wingSpeed, Tuning.WING_IDLE, dt * 4)
        s.wingPhase += dt * s.wingSpeed
    }
    val scale = if (s.power(PowerType.Mini) > 0) Tuning.MINI_SCALE else Tuning.BIRD_SCALE
    pose.baseScale = SimMath.lerp(pose.baseScale, scale, min(1.0, dt * 8))
    // Squash & stretch: stretched tall right after a flap, springing back.
    s.squash = max(0.0, s.squash - dt * 6)
    val q = squashAmount(s.squash)
    pose.scaleX = pose.baseScale * (1 - 0.14 * q)
    pose.scaleY = pose.baseScale * (1 + 0.24 * q)
    pose.scaleZ = pose.baseScale * (1 - 0.1 * q)
    // Blink during the grace period after the rainbow ends.
    pose.visible = !(s.grace > 0 && floor(s.time * 12).toLong() % 2 == 0L)
}

/** `q = sin(squash * PI) * 0.5 + squash * 0.2`. */
fun squashAmount(squash: Double): Double = sin(squash * PI) * 0.5 + squash * 0.2

/** Camera-shake decay (main.js `updateCamera`); the loop applies the jitter. */
fun GameSimulation.tickShake(dt: Double) {
    if (state.shake > 0) state.shake = max(0.0, state.shake - dt)
}
