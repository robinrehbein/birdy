package de.robinrehbein.birdy.game.bot

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.game.GameState
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.sim.PlantTiming
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BotGoldenTest {
    private val g = Golden.json("meta-bot.json").jsonObject
    private fun num(e: kotlinx.serialization.json.JsonElement, k: String) = e.jsonObject[k]!!.jsonPrimitive.double

    @Test
    fun constantsAndSkills() {
        val c = g["constants"]!!
        assertEquals(num(c, "GRAVITY"), BotConst.GRAVITY)
        assertEquals(num(c, "FLAP"), BotConst.FLAP)
        assertEquals(num(c, "HOP"), BotConst.HOP)
        assertEquals(num(c, "BPM"), BotConst.BPM)
        for ((name, v) in g["skills"]!!.jsonObject) {
            val s = BotSkill.byName(name)
            val o = v.jsonObject
            assertEquals(o["plan2"]!!.jsonPrimitive.boolean, s.plan2, name)
            assertEquals(num(v, "delay"), s.delay, name)
            assertEquals(num(v, "anticipate"), s.anticipate, name)
            assertEquals(num(v, "interval"), s.interval, name)
            assertEquals(num(v, "noise"), s.noise, name)
            assertEquals(num(v, "lookAhead"), s.lookAhead, name)
            assertEquals(num(v, "mistake"), s.mistake, name)
            assertEquals(o["predict"]!!.jsonPrimitive.boolean, s.predict, name)
            assertEquals(o["lateSwitch"]!!.jsonPrimitive.boolean, s.lateSwitch, name)
            assertEquals(num(v, "apexCheck"), s.apexCheck, name)
        }
    }

    @Test
    fun botPlantRiseIsItsOwnLinearCopy() {
        // The generator accumulated `beat += 0.1` and printed it rounded: replay the accumulation.
        var beat = 0.0
        for (s in g["plantRiseSamples"]!!.jsonArray) {
            assertEquals(num(s, "beat"), kotlin.math.round(beat * 100) / 100, "beat")
            assertTrue(abs(num(s, "rise") - BotConst.plantRise(beat)) < 1e-9, "rise($beat)")
            beat += 0.1
        }
        // Not world.js's smoothstep curve: the bot drops instantly at 3.9 and ramps linearly.
        assertEquals(1.0, BotConst.plantRise(3.6))
        assertTrue(PlantTiming.plantRise(3.6) < 1.0)
        assertEquals(0.5, BotConst.plantRise(2.2), 1e-12)
    }

    @Test
    fun pickLaneCosts() {
        val ex = g["pickLaneCostExample"]!!.jsonObject
        val st = ex["state"]!!
        val lanes = ex["lanes"]!!.jsonArray
        val row = GateRow()
        lanes.forEachIndexed { i, l ->
            row.lanes[i].gapLow = num(l, "gapLow")
            row.lanes[i].gapHigh = num(l, "gapHigh")
            row.lanes[i].hasPlant = l.jsonObject["hasPlant"]!!.jsonPrimitive.boolean
            row.lanes[i].amp = num(l, "amp")
        }
        val birdLane = st.jsonObject["lane"]!!.jsonPrimitive.int
        val y = num(st, "y")
        for (c in ex["costs"]!!.jsonArray) {
            val i = c.jsonObject["lane"]!!.jsonPrimitive.int
            assertEquals(num(c, "cost"), Bot.laneCost(row.lanes[i], i, birdLane, y), "cost lane $i")
        }
        val state = GameState().apply { lane = birdLane; this.y = y }
        assertEquals(0, Bot(BotSkill.PRO, rng = Random(1)).pickLane(row, state))
    }

    @Test
    fun flapDecisionFormulas() {
        for (s in g["flapDecisionSamples"]!!.jsonArray) {
            val y = num(s, "y")
            val vy = num(s, "vy")
            assertEquals(num(s, "apex"), Bot.flapApex(y))
            assertTrue(abs(num(s, "predictedY_react0_15") - Bot.predictY(y, vy, 0.15)) < 5.1e-5)
            assertTrue(abs(num(s, "hopApex_vyOrHop") - Bot.hopApex(y, vy)) < 5.1e-5)
        }
    }

    @Test
    fun aimClamp() {
        // Wide band: want is clamped 1 unit inside; narrow band: the middle.
        assertEquals(4.0, Bot.aim(3.0, 9.0, 3.2, 0.0))
        assertEquals(8.0, Bot.aim(3.0, 9.0, 12.0, 0.0))
        assertEquals(5.5, Bot.aim(5.0, 6.0, 0.0, 0.0))
        assertEquals(9.0, Bot.aim(3.0, 9.0, 8.0, 3.0), "offset still clamped to the band")
    }
}
