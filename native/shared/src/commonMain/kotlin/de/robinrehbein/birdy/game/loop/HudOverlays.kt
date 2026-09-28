package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.HandMode
import de.robinrehbein.birdy.game.HandUi
import de.robinrehbein.birdy.game.PopupUi
import de.robinrehbein.birdy.game.TapFxUi
import de.robinrehbein.birdy.game.ZoneBannerUi
import de.robinrehbein.birdy.game.ZonesHint
import de.robinrehbein.birdy.game.ZonesHintUi
import kotlin.math.floor
import kotlin.math.max

/**
 * One-shot HUD animations whose CSS keyframes run in real time: the near-miss popup (0.8 s),
 * the zone banner (2.4 s) and the pool of four tap ripples (0.35 s).
 */
class HudFx {
    private var popupId = 0
    private var bannerId = 0
    private var tapId = 0
    var popup: PopupUi? = null
        private set
    var banner: ZoneBannerUi? = null
        private set
    private val taps = ArrayList<TapFxUi>(TAP_POOL)

    val tapFx: List<TapFxUi> get() = taps.toList()

    fun popup(text: String, x: Double, y: Double) {
        popup = PopupUi(++popupId, text, x.toFloat(), y.toFloat(), 0f)
    }

    fun banner(zoneLabel: String, name: String) {
        banner = ZoneBannerUi(++bannerId, zoneLabel, name, 0f)
    }

    fun tap(x: Double, y: Double, dir: Int) {
        if (taps.size >= TAP_POOL) taps.removeAt(0) // reuse the oldest ripple
        taps += TapFxUi(++tapId, x.toFloat(), y.toFloat(), dir, 0f)
    }

    fun update(dt: Double) {
        val d = dt.toFloat()
        popup = popup?.let { if (it.age + d >= POPUP_S) null else it.copy(age = it.age + d) }
        banner = banner?.let { if (it.age + d >= BANNER_S) null else it.copy(age = it.age + d) }
        if (taps.isNotEmpty()) {
            for (i in taps.indices.reversed()) {
                val t = taps[i]
                if (t.age + d >= TAP_S) taps.removeAt(i) else taps[i] = t.copy(age = t.age + d)
            }
        }
    }

    fun clear() {
        popup = null
        banner = null
        taps.clear()
    }

    companion object {
        const val TAP_POOL = 4
        const val POPUP_S = 0.8f
        const val BANNER_S = 2.4f
        const val TAP_S = 0.35f
    }
}

/**
 * `updateHand()`: a tap at the bird for "flap"; for "side" a swipe that starts at the bird and
 * slides [SWIPE_DX] of the screen width sideways, alternating right and left each [SWIPE_S]
 * cycle ([timeS] = real seconds). The label stays under the bird.
 */
object TutorialHand {
    const val SWIPE_S = 1.6
    const val SWIPE_DX = 0.24

    fun ui(mode: HandMode, birdScreenX: Double, birdScreenY: Double, timeS: Double, label: String): HandUi? {
        if (mode == HandMode.None) return null
        val y = (birdScreenY + 0.06).toFloat()
        if (mode != HandMode.Side) return HandUi(birdScreenX.toFloat(), y, "flap", label)
        val cycle = floor(max(0.0, timeS) / SWIPE_S)
        val p = max(0.0, timeS) / SWIPE_S - cycle
        val dir = if (cycle.toLong() % 2 == 0L) 1 else -1
        val (slide, alpha) = swipePhase(p)
        return HandUi(birdScreenX.toFloat(), y, "side", label, (dir * SWIPE_DX * slide).toFloat(), alpha.toFloat())
    }

    /** Slide progress (0..1, eased) and opacity at cycle phase [p]: press, swipe, fade out. */
    fun swipePhase(p: Double): Pair<Double, Double> = when {
        p < 0.2 -> 0.0 to p / 0.2
        p < 0.7 -> {
            val u = (p - 0.2) / 0.5
            u * u * (3 - 2 * u) to 1.0
        }
        else -> 1.0 to ((1 - p) / 0.3).coerceIn(0.0, 1.0)
    }
}

/**
 * `updateZonesOverlay()`: only touches the overlay when the lane geometry changes (memo key
 * `b1|b2|lane` with 3 decimals), so the one-shot fade is never restarted by per-frame updates.
 */
class ZonesOverlay {
    private var key = ""
    private var cached: ZonesHintUi? = null
    private var age = 0f
    private var ageSerial = -1

    /** Forces the next [update] to rebuild (language change, tutorial reveal). */
    fun invalidate() {
        key = ""
    }

    /** Advances the `show` keyframes (2.6 s, real time like the CSS animation). */
    fun tick(dt: Double) {
        if (age < FLASH_S) age = minOf(FLASH_S, age + dt.toFloat())
    }

    fun update(hint: ZonesHint, serial: Int, lane: Int, bounds: () -> DoubleArray, flapLabel: String, moveLabel: String): ZonesHintUi? {
        if (hint == ZonesHint.None) {
            cached = null
            return null
        }
        if (serial != ageSerial) {
            ageSerial = serial
            age = 0f
        }
        val flashAge = if (hint == ZonesHint.Show) age else 0f
        val b = bounds()
        val k = memoKey(b[0], b[1], lane)
        val c = cached
        if (k == key && c != null && c.hold == (hint == ZonesHint.Hold) && c.serial == serial && c.flapLabel == flapLabel) {
            return if (c.age == flashAge) c else c.copy(age = flashAge).also { cached = it }
        }
        key = k
        return ZonesHintUi(
            key = k,
            edges = listOf(b[0].toFloat(), b[1].toFloat()),
            flash = hint == ZonesHint.Show,
            hold = hint == ZonesHint.Hold,
            lane = lane,
            serial = serial,
            flapLabel = flapLabel,
            moveLabel = moveLabel,
            age = flashAge,
        ).also { cached = it }
    }

    companion object {
        /** `#zones.show` keyframes: labels hold to 60 %, then fade out by 2.6 s. */
        const val FLASH_S = 2.6f

        /** JS `${b1.toFixed(3)}|${b2.toFixed(3)}|${lane}`. */
        fun memoKey(b1: Double, b2: Double, lane: Int): String = "${fixed3(b1)}|${fixed3(b2)}|$lane"

        private fun fixed3(v: Double): String {
            val scaled = kotlin.math.round(v * 1000).toLong()
            val neg = scaled < 0
            val a = kotlin.math.abs(scaled)
            val s = "${a / 1000}.${(a % 1000).toString().padStart(3, '0')}"
            return if (neg) "-$s" else s
        }
    }
}
