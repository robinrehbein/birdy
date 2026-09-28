package de.robinrehbein.birdy.game

import de.robinrehbein.birdy.audio.GameAudio
import de.robinrehbein.birdy.audio.SynthAudio
import de.robinrehbein.birdy.engine.RenderBackend
import de.robinrehbein.birdy.engine.RenderTarget
import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.game.loop.GameSession
import de.robinrehbein.birdy.game.loop.GameViews
import de.robinrehbein.birdy.meta.ProgressRepository
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.platform.PlatformServices
import de.robinrehbein.birdy.view.SceneView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.random.Random

/** What the game needs once storage is ready (built on the game thread by [GameBoot]). */
class GameParts(
    val progress: ProgressRepository,
    val audio: GameAudio,
    val views: GameViews = GameViews(),
    /** Simulation randomness (gate rows, power-up spacing). */
    val simRandom: Random = Random.Default,
    /** UI randomness (camera shake, surprise, dice). */
    val uiRandom: Random = Random.Default,
)

/** Returns the parts once the game can start (migration done), else null (try next frame). */
fun interface GameBoot {
    fun tryBoot(): GameParts?
}

/**
 * Composition root and frame loop, running on the render (GL) thread (ARCHITECTURE.md
 * "Game loop"). Per [frame]: run queued tasks and [UiCommand]s -> real-time timers/animations
 * and the quality ratchet -> unless paused, main.js `update()` (hit-stop, simulation step with
 * the music beat, event routing, views, camera) -> render -> publish [UiState] if it changed.
 *
 * Until [GameBoot] delivers the parts (one-time WebView storage migration), frames only clear
 * the screen to the sky colour and commands stay queued; [UiState.booted] is false.
 */
