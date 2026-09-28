package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.engine.RenderBackend
import de.robinrehbein.birdy.meta.Settings
import kotlin.math.min

/**
 * `adaptQuality(rawDt)` (main-b.md §15, main-a.md §2.1): a one-way ratchet. Every 1.5 s window
 * measures fps; while playing (not paused) and after a 3 s cooldown, two slow windows (< 48 fps)
 * in a row lower the tier. Tiers 1..3 lower the pixel ratio (1.5 / 1.25 / 1), tier 4 also turns
 * shadows off. The tier is persisted in `birdy-quality` and never raised again.
 */
class QualityController(private val settings: Settings, private val backend: RenderBackend?) {
    var quality: Int = settings.quality
        private set
    /** Device pixels per CSS pixel (devicePixelRatio); set by the shell. */
    var density = 1.0
        set(value) {
            field = value
            apply()
        }
    var fps = 0.0
        private set

    private var frames = 0
    private var time = 0.0
    private var cooldown = 3.0
    private var slow = 0

    init {
        apply()
    }

    /** JS `QUALITY_DPR = [min(dpr, 2), 1.5, 1.25, 1]`. */
    fun dprSteps(): DoubleArray = doubleArrayOf(min(density, 2.0), 1.5, 1.25, 1.0)

    /** Pixel ratio of the current tier: `min(DPR[0], DPR[min(q, 3)])`. */
    fun pixelRatio(): Double {
        val d = dprSteps()
        return min(d[0], d[min(quality, 3)])
    }

    /** Surface fraction to render at (the native surface is at full device resolution). */
    fun resolutionScale(): Float = (pixelRatio() / density).coerceIn(0.1, 1.0).toFloat()

    fun apply() {
        val b = backend ?: return
        b.resolutionScale = resolutionScale()
        b.shadowsEnabled = quality < STEPS
    }

    /**
     * Counts one frame of [rawDt]. [judge] = playing and not paused. Returns true when a window
     * closed (fps updated).
     */
    fun tick(rawDt: Double, judge: Boolean): Boolean {
        frames++
        time += rawDt
        if (time < WINDOW) return false
        fps = frames / time
        frames = 0
        time = 0.0
        cooldown -= WINDOW
        // Only judge real gameplay, and give each step time to settle.
        if (!judge || cooldown > 0) return true
        // Two slow windows in a row, so a single hiccup doesn't lower quality.
        slow = if (fps < SLOW_FPS) slow + 1 else 0
        if (slow < 2 || quality >= STEPS) return true
        slow = 0
        quality++
        cooldown = 3.0
        apply()
        runCatching { settings.quality = quality }
        return true
    }

    companion object {
        /** `QUALITY_DPR.length`. */
        const val STEPS = 4
        const val WINDOW = 1.5
        const val SLOW_FPS = 48.0
    }
}
