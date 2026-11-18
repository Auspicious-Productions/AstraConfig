package xyz.essencexc.astra.core

import com.mojang.serialization.Codec
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import java.util.EnumSet

/**
 * Conversions for collection options (`@MultiSelect`, `@ItemList`). Options store a canonical
 * `List`; the dev's field keeps whatever collection type it declared (Set, EnumSet, List), and Astra
 * writes a fresh one on every change.
 */
internal object OptionCollections {
    enum class Kind { ENUM_SET, SET, LIST }

    fun kind(type: Class<*>): Kind? = when {
        EnumSet::class.java.isAssignableFrom(type) -> Kind.ENUM_SET
        Set::class.java.isAssignableFrom(type) -> Kind.SET
        List::class.java.isAssignableFrom(type) || type == Collection::class.java -> Kind.LIST
        else -> null
    }

    /** The element class of a `Set<X>` / `List<X>` field, if it can be read. */
    fun elementType(field: Field): Class<*>? {
        val p = field.genericType as? ParameterizedType ?: return null
        return when (val a = p.actualTypeArguments.firstOrNull()) {
            is Class<*> -> a
            is WildcardType -> a.upperBounds.firstOrNull() as? Class<*>
            else -> null
        }
    }

    /** Saved name of a choice: enum constant name or the string itself. */
    fun key(choice: Any): String = (choice as? Enum<*>)?.name ?: choice.toString()

    /** A list of choice names in the config; unknown names are dropped on load. */
    fun choiceCodec(choices: List<Any>): Codec<List<Any>> =
        Codec.STRING.listOf().xmap(
            { names -> choices.filter { c -> key(c) in names } },
            { values -> values.map(::key) },
        )

    @Suppress("UNCHECKED_CAST")
    fun build(kind: Kind, elementType: Class<*>?, values: Collection<Any>): Any = when (kind) {
        Kind.ENUM_SET -> EnumSet.noneOf(elementType as Class<Nothing>).also { (it as MutableSet<Any>).addAll(values) }
        Kind.SET -> LinkedHashSet(values)
        Kind.LIST -> ArrayList(values)
    }

    /** Field (any collection) ↔ selected choices in choice order. */
    fun selectionAccessor(field: Accessor<Any?>, choices: List<Any>, kind: Kind, elementType: Class<*>?): Accessor<List<Any>> =
        MappedAccessor({ field }, { f -> (f.get() as? Collection<*>).orEmpty().let { set -> choices.filter { it in set } } }) { f, v -> f.set(build(kind, elementType, v)) }

    /** Field `List<String>` ↔ a plain list of strings. */
    fun stringListAccessor(field: Accessor<Any?>): Accessor<List<String>> =
        MappedAccessor({ field }, { f -> (f.get() as? Collection<*>).orEmpty().map { it.toString() } }) { f, v -> f.set(ArrayList(v)) }

    val STRING_LIST: Codec<List<String>> = Codec.STRING.listOf()
}
