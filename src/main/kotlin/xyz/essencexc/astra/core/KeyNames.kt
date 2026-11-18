package xyz.essencexc.astra.core

import com.mojang.blaze3d.platform.InputConstants
import xyz.essencexc.astra.api.keybind.InputType
import xyz.essencexc.astra.api.keybind.KeyBinding

/**
 * Minecraft key names ("key.keyboard.r", "key.mouse.4"). Key codes differ between versions (26.3
 * uses SDL scancodes), names don't, so configs save the name and resolve it on load.
 *
 * Every call is guarded: outside a running game (unit tests) InputConstants may not initialise,
 * and then callers fall back to the raw code.
 */
internal object KeyNames {
    fun name(b: KeyBinding): String? {
        if (!b.bound) return null
        return runCatching {
            //~ if >= 26.3 'KEYSYM' -> 'KEYBOARD'
            val keyboard = InputConstants.Type.KEYSYM
            val type = if (b.type == InputType.MOUSE) InputConstants.Type.MOUSE else keyboard
            type.getOrCreate(b.code).name
        }.getOrNull()
    }

    /** The input type and this version's code for a saved name; null if unknown here. */
    fun resolve(name: String): Pair<InputType, Int>? = runCatching {
        val key = InputConstants.getKey(name)
        if (key == InputConstants.UNKNOWN) null
        else (if (key.type == InputConstants.Type.MOUSE) InputType.MOUSE else InputType.KEYBOARD) to key.value
    }.getOrNull()
}
