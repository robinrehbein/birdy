package de.robinrehbein.birdy.audio.dsp

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * ConvolverNode with the audio.js generated room IR (1.2 s of white noise * (1 - i/len)^3) and
 * Chromium's default `normalize = true` scaling. Non-uniform partitioning keeps it cheap: the first
 * [TAIL_START] IR samples run at the engine quantum with zero latency, the rest in 2048-sample
 * blocks whose output is due exactly one tail block later.
 */
internal class Reverb(sampleRate: Int, rng: Random, private val quantum: Int) {
    val ir: FloatArray
    private val headConv: PartitionedConvolver
    private val tailConv: PartitionedConvolver?
    private val tailIn = FloatArray(TAIL_BLOCK)
    private val tailOut = FloatArray(TAIL_BLOCK)
    private var tailFill = 0

    init {
        require(TAIL_BLOCK % quantum == 0)
        val len = (sampleRate * 1.2).toInt()
        ir = FloatArray(len)
        for (i in 0 until len) ir[i] = ((rng.nextDouble() * 2 - 1) * (1.0 - i.toDouble() / len).pow(3)).toFloat()
        val scale = normalizationScale(ir, sampleRate)
        for (i in 0 until len) ir[i] *= scale
        headConv = PartitionedConvolver(ir, 0, minOf(TAIL_START, len), quantum)
        tailConv = if (len > TAIL_START) PartitionedConvolver(ir, TAIL_START, len - TAIL_START, TAIL_BLOCK) else null
    }

    /** Convolves one quantum of [input] into [output] (overwrites). */
    fun process(input: FloatArray, output: FloatArray) {
        headConv.process(input, 0, output, 0)
        val tail = tailConv ?: return
        for (i in 0 until quantum) output[i] += tailOut[tailFill + i]
        input.copyInto(tailIn, tailFill, 0, quantum)
        tailFill += quantum
        if (tailFill == TAIL_BLOCK) {
            tail.process(tailIn, 0, tailOut, 0)
            tailFill = 0
        }
    }

    companion object {
        const val TAIL_START = 2048
        const val TAIL_BLOCK = 2048

        /** Chromium Reverb::CalculateNormalizationScale (mono response). */
        fun normalizationScale(ir: FloatArray, sampleRate: Int): Float {
            var sum = 0.0
            for (v in ir) sum += v.toDouble() * v
            var power = sqrt(sum / ir.size)
            if (!power.isFinite() || power < 0.000125) power = 0.000125
            var scale = 1.0 / power
            scale *= 10.0.pow(-58 * 0.05)
            scale *= 44100.0 / sampleRate
            return scale.toFloat()
        }
    }
}
