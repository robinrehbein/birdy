package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.meta.ProgressRepository
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.meta.epochDay
import de.robinrehbein.birdy.platform.Clock
import de.robinrehbein.birdy.platform.KeyValueStore
import de.robinrehbein.birdy.platform.LocalDay
import de.robinrehbein.birdy.platform.ReminderPermission
import de.robinrehbein.birdy.platform.ReminderPlan
import de.robinrehbein.birdy.platform.Reminders
import de.robinrehbein.birdy.platform.StorageKeys

/** Pure timing rules for the daily reminder. */
object ReminderTiming {
    const val DEFAULT_HOUR = 18
    /** Never earlier than 09:00 nor later than 21:30 local (whole hours, so 21:00 at the latest). */
    const val EARLIEST_HOUR = 9
    const val LATEST_HOUR = 21
    private const val DAY_MS = 86_400_000L
    private const val HOUR_MS = 3_600_000L

    /** Local hour of day (0-23) at [nowMillis] for a zone [offsetMillis] from UTC. */
    fun localHour(nowMillis: Long, offsetMillis: Long): Int =
        (((nowMillis + offsetMillis) % DAY_MS + DAY_MS) % DAY_MS / HOUR_MS).toInt()

    fun fireHour(typicalHour: Int?): Int = (typicalHour ?: DEFAULT_HOUR).coerceIn(EARLIEST_HOUR, LATEST_HOUR)

    /** Epoch millis of tomorrow (relative to [today]) at the reminder hour, local time. */
    fun fireAtMillis(today: LocalDay, offsetMillis: Long, typicalHour: Int?): Long =
        (today.epochDay() + 1) * DAY_MS + fireHour(typicalHour) * HOUR_MS - offsetMillis
}

/**
 * Decides when and what to remind: at most one local notification per day, tomorrow at the
 * player's typical play hour, re-planned after every finished run and when the app goes to the
 * background. Asks for the notification permission once, after the 3rd completed run.
 */
class ReminderController(
    private val storage: KeyValueStore,
    private val clock: Clock,
    private val reminders: Reminders,
    private val progress: ProgressRepository,
    private val strings: Strings,
) {
    var isOn: Boolean
        get() = storage.getString(StorageKeys.REMINDERS) != "0"
        private set(value) { storage.putString(StorageKeys.REMINDERS, if (value) "1" else "0") }

    private var asked: Boolean
        get() = storage.getString(StorageKeys.REMINDER_ASKED) == "1"
        set(value) { storage.putString(StorageKeys.REMINDER_ASKED, if (value) "1" else "0") }

    /** What the toggle shows: on unless switched off or the system permission was denied. */
    val shownOn: Boolean
        get() = isOn && !(asked && reminders.permissionState == ReminderPermission.Denied)

    /** [mayAsk] false postpones the permission prompt (e.g. an interstitial is about to show). */
    fun onRunFinished(mayAsk: Boolean = true) {
        val now = clock.nowMillis()
        val hour = ReminderTiming.localHour(now, clock.utcOffsetMillis(now))
        storage.putString(StorageKeys.REMINDER_HOUR, hour.toString())
        if (mayAsk && isOn && !asked && progress.data.value.runs >= PERMISSION_AFTER_RUNS) {
            asked = true
            reminders.requestPermission { granted -> if (granted) reschedule() }
        }
        reschedule()
    }

    fun onBackground() = reschedule()

    /**
     * Turning on while the system permission is denied asks again (Android may still show the
     * prompt once more). Returns [shownOn] afterwards, so the caller can tell "on" from "blocked".
     */
    fun switchTo(on: Boolean): Boolean {
        isOn = on
        if (on && asked && reminders.permissionState == ReminderPermission.Denied) {
            reminders.requestPermission { granted -> if (granted) reschedule() }
        }
        reschedule()
        return shownOn
    }

    /** Re-plans (or cancels) the single pending reminder. */
    fun reschedule() {
        if (!isOn) { reminders.cancelAll(); return }
        if (progress.data.value.runs < PERMISSION_AFTER_RUNS) return
        if (reminders.permissionState != ReminderPermission.Granted) return
        reminders.schedule(plan())
    }

    fun plan(): ReminderPlan {
        val now = clock.nowMillis()
        val typical = storage.getString(StorageKeys.REMINDER_HOUR)?.toIntOrNull()?.takeIf { it in 0..23 }
        return ReminderPlan(
            fireAtMillis = ReminderTiming.fireAtMillis(clock.today(), clock.utcOffsetMillis(now), typical),
            title = strings.t("remindTitle"),
            body = body(),
        )
    }

    /**
     * Gift claimed today => tomorrow's claim continues the running streak, and not coming back
     * tomorrow ends it. Otherwise the streak is already lost by tomorrow and the gift restarts at day 1.
     */
    private fun body(): String {
        val claimedToday = !progress.giftAvailable()
        val running = if (claimedToday) progress.streak else 0
        if (running >= 2) return strings.t("remindStreak", mapOf("n" to running))
        return strings.t("remindGift", mapOf("n" to progress.giftAmount(running + 1)))
    }

    companion object {
        const val PERMISSION_AFTER_RUNS = 3
    }
}
