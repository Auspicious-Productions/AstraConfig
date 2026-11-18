package xyz.essencexc.astra.core

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/** Reads and writes the value behind an option: a dev's field, or a Kotlin delegate's storage. */
internal interface Accessor<T> {
    fun get(): T
    fun set(value: T)
}

/** Field access through method handles, created with the mod's lookup so private fields work. */
internal class FieldAccessor<T>(private val field: Field, private val receiver: Any?, lookup: MethodHandles.Lookup) : Accessor<T> {
    private val getter: MethodHandle
    /** Null for final fields (e.g. `@Hud`), which are only ever read. */
    private val setter: MethodHandle?

    init {
        val private = MethodHandles.privateLookupIn(field.declaringClass, lookup)
        val static = Modifier.isStatic(field.modifiers)
        var g = private.unreflectGetter(field)
        var s = if (Modifier.isFinal(field.modifiers)) null else private.unreflectSetter(field)
        if (!static) {
            g = g.bindTo(receiver)
            s = s?.bindTo(receiver)
        }
        // Normalise to (Object)Object / (Object)void so primitives are boxed for us.
        getter = g.asType(g.type().changeReturnType(Any::class.java))
        setter = s?.let { it.asType(it.type().changeParameterType(0, Any::class.java)) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(): T = getter.invoke() as T

    override fun set(value: T) {
        val s = setter ?: throw IllegalStateException("Field '${field.name}' is final and can't be written.")
        s.invoke(value as Any?)
    }
}

/** Plain in-memory storage, used by Kotlin delegates. */
internal class ValueAccessor<T>(@Volatile var value: T) : Accessor<T> {
    override fun get(): T = value
    override fun set(value: T) { this.value = value }
}

/** Maps another accessor's value, e.g. an `AstraKeybind` field to its `KeyBinding`. */
internal class MappedAccessor<S, T>(private val source: () -> S, private val read: (S) -> T, private val write: (S, T) -> Unit) : Accessor<T> {
    override fun get(): T = read(source())
    override fun set(value: T) = write(source(), value)
}
