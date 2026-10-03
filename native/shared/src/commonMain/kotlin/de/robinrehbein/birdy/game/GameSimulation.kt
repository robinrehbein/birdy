package de.robinrehbein.birdy.game

import de.robinrehbein.birdy.game.sim.Difficulty
import de.robinrehbein.birdy.game.sim.RowCloudPicker
import de.robinrehbein.birdy.game.sim.SimDebug
import de.robinrehbein.birdy.game.sim.SimMath
import de.robinrehbein.birdy.game.sim.SwipeState
import de.robinrehbein.birdy.game.sim.addToRun
import de.robinrehbein.birdy.game.sim.checkMissions
import de.robinrehbein.birdy.game.sim.prespawnRows
import de.robinrehbein.birdy.game.sim.tickShake
import de.robinrehbein.birdy.game.sim.updateBirdVisual
import de.robinrehbein.birdy.game.sim.updateDead
import de.robinrehbein.birdy.game.sim.updatePlaying
import de.robinrehbein.birdy.meta.ProgressRepository
import de.robinrehbein.birdy.meta.RunStats
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pure game logic of main.js (no rendering, no audio, no UI): run reset, gate-row generation
 * with the fairness pass, coins/pickups/rush placement, physics, lane switching, collisions,
 * scoring, near misses, power-ups, zones, tutorial flow and run finishing.
 *
 * Deterministic given [rng] and the sequence of [step]/[flap]/[tapLane]/[pointerDown]/[swipe] calls, so tests, the
 * bot playtest and the screenshot tool can replay runs. Game thread only.
 *
 * Frame driving: the loop calls [update] (JS `update()`: hit-stop, [step], bird pose, shake decay)
 * or composes [step] + [updateBirdVisual] + [tickShake] itself. The bot harness calls [step] at
 * a fixed 1/60 like JS `simulate()`.
 */
