package de.robinrehbein.birdy.engine.gl

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * CPU transcription of three.js r186 `MeshStandardMaterial` lighting for one directional and one
 * hemisphere light (same formulas as [ShaderChunks]), used as the test oracle.
 */
internal object RefLighting {
    private const val RECIPROCAL_PI = 0.3183098861837907

    class V(val x: Double, val y: Double, val z: Double) {
        operator fun plus(o: V) = V(x + o.x, y + o.y, z + o.z)
        fun dot(o: V) = x * o.x + y * o.y + z * o.z
        fun norm(): V { val l = sqrt(dot(this)); return V(x / l, y / l, z / l) }
    }

    /** Bilinear lookup with clamp-to-edge, like the RG16F texture. */
    fun dfg(u: Double, v: Double): DoubleArray {
        val n = DfgLut.SIZE
        val fx = (u * n - 0.5).coerceIn(0.0, n - 1.0)
        val fy = (v * n - 0.5).coerceIn(0.0, n - 1.0)
        val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
        val x1 = min(x0 + 1, n - 1); val y1 = min(y0 + 1, n - 1)
        val tx = fx - x0; val ty = fy - y0
        return DoubleArray(2) { c ->
            val a = DfgLut.value(x0, y0, c) * (1 - tx) + DfgLut.value(x1, y0, c) * tx
            val b = DfgLut.value(x0, y1, c) * (1 - tx) + DfgLut.value(x1, y1, c) * tx
            a * (1 - ty) + b * ty
        }
    }

    private fun sat(x: Double) = x.coerceIn(0.0, 1.0)
    private fun fSchlick(f0: Double, f90: Double, dotVH: Double): Double {
        val fr = 2.0.pow((-5.55473 * dotVH - 6.98316) * dotVH)
        return f0 * (1 - fr) + f90 * fr
    }

    /**
     * Linear outgoing radiance of one colour channel. [n], [v], [l], [hemiDir] are unit vectors
     * in the same space; colours are linear and already multiplied by intensity.
     */
    fun standard(
        n: V, v: V, l: V, hemiDir: V,
        diffuse: Double, sun: Double, hemiSky: Double, hemiGround: Double,
        roughness: Double, metalness: Double, emissive: Double = 0.0, shadow: Double = 1.0,
    ): Double {
        val rough = min(max(roughness, 0.0525), 1.0)
        val diffuseContribution = diffuse * (1 - metalness)
        val specularColor = 0.04
        val specularBlended = specularColor + (diffuse - specularColor) * metalness
        val dotNVms = sat(n.dot(v))
        val fab = dfg(rough, dotNVms)
        val essMs = fab[0] + fab[1]
        val msComp = 1 + specularBlended * (1 / essMs - 1)

        val dotNL = sat(n.dot(l))
        val irradiance = dotNL * sun * shadow
        val alpha = rough * rough
        val h = (l + v).norm()
        val dotNV = sat(n.dot(v)); val dotNH = sat(n.dot(h)); val dotVH = sat(v.dot(h))
        val a2 = alpha * alpha
        val gv = dotNL * sqrt(a2 + (1 - a2) * dotNV * dotNV)
        val gl = dotNV * sqrt(a2 + (1 - a2) * dotNL * dotNL)
        val vis = 0.5 / max(gv + gl, 1e-6)
        val denom = dotNH * dotNH * (a2 - 1) + 1
        val d = RECIPROCAL_PI * a2 / (denom * denom)
        val spec = irradiance * fSchlick(specularBlended, 1.0, dotVH) * vis * d * msComp
        val directDiffuse = irradiance * RECIPROCAL_PI * diffuseContribution * (1 - fSchlick(specularColor, 1.0, dotVH))

        val w = 0.5 * n.dot(hemiDir) + 0.5
        val hemi = hemiGround + (hemiSky - hemiGround) * w
        val fssEss = specularColor * fab[0] + fab[1]
        val ems = 1 - essMs
        val favg = specularColor + (1 - specularColor) * 0.047619
        val fms = fssEss * favg / (1 - ems * favg)
        val indirect = hemi * RECIPROCAL_PI * diffuseContribution * (1 - fssEss - fms * ems)
        return directDiffuse + indirect + spec + emissive
    }
}
