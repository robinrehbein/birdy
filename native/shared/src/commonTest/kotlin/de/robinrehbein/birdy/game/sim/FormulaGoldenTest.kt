package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.Tuning
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun JsonElement.o(key: String) = jsonObject[key]!!
internal fun JsonElement.d(key: String) = o(key).jsonPrimitive.double
internal fun JsonElement.i(key: String) = o(key).jsonPrimitive.int
internal fun JsonElement.b(key: String) = o(key).jsonPrimitive.boolean
internal fun JsonElement.arr(key: String) = o(key).jsonArray

/** Values the generators rounded to 6 decimals. */
internal fun assertRounded(expected: Double, actual: Double, msg: String) =
    assertTrue(abs(expected - actual) <= 5.1e-7, "$msg: expected $expected, got $actual")

class FormulaGoldenTest {
    private fun powerType(id: String) = PowerType.entries.first { it.id == id }

    @Test
    fun difficultyCurveMainA() {
        for (row in Golden.json("main-a-difficulty.json").arr("rows")) {
            val score = row.i("score")
            assertEquals(row.d("difficulty"), Difficulty.difficulty(score), "difficulty($score)")
            assertEquals(row.d("baseSpeed"), Difficulty.baseSpeed(score), "baseSpeed($score)")
            assertEquals(row.d("spacing"), Difficulty.spacing(score), "spacing($score)")
        }
    }

    @Test
    fun difficultyCurveMainB() {
        for (row in Golden.json("main-b-difficulty-curve.json").jsonArray) {
            val score = row.i("score")
            assertEquals(row.d("difficulty"), Difficulty.difficulty(score))
            assertEquals(row.d("baseSpeed"), Difficulty.baseSpeed(score), "baseSpeed($score)")
            assertEquals(row.d("spacing"), Difficulty.spacing(score), "spacing($score)")
        }
    }

    @Test
    fun powerDurationsAndMagnet() {
        val a = Golden.json("main-a-powerups.json")
        for (row in a.arr("durationRows")) {
            assertEquals(row.d("duration"), Tuning.powerDuration(powerType(row.o("type").jsonPrimitive.content), row.i("level")))
        }
        for (row in a.arr("magnetRangeRows")) assertEquals(row.d("magnetRange"), Tuning.magnetRange(row.i("level")))
        for (t in PowerType.entries) {
            assertEquals(a.o("baseDurations").d(t.id), t.baseDuration)
            assertEquals(a.o("upgradeBonus").d(t.id), Tuning.upgradeBonus(t))
        }
        for (row in Golden.json("main-b-power-durations.json").jsonArray) {
            assertEquals(row.d("duration"), Tuning.powerDuration(powerType(row.o("type").jsonPrimitive.content), row.i("level")))
        }
        for (row in Golden.json("main-b-magnet-ranges.json").jsonArray) {
            assertEquals(row.d("range"), Tuning.magnetRange(row.i("level")))
        }
    }

    @Test
    fun powerDurationUsesProgressLevels() {
        val h = SimHarness(progress = FakeProgress(levels = mutableMapOf("star" to 2, "magnet" to 3)))
        assertEquals(9.0, h.sim.powerDuration(PowerType.Star))
        assertEquals(18.0, h.sim.powerDuration(PowerType.Magnet))
        assertEquals(9.0, h.sim.powerDuration(PowerType.Mini))
        assertEquals(15.0, h.sim.magnetRange())
    }

    @Test
    fun constantsMainB() {
        val c = Golden.json("main-b-constants.json")
        for (t in PowerType.entries) assertEquals(c.o("UPGRADE_BONUS").d(t.id), Tuning.upgradeBonus(t))
        assertEquals(c.d("MAGNET_RANGE"), Tuning.MAGNET_RANGE)
        assertEquals(c.d("STAR_SPEED_BOOST"), Tuning.STAR_SPEED_BOOST)
        assertEquals(c.d("GRACE_TIME"), Tuning.GRACE_TIME)
        assertEquals(c.d("HIT_STOP"), Tuning.HIT_STOP)
        assertEquals(c.d("NEAR_MISS"), Tuning.NEAR_MISS)
        assertEquals(c.i("TUT_SWITCH_ROW"), Tuning.TUT_SWITCH_ROW)
        // NEAR_BIRD (tap-near-the-bird flap zone) is gone: swipe controls ignore where a touch lands.
    }

    @Test
    fun reachabilityMainA() {
        val g = Golden.json("main-a-reachability.json")
        assertEquals(g.o("constants").d("SWITCH_FACTOR"), Tuning.SWITCH_FACTOR)
        for (c in g.arr("cases")) {
            val r = Reach.atScore(c.i("score"))
            assertEquals(c.d("t"), r.t, "t")
            assertEquals(c.d("maxDrop"), r.maxDrop, "maxDrop")
            assertEquals(c.d("maxRise"), r.maxRise, "maxRise")
            assertEquals(c.b("reachable"), r.reach(0.0, c.d("delta"), c.i("steps")), "reach $c")
        }
    }

