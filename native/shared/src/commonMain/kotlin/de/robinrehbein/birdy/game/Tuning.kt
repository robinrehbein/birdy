package de.robinrehbein.birdy.game

/**
 * Gameplay tuning copied verbatim from src/main.js (lines 28-53) and src/world.js constants.
 * Units: world units, seconds. Never "improve" these values; see main-a.md §1.
 */
object Tuning {
    const val GRAVITY = 36.0
    const val FLAP_VELOCITY = 11.5
    const val SWITCH_HOP = 6.0
    const val WARMUP_GATES = 6
    const val MAX_FALL = -22.0
    const val CEILING = 14.0
    const val BIRD_RADIUS = 0.5
    const val MINI_RADIUS = 0.3
    const val BIRD_SCALE = 1.1
    const val MINI_SCALE = 0.6
    const val LANE_SWITCH_SPEED = 18.0
    const val SPAWN_DISTANCE = 170.0
    const val FIRST_GATE_Z = -60.0
    const val COIN_RADIUS = 1.1
    const val MAGNET_RANGE = 9.0
    const val STAR_SPEED_BOOST = 1.35
    const val GRACE_TIME = 1.2
    const val FALLBACK_BPM = 124.0
    const val ZONE_ROWS = 10
    const val NEAR_MISS = 0.45
    const val HIT_STOP = 0.14

    /** Per-upgrade-level bonus for [PowerType] durations / magnet reach (`UPGRADE_BONUS`). */
    fun upgradeBonus(type: PowerType): Double = when (type) {
        PowerType.Star -> 1.5
        PowerType.Magnet -> 3.0
        PowerType.Mini -> 3.0
    }

    /** `powerDuration(type)` for a given upgrade level. */
    fun powerDuration(type: PowerType, level: Int): Double = type.baseDuration + upgradeBonus(type) * level

    /** `magnetRange()` for a given magnet upgrade level. */
    fun magnetRange(level: Int): Double = MAGNET_RANGE + 2 * level

    /** Pool sizes (main-a.md §2.2). */
    const val GATE_POOL = 12
    const val COIN_POOL = 60
    const val PICKUPS_PER_TYPE = 2

    /** biomes.js `BIOMES.length`. */
    const val BIOME_COUNT = 4
    /** Row index of the tutorial's dodge lesson (`TUT_SWITCH_ROW`). */
    const val TUT_SWITCH_ROW = 3
    /** makeReachable: each lane step multiplies the reach budgets by this. */
    const val SWITCH_FACTOR = 0.6
    /** Pickup collection radius (independent of the bubble's look). */
    const val PICKUP_RADIUS = 1.4
    /** Seconds from `die()` to `showGameOver()`. */
    const val DEAD_TO_OVER = 0.45
    /** `tryRestart` debounce after the game-over screen appears. */
    const val RESTART_DEBOUNCE_MS = 350L
    /** Wing speeds: after a flap, after a lane switch, idle target. */
    const val WING_FLAP = 38.0
    const val WING_SWITCH = 26.0
    const val WING_IDLE = 12.0
    /** Swipe detection (pointermove): |dx| >= max(18 px, 5 % width) and |dx| >= 1.2 |dy|. */
    const val SWIPE_MIN_PX = 18.0
    const val SWIPE_MIN_WIDTH = 0.05
    const val SWIPE_DOMINANCE = 1.2

    /** Revive ("Weiterfliegen?"): only once the record before the run is at least this. */
    const val REVIVE_MIN_BEST = 10
    /** Seconds the revive offer stays up before it counts as declined. */
    const val REVIVE_OFFER_TIME = 3.0
    /** Invulnerability after a revive (the star's grace blink), counted from the first tap. */
    const val REVIVE_GRACE = 2.0
    /** Coin price of a revive when automatic ads were removed (no ad offered then). */
    const val REVIVE_COINS = 100
}

/** world.js exported/shared constants used by simulation and views. */
object WorldConst {
    val LANES = doubleArrayOf(-3.0, 0.0, 3.0)
    const val PIPE_RADIUS = 1.1
    const val PIPE_TOP = 40.0
    const val GROUND_TILE = 10.0
    const val PLANT_HEIGHT = 1.8
    const val PLANT_REACH = 0.9
    const val PLANT_WIDTH = 1.2
    const val CACTUS_R_Y = 0.8
    const val CACTUS_Y = PLANT_HEIGHT - 0.95
    const val BANK_Y = 16.0
}

/** Power-up catalogue (powerups.js POWERUPS; order = POWERUP_TYPES). */
enum class PowerType(val id: String, val baseDuration: Double, val color: Int, val icon: String) {
    Star("star", 6.0, 0xffd400, "rainbow"),
    Magnet("magnet", 9.0, 0xe53935, "magnet"),
    Mini("mini", 9.0, 0x9b59b6, "mushroom"),
}