class GameSimulation(
    val progress: ProgressRepository,
    val rng: Random,
    internal val events: GameEventSink,
) {
    val state = GameState()
    val gates: List<GateRow> = List(Tuning.GATE_POOL) { GateRow() }
    val coins: List<Coin> = List(Tuning.COIN_POOL) { Coin() }
    val pickups: List<Pickup> = PowerType.entries.flatMap { t -> List(Tuning.PICKUPS_PER_TYPE) { Pickup(t) } }
    val zoneMarks = ArrayList<ZoneMark>()

    /** Nearest active, unpassed row while playing (height-marker target); null otherwise. */
    var nextGate: GateRow? = null
        internal set

    /** The tutorial's dodge row (`tut.gate`). */
    var tutorialGate: GateRow? = null
        internal set

    /**
     * Monotonic millis for the game-over restart debounce (JS `performance.now()`). Defaults to
     * game time; the loop should inject the platform clock.
     */
    var clockMillis: () -> Long = { (state.time * 1000).toLong() }

    internal val rowClouds = RowCloudPicker()
    internal val swipeState = SwipeState()

    /** Screenshot/test hooks (god mode, power-ups, zones, scripted rows, advance). */
    val debug = SimDebug(this)

    internal fun emit(event: GameEvent) = events.emit(event)

    // --- pacing -----------------------------------------------------------------------------

    fun difficulty(): Double = Difficulty.difficulty(state.score)
    fun baseSpeed(): Double = Difficulty.baseSpeed(state.score)
    fun spacing(): Double = Difficulty.spacing(state.score)
    fun powerDuration(type: PowerType): Double = Tuning.powerDuration(type, progress.level(type.id))
    fun magnetRange(): Double = Tuning.magnetRange(progress.level("magnet"))

    // --- run lifecycle ------------------------------------------------------------------------

    /** First launch: straight into the guided first run (`tut.active = true; resetGame()`). */
    fun startTutorialRun() {
        state.tutorialActive = true
        resetGame()
    }

    /** `resetGame()` (main-a.md §6). */
    fun resetGame() {
        val s = state
        val runs = progress.data.value.runs
        s.mode = GameMode.Playing
        s.hold = true
        s.holdY = 5.0
        s.x = 0.0
        s.y = 5.0
        s.vy = 0.0
        s.lane = 1
        s.radius = Tuning.BIRD_RADIUS
        s.speed = 18.0
        s.score = 0
        s.coins = 0
        s.prevGaps = null
        s.gatesSpawned = 0
        s.gatesToPower = 5 + floor(rng.nextDouble() * 3).toInt() - progress.level("luck")
        s.power.fill(0.0)
        s.grace = 0.0
        s.deadTimer = 0.0
        s.shake = 0.0
        s.runTime = 0.0
        s.nearChain = 0
        s.zone = 0
        s.rushAt = -1
        zoneMarks.clear()
        s.squash = 0.0
        s.run = RunStats()
        s.celebrated.clear()
        s.pose.rotX = 0.0
        s.pose.rotZ = 0.0
        s.pose.visible = true
        s.deathCause = null
        s.runBest = progress.data.value.best
        s.reviveOffered = false
        s.revived = false
        s.revive = null
        nextGate = null
        emit(GameEvent.RunStarted(s.tutorialActive))
        // Second and fourth run: point out the swipe alternative once more.
        if (runs == 1 || runs == 3) emit(GameEvent.Toast("swipeHint", delayMs = 900))

        clearTrack()
        prespawnRows()
        s.menu = Menu.Start
        s.zonesHint = ZonesHint.None
        if (s.tutorialActive) {
            s.tutorialStep = TutorialStep.Flap
            s.hand = HandMode.Flap
        } else {
            s.zonesHint = ZonesHint.Hold
        }
    }

    internal fun clearTrack() {
        for (g in gates) { g.active = false; g.visible = false }
        for (c in coins) { c.active = false; c.visible = false }
        for (p in pickups) { p.active = false; p.visible = false }
    }

    /** `die(cause)` (main-b.md §18); only while playing. */
    fun die(cause: DeathCause) {
        val s = state
        if (s.mode != GameMode.Playing) return
        s.mode = GameMode.Dead
        nextGate = null
        s.hand = HandMode.None
        if (s.zonesHint == ZonesHint.Show) s.zonesHint = ZonesHint.None
        s.hitStop = Tuning.HIT_STOP
        s.lastRun = LastRun(s.score, s.coins, s.runTime, cause, s.zone)
        s.deathCause = cause
        s.deadTimer = 0.0
        s.shake = 0.5
        s.vy = max(s.vy, 6.0)
        // The bird is squashed flat for the freeze-frame.
        val sc = s.pose.scaleX
        s.pose.scaleX = sc * 1.3
        s.pose.scaleY = sc * 0.72
        s.pose.scaleZ = sc * 1.25
        emit(GameEvent.Buzz(70))
        emit(GameEvent.Died(cause))
    }

    // --- revive ("Weiterfliegen?") ----------------------------------------------------------------

    /**
     * Injected by the session: how a revive could be paid right now (coins when automatic ads
     * were removed, an ad when one is loaded), or null to never offer one. Default: never.
     */
    var reviveOption: () -> RevivePay? = { null }

    /** Once per run, only with a record of [Tuning.REVIVE_MIN_BEST]+ and a score of 80 % of it. */
    fun reviveEligible(): Boolean {
        val s = state
        return !s.tutorialActive && !s.reviveOffered && s.runBest >= Tuning.REVIVE_MIN_BEST &&
            s.score * 5 >= s.runBest * 4
    }

    /** At the end of the death freeze: opens the offer if eligible and payable. */
    internal fun offerRevive(): Boolean {
        val s = state
        if (!reviveEligible()) return false
        val pay = reviveOption() ?: return false
        s.reviveOffered = true
        s.revive = ReviveOffer(pay)
        return true
    }

    /** "Watch ad" / "Continue for coins": freezes the countdown; returns how to pay, or null. */
    fun acceptRevive(): RevivePay? {
        val offer = state.revive ?: return null
        if (offer.pending || state.mode != GameMode.Dead) return null
        offer.pending = true
        return offer.pay
    }

    /** "No thanks", a tap elsewhere or the timeout: the normal game over. */
    fun declineRevive() {
        val offer = state.revive ?: return
        if (offer.pending) return
        state.revive = null
        showGameOver()
    }

    /** Outcome of an accepted offer: [earned] revives the run, otherwise game over. */
    fun reviveResult(earned: Boolean) {
        val offer = state.revive ?: return
        if (!offer.pending) return
        state.revive = null
        if (earned) revive(offer.pay) else showGameOver()
    }

    /**
     * Back into the run with the same score and coins: the colliding row counts as passed (no
     * point) and vanishes, the bird hovers in its lane at the next gap's height until the first
     * tap, then has [Tuning.REVIVE_GRACE] seconds of the star's grace invulnerability.
     */
    private fun revive(pay: RevivePay) {
        val s = state
        s.mode = GameMode.Playing
        s.revived = true
        s.deathCause = null
        s.hitStop = 0.0
        s.shake = 0.0
        s.deadTimer = 0.0
        s.nearChain = 0
        val reach = WorldConst.PIPE_RADIUS + 0.25 + s.radius
        for (g in gates) {
            if (!g.active || g.passed || g.z < -reach) continue
            g.passed = true
            g.minClear = null
            g.visible = false
        }
        s.x = WorldConst.LANES[s.lane]
        s.holdY = safeHoverY()
        s.y = s.holdY
        s.vy = 0.0
        s.hold = true
        s.grace = Tuning.REVIVE_GRACE
        s.pose.rotX = 0.0
        s.pose.rotZ = 0.0
        s.pose.visible = true
        s.hand = HandMode.None
        s.zonesHint = ZonesHint.Hold
        emit(GameEvent.Revived(pay))
    }

    /** Middle of the next open gap in the bird's lane, else the usual hover height. */
    private fun safeHoverY(): Double {
        val s = state
        val next = gates.filter { it.active && !it.passed && it.z < 0 }.maxByOrNull { it.z } ?: return 5.0
        val lane = next.lanes[s.lane]
        if (lane.blocked) return 5.0
        return SimMath.clamp((lane.hitLow + lane.hitHigh) / 2, 2.0, Tuning.CEILING - 2)
    }

    /** `showGameOver()`: persists the run through `progress.finishRun`. */
    internal fun showGameOver() {
        val s = state
        s.mode = GameMode.Over
        s.overAt = clockMillis()
        s.run = s.run.copy(coins = s.coins, score = s.score)
        val result = progress.finishRun(s.run)
        if (result.isBest && s.score > 0) {
            // Record fanfare.
            emit(GameEvent.Fanfare(delayMs = 250))
            emit(GameEvent.Buzz(30))
        }
        val last = s.lastRun
        emit(GameEvent.GameOver(RunSummary(s.score, s.coins, last?.time ?: s.runTime, s.zone, last?.cause, result)))
    }

    /** `tryRestart()`: game over -> new run, debounced 350 ms. Returns true if a run started. */
    fun tryRestart(): Boolean {
        if (state.mode == GameMode.Over && clockMillis() - state.overAt > Tuning.RESTART_DEBOUNCE_MS) {
            resetGame()
            return true
        }
        return false
    }

    fun setPaused(paused: Boolean) {
        state.paused = paused
        emit(GameEvent.PauseChanged(paused))
    }

    /** `goToMenu()` state reset (main-b.md §3 checklist, simulation part). */
    fun goToMenu() {
        val s = state
        setPaused(false)
        s.mode = GameMode.Ready
        s.hold = false
        s.revive = null
        // Leaving a run with a power-up active: no rainbow glow or mini bird in the menu.
        s.power.fill(0.0)
        s.grace = 0.0
        s.zonesHint = ZonesHint.None
        nextGate = null
        s.hand = HandMode.None
        s.pose.rotX = 0.0
        s.pose.rotZ = 0.0
        s.pose.visible = true
        s.y = 5.0
        s.menu = Menu.Start
        clearTrack()
        emit(GameEvent.WentToMenu)
    }

    // --- input ------------------------------------------------------------------------------

    /** The single tap action (`flap()`, main-a.md §9.3): unpause, start a run, or flap. */
    fun flap() {
        val s = state
        if (s.paused) {
            setPaused(false)
            return
        }
        if (s.mode == GameMode.Ready) {
            resetGame()
            return
        }
        if (s.mode != GameMode.Playing) return
        if (s.hold) {
            s.hold = false
            if (s.zonesHint == ZonesHint.Hold) s.zonesHint = ZonesHint.None
            if (s.tutorialActive) {
                s.tutorialStep = TutorialStep.Fly
                s.hand = HandMode.None
            } else {
                showZones(restart = true)
            }
            emit(GameEvent.HoldEnded)
        }
        s.vy = Tuning.FLAP_VELOCITY
        s.wingSpeed = Tuning.WING_FLAP
        s.squash = 1.0
        emit(GameEvent.Flap)
    }

    /**
     * Programmatic lane action (bot, tests; JS `tap(lane)`): the own lane flaps, another lane
     * moves one step toward it with a hop. Touch input uses [pointerDown] + [swipe] instead.
     */
    fun tapLane(lane: Int) {
        val s = state
        if (s.hold) return flap() // first tap just starts
        // Tutorial: until the dodge lesson every tap flaps.
        if (s.tutorialActive && (s.tutorialStep == TutorialStep.Flap || s.tutorialStep == TutorialStep.Fly)) return flap()
        if (s.tutorialStep == TutorialStep.Switch) {
            if (lane == s.lane) return // frozen until the player dodges
            s.tutorialStep = TutorialStep.Go
            s.hand = HandMode.None
        }
        if (s.mode == GameMode.Playing && !s.paused && lane != s.lane) {
            // One lane per action: a two-lane jump would sweep through the middle pipe.
            switchHop(s.lane + SimMath.sign(lane - s.lane), s.vy)
            return
        }
        flap()
    }

    /** Bot/test alias of [tapLane] (JS `tap`). */
    fun tap(lane: Int) = tapLane(lane)

    /** `setLane` (keyboard arrows, swipes): clamps, only while playing. */
    fun setLane(lane: Int) {
        val s = state
        if (s.mode != GameMode.Playing) return
        val next = min(max(lane, 0), WorldConst.LANES.size - 1)
        if (next != s.lane) {
            val from = s.lane
            s.lane = next
            emit(GameEvent.LaneSwitch(from, next))
        }
    }

    /** Lane change with the small hop instead of a flap (`vy = max(vyBefore, SWITCH_HOP)`). */
    private fun switchHop(lane: Int, vyBefore: Double) {
        val s = state
        setLane(lane)
        s.vy = max(vyBefore, Tuning.SWITCH_HOP)
        s.wingSpeed = Tuning.WING_SWITCH
        s.squash = 0.5
    }

    /**
     * Touch down anywhere on the canvas (swipe controls): unpauses if paused, otherwise flaps at
     * once in the current lane (a tap and a swipe up both flap without latency) and arms the
     * swipe tracker. The screen position does not matter and the lane never changes here. In the
     * tutorial's dodge lesson the touch does nothing until it turns into a sideways [swipe].
     * Returns true if a run was already in flight (show the tap ripple).
     */
    fun pointerDown(): Boolean {
        val s = state
        if (s.paused) {
            setPaused(false)
            return false
        }
        val from = s.lane
        val vy = s.vy
        val wasPlaying = s.mode == GameMode.Playing && !s.hold
        val dodgeLesson = wasPlaying && s.tutorialStep == TutorialStep.Switch
        if (!dodgeLesson) flap()
        // Sideways swipes count in normal runs and in the dodge lesson, not while learning to flap.
        val swipes = wasPlaying && (!s.tutorialActive || dodgeLesson)
        swipeState.arm(done = !swipes, lane = from, vy = vy)
        return wasPlaying && !dodgeLesson
    }

    /**
     * The touch turned into a sideways swipe ([direction] = sign of dx; thresholds in [Tuning]).
     * The swipe wins: exactly one lane from where the gesture started, the touch's flap is taken
     * back as a hop, and the bird stays in that lane until the next sideways swipe. A swipe into
     * the edge keeps the flap. One action per touch. Returns true if the lane changed.
     */
    fun swipe(direction: Int): Boolean {
        val s = state
        val sw = swipeState
        if (sw.done) return false
        sw.done = true
        if (s.mode != GameMode.Playing || s.paused || s.hold || direction == 0) return false
        val lesson = s.tutorialStep == TutorialStep.Switch
        if (s.tutorialActive && !lesson) return false
        val target = min(max(sw.lane + SimMath.sign(direction), 0), WorldConst.LANES.size - 1)
        if (target == sw.lane) return false // swipe into the edge: keep the flap
        if (lesson) {
            s.tutorialStep = TutorialStep.Go
            s.hand = HandMode.None
        }
        val before = s.lane
        switchHop(target, sw.vy)
        return s.lane != before
    }

    /** `pointerup` / `pointercancel`. */
    fun pointerUp() {
        swipeState.done = true
    }

    // --- per frame ----------------------------------------------------------------------------

    /**
     * Advances the simulation by [dt] seconds (already clamped to 1/30 by the loop). [beat] is the
     * music transport beat, or null to derive it from game time (FALLBACK_BPM), as JS `step()`.
     */
    fun step(dt: Double, beat: Double?) {
        val s = state
        s.time += dt
        // Plants follow the music's beat; without audio fall back to game time.
        s.beat = beat ?: (s.time * Tuning.FALLBACK_BPM / 60)
        when (s.mode) {
            GameMode.Ready -> {
                moveWorld(10 * dt)
                s.y = 5 + sin(s.time * 3) * 0.4
                s.vy = cos(s.time * 3) * 1.2
            }
            GameMode.Playing -> updatePlaying(dt)
            else -> updateDead(dt)
        }
    }

    /**
     * JS `update(rawDt)` minus rendering/fx: clamps dt to 1/30, runs the hit-stop freeze-frame,
     * then [step], the bird pose ([updateBirdVisual]) and the camera-shake decay ([tickShake]).
     * Callers skip it while paused.
     */
    fun update(rawDt: Double, beat: Double?) {
        val dt = min(rawDt, 1.0 / 30)
        if (state.hitStop > 0) {
            // Freeze-frame on impact; only the camera shake keeps going.
            state.hitStop -= rawDt
            tickShake(dt)
            return
        }
        step(dt, beat)
        updateBirdVisual(dt)
        tickShake(dt)
    }

    internal fun moveWorld(dz: Double) {
        state.distance += dz
    }

    // --- events shared by several sim files -----------------------------------------------------

    /** `activatePower(type)`. */
    fun activatePower(type: PowerType) {
        val s = state
        s.power[type.ordinal] = powerDuration(type)
        addToRun { it.copy(powerups = it.powerups + 1) }
        checkMissions()
        if (type == PowerType.Star) s.grace = 0.0
        emit(GameEvent.PowerUp(type))
    }

    /** `enterZone(zone)`: the bird passed a zone banner. */
    fun enterZone(zone: Int) {
        state.zone = zone
        emit(GameEvent.ZoneEntered(zone))
        addToRun { it.copy(zone = max(it.zone, zone)) }
        checkMissions()
    }

    /** Adds `show` to the lane hint; [restart] replays the one-shot animation (reflow trick). */
    internal fun showZones(restart: Boolean) {
        if (state.zonesHint != ZonesHint.Show || restart) state.zonesShowSerial++
        state.zonesHint = ZonesHint.Show
    }
}
