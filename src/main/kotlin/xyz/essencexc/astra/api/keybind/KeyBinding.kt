package xyz.essencexc.astra.api.keybind

/** Keyboard keys use GLFW key codes, mouse buttons use GLFW mouse button numbers (0 = left). */
enum class InputType { KEYBOARD, MOUSE }

/** An immutable key combination: an input plus required modifiers. [UNBOUND] when no key is set. */
class KeyBinding(
    @get:JvmName("type") val type: InputType,
    @get:JvmName("code") val code: Int,
    @get:JvmName("ctrl") val ctrl: Boolean = false,
    @get:JvmName("shift") val shift: Boolean = false,
    @get:JvmName("alt") val alt: Boolean = false,
) {
    val bound: Boolean
        @JvmName("isBound") get() = code != UNKNOWN_CODE

    fun withCtrl(ctrl: Boolean = true) = KeyBinding(type, code, ctrl, shift, alt)
    fun withShift(shift: Boolean = true) = KeyBinding(type, code, ctrl, shift, alt)
    fun withAlt(alt: Boolean = true) = KeyBinding(type, code, ctrl, shift, alt)

    /** Same input and modifiers, used for conflict detection. */
    override fun equals(other: Any?): Boolean =
        other is KeyBinding && other.type == type && other.code == code && other.ctrl == ctrl && other.shift == shift && other.alt == alt

    override fun hashCode(): Int = ((type.hashCode() * 31 + code) * 31 + ctrl.hashCode()) * 31 + (shift.hashCode() * 2 + alt.hashCode())

    override fun toString(): String = buildString {
        if (!bound) return "None"
        if (ctrl) append("Ctrl+")
        if (shift) append("Shift+")
        if (alt) append("Alt+")
        append(if (type == InputType.MOUSE) "Mouse$code" else "Key$code")
    }

    companion object {
        /** GLFW_KEY_UNKNOWN. */
        const val UNKNOWN_CODE: Int = -1

        @JvmField
        val UNBOUND: KeyBinding = KeyBinding(InputType.KEYBOARD, UNKNOWN_CODE)

        @JvmStatic
        fun key(code: Int): KeyBinding = KeyBinding(InputType.KEYBOARD, code)

        @JvmStatic
        fun mouse(button: Int): KeyBinding = KeyBinding(InputType.MOUSE, button)
    }
}
