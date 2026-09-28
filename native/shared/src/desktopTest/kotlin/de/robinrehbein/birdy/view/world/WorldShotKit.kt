package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.RenderTarget
import de.robinrehbein.birdy.engine.gl.GL
import de.robinrehbein.birdy.engine.gl.GlRenderer
import de.robinrehbein.birdy.engine.gl.HeadlessEglContext
import de.robinrehbein.birdy.engine.gl.LwjglGl
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.game.Coin
import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.sim.GateRows
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.fail

/** Headless world renderer for the world view tests (540x1200, like a 9:20 phone at half res). */
internal class WorldShotKit(val width: Int = 540, val height: Int = 1200) : AutoCloseable {
    private val ctx = HeadlessEglContext()
    val gl = LwjglGl()
    val renderer = GlRenderer(gl).also { it.onContextCreated() }
    private val target: RenderTarget = renderer.createRenderTarget(width, height, 4)

    val scene = Scene()
    val world = WorldView(Random(4242)).also { it.attach(scene) }
    val gates = List(Tuning.GATE_POOL) { GateRow() }
    val coins = List(Tuning.COIN_POOL) { Coin() }

    /** The in-run camera of main.js `updateCamera` for bird height [y] (74° on narrow portrait). */
    fun runCamera(y: Double = 5.0) = PerspectiveCamera(74f, width.toFloat() / height, 0.1f, 400f).apply {
        position.set(0f, (7.5 + y * 0.6).toFloat(), 14f)
        updateProjection()
        lookAt(0f, (1.8 + y * 0.6).toFloat(), -22f)
    }

    /** Configures pooled row [i] at [z] and poses it for [time]/[beat] like the simulation does. */
    fun row(i: Int, z: Double, spec: List<GapSpec?>, cloud: Int, time: Double, beat: Double): GateRow {
        val r = gates[i]
        r.active = true
        GateRows.configure(r, z, spec, cloud)
        GateRows.update(r, time, beat)
        return r
    }

    fun coin(i: Int, x: Double, y: Double, z: Double, spin: Double = 0.6) {
        val c = coins[i]
        c.active = true; c.visible = true
        c.x = x; c.y = y; c.z = z; c.spin = spin; c.scale = 1.0
    }

    fun sync(dt: Double = 1.0 / 30, distance: Double = 0.0) = world.sync(dt, distance, gates, coins)

    fun render(camera: PerspectiveCamera = runCamera()): Shot {
        renderer.render(scene, camera, target)
        val err = gl.getError()
        if (err != GL.NO_ERROR) fail("GL error 0x${err.toString(16)}")
        return Shot(renderer.readPixels(target), width, height)
    }

    override fun close() {
        target.dispose()
        renderer.dispose()
        ctx.close()
    }
}

internal class Shot(val argb: IntArray, val width: Int, val height: Int) {
    operator fun get(x: Int, y: Int): Int = argb[y * width + x]

    fun save(name: String): Shot {
        val dir = File("../build/worldtest").apply { mkdirs() }
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, width, height, argb, 0, width)
        ImageIO.write(img, "png", File(dir, "$name.png"))
        return this
    }

    /** Mean colour of a [w]x[h] box at ([x], [y]). */
    fun mean(x: Int, y: Int, w: Int = 8, h: Int = 8): Int {
        var r = 0L; var g = 0L; var b = 0L
        for (j in y until y + h) for (i in x until x + w) {
            val c = this[i, j]
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
        }
        val n = w * h
        return ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
}

internal fun red(c: Int) = (c shr 16) and 0xFF
internal fun green(c: Int) = (c shr 8) and 0xFF
internal fun blue(c: Int) = c and 0xFF
internal fun hexOf(c: Int) = "#" + (c and 0xFFFFFF).toString(16).padStart(6, '0')
internal fun near(a: Int, b: Int, tol: Int) =
    kotlin.math.abs(red(a) - red(b)) <= tol && kotlin.math.abs(green(a) - green(b)) <= tol && kotlin.math.abs(blue(a) - blue(b)) <= tol
