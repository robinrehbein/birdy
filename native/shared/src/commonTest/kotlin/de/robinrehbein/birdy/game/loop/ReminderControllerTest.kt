package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.meta.LocalProgressRepository
import de.robinrehbein.birdy.meta.TableStrings
import de.robinrehbein.birdy.platform.FakeClock
import de.robinrehbein.birdy.platform.LocalDay
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import de.robinrehbein.birdy.platform.ReminderPermission
import de.robinrehbein.birdy.platform.ReminderPlan
import de.robinrehbein.birdy.platform.Reminders
import de.robinrehbein.birdy.platform.StorageKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeReminders(var permission: ReminderPermission = ReminderPermission.Granted) : Reminders {
    val plans = ArrayList<ReminderPlan>()
    var cancels = 0
    var prompts = 0
    var promptAnswer = true
    override val permissionState get() = permission
    override fun schedule(plan: ReminderPlan) { plans += plan }
    override fun cancelAll() { cancels++ }
    override fun requestPermission(onResult: (Boolean) -> Unit) {
        prompts++
        if (promptAnswer) permission = ReminderPermission.Granted
        onResult(promptAnswer)
    }
}

class ReminderControllerTest {
    private val day = LocalDay(2026, 1, 1)
    // 2026-01-01 14:30 UTC = 15:30 local in the UTC+1 zone used here.
    private val now = 1_767_277_800_000L
    private val jan2Midnight = 1_767_312_000_000L // 2026-01-02 00:00 UTC
    private val hour = 3_600_000L

    private class Rig(json: String, clock: FakeClock, lang: String) {
        val store = MemoryKeyValueStore(mapOf(StorageKeys.PROGRESS to json))
        val fake = FakeReminders()
        val progress = LocalProgressRepository(store, clock)
        val ctl = ReminderController(store, clock, fake, progress, TableStrings(store, lang))
    }

    private fun rig(json: String, lang: String = "de-DE") =
        Rig(json, FakeClock(now, day, offsetMillis = hour), lang)

    @Test
    fun defaultsToTomorrowAtSixPmLocal() {
        val plan = rig("""{"runs":3}""").ctl.plan()
        // 2026-01-02 18:00 at UTC+1 = 17:00 UTC
        assertEquals(jan2Midnight + 17 * hour, plan.fireAtMillis)
        assertEquals("Birdy", plan.title)
    }

    @Test
    fun usesLastPlayHourClampedToWindow() {
        assertEquals(9, ReminderTiming.fireHour(3))
        assertEquals(21, ReminderTiming.fireHour(23))
        assertEquals(14, ReminderTiming.fireHour(14))
        assertEquals(18, ReminderTiming.fireHour(null))
        val r = rig("""{"runs":3}""")
        r.ctl.onRunFinished() // finished at 15:30 local
        assertEquals("15", r.store.getString(StorageKeys.REMINDER_HOUR))
        assertEquals(jan2Midnight + 14 * hour, r.fake.plans.last().fireAtMillis) // 15:00 local = 14:00 UTC
    }

    @Test
    fun localHourHandlesOffsets() {
        assertEquals(15, ReminderTiming.localHour(now, hour))
        assertEquals(9, ReminderTiming.localHour(now, -5 * hour))
    }

    @Test
    fun streakTextWhenGiftClaimedToday() {
        val de = rig("""{"runs":5,"gift":{"last":"2026-1-1","streak":3}}""")
        assertEquals("Deine 3-Tage-Serie läuft heute ab!", de.ctl.plan().body)
        val en = rig("""{"runs":5,"gift":{"last":"2026-1-1","streak":3}}""", "en-US")
        assertEquals("Your 3-day streak ends today!", en.ctl.plan().body)
    }

    @Test
    fun giftTextUsesRealNextAmount() {
        // Claimed today with streak 1: tomorrow's claim is day 2.
        val a = rig("""{"runs":5,"gift":{"last":"2026-1-1","streak":1}}""")
        assertEquals("Dein Tagesgeschenk wartet (+${a.progress.giftAmount(2)} Münzen)", a.ctl.plan().body)
        // Not claimed today: the streak is gone by tomorrow, so the gift restarts at day 1.
        val b = rig("""{"runs":5,"gift":{"last":"2025-12-31","streak":4}}""")
        assertEquals("Dein Tagesgeschenk wartet (+${b.progress.giftAmount(1)} Münzen)", b.ctl.plan().body)
        val c = rig("""{"runs":5}""", "en-US")
        assertEquals("Your daily gift is waiting (+${c.progress.giftAmount(1)} coins)", c.ctl.plan().body)
    }

    @Test
    fun nothingScheduledBeforeThirdRunAndPermissionAskedOnce() {
        val r = rig("""{"runs":2}""")
        r.ctl.onRunFinished()
        r.ctl.onBackground()
        assertTrue(r.fake.plans.isEmpty())
        assertEquals(0, r.fake.prompts)

        val s = rig("""{"runs":3}""")
        s.fake.permission = ReminderPermission.Denied
        s.ctl.onRunFinished()
        assertEquals(1, s.fake.prompts)
        assertTrue(s.fake.plans.isNotEmpty())
        s.ctl.onRunFinished()
        assertEquals(1, s.fake.prompts)
    }

    @Test
    fun promptWaitsForAGameOverWithoutInterstitial() {
        val r = rig("""{"runs":3}""")
        r.fake.permission = ReminderPermission.Denied
        r.ctl.onRunFinished(mayAsk = false)
        assertEquals(0, r.fake.prompts)
        r.ctl.onRunFinished()
        assertEquals(1, r.fake.prompts)
    }

    @Test
    fun deniedPermissionIsRespected() {
        val r = rig("""{"runs":3}""")
        r.fake.permission = ReminderPermission.Denied
        r.fake.promptAnswer = false
        r.ctl.onRunFinished()
        r.ctl.onRunFinished()
        r.ctl.onBackground()
        assertEquals(1, r.fake.prompts)
        assertTrue(r.fake.plans.isEmpty())
        assertEquals(false, r.ctl.shownOn)
    }

    @Test
    fun switchingOnAfterDenialAsksAgain() {
        val r = rig("""{"runs":3}""")
        r.fake.permission = ReminderPermission.Denied
        r.fake.promptAnswer = false
        r.ctl.onRunFinished()
        assertEquals(false, r.ctl.shownOn)
        // Still denied: the toggle reports "blocked" instead of claiming reminders are on.
        assertEquals(false, r.ctl.switchTo(true))
        assertEquals(2, r.fake.prompts)
        assertTrue(r.fake.plans.isEmpty())
        // Granted on the re-ask: reminders come back and get scheduled.
        r.fake.promptAnswer = true
        assertEquals(true, r.ctl.switchTo(true))
        assertEquals(3, r.fake.prompts)
        assertTrue(r.fake.plans.isNotEmpty())
        // Switching off never prompts.
        assertEquals(false, r.ctl.switchTo(false))
        assertEquals(3, r.fake.prompts)
    }

    @Test
    fun toggleOffCancelsAndPersists() {
        val r = rig("""{"runs":4}""")
        r.ctl.onBackground()
        assertEquals(1, r.fake.plans.size)
        r.ctl.switchTo(false)
        assertEquals(1, r.fake.cancels)
        assertEquals("0", r.store.getString(StorageKeys.REMINDERS))
        r.ctl.onBackground()
        r.ctl.onRunFinished()
        assertEquals(1, r.fake.plans.size)
        assertEquals(0, r.fake.prompts)
        r.ctl.switchTo(true)
        assertEquals(2, r.fake.plans.size)
    }
}
