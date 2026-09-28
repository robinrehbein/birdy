package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.scene.Filter
import de.robinrehbein.birdy.engine.scene.Texture
import de.robinrehbein.birdy.engine.scene.Wrap

/**
 * GPU copy of a [Texture], uploaded like a three.js `CanvasTexture` with
 * `colorSpace = SRGBColorSpace`: `SRGB8_ALPHA8` storage (decoded to linear before filtering),
 * `flipY = true` (the top image row ends up at v = 1), mipmaps for [Filter.LinearMipmap] and
 * anisotropy 8 on mipmapped textures (world.js sets `anisotropy = 8` on its canvas textures).
 */
internal class GpuTexture(private val gl: GlApi) {
    val id = gl.genTexture()
    private var version = -1
    var lastUsed = 0

    fun sync(tex: Texture, maxAnisotropy: Float) {
        if (version == tex.version) return
        gl.bindTexture(GL.TEXTURE_2D, id)
        gl.texImage2D(GL.TEXTURE_2D, 0, GL.SRGB8_ALPHA8, tex.width, tex.height, GL.RGBA, GL.UNSIGNED_BYTE, flipRows(tex))
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, wrap(tex.wrapS))
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, wrap(tex.wrapT))
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, if (tex.magFilter == Filter.Nearest) GL.NEAREST else GL.LINEAR)
        val min = when (tex.minFilter) {
            Filter.Nearest -> GL.NEAREST
            Filter.Linear -> GL.LINEAR
            Filter.LinearMipmap -> GL.LINEAR_MIPMAP_LINEAR
        }
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, min)
        if (tex.minFilter == Filter.LinearMipmap) {
            gl.generateMipmap(GL.TEXTURE_2D)
            if (maxAnisotropy > 1f) gl.texParameterf(GL.TEXTURE_2D, GL.TEXTURE_MAX_ANISOTROPY_EXT, minOf(ANISOTROPY, maxAnisotropy))
        }
        version = tex.version
    }

    fun dispose() = gl.deleteTexture(id)

    companion object {
        const val ANISOTROPY = 8f

        /** [Texture.pixels] is top row first; GL's first row is v = 0, so reverse (flipY). */
        fun flipRows(tex: Texture): ByteArray {
            val row = tex.width * 4
            val src = tex.pixels
            val out = ByteArray(row * tex.height)
            for (y in 0 until tex.height) src.copyInto(out, (tex.height - 1 - y) * row, y * row, y * row + row)
            return out
        }

        fun wrap(w: Wrap) = when (w) {
            Wrap.Repeat -> GL.REPEAT
            Wrap.Clamp -> GL.CLAMP_TO_EDGE
            Wrap.Mirror -> GL.MIRRORED_REPEAT
        }
    }
}
