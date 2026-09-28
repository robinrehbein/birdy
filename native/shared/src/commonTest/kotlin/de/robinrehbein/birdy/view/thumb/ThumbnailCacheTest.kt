package de.robinrehbein.birdy.view.thumb

import de.robinrehbein.birdy.engine.RenderBackend
import de.robinrehbein.birdy.engine.RenderStats
import de.robinrehbein.birdy.engine.RenderTarget
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.meta.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThumbnailCacheTest {
    private class FakeBackend : RenderBackend {
        var renders = 0
        var lastFov = 0f
        var visibleMeshes = 0
        override fun onContextCreated() {}
        override fun setSurfaceSize(width: Int, height: Int) {}
        override var resolutionScale = 1f
        override var shadowsEnabled = true
        override fun render(scene: Scene, camera: PerspectiveCamera, target: RenderTarget?) {
            renders++
            lastFov = camera.fov
            visibleMeshes = 0
            scene.traverseVisible { if (it is Mesh) visibleMeshes++ }
        }
        override fun createRenderTarget(width: Int, height: Int): RenderTarget = object : RenderTarget {
            override val width = width
            override val height = height
            override fun dispose() {}
        }
        override fun readPixels(target: RenderTarget): IntArray = IntArray(target.width * target.height) { renders }
        override val stats = RenderStats()
        override fun dispose() {}
    }

    @Test
    fun cachesPerKindIdAndSkin() {
        val backend = FakeBackend()
        val thumbs = ThumbnailRenderer(backend, size = 16)
        val sunny = Catalog.skins[0]
        val sky = Catalog.skins[1]
        val a = thumbs.thumbnail(Kind.Hat, "crown", sunny)
        assertEquals(256, a.size)
        assertEquals(24f, backend.lastFov)
        assertSame(a, thumbs.thumbnail(Kind.Hat, "crown", sunny))
        assertEquals(1, backend.renders)
        assertEquals("hat:crown:sunny", thumbs.key(Kind.Hat, "crown", sunny))
        thumbs.thumbnail(Kind.Hat, "crown", sky)
        assertEquals(2, backend.renders)
        thumbs.thumbnail(Kind.Beak, "toucan", sky)
        assertEquals(26f, backend.lastFov)
        thumbs.invalidate(keepSkinId = "sky")
        assertEquals(2, thumbs.cachedCount)
        thumbs.invalidate()
        assertEquals(0, thumbs.cachedCount)
        assertFailsWith<IllegalArgumentException> { thumbs.thumbnail(Kind.Trail, "none", sky) }
    }

    @Test
    fun onlyTheRequestedPartIsWorn() {
        val backend = FakeBackend()
        val thumbs = ThumbnailRenderer(backend, size = 8)
        val sunny = Catalog.skins[0]
        thumbs.thumbnail(Kind.Pattern, "plain", sunny)
        val plain = backend.visibleMeshes
        thumbs.thumbnail(Kind.Hat, "tophat", sunny)
        assertEquals(plain + 3, backend.visibleMeshes)
        thumbs.thumbnail(Kind.Beak, "toucan", sunny)
        // Round beak (2 meshes) swapped for the toucan (3 meshes); the tophat is gone again.
        assertEquals(plain + 1, backend.visibleMeshes)
        assertTrue(ThumbView.BY_KIND.keys.containsAll(listOf(Kind.Pattern, Kind.Hat, Kind.Eyes, Kind.Beak)))
    }
}
