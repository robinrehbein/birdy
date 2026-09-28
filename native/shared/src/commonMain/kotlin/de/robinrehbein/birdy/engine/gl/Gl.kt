package de.robinrehbein.birdy.engine.gl

/**
 * Minimal OpenGL ES 3.0 facade. All renderer code talks to this interface so the exact same
 * renderer + GLSL ES 3.00 shaders run on Android (`android.opengl.GLES30`, [AndroidGl]) and on
 * the desktop JVM (LWJGL GLES over Mesa EGL surfaceless, `LwjglGl`), and could run on iOS via
 * OpenGLES.framework.
 *
 * Conventions: object names are Ints (0 = none); array uploads take Kotlin arrays and an element
 * count (implementations copy into direct buffers as needed); byte offsets are in bytes.
 * Add methods here (and to every implementation) rather than calling platform GL directly.
 */
interface GlApi {
    fun getError(): Int
    fun getString(name: Int): String?
    /** `glGetIntegerv` for a single-valued [pname]. */
    fun getInteger(pname: Int): Int

    // --- state ---
    fun viewport(x: Int, y: Int, width: Int, height: Int)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clear(mask: Int)
    fun enable(cap: Int)
    fun disable(cap: Int)
    fun depthMask(flag: Boolean)
    fun depthFunc(func: Int)
    fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean)
    fun blendFuncSeparate(srcRgb: Int, dstRgb: Int, srcAlpha: Int, dstAlpha: Int)
    fun blendEquation(mode: Int)
    fun cullFace(mode: Int)
    fun frontFace(mode: Int)
    fun polygonOffset(factor: Float, units: Float)

    // --- shaders ---
    fun createShader(type: Int): Int
    fun shaderSource(shader: Int, source: String)
    fun compileShader(shader: Int)
    fun getShaderiv(shader: Int, pname: Int): Int
    fun getShaderInfoLog(shader: Int): String
    fun deleteShader(shader: Int)
    fun createProgram(): Int
    fun attachShader(program: Int, shader: Int)
    fun bindAttribLocation(program: Int, index: Int, name: String)
    fun linkProgram(program: Int)
    fun getProgramiv(program: Int, pname: Int): Int
    fun getProgramInfoLog(program: Int): String
    fun useProgram(program: Int)
    fun deleteProgram(program: Int)
    fun getUniformLocation(program: Int, name: String): Int
    fun getAttribLocation(program: Int, name: String): Int

    fun uniform1i(location: Int, v: Int)
    fun uniform1f(location: Int, v: Float)
    fun uniform2f(location: Int, x: Float, y: Float)
    fun uniform3f(location: Int, x: Float, y: Float, z: Float)
    fun uniform4f(location: Int, x: Float, y: Float, z: Float, w: Float)
    fun uniform3fv(location: Int, count: Int, v: FloatArray)
    fun uniformMatrix3fv(location: Int, count: Int, v: FloatArray)
    fun uniformMatrix4fv(location: Int, count: Int, v: FloatArray)

    // --- buffers / vertex arrays ---
    fun genBuffer(): Int
    fun deleteBuffer(buffer: Int)
    fun bindBuffer(target: Int, buffer: Int)
    /** Uploads the first [count] floats of [data]. */
    fun bufferData(target: Int, data: FloatArray, count: Int, usage: Int)
    /** Uploads the first [count] ints of [data] (element arrays use GL_UNSIGNED_INT). */
    fun bufferData(target: Int, data: IntArray, count: Int, usage: Int)
    fun bufferSubData(target: Int, offsetBytes: Int, data: FloatArray, count: Int)
    fun genVertexArray(): Int
    fun deleteVertexArray(array: Int)
    fun bindVertexArray(array: Int)
    fun enableVertexAttribArray(index: Int)
    fun disableVertexAttribArray(index: Int)
    fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, strideBytes: Int, offsetBytes: Int)
    fun vertexAttribDivisor(index: Int, divisor: Int)
    fun vertexAttrib3f(index: Int, x: Float, y: Float, z: Float)

    // --- draw ---
    fun drawArrays(mode: Int, first: Int, count: Int)
    fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int)
    fun drawArraysInstanced(mode: Int, first: Int, count: Int, instances: Int)
    fun drawElementsInstanced(mode: Int, count: Int, type: Int, offsetBytes: Int, instances: Int)

    // --- textures ---
    fun genTexture(): Int
    fun deleteTexture(texture: Int)
    fun activeTexture(unit: Int)
    fun bindTexture(target: Int, texture: Int)
    /** [pixels] may be null to allocate storage only. */
    fun texImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, pixels: ByteArray?)
    fun texParameteri(target: Int, pname: Int, param: Int)
    fun texParameterf(target: Int, pname: Int, param: Float)
    fun generateMipmap(target: Int)

    // --- framebuffers ---
    fun genFramebuffer(): Int
    fun deleteFramebuffer(fb: Int)
    fun bindFramebuffer(target: Int, fb: Int)
    fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int)
    fun genRenderbuffer(): Int
    fun deleteRenderbuffer(rb: Int)
    fun bindRenderbuffer(target: Int, rb: Int)
    fun renderbufferStorage(target: Int, internalFormat: Int, width: Int, height: Int)
    fun renderbufferStorageMultisample(target: Int, samples: Int, internalFormat: Int, width: Int, height: Int)
    fun framebufferRenderbuffer(target: Int, attachment: Int, rbTarget: Int, rb: Int)
    fun checkFramebufferStatus(target: Int): Int
    fun blitFramebuffer(srcX0: Int, srcY0: Int, srcX1: Int, srcY1: Int, dstX0: Int, dstY0: Int, dstX1: Int, dstY1: Int, mask: Int, filter: Int)
    fun readBuffer(mode: Int)
    /** Reads RGBA/UNSIGNED_BYTE pixels (bottom row first, GL convention) into [out]. */
    fun readPixels(x: Int, y: Int, width: Int, height: Int, out: ByteArray)
}
