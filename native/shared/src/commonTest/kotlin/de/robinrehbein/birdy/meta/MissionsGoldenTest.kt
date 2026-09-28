package de.robinrehbein.birdy.meta

import de.robinrehbein.birdy.Golden
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissionsGoldenTest {
    @Test
    fun seededRawDraws() {
        val golden = Golden.json("meta-missions.json").jsonObject["seededRawDraws"]!!.jsonObject
        for ((date, arr) in golden) {
            val rnd = Missions.Seeded(date)
            for (expected in arr.jsonArray) {
                val actual = rnd.next()
                assertTrue(abs(expected.jsonPrimitive.double - actual) < 1e-12, "seeded('$date'): expected $expected got $actual")
            }
        }
    }

    @Test
    fun dailyMissionsMatrix() {
        val golden = Golden.json("meta-missions.json").jsonObject["dailyMissions"]!!.jsonArray
        for (row in golden) {
            val o = row.jsonObject
            val date = o["date"]!!.jsonPrimitive.content
            val best = o["best"]!!.jsonPrimitive.int
            val expectedList = o["list"]!!.jsonArray
            val actual = Missions.dailyMissions(date, best)
            assertEquals(date, actual.date)
            assertEquals(expectedList.size, actual.list.size, "mission count for $date/$best")
            expectedList.forEachIndexed { i, expEl ->
                val exp = expEl.jsonObject
                val act = actual.list[i]
                assertEquals(exp["id"]!!.jsonPrimitive.content, act.id, "mission[$i].id for $date/$best")
                assertEquals(exp["goal"]!!.jsonPrimitive.int, act.goal, "mission[$i].goal for $date/$best")
                assertEquals(exp["reward"]!!.jsonPrimitive.int, act.reward)
            }
        }
    }

    /** Goals whose raw product is an exact .5 tie; expected values from progress.js (Math.round). */
    @Test
    fun scoreGoalRoundsHalfUpLikeJs() {
        val cases = listOf(
            Triple("2026-12-1", 15, 14), Triple("2026-2-2", 15, 17),
            Triple("2026-12-1", 25, 23), Triple("2026-2-2", 25, 28),
            Triple("2026-12-1", 35, 32), Triple("2026-2-2", 35, 39),
            Triple("2026-12-1", 45, 41), Triple("2026-2-2", 45, 50),
            Triple("2026-12-1", 95, 86), Triple("2026-2-2", 95, 105),
        )
        for ((date, best, goal) in cases) {
            val score = Missions.dailyMissions(date, best).list.single { it.id == "score" }
            assertEquals(goal, score.goal, "score goal for $date/best=$best")
        }
    }
}
