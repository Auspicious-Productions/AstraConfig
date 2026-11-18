package xyz.essencexc.astra.ui.util

import kotlin.math.roundToInt

/** ARGB helpers. */
object Colors {
    @JvmStatic fun alpha(c: Int): Int = c ushr 24
    @JvmStatic fun red(c: Int): Int = (c shr 16) and 0xFF
    @JvmStatic fun green(c: Int): Int = (c shr 8) and 0xFF
    @JvmStatic fun blue(c: Int): Int = c and 0xFF

    @JvmStatic
    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Multiplies the alpha channel by [factor]. */
    @JvmStatic
    fun fade(c: Int, factor: Float): Int {
        if (factor >= 1f) return c
        val a = (alpha(c) * factor.coerceIn(0f, 1f)).roundToInt()
        return (a shl 24) or (c and 0x00FFFFFF)
    }

    /** Replaces the alpha channel. */
    @JvmStatic
    fun withAlpha(c: Int, a: Float): Int = ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (c and 0x00FFFFFF)

    /** Linear blend between two colours, alpha included. */
    @JvmStatic
    fun mix(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt()
        return argb(ch(alpha(a), alpha(b)), ch(red(a), red(b)), ch(green(a), green(b)), ch(blue(a), blue(b)))
    }

    /** Relative luminance 0..1, for picking readable text on a colour. */
    @JvmStatic
    fun luminance(c: Int): Float = (0.2126f * red(c) + 0.7152f * green(c) + 0.0722f * blue(c)) / 255f

    // HSV, all components 0..1.

    @JvmStatic
    fun hsv(h: Float, s: Float, v: Float, a: Float = 1f): Int {
        val rgb = java.awt.Color.HSBtoRGB(h, s, v)
        return withAlpha(rgb, a)
    }

    /** Returns `[h, s, v]`. */
    @JvmStatic
    fun toHsv(c: Int): FloatArray = java.awt.Color.RGBtoHSB(red(c), green(c), blue(c), null)

    @JvmStatic
    fun hex(c: Int, alpha: Boolean): String =
        if (alpha) "#%08X".format(c) else "#%06X".format(c and 0xFFFFFF)

    /** Parses `#RGB`, `#RRGGBB` or `#AARRGGBB` (the `#` is optional). Null if it isn't a colour. */
    @JvmStatic
    fun parseHex(text: String): Int? {
        var s = text.trim().removePrefix("#")
        if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
        return when (s.length) {
            6 -> s.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
            8 -> s.toLongOrNull(16)?.toInt()
            else -> null
        }
    }
}
