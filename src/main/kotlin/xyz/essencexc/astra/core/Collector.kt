package xyz.essencexc.astra.core

import com.mojang.serialization.Codec
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.annotations.*
import xyz.essencexc.astra.api.annotations.Number
import xyz.essencexc.astra.api.function.ValueFormatter
import xyz.essencexc.astra.api.hud.HudElement
import xyz.essencexc.astra.api.keybind.AstraKeybind
import xyz.essencexc.astra.api.keybind.KeyBinding
import xyz.essencexc.astra.api.value.FloatRange
import xyz.essencexc.astra.api.value.IntRange
import java.lang.invoke.MethodHandles
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Thrown at startup when a module is declared wrongly. The message says what to fix. */
class AstraConfigException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Turns a module class into options: annotated fields first (in declaration order, superclasses
 * first), then `@Button` methods, then each nested `@Section` class in source order.
 */
internal object Collector {
    private const val GENERAL = "General"

    fun collect(module: Module, lookup: MethodHandles.Lookup) {
        if (module.collected) return
        val found = mutableListOf<Option<*>>()

        for (cls in hierarchy(module.javaClass)) collectClass(module, cls, receiver = module, section = null, lookup, found)

        val nested = module.javaClass.declaredClasses.filter { it.isAnnotationPresent(Section::class.java) }
        for (cls in ClassOrder.sort(nested)) {
            val section = cls.getAnnotation(Section::class.java).value
            collectClass(module, cls, receiver = kotlinObjectInstance(cls), section, lookup, found)
        }

        // Delegate options (KtModule) registered themselves during construction; keep them first.
        val all = module.options + found
        val duplicate = all.groupBy { it.key }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) fail(module, "two options are named '${duplicate.key}'. Option names (field names) must be unique within a module, including nested @Section classes.")

