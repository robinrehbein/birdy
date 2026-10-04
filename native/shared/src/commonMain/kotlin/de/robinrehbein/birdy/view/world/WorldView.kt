package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.scene.DirectionalLight
import de.robinrehbein.birdy.engine.scene.Fog
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.game.Coin
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.meta.PipeColors
import de.robinrehbein.birdy.meta.ProgressRepository
import de.robinrehbein.birdy.view.FrameInfo
import de.robinrehbein.birdy.view.SceneView
import kotlin.random.Random

/**
 * The world of world.js/biomes.js as a [SceneView]: sky, lights, fog, ground, scenery, clouds,
 * pipe rows with cacti and cloud banks, and the instanced coins. It renders the simulation's
 * state (gates, coins, distance) and owns the zone [biomes] blender.
 *
 * [rng] replaces `Math.random()` for scenery and sky clouds (pass a seeded one for screenshots).
 *
 * The loop is expected to call, like main.js:
 * - [boot] once (or it happens on the first [update]): `biomes.set(0, 0, zoneBiome(0))` and the
 *   equipped pipe style;
 * - `biomes.set(zone, 3.0, WorldPicks.zoneBiome(zone, progress))` on `GameEvent.ZoneEntered`,
 *   `biomes.set(0, 1.2, ...)` in resetGame and `biomes.set(0, 0.5, world)` for shop previews;
 * - [setPipeStyle] with [WorldPicks.equippedPipes] / [WorldPicks.pipeFor].
 */
class WorldView(private val rng: Random = Random.Default) : SceneView, BiomeTargets {
    override lateinit var env: WorldEnv
        private set
    override lateinit var ground: Ground
        private set
    override lateinit var scenery: Scenery
        private set
    lateinit var clouds: Clouds
        private set
    lateinit var pipes: PipeKit
        private set
    lateinit var coins: CoinField
        private set
    lateinit var biomes: BiomeBlender
        private set
    internal lateinit var gates: GateLayer
        private set

    /** Banner text of the record gate ("REKORD 32"), set by the session at each run start. */
    var recordLabel = ""
    private lateinit var scene: Scene

    override val cloudColors: List<Color> get() = listOf(clouds.material.color, gates.bankColor)

    private var booted = false
    private var lastDistance = Double.NaN

    /** The shadow-casting sun (fixed position, world.js `createScene`). */
    val sun: DirectionalLight get() = env.sun
    val fog: Fog get() = env.fog
    val sky: Mesh get() = env.sky

    override fun attach(scene: Scene) {
        this.scene = scene
        env = WorldEnv(scene)
        ground = Ground(scene)
        scenery = Scenery(scene, rng)
        clouds = Clouds(scene, rng)
        pipes = PipeKit()
        gates = GateLayer(scene, pipes, skyHazePatch(env.skyTop, env.skyHorizon), Tuning.GATE_POOL)
        coins = CoinField(scene, Tuning.COIN_POOL)
        biomes = BiomeBlender(this)
    }

    /** main.js boot: zone 0's biome without cross-fade and the equipped pipes. */
    fun boot(progress: ProgressRepository) {
        booted = true
        biomes.set(0, 0.0, WorldPicks.zoneBiome(0, progress))
        setPipeStyle(WorldPicks.equippedPipes(progress))
    }

    /** world.js `setPipeStyle`: restyles every pipe (gates and shop preview) at once. */
    fun setPipeStyle(style: PipeColors) = pipes.setStyle(style)

    /** world.js `createPipePreview(scene)`. */
    fun createPipePreview(): PipePreview = PipePreview(scene, pipes)

    override fun update(frame: FrameInfo) {
        if (!booted) boot(frame.sim.progress)
        sync(frame.dt, frame.sim.state.distance, frame.sim.gates, frame.sim.coins, frame.time)
    }

    /**
     * Mirrors one frame: ground scroll and scenery/cloud movement by the distance flown since the
     * last frame (main.js `moveWorld`), gate rows, the biome blend and the coin instances.
     */
    fun sync(dt: Double, distance: Double, gateRows: List<GateRow>, coinList: List<Coin>, time: Double = 0.0) {
        val dz = if (lastDistance.isNaN() || distance < lastDistance) 0.0 else distance - lastDistance
        lastDistance = distance
        ground.update(distance)
        if (dz > 0) {
            scenery.update(dz)
            clouds.update(dz)
        }
        gates.recordLabel = recordLabel
        gates.sync(gateRows, time)
        biomes.update(dt)
        coins.sync(coinList)
    }
}
