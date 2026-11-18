package xyz.essencexc.astra.api.kt

import com.mojang.serialization.Codec
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.annotations.InfoType
import xyz.essencexc.astra.api.function.Condition
import xyz.essencexc.astra.api.function.OptionListener
import xyz.essencexc.astra.api.function.ValueFormatter
import xyz.essencexc.astra.api.keybind.AstraKeybind
import xyz.essencexc.astra.api.keybind.KeyBinding
import xyz.essencexc.astra.api.value.FloatRange
import xyz.essencexc.astra.api.value.IntRange
import xyz.essencexc.astra.api.hud.HudElement
import xyz.essencexc.astra.api.hud.HudLayout
import xyz.essencexc.astra.core.Accessor
import xyz.essencexc.astra.core.AstraConfigException
import xyz.essencexc.astra.core.HudRegistry
import xyz.essencexc.astra.core.Codecs
import xyz.essencexc.astra.core.Collector
import xyz.essencexc.astra.core.KeybindRegistry
import xyz.essencexc.astra.core.MappedAccessor
import xyz.essencexc.astra.core.NumberKind
import xyz.essencexc.astra.core.Option
import xyz.essencexc.astra.core.OptionCollections
import xyz.essencexc.astra.core.OptionSpec
import xyz.essencexc.astra.core.ValueAccessor
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Kotlin-only alternative to annotations, like OneConfig's `KtConfig`:
 *
 * ```kotlin
 * object FishingModule : KtModule("fishing", "Fishing") {
 *     var enabled by masterToggle()
 *     var sneak by switch("Sneak While Fishing", section = "Fishing Macro").onChange { println(it) }
 *     var delay by rangeSlider("Reel Delay", 120..250, bounds = 0..1_000)
 * }
 * ```
 *
 * Options are registered in declaration order. Writing a delegated property fires `onChange`.
 * Annotated fields still work in a `KtModule` and are listed after the delegates.
 */
abstract class KtModule(id: String, name: String) : Module(id, name) {
    protected fun masterToggle(default: Boolean = false): OptionDelegate<Boolean, Boolean> =
        simple("Enabled", default, "", "", OptionSpec.MasterToggleSpec, Codec.BOOL)

    protected fun switch(title: String, default: Boolean = false, description: String = "", section: String = ""): OptionDelegate<Boolean, Boolean> =
        simple(title, default, description, section, OptionSpec.SwitchSpec, Codec.BOOL)

    protected fun checkbox(title: String, default: Boolean = false, description: String = "", section: String = ""): OptionDelegate<Boolean, Boolean> =
        simple(title, default, description, section, OptionSpec.CheckboxSpec, Codec.BOOL)

    protected fun slider(title: String, default: Int, range: kotlin.ranges.IntRange, step: Int = 1, description: String = "", section: String = "", formatter: ValueFormatter = ValueFormatter.Default(), logarithmic: Boolean = false): OptionDelegate<Int, Int> =
        simple(title, default, description, section, OptionSpec.SliderSpec(range.first.toDouble(), range.last.toDouble(), step.toDouble(), NumberKind.INT, formatter, logarithmic.alsoCheckLog(range.first.toDouble())), Codec.INT)

    protected fun slider(title: String, default: Float, range: ClosedFloatingPointRange<Float>, step: Float = 0.1f, description: String = "", section: String = "", formatter: ValueFormatter = ValueFormatter.Default(), logarithmic: Boolean = false): OptionDelegate<Float, Float> =
        simple(title, default, description, section, OptionSpec.SliderSpec(range.start.toDouble(), range.endInclusive.toDouble(), step.toDouble(), NumberKind.FLOAT, formatter, logarithmic.alsoCheckLog(range.start.toDouble())), Codec.FLOAT)

    protected fun slider(title: String, default: Double, range: ClosedFloatingPointRange<Double>, step: Double = 0.1, description: String = "", section: String = "", formatter: ValueFormatter = ValueFormatter.Default(), logarithmic: Boolean = false): OptionDelegate<Double, Double> =
        simple(title, default, description, section, OptionSpec.SliderSpec(range.start, range.endInclusive, step, NumberKind.DOUBLE, formatter, logarithmic.alsoCheckLog(range.start)), Codec.DOUBLE)

    /** A from–to range. Read it with `.from`, `.to`, `.random()`; `toKotlin()` gives a Kotlin range. */
    protected fun rangeSlider(title: String, default: kotlin.ranges.IntRange, bounds: kotlin.ranges.IntRange, step: Int = 1, minSpread: Int = 0, description: String = "", section: String = "", formatter: ValueFormatter = ValueFormatter.Default()): OptionDelegate<IntRange, IntRange> =
        simple(title, IntRange.of(default), description, section, OptionSpec.RangeSliderSpec(bounds.first.toDouble(), bounds.last.toDouble(), step.toDouble(), minSpread.toDouble(), true, formatter), Codecs.INT_RANGE)

