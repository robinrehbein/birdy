package de.robinrehbein.birdy.engine.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Growable direct buffers reused across GL calls on JVM platforms (Android, desktop), so array
 * uploads in the [Gl] facade don't allocate per call. Not thread-safe: one instance per GL thread.
 */
class NioScratch {
    private var bytes: ByteBuffer = ByteBuffer.allocateDirect(64 * 1024).order(ByteOrder.nativeOrder())

    private fun ensure(byteCount: Int): ByteBuffer {
        if (bytes.capacity() < byteCount) {
            bytes = ByteBuffer.allocateDirect(Integer.highestOneBit(byteCount) shl 1).order(ByteOrder.nativeOrder())
        }
        bytes.clear()
        return bytes
    }

    fun floats(data: FloatArray, count: Int): FloatBuffer {
        val fb = ensure(count * 4).asFloatBuffer()
        fb.put(data, 0, count).flip()
        return fb
    }

    fun ints(data: IntArray, count: Int): IntBuffer {
        val ib = ensure(count * 4).asIntBuffer()
        ib.put(data, 0, count).flip()
        return ib
    }

    fun bytes(data: ByteArray): ByteBuffer {
        val bb = ensure(data.size)
        bb.put(data).flip()
        return bb
    }

    /** A zeroed direct buffer of [size] bytes for reads (readPixels). */
    fun out(size: Int): ByteBuffer {
        val bb = ensure(size)
        bb.limit(size)
        return bb
    }
}
