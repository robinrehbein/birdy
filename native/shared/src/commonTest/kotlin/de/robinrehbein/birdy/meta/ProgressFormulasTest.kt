package de.robinrehbein.birdy.meta

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.platform.FakeClock
import de.robinrehbein.birdy.platform.LocalDay
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressFormulasTest {
    private val golden = Golden.json("meta-progress-formulas.json").jsonObject

    @Test
    fun giftAmountsByStreak() {
        val table = golden["gift"]!!.jsonObject["amountsByStreak"]!!.jsonObject
        val repo = LocalProgressRepository(MemoryKeyValueStore(), FakeClock())
        for ((streak, expected) in table) {
            assertEquals(expected.jsonPrimitive.int, repo.giftAmount(streak.toInt()), "streak $streak")
        }
    }

    @Test
    fun achievementsTableMatchesDeclarationOrder() {
        val table = golden["achievements"]!!.jsonObject["table"]!!.jsonArray
        assertEquals(21, Achievements.ALL.size)
        assertEquals(table.size, Achievements.ALL.size)
        table.forEachIndexed { i, el ->
            val o = el.jsonObject
            val a = Achievements.ALL[i]
            assertEquals(o["id"]!!.jsonPrimitive.content, a.id)
            assertEquals(o["stat"]!!.jsonPrimitive.content, a.stat)
            assertEquals(o["goal"]!!.jsonPrimitive.int, a.goal)
            assertEquals(o["reward"]!!.jsonPrimitive.int, a.reward)
        }
    }

    @Test
    fun rareSkinLinksMatch() {
        val links = golden["achievements"]!!.jsonObject["rareSkinLinks"]!!.jsonObject
        for ((achId, skinId) in links) {
            val a = Achievements.ALL.first { it.id == achId }
            assertEquals(skinId.jsonPrimitive.content, a.skin, achId)
        }
        assertEquals(7, Achievements.ALL.count { it.skin != null })
    }

    @Test
    fun upgradesTableAndPriceFormula() {
        val table = golden["upgrades"]!!.jsonObject["table"]!!.jsonArray
        assertEquals(table.size, Catalog.upgrades.size)
        val repo = LocalProgressRepository(MemoryKeyValueStore(), FakeClock())
        assertEquals(300, repo.upgradePrice("star"))
        assertEquals(0, repo.level("star"))
    }

    @Test
    fun rewardedAdsAndStylePassConstants() {
        val store = MemoryKeyValueStore()
        val clock = FakeClock(day = LocalDay(2026, 1, 1))
        val repo = LocalProgressRepository(store, clock)
        assertEquals(3, repo.rewardedAdsLeft)
        assertEquals(30, repo.grantRewardedCoins())
        assertEquals(2, repo.rewardedAdsLeft)
        assertEquals(30, repo.grantRewardedCoins())
        assertEquals(30, repo.grantRewardedCoins())
        assertEquals(0, repo.rewardedAdsLeft)
        assertEquals(0, repo.grantRewardedCoins())

        // grantStylePass shares the same 3/day pool and is now exhausted.
        assertEquals(false, repo.grantStylePass())
    }

    @Test
    fun stylePassMinutesLeftCeilsUp() {
        val store = MemoryKeyValueStore()
        val clock = FakeClock(day = LocalDay(2026, 1, 1))
        val repo = LocalProgressRepository(store, clock)
        assertEquals(true, repo.grantStylePass())
        assertEquals(60, repo.stylePassMinutesLeft)
        clock.millis += 59 * 60_000L + 1 // 59 min 1 ms elapsed -> 1 ms left in the last minute
        assertEquals(1, repo.stylePassMinutesLeft)
        clock.millis += 60_000L
        assertEquals(0, repo.stylePassMinutesLeft)
    }
}
