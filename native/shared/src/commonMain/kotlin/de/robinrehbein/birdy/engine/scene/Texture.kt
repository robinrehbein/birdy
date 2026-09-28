package de.robinrehbein.birdy.engine.scene

/** Texture wrap modes (GL_REPEAT / GL_CLAMP_TO_EDGE / GL_MIRRORED_REPEAT). */
enum class Wrap { Repeat, Clamp, Mirror }

/** Texture filters; [LinearMipmap] generates mipmaps on upload. */
enum class Filter { Nearest, Linear, LinearMipmap }

/**
 * CPU-side RGBA8 image (three.js `CanvasTexture` / `DataTexture` equivalent). [pixels] is
 * row-major, top row first, 4 bytes per pixel, non-premultiplied, sRGB-encoded.
 */
class Texture(
    val width: Int,
    val height: Int,
    val pixels: ByteArray = ByteArray(width * height * 4),
) {
    var wrapS = Wrap.Clamp
    var wrapT = Wrap.Clamp
    var minFilter = Filter.LinearMipmap
    var magFilter = Filter.Linear
    /** UV transform: uv' = uv * repeat + offset. */
    var repeatU = 1f
    var repeatV = 1f
    var offsetU = 0f
    var offsetV = 0f

    var version = 0
        private set

    /** Re-upload [pixels] (three.js `texture.needsUpdate = true`). */
    fun markDirty() { version++ }
}
