package de.robinrehbein.birdy.game

import de.robinrehbein.birdy.meta.RunStats

enum class GameMode { Ready, Playing, Dead, Over }

enum class Menu { Start, Shop, Achievements }

/** `die(cause)` causes (main-b.md §18). */
enum class DeathCause { PipeTop, PipeBottom, Plant, Blocked, Ground }

/**
 * Tutorial steps (main.js `tut.step`, main-b.md §9); null = no step (JS `''`).
 * JS order: Flap (hand shown, hovering) -> Fly -> Switch (frozen before the blocked row) -> Go.
 * [Done] is never set by the simulation: when the tutorial ends the step becomes null again.
 */
enum class TutorialStep { Flap, Switch, Done, Fly, Go }

/** Ghost-hand overlay mode (main.js `showHand(mode)`). */
enum class HandMode { None, Flap, Side }

/**
 * The lane-hint overlay classes on `#zones` (main-b.md §10): [Hold] pulses while the bird waits
 * for the first tap, [Show] plays the one-shot fade to faint dividers (restarted whenever
 * [GameState.zonesShowSerial] changes).
 */
enum class ZonesHint { None, Hold, Show }

/** How a revive is paid: a rewarded ad, or coins once automatic ads were removed. */
enum class RevivePay { Ad, Coins }

/**
 * The "Weiterfliegen?" offer shown after a crash near the record (mode stays [GameMode.Dead]).
 * [timeLeft] counts down to an automatic decline; [pending] = accepted, waiting for the payment.
 */
class ReviveOffer(val pay: RevivePay) {
    var timeLeft = Tuning.REVIVE_OFFER_TIME
    var pending = false
    /** Visible game time spent waiting for the payment; frames stop while the ad covers the game. */
    var pendingTime = 0.0
}

/** main.js `lastRun`: snapshot taken in `die()` (or by the bot harness on timeout, cause = null). */
data class LastRun(val score: Int, val coins: Int, val time: Double, val cause: DeathCause?, val zone: Int)

/**
 * Bird transform integrated by the simulation (main.js `updateBirdVisual`, `updateDead`, `die`);
 * the bird view copies it onto the mesh. Rotation in radians, scale absolute (includes BIRD_SCALE).
 */
class BirdPose {
    var rotX = 0.0
    var rotZ = 0.0
    var scaleX = Tuning.BIRD_SCALE
    var scaleY = Tuning.BIRD_SCALE
    var scaleZ = Tuning.BIRD_SCALE
    /** Smoothed normal/mini scale (`state.baseScale`). */
    var baseScale = Tuning.BIRD_SCALE
    /** False on the "off" half of the grace-period blink (`g.visible`). */
    var visible = true
}

/**
 * The mutable simulation state (main.js `state`, main-a.md §4). Owned and mutated only by
 * [GameSimulation] on the game thread; views read it each frame. Doubles keep JS number
 * semantics for parity.
 */
class GameState {
    var mode = GameMode.Ready
    var paused = false
    var x = 0.0
    var y = 5.0
    var vy = 0.0
    var lane = 1
    var radius = Tuning.BIRD_RADIUS
    var speed = 10.0
    var distance = 0.0
    var score = 0
    var coins = 0
    var lastGateZ = 0.0
    var gatesSpawned = 0
    var gatesToPower = 6
    /** Remaining seconds per power-up; 0 = inactive. */
    val power = DoubleArray(PowerType.entries.size)
    var grace = 0.0
    var wingPhase = 0.0
    var wingSpeed = 10.0
    var deadTimer = 0.0
    var shake = 0.0
    var time = 0.0
    var beat = 0.0
    var runTime = 0.0
    var hold = false
    /** Centre height of the "get ready" hover (5; a revive moves it into the next gap). */
    var holdY = 5.0
    /** The record before this run (revive eligibility). */
    var runBest = 0
    /** A revive was already offered this run (at most once per run). */
    var reviveOffered = false
    /** This run was revived. */
    var revived = false
    /** Rows cleared by a revive without scoring; shifts which row index carries the record marker. */
    var unscoredRows = 0
    /** Non-null while the revive offer is up. */
    var revive: ReviveOffer? = null
    /** Monotonic millis when game over was shown (restart debounce 350 ms). */
    var overAt = 0L
    var zone = 0
    var rushAt = -1
    var squash = 0.0
    var hitStop = 0.0
    var nearChain = 0
    var menu = Menu.Start
    var run = RunStats()
    val celebrated = HashSet<String>()
    /** Debug/screenshot-only invincibility (JS `state.god`); never set by the shipped UI. */
    var god = false
    var deathCause: DeathCause? = null
    var tutorialStep: TutorialStep? = null

    /** `state.prevGaps`: the previous row's spec (null after a rush / at run start). */
    var prevGaps: List<GapSpec?>? = null
    /** `tut.active`. */
    var tutorialActive = false
    /** `tut.freezeY`. */
    var freezeY = 0.0
    var hand = HandMode.None
    var zonesHint = ZonesHint.None
    /** Incremented whenever the one-shot `show` animation of the lane hint (re)starts. */
    var zonesShowSerial = 0
    /** main.js `lastRun`, set by `die()`. */
    var lastRun: LastRun? = null
    val pose = BirdPose()

    fun power(type: PowerType): Double = power[type.ordinal]

    /** `invincible()` (main.js:741). */
    val invincible: Boolean get() = power(PowerType.Star) > 0 || grace > 0 || god
}
