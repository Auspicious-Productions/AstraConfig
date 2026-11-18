package xyz.essencexc.astra.core

import com.mojang.blaze3d.platform.InputConstants
import foo.starred.snowbird.api.client
import foo.starred.snowbird.api.inputs.impl.GenericInputState
import foo.starred.snowbird.api.inputs.impl.KeyboardInputState
import foo.starred.snowbird.api.inputs.impl.MouseInputState
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.AstraMod
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.event.KeybindEvent
import xyz.essencexc.astra.api.keybind.AstraKeybind
import xyz.essencexc.astra.api.keybind.InputType
import xyz.essencexc.astra.api.keybind.KeyBinding
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Every Astra keybind across all mods. Polled once per client tick; no vanilla `KeyMapping`s are
 * registered, so binds only appear on Astra's Bound Keys page.
 */
internal object KeybindRegistry {
    class Entry(val keybind: AstraKeybind, val module: Module?, val title: String, private val owner: AstraMod? = null) {
        /** The mod this keybind belongs to, if any. */
        val mod: AstraMod? get() = owner ?: module?.mod
    }

    private val entries = CopyOnWriteArrayList<Entry>()

    fun register(keybind: AstraKeybind, module: Module?, title: String, mod: AstraMod? = null) {
        if (entries.any { it.keybind === keybind }) return
        entries += Entry(keybind, module, title, mod)
    }

    fun unregister(keybind: AstraKeybind) {
        entries.removeIf { it.keybind === keybind }
    }

    fun entries(): List<Entry> = entries.toList()

    /** Bindings used by more than one keybind, for the Bound Keys page. */
    fun conflicts(): Map<KeyBinding, List<Entry>> =
        entries.filter { it.keybind.binding.bound }.groupBy { it.keybind.binding }.filterValues { it.size > 1 }

    fun tick() {
        //~ if >= 26.2 'client.screen' -> 'client.gui.screen()'
        val screenOpen = client.screen != null
        for (entry in entries) {
            val kb = entry.keybind
            val down = (!screenOpen || kb.inGui) && isDown(kb.binding)

            if (down && !kb.isPressed) {
                kb.isPressed = true
                run(kb.pressAction, entry)
                Astra.EVENTS.post(KeybindEvent(kb, true))
            } else if (!down && kb.isPressed) {
                kb.isPressed = false
                run(kb.releaseAction, entry)
                Astra.EVENTS.post(KeybindEvent(kb, false))
            }
            if (down) run(kb.heldAction, entry)
        }
    }

    private fun run(action: xyz.essencexc.astra.api.function.KeyAction?, entry: Entry) {
        action ?: return
        runCatching { action.run() }.onFailure { Astra.LOGGER.error("Keybind '{}' threw", entry.title, it) }
    }

    private fun isDown(binding: KeyBinding): Boolean {
        if (!binding.bound) return false
        if (binding.ctrl && !KeyboardInputState.States.control()) return false
        if (binding.shift && !KeyboardInputState.States.shift()) return false
        if (binding.alt && !alt()) return false

        val key = when (binding.type) {
            InputType.KEYBOARD -> KeyboardInputState.vanilla(binding.code)
            InputType.MOUSE -> MouseInputState.vanilla(binding.code)
        }
        return GenericInputState.pressed(key)
    }

    private fun alt(): Boolean =
        KeyboardInputState.pressed(KeyboardInputState.vanilla(InputConstants.KEY_LALT)) || KeyboardInputState.pressed(KeyboardInputState.vanilla(InputConstants.KEY_RALT))
}