    @Test
    fun reachabilityMainB() {
        for (c in Golden.json("main-b-reachability.json").jsonArray) {
            assertEquals(c.b("ok"), Reach(c.d("t")).reach(0.0, c.d("d"), c.i("steps")), "reach $c")
        }
    }

    @Test
    fun plantTiming() {
        for (s in Golden.json("world-plant-timing.json").arr("samples")) {
            val beat = s.d("beat")
            assertEquals(s.b("peek"), PlantTiming.plantPeek(beat), "peek($beat)")
            assertRounded(s.d("rise"), PlantTiming.plantRise(beat), "rise($beat)")
            assertRounded(s.d("pulseScale"), PlantTiming.pulseScale(beat), "pulseScale($beat)")
        }
    }

    @Test
    fun rowCloudLcg() {
        val g = Golden.json("world-cloud-rng.json")
        val picker = RowCloudPicker()
        for (e in g.arr("rowPicks")) {
            val want = if (e is JsonNull) -1 else e.jsonPrimitive.int
            assertEquals(want, picker.next())
        }
        for (seed in 1..3) {
            val bank = RowCloudPicker.bankRandom(seed)
            for (v in g.o("bankFirst8").arr(seed.toString())) assertRounded(v.jsonPrimitive.double, bank(), "bank$seed")
            val collar = RowCloudPicker.collarRandom(seed)
            for (v in g.o("collarFirst8").arr(seed.toString())) assertRounded(v.jsonPrimitive.double, collar(), "collar$seed")
        }
    }

    @Test
    fun squashStretchTrace() {
        val g = Golden.json("bird-fx-squash-stretch.json")
        val dt = g.d("dtAssumed")
        val h = SimHarness()
        h.state.mode = GameMode.Over // no pitch/wing side effects needed
        h.state.squash = 1.0
        h.state.pose.baseScale = g.d("baseScaleAssumed")
        for (f in g.arr("frames")) {
            if (f.i("frame") > 0) {
                // Same order as updateBirdVisual: decay first, then q.
                h.state.squash = kotlin.math.max(0.0, h.state.squash - dt * 6)
            }
            val q = squashAmount(h.state.squash)
            assertRounded(f.d("squash"), h.state.squash, "squash ${f.i("frame")}")
            assertRounded(f.d("q"), q, "q ${f.i("frame")}")
            val bs = h.state.pose.baseScale
            assertRounded(f.o("scale").d("x"), bs * (1 - 0.14 * q), "sx")
            assertRounded(f.o("scale").d("y"), bs * (1 + 0.24 * q), "sy")
            assertRounded(f.o("scale").d("z"), bs * (1 - 0.1 * q), "sz")
        }
    }

    @Test
    fun squashThroughUpdateBirdVisual() {
        // frame 1 of the golden: one 60 Hz updateBirdVisual after squash = 1.
        val g = Golden.json("bird-fx-squash-stretch.json")
        val f1 = g.arr("frames")[1]
        val h = SimHarness()
        h.state.squash = 1.0
        h.sim.updateBirdVisual(g.d("dtAssumed"))
        assertRounded(f1.d("squash"), h.state.squash, "squash")
        assertRounded(f1.o("scale").d("y"), h.state.pose.scaleY, "scaleY")
    }

    @Test
    fun wingIntegration() {
        val g = Golden.json("bird-fx-wing-animate.json")
        val h = SimHarness()
        h.state.mode = GameMode.Playing
        h.state.wingSpeed = Tuning.WING_FLAP
        h.state.wingPhase = 0.0
        for (f in g.arr("simulation60fps_flapAtFrame0")) {
            h.sim.updateBirdVisual(1.0 / 60)
            assertRounded(f.d("wingSpeed"), h.state.wingSpeed, "wingSpeed ${f.i("frame")}")
            assertRounded(f.d("phase"), h.state.wingPhase, "phase ${f.i("frame")}")
        }
    }

    @Test
    fun pickupShrink() {
        val g = Golden.json("bird-fx-powerup-pickup.json")
        for (s in g.arr("worldShrinkSamples")) {
            val k = shrinkBehind(s.d("z"))
            assertEquals(s.d("k"), k, "k(${s.d("z")})")
            assertEquals(s.b("visible"), k > 0)
        }
        for (t in PowerType.entries) {
            assertEquals(g.o("perTypeDurationSeconds").d(t.id), t.baseDuration)
            assertEquals(g.o("colorHex").i(t.id), t.color)
        }
        assertEquals(1.4, Tuning.PICKUP_RADIUS)
    }
}
