package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.RenderTarget
import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.assertTrue
import kotlin.test.fail

/** Headless GL fixture: one EGL context + renderer + target per test. */
internal class GlFixture(val width: Int = 64, val height: Int = 64, samples: Int = 0) : AutoCloseable {
    private val ctx = HeadlessEglContext()
    val gl = LwjglGl()
    val renderer = GlRenderer(gl).also { it.onContextCreated() }
    val target: RenderTarget = renderer.createRenderTarget(width, height, samples)

    fun camera(fov: Float = 60f) = PerspectiveCamera(fov, width.toFloat() / height, 0.1f, 400f).apply { updateProjection() }

    fun render(scene: Scene, camera: PerspectiveCamera): Pixels {
        renderer.render(scene, camera, target)
        val err = gl.getError()
        if (err != GL.NO_ERROR) fail("GL error 0x${err.toString(16)}")
        return Pixels(renderer.readPixels(target), width, height)
    }

    override fun close() {
        target.dispose()
        renderer.dispose()
        ctx.close()
    }
}

internal class Pixels(val argb: IntArray, val width: Int, val height: Int) {
    operator fun get(x: Int, y: Int): Int = argb[y * width + x]
    fun center(): Int = this[width / 2, height / 2]

    fun save(name: String): Pixels {
        val dir = File("../build/gltest").apply { mkdirs() }
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, width, height, argb, 0, width)
        ImageIO.write(img, "png", File(dir, "$name.png"))
        return this
    }
}

internal fun r(c: Int) = (c shr 16) and 0xFF
internal fun g(c: Int) = (c shr 8) and 0xFF
internal fun b(c: Int) = c and 0xFF
internal fun luma(c: Int) = 0.2126 * r(c) + 0.7152 * g(c) + 0.0722 * b(c)
internal fun hex(c: Int) = "#" + (c and 0xFFFFFF).toString(16).padStart(6, '0')

internal fun assertColor(expected: Int, actual: Int, tolerance: Int = 1, what: String = "") {
    val ok = abs(r(expected) - r(actual)) <= tolerance && abs(g(expected) - g(actual)) <= tolerance &&
        abs(b(expected) - b(actual)) <= tolerance
    assertTrue(ok, "$what expected ${hex(expected)} but was ${hex(actual)} (tolerance $tolerance)")
}

/** Colour maths on the CPU side of the tests (three.js formulas). */
internal object Ref {
    fun lin(c: Float): Double = Color.srgbToLinear(c).toDouble()
    fun srgb(l: Double): Double = if (l <= 0.0031308) l * 12.92 else 1.055 * l.pow(0.41666) - 0.055
    fun byte(v: Double): Int = (v.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()
    fun rgb(r: Double, g: Double, b: Double): Int = (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    fun fromLinear(r: Double, g: Double, b: Double): Int = rgb(srgb(r), srgb(g), srgb(b))
}

/** Small geometry builders so the GL tests don't depend on the (separately owned) mesh module. */
internal object TestGeo {
    /** Unit quad in the XY plane facing +Z, uv (0,0) bottom-left. */
    fun quad(w: Float = 1f, h: Float = 1f): Geometry {
        val x = w / 2; val y = h / 2
        return Geometry(
            positions = floatArrayOf(-x, -y, 0f, x, -y, 0f, x, y, 0f, -x, y, 0f),
            normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
            uvs = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f),
            indices = intArrayOf(0, 1, 2, 0, 2, 3),
        )
    }

    /** Non-indexed triangles with face normals from [positions]. */
    fun flat(positions: FloatArray): Geometry {
        val n = FloatArray(positions.size)
        for (t in 0 until positions.size / 9) {
            val o = t * 9
            val ax = positions[o + 3] - positions[o]; val ay = positions[o + 4] - positions[o + 1]; val az = positions[o + 5] - positions[o + 2]
            val bx = positions[o + 6] - positions[o]; val by = positions[o + 7] - positions[o + 1]; val bz = positions[o + 8] - positions[o + 2]
            var nx = ay * bz - az * by; var ny = az * bx - ax * bz; var nz = ax * by - ay * bx
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            nx /= l; ny /= l; nz /= l
            for (v in 0 until 3) { n[o + v * 3] = nx; n[o + v * 3 + 1] = ny; n[o + v * 3 + 2] = nz }
        }
        return Geometry(positions = positions, normals = n)
    }

    fun box(sx: Float = 1f, sy: Float = 1f, sz: Float = 1f): Geometry {
        val x = sx / 2; val y = sy / 2; val z = sz / 2
        val c = arrayOf(
            floatArrayOf(-x, -y, z), floatArrayOf(x, -y, z), floatArrayOf(x, y, z), floatArrayOf(-x, y, z),
            floatArrayOf(-x, -y, -z), floatArrayOf(x, -y, -z), floatArrayOf(x, y, -z), floatArrayOf(-x, y, -z),
        )
        val faces = arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(5, 4, 7, 6), intArrayOf(1, 5, 6, 2),
            intArrayOf(4, 0, 3, 7), intArrayOf(3, 2, 6, 7), intArrayOf(4, 5, 1, 0),
        )
        val p = ArrayList<Float>()
        for (f in faces) for (i in intArrayOf(0, 1, 2, 0, 2, 3)) c[f[i]].forEach { p += it }
        return flat(p.toFloatArray())
    }

    /** UV sphere with smooth normals (three.js SphereGeometry layout). */
    fun sphere(radius: Float = 1f, ws: Int = 48, hs: Int = 32): Geometry {
        val pos = ArrayList<Float>(); val nor = ArrayList<Float>(); val uv = ArrayList<Float>()
        for (iy in 0..hs) {
            val v = iy.toFloat() / hs
            for (ix in 0..ws) {
                val u = ix.toFloat() / ws
                val x = -cos(u * 2 * PI) * sin(v * PI)
                val y = cos(v * PI)
                val z = sin(u * 2 * PI) * sin(v * PI)
                pos += (radius * x).toFloat(); pos += (radius * y).toFloat(); pos += (radius * z).toFloat()
                nor += x.toFloat(); nor += y.toFloat(); nor += z.toFloat()
                uv += u; uv += 1 - v
            }
        }
        val idx = ArrayList<Int>()
        for (iy in 0 until hs) for (ix in 0 until ws) {
            val a = iy * (ws + 1) + ix + 1
            val b = iy * (ws + 1) + ix
            val c = (iy + 1) * (ws + 1) + ix
            val d = (iy + 1) * (ws + 1) + ix + 1
            if (iy != 0) { idx += a; idx += b; idx += d }
            if (iy != hs - 1) { idx += b; idx += c; idx += d }
        }
        return Geometry(pos.toFloatArray(), nor.toFloatArray(), null, uv.toFloatArray(), idx.toIntArray())
    }
}
