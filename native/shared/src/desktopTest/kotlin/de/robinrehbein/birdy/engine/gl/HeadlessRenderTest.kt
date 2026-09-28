package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import kotlin.test.Test
import kotlin.test.assertEquals

/** Renders through the real GLSL ES pipeline on Mesa llvmpipe (EGL surfaceless). */
class HeadlessRenderTest {
    @Test
    fun clearsAndDrawsUnlitMesh() = HeadlessEglContext().use {
        val renderer = GlRenderer(LwjglGl())
        renderer.onContextCreated()
        val target = renderer.createRenderTarget(64, 64)
        val scene = Scene().apply { background = Color.hex(0x4ec0ca) }
        scene.add(Mesh(TestGeo.box(1f, 1f, 1f), BasicMaterial().apply { color.setHex(0xff0000) }).apply {
            position.set(0f, 0f, -3f)
        })
        val camera = PerspectiveCamera(60f, 1f, 0.1f, 100f).apply { updateProjection() }
        renderer.render(scene, camera, target)
        val px = renderer.readPixels(target)
        assertEquals(0xff4ec0ca.toInt(), px[0])
        assertEquals(0xffff0000.toInt(), px[32 * 64 + 32])
        target.dispose()
        renderer.dispose()
    }
}
