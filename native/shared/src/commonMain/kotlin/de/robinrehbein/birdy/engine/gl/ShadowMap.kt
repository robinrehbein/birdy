package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.scene.DirectionalLight

/**
 * Depth texture + FBO for the directional light (three.js `DirectionalLightShadow` with
 * `PCFShadowMap`): `DEPTH_COMPONENT24`, compare mode `LEQUAL`, linear filtering so every tap is a
 * hardware 2x2 PCF, clamp to edge. [matrix] maps world space to shadow texture space.
 */
internal class ShadowMap(private val gl: GlApi) {
    var texture = 0
        private set
    var fbo = 0
        private set
    var size = 0
        private set

    val view = FloatArray(16)
    val projection = FloatArray(16)
    /** projection * view (light clip space). */
    val viewProjection = FloatArray(16)
    /** Bias matrix * [viewProjection] (texture space), three.js `shadow.matrix`. */
    val matrix = FloatArray(16)
    val frustum = Frustum()
    private val bias = floatArrayOf(
        0.5f, 0f, 0f, 0f,
        0f, 0.5f, 0f, 0f,
        0f, 0f, 0.5f, 0f,
        0.5f, 0.5f, 0.5f, 1f,
    )

    fun ensure(mapSize: Int) {
        if (texture != 0 && size == mapSize) return
        dispose()
        size = mapSize
        texture = gl.genTexture()
        gl.bindTexture(GL.TEXTURE_2D, texture)
        gl.texImage2D(GL.TEXTURE_2D, 0, GL.DEPTH_COMPONENT24, size, size, GL.DEPTH_COMPONENT, GL.UNSIGNED_INT, null)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_COMPARE_MODE, GL.COMPARE_REF_TO_TEXTURE)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_COMPARE_FUNC, GL.LEQUAL)
        gl.bindTexture(GL.TEXTURE_2D, 0)
        fbo = gl.genFramebuffer()
        gl.bindFramebuffer(GL.FRAMEBUFFER, fbo)
        gl.framebufferTexture2D(GL.FRAMEBUFFER, GL.DEPTH_ATTACHMENT, GL.TEXTURE_2D, texture, 0)
        val status = gl.checkFramebufferStatus(GL.FRAMEBUFFER)
        gl.bindFramebuffer(GL.FRAMEBUFFER, 0)
        check(status == GL.FRAMEBUFFER_COMPLETE) { "Shadow framebuffer incomplete: 0x${status.toString(16)}" }
    }

    /** three.js `LightShadow.updateMatrices`: ortho camera at the light, looking at its target. */
    fun updateMatrices(light: DirectionalLight) {
        val w = light.worldMatrix.e
        val t = light.target
        GlMath.lookAtView(view, w[12], w[13], w[14], t.x, t.y, t.z)
        val s = light.shadow
        GlMath.ortho(projection, s.left, s.right, s.top, s.bottom, s.near, s.far)
        GlMath.mul(viewProjection, projection, view)
        GlMath.mul(matrix, bias, viewProjection)
        frustum.set(viewProjection)
    }

    /** Forgets GL names without deleting them (context loss). */
    fun forget() {
        texture = 0; fbo = 0; size = 0
    }

    fun dispose() {
        if (texture != 0) gl.deleteTexture(texture)
        if (fbo != 0) gl.deleteFramebuffer(fbo)
        forget()
    }
}
