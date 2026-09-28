package de.robinrehbein.birdy.audio.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * DynamicsCompressorNode approximation. The static curve is Chromium's (linear up to the
 * threshold, exponential knee in the linear domain whose slope reaches 1/ratio at
 * threshold+knee, then 1/ratio in dB) including its automatic makeup gain
 * `(1 / curve(1.0))^0.6` and the 6 ms look-ahead pre-delay. The envelope is a plain one-pole
 * attack/release follower instead of Chromium's adaptive release polynomial.
 */
class Compressor(
    sampleRate: Int,
    thresholdDb: Double = -14.0,
    private val kneeDb: Double = 12.0,
    ratio: Double = 3.0,
    attack: Double = 0.005,
    release: Double = 0.2,
) {
    private val linearThreshold = dbToLin(thresholdDb)
    private val slope = 1.0 / ratio
    private val kneeThresholdDb = thresholdDb + kneeDb
    private val kneeThreshold = dbToLin(kneeThresholdDb)
    private val k: Double
    private val yKneeThresholdDb: Double
    val makeupGain: Double
    private val attackCoef = 1 - exp(-1.0 / (maxOf(0.001, attack) * sampleRate))
    private val releaseCoef = 1 - exp(-1.0 / (maxOf(0.001, release) * sampleRate))
    private val delay = FloatArray(maxOf(1, (0.006 * sampleRate).toInt()))
    private var delayPos = 0
    private var env = 1.0

    /** Current gain reduction (linear, <= 1), for tests/meters. */
    val reduction: Double get() = env

    init {
        k = kAtSlope(slope)
        yKneeThresholdDb = linToDb(kneeCurve(kneeThreshold, k))
        makeupGain = (1.0 / saturate(1.0, k)).pow(0.6)
    }

    private fun kneeCurve(x: Double, k: Double): Double =
        if (x < linearThreshold) x else linearThreshold + (1 - exp(-k * (x - linearThreshold))) / k

    private fun saturate(x: Double, k: Double): Double =
        if (x < kneeThreshold) kneeCurve(x, k)
        else dbToLin(yKneeThresholdDb + slope * (linToDb(x) - kneeThresholdDb))

    private fun slopeAt(x: Double, k: Double): Double {
        if (x < linearThreshold) return 1.0
        val x2 = x * 1.001
        val xDb = linToDb(x); val x2Db = linToDb(x2)
        val yDb = linToDb(kneeCurve(x, k)); val y2Db = linToDb(kneeCurve(x2, k))
        return (y2Db - yDb) / (x2Db - xDb)
    }

    private fun kAtSlope(desired: Double): Double {
        val x = dbToLin(kneeThresholdDb)
        var minK = 0.1; var maxK = 10000.0; var kk = 5.0
        repeat(15) {
            if (slopeAt(x, kk) < desired) maxK = kk else minK = kk
            kk = sqrt(minK * maxK)
        }
        return kk
    }

    /** Static gain (<= 1) for a detector level. */
    fun staticGain(level: Double): Double =
        if (level <= linearThreshold) 1.0 else saturate(level, k) / level

    fun process(x: Float): Float {
        val target = staticGain(abs(x.toDouble()))
        env += (target - env) * (if (target < env) attackCoef else releaseCoef)
        val delayed = delay[delayPos]
        delay[delayPos] = x
        if (++delayPos == delay.size) delayPos = 0
        return (delayed * env * makeupGain).toFloat()
    }

    private companion object {
        fun dbToLin(db: Double) = 10.0.pow(db / 20)
        fun linToDb(x: Double) = 20 * log10(x)
    }
}
