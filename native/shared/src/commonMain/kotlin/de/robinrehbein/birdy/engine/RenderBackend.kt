package de.robinrehbein.birdy.engine

import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene

/**
 * Platform-neutral renderer API. The Android and desktop implementation is
 * [de.robinrehbein.birdy.engine.gl.GlRenderer] on top of the [de.robinrehbein.birdy.engine.gl.Gl]
 * facade; an iOS Metal backend would implement this interface directly.
 *
 * All methods must be called on the thread that owns the graphics context.
 */
interface RenderBackend {
    /** (Re)creates all GPU objects. Called when a context is created or was lost (Android). */
    fun onContextCreated()

    /** Size of the default framebuffer in physical pixels. */
    fun setSurfaceSize(width: Int, height: Int)

    /**
     * Fraction of the surface resolution the 3D scene is rendered at (quality tiers,
     * main-a.md §2.1: QUALITY_DPR). 1 = native; the backend upscales when < 1.
     * Only applies to the default framebuffer; offscreen targets render at their own size.
     */
    var resolutionScale: Float

    /** Directional-light shadow pass on/off (quality tier 4 disables it). */
    var shadowsEnabled: Boolean

    /** Renders [scene] from [camera] into [target], or the default framebuffer when null. */
    fun render(scene: Scene, camera: PerspectiveCamera, target: RenderTarget? = null)

    /** Offscreen colour+depth target (thumbnails, headless screenshots). */
    fun createRenderTarget(width: Int, height: Int): RenderTarget

    /**
     * Offscreen target with [samples]x MSAA (clamped to what the device supports; 0 = none), the
     * equivalent of `WebGLRenderer({ antialias: true })` for screenshots.
     */
    fun createRenderTarget(width: Int, height: Int, samples: Int): RenderTarget = createRenderTarget(width, height)

    /** Reads [target] back as ARGB_8888 ints, top row first (ready for Bitmap/BufferedImage). */
    fun readPixels(target: RenderTarget): IntArray

    /** Per-frame counters for the dev FPS overlay and budget tests. */
    val stats: RenderStats

    fun dispose()
}

/** Offscreen framebuffer handle created by [RenderBackend.createRenderTarget]. */
interface RenderTarget {
    val width: Int
    val height: Int
    fun dispose()
}

/** Counters reset at the start of every [RenderBackend.render]. */
class RenderStats {
    /**
     * All draw calls of the frame, shadow pass included, like three.js
     * `renderer.info.render.calls` shown by the JS dev overlay.
     */
    var drawCalls = 0
    /** Triangles submitted, shadow pass included (three.js `info.render.triangles`). */
    var triangles = 0
    /** Linked programs currently cached. */
    var programs = 0
    /** Draw calls of the shadow-map pass. */
    var shadowDrawCalls = 0
    /** Meshes skipped by frustum culling in the main pass. */
    var culled = 0
}
