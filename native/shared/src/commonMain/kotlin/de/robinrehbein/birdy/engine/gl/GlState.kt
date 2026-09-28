package de.robinrehbein.birdy.engine.gl

/**
 * Redundant-call filter for the fixed-function state the renderer toggles per draw.
 * [invalidate] forgets everything (new frame, new context, or foreign GL code ran).
 */
internal class GlState(private val gl: Gl) {
    private var depthTest: Boolean? = null
    private var depthWrite: Boolean? = null
    private var blend: Boolean? = null
    private var blendMode = -1
    private var cull: Int = -1 // 0 = off, else GL.FRONT / GL.BACK
    private var polygonOffset: Boolean? = null
    private var program = -1
    private var colorWrite: Boolean? = null

    fun invalidate() {
        depthTest = null; depthWrite = null; blend = null; blendMode = -1
        cull = -1; polygonOffset = null; program = -1; colorWrite = null
    }

    fun useProgram(id: Int) {
        if (program == id) return
        program = id
        gl.useProgram(id)
    }

    fun depthTest(on: Boolean) {
        if (depthTest == on) return
        depthTest = on
        if (on) gl.enable(GL.DEPTH_TEST) else gl.disable(GL.DEPTH_TEST)
    }

    fun depthWrite(on: Boolean) {
        if (depthWrite == on) return
        depthWrite = on
        gl.depthMask(on)
    }

    fun colorWrite(on: Boolean) {
        if (colorWrite == on) return
        colorWrite = on
        gl.colorMask(on, on, on, on)
    }

    /** [face] = 0 disables culling, otherwise the face to cull (GL.FRONT / GL.BACK). */
    fun cull(face: Int) {
        if (cull == face) return
        if (face == 0) gl.disable(GL.CULL_FACE) else {
            if (cull <= 0) gl.enable(GL.CULL_FACE)
            gl.cullFace(face)
        }
        cull = face
    }

    /** [mode]: [NO_BLEND], [NORMAL] or [ADDITIVE] (three.js non-premultiplied blend funcs). */
    fun blending(mode: Int) {
        if (mode == NO_BLEND) {
            if (blend != false) { gl.disable(GL.BLEND); blend = false }
            return
        }
        if (blend != true) { gl.enable(GL.BLEND); blend = true }
        if (blendMode == mode) return
        blendMode = mode
        gl.blendEquation(GL.FUNC_ADD)
        when (mode) {
            NORMAL -> gl.blendFuncSeparate(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA, GL.ONE, GL.ONE_MINUS_SRC_ALPHA)
            ADDITIVE -> gl.blendFuncSeparate(GL.SRC_ALPHA, GL.ONE, GL.ONE, GL.ONE)
        }
    }

    fun polygonOffset(on: Boolean, factor: Float = 0f, units: Float = 0f) {
        if (on) gl.polygonOffset(factor, units)
        if (polygonOffset == on) return
        polygonOffset = on
        if (on) gl.enable(GL.POLYGON_OFFSET_FILL) else gl.disable(GL.POLYGON_OFFSET_FILL)
    }

    companion object {
        const val NO_BLEND = 0
        const val NORMAL = 1
        const val ADDITIVE = 2
    }
}