        module.options.clear()
        module.options.addAll(all)
        module.master = resolveMaster(module)
        resolveDependencies(module)
        module.collected = true
    }

    private fun hierarchy(cls: Class<*>): List<Class<*>> =
        generateSequence(cls) { it.superclass }.takeWhile { it != Module::class.java && it != Any::class.java && it.name != "xyz.essencexc.astra.api.kt.KtModule" }.toList().asReversed()

    private fun collectClass(module: Module, cls: Class<*>, receiver: Any?, section: String?, lookup: MethodHandles.Lookup, out: MutableList<Option<*>>) {
        for (field in cls.declaredFields) {
            if (field.isSynthetic) continue
            val option = fieldOption(module, field, receiver, section, lookup) ?: continue
            out += option
        }
        for (method in cls.declaredMethods) {
            val button = method.getAnnotation(Button::class.java) ?: continue
            out += buttonOption(module, method, button, receiver, section, lookup)
        }
    }

    private fun fieldOption(module: Module, field: Field, receiver: Any?, nestedSection: String?, lookup: MethodHandles.Lookup): Option<*>? {
        val master = field.getAnnotation(MasterToggle::class.java)
        val annotations = field.annotations.filter { it.annotationClass.java.`package`?.name == Switch::class.java.`package`.name && it !is PreviousNames && it !is DependsOn }
        if (annotations.isEmpty()) return null
        if (annotations.size > 1) fail(module, "field '${field.name}' has more than one option annotation (${annotations.joinToString { "@" + it.annotationClass.simpleName }}).")
        val annotation = annotations.single()

        val static = Modifier.isStatic(field.modifiers)
        if (Modifier.isFinal(field.modifiers) && annotation !is Hud) fail(module, "field '${field.name}' is final. Option fields must be writable: remove 'final' (Java) or use 'var' instead of 'val' (Kotlin).")
        if (!static && receiver == null) fail(module, "field '${field.name}' in @Section class ${field.declaringClass.simpleName} must be static (Java) or the class must be a Kotlin object.")
        val actualReceiver = if (static) null else receiver

        val type = field.type
        val accessor = { FieldAccessor<Any?>(field, actualReceiver, lookup) }
        fun expect(ok: Boolean, what: String) {
            if (!ok) fail(module, "field '${field.name}' is @${annotation.annotationClass.simpleName} but has type ${type.simpleName}; expected $what.")
        }
        val isBoolean = type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType

        if (master != null) {
            expect(isBoolean, "boolean")
            return build(module, field, "Enabled", "", GENERAL, "", emptyArray(), OptionSpec.MasterToggleSpec, Codec.BOOL, accessor(), nestedSection)
        }

        return when (annotation) {
            is Switch -> {
                expect(isBoolean, "boolean")
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, OptionSpec.SwitchSpec, Codec.BOOL, accessor(), nestedSection)
            }
            is Checkbox -> {
                expect(isBoolean, "boolean")
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, OptionSpec.CheckboxSpec, Codec.BOOL, accessor(), nestedSection)
            }
            is Slider -> {
                val kind = NumberKind.of(type)
                expect(kind != null, "int, float or double")
                checkBounds(module, field, annotation.min, annotation.max, annotation.step)
                if (annotation.logarithmic && annotation.min <= 0f) fail(module, "@Slider field '${field.name}' is logarithmic, so min must be above 0 (it is ${annotation.min}).")
                val spec = OptionSpec.SliderSpec(annotation.min.toDouble(), annotation.max.toDouble(), annotation.step.toDouble(), kind!!, formatter(module, annotation.formatter.java), annotation.logarithmic)
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, spec, Codecs.number(kind), accessor(), nestedSection)
            }
            is RangeSlider -> {
                val integer = type == IntRange::class.java
                expect(integer || type == FloatRange::class.java, "IntRange or FloatRange from xyz.essencexc.astra.api.value")
                checkBounds(module, field, annotation.min, annotation.max, annotation.step)
                val spec = OptionSpec.RangeSliderSpec(annotation.min.toDouble(), annotation.max.toDouble(), annotation.step.toDouble(), annotation.minSpread.toDouble(), integer, formatter(module, annotation.formatter.java))
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, spec, if (integer) Codecs.INT_RANGE else Codecs.FLOAT_RANGE, accessor(), nestedSection)
            }
            is Number -> {
                val kind = NumberKind.of(type)
                expect(kind != null, "int, float or double")
                val spec = OptionSpec.NumberSpec(annotation.min.toDouble(), annotation.max.toDouble(), annotation.unit, kind!!)
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, spec, Codecs.number(kind), accessor(), nestedSection)
            }
            is Dropdown -> {
                if (type.isEnum) {
                    @Suppress("UNCHECKED_CAST")
                    val enumType = type as Class<out Enum<*>>
                    val spec = OptionSpec.DropdownSpec(enumType.enumConstants.map(::label), enumType)
                    build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, spec, enumCodec(enumType), accessor(), nestedSection)
                } else {
                    expect(type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType, "an enum, or int with options = {...}")
                    if (annotation.options.isEmpty()) fail(module, "field '${field.name}' is an int @Dropdown without options. Add options = {\"A\", \"B\"} or use an enum field.")
                    val spec = OptionSpec.DropdownSpec(annotation.options.toList(), null)
                    build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, spec, Codec.INT, accessor(), nestedSection)
                }
            }
            is Text -> {
                expect(type == String::class.java, "String")
                val spec = OptionSpec.TextSpec(annotation.placeholder, annotation.multiline, annotation.maxLength)
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, spec, Codec.STRING, accessor(), nestedSection)
            }
            is Color -> {
                expect(type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType, "int (ARGB)")
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, OptionSpec.ColorSpec(annotation.alpha), Codecs.COLOR, accessor(), nestedSection)
            }
            is Keybind -> {
                expect(type == AstraKeybind::class.java, "AstraKeybind")
                val fieldAccess = accessor()
                val keybind = fieldAccess.get() as? AstraKeybind ?: fail(module, "@Keybind field '${field.name}' is null. Initialise it, e.g. AstraKeybind.of(GLFW.GLFW_KEY_R).")
                val binding = MappedAccessor({ fieldAccess.get() as AstraKeybind }, AstraKeybind::binding) { kb, b: KeyBinding -> kb.binding = b }
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, OptionSpec.KeybindSpec, Codecs.KEY_BINDING, binding, nestedSection)
                    .also { KeybindRegistry.register(keybind, module, annotation.title) }
            }
            is Info -> {
                expect(type == String::class.java, "String (the text to show)")
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, OptionSpec.InfoSpec(annotation.type), null, accessor(), nestedSection)
            }
            is Position -> {
                val integer = type == net.minecraft.core.BlockPos::class.java
                expect(integer || type == net.minecraft.world.phys.Vec3::class.java, "BlockPos or Vec3")
                val codec = if (integer) net.minecraft.core.BlockPos.CODEC else net.minecraft.world.phys.Vec3.CODEC
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, OptionSpec.PositionSpec(integer), codec, accessor(), nestedSection)
            }
            is MultiSelect -> {
                val kind = OptionCollections.kind(type) ?: fail(module, "@MultiSelect field '${field.name}' must be a Set, EnumSet or List.")
                val element = OptionCollections.elementType(field)
                val choices: List<Any>
                val labels: List<String>
                if (element != null && element.isEnum) {
                    choices = element.enumConstants.toList()
                    labels = choices.map { label(it as Enum<*>) }
                } else {
                    if (annotation.options.isEmpty()) fail(module, "@MultiSelect field '${field.name}' holds strings but has no options. Add options = {\"A\", \"B\"} or use an enum element type.")
                    if (kind == OptionCollections.Kind.ENUM_SET) fail(module, "@MultiSelect field '${field.name}': couldn't read the EnumSet's enum type.")
                    choices = annotation.options.toList()
                    labels = annotation.options.toList()
                }
                val acc = OptionCollections.selectionAccessor(accessor(), choices, kind, element)
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, OptionSpec.MultiSelectSpec(labels, choices), OptionCollections.choiceCodec(choices), acc, nestedSection)
            }
            is ItemList -> {
                expect(OptionCollections.kind(type) == OptionCollections.Kind.LIST, "List<String>")
                val spec = OptionSpec.ItemListSpec(annotation.placeholder, annotation.maxItems, annotation.maxLength)
                build(module, field, annotation.title, annotation.description, annotation.section, annotation.icon, annotation.keywords, spec, OptionCollections.STRING_LIST, OptionCollections.stringListAccessor(accessor()), nestedSection)
            }
            is Hud -> {
                expect(HudElement::class.java.isAssignableFrom(type), "a HudElement subclass")
                val element = accessor().get() as? HudElement ?: fail(module, "@Hud field '${field.name}' is null. Initialise it with your HudElement.")
                HudRegistry.option(module, element, annotation.title.ifEmpty { element.title }, annotation.description, annotation.section.ifEmpty { nestedSection ?: GENERAL }, annotation.keywords.toList(), field.name)
            }
            else -> null
        }
    }

    private fun buttonOption(module: Module, method: Method, button: Button, receiver: Any?, nestedSection: String?, lookup: MethodHandles.Lookup): Option<*> {
        if (method.parameterCount != 0) fail(module, "@Button method '${method.name}' must take no parameters.")
        val static = Modifier.isStatic(method.modifiers)
        if (!static && receiver == null) fail(module, "@Button method '${method.name}' in ${method.declaringClass.simpleName} must be static (or the class a Kotlin object).")

        var handle = MethodHandles.privateLookupIn(method.declaringClass, lookup).unreflect(method)
        if (!static) handle = handle.bindTo(receiver)
        val action = { handle.invoke(); Unit }

        val section = button.section.ifEmpty { nestedSection ?: GENERAL }
        return Option(module, method.name, button.title, button.description, section, button.icon, button.keywords.toList(), emptyList(), OptionSpec.ButtonSpec(button.text, action), null, ValueAccessor<Any?>(null))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> build(
        module: Module, field: Field, title: String, description: String, section: String, icon: String, keywords: Array<String>,
        spec: OptionSpec, codec: Codec<*>?, accessor: Accessor<T>, nestedSection: String?,
    ): Option<T> {
        val option = Option(
            module = module,
            key = field.name,
            title = title,
            description = description,
            section = section.ifEmpty { nestedSection ?: GENERAL },
            icon = icon,
            keywords = keywords.toList(),
            previousNames = field.getAnnotation(PreviousNames::class.java)?.value?.toList() ?: emptyList(),
            spec = spec,
            codec = codec as Codec<T>?,
            accessor = accessor,
        )
        field.getAnnotation(DependsOn::class.java)?.let { option.dependsOnNames += it.value }
        return option
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveMaster(module: Module): Option<Boolean>? {
        val masters = module.options.filter { it.spec == OptionSpec.MasterToggleSpec }
        if (masters.size > 1) fail(module, "has more than one @MasterToggle.")
        return masters.firstOrNull() as Option<Boolean>?
    }

    private fun resolveDependencies(module: Module) {
        for (option in module.options) {
            option.dependsOn = option.dependsOnNames.map { name ->
                val target = module.options.firstOrNull { it.key == name } ?: fail(module, "@DependsOn(\"$name\") on '${option.key}' names an option that doesn't exist.")
                if (target.spec !in setOf(OptionSpec.SwitchSpec, OptionSpec.CheckboxSpec, OptionSpec.MasterToggleSpec)) fail(module, "@DependsOn(\"$name\") on '${option.key}' must name a boolean option.")
                target
            }
        }
        for ((name, condition) in module.pendingHide) {
            val option = module.options.firstOrNull { it.key == name } ?: fail(module, "hideIf(\"$name\", ...) names an option that doesn't exist.")
            option.hideIf = condition
        }
        for ((name, listeners) in module.pendingListeners) {
            val option = module.options.firstOrNull { it.key == name } ?: fail(module, "onChange(\"$name\", ...) names an option that doesn't exist.")
            listeners.forEach(option::addListenerUnchecked)
        }
        module.pendingHide.clear()
        module.pendingListeners.clear()
    }

    private fun checkBounds(module: Module, field: Field, min: Float, max: Float, step: Float) {
        if (min >= max) fail(module, "field '${field.name}': min ($min) must be less than max ($max).")
        if (step <= 0f) fail(module, "field '${field.name}': step must be positive.")
    }

    internal fun formatter(module: Module, cls: Class<out ValueFormatter>): ValueFormatter {
        if (cls == ValueFormatter::class.java) return ValueFormatter.Default()
        return try {
            cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        } catch (e: ReflectiveOperationException) {
            fail(module, "formatter ${cls.name} needs a public no-argument constructor.", e)
        }
    }

    /** `"BARN_FISHING"` -> `"Barn Fishing"`, unless the enum overrides toString. */
    internal fun label(constant: Enum<*>): String {
        val text = constant.toString()
        if (text != constant.name) return text
        return text.split('_').filter { it.isNotEmpty() }.joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun enumCodec(type: Class<out Enum<*>>): Codec<*> = Codecs.enumCodec(type as Class<Enum<*>>)

    /** The `INSTANCE` of a Kotlin object, or null for a plain (static) Java class. */
    private fun kotlinObjectInstance(cls: Class<*>): Any? = try {
        val f = cls.getDeclaredField("INSTANCE")
        if (Modifier.isStatic(f.modifiers) && f.type == cls) f.apply { isAccessible = true }.get(null) else null
    } catch (_: NoSuchFieldException) {
        null
    }

    internal fun fail(module: Module, message: String, cause: Throwable? = null): Nothing =
        throw AstraConfigException("Astra: module '${module.id}' (${module.javaClass.name}): $message", cause)
}