    protected fun rangeSlider(title: String, default: ClosedFloatingPointRange<Float>, bounds: ClosedFloatingPointRange<Float>, step: Float = 0.1f, minSpread: Float = 0f, description: String = "", section: String = "", formatter: ValueFormatter = ValueFormatter.Default()): OptionDelegate<FloatRange, FloatRange> =
        simple(title, FloatRange.of(default), description, section, OptionSpec.RangeSliderSpec(bounds.start.toDouble(), bounds.endInclusive.toDouble(), step.toDouble(), minSpread.toDouble(), false, formatter), Codecs.FLOAT_RANGE)

    protected fun number(title: String, default: Int, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, unit: String = "", description: String = "", section: String = ""): OptionDelegate<Int, Int> =
        simple(title, default, description, section, OptionSpec.NumberSpec(min.toDouble(), max.toDouble(), unit, NumberKind.INT), Codec.INT)

    protected fun number(title: String, default: Double, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE, unit: String = "", description: String = "", section: String = ""): OptionDelegate<Double, Double> =
        simple(title, default, description, section, OptionSpec.NumberSpec(min, max, unit, NumberKind.DOUBLE), Codec.DOUBLE)

    protected fun <E : Enum<E>> dropdown(title: String, default: E, description: String = "", section: String = ""): OptionDelegate<E, E> {
        val type = default.declaringJavaClass
        return simple(title, default, description, section, OptionSpec.DropdownSpec(type.enumConstants.map(Collector::label), type), Codecs.enumCodec(type))
    }

    protected fun text(title: String, default: String = "", placeholder: String = "", multiline: Boolean = false, maxLength: Int = 256, description: String = "", section: String = ""): OptionDelegate<String, String> =
        simple(title, default, description, section, OptionSpec.TextSpec(placeholder, multiline, maxLength), Codec.STRING)

    /** ARGB colour. */
    protected fun color(title: String, default: Int, alpha: Boolean = true, description: String = "", section: String = ""): OptionDelegate<Int, Int> =
        simple(title, default, description, section, OptionSpec.ColorSpec(alpha), Codecs.COLOR)

    protected fun info(title: String, text: String, type: InfoType = InfoType.INFO, section: String = ""): OptionDelegate<String, String> =
        OptionDelegate(title, "", section, OptionSpec.InfoSpec(type), null, ValueAccessor(text)) { it }

    /** A rebindable key: `val toggleKey by keybind("Toggle", InputConstants.KEY_R) { onPress { toggle() } }`. */
    protected fun keybind(title: String, key: Int, description: String = "", section: String = "", configure: AstraKeybind.() -> Unit = {}): OptionDelegate<KeyBinding, AstraKeybind> {
        val keybind = AstraKeybind.of(key).apply(configure)
        val accessor = MappedAccessor({ keybind }, AstraKeybind::binding) { kb, b: KeyBinding -> kb.binding = b }
        return OptionDelegate(title, description, section, OptionSpec.KeybindSpec, Codecs.KEY_BINDING, accessor) { keybind }
            .also { KeybindRegistry.register(keybind, this, title) }
    }

    /** A block position with X/Y/Z boxes and a "Here" button: `var home by position("Home", BlockPos.ZERO)`. */
    protected fun position(title: String, default: net.minecraft.core.BlockPos, description: String = "", section: String = ""): OptionDelegate<net.minecraft.core.BlockPos, net.minecraft.core.BlockPos> =
        simple(title, default, description, section, OptionSpec.PositionSpec(true), net.minecraft.core.BlockPos.CODEC)

    /** An exact position (decimals): `var spot by position("Spot", Vec3(0.5, 64.0, 0.5))`. */
    protected fun position(title: String, default: net.minecraft.world.phys.Vec3, description: String = "", section: String = ""): OptionDelegate<net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3> =
        simple(title, default, description, section, OptionSpec.PositionSpec(false), net.minecraft.world.phys.Vec3.CODEC)

    /** Several enum values: `var mobs by multiSelect("Mobs", setOf(Mob.ZOMBIE))`. */
    protected inline fun <reified E : Enum<E>> multiSelect(title: String, default: Set<E>, description: String = "", section: String = ""): OptionDelegate<List<Any>, Set<E>> =
        multiSelect(title, E::class.java, default, description, section)

