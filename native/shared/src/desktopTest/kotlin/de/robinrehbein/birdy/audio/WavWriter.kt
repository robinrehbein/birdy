package de.robinrehbein.birdy.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes mono 16-bit PCM WAV files for listening checks. */
object WavWriter {
    fun write(file: File, pcm: FloatArray, sampleRate: Int = 44100) {
        file.parentFile?.mkdirs()
        val data = pcm.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate)
            .putInt(sampleRate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (v in pcm) b.putShort((v.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        file.writeBytes(b.array())
    }
}
