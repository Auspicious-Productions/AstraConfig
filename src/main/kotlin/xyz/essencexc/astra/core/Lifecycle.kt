package xyz.essencexc.astra.core

import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.event.ModuleToggleEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps each module's Orbit subscription in line with its master toggle. Runs on every change made
 * through Astra and once per tick, so direct writes to the `@MasterToggle` field also take effect.
 */
internal object Lifecycle {
    /** The state last applied (subscribed or not). Absent = never applied. */
    private val applied = ConcurrentHashMap<Module, Boolean>()

    fun sync(module: Module) {
        // Not registered yet: build() syncs every module once its config is loaded.
        if (module.mod?.store == null) return
        val wanted = module.isEnabled()
        val current = applied[module]
        if (current == wanted) return

        // The very first sync is the initial state, not a toggle, so it can't be cancelled.
        if (current != null) {
            val event = Astra.EVENTS.post(ModuleToggleEvent(module, wanted))
            if (event.isCancelled) {
                module.master?.setSilently(current)
                return
            }
        }

        applied[module] = wanted
        if (wanted) {
            Astra.EVENTS.subscribe(module)
            runCatching { module.onEnable() }.onFailure { Astra.LOGGER.error("onEnable failed in {}", module, it) }
        } else {
            Astra.EVENTS.unsubscribe(module)
            if (current != null) runCatching { module.onDisable() }.onFailure { Astra.LOGGER.error("onDisable failed in {}", module, it) }
        }
    }

    fun syncAll() {
        for (mod in Astra.mods()) for (m in mod.modules()) sync(m)
    }
}
