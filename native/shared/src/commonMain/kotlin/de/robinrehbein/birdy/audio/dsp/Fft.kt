package de.robinrehbein.birdy.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 complex FFT of a fixed power-of-two size. Allocation-free after init. */
internal class Fft(val size: Int) {
    private val cosT = FloatArray(size / 2)
    private val sinT = FloatArray(size / 2)
    private val rev = IntArray(size)

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
        for (i in 0 until size / 2) {
            val a = -2.0 * PI * i / size
            cosT[i] = cos(a).toFloat()
            sinT[i] = sin(a).toFloat()
        }
        var bits = 0
        while (1 shl bits < size) bits++
        for (i in 0 until size) {
            var r = 0
            var x = i
            repeat(bits) { r = (r shl 1) or (x and 1); x = x shr 1 }
            rev[i] = r
        }
    }

    /** Forward (e^-i) or unscaled inverse (e^+i) transform. */
    fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        for (i in 0 until size) {
            val j = rev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        val sign = if (inverse) -1f else 1f
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosT[k]
                    val wi = sign * sinT[k]
                    val l = j + half
                    val xr = re[l] * wr - im[l] * wi
                    val xi = re[l] * wi + im[l] * wr
                    re[l] = re[j] - xr
                    im[l] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += step
                }
                start += len
            }
            len = len shl 1
        }
    }
}
