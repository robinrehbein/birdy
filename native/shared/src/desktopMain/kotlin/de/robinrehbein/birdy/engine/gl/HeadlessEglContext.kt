package de.robinrehbein.birdy.engine.gl

import org.lwjgl.egl.EGL
import org.lwjgl.egl.EGL10.EGL_NONE
import org.lwjgl.egl.EGL10.EGL_PBUFFER_BIT
import org.lwjgl.egl.EGL10.EGL_SURFACE_TYPE
import org.lwjgl.egl.EGL10.EGL_NO_CONTEXT
import org.lwjgl.egl.EGL10.EGL_NO_SURFACE
import org.lwjgl.egl.EGL10.eglChooseConfig
import org.lwjgl.egl.EGL10.eglCreateContext
import org.lwjgl.egl.EGL10.eglDestroyContext
import org.lwjgl.egl.EGL10.eglGetDisplay
import org.lwjgl.egl.EGL10.eglInitialize
import org.lwjgl.egl.EGL10.eglMakeCurrent
import org.lwjgl.egl.EGL10.eglTerminate
import org.lwjgl.egl.EGL12.eglBindAPI
import org.lwjgl.egl.EGL14.EGL_DEFAULT_DISPLAY
import org.lwjgl.egl.EGL14.EGL_OPENGL_ES_API
import org.lwjgl.egl.EGL14.EGL_RENDERABLE_TYPE
import org.lwjgl.egl.EGL14.EGL_NO_DISPLAY
import org.lwjgl.egl.EGL15.EGL_CONTEXT_MAJOR_VERSION
import org.lwjgl.egl.EGL15.EGL_CONTEXT_MINOR_VERSION
import org.lwjgl.egl.KHRCreateContext.EGL_OPENGL_ES3_BIT_KHR
import org.lwjgl.opengles.GLES
import org.lwjgl.system.MemoryStack

/**
 * Offscreen OpenGL ES 3 context without any window system (Mesa surfaceless platform; run with
 * env `EGL_PLATFORM=surfaceless`). Rendering must target an FBO
 * ([de.robinrehbein.birdy.engine.RenderBackend.createRenderTarget]) since there is no default
 * framebuffer. Create, use and [close] on the same thread.
 */
class HeadlessEglContext : AutoCloseable {
    private val display: Long
    private val context: Long

    init {
        display = eglGetDisplay(EGL_DEFAULT_DISPLAY)
        check(display != EGL_NO_DISPLAY) { "eglGetDisplay failed (is EGL_PLATFORM=surfaceless set?)" }
        MemoryStack.stackPush().use { stack ->
            val major = stack.mallocInt(1)
            val minor = stack.mallocInt(1)
            check(eglInitialize(display, major, minor)) { "eglInitialize failed" }
            EGL.createDisplayCapabilities(display, major[0], minor[0])
            check(eglBindAPI(EGL_OPENGL_ES_API)) { "eglBindAPI(GLES) failed" }
            // Surfaceless displays expose no window configs: ask for pbuffer-capable ones.
            val attribs = stack.ints(EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR, EGL_NONE)
            val configs = stack.mallocPointer(1)
            val count = stack.mallocInt(1)
            check(eglChooseConfig(display, attribs, configs, count) && count[0] > 0) { "No GLES3 EGL config" }
            val ctxAttribs = stack.ints(EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 0, EGL_NONE)
            context = eglCreateContext(display, configs[0], EGL_NO_CONTEXT, ctxAttribs)
            check(context != EGL_NO_CONTEXT) { "eglCreateContext failed" }
            check(eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context)) { "eglMakeCurrent(surfaceless) failed" }
        }
        GLES.createCapabilities()
    }

    override fun close() {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT)
        eglDestroyContext(display, context)
        eglTerminate(display)
    }
}
