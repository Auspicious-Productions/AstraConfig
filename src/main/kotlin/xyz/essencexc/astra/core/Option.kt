package xyz.essencexc.astra.core

import com.mojang.serialization.Codec
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.event.OptionChangedEvent
import xyz.essencexc.astra.api.function.Condition
import xyz.essencexc.astra.api.function.OptionListener
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One row in the menu, backed by a dev's field or a Kotlin delegate. Internal: devs use fields,
 * `Module.set/get/onChange/hideIf`, and never see this type.
 */
internal class Option<T>(
    val module: Module,
    /** Field or property name. Saved as `"<moduleId>.<key>"`. */
    val key: String,
    val title: String,
    val description: String,
    val section: String,
    val icon: String,
    val keywords: List<String>,
    val previousNames: List<String>,
    val spec: OptionSpec,
    /** Null for options that aren't saved (buttons, info text). */
    val codec: Codec<T>?,
    private val accessor: Accessor<T>,
) {
    /** Value at collection time, used by reset. */
    val default: T = accessor.get()

    var hideIf: Condition? = null
    val dependsOnNames: MutableList<String> = mutableListOf()
    var dependsOn: List<Option<*>> = emptyList()
    val listeners: MutableList<OptionListener<T>> = CopyOnWriteArrayList()

    val saved: Boolean
        get() = codec != null

    val hidden: Boolean
        get() = hideIf?.test() == true

    /** Greyed out because a `@DependsOn` option is off. */
    val disabled: Boolean
        get() = dependsOn.any { it.get() == false }

    val savedKey: String
        get() = "${module.id}.$key"

    fun get(): T = accessor.get()

    /** Writes the value and notifies listeners, the event bus and the config store. */
    fun set(value: T) {
        val old = accessor.get()
        accessor.set(value)
        if (old == value) return

        for (l in listeners) l.onChange(value)
        Astra.EVENTS.post(OptionChangedEvent(module, key, value))
        module.mod?.store?.markDirty()
        if (spec == OptionSpec.MasterToggleSpec) Lifecycle.sync(module)
    }

    /** Writes without notifying anyone. Used when loading the config. */
    fun setSilently(value: T) = accessor.set(value)

    fun reset() = set(default)

    @Suppress("UNCHECKED_CAST")
    fun setUnchecked(value: Any?) = set(value as T)

    @Suppress("UNCHECKED_CAST")
    fun addListenerUnchecked(listener: OptionListener<*>) = listeners.add(listener as OptionListener<T>)

    override fun toString(): String = "Option($savedKey = ${get()})"
}
