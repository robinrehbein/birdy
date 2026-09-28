package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.WorldConst
import kotlin.math.abs
import kotlin.math.max

/**
 * Touch lane targeting (main-a.md §9.4, main.js `screenX`/`laneBounds`/`laneAtScreen`) and the
 * swipe detector of the canvas pointer handlers. Screen positions are fractions of the surface.
 */
class InputMapper(private val rig: CameraRig) {
    private val p = DoubleArray(2)

    /** Horizontal screen fraction of world point (x, y, 0). */
    fun screenX(x: Double, y: Double): Double = rig.project(x, y, 0.0, p)[0]

    /** Lane boundaries on screen at the bird's height [birdY]. */
    fun laneBounds(birdY: Double): DoubleArray {
        val xs = DoubleArray(3) { screenX(WorldConst.LANES[it], birdY) }
        return doubleArrayOf((xs[0] + xs[1]) / 2, (xs[1] + xs[2]) / 2)
    }

    /** Lane under screen fraction [x]; near the drawn bird it is always the bird's own lane. */
    fun laneAtScreen(x: Double, birdX: Double, birdY: Double, lane: Int): Int {
        val b = laneBounds(birdY)
        return laneAt(x, b[0], b[1], screenX(birdX, birdY), lane)
    }

    // --- swipes (pointermove) ---------------------------------------------------------------

    private var downX = 0.0
    private var downY = 0.0
    var tracking = false
        private set

    fun down(x: Double, y: Double) {
        downX = x
        downY = y
        tracking = true
    }

    fun up() {
        tracking = false
    }

    /**
     * Returns the swipe direction (sign of dx) once the move qualifies, else 0. [widthPx] and
     * [heightPx] are the surface size, [density] converts the JS CSS-pixel threshold.
     */
    fun move(x: Double, y: Double, widthPx: Double, heightPx: Double, density: Double): Int {
        if (!tracking) return 0
        val dir = swipeDirection((x - downX) * widthPx, (y - downY) * heightPx, widthPx, density)
        if (dir != 0) tracking = false
        return dir
    }

    companion object {
        /** `x < b1 ? 0 : x < b2 ? 1 : 2`, overridden by the NEAR_BIRD flap zone. */
        fun laneAt(x: Double, b1: Double, b2: Double, birdScreenX: Double, lane: Int): Int {
            val l = if (x < b1) 0 else if (x < b2) 1 else 2
            return if (abs(x - birdScreenX) <= Tuning.NEAR_BIRD) lane else l
        }

        /**
         * `|dx| >= max(18 px, 5 % width)` and `|dx| >= 1.2 |dy|` (pixels; 18 CSS px scaled by
         * [density]). Returns sign(dx) or 0.
         */
        fun swipeDirection(dxPx: Double, dyPx: Double, widthPx: Double, density: Double): Int {
            val min = max(Tuning.SWIPE_MIN_PX * density, widthPx * Tuning.SWIPE_MIN_WIDTH)
            if (abs(dxPx) < min || abs(dxPx) < abs(dyPx) * Tuning.SWIPE_DOMINANCE) return 0
            return if (dxPx > 0) 1 else -1
        }
    }
}
