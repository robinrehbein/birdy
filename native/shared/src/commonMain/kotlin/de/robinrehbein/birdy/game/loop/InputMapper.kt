package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.WorldConst
import kotlin.math.abs
import kotlin.math.max

/**
 * The sideways-swipe detector of the canvas pointer handlers (swipe controls: where a touch lands
 * does not matter) plus the projected lane geometry the lane hint overlay draws
 * (main.js `screenX`/`laneBounds`). Screen positions are fractions of the surface.
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
