package de.robinrehbein.birdy.engine.math

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * sRGB colour stored exactly as the JS hex literal (`0xRRGGBB`), i.e. [r]/[g]/[b] are sRGB-encoded
 * components. Keep hex values bit-identical to the JS source.
 *
 * three.js (ColorManagement on) stores *linear* components instead; every operation here that three.js
 * performs on its stored values ([lerp], [multiply], [add], [multiplyScalar]) converts to linear, operates,
 * and converts back, so results match three.js after `getHex()`. [linearR]/[linearG]/[linearB] give the
 * values three.js would hold in `color.r/g/b` (e.g. for baked vertex colours).
 */
class Color(var r: Float = 1f, var g: Float = 1f, var b: Float = 1f) {
    fun setHex(hex: Int): Color {
        r = ((hex shr 16) and 0xFF) / 255f
        g = ((hex shr 8) and 0xFF) / 255f
        b = (hex and 0xFF) / 255f
        return this
    }

    fun set(o: Color): Color { r = o.r; g = o.g; b = o.b; return this }
    fun copy(): Color = Color(r, g, b)
    fun copy(o: Color): Color = set(o)
    fun clone(): Color = Color(r, g, b)

    /** sRGB components (three.js `setRGB(r, g, b, SRGBColorSpace)`). */
    fun setRGB(r: Float, g: Float, b: Float): Color { this.r = r; this.g = g; this.b = b; return this }
    fun setScalar(s: Float): Color = setRGB(s, s, s)

    /** Linear components (three.js `setRGB(r, g, b)` with the default linear working space). */
    fun setLinear(r: Double, g: Double, b: Double): Color {
        this.r = linearToSrgb(r).toFloat(); this.g = linearToSrgb(g).toFloat(); this.b = linearToSrgb(b).toFloat()
        return this
    }

    val linearR: Float get() = srgbToLinear(r.toDouble()).toFloat()
    val linearG: Float get() = srgbToLinear(g.toDouble()).toFloat()
    val linearB: Float get() = srgbToLinear(b.toDouble()).toFloat()

