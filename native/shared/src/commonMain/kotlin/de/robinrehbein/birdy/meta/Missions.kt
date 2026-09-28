package de.robinrehbein.birdy.meta

import de.robinrehbein.birdy.game.jsRound

/** One entry of `MISSION_POOL` (progress.js lines 41-51, meta.md §1.7.1). */
data class MissionTemplate(
    val id: String,
    val stat: String,
    val per: MissionPer,
    val goals: List<Int>,
    val minBest: Int = 0,
    val text: (Int) -> LocalizedText,
)

enum class MissionPer { RUN, DAY }

object Missions {
    val REWARDS = listOf(40, 70, 120)

    val POOL: List<MissionTemplate> = listOf(
        MissionTemplate("coins", "coins", MissionPer.DAY, listOf(20, 40, 70)) { n ->
            LocalizedText("Sammle $n Münzen", "Collect $n coins")
        },
        MissionTemplate("score", "score", MissionPer.RUN, listOf(10, 20, 35)) { n ->
            LocalizedText("Erreiche $n Punkte in einem Flug", "Score $n in one flight")
        },
        MissionTemplate("rows", "score", MissionPer.DAY, listOf(30, 60, 100)) { n ->
            LocalizedText("Flieg durch $n Röhren", "Fly through $n pipes")
        },
        MissionTemplate("powers", "powerups", MissionPer.DAY, listOf(2, 4, 6), minBest = 8) { n ->
            LocalizedText("Schnapp dir $n Power-ups", "Grab $n power-ups")
        },
        MissionTemplate("runs", "runs", MissionPer.DAY, listOf(3, 5, 8)) { n ->
            LocalizedText("Spiele $n Runden", "Play $n rounds")
        },
        MissionTemplate("plants", "plants", MissionPer.DAY, listOf(3, 6, 10), minBest = 14) { n ->
            LocalizedText("Flieg an $n Kakteen vorbei", "Pass $n spiky cacti")
        },
        MissionTemplate("moving", "moving", MissionPer.DAY, listOf(4, 8, 14), minBest = 10) { n ->
            LocalizedText("Durchquere $n bewegte Lücken", "Fly through $n moving gaps")
        },
        MissionTemplate("star", "starRows", MissionPer.DAY, listOf(3, 6, 10), minBest = 12) { n ->
            LocalizedText("Als Regenbogen durch $n Reihen", "Pass $n rows as a rainbow")
        },
    )

    fun byId(id: String): MissionTemplate = POOL.first { it.id == id }

    /**
     * progress.js `seeded(str)` (meta.md §1.7.2): FNV-1a to seed a 32-bit state, then a
     * MurmurHash3-finalizer-style mixer as the generator. Must be bit-exact: `Int * Int` on the
     * JVM already wraps mod 2^32 like `Math.imul`.
     */
    class Seeded(str: String) {
        private var h: Int = run {
            var acc = 2166136261L.toInt()
            for (c in str) acc = imul(acc xor c.code, 16777619)
            acc
        }

        fun next(): Double {
            h = imul(h xor (h ushr 15), 2246822507L.toInt())
            h = imul(h xor (h ushr 13), 3266489909L.toInt())
            h = h xor (h ushr 16)
            return (h.toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
        }

        private fun imul(a: Int, b: Int): Int = a * b
    }

    /**
     * `dailyMissions(date, best)` (progress.js lines 75-86, meta.md §1.7.3): 3 missions drawn
     * without replacement from the eligible pool, seeded purely by the date string.
     */
    fun dailyMissions(date: String, best: Int): MissionDay {
        val rnd = Seeded(date)
        val pool = POOL.filter { best >= it.minBest }.toMutableList()
        val list = mutableListOf<Mission>()
        for (tier in 0 until 3) {
            val idx = (rnd.next() * pool.size).toInt().coerceIn(0, pool.size - 1)
            val m = pool.removeAt(idx)
            var goal = m.goals[tier]
            if (m.id == "score") {
                val mult = listOf(0.6, 0.9, 1.1)[tier]
                // JS Math.round rounds .5 ties up; kotlin.math.round would round them to even.
                goal = maxOf(5, jsRound(maxOf(8, best) * mult))
            }
            list.add(Mission(id = m.id, goal = goal, progress = 0, reward = REWARDS[tier], done = false))
        }
        return MissionDay(date, list)
    }
}
