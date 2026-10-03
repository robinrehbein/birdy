package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.MissionUi
import de.robinrehbein.birdy.meta.LocalProgressRepository
import de.robinrehbein.birdy.meta.TableStrings
import de.robinrehbein.birdy.platform.FakeClock
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameOverTextsTest {
    private val store = MemoryKeyValueStore()
    private fun texts(lang: String = "de-DE") =
        MenuTexts(LocalProgressRepository(store, FakeClock()), TableStrings(store, lang))

    private fun m(id: String, progress: Int, goal: Int, done: Boolean = false) =
        MissionUi(id, id, 10, progress, goal, done)

    @Test
    fun closestMissionIsTheHighestUnfinishedRatio() {
        val t = texts()
        val list = listOf(m("a", 1, 10), m("b", 8, 10), m("c", 20, 20, done = true))
        assertEquals("b", t.closestMission(list)?.id)
        assertNull(t.closestMission(listOf(m("c", 20, 20, done = true))))
        assertNull(t.closestMission(emptyList()))
    }

    @Test
    fun doubleCoinsButtonEligibility() {
        val t = texts()
        val ok = t.doubleCoins(12, adReady = true, slotsLeft = 3, ownsRemoveAds = false, used = false, busy = false)
        assertNotNull(ok)
        assertTrue(ok.label.contains("+12") && ok.label.contains("Münzen"))
        assertEquals("+12 coins (ad)", texts("en-US").doubleCoins(12, true, 3, false, false, false)?.label)
        assertNull(t.doubleCoins(4, true, 3, false, false, false), "below 5 coins")
        assertNotNull(t.doubleCoins(5, true, 3, false, false, false))
        assertNull(t.doubleCoins(12, false, 3, false, false, false), "no ad ready")
        assertNull(t.doubleCoins(12, true, 0, false, false, false), "daily limit")
        assertNull(t.doubleCoins(12, true, 3, true, false, false), "remove-ads owners")
        assertNull(t.doubleCoins(12, true, 3, false, true, false), "already used")
        assertEquals(false, t.doubleCoins(12, true, 3, false, false, true)?.enabled)
    }
}
