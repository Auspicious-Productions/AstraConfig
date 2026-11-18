package xyz.essencexc.astra.api

import xyz.essencexc.astra.api.function.Condition
import xyz.essencexc.astra.api.function.OptionListener
import xyz.essencexc.astra.core.Lifecycle
import xyz.essencexc.astra.core.Option
import kotlin.reflect.KProperty

/**
 * A module: one card in the menu. Subclass it and declare settings as annotated fields
 * (`@Switch`, `@Slider`, ...). Kotlin users can also extend `KtModule` and use delegates.
 *
 * ```java
 * public final class FishingModule extends Module {
 *     @Switch(title = "Sneak While Fishing")
 *     public static boolean sneak = false;
 *
 *     private FishingModule() { super("fishing", "Fishing"); }
 * }
 * ```
 *
 * @param id stable id used in the config file; don't change it after release.
 */
abstract class Module(
    @get:JvmName("id") val id: String,
    @get:JvmName("name") val name: String,
) {
    /** Text on the card and the module page. */
    var description: String = ""

    /** Icon resource id, e.g. `"mymod:icons/fish.svg"`. */
    var icon: String? = null

    /** ARGB accent colour for the card; 0 = the theme's accent. */
    var accent: Int = 0

    fun description(text: String): Module = apply { description = text }
    fun icon(id: String): Module = apply { icon = id }
    fun accent(argb: Int): Module = apply { accent = argb }

    /** The mod this module was registered with; null until `AstraBuilder.build()`. */
    var mod: AstraMod? = null
        internal set

    internal val options: MutableList<Option<*>> = mutableListOf()
    internal var master: Option<Boolean>? = null
    internal var collected: Boolean = false
    internal val pendingHide: MutableMap<String, Condition> = linkedMapOf()
    internal val pendingListeners: MutableMap<String, MutableList<OptionListener<*>>> = linkedMapOf()

    /** True when the module has no `@MasterToggle`, or it is on. */
    fun isEnabled(): Boolean = master?.get() ?: true

    // Named enable()/disable() rather than setEnabled(), so a Kotlin `@MasterToggle var enabled`
    // (whose setter compiles to setEnabled) doesn't clash with it.

    /** Turns the module on (no-op without a `@MasterToggle`). Fires a cancellable `ModuleToggleEvent`. */
    fun enable() = setState(true)

    /** Turns the module off (no-op without a `@MasterToggle`). Fires a cancellable `ModuleToggleEvent`. */
    fun disable() = setState(false)

    fun toggle() = setState(!isEnabled())

    private fun setState(enabled: Boolean) {
        val m = master ?: return
        m.set(enabled) // the master option syncs the lifecycle itself
        if (m.get() == enabled) Lifecycle.sync(this)
    }

    /** Called when the module turns on, after its `@EventHandler`s are subscribed. */
    open fun onEnable() {}

    /** Called when the module turns off, after its `@EventHandler`s are unsubscribed. */
    open fun onDisable() {}

    /** Hides the option (by field name) while [condition] is true. */
    fun hideIf(option: String, condition: Condition): Module = apply {
        if (collected) find(option).hideIf = condition else pendingHide[option] = condition
    }

    /** Calls [listener] when the option (by field name) changes through the menu or [set]. */
    fun <T> onChange(option: String, listener: OptionListener<T>): Module = apply {
        if (collected) find(option).addListenerUnchecked(listener)
        else pendingListeners.getOrPut(option) { mutableListOf() }.add(listener)
    }

    /** Sets an option by field name and fires `onChange` listeners (a direct field write doesn't). */
    fun set(option: String, value: Any?) = find(option).setUnchecked(value)

    /** Reads an option by field name. */
    fun get(option: String): Any? = find(option).get()

    /** Resets an option to the value its field had at startup. */
    fun reset(option: String) = find(option).reset()

    /** Kotlin: `hideIf(::axeHp) { type != FishingType.NORMAL }`. */
    @JvmSynthetic
    fun hideIf(property: KProperty<*>, condition: Condition): Module = hideIf(property.name, condition)

    /** Kotlin: `onChange(::sneak) { v: Boolean -> ... }`. */
    @JvmSynthetic
    fun <T> onChange(property: KProperty<T>, listener: OptionListener<T>): Module = onChange(property.name, listener)

    internal fun find(option: String): Option<*> =
        options.firstOrNull { it.key == option } ?: throw IllegalArgumentException("Module '$id' has no option '$option'. Options: ${options.joinToString { it.key }}")

    override fun toString(): String = "Module($id)"
}
