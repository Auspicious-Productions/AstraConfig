package xyz.essencexc.astra.ui.core

import com.mojang.blaze3d.platform.InputConstants
import foo.starred.snowbird.api.client
import foo.starred.snowbird.api.inputs.impl.KeyboardInputState
import xyz.essencexc.astra.api.keybind.InputType
import xyz.essencexc.astra.api.keybind.KeyBinding

/** Keyboard helpers for widgets. Key codes are `InputConstants.KEY_*`, which differ on 26.3 (SDL). */
object Keys {
    fun ctrl(): Boolean = KeyboardInputState.States.control()
    fun shift(): Boolean = KeyboardInputState.States.shift()
    fun alt(): Boolean =
        KeyboardInputState.pressed(KeyboardInputState.vanilla(InputConstants.KEY_LALT)) ||
            KeyboardInputState.pressed(KeyboardInputState.vanilla(InputConstants.KEY_RALT))

    fun isModifier(key: Int): Boolean = key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL ||
        key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT ||
        key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT ||
        //~ if >= 26.3 'KEY_LSUPER' -> 'KEY_LGUI'
        key == InputConstants.KEY_LSUPER ||
        //~ if >= 26.3 'KEY_RSUPER' -> 'KEY_RGUI'
        key == InputConstants.KEY_RSUPER

    /**
     * Mouse buttons as 0 = left, 1 = right, 2 = middle, 3+ = side buttons, on every version.
     * 26.3 (SDL) numbers them 1 = left, 2 = middle, 3 = right; widgets only ever see this form.
     */
    @JvmStatic
    fun button(raw: Int): Int = when (raw) {
        InputConstants.MOUSE_BUTTON_LEFT -> 0
        InputConstants.MOUSE_BUTTON_RIGHT -> 1
        InputConstants.MOUSE_BUTTON_MIDDLE -> 2
        else -> raw - InputConstants.MOUSE_BUTTON_4 + 3
    }

    /** Inverse of [button]: the version's own code, as keybinds store it. */
    @JvmStatic
    fun rawButton(button: Int): Int = when (button) {
        0 -> InputConstants.MOUSE_BUTTON_LEFT
        1 -> InputConstants.MOUSE_BUTTON_RIGHT
        2 -> InputConstants.MOUSE_BUTTON_MIDDLE
        else -> button - 3 + InputConstants.MOUSE_BUTTON_4
    }

    /**
     * 26.3 (SDL) only delivers typed characters while text input is switched on, which vanilla
     * does for its own text boxes. Astra's text fields call this when they gain or lose focus.
     * No-op on older versions.
     */
    @JvmStatic
    fun textInput(owner: Any, on: Boolean) {
        runCatching {
            //? if >= 26.3 {
            /*if (on) client.textInputManager().startTextInput(owner) else client.textInputManager().stopTextInput(owner)
            *///?}
        }
    }

    /** Switches text input off entirely (screen closed). */
    @JvmStatic
    fun stopTextInput() {
        runCatching {
            //? if >= 26.3 {
            /*client.textInputManager().stopTextInput()
            *///?}
        }
    }

    fun isEnter(key: Int): Boolean = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
    fun isActivate(key: Int): Boolean = isEnter(key) || key == InputConstants.KEY_SPACE

    var clipboard: String
        get() = runCatching { client.keyboardHandler.clipboard }.getOrDefault("")
        set(value) {
            runCatching { client.keyboardHandler.clipboard = value }
        }

    /** "R", "Ctrl + Shift + F", "Mouse 4", "None". */
    @JvmStatic
    fun describe(binding: KeyBinding): String {
        if (!binding.bound) return "None"
        val parts = ArrayList<String>(4)
        if (binding.ctrl) parts += "Ctrl"
        if (binding.shift) parts += "Shift"
        if (binding.alt) parts += "Alt"
        parts += when (binding.type) {
            InputType.KEYBOARD -> keyName(binding.code)
            InputType.MOUSE -> "Mouse ${button(binding.code) + 1}"
        }
        return parts.joinToString(" + ")
    }

    @JvmStatic
    fun keyName(code: Int): String {
        //~ if >= 26.3 'KEYSYM' -> 'KEYBOARD'
        val name = runCatching { InputConstants.Type.KEYSYM.getOrCreate(code).displayName.string }.getOrNull()
        if (name.isNullOrBlank()) return "Key $code"
        return if (name.length == 1) name.uppercase() else name
    }
}