class BirdyGame(
    private val services: PlatformServices,
    private val renderer: RenderBackend,
    val strings: Strings,
    private val boot: GameBoot,
) {
    /** Eager construction (tests, screenshots): everything is ready immediately. */
    constructor(
        services: PlatformServices,
        renderer: RenderBackend,
        progress: ProgressRepository,
        strings: Strings,
        audio: GameAudio,
        rng: Random = Random.Default,
        views: GameViews = GameViews(),
        uiRandom: Random = Random.Default,
    ) : this(services, renderer, strings, GameBoot { GameParts(progress, audio, views, rng, uiRandom) })

    private val commands = Channel<UiCommand>(Channel.UNLIMITED)
    private val tasks = Channel<() -> Unit>(Channel.UNLIMITED)
    private val effectChannel = Channel<UiEffect>(Channel.UNLIMITED)
    private val uiState = MutableStateFlow(UiState(lang = strings.lang.value))

    val ui: StateFlow<UiState> = uiState
    /** One-off effects for the app shell; collect on the UI thread. */
    val effects: Flow<UiEffect> = effectChannel.receiveAsFlow()

    val scene = Scene().apply {
        background = Color.hex(SKY)
    }
    val camera = PerspectiveCamera(60f, 1f, 0.1f, 400f)

    private var session: GameSession? = null
    private val pendingViews = ArrayList<SceneView>()
    private var lastNanos = -1L
    private var width = 0
    private var height = 0
    private var density = 1.0

    val isBooted: Boolean get() = session != null

    /** The running session (after boot). */
    val game: GameSession get() = session ?: error("BirdyGame not booted yet")
    val sim: GameSimulation get() = game.sim
    val progress: ProgressRepository get() = game.progress
    val audio: GameAudio get() = game.audio

    /** Adds an extra visual layer (world/bird/fx come from [GameParts.views]). */
    fun addView(view: SceneView) {
        val s = session
        if (s != null) s.addView(view) else pendingViews += view
    }

    /**
     * Thread-safe: enqueue input for the next frame. A system back press before boot is dropped:
     * queued, it would resolve against the fresh start menu and exit the app the moment the
     * splash ends.
     */
    fun post(command: UiCommand) {
        if (command == UiCommand.Back && !uiState.value.booted) return
        commands.trySend(command)
    }

    /**
     * Thread-safe: runs [block] on the game thread at the start of the next frame. Platform
     * callbacks (billing, ads) use this to touch progress/simulation state safely.
     */
    fun runOnGameThread(block: () -> Unit) {
        tasks.trySend(block)
    }

    /**
     * Device pixels per density-independent pixel (Android `displayMetrics.density`, the JS
     * `devicePixelRatio`). Scales the swipe threshold and the quality tiers' resolution
     * (`QUALITY_DPR = [min(dpr, 2), 1.5, 1.25, 1]`). Game thread or before the first frame.
     */
    var displayDensity: Double
        get() = density
        set(value) {
            density = value
            session?.density = value
        }

    fun onSurfaceCreated() = renderer.onContextCreated()

    fun onSurfaceChanged(width: Int, height: Int) {
        this.width = width
        this.height = height
        renderer.setSurfaceSize(width, height)
        camera.aspect = width.toFloat() / height.coerceAtLeast(1)
        camera.updateProjection()
        session?.resize(width, height)
    }

    /** Runs one frame; [nanos] is a monotonic timestamp (Choreographer / System.nanoTime). */
    fun frame(nanos: Long) {
        val rawDt = if (lastNanos < 0) 0.0 else ((nanos - lastNanos) / 1e9).coerceAtLeast(0.0)
        lastNanos = nanos
        val s = ensureBooted()
        if (s == null) {
            renderer.render(scene, camera)
            return
        }
        drain(s)
        s.tickRealtime(rawDt)
        if (!s.sim.state.paused) s.update(rawDt, s.audio.musicBeat())
        s.render()
        publish(s)
    }

    /**
     * Headless stepping like JS `advance(seconds)`: `update(1/30)` steps (beat from game time)
     * with timers and animations advancing by the same amount, no rendering. Queued commands and
     * tasks are applied first. Screenshots and tests only.
     */
    fun advance(seconds: Double) {
        val s = ensureBooted() ?: return
        drain(s)
        var t = 0.0
        while (t < seconds) {
            s.tickRealtime(STEP)
            if (!s.sim.state.paused) s.update(STEP, null)
            t += STEP
        }
        publish(s)
    }

    /** Renders the current frame into [target] (headless) and publishes the UI state. */
    fun renderTo(target: RenderTarget?) {
        val s = ensureBooted() ?: return
        s.render(target)
        publish(s)
    }

    /**
     * Applies [command] immediately on the calling thread. Only for moments when the game thread
     * is stopped (Android `onPause` after the GL thread paused), where a posted command would not
     * run until the app comes back (e.g. suspending the music when the app goes to background).
     */
    fun handleWhileStopped(command: UiCommand) {
        val s = session ?: return
        s.handle(command)
        publish(s)
    }

    /** Stops the audio thread for good (the app is finishing). */
    fun release() {
        (session?.audio as? SynthAudio)?.release()
    }

    /** Takes the next pending effect without a collector (tests, headless tools). */
    fun pollEffect(): UiEffect? = effectChannel.tryReceive().getOrNull()

    /** Applies queued tasks and commands now (tests, screenshots). */
    fun flush() {
        val s = ensureBooted() ?: return
        drain(s)
        publish(s)
    }

    private fun ensureBooted(): GameSession? {
        session?.let { return it }
        val parts = boot.tryBoot() ?: return null
        val s = GameSession(
            services = services,
            renderer = renderer,
            strings = strings,
            progress = parts.progress,
            audio = parts.audio,
            views = parts.views,
            scene = scene,
            camera = camera,
            simRandom = parts.simRandom,
            uiRandom = parts.uiRandom,
            emitEffect = { effectChannel.trySend(it) },
        )
        s.density = density
        if (width > 0 && height > 0) s.resize(width, height)
        pendingViews.forEach { s.addView(it) }
        pendingViews.clear()
        session = s
        return s
    }

    private fun drain(s: GameSession) {
        while (true) {
            val task = tasks.tryReceive().getOrNull() ?: break
            task()
            s.routeEvents()
        }
        while (true) {
            val cmd = commands.tryReceive().getOrNull() ?: break
            s.handle(cmd)
        }
    }

    private fun publish(s: GameSession) {
        val next = s.snapshot(uiState.value)
        if (next != uiState.value) uiState.value = next
    }

    companion object {
        /** JS `advance()` step. */
        const val STEP = 1.0 / 30
        /** Park sky horizon, shown before the world is built. */
        const val SKY = 0xa6e4ea
    }
}
