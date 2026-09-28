package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameState
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.game.PowerType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Free band between menu title and panel (main.js `measureMenuFrame`), fractions of height. */
data class MenuFrameInfo(val center: Double, val far: Double) {
    companion object {
        /** JS fallback when nothing is measured. */
        val DEFAULT = MenuFrameInfo(0.4, 1.0)

        /** `free = max(0.12, (bottom - top))`, `far = clamp(0.4 / free, 1, 2.4)`. */
        fun fromLayout(titleBottom: Double, panelTop: Double): MenuFrameInfo {
            val free = max(0.12, panelTop - titleBottom)
            return MenuFrameInfo((titleBottom + panelTop) / 2, (0.4 / free).coerceIn(1.0, 2.4))
        }
    }
}

/**
 * main.js `updateCamera` / `frameBird` / `resize`: chase cam in runs, side view in the shop,
 * behind-the-bird view in the other menus, hit shake, the speed-kick FOV and the asymmetric
 * menu framing through [PerspectiveCamera.setViewOffset]. Game thread only.
 */
class CameraRig(val camera: PerspectiveCamera, private val random: Random = Random.Default) {
    var baseFov = 68.0
        private set
    /** Surface size in pixels (the view offset is expressed in these units). */
    var width = 1f
        private set
    var height = 1f
        private set
    /** Current vertical view shift in pixels (JS `viewShift`). */
    var viewShift = 0.0
        private set

    private val target = Vec3()
    private val look = Vec3()
    val lookCur = Vec3(0f, 0.5f, -9.5f)
    private val tmp = Vec3()

    init {
        camera.position.set(0f, 6.2f, 6.5f)
    }

    /** `resize()`: aspect and the base FOV (narrow screens see more). */
    fun resize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1).toFloat()
        this.height = height.coerceAtLeast(1).toFloat()
        camera.aspect = this.width / this.height
        baseFov = baseFovFor(camera.aspect.toDouble())
        camera.fov = baseFov.toFloat()
        applyView()
    }

    /** One frame of `updateCamera(dt)`; [menuFrame] is non-null in mode Ready. */
    fun update(dt: Double, s: GameState, menuFrame: MenuFrameInfo?) {
        val y = s.y
        if (s.mode == GameMode.Ready && s.menu == Menu.Shop) {
            // Shop: side view of the bird, pulled back when little space is free.
            val far = (menuFrame ?: MenuFrameInfo.DEFAULT).far * (if (baseFov < 70) 1.15 else 1.0)
            target.set((5.6 * far).toFloat(), (y + 0.9).toFloat(), (-2.1 * far).toFloat())
            look.set(0f, (y - 0.3).toFloat(), 0f)
        } else if (s.mode == GameMode.Ready) {
            val far = (menuFrame ?: MenuFrameInfo.DEFAULT).far
            target.set(0f, (y + 1.2 * far).toFloat(), (6.5 * far).toFloat())
            look.set(0f, (y - 0.6).toFloat(), -9.5f)
        } else {
            // Above and behind the bird; no sideways panning so lanes keep their screen place.
            target.set(0f, (7.5 + y * 0.6).toFloat(), 14f)
            look.set(0f, (1.8 + y * 0.6).toFloat(), -22f)
        }
        val k = min(1.0, dt * (if (s.mode == GameMode.Ready) 3.5 else 6.0)).toFloat()
        camera.position.lerp(target, k)
        lookCur.lerp(look, k)
        // The simulation decays state.shake (tickShake); the jitter is applied here.
        if (s.shake > 0) {
            val m = s.shake * 0.8
            camera.position.x += ((random.nextDouble() - 0.5) * m).toFloat()
            camera.position.y += ((random.nextDouble() - 0.5) * m).toFloat()
        }
        camera.lookAt(lookCur)
        frameBird(menuFrame, s)
        val fov = targetFov(baseFov, s.mode == GameMode.Playing, s.speed, s.power(PowerType.Star) > 0)
        if (abs(camera.fov - fov) > 0.05) {
            camera.fov = (camera.fov + (fov - camera.fov) * min(1.0, dt * 4)).toFloat()
            applyView()
        }
    }

    /** Refreshes world/view matrices so [project] matches what the renderer will draw. */
    fun refreshMatrices() {
        camera.updateWorldMatrix(null)
        camera.updateView()
    }

    /** World point -> screen fractions (x right, y down), like `(ndc + 1) / 2`. */
    fun project(x: Double, y: Double, z: Double, out: DoubleArray = DoubleArray(2)): DoubleArray {
        refreshMatrices()
        tmp.set(x.toFloat(), y.toFloat(), z.toFloat())
        camera.project(tmp)
        out[0] = (tmp.x + 1.0) / 2
        out[1] = (1 - tmp.y.toDouble()) / 2
        return out
    }

    private fun frameBird(frame: MenuFrameInfo?, s: GameState) {
        if (frame == null) {
            // Leaving the menu: ease the shift back to zero instead of jumping.
            if (viewShift == 0.0) return
            viewShift += (0 - viewShift) * 0.15
            if (abs(viewShift) < 0.5) {
                viewShift = 0.0
                camera.clearViewOffset()
            } else {
                camera.setViewOffset(width, height, 0f, viewShift.toFloat(), width, height)
            }
            camera.updateProjection()
            return
        }
        camera.clearViewOffset()
        camera.updateProjection()
        val p = project(s.x, s.y, 0.0)
        val want = (p[1] - frame.center) * height
        viewShift += (want - viewShift) * 0.25
        camera.setViewOffset(width, height, 0f, viewShift.toFloat(), width, height)
        camera.updateProjection()
    }

    private fun applyView() = camera.updateProjection()

    companion object {
        /** Narrow portrait screens (aspect < 0.5) need a wider vertical FOV to see all lanes. */
        fun baseFovFor(aspect: Double): Double = if (aspect < 0.5) 74.0 else 68.0

        /** Speed kick: wider view as the pace rises, +8 in the rainbow (golden main-b-camera-fov). */
        fun targetFov(baseFov: Double, playing: Boolean, speed: Double, star: Boolean): Double {
            val pace = if (playing) ((speed - 18) / 18).coerceIn(0.0, 1.0) else 0.0
            return baseFov + pace * 4 + (if (playing && star) 8 else 0)
        }
    }
}
