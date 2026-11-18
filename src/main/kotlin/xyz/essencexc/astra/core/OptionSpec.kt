package xyz.essencexc.astra.core

import xyz.essencexc.astra.api.annotations.InfoType
import xyz.essencexc.astra.api.function.ValueFormatter

/** Numeric field types an option can be backed by. */
internal enum class NumberKind {
    INT, FLOAT, DOUBLE;

    /** Converts a UI double into the field's boxed type. */
    fun box(value: Double): Any = when (this) {
        INT -> Math.round(value).toInt()
        FLOAT -> value.toFloat()
        DOUBLE -> value
    }

    companion object {
        fun of(type: Class<*>): NumberKind? = when (type) {
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> INT
            Float::class.javaPrimitiveType, Float::class.javaObjectType -> FLOAT
            Double::class.javaPrimitiveType, Double::class.javaObjectType -> DOUBLE
            else -> null
        }
    }
}

/** What kind of row an option is, plus the widget's settings. The UI (Phase 2) switches on this. */
internal sealed class OptionSpec {
    data object SwitchSpec : OptionSpec()
    data object CheckboxSpec : OptionSpec()
    data object MasterToggleSpec : OptionSpec()

    data class SliderSpec(val min: Double, val max: Double, val step: Double, val kind: NumberKind, val formatter: ValueFormatter, val logarithmic: Boolean = false) : OptionSpec()

    /** [integer] = backed by `IntRange`, otherwise `FloatRange`. */
    data class RangeSliderSpec(val min: Double, val max: Double, val step: Double, val minSpread: Double, val integer: Boolean, val formatter: ValueFormatter) : OptionSpec()

    data class NumberSpec(val min: Double, val max: Double, val unit: String, val kind: NumberKind) : OptionSpec()

    /** [enumType] is null for `int`-index dropdowns. */
    data class DropdownSpec(val labels: List<String>, val enumType: Class<out Enum<*>>?) : OptionSpec()

    data class TextSpec(val placeholder: String, val multiline: Boolean, val maxLength: Int) : OptionSpec()
    data class ColorSpec(val alpha: Boolean) : OptionSpec()
    data object KeybindSpec : OptionSpec()
    data class InfoSpec(val type: InfoType) : OptionSpec()
    data class ButtonSpec(val text: String, val action: () -> Unit) : OptionSpec()

    /** Value: a `BlockPos` ([integer]) or `Vec3`. */
    data class PositionSpec(val integer: Boolean) : OptionSpec()

    /** Value: the selected [choices] (a `List<Any>` in choice order). */
    data class MultiSelectSpec(val labels: List<String>, val choices: List<Any>) : OptionSpec()

    /** Value: `List<String>`. */
    data class ItemListSpec(val placeholder: String, val maxItems: Int, val maxLength: Int) : OptionSpec()

    /** The option's value is the element's [xyz.essencexc.astra.api.hud.HudLayout]. */
    data class HudSpec(val element: xyz.essencexc.astra.api.hud.HudElement) : OptionSpec()
}
