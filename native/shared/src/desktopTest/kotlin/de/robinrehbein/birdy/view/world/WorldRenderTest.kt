package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.sim.GateRows
import de.robinrehbein.birdy.meta.Catalog
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Headless renders of the world view (540x1200) saved to native/build/worldtest/ for visual
 * comparison with the JS reference shots (build/worldtest/js/, see scripts in the report), plus
 * basic pixel sanity: sky gradient at the top, ground at the bottom.
 */
class WorldRenderTest {
    /** 3 beats in at 124 bpm: plants with offset 0 are fully up, offset -1.5 peek. */
    private val beat = 3.0
    private val time = beat * 60 / 124

    private fun WorldShotKit.gateRows() {
        row(
            0, -9.0,
            listOf(GapSpec(5.0, 5.0), GapSpec(5.4, 5.0, plant = true, plantOffset = 0.0), null),
            cloud = 0, time = time, beat = beat,
        )
        row(
            1, -31.0,
            listOf(
                GapSpec(5.5, 4.6, amp = 1.2, speed = 2.0, phase = 0.3),
                GapSpec(5.0, 4.8, pulse = true, plantOffset = 0.5),
                GapSpec(5.2, 5.0, plant = true, plantOffset = -1.5),
            ),
            cloud = -1, time = time, beat = beat,
        )
        row(2, -53.0, listOf(null, GapSpec(6.0, 4.6), GapSpec(4.5, 4.6)), cloud = 0, time = time, beat = beat)
        row(3, -75.0, listOf(GapSpec(5.0, 4.6), GapSpec(5.0, 4.6), null), cloud = -1, time = time, beat = beat)
        for (k in 0 until 4) coin(k, 0.0, 5.0, -16.0 - k * 2.2, spin = 0.5 + k * 0.4)
    }

    private fun sanity(shot: Shot, topHex: Int, horizonHex: Int, what: String) {
        val top = shot.mean(4, 40)
        for ((get, name) in listOf(::red to "r", ::green to "g", ::blue to "b")) {
            val lo = min(get(topHex), get(horizonHex)) - 10
            val hi = max(get(topHex), get(horizonHex)) + 10
            assertTrue(get(top) in lo..hi, "$what: sky at the top ${hexOf(top)} channel $name not between ${hexOf(topHex)} and ${hexOf(horizonHex)}")
        }
        val bottom = shot.mean(shot.width / 2 - 4, shot.height - 12)
        assertTrue(!near(bottom, horizonHex, 24) && !near(bottom, topHex, 24), "$what: ground expected at the bottom, got ${hexOf(bottom)}")
    }

    @Test
    fun parkGateRows(): Unit = WorldShotKit().use { k ->
        k.world.biomes.set(0, 0.0, WorldPicks.zoneBiome(0, Catalog.worlds[0]))
        k.world.setPipeStyle(Catalog.pipes[0].colors)
        k.gateRows()
        k.sync()
        k.sync()
        val shot = k.render().save("park_gates")
        val s = k.renderer.stats
        println("WORLD RenderStats park_gates: drawCalls=${s.drawCalls} shadow=${s.shadowDrawCalls} triangles=${s.triangles} culled=${s.culled} programs=${s.programs}")
        sanity(shot, 0x2a9bd0, 0xa6e4ea, "park")
        assertTrue(s.drawCalls < 150, "draw calls ${s.drawCalls}")
    }

    @Test
    fun fadingRow(): Unit = WorldShotKit().use { k ->
        k.world.biomes.set(0, 0.0, WorldPicks.zoneBiome(0, Catalog.worlds[0]))
        k.gateRows()
        val r = k.gates[0]
        r.passed = true
        GateRows.update(r, time, beat)
        GateRows.setOpacity(r, 0.45)
        k.sync()
        k.sync()
        val cam = k.runCamera().apply { position.z = -2f; lookAt(0f, 4.8f, -40f) }
        k.render(cam).save("park_fading_row")
    }

    /** Looking up at the pipe tops: they fade into the sky colour between y 32 and 40. */
    @Test
    fun skyHaze(): Unit = WorldShotKit().use { k ->
        k.world.biomes.set(0, 0.0, WorldPicks.zoneBiome(0, Catalog.worlds[0]))
        k.gateRows()
        k.sync()
        k.sync()
        val cam = k.runCamera().apply { position.set(0f, 12f, 30f); lookAt(0f, 34f, -9f) }
        k.render(cam).save("park_haze")
    }

    @Test
    fun zoneBiomes() {
        for ((i, b) in Catalog.zoneBiomes.withIndex()) {
            if (i == 0) continue
            WorldShotKit().use { k ->
                k.world.biomes.set(i, 0.0, WorldPicks.zoneBiome(i, Catalog.worlds[0]))
                k.world.setPipeStyle(Catalog.pipes[0].colors)
                k.gateRows()
                k.sync()
                k.sync()
                val shot = k.render().save("biome_${b.scenery}")
                sanity(shot, b.palette.top, b.palette.horizon, b.scenery)
            }
        }
    }

    @Test
    fun shopWorlds() {
        for (w in Catalog.worlds) WorldShotKit().use { k ->
            k.world.biomes.set(0, 0.0, WorldPicks.zoneBiome(0, w))
            k.world.setPipeStyle(WorldPicks.pipeFor(w, Catalog.pipes[0]))
            k.gateRows()
            k.sync()
            k.sync()
            val shot = k.render().save("world_${w.id}")
            sanity(shot, w.palette.top, w.palette.horizon, w.id)
        }
    }
}
