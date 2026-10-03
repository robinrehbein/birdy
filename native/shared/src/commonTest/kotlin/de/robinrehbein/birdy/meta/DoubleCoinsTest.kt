package de.robinrehbein.birdy.meta

import de.robinrehbein.birdy.platform.FakeClock
import de.robinrehbein.birdy.platform.LocalDay
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals

class DoubleCoinsTest {
    private val store = MemoryKeyValueStore()
    private val clock = FakeClock(day = LocalDay(2026, 1, 1))

    @Test
    fun grantsRunCoinsFiveTimesPerDayIndependentOfShopAds() {
        val repo = LocalProgressRepository(store, clock)
        assertEquals(5, repo.doubleCoinsAdsLeft)
        repeat(5) { assertEquals(12, repo.grantDoubleCoins(12)) }
        assertEquals(60, repo.data.value.coins)
        assertEquals(0, repo.doubleCoinsAdsLeft)
        assertEquals(0, repo.grantDoubleCoins(12))
        assertEquals(60, repo.data.value.coins)
        // The shop's 3/day pool is untouched.
        assertEquals(3, repo.rewardedAdsLeft)
        assertEquals(30, repo.grantRewardedCoins())
        assertEquals(2, repo.rewardedAdsLeft)
        assertEquals(0, repo.grantDoubleCoins(12))
    }

    @Test
    fun shopAdsDoNotUseUpTheDoubleCoinsLimit() {
        val repo = LocalProgressRepository(store, clock)
        repeat(3) { repo.grantRewardedCoins() }
        assertEquals(5, repo.doubleCoinsAdsLeft)
        assertEquals(7, repo.grantDoubleCoins(7))
        assertEquals(4, repo.doubleCoinsAdsLeft)
    }

    @Test
    fun nothingToGrantKeepsTheSlot() {
        val repo = LocalProgressRepository(store, clock)
        assertEquals(0, repo.grantDoubleCoins(0))
        assertEquals(5, repo.doubleCoinsAdsLeft)
    }

    @Test
    fun counterPersistsAndResetsNextDay() {
        val repo = LocalProgressRepository(store, clock)
        repeat(5) { repo.grantDoubleCoins(10) }
        val reloaded = LocalProgressRepository(store, clock)
        assertEquals(0, reloaded.doubleCoinsAdsLeft)
        assertEquals(50, reloaded.data.value.coins)
        clock.day = LocalDay(2026, 1, 2)
        assertEquals(5, reloaded.doubleCoinsAdsLeft)
        assertEquals(10, reloaded.grantDoubleCoins(10))
    }
}
