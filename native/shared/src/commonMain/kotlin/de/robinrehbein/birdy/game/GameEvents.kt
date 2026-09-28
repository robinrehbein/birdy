package de.robinrehbein.birdy.game

import de.robinrehbein.birdy.meta.Achievement
import de.robinrehbein.birdy.meta.Mission
import de.robinrehbein.birdy.meta.RunResult

/**
 * Side effects emitted by [GameSimulation] instead of calling audio/haptics/UI directly.
 * The game loop routes them (audio, haptics gate on mute, UI toasts, flashes, camera shake).
 * Haptics are always explicit [Buzz] events; everything else a JS call site did next to the
 * state change is listed per event.
 */
sealed class GameEvent {
    /** `sfx.flap()`. */
    data object Flap : GameEvent()
    /** `setLane` changed the lane: `sfx.swoosh()`, lane dots. */
    data class LaneSwitch(val from: Int, val to: Int) : GameEvent()
    /** `addScore`: `sfx.point()`, score pop. */
    data class Point(val score: Int) : GameEvent()
    /** Coin pickup at (x, y, z): `sfx.coin()`, coin bump, 8-particle burst at the coin. */
    data class CoinCollected(val runCoins: Int, val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) : GameEvent()
    /** `activatePower`: `sfx.powerup()`, 24-particle burst; star also `music.setHype(true)`. */
    data class PowerUp(val type: PowerType) : GameEvent()
    /** Timer ran out: `sfx.powerdown()`; star also `music.setHype(false)`, `bird.setGlow(null)`. */
    data class PowerDown(val type: PowerType) : GameEvent()
    /** `nearMiss()`: coin bump, `sfx.near(chain - 1)`, "Knapp!" popup (×chain if > 1), particles. */
    data class NearMiss(val chain: Int) : GameEvent()
    /** `enterZone`: biome blend (3 s), `music.setTheme(zone)`, banner, `sfx.zone()`. */
    data class ZoneEntered(val zone: Int) : GameEvent()
    /** Invincible ground bounce: `sfx.bounce()`. */
    data object Bounce : GameEvent()
    /**
     * `die(cause)`: `sfx.hit()`, `music.duck()`, hype off, glow off, feathers, bonk star at
     * bird + [hitOffset], white flash 0.55. Hit-stop and shake are in [GameState].
     */
    data class Died(val cause: DeathCause) : GameEvent()
    /** `showGameOver()`: `music.setMode(menu)`, game-over screen. */
    data class GameOver(val summary: RunSummary) : GameEvent()
    /** Newly reached daily missions mid-run: toast `[check] text +reward` and `sfx.powerup()` each. */
    data class MissionPreview(val missionIds: List<String>) : GameEvent()
    /** Newly unlocked achievements mid-run: toast `[trophy] name +reward` and `sfx.powerup()` each. */
    data class AchievementPreview(val achievements: List<Achievement>) : GameEvent()
    /** `buzz(ms)`; the loop drops it while muted. */
    data class Buzz(val millis: Int) : GameEvent()
    /**
     * Toast with an i18n key + params (rendered by the UI toast queue). [delayMs] > 0 schedules it
     * like JS `setTimeout` (not cancelled by [RunStarted]).
     */
    data class Toast(val key: String, val params: Map<String, Any?> = emptyMap(), val delayMs: Int = 0) : GameEvent()

    /**
     * `resetGame()` ran: clear the toast queue, `music.setTheme(0)`, `setMode(game)`, hype off,
     * glow off, particles/impact cleared, biome to zone 0 (1.2 s fade if different), HUD shown,
     * menus hidden, equipped look re-applied. [GameState.hand]/[GameState.zonesHint] are set.
     */
    data class RunStarted(val tutorial: Boolean) : GameEvent()
    /** The first flap of a run ended the "get ready" hover. */
    data object HoldEnded : GameEvent()
    /** `setPaused(p)`: pause screen and `audio.setSuspended(p)`. */
    data class PauseChanged(val paused: Boolean) : GameEvent()
    /** `goToMenu()`: hype off, glow off, `music.setMode(menu)`, menus/wallet shown. */
    data object WentToMenu : GameEvent()
    /** `spawnRush`: switch scenery to `zoneBiome(zone).scenery` now (chunks ahead use it). */
    data class SceneryTheme(val zone: Int) : GameEvent()
    /** A `sfx.powerup()` celebration without a power-up (record, tutorial done), after [delayMs]. */
    data class Fanfare(val delayMs: Int = 0) : GameEvent()
}

/** Where the bonk star appears relative to the bird (main.js `HIT_OFFSET`). */
val DeathCause.hitOffset: DoubleArray
    get() = when (this) {
        DeathCause.PipeTop -> doubleArrayOf(0.25, 0.8, 0.0)
        DeathCause.PipeBottom -> doubleArrayOf(0.25, -0.75, 0.0)
        DeathCause.Plant -> doubleArrayOf(0.25, -0.75, 0.0)
        DeathCause.Ground -> doubleArrayOf(0.25, -0.7, 0.0)
        DeathCause.Blocked -> doubleArrayOf(0.8, 0.45, -0.6)
    }

/** JS cause strings ('pipe-top', …) for reports. */
val DeathCause.jsName: String
    get() = when (this) {
        DeathCause.PipeTop -> "pipe-top"
        DeathCause.PipeBottom -> "pipe-bottom"
        DeathCause.Plant -> "plant"
        DeathCause.Ground -> "ground"
        DeathCause.Blocked -> "blocked"
    }

/** Everything the game-over screen shows (main.js `lastRun`, main-b.md §5). */
data class RunSummary(
    val score: Int,
    val coins: Int,
    val time: Double,
    val zone: Int,
    val cause: DeathCause?,
    val result: RunResult,
    val completedMissions: List<Mission> = result.completed,
)

/** Receives [GameEvent]s synchronously on the game thread. */
fun interface GameEventSink {
    fun emit(event: GameEvent)
}
