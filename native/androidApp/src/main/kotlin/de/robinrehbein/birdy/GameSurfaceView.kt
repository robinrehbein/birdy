package de.robinrehbein.birdy

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import de.robinrehbein.birdy.game.BirdyGame
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10

/**
 * GLES 3.0 surface. Its render thread is the game thread: every [BirdyGame] frame (simulation +
 * rendering) runs here; the UI thread only posts commands and observes state.
 */
@SuppressLint("ViewConstructor")
class GameSurfaceView(context: Context, private val game: BirdyGame) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MsaaConfigChooser())
        preserveEGLContextOnPause = true
        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) = game.onSurfaceCreated()
            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = game.onSurfaceChanged(width, height)
            override fun onDrawFrame(gl: GL10?) = game.frame(System.nanoTime())
        })
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

/**
 * RGBA8888 + 24-bit depth + 4x MSAA (the JS `antialias: true` canvas), then progressively weaker
 * configs: no MSAA, 16-bit depth, no alpha (RGB888), RGB565 and finally any GLES 3 window config.
 * The manifest only guarantees GLES 3.0, not an alpha-capable or multisampled surface.
 */
private class MsaaConfigChooser : GLSurfaceView.EGLConfigChooser {
    private class Attempt(val rgba: IntArray, val depth: Int, val samples: Int)

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        for (attempt in ATTEMPTS) choose(egl, display, attempt)?.let { return it }
        throw IllegalStateException("No GLES 3 EGL config")
    }

    private fun choose(egl: EGL10, display: EGLDisplay, at: Attempt): EGLConfig? {
        val attribs = intArrayOf(
            EGL10.EGL_RED_SIZE, at.rgba[0],
            EGL10.EGL_GREEN_SIZE, at.rgba[1],
            EGL10.EGL_BLUE_SIZE, at.rgba[2],
            EGL10.EGL_ALPHA_SIZE, at.rgba[3],
            EGL10.EGL_DEPTH_SIZE, at.depth,
            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
            EGL10.EGL_SAMPLE_BUFFERS, if (at.samples > 0) 1 else 0,
            EGL10.EGL_SAMPLES, at.samples,
            EGL10.EGL_NONE,
        )
        val count = IntArray(1)
        if (!egl.eglChooseConfig(display, attribs, null, 0, count) || count[0] <= 0) return null
        val configs = arrayOfNulls<EGLConfig>(count[0])
        if (!egl.eglChooseConfig(display, attribs, configs, configs.size, count)) return null
        val found = configs.filterNotNull()
        // eglChooseConfig sorts deeper colour buffers first; prefer an exact channel-size match.
        return found.firstOrNull { c ->
            CHANNELS.indices.all { attr(egl, display, c, CHANNELS[it]) == at.rgba[it] }
        } ?: found.firstOrNull()
    }

    private fun attr(egl: EGL10, display: EGLDisplay, config: EGLConfig, name: Int): Int {
        val v = IntArray(1)
        return if (egl.eglGetConfigAttrib(display, config, name, v)) v[0] else 0
    }

    private companion object {
        const val EGL_OPENGL_ES3_BIT = 0x40
        val CHANNELS = intArrayOf(EGL10.EGL_RED_SIZE, EGL10.EGL_GREEN_SIZE, EGL10.EGL_BLUE_SIZE, EGL10.EGL_ALPHA_SIZE)
        private val RGBA8 = intArrayOf(8, 8, 8, 8)
        private val RGB8 = intArrayOf(8, 8, 8, 0)
        private val RGB565 = intArrayOf(5, 6, 5, 0)
        private val ANY = intArrayOf(0, 0, 0, 0)
        val ATTEMPTS = listOf(
            Attempt(RGBA8, 24, 4),
            Attempt(RGBA8, 24, 0),
            Attempt(RGBA8, 16, 0),
            Attempt(RGB8, 24, 4),
            Attempt(RGB8, 24, 0),
            Attempt(RGB8, 16, 0),
            Attempt(RGB565, 16, 0),
            Attempt(ANY, 16, 0),
            Attempt(ANY, 0, 0),
        )
    }
}
