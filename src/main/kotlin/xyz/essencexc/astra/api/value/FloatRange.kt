package xyz.essencexc.astra.api.value

import java.util.concurrent.ThreadLocalRandom

/** An immutable inclusive float range `[from, to]`, the value type of `@RangeSlider` float fields. */
class FloatRange private constructor(
    @get:JvmName("from") val from: Float,
    @get:JvmName("to") val to: Float,
) {
    fun span(): Float = to - from

    operator fun contains(value: Float): Boolean = value in from..to

    fun clamp(value: Float): Float = value.coerceIn(from, to)

    /** A uniformly random value in `[from, to]`. */
    fun random(): Float = if (from == to) from else from + ThreadLocalRandom.current().nextFloat() * (to - from)

    fun toKotlin(): ClosedFloatingPointRange<Float> = from..to

    override fun equals(other: Any?): Boolean = other is FloatRange && other.from == from && other.to == to
    override fun hashCode(): Int = 31 * from.hashCode() + to.hashCode()
    override fun toString(): String = "$from..$to"

    companion object {
        @JvmStatic
        fun of(from: Float, to: Float): FloatRange = if (from <= to) FloatRange(from, to) else FloatRange(to, from)

        @JvmStatic
        @JvmSynthetic
        fun of(range: ClosedFloatingPointRange<Float>): FloatRange = of(range.start, range.endInclusive)
    }
}
