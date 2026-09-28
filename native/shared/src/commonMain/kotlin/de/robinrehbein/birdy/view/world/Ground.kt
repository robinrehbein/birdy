package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.engine.scene.ShaderPatch
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.engine.scene.Texture
import de.robinrehbein.birdy.engine.scene.Wrap
import de.robinrehbein.birdy.engine.texture.RasterCanvas
import de.robinrehbein.birdy.game.WorldConst
import kotlin.math.PI

/** The canvas textures of the ground (world.js:73-133). */
object GroundTextures {
    const val WIDTH = 512
    const val HEIGHT = 128
    private const val X0 = 150.0
    private const val X1 = 362.0

    /** world.js `drawGround(g, [road, stripe, edge])`. */
    fun drawGround(g: RasterCanvas, palette: List<Int>) {
        val (road, stripe, edge) = palette
        // The grass beside the track stays transparent: the grass plane below shows through.
        g.clearRect(0.0, 0.0, 512.0, 128.0)
        g.setFillStyle(road)
        g.fillRect(X0, 0.0, X1 - X0, 128.0)
        g.setStrokeStyle(stripe)
        g.lineWidth = 14.0
        var i = -128
        while (i < 256) {
            g.beginPath()
            g.moveTo(X0, i.toDouble())
            g.lineTo(X1, i + 128.0)
            g.stroke()
            i += 32
        }
        // Dashed lane dividers (lanes at x = -3, 0, 3; the texture spans 26 units).
        g.setFillStyle("rgba(255, 252, 235, 0.75)")
        for (x in doubleArrayOf(226.5, 285.5)) g.fillRect(x - 2.5, 8.0, 5.0, 48.0)
        g.setFillStyle(edge)
        g.fillRect(X0 - 10, 0.0, 10.0, 128.0)
        g.fillRect(X1, 0.0, 10.0, 128.0)
        g.setFillStyle("#543847")
        g.fillRect(X0 - 14, 0.0, 4.0, 128.0)
        g.fillRect(X1 + 10, 0.0, 4.0, 128.0)
    }

    /** world.js `makeGrassTexture()`: grey stripes tinted by the grass material colour. */
    fun drawGrass(): RasterCanvas {
        val g = RasterCanvas(4, 128)
        g.setFillStyle("#ffffff")
        g.fillRect(0.0, 0.0, 4.0, 128.0)
        g.setFillStyle("#e0e6d8")
        var y = 0
        while (y < 128) {
            g.fillRect(0.0, y.toDouble(), 4.0, 16.0)
            y += 32
        }
        return g
    }
}

/**
 * world.js `createGround(scene)`: the textured track (alpha-tested, repainted per road palette)
 * over a wide striped grass plane; both scroll by texture offset with the distance flown.
 */
class Ground(scene: Scene) {
    private val canvas = RasterCanvas(GroundTextures.WIDTH, GroundTextures.HEIGHT)
    val trackTexture: Texture
    val grassTexture: Texture
    val trackMat = StandardMaterial()
    val grassMat = StandardMaterial()
    val track: Mesh
    val grass: Mesh
    private var roadKey = WorldLook.SAND_ROAD

    /** Number of times the track canvas was repainted (tests; biomes repaint in 1/8 steps). */
    var repaints = 0
        private set

    init {
        GroundTextures.drawGround(canvas, WorldLook.SAND_ROAD)
        trackTexture = canvas.toTexture().apply {
            wrapS = Wrap.Clamp
            wrapT = Wrap.Repeat
            repeatU = 1f
            repeatV = (LENGTH / WorldConst.GROUND_TILE).toFloat()
        }
        trackMat.map = trackTexture
        trackMat.roughness = 1f
        trackMat.patch = ALPHA_TEST
        track = Mesh(Primitives.plane(WIDTH, LENGTH), trackMat, "track").apply {
            rotation.x = (-PI / 2).toFloat()
            position.z = (-LENGTH / 2 + 30).toFloat()
            receiveShadow = true
        }
        scene.add(track)

        grassTexture = GroundTextures.drawGrass().toTexture().apply {
            wrapS = Wrap.Repeat
            wrapT = Wrap.Repeat
            repeatU = 1f
            repeatV = (GRASS_SIZE / WorldConst.GROUND_TILE).toFloat()
        }
        grassMat.map = grassTexture
        grassMat.color.setHex(0x73bf2e)
        grassMat.roughness = 1f
        grass = Mesh(Primitives.plane(GRASS_SIZE, GRASS_SIZE), grassMat, "grass").apply {
            rotation.x = (-PI / 2).toFloat()
            position.y = -0.02f
            receiveShadow = true
        }
        scene.add(grass)
    }

    /** Current road palette painted into the track texture. */
    val road: List<Int> get() = roadKey

    /** Repaints the track in [palette] (only when it actually changed). */
    fun setRoad(palette: List<Int>) {
        if (palette == roadKey) return
        roadKey = palette.toList()
        GroundTextures.drawGround(canvas, palette)
        canvas.writeTo(trackTexture)
        repaints++
    }

    /** Scrolls both textures with the total [distance] flown. */
    fun update(distance: Double) {
        val off = ((distance / WorldConst.GROUND_TILE) % 1).toFloat()
        trackTexture.offsetV = off
        grassTexture.offsetV = off
    }

    companion object {
        /** The texture track (x0..x1) maps to about -5.3..5.3. */
        const val WIDTH = 26.0
        const val LENGTH = 400.0
        const val GRASS_SIZE = 600.0

        /** three.js `alphaTest: 0.5`. */
        val ALPHA_TEST = ShaderPatch(key = "alpha-test-0.5", fragmentColor = "if ( diffuseColor.a < 0.5 ) discard;")
    }
}
