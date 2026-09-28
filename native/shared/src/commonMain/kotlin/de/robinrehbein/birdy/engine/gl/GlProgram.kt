package de.robinrehbein.birdy.engine.gl

/** A linked program with a lazily filled uniform-location cache. */
internal class GlProgram(
    private val gl: Gl,
    vertexSource: String,
    fragmentSource: String,
    extraAttributes: List<String>,
) {
    val id: Int
    private val locations = HashMap<String, Int>()

    /** Pass stamp of the last per-pass uniform upload (camera, lights, fog, shadow). */
    var passStamp = -1

    init {
        val vs = compile(GL.VERTEX_SHADER, vertexSource)
        val fs = try {
            compile(GL.FRAGMENT_SHADER, fragmentSource)
        } catch (e: IllegalStateException) {
            gl.deleteShader(vs)
            throw e
        }
        id = gl.createProgram()
        gl.attachShader(id, vs)
        gl.attachShader(id, fs)
        extraAttributes.forEachIndexed { i, name -> gl.bindAttribLocation(id, Attr.FIRST_EXTRA + i, name) }
        gl.linkProgram(id)
        gl.deleteShader(vs)
        gl.deleteShader(fs)
        if (gl.getProgramiv(id, GL.LINK_STATUS) == 0) {
            val log = gl.getProgramInfoLog(id)
            gl.deleteProgram(id)
            error("GL program link failed: $log")
        }
    }

    private fun compile(type: Int, source: String): Int {
        val sh = gl.createShader(type)
        gl.shaderSource(sh, source)
        gl.compileShader(sh)
        if (gl.getShaderiv(sh, GL.COMPILE_STATUS) == 0) {
            val log = gl.getShaderInfoLog(sh)
            gl.deleteShader(sh)
            val numbered = source.lines().mapIndexed { i, l -> "${i + 1}: $l" }.joinToString("\n")
            error("GL shader compile failed: $log\n$numbered")
        }
        return sh
    }

    fun loc(name: String): Int = locations.getOrPut(name) { gl.getUniformLocation(id, name) }

    fun dispose() = gl.deleteProgram(id)
}
