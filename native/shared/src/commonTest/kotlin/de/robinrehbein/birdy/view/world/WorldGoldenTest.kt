package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.game.sim.RowCloudPicker
import de.robinrehbein.birdy.meta.Catalog
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private operator fun JsonElement.get(key: String): JsonElement = jsonObject.getValue(key)
private val JsonElement.d: Double get() = jsonPrimitive.double
private fun JsonElement.hexList(): List<Int> = jsonArray.map { it.jsonPrimitive.content.toInt(16) }

/** Parity with docs/native/golden/world-*.json (biome blending and the row-cloud LCGs). */
class WorldGoldenTest {
    @Test
    fun blendEasing() {
        for (s in Golden.json("world-biome-blend.json")["easing"].jsonArray) {
            assertTrue(abs(BiomeBlender.ease(s["t"].d) - s["eased"].d) < 1e-6, "ease(${s["t"].d})")
        }
    }

    @Test
    fun steppedRoadPalette() {
        val g = Golden.json("world-biome-blend.json")
        val from = g["roadFrom"].hexList()
        val to = g["roadTo"].hexList()
        assertEquals(WorldLook.SAND_ROAD, from)
        assertEquals(Catalog.worlds.first { it.id == "winter" }.road, to)
        for (s in g["roadSteps"].jsonArray) {
            val e = BiomeBlender.ease(s["t"].d)
            assertTrue(abs(e - s["eased"].d) < 1e-6)
            assertEquals(s["road"].hexList().map(::hex), BiomeBlender.stepRoad(from, to, e).map(::hex), "road at t=${s["t"].d}")
        }
    }

    /** The whole blender: SAND_ROAD -> winter over 1 s in 1/8 s frames repaints exactly the golden palettes. */
    @Test
    fun blenderRepaintsRoadInSteps() {
        val g = Golden.json("world-biome-blend.json")
        val world = WorldView(kotlin.random.Random(1)).also { it.attach(Scene()) }
        val b = world.biomes
        b.set(0, 0.0, WorldPicks.zoneBiome(0, Catalog.worlds[0]))
        b.update(1.0 / 60)
        val winter = Catalog.worlds.first { it.id == "winter" }
        b.set(0, 1.0, Biome.of(winter))
        assertEquals(WorldLook.SAND_ROAD, world.ground.road)
        val before = world.ground.repaints
        val steps = g["roadSteps"].jsonArray.drop(1)
        for (s in steps) {
            b.update(0.125)
            assertEquals(s["road"].hexList(), world.ground.road, "road at t=${s["t"].d}")
        }
        assertTrue(world.ground.repaints - before <= 8, "repaints ${world.ground.repaints - before}")
        val p = winter.palette
        assertEquals(p.top, world.env.skyTop.value.hex())
        assertEquals(p.horizon, world.env.skyHorizon.value.hex())
        assertEquals(p.horizon, world.env.fog.color.hex())
        assertEquals(p.horizon, world.env.background.hex())
        assertEquals(p.hemiSky, world.env.hemi.color.hex())
        assertEquals(p.hemiGround, world.env.hemi.groundColor.hex())
        assertEquals(p.sun, world.env.sun.color.hex())
        assertEquals(p.tint, world.scenery.material.color.hex())
        assertEquals(p.clouds, world.clouds.material.color.hex())
        assertEquals(p.clouds, world.gates.bankColor.hex())
        assertEquals(p.grass, world.ground.grassMat.color.hex())
        assertEquals(p.track, world.ground.trackMat.color.hex())
        assertEquals(p.hemiI, world.env.hemi.intensity, 1e-6f)
        assertEquals(p.sunI, world.env.sun.intensity, 1e-6f)
        assertEquals("winter", world.scenery.theme)
    }

    /** Colours mid-blend lerp in linear space like three.js `Color.lerp`. */
    @Test
    fun midBlendIsLinearLerp() {
        val world = WorldView(kotlin.random.Random(1)).also { it.attach(Scene()) }
        val b = world.biomes
        b.set(0, 0.0, WorldPicks.zoneBiome(0, Catalog.worlds[0]))
        b.update(1.0)
        b.set(1, 2.0)
        b.update(1.0) // t = 0.5 -> e = 0.5
        val expected = de.robinrehbein.birdy.engine.math.Color.hex(0x2a9bd0).lerp(de.robinrehbein.birdy.engine.math.Color.hex(0x4d6fc4), 0.5).hex()
        assertEquals(hex(expected), hex(world.env.skyTop.value.hex()))
        assertEquals((1.4 + (1.35 - 1.4) * 0.5).toFloat(), world.env.hemi.intensity, 1e-5f)
    }

    /** biomes.js quirk: `set(i, 0)` calls `update(0)`, which leaves t at 0; the next update snaps. */
    @Test
    fun blendZeroSnapsOnNextUpdate() {
        val world = WorldView(kotlin.random.Random(1)).also { it.attach(Scene()) }
        val b = world.biomes
        val autumn = Catalog.zoneBiomes[1]
        b.set(1, 0.0)
        assertEquals(WorldLook.SKY_TOP, world.env.skyTop.value.hex())
        assertEquals("autumn", world.scenery.theme)
        assertTrue(world.scenery.chunks.all { world.scenery.themeOf(it) == "autumn" }, "instant rebuild")
        b.update(1.0 / 60)
        assertEquals(autumn.palette.top, world.env.skyTop.value.hex())
        assertEquals(autumn, b.current!!.source)
        assertEquals(1, b.index)
    }

    @Test
    fun rowCloudLcgs() {
        val g = Golden.json("world-cloud-rng.json")
        val picker = RowCloudPicker()
        for ((i, e) in g["rowPicks"].jsonArray.withIndex()) {
            val want = if (e is JsonNull) -1 else e.jsonPrimitive.int
            assertEquals(want, picker.next(), "pick $i")
        }
        for (seed in 1..3) {
            val bank = RowCloudPicker.bankRandom(seed)
            for (v in g["bankFirst8"]["$seed"].jsonArray) assertTrue(abs(bank() - v.d) < 1e-6, "bank $seed")
            val collar = RowCloudPicker.collarRandom(seed)
            for (v in g["collarFirst8"]["$seed"].jsonArray) assertTrue(abs(collar() - v.d) < 1e-6, "collar $seed")
        }
    }

    /** Bank/collar geometries consume their LCG in the JS order: the puff count follows from it. */
    @Test
    fun rowCloudGeometryFollowsLcg() {
        val puffVerts = 240 // icosahedron detail 1: 80 triangles
        for (seed in 1..3) {
            val rnd = RowCloudPicker.bankRandom(seed)
            var x = -6.5
            var puffs = 0
            while (x <= 6.5) {
                repeat(4) { rnd() }
                puffs++
                x += 1.6 + rnd() * 0.5
            }
            assertEquals(puffs * puffVerts, RowCloudGeometry.bank(seed).vertexCount, "bank $seed")
            assertEquals(15 * puffVerts, RowCloudGeometry.collar(seed).vertexCount, "collar $seed")
        }
        assertEquals(6, RowCloudGeometry.all.size)
    }

    private fun hex(c: Int) = c.toString(16).padStart(6, '0')
}
