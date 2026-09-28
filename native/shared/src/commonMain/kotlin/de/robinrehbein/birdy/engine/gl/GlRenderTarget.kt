package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.RenderTarget

/**
 * Offscreen colour + depth framebuffer. With [samples] > 0 the scene is drawn into a
 * multisampled renderbuffer FBO and resolved into [colorTexture] on demand ([resolve]).
 * GL objects are (re)created lazily for the renderer's current context generation, so targets
 * survive an Android context loss.
 */
internal class GlRenderTarget(
    private val renderer: GlRenderer,
    override val width: Int,
    override val height: Int,
    requestedSamples: Int,
) : RenderTarget {
    var samples = requestedSamples
        private set

    /** FBO the scene is drawn into. */
    var drawFbo = 0
        private set

    /** Single-sample FBO holding [colorTexture] (== [drawFbo] without MSAA). */
    var readFbo = 0
        private set
    var colorTexture = 0
        private set
    private var msaaColor = 0
    private var depth = 0
    private var generation = -1
    private var disposed = false

    /** True when [drawFbo] has content newer than [readFbo]. */
    var dirty = false

    fun ensure(gl: GlApi) {
        check(!disposed) { "RenderTarget used after dispose()" }
        if (generation == renderer.generation) return
        generation = renderer.generation
        samples = if (samples > 0) minOf(samples, renderer.maxSamples) else 0

        colorTexture = gl.genTexture()
        gl.bindTexture(GL.TEXTURE_2D, colorTexture)
        gl.texImage2D(GL.TEXTURE_2D, 0, GL.RGBA8, width, height, GL.RGBA, GL.UNSIGNED_BYTE, null)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
        gl.bindTexture(GL.TEXTURE_2D, 0)
        readFbo = gl.genFramebuffer()
        gl.bindFramebuffer(GL.FRAMEBUFFER, readFbo)
        gl.framebufferTexture2D(GL.FRAMEBUFFER, GL.COLOR_ATTACHMENT0, GL.TEXTURE_2D, colorTexture, 0)
        checkComplete(gl)

        depth = gl.genRenderbuffer()
        gl.bindRenderbuffer(GL.RENDERBUFFER, depth)
        if (samples > 0) {
            gl.renderbufferStorageMultisample(GL.RENDERBUFFER, samples, GL.DEPTH24_STENCIL8, width, height)
            drawFbo = gl.genFramebuffer()
            gl.bindFramebuffer(GL.FRAMEBUFFER, drawFbo)
            msaaColor = gl.genRenderbuffer()
            gl.bindRenderbuffer(GL.RENDERBUFFER, msaaColor)
            gl.renderbufferStorageMultisample(GL.RENDERBUFFER, samples, GL.RGBA8, width, height)
            gl.framebufferRenderbuffer(GL.FRAMEBUFFER, GL.COLOR_ATTACHMENT0, GL.RENDERBUFFER, msaaColor)
        } else {
            gl.renderbufferStorage(GL.RENDERBUFFER, GL.DEPTH24_STENCIL8, width, height)
            drawFbo = readFbo
        }
        gl.framebufferRenderbuffer(GL.FRAMEBUFFER, GL.DEPTH_STENCIL_ATTACHMENT, GL.RENDERBUFFER, depth)
        checkComplete(gl)
        gl.bindRenderbuffer(GL.RENDERBUFFER, 0)
        gl.bindFramebuffer(GL.FRAMEBUFFER, 0)
        dirty = false
    }

    private fun checkComplete(gl: GlApi) {
        val status = gl.checkFramebufferStatus(GL.FRAMEBUFFER)
        check(status == GL.FRAMEBUFFER_COMPLETE) { "Framebuffer incomplete: 0x${status.toString(16)}" }
    }

    /** Resolves the multisampled colour into [colorTexture]. No-op without MSAA. */
    fun resolve(gl: GlApi) {
        if (!dirty) return
        dirty = false
        if (drawFbo == readFbo) return
        gl.bindFramebuffer(GL.READ_FRAMEBUFFER, drawFbo)
        gl.bindFramebuffer(GL.DRAW_FRAMEBUFFER, readFbo)
        gl.blitFramebuffer(0, 0, width, height, 0, 0, width, height, GL.COLOR_BUFFER_BIT, GL.NEAREST)
        gl.bindFramebuffer(GL.FRAMEBUFFER, 0)
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        // Names from a lost context must not be deleted: they may alias new objects.
        if (generation != renderer.generation) return
        val gl = renderer.gl
        if (drawFbo != readFbo) gl.deleteFramebuffer(drawFbo)
        gl.deleteFramebuffer(readFbo)
        gl.deleteTexture(colorTexture)
        if (msaaColor != 0) gl.deleteRenderbuffer(msaaColor)
        gl.deleteRenderbuffer(depth)
        renderer.forget(this)
    }
}
