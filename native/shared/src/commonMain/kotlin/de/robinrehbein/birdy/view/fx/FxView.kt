package de.robinrehbein.birdy.view.fx

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.game.GameEvent
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.TutorialStep
import de.robinrehbein.birdy.game.hitOffset
import de.robinrehbein.birdy.meta.TrailItem
import de.robinrehbein.birdy.view.FrameInfo
import de.robinrehbein.birdy.view.SceneView
import de.robinrehbein.birdy.view.bird.BirdView
import kotlin.random.Random

/**
 * Effects around the bird: particles (bursts + flight trails), speed lines, the crash bonk, the
 * power-up aura, the six power-up pickups, the blob shadow and the height marker
 * (effects.js, powerups.js, main.js `updateBirdVisual`/`updateMarker`/`update`).
 *
 * Wiring per frame, in this order:
 *  1. after `sim.update(...)`, pass every [GameEvent] of the frame to [onEvent] (bursts, bonk,
 *     clears); JS fires these inside `step()`, before the particle update;
 *  2. [update] with the frame's [FrameInfo] (also during pause and hit-stop: it detects both).
 *
 * [bird] supplies the worn skin (feather colours) and the equipped trail. Shop previews set
 * [previewTrail]; it is shown while the menu is the shop (JS `previewTrail`, falls back to the
 * equipped trail when null). Menu/shop celebrations call [burst] with a [Bursts] preset.
 */
class FxView(private val bird: BirdView, random: Random = Random.Default) : SceneView {
    val particles = Particles(random)
    val speedLines = SpeedLines(random)
    val impact = Impact()
    val aura = Aura()
    val blob = BlobShadow()
    val marker = HeightMarker()
    private val pickupVisuals = HashMap<de.robinrehbein.birdy.game.Pickup, PickupVisual>()
    private val rim = PickupVisual.sharedRim()
    private var scene: Scene? = null

    /** Trail shown on the hovering bird in the shop; null = the equipped one. */
    var previewTrail: TrailItem? = null

    private var trailAcc = 0.0
    private var lastHitStop = 0.0
    private val camQuat = Quat()
    private val tmpColor = Color()

    override fun attach(scene: Scene) {
        this.scene = scene
        scene.add(blob.mesh, marker.mesh, particles.mesh, speedLines.mesh, impact.mesh, aura.mesh)
    }

    /** Fires a burst at the bird's current position (menu/shop celebrations). */
    fun burst(sim: GameSimulation, options: EmitOptions) {
        val s = sim.state
        particles.emit(s.x, s.y, 0.0, options)
    }

    /** Reacts to a simulation event (bursts, bonk, clearing on reset/menu). */
    fun onEvent(event: GameEvent, sim: GameSimulation) {
        val s = sim.state
        when (event) {
            is GameEvent.CoinCollected -> particles.emit(event.x, event.y, event.z, Bursts.coin)
            is GameEvent.PowerUp -> particles.emit(s.x, s.y, 0.0, Bursts.powerUp(event.type))
            is GameEvent.NearMiss -> particles.emit(s.x, s.y, 0.0, Bursts.nearMiss)
            is GameEvent.Died -> {
                marker.hide()
                particles.emit(s.x, s.y, 0.0, Bursts.death(bird.skin))
                val at = event.cause.hitOffset
                impact.hit(s.x + at[0], s.y + at[1], at[2])
            }
            is GameEvent.RunStarted -> {
                particles.clear()
                impact.clear()
                trailAcc = 0.0
            }
            is GameEvent.WentToMenu -> marker.hide()
            else -> Unit
        }
    }

    override fun update(frame: FrameInfo) {
        val sim = frame.sim
        val s = sim.state
        frame.camera.getQuaternion(camQuat)
        syncPickupNodes(sim)
        // JS skips the whole update while paused; during hit-stop only the bonk animates.
        val frozen = lastHitStop > 0
        lastHitStop = s.hitStop
        if (s.paused) return
        val dt = frame.dt
        if (frozen) {
            impact.update(dt, camQuat)
            return
        }
        val playing = s.mode == GameMode.Playing
        val star = s.power(PowerType.Star) > 0

        // Parts of updatePlaying (skipped while hovering and while the tutorial holds the bird).
        if (!playing) {
            marker.hide()
        } else if (!s.hold && s.tutorialStep != TutorialStep.Switch) {
            marker.update(sim.nextGate, true, star, s.lane, s.y, s.radius, s.tutorialActive, s.time, camQuat)
            for ((pu, v) in pickupVisuals) if (pu.active) v.animate(s.time, camQuat)
            if (star) {
                tmpColor.setHSL(BirdView.starHue(s.time), 1.0, 0.6)
                particles.emitColor(s.x, s.y, 0.0, tmpColor.getHex(), Bursts.starTrail)
            }
        }

        val dz = if (playing) s.speed * dt else 0.0
        particles.update(dt, dz)
        speedLines.update(dz, SpeedLines.rush(playing, s.hold, s.speed, star))
        impact.update(dt, camQuat)

        // updateBirdVisual: blob, flight trail, aura.
        blob.update(s.x, s.y, s.pose.scaleX)
        val trail = when {
            playing && !s.hold && !star -> bird.trail
            s.mode == GameMode.Ready && s.menu == Menu.Shop -> previewTrail ?: bird.trail
            else -> null
        }
        if (trail != null && trail.colors.isNotEmpty()) {
            trailAcc += dt * 50
            val drift = if (playing) 0.0 else 6.0 // the world stands still in the shop
            val options = Bursts.trail(trail, drift)
            while (trailAcc >= 1) {
                trailAcc -= 1
                particles.emit(s.x, s.y - 0.1, 0.45, options)
            }
        }
        val p = s.power
        val kind = Aura.kindFor(playing, p[PowerType.Star.ordinal], p[PowerType.Magnet.ordinal], p[PowerType.Mini.ordinal])
        aura.update(kind, s.x, s.y, 0.0, s.time, s.pose.baseScale, camQuat)
    }

    /** Mirrors the simulation's pickup pool (position, forward shrink, visibility). */
    private fun syncPickupNodes(sim: GameSimulation) {
        for (pu in sim.pickups) {
            val v = pickupVisuals.getOrPut(pu) { PickupVisual(pu.type, rim).also { scene?.add(it.group) } }
            v.group.visible = pu.visible
            v.group.position.set(pu.x.toFloat(), pu.y.toFloat(), pu.z.toFloat())
            v.group.scale.setScalar(pu.scale.toFloat())
        }
    }
}
