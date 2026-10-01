package de.robinrehbein.birdy.platform.ads

import de.robinrehbein.birdy.platform.Clock
import de.robinrehbein.birdy.platform.KeyValueStore
import de.robinrehbein.birdy.platform.StorageKeys

/** Counts completed flight time between automatic ads and limits shown ads per local day. */
class InterstitialPacing(
    private val storage: KeyValueStore,
    private val clock: Clock,
) {
    private data class State(val day: String = "", val count: Int = 0, val flightMillis: Long = 0)

    private var state = storage.getString(StorageKeys.INTERSTITIAL_PACING)
        ?.split('|', limit = 3)
        ?.takeIf { it.size == 3 }
        ?.let { fields ->
            val count = fields[1].toIntOrNull()
            val millis = fields[2].toLongOrNull()
            if (count != null && count >= 0 && millis != null && millis >= 0) {
                State(fields[0], count.coerceAtMost(DAILY_LIMIT), millis.coerceAtMost(INTERVAL_MS))
            } else null
        } ?: State()

    val shownToday: Int get() = if (state.day == clock.today().key()) state.count else 0

    fun onRunFinished(flightSeconds: Double) {
        if (!flightSeconds.isFinite() || flightSeconds <= 0.0) return
        val millis = (flightSeconds.coerceAtMost(INTERVAL_MS / 1000.0) * 1000).toLong()
        state = state.copy(flightMillis = (state.flightMillis + millis).coerceAtMost(INTERVAL_MS))
        save()
    }

    fun canShow(adReady: Boolean, adFree: Boolean): Boolean =
        adReady && !adFree && shownToday < DAILY_LIMIT && state.flightMillis >= INTERVAL_MS

    /** Call only after the SDK confirms that an ad appeared on screen. */
    fun markShown() {
        if (shownToday >= DAILY_LIMIT) return
        state = State(clock.today().key(), shownToday + 1, 0)
        save()
    }

    private fun save() {
        storage.putString(StorageKeys.INTERSTITIAL_PACING, "${state.day}|${state.count}|${state.flightMillis}")
    }

    companion object {
        const val INTERVAL_MS = 5L * 60L * 1000L
        const val DAILY_LIMIT = 3
    }
}
