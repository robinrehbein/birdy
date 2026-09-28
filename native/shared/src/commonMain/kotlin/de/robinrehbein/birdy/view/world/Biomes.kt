package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.meta.LocalizedText
import de.robinrehbein.birdy.meta.Palette
import de.robinrehbein.birdy.meta.PipeColors
import de.robinrehbein.birdy.meta.PipeItem
import de.robinrehbein.birdy.meta.ProgressRepository
import de.robinrehbein.birdy.meta.WorldItem
import de.robinrehbein.birdy.meta.ZoneBiome
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A blend target: one of the fixed zone biomes (biomes.js `BIOMES`) or a shop world, which has
 * the same fields plus an optional road palette and pipe colours. [source] is the catalogue
 * object it came from (JS compares `biomes.current === world` by identity).
 */
class Biome(
    val name: LocalizedText,
    val scenery: String,
    val palette: Palette,
    val road: List<Int>?,
    val pipes: PipeColors?,
    val source: Any,
) {
    companion object {
        private val zones = HashMap<ZoneBiome, Biome>()
        private val worlds = HashMap<WorldItem, Biome>()

        fun of(z: ZoneBiome): Biome = zones.getOrPut(z) { Biome(z.name, z.scenery, z.palette, null, null, z) }
        fun of(w: WorldItem): Biome = worlds.getOrPut(w) { Biome(w.name, w.scenery, w.palette, w.road, w.pipes, w) }
    }
}

/** main.js boot helpers around the biomes (main-a.md §2.2 items 7, 9, 10). */
object WorldPicks {
    /** Every 4th zone (0, 4, 8, ...) is the world equipped in the shop, the others cycle BIOMES. */
    fun zoneBiome(zone: Int, equippedWorld: WorldItem): Biome {
        val i = zone % Tuning.BIOME_COUNT
        return if (i == 0) Biome.of(equippedWorld) else Biome.of(Catalog.zoneBiomes[i])
    }

    fun zoneBiome(zone: Int, progress: ProgressRepository): Biome =
        zoneBiome(zone, progress.equipped(Kind.World) as WorldItem)

    /** With the classic pipes equipped a world brings its own pipe colours; a bought design wins. */
    fun pipeFor(world: WorldItem, pipe: PipeItem): PipeColors {
        val wp = world.pipes
        return if (pipe.id == Catalog.pipes[0].id && wp != null) {
            PipeColors(wp.pipe, wp.light, wp.dark, metal = pipe.colors.metal || wp.metal)
        } else {
            pipe.colors
        }
    }

    fun equippedPipes(progress: ProgressRepository): PipeColors =
        pipeFor(progress.equipped(Kind.World) as WorldItem, progress.equipped(Kind.Pipe) as PipeItem)
}

/** The live objects a [BiomeBlender] recolours (biomes.js `createBiomeBlender` arguments). */
interface BiomeTargets {
    val env: WorldEnv
    val ground: Ground
    val scenery: Scenery
    /** Colour shared by the sky clouds and every pipe-row cloud bank. */
    val cloudColors: List<Color>
}

/**
 * biomes.js `createBiomeBlender`: blends sky, fog, lights and the tints of scenery, clouds, grass
 * and track from their current values towards a biome over `blend` seconds (smoothstep), and
 * repaints the road texture in 1/8 steps. Colours lerp in linear space like three.js.
 */
class BiomeBlender(private val targets: BiomeTargets) {
    private enum class Key { Top, Horizon, Background, Fog, HemiSky, HemiGround, Sun, Tint, Clouds, Grass, Track }

    private val env = targets.env
    private val live: Map<Key, Color> = mapOf(
        Key.Top to env.skyTop.value,
        Key.Horizon to env.skyHorizon.value,
        Key.Background to env.background,
        Key.Fog to env.fog.color,
        Key.HemiSky to env.hemi.color,
        Key.HemiGround to env.hemi.groundColor,
        Key.Sun to env.sun.color,
        Key.Tint to targets.scenery.material.color,
        Key.Clouds to targets.cloudColors.first(),
        Key.Grass to targets.ground.grassMat.color,
        Key.Track to targets.ground.trackMat.color,
    )
    private val from = Key.entries.associateWith { Color() }
    private val to = Key.entries.associateWith { Color() }
    private var fromHemi = env.hemi.intensity
    private var fromSun = env.sun.intensity
    private var toHemi = fromHemi
    private var toSun = fromSun
    private var t = 1.0
    private var duration = 1.0

    var index = 0
        private set
    var current: Biome? = null
        private set

    private var road: List<Int> = WorldLook.SAND_ROAD
    private var roadFrom = road
    private var roadTo = road

    /** Road palette currently painted (after the last step). */
    val currentRoad: List<Int> get() = road

    /** Blend progress 0..1 (1 = idle). */
    val progress: Double get() = t

    private fun paintRoad(e: Double) {
        road = stepRoad(roadFrom, roadTo, e)
        targets.ground.setRoad(road)
    }

    /** `set(i, blend = 3, b = BIOMES[i % BIOMES.length])`. */
    fun set(i: Int, blend: Double = 3.0, b: Biome = Biome.of(Catalog.zoneBiomes[i % Tuning.BIOME_COUNT])): Biome {
        index = i
        current = b
        roadFrom = road
        roadTo = b.road ?: WorldLook.SAND_ROAD
        for (k in Key.entries) {
            from.getValue(k).set(live.getValue(k))
            to.getValue(k).setHex(targetHex(b.palette, k))
        }
        fromHemi = env.hemi.intensity
        fromSun = env.sun.intensity
        toHemi = b.palette.hemiI
        toSun = b.palette.sunI
        duration = max(0.001, blend)
        t = 0.0
        targets.scenery.setTheme(b.scenery, blend == 0.0)
        // As in JS, blend 0 only "snaps" on the next update(dt): update(0) leaves t at 0.
        if (blend == 0.0) update(0.0) else paintRoad(0.0)
        return b
    }

    fun update(dt: Double) {
        if (t >= 1) return
        t = minOf(1.0, t + dt / duration)
        val e = ease(t)
        for (k in Key.entries) live.getValue(k).set(from.getValue(k)).lerp(to.getValue(k), e)
        val clouds = live.getValue(Key.Clouds)
        for (c in targets.cloudColors) if (c !== clouds) c.set(clouds)
        paintRoad(e)
        env.hemi.intensity = (fromHemi + (toHemi - fromHemi) * e).toFloat()
        env.sun.intensity = (fromSun + (toSun - fromSun) * e).toFloat()
    }

    private fun targetHex(p: Palette, k: Key): Int = when (k) {
        Key.Top -> p.top
        Key.Horizon, Key.Background, Key.Fog -> p.horizon
        Key.HemiSky -> p.hemiSky
        Key.HemiGround -> p.hemiGround
        Key.Sun -> p.sun
        Key.Tint -> p.tint
        Key.Clouds -> p.clouds
        Key.Grass -> p.grass
        Key.Track -> p.track
    }

    companion object {
        /** The blend easing `t * t * (3 - 2 * t)`. */
        fun ease(t: Double): Double = t * t * (3 - 2 * t)

        /** paintRoad's palette at eased progress [e]: quantized to 1/8, mixed in linear space. */
        fun stepRoad(from: List<Int>, to: List<Int>, e: Double): List<Int> {
            val step = (e * 8).roundToInt() / 8.0
            return from.mapIndexed { k, c -> Color.hex(c).lerp(Color.hex(to[k]), step).hex() }
        }
    }
}