    fun hex(): Int {
        fun ch(v: Float) = floor(MathUtil.clamp(v.toDouble() * 255.0, 0.0, 255.0) + 0.5).toInt()
        return (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    fun getHex(): Int = hex()
    fun getHexString(): String = hex().toString(16).padStart(6, '0')

    /**
     * three.js `setHSL(h, s, l, colorSpace)`: hue wraps, s/l are clamped. As in three.js r186 the
     * default [space] is the linear working space, i.e. the HSL-derived RGB values are taken as linear
     * components (`setHSL(t % 1, 1, 0.6)` in effects.js/main.js). Pass [ColorSpace.SRGB] for CSS-style HSL.
     */
    fun setHSL(h: Double, s: Double, l: Double, space: ColorSpace = ColorSpace.Linear): Color {
        val hh = MathUtil.euclideanModulo(h, 1.0)
        val ss = MathUtil.clamp(s, 0.0, 1.0)
        val ll = MathUtil.clamp(l, 0.0, 1.0)
        val cr: Double; val cg: Double; val cb: Double
        if (ss == 0.0) {
            cr = ll; cg = ll; cb = ll
        } else {
            val p = if (ll <= 0.5) ll * (1 + ss) else ll + ss - (ll * ss)
            val q = 2 * ll - p
            cr = hue2rgb(q, p, hh + 1.0 / 3)
            cg = hue2rgb(q, p, hh)
            cb = hue2rgb(q, p, hh - 1.0 / 3)
        }
        return if (space == ColorSpace.SRGB) setRGB(cr.toFloat(), cg.toFloat(), cb.toFloat()) else setLinear(cr, cg, cb)
    }

    fun setHSL(h: Float, s: Float, l: Float, space: ColorSpace = ColorSpace.Linear): Color =
        setHSL(h.toDouble(), s.toDouble(), l.toDouble(), space)

    /**
     * three.js `getHSL(target, colorSpace)`. The default [space] is linear like three.js; bird.js'
     * wing rule calls it with [ColorSpace.SRGB] (plain CSS lightness of the hex).
     */
    fun getHSL(target: Hsl = Hsl(), space: ColorSpace = ColorSpace.Linear): Hsl =
        if (space == ColorSpace.SRGB) hslOf(r.toDouble(), g.toDouble(), b.toDouble(), target)
        else hslOf(srgbToLinear(r.toDouble()), srgbToLinear(g.toDouble()), srgbToLinear(b.toDouble()), target)

    /** three.js `offsetHSL` (three.js reads and writes HSL in the linear working space). */
    fun offsetHSL(h: Double, s: Double, l: Double): Color {
        val hsl = getHSL()
        return setHSL(hsl.h + h, hsl.s + s, hsl.l + l)
    }

    /** three.js `Color.lerp`: interpolates the linear components. */
    fun lerp(o: Color, alpha: Float): Color = lerp(o, alpha.toDouble())

    fun lerp(o: Color, alpha: Double): Color {
        val lr = srgbToLinear(r.toDouble()); val lg = srgbToLinear(g.toDouble()); val lb = srgbToLinear(b.toDouble())
        return setLinear(
            lr + (srgbToLinear(o.r.toDouble()) - lr) * alpha,
            lg + (srgbToLinear(o.g.toDouble()) - lg) * alpha,
            lb + (srgbToLinear(o.b.toDouble()) - lb) * alpha,
        )
    }

    fun lerpColors(a: Color, b: Color, alpha: Float): Color = set(a).lerp(b, alpha)

    /** Plain lerp of the stored sRGB components (NOT what three.js does; for UI-style blends). */
    fun lerpSrgb(o: Color, alpha: Float): Color =
        setRGB(r + (o.r - r) * alpha, g + (o.g - g) * alpha, b + (o.b - b) * alpha)

    /** three.js `Color.multiply` (linear components). */
    fun multiply(o: Color): Color = setLinear(
        srgbToLinear(r.toDouble()) * srgbToLinear(o.r.toDouble()),
        srgbToLinear(g.toDouble()) * srgbToLinear(o.g.toDouble()),
        srgbToLinear(b.toDouble()) * srgbToLinear(o.b.toDouble()),
    )

    /** three.js `Color.multiplyScalar` (linear components). */
    fun multiplyScalar(s: Float): Color = setLinear(
        srgbToLinear(r.toDouble()) * s, srgbToLinear(g.toDouble()) * s, srgbToLinear(b.toDouble()) * s,
    )

    /** three.js `Color.add` (linear components). */
    fun add(o: Color): Color = setLinear(
        srgbToLinear(r.toDouble()) + srgbToLinear(o.r.toDouble()),
        srgbToLinear(g.toDouble()) + srgbToLinear(o.g.toDouble()),
        srgbToLinear(b.toDouble()) + srgbToLinear(o.b.toDouble()),
    )

    fun equals(o: Color): Boolean = r == o.r && g == o.g && b == o.b

    companion object {
        fun hex(hex: Int): Color = Color().setHex(hex)

        /** sRGB -> linear transfer (three.js `SRGBToLinear`). */
        fun srgbToLinear(c: Float): Float = srgbToLinear(c.toDouble()).toFloat()

        fun srgbToLinear(c: Double): Double =
            if (c < 0.04045) c * 0.0773993808 else (c * 0.9478672986 + 0.0521327014).pow(2.4)

        /** linear -> sRGB transfer (three.js `LinearToSRGB`). */
        fun linearToSrgb(c: Double): Double =
            if (c < 0.0031308) c * 12.92 else 1.055 * c.pow(0.41666) - 0.055

        fun linearToSrgb(c: Float): Float = linearToSrgb(c.toDouble()).toFloat()

        private fun hue2rgb(p: Double, q: Double, t0: Double): Double {
            var t = t0
            if (t < 0) t += 1.0
            if (t > 1) t -= 1.0
            if (t < 1.0 / 6) return p + (q - p) * 6 * t
            if (t < 1.0 / 2) return q
            if (t < 2.0 / 3) return p + (q - p) * 6 * (2.0 / 3 - t)
            return p
        }

        private fun hslOf(r: Double, g: Double, b: Double, target: Hsl): Hsl {
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val lightness = (mn + mx) / 2.0
            var hue = 0.0
            var saturation = 0.0
            if (mn != mx) {
                val delta = mx - mn
                saturation = if (lightness <= 0.5) delta / (mx + mn) else delta / (2 - mx - mn)
                hue = when (mx) {
                    r -> (g - b) / delta + (if (g < b) 6 else 0)
                    g -> (b - r) / delta + 2
                    else -> (r - g) / delta + 4
                }
                hue /= 6
            }
            target.h = hue; target.s = saturation; target.l = lightness
            return target
        }
    }

    override fun toString(): String = "Color(#${getHexString()})"
}

/** Colour space argument of [Color.setHSL]/[Color.getHSL] (three.js `LinearSRGBColorSpace`/`SRGBColorSpace`). */
enum class ColorSpace { Linear, SRGB }

/** HSL triple (all in [0, 1]) for [Color.getHSL]. */
class Hsl(var h: Double = 0.0, var s: Double = 0.0, var l: Double = 0.0) {
    override fun toString(): String = "Hsl($h, $s, $l)"
}
