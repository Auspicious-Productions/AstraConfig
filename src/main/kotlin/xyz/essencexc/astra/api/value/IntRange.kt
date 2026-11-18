package xyz.essencexc.astra.api.value

import java.util.concurrent.ThreadLocalRandom

/**
 * An immutable inclusive int range `[from, to]`, the value type of `@RangeSlider` int fields.
 * Not Kotlin's `kotlin.ranges.IntRange` (unusable from Java); convert with [toKotlin].
 */
class IntRange private constructor(
    @get:JvmName("from") val from: Int,
    @get:JvmName("to") val to: Int,
) : Iterable<Int> {
    /** `to - from`. */
    fun span(): Int = to - from

    operator fun contains(value: Int): Boolean = value in from..to

    fun clamp(value: Int): Int = value.coerceIn(from, to)

    /** A uniformly random value in `[from, to]`. */
    fun random(): Int = if (from == to) from else ThreadLocalRandom.current().nextInt(from, to + 1)

    fun toKotlin(): kotlin.ranges.IntRange = from..to

    override fun iterator(): Iterator<Int> = (from..to).iterator()

    override fun equals(other: Any?): Boolean = other is IntRange && other.from == from && other.to == to
    override fun hashCode(): Int = 31 * from + to
    override fun toString(): String = "$from..$to"

    companion object {
        /** Creates a range; swaps the bounds if `from > to`. */
        @JvmStatic
        fun of(from: Int, to: Int): IntRange = if (from <= to) IntRange(from, to) else IntRange(to, from)

        @JvmStatic
        @JvmSynthetic
        fun of(range: kotlin.ranges.IntRange): IntRange = of(range.first, range.last)
    }
}
