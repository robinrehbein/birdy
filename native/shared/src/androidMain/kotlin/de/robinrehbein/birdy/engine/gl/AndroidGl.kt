package de.robinrehbein.birdy.engine.gl

import android.opengl.GLES30

/** [Gl] facade over `android.opengl.GLES30`. Must be used on the GLSurfaceView render thread. */
class AndroidGl : Gl {
    private val scratch = NioScratch()
    private val one = IntArray(1)

    override fun getError(): Int = GLES30.glGetError()
    override fun getString(name: Int): String? = GLES30.glGetString(name)
    override fun getInteger(pname: Int): Int { GLES30.glGetIntegerv(pname, one, 0); return one[0] }

    override fun viewport(x: Int, y: Int, width: Int, height: Int) = GLES30.glViewport(x, y, width, height)
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = GLES30.glClearColor(r, g, b, a)
    override fun clear(mask: Int) = GLES30.glClear(mask)
    override fun enable(cap: Int) = GLES30.glEnable(cap)
    override fun disable(cap: Int) = GLES30.glDisable(cap)
    override fun depthMask(flag: Boolean) = GLES30.glDepthMask(flag)
    override fun depthFunc(func: Int) = GLES30.glDepthFunc(func)
    override fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean) = GLES30.glColorMask(r, g, b, a)
    override fun blendFuncSeparate(srcRgb: Int, dstRgb: Int, srcAlpha: Int, dstAlpha: Int) =
        GLES30.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
    override fun blendEquation(mode: Int) = GLES30.glBlendEquation(mode)
    override fun cullFace(mode: Int) = GLES30.glCullFace(mode)
    override fun frontFace(mode: Int) = GLES30.glFrontFace(mode)
    override fun polygonOffset(factor: Float, units: Float) = GLES30.glPolygonOffset(factor, units)

    override fun createShader(type: Int): Int = GLES30.glCreateShader(type)
    override fun shaderSource(shader: Int, source: String) = GLES30.glShaderSource(shader, source)
    override fun compileShader(shader: Int) = GLES30.glCompileShader(shader)
    override fun getShaderiv(shader: Int, pname: Int): Int { GLES30.glGetShaderiv(shader, pname, one, 0); return one[0] }
    override fun getShaderInfoLog(shader: Int): String = GLES30.glGetShaderInfoLog(shader)
    override fun deleteShader(shader: Int) = GLES30.glDeleteShader(shader)
    override fun createProgram(): Int = GLES30.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = GLES30.glAttachShader(program, shader)
    override fun bindAttribLocation(program: Int, index: Int, name: String) = GLES30.glBindAttribLocation(program, index, name)
    override fun linkProgram(program: Int) = GLES30.glLinkProgram(program)
    override fun getProgramiv(program: Int, pname: Int): Int { GLES30.glGetProgramiv(program, pname, one, 0); return one[0] }
    override fun getProgramInfoLog(program: Int): String = GLES30.glGetProgramInfoLog(program)
    override fun useProgram(program: Int) = GLES30.glUseProgram(program)
    override fun deleteProgram(program: Int) = GLES30.glDeleteProgram(program)
    override fun getUniformLocation(program: Int, name: String): Int = GLES30.glGetUniformLocation(program, name)
    override fun getAttribLocation(program: Int, name: String): Int = GLES30.glGetAttribLocation(program, name)

    override fun uniform1i(location: Int, v: Int) = GLES30.glUniform1i(location, v)
    override fun uniform1f(location: Int, v: Float) = GLES30.glUniform1f(location, v)
    override fun uniform2f(location: Int, x: Float, y: Float) = GLES30.glUniform2f(location, x, y)
    override fun uniform3f(location: Int, x: Float, y: Float, z: Float) = GLES30.glUniform3f(location, x, y, z)
    override fun uniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) = GLES30.glUniform4f(location, x, y, z, w)
    override fun uniform3fv(location: Int, count: Int, v: FloatArray) = GLES30.glUniform3fv(location, count, v, 0)
    override fun uniformMatrix3fv(location: Int, count: Int, v: FloatArray) = GLES30.glUniformMatrix3fv(location, count, false, v, 0)
    override fun uniformMatrix4fv(location: Int, count: Int, v: FloatArray) = GLES30.glUniformMatrix4fv(location, count, false, v, 0)

    override fun genBuffer(): Int { GLES30.glGenBuffers(1, one, 0); return one[0] }
    override fun deleteBuffer(buffer: Int) { one[0] = buffer; GLES30.glDeleteBuffers(1, one, 0) }
    override fun bindBuffer(target: Int, buffer: Int) = GLES30.glBindBuffer(target, buffer)
    override fun bufferData(target: Int, data: FloatArray, count: Int, usage: Int) =
        GLES30.glBufferData(target, count * 4, scratch.floats(data, count), usage)
    override fun bufferData(target: Int, data: IntArray, count: Int, usage: Int) =
        GLES30.glBufferData(target, count * 4, scratch.ints(data, count), usage)
    override fun bufferSubData(target: Int, offsetBytes: Int, data: FloatArray, count: Int) =
        GLES30.glBufferSubData(target, offsetBytes, count * 4, scratch.floats(data, count))
    override fun genVertexArray(): Int { GLES30.glGenVertexArrays(1, one, 0); return one[0] }
    override fun deleteVertexArray(array: Int) { one[0] = array; GLES30.glDeleteVertexArrays(1, one, 0) }
    override fun bindVertexArray(array: Int) = GLES30.glBindVertexArray(array)
    override fun enableVertexAttribArray(index: Int) = GLES30.glEnableVertexAttribArray(index)
    override fun disableVertexAttribArray(index: Int) = GLES30.glDisableVertexAttribArray(index)
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, strideBytes: Int, offsetBytes: Int) =
        GLES30.glVertexAttribPointer(index, size, type, normalized, strideBytes, offsetBytes)
    override fun vertexAttribDivisor(index: Int, divisor: Int) = GLES30.glVertexAttribDivisor(index, divisor)
    override fun vertexAttrib3f(index: Int, x: Float, y: Float, z: Float) = GLES30.glVertexAttrib3f(index, x, y, z)

    override fun drawArrays(mode: Int, first: Int, count: Int) = GLES30.glDrawArrays(mode, first, count)
    override fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int) = GLES30.glDrawElements(mode, count, type, offsetBytes)
    override fun drawArraysInstanced(mode: Int, first: Int, count: Int, instances: Int) =
        GLES30.glDrawArraysInstanced(mode, first, count, instances)
    override fun drawElementsInstanced(mode: Int, count: Int, type: Int, offsetBytes: Int, instances: Int) =
        GLES30.glDrawElementsInstanced(mode, count, type, offsetBytes, instances)

    override fun genTexture(): Int { GLES30.glGenTextures(1, one, 0); return one[0] }
    override fun deleteTexture(texture: Int) { one[0] = texture; GLES30.glDeleteTextures(1, one, 0) }
    override fun activeTexture(unit: Int) = GLES30.glActiveTexture(unit)
    override fun bindTexture(target: Int, texture: Int) = GLES30.glBindTexture(target, texture)
    override fun texImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, pixels: ByteArray?) =
        GLES30.glTexImage2D(target, level, internalFormat, width, height, 0, format, type, pixels?.let { scratch.bytes(it) })
    override fun texParameteri(target: Int, pname: Int, param: Int) = GLES30.glTexParameteri(target, pname, param)
    override fun texParameterf(target: Int, pname: Int, param: Float) = GLES30.glTexParameterf(target, pname, param)
    override fun generateMipmap(target: Int) = GLES30.glGenerateMipmap(target)

    override fun genFramebuffer(): Int { GLES30.glGenFramebuffers(1, one, 0); return one[0] }
    override fun deleteFramebuffer(fb: Int) { one[0] = fb; GLES30.glDeleteFramebuffers(1, one, 0) }
    override fun bindFramebuffer(target: Int, fb: Int) = GLES30.glBindFramebuffer(target, fb)
    override fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int) =
        GLES30.glFramebufferTexture2D(target, attachment, texTarget, texture, level)
    override fun genRenderbuffer(): Int { GLES30.glGenRenderbuffers(1, one, 0); return one[0] }
    override fun deleteRenderbuffer(rb: Int) { one[0] = rb; GLES30.glDeleteRenderbuffers(1, one, 0) }
    override fun bindRenderbuffer(target: Int, rb: Int) = GLES30.glBindRenderbuffer(target, rb)
    override fun renderbufferStorage(target: Int, internalFormat: Int, width: Int, height: Int) =
        GLES30.glRenderbufferStorage(target, internalFormat, width, height)
    override fun renderbufferStorageMultisample(target: Int, samples: Int, internalFormat: Int, width: Int, height: Int) =
        GLES30.glRenderbufferStorageMultisample(target, samples, internalFormat, width, height)
    override fun framebufferRenderbuffer(target: Int, attachment: Int, rbTarget: Int, rb: Int) =
        GLES30.glFramebufferRenderbuffer(target, attachment, rbTarget, rb)
    override fun checkFramebufferStatus(target: Int): Int = GLES30.glCheckFramebufferStatus(target)
    override fun blitFramebuffer(srcX0: Int, srcY0: Int, srcX1: Int, srcY1: Int, dstX0: Int, dstY0: Int, dstX1: Int, dstY1: Int, mask: Int, filter: Int) =
        GLES30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter)
    override fun readBuffer(mode: Int) = GLES30.glReadBuffer(mode)
    override fun readPixels(x: Int, y: Int, width: Int, height: Int, out: ByteArray) {
        val buf = scratch.out(out.size)
        GLES30.glReadPixels(x, y, width, height, GL.RGBA, GL.UNSIGNED_BYTE, buf)
        buf.get(out)
    }
}
