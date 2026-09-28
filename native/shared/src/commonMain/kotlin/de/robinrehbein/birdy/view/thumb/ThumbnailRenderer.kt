package de.robinrehbein.birdy.view.thumb

import de.robinrehbein.birdy.engine.RenderBackend
import de.robinrehbein.birdy.engine.RenderTarget
import de.robinrehbein.birdy.engine.scene.DirectionalLight
import de.robinrehbein.birdy.engine.scene.HemisphereLight
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.meta.SkinItem
import de.robinrehbein.birdy.view.bird.BirdLook
import de.robinrehbein.birdy.view.bird.BirdRig

/** Per-kind camera rig of the shop thumbnails (main.js `THUMB_VIEW`). */
data class ThumbView(val cam: FloatArray, val look: FloatArray, val fov: Float) {
    companion object {
        val BY_KIND: Map<Kind, ThumbView> = mapOf(
            Kind.Pattern to ThumbView(floatArrayOf(1.9f, 1.4f, 2.4f), floatArrayOf(0f, 0.05f, 0.1f), 30f),
            Kind.Hat to ThumbView(floatArrayOf(1.5f, 1.4f, -2.3f), floatArrayOf(0f, 0.62f, -0.12f), 24f),
            Kind.Eyes to ThumbView(floatArrayOf(1.1f, 0.5f, -2.5f), floatArrayOf(0.1f, 0.24f, -0.4f), 22f),
            Kind.Beak to ThumbView(floatArrayOf(2.3f, 0.4f, -1.7f), floatArrayOf(0f, -0.02f, -0.75f), 26f),
        )
    }
}

/**
 * Shop tile previews for workshop parts (main.js `thumbUrl`, main-b.md §7.1): a bird wearing just
 * one part (other categories at their defaults) in the current skin, rendered offscreen with its
 * own light rig and read back as ARGB_8888 (top row first, transparent background).
 *
 * Results are cached per `kind:id:skinId`, so a skin change naturally renders fresh tiles; call
 * [invalidate] to drop everything (e.g. after a GL context loss the cache stays valid, since the
 * pixels live on the CPU). Game/GL thread only. The first call lazily creates the scene and target.
 *
 * `hat:none` gets a thumbnail too; the UI shows a cross icon for it instead (JS special case).
 */
class ThumbnailRenderer(private val renderer: RenderBackend, val size: Int = 192, private val samples: Int = 4) {
    private val cache = HashMap<String, IntArray>()
    private var kit: Kit? = null

    private class Kit(val scene: Scene, val bird: BirdRig, val camera: PerspectiveCamera, val target: RenderTarget)

    /** Number of cached tiles. */
    val cachedCount: Int get() = cache.size

    fun key(kind: Kind, id: String, skin: SkinItem): String = "${kind.id}:$id:${skin.id}"

    /** Returns the cached tile or renders it now. [kind] must be pattern, hat, eyes or beak. */
    fun thumbnail(kind: Kind, id: String, skin: SkinItem): IntArray {
        val key = key(kind, id, skin)
        cache[key]?.let { return it }
        val view = ThumbView.BY_KIND[kind] ?: throw IllegalArgumentException("no thumbnail rig for $kind")
        val k = kit ?: createKit().also { kit = it }
        k.bird.setSkin(skin)
        k.bird.setLook(BirdLook().with(kind, id))
        k.camera.fov = view.fov
        k.camera.updateProjection()
        k.camera.position.set(view.cam[0], view.cam[1], view.cam[2])
        k.camera.lookAt(view.look[0], view.look[1], view.look[2])
        renderer.render(k.scene, k.camera, k.target)
        val pixels = renderer.readPixels(k.target)
        cache[key] = pixels
        return pixels
    }

    /** Drops cached tiles (all, or only those not rendered with [keepSkinId]). */
    fun invalidate(keepSkinId: String? = null) {
        if (keepSkinId == null) cache.clear() else cache.keys.removeAll { !it.endsWith(":$keepSkinId") }
    }

    fun dispose() {
        kit?.target?.dispose()
        kit = null
        cache.clear()
    }

    private fun createKit(): Kit {
        val scene = Scene().apply { background = null }
        scene.add(HemisphereLight(0xffffff, 0x998866, 1.9f))
        scene.add(DirectionalLight(0xffffff, 2.2f).apply { position.set(2f, 5f, 1f) })
        val bird = BirdRig()
        bird.animateWings(1.2)
        scene.add(bird.root)
        val camera = PerspectiveCamera(34f, 1f, 0.1f, 30f)
        return Kit(scene, bird, camera, renderer.createRenderTarget(size, size, samples))
    }
}
