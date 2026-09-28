package de.robinrehbein.birdy.view.bird

import de.robinrehbein.birdy.engine.gl.GlRenderer
import de.robinrehbein.birdy.engine.gl.HeadlessEglContext
import de.robinrehbein.birdy.engine.gl.LwjglGl
import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.scene.DirectionalLight
import de.robinrehbein.birdy.engine.scene.HemisphereLight
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Headless GL fixture for the bird/fx render tests: a studio scene with the thumbnail light rig
 * (hemisphere 1.9 + sun 2.2 at (2,5,1)) over a sky-blue background. The same setup is used by the
 * JS reference harness (native/build/birdtest/js) so both can be compared side by side.
 */
class BirdShots(val size: Int = 384) : AutoCloseable {
    private val ctx = HeadlessEglContext()
    val renderer = GlRenderer(LwjglGl()).also { it.onContextCreated() }
    private val target = renderer.createRenderTarget(size, size, 4)

    fun studio(background: Int? = 0x9fd8f0): Scene = Scene().apply {
        this.background = background?.let { Color.hex(it) }
        add(HemisphereLight(0xffffff, 0x998866, 1.9f))
        add(DirectionalLight(0xffffff, 2.2f).apply { position.set(2f, 5f, 1f) })
    }

    /** Three-quarter front view of a bird at the origin. */
    fun birdCamera(fov: Float = 36f): PerspectiveCamera = PerspectiveCamera(fov, 1f, 0.1f, 100f).apply {
        position.set(2.6f, 1.5f, -3.4f)
        lookAt(0f, 0.12f, -0.15f)
        updateProjection()
    }

    fun render(scene: Scene, camera: PerspectiveCamera): IntArray {
        renderer.render(scene, camera, target)
        return renderer.readPixels(target)
    }

    override fun close() {
        target.dispose()
        renderer.dispose()
        ctx.close()
    }

    companion object {
        val OUT = File("../build/birdtest").apply { mkdirs() }

        fun save(name: String, argb: IntArray, w: Int, h: Int = w): File {
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            img.setRGB(0, 0, w, h, argb, 0, w)
            val f = File(OUT, "$name.png")
            ImageIO.write(img, "png", f)
            return f
        }

        /** Tiles [tiles] (each [tile] px square) into a grid [cols] wide with a label-free layout. */
        fun sheet(name: String, tiles: List<IntArray>, tile: Int, cols: Int, background: Int = 0xff2b2b33.toInt()): File {
            val rows = (tiles.size + cols - 1) / cols
            val img = BufferedImage(cols * tile, rows * tile, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.color = java.awt.Color(background, true)
            g.fillRect(0, 0, img.width, img.height)
            for ((i, t) in tiles.withIndex()) {
                val tileImg = BufferedImage(tile, tile, BufferedImage.TYPE_INT_ARGB)
                tileImg.setRGB(0, 0, tile, tile, t, 0, tile)
                g.drawImage(tileImg, (i % cols) * tile, (i / cols) * tile, null)
            }
            g.dispose()
            val f = File(OUT, "$name.png")
            ImageIO.write(img, "png", f)
            return f
        }

        /** Fraction of pixels differing from the background colour [bg]. */
        fun coverage(argb: IntArray, bg: Int): Double = argb.count { (it and 0xffffff) != (bg and 0xffffff) }.toDouble() / argb.size
    }
}
