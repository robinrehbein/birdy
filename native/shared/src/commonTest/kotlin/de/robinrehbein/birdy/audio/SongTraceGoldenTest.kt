package de.robinrehbein.birdy.audio

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.audio.music.MusicData
import de.robinrehbein.birdy.audio.music.Sequencer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Every step of audio-song-trace.json (256 steps x 4 themes) plus the calm transform and hype layer. */
class SongTraceGoldenTest {
    private val golden = Golden.json("audio-song-trace.json").jsonObject
    private val themes = golden["themes"]!!.jsonArray

    private fun near(exp: Double, act: Double, what: String) {
        if (abs(exp - act) > 1e-9 * maxOf(1.0, abs(exp))) fail("$what: expected $exp, got $act")
    }

    private fun JsonObject.d(k: String) = this[k]!!.jsonPrimitive.double
    private fun JsonObject.i(k: String) = this[k]!!.jsonPrimitive.int
    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

    private fun checkTone(exp: JsonObject, c: RecordingSink.Call?, what: String, volScale: Double = 1.0) {
        if (c == null) fail("$what: missing")
        near(exp.d("freq"), c.freq, "$what freq")
        exp["midi"]?.let { near(MusicData.midi(it.jsonPrimitive.int), c.freq, "$what midi") }
        near(exp.d("dur"), c.dur, "$what dur")
        near(exp.d("vol") * volScale, c.vol, "$what vol")
        exp["type"]?.let { assertEquals(it.jsonPrimitive.content, waveName(c.wave), "$what type") }
        assertEquals(Bus.MUSIC, c.bus, "$what bus")
    }

    private fun isNull(e: JsonElement?) = e == null || e is JsonNull

    private fun runTheme(index: Int, mode: MusicMode, hype: Boolean): List<List<RecordingSink.Call>> {
        val seq = Sequencer()
        seq.reset(index, mode, hype)
        val sink = RecordingSink()
        return (0 until 256).map { s ->
            seq.playStep(s, 0.05 + s * MusicData.STEP, sink)
            sink.take()
        }
    }

    @Test
    fun constants() {
        assertEquals(golden["BPM"]!!.jsonPrimitive.int, MusicData.BPM)
        assertEquals(golden["STEP"]!!.jsonPrimitive.double, MusicData.STEP)
    }

    @Test
    fun gameModeTraceMatchesEveryStep() {
        for (theme in themes) {
            val t = theme.jsonObject
            val index = t.i("themeIndex")
            assertEquals(t.i("key"), MusicData.THEMES[index].key)
            assertEquals(t.s("drumsKind"), MusicData.THEMES[index].drums.name.lowercase())
            assertEquals(t["minor"]!!.jsonPrimitive.boolean, MusicData.THEMES[index].minor)
            val plain = runTheme(index, MusicMode.Game, hype = false)
            val hyped = runTheme(index, MusicMode.Game, hype = true)
            for ((s, stepEl) in t["steps"]!!.jsonArray.withIndex()) {
                val st = stepEl.jsonObject
                val what = "theme $index step $s"
                assertEquals(s, st.i("step"))
                val calls = plain[s]
                checkStep(st, calls, what, calm = false)
                assertTrue(calls.none { it.role == Role.SPARKLE }, "$what: no sparkle without hype")
                // With hype the sparkle layer appears and nothing else changes.
                val sparkle = hyped[s].filter { it.role == Role.SPARKLE }
                assertEquals(1, sparkle.size, "$what sparkle count")
                checkTone(st["hypeSparkleIfHype"]!!.jsonObject, sparkle[0], "$what sparkle")
                assertEquals("square", waveName(sparkle[0].wave))
                assertEquals(calls, hyped[s].filter { it.role != Role.SPARKLE }, "$what hype leaves rest")
            }
        }
    }

    @Test
    fun calmTransformMatches() {
        for (theme in themes) {
            val t = theme.jsonObject
            val index = t.i("themeIndex")
            val calm = runTheme(index, MusicMode.Menu, hype = true)
            for ((s, stepEl) in t["steps"]!!.jsonArray.withIndex()) {
                checkStep(stepEl.jsonObject, calm[s], "calm theme $index step $s", calm = true)
                assertTrue(calm[s].none { it.role == Role.SPARKLE }, "calm never sparkles")
            }
        }
    }

    private fun checkStep(st: JsonObject, calls: List<RecordingSink.Call>, what: String, calm: Boolean) {
        val drums = calls.filter { it.role in DRUM_ROLES }
        val expDrums = st["drums"]!!.jsonArray
        if (calm) {
            assertTrue(drums.isEmpty(), "$what calm drums")
        } else {
            assertEquals(expDrums.size, drums.size, "$what drum count")
            for ((i, e) in expDrums.withIndex()) {
                val o = e.jsonObject
                val c = drums[i]
                val type = o.s("type")
                val role = when (type) {
                    "kick" -> Role.KICK
                    "snare" -> Role.SNARE
                    "desertBlip" -> Role.DESERT_BLIP
                    else -> if (o["note"]?.jsonPrimitive?.content == "breakdown-tick") Role.BREAKDOWN_TICK else Role.HAT
                }
                assertEquals(role, c.role, "$what drum $i")
                near(o.d("dur"), c.dur, "$what drum dur")
                near(o.d("vol"), c.vol, "$what drum vol")
                if (c.noise) near(o.d("cutoff"), c.cutoff, "$what cutoff")
                else { near(o.d("freq"), c.freq, "$what drum freq"); near(o.d("to"), c.to, "$what drum to") }
            }
        }

        val bass = calls.filter { it.role == Role.BASS }
        if (isNull(st["bass"])) assertTrue(bass.isEmpty(), "$what no bass")
        else {
            assertEquals(1, bass.size)
            checkTone(st["bass"]!!.jsonObject, bass[0], "$what bass", if (calm) 0.3 / 0.55 else 1.0)
            assertEquals("triangle", waveName(bass[0].wave))
        }

        val arp = calls.filter { it.role == Role.ARP }
        if (isNull(st["arp"])) assertTrue(arp.isEmpty(), "$what no arp")
        else { assertEquals(1, arp.size); checkTone(st["arp"]!!.jsonObject, arp[0], "$what arp") }

        val lead = calls.filter { it.role == Role.LEAD }
        val harmony = calls.filter { it.role == Role.HARMONY }
        val melody = st["melody"]
        val volScale = if (calm) 0.6 else 1.0
        if (isNull(melody)) {
            assertTrue(lead.isEmpty() && harmony.isEmpty(), "$what no melody")
        } else {
            val m = melody!!.jsonObject
            assertEquals(1, lead.size)
            checkTone(m, lead[0], "$what lead", volScale)
            near(MusicData.STEP * m.i("lengthSteps") * 0.95, lead[0].dur, "$what lead len")
            val h = m["harmonyVoice"]
            if (isNull(h)) assertTrue(harmony.isEmpty(), "$what no harmony")
            else checkTone(h!!.jsonObject, harmony.single(), "$what harmony", volScale)
        }
        assertTrue(calls.all { it.at == calls.first().at }, "$what all at step time")
    }

    @Test
    fun hypeIsNullInTraceWithoutHype() {
        for (theme in themes) for (st in theme.jsonObject["steps"]!!.jsonArray) assertNull((st.jsonObject["hypeSparkle"] as? JsonObject))
    }

    private companion object {
        val DRUM_ROLES = setOf(Role.KICK, Role.SNARE, Role.HAT, Role.DESERT_BLIP, Role.BREAKDOWN_TICK)
    }
}
