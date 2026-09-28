package de.robinrehbein.birdy.audio.dsp

/**
 * Uniformly partitioned overlap-save FFT convolution with block size [block] (FFT size 2*block).
 * [process] consumes one block of input and yields the matching block of output with no added
 * latency (the current block is convolved with the first partition).
 */
internal class PartitionedConvolver(ir: FloatArray, irOffset: Int, irLength: Int, val block: Int) {
    private val n = block * 2
    private val bins = block + 1
    private val fft = Fft(n)
    private val parts = maxOf(1, (irLength + block - 1) / block)
    private val hRe = FloatArray(parts * bins)
    private val hIm = FloatArray(parts * bins)
    private val xRe = FloatArray(parts * bins)
    private val xIm = FloatArray(parts * bins)
    private val history = FloatArray(n)
    private val wRe = FloatArray(n)
    private val wIm = FloatArray(n)
    private var head = 0

    init {
        for (p in 0 until parts) {
            wRe.fill(0f); wIm.fill(0f)
            val from = p * block
            for (i in 0 until block) {
                val k = from + i
                if (k < irLength) wRe[i] = ir[irOffset + k]
            }
            fft.transform(wRe, wIm, inverse = false)
            wRe.copyInto(hRe, p * bins, 0, bins)
            wIm.copyInto(hIm, p * bins, 0, bins)
        }
    }

    fun process(input: FloatArray, inOffset: Int, output: FloatArray, outOffset: Int) {
        history.copyInto(history, 0, block, n)
        input.copyInto(history, block, inOffset, inOffset + block)
        history.copyInto(wRe)
        wIm.fill(0f)
        fft.transform(wRe, wIm, inverse = false)
        head = if (head == 0) parts - 1 else head - 1
        wRe.copyInto(xRe, head * bins, 0, bins)
        wIm.copyInto(xIm, head * bins, 0, bins)

        wRe.fill(0f); wIm.fill(0f)
        for (p in 0 until parts) {
            val xp = ((head + p) % parts) * bins
            val hp = p * bins
            for (k in 0 until bins) {
                val ar = xRe[xp + k]; val ai = xIm[xp + k]
                val br = hRe[hp + k]; val bi = hIm[hp + k]
                wRe[k] += ar * br - ai * bi
                wIm[k] += ar * bi + ai * br
            }
        }
        for (k in 1 until block) {
            wRe[n - k] = wRe[k]
            wIm[n - k] = -wIm[k]
        }
        fft.transform(wRe, wIm, inverse = true)
        val scale = 1f / n
        for (i in 0 until block) output[outOffset + i] = wRe[block + i] * scale
    }
}
