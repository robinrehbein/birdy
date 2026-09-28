package de.robinrehbein.birdy.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log

/**
 * [AudioOut] on a streaming mono float AudioTrack at the device's native rate (no resampling),
 * fed by a dedicated URGENT_AUDIO thread that renders [blockFrames] at a time.
 *
 * - Buffer: capacity 2x the platform minimum (at least 4 blocks); the active size starts near the
 *   minimum and grows by one block whenever the track reports new underruns.
 * - Pause parks the thread on a monitor (no busy wait); resume continues where it stopped.
 * - A dead track (e.g. audio server restart) is rebuilt in place; [stop] then [start] works.
 */
class AudioTrackOut(
    override val sampleRate: Int = nativeRate(),
    private val blockFrames: Int = 256,
) : AudioOut {
    @Volatile private var running = false
    @Volatile private var paused = false
    private val lock = Object()
    private var thread: Thread? = null
    @Volatile private var track: AudioTrack? = null

    override fun start(render: (buffer: FloatArray, frames: Int) -> Unit) {
        if (running) return
        running = true
        thread = Thread({ loop(render) }, "birdy-audio").apply { start() }
    }

    private fun loop(render: (FloatArray, Int) -> Unit) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = FloatArray(blockFrames)
        var t = createTrack() ?: run { running = false; return }
        var underruns = t.underrunCount
        if (!paused) runCatching { t.play() }
        while (running) {
            if (paused) {
                synchronized(lock) { while (paused && running) lock.wait() }
                continue
            }
            render(buf, blockFrames)
            var off = 0
            while (off < blockFrames && running) {
                val n = t.write(buf, off, blockFrames - off, AudioTrack.WRITE_BLOCKING)
                if (n < 0) {
                    Log.w(TAG, "AudioTrack write failed ($n), recreating")
                    track = null
                    runCatching { t.release() }
                    // On failure the audio stays dead: leave no released track behind for stop()/setPaused().
                    t = createTrack() ?: run { running = false; return }
                    underruns = t.underrunCount
                    if (!paused) runCatching { t.play() }
                    break
                }
                if (n == 0 && paused) break
                off += n
            }
            val u = t.underrunCount
            if (u > underruns) {
                underruns = u
                val size = t.bufferSizeInFrames
                val cap = t.bufferCapacityInFrames
                if (size < cap) t.bufferSizeInFrames = minOf(cap, size + blockFrames)
            }
        }
        runCatching { t.pause(); t.flush(); t.stop() }
        t.release()
        track = null
    }

    private fun createTrack(): AudioTrack? = runCatching {
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val bytes = maxOf(minBytes * 2, blockFrames * 4 * BYTES_PER_FRAME)
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= 26) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        val t = builder.build()
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            error("AudioTrack not initialized")
        }
        val startFrames = maxOf(minBytes / BYTES_PER_FRAME + blockFrames, blockFrames * 2)
        if (startFrames < t.bufferCapacityInFrames) t.bufferSizeInFrames = startFrames
        track = t
        t
    }.onFailure { Log.e(TAG, "AudioTrack init failed", it) }.getOrNull()

    override fun setPaused(paused: Boolean) {
        if (this.paused == paused) return
        this.paused = paused
        val t = track
        if (paused) {
            runCatching { t?.pause() }
        } else {
            runCatching { t?.play() }
            synchronized(lock) { lock.notifyAll() }
        }
    }

    override fun stop() {
        running = false
        synchronized(lock) { lock.notifyAll() }
        // Pause + flush releases a writer blocked on a full buffer.
        runCatching { track?.run { pause(); flush() } }
        thread?.join(1000)
        thread = null
    }

    private companion object {
        const val TAG = "BirdyAudio"
        const val BYTES_PER_FRAME = 4

        fun nativeRate(): Int =
            AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it in 8000..192000 } ?: 44100
    }
}
