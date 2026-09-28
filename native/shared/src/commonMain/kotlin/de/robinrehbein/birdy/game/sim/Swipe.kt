package de.robinrehbein.birdy.game.sim

/** main.js `swipe` tracker: armed on pointerdown, consumed by the first qualifying move. */
class SwipeState {
    var done = true
    /** Lane at touch start. */
    var lane = 0
    /** Vertical speed before the touch's own tap. */
    var vy = 0.0

    fun arm(done: Boolean, lane: Int, vy: Double) {
        this.done = done
        this.lane = lane
        this.vy = vy
    }
}
