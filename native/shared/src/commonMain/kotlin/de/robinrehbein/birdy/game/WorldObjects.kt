package de.robinrehbein.birdy.game

/**
 * Gap spec for one lane of a row, as produced by `gateSpec()`/`makeReachable()` and consumed by
 * `gate.configure()` (main-a.md §8). A blocked lane is represented by `null` in the row spec.
 */
data class GapSpec(
    val center: Double,
    val size: Double,
    val amp: Double = 0.0,
    val speed: Double = 0.0,
    val phase: Double = 0.0,
    val plant: Boolean = false,
    val plantOffset: Double = 0.0,
    val pulse: Boolean = false,
    /** Wandering gap: the lane this gap starts in before it slides into its own (-1 = none). */
    val wanderFrom: Int = -1,
)

/**
 * Logical per-lane state of a gate row (world.js lane object minus meshes). The simulation
 * recomputes the animated gap and hitbox every frame (world.md §7.4 `update`: hitLow/hitHigh
 * follow the moving/pulsing gap and the rising plant), and exposes the plant pose for the view.
 *
 * Like world.js, `configure()` of a blocked lane only resets blocked/hasPlant/gap bounds; the
 * other fields keep the values of the row's previous use.
 */
class LaneState(val x: Double) {
    var blocked = false
    var center = 0.0
    var size = 0.0
    var amp = 0.0
    var speed = 0.0
    var phase = 0.0
    var hasPlant = false
    var plantOffset = 0.0
    var pulse = false
    var gapLow = 0.0
    var gapHigh = 0.0
    var hitLow = 0.0
    var hitHigh = 0.0

    // Plant pose (computed with the hitbox; rendered by the world view).
    var plantVisible = false
    /** Plant group origin Y (world units). */
    var plantY = 0.0
    /** Group rotation.z while peeking (`sin(time*22)*0.1`), else 0. */
    var plantWiggle = 0.0
    /** 0..1 chomp amount; drives body scale and the bristle shader. */
    var plantPuff = 0.0
}

/** One pooled gate row (main.js `gates`, 12 entries). */
class GateRow {
    var active = false
    /** `group.visible`: false once recycled or fully faded after passing. */
    var visible = false
    var z = 0.0
    var passed = false
    /** Fade-out opacity after being passed (world.js setOpacity, 0.001 change threshold). */
    var opacity = 1.0
    /** Row cloud decoration index from the seeded pickRowCloud() LCG (-1 = none). */
    var cloud = -1
    /** The row whose index equals the previous best score: carries the golden record marker. */
    var record = false
    /** Tightest clearance seen inside this row (JS `gate.minClear`, null = undefined). */
    var minClear: Double? = null
    /**
     * Wandering gap (native addition): the gap starts in lane [wanderFrom] and slides into lane
     * [wanderTo] (-1 = none) while [wanderT] runs 0..1; the lanes swap open/blocked at 0.5.
     */
    var wanderFrom = -1
    var wanderTo = -1
    var wanderT = 0.0
    val lanes = Array(3) { LaneState(WorldConst.LANES[it]) }
}

/** One pooled coin (world.js createCoinField entry). */
class Coin {
    var active = false
    var visible = false
    var x = 0.0
    var y = 0.0
    var z = 0.0
    /** Visual spin angle (radians) and pickup shrink scale, advanced by the simulation. */
    var spin = 0.0
    var scale = 1.0
}

/** One pooled power-up pickup (2 per type). */
class Pickup(val type: PowerType) {
    var active = false
    var visible = false
    var x = 0.0
    var y = 0.0
    var z = 0.0
    /** Forward shrink `k` before passing the bird (bird-fx.md §5.4); 1 while ahead. */
    var scale = 1.0
}

/** Zone boundary marker ahead of the bird (main.js `zoneMarks`). */
data class ZoneMark(val z: Double, val zone: Int)
