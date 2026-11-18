@file:JvmName("Events")

package xyz.essencexc.astra.api.event

import meteordevelopment.orbit.ICancellable
import xyz.essencexc.astra.api.AstraMod
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.keybind.AstraKeybind

/** Base for cancellable Astra events. Orbit stops calling listeners once one cancels. */
abstract class CancellableEvent : ICancellable {
    private var cancelled = false
    override fun setCancelled(cancelled: Boolean) { this.cancelled = cancelled }
    override fun isCancelled(): Boolean = cancelled
}

/** Posted at the end of every client tick. Only reaches modules that are enabled. */
object TickEvent

/** An option changed through the menu or `Module.set`. Not posted for direct field writes. */
class OptionChangedEvent(
    @get:JvmName("module") val module: Module,
    @get:JvmName("option") val option: String,
    @get:JvmName("value") val value: Any?,
)

/** A module is about to be enabled or disabled. Cancel to keep the current state. */
class ModuleToggleEvent(
    @get:JvmName("module") val module: Module,
    @get:JvmName("enabled") val enabled: Boolean,
) : CancellableEvent()

/** A registered keybind was pressed or released. */
class KeybindEvent(
    @get:JvmName("keybind") val keybind: AstraKeybind,
    @get:JvmName("pressed") val pressed: Boolean,
)

/** A mod's config was loaded from disk (values are already in the fields). */
class ConfigLoadEvent(@get:JvmName("mod") val mod: AstraMod)

/** A mod's config is about to be written to disk. */
class ConfigSaveEvent(@get:JvmName("mod") val mod: AstraMod)