    @PublishedApi
    internal fun <E : Enum<E>> multiSelect(title: String, type: Class<E>, default: Set<E>, description: String, section: String): OptionDelegate<List<Any>, Set<E>> {
        val choices: List<Any> = type.enumConstants.toList()
        val initial: List<Any> = choices.filter { it in default }
        @Suppress("UNCHECKED_CAST")
        return OptionDelegate(
            title, description, section,
            OptionSpec.MultiSelectSpec(choices.map { Collector.label(it as Enum<*>) }, choices),
            OptionCollections.choiceCodec(choices), ValueAccessor(initial),
            unview = { set -> choices.filter { it in set } },
        ) { list -> list.map { it as E }.toCollection(LinkedHashSet()) }
    }

    /** An editable list of strings: `var blacklist by itemList("Blacklist", listOf("Steve"))`. */
    protected fun itemList(title: String, default: List<String> = emptyList(), placeholder: String = "", maxItems: Int = 100, maxLength: Int = 128, description: String = "", section: String = ""): OptionDelegate<List<String>, List<String>> =
        OptionDelegate(title, description, section, OptionSpec.ItemListSpec(placeholder, maxItems, maxLength), OptionCollections.STRING_LIST, ValueAccessor(default.toList())) { it }

    /** A HUD element: `val stats by hud(StatsHud())`. The property returns the element. */
    protected fun <E : HudElement> hud(element: E, title: String = element.title, description: String = "", section: String = ""): OptionDelegate<HudLayout, E> {
        val accessor = MappedAccessor({ element }, HudElement::layout) { e, l: HudLayout -> e.layout = l }
        return OptionDelegate(title, description, section, OptionSpec.HudSpec(element), Codecs.HUD_LAYOUT, accessor) { element }
    }

    /** An action button. The property's value is unused. */
    protected fun button(title: String, text: String = "Run", description: String = "", section: String = "", action: () -> Unit): OptionDelegate<Any?, Unit> =
        OptionDelegate(title, description, section, OptionSpec.ButtonSpec(text, action), null, ValueAccessor(null)) { }

    /** Log sliders need a positive minimum. */
    private fun Boolean.alsoCheckLog(min: Double): Boolean {
        if (this && min <= 0.0) throw AstraConfigException("Astra: a logarithmic slider in '$id' needs a minimum above 0 (it is $min).")
        return this
    }

    private fun <T> simple(title: String, default: T, description: String, section: String, spec: OptionSpec, codec: Codec<T>?): OptionDelegate<T, T> =
        OptionDelegate(title, description, section, spec, codec, ValueAccessor(default)) { it }

    /**
     * A delegated option. Chain `.onChange { }`, `.hideIf { }`, `.dependsOn(::other)` or `.keywords(...)`
     * before `by` binds it. [T] is the stored value, [V] what the property returns.
     */
    inner class OptionDelegate<T, V> internal constructor(
        private val title: String,
        private val description: String,
        private val section: String,
        private val spec: OptionSpec,
        private val codec: Codec<T>?,
        private val accessor: Accessor<T>,
        /** Converts a property write back to the stored type; null = the types are the same. */
        private val unview: ((V) -> T)? = null,
        private val view: (T) -> V,
    ) : PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, V>> {
        private val listeners = mutableListOf<OptionListener<T>>()
        private var hide: Condition? = null
        private val depends = mutableListOf<String>()
        private val keywords = mutableListOf<String>()

        fun onChange(listener: OptionListener<T>): OptionDelegate<T, V> = apply { listeners += listener }
        fun hideIf(condition: Condition): OptionDelegate<T, V> = apply { hide = condition }
        fun dependsOn(vararg booleans: KProperty<Boolean>): OptionDelegate<T, V> = apply { depends += booleans.map { it.name } }
        fun keywords(vararg words: String): OptionDelegate<T, V> = apply { keywords += words }

        override fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadWriteProperty<Any?, V> {
            val option = Option(
                module = this@KtModule,
                key = property.name,
                title = title,
                description = description,
                section = section.ifEmpty { "General" },
                icon = "",
                keywords = keywords.toList(),
                previousNames = emptyList(),
                spec = spec,
                codec = codec,
                accessor = accessor,
            )
            option.hideIf = hide
            option.listeners += listeners
            option.dependsOnNames += depends
            options += option
            if (spec is OptionSpec.HudSpec) HudRegistry.register(spec.element, this@KtModule, option as Option<xyz.essencexc.astra.api.hud.HudLayout>)

            return object : ReadWriteProperty<Any?, V> {
                override fun getValue(thisRef: Any?, property: KProperty<*>): V = view(option.get())

                @Suppress("UNCHECKED_CAST")
                override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
                    // Only simple delegates (T == V) are writable; keybinds and buttons ignore writes.
                    if (spec == OptionSpec.KeybindSpec || spec is OptionSpec.ButtonSpec || spec is OptionSpec.HudSpec) return
                    option.set(unview?.invoke(value) ?: value as T)
                }
            }
        }
    }
}
