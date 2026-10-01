package de.robinrehbein.birdy.platform.ads

import de.robinrehbein.birdy.platform.FakeClock
import de.robinrehbein.birdy.platform.LocalDay
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterstitialPacingTest {
    @Test
    fun waitsForFiveMinutesOfCompletedFlightTime() {
        val pacing = InterstitialPacing(MemoryKeyValueStore(), FakeClock())
        pacing.onRunFinished(299.0)
        assertFalse(pacing.canShow(adReady = true, adFree = false))
        pacing.onRunFinished(1.0)
        assertTrue(pacing.canShow(adReady = true, adFree = false))
        pacing.markShown()
        assertFalse(pacing.canShow(adReady = true, adFree = false))
    }

    @Test
    fun requiresAnAvailableAdAndRespectsPermanentAdFreePurchase() {
        val pacing = InterstitialPacing(MemoryKeyValueStore(), FakeClock())
        pacing.onRunFinished(1200.0)
        assertFalse(pacing.canShow(adReady = false, adFree = false))
        assertFalse(pacing.canShow(adReady = true, adFree = true))
        assertTrue(pacing.canShow(adReady = true, adFree = false))
    }

    @Test
    fun capsAtThreePerLocalDayAndResetsTheCountTomorrow() {
        val clock = FakeClock(day = LocalDay(2026, 9, 29))
        val store = MemoryKeyValueStore()
        val pacing = InterstitialPacing(store, clock)
        repeat(3) {
            pacing.onRunFinished(1200.0)
            assertTrue(pacing.canShow(adReady = true, adFree = false))
            pacing.markShown()
        }
        pacing.onRunFinished(1200.0)
        assertFalse(pacing.canShow(adReady = true, adFree = false))
        clock.day = LocalDay(2026, 9, 30)
        assertTrue(pacing.canShow(adReady = true, adFree = false))
        assertEquals(3, InterstitialPacing(store, FakeClock(day = LocalDay(2026, 9, 29))).shownToday)
    }

    @Test
    fun persistsProgressButDoesNotCountAnAdThatFailedToShow() {
        val store = MemoryKeyValueStore()
        val clock = FakeClock(day = LocalDay(2026, 9, 29))
        InterstitialPacing(store, clock).onRunFinished(1200.0)
        val restored = InterstitialPacing(store, clock)
        assertTrue(restored.canShow(adReady = true, adFree = false))
        assertEquals(0, restored.shownToday)
        restored.markShown()
        assertEquals(1, InterstitialPacing(store, clock).shownToday)
    }
}
