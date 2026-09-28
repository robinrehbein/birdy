package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.audio.music.MusicData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** audio-sfx-sequences.json: every sfx.* tone/noise with exact freqs, durations, volumes and delays. */
class SfxGoldenTest {
    private val g = Golden.json("audio-sfx-sequences.json").jsonObject
    private val now = 3.25

    private fun near(exp: Double, act: Double, what: String, tol: Double = 1e-6) {
        if (abs(exp - act) > tol * maxOf(1.0, abs(exp))) fail("$what: expected $exp, got $act")
    }

    private fun JsonObject.d(k: String) = this[k]!!.jsonPrimitive.double

    private fun play(id: Int, arg: Double = 0.0): List<RecordingSink.Call> =
        RecordingSink().also { SfxBank.play(id, arg, now, it) }.calls

    private fun checkTone(exp: JsonObject, c: RecordingSink.Call, what: String) {
        assertTrue(!c.noise, "$what is a tone")
        near(exp.d("freq"), c.freq, "$what freq")
        near(exp["to"]?.jsonPrimitive?.double ?: exp.d("freq"), c.to, "$what to")
        near(exp.d("dur"), c.dur, "$what dur", 1e-12)
        near(exp.d("vol"), c.vol, "$what vol", 1e-12)
        near(now + (exp["delay"]?.jsonPrimitive?.double ?: 0.0), c.at, "$what delay", 1e-12)
        assertEquals(exp["type"]!!.jsonPrimitive.content, waveName(c.wave), "$what type")
        assertEquals(Bus.SFX, c.bus)
    }

    private fun checkNoise(exp: JsonObject, c: RecordingSink.Call, what: String) {
        assertTrue(c.noise, "$what is noise")
        near(exp.d("dur"), c.dur, "$what dur", 1e-12)
        near(exp.d("vol"), c.vol, "$what vol", 1e-12)
        near(exp.d("cutoff"), c.cutoff, "$what cutoff", 1e-12)
        near(now, c.at, "$what at", 1e-12)
        assertEquals(Bus.SFX, c.bus)
    }

    private fun checkList(exp: List<JsonObject>, calls: List<RecordingSink.Call>, what: String) {
        assertEquals(exp.size, calls.size, "$what count")
        exp.forEachIndexed { i, e -> checkTone(e, calls[i], "$what[$i]") }
    }

    @Test
    fun midiTable() {
        for ((k, v) in g["midiTable"]!!.jsonObject) near(v.jsonPrimitive.double, MusicData.midi(k.toInt()), "midi $k")
    }

    @Test
    fun fixedEffects() {
        checkList(g["point"]!!.jsonArray.map { it.jsonObject }, play(SfxId.POINT), "point")
        checkList(g["powerup"]!!.jsonArray.map { it.jsonObject }, play(SfxId.POWERUP), "powerup")
        checkList(g["powerdown"]!!.jsonArray.map { it.jsonObject }, play(SfxId.POWERDOWN), "powerdown")
        val zone = g["zone"]!!.jsonObject
        checkList(listOf(zone["sweep"]!!.jsonObject) + zone["arpeggio"]!!.jsonArray.map { it.jsonObject }, play(SfxId.ZONE), "zone")
        checkList(listOf(g["bounce"]!!.jsonObject), play(SfxId.BOUNCE), "bounce")
        val hit = g["hit"]!!.jsonObject
        val hc = play(SfxId.HIT)
        assertEquals(3, hc.size)
        checkTone(hit["tone1"]!!.jsonObject, hc[0], "hit1")
        checkTone(hit["tone2"]!!.jsonObject, hc[1], "hit2")
        checkNoise(hit["noise"]!!.jsonObject, hc[2], "hitNoise")
        val sw = play(SfxId.SWOOSH)
        assertEquals(1, sw.size)
        checkNoise(g["swoosh"]!!.jsonObject, sw[0], "swoosh")
    }

    @Test
    fun coinCombo() {
        val coin = g["coin"]!!.jsonObject
        assertEquals(7, coin["comboMax"]!!.jsonPrimitive.int)
        for (e in coin["byCombo"]!!.jsonArray) {
            val o = e.jsonObject
            val combo = o["combo"]!!.jsonPrimitive.int
            val cc = CoinCombo()
            var k = 0.0
            var t = 10_000.0
            repeat(combo + 1) { k = cc.hit(t); t += 100 }
            assertEquals(combo, cc.combo)
            near(o.d("k"), k, "k$combo")
            val calls = play(SfxId.COIN, k)
            checkTone(o["tone1"]!!.jsonObject, calls[0], "coin$combo.1")
            checkTone(o["tone2"]!!.jsonObject, calls[1], "coin$combo.2")
        }
        // Clamp at 7, reset after a >= 700 ms gap.
        val cc = CoinCombo()
        var t = 5000.0
        repeat(12) { cc.hit(t); t += 699 }
        assertEquals(7, cc.combo)
        cc.hit(t + 1)
        assertEquals(0, cc.combo)
    }

    @Test
    fun nearChain() {
        for (e in g["near"]!!.jsonObject["byChain"]!!.jsonArray) {
            val o = e.jsonObject
            val chain = o["chain"]!!.jsonPrimitive.int
            assertEquals(o["base"]!!.jsonPrimitive.int, SfxBank.nearBase(chain))
            checkList(o["notes"]!!.jsonArray.map { it.jsonObject }, play(SfxId.NEAR, chain.toDouble()), "near$chain")
        }
    }

    @Test
    fun flapRangeAndLayers() {
        val f = g["flap"]!!.jsonObject
        near(f.d("kMin"), SfxBank.flapK(0.0), "kMin")
        near(f.d("kMaxExclusive"), SfxBank.flapK(1.0), "kMax")
        val calls = play(SfxId.FLAP, 1.0)
        checkTone(f["toneAtKEquals1"]!!.jsonObject, calls[0], "flap")
        checkNoise(f["noise"]!!.jsonObject, calls[1], "flapNoise")
        val scaled = play(SfxId.FLAP, 0.97)[0]
        near(380 * 0.97, scaled.freq, "flap k")
        near(620 * 0.97, scaled.to, "flap k to")
    }
}
