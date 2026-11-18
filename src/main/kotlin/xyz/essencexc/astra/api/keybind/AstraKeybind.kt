package xyz.essencexc.astra.api.keybind

import xyz.essencexc.astra.api.function.KeyAction
import xyz.essencexc.astra.core.KeybindRegistry

/**
 * A rebindable key with callbacks. Declare it as a `@Keybind` field and Astra registers it, saves the
 * user's binding and shows it on the Bound Keys page. For a keybind outside any module call [register].
 *
 * ```java
 * @Keybind(title = "Toggle Fishing Macro")
 * public static AstraKeybind toggleKey = AstraKeybind.of(GLFW.GLFW_KEY_R).onPress(() -> INSTANCE.toggle());
 * ```
 */
class AstraKeybind private constructor(binding: KeyBinding) {
    /** The current binding. Changed by the user in the menu; also settable from code. */
    @Volatile
    var binding: KeyBinding = binding

    /** The binding it was declared with, used by "reset". */
    val defaultBinding: KeyBinding = binding

    internal var pressAction: KeyAction? = null
    internal var releaseAction: KeyAction? = null
    internal var heldAction: KeyAction? = null
    internal var inGui: Boolean = false

    /** Set by the registry while the key is held. */
    @Volatile
    var isPressed: Boolean = false
        internal set

    fun onPress(action: KeyAction): AstraKeybind = apply { pressAction = action }
    fun onRelease(action: KeyAction): AstraKeybind = apply { releaseAction = action }

    /** Runs every client tick while the key is held. */
    fun whileHeld(action: KeyAction): AstraKeybind = apply { heldAction = action }

    /** Also fire while a screen (chat, inventory, menus) is open. Off by default. */
    @JvmOverloads
    fun worksInGui(works: Boolean = true): AstraKeybind = apply { inGui = works }

    fun ctrl(): AstraKeybind = apply { binding = binding.withCtrl() }
    fun shift(): AstraKeybind = apply { binding = binding.withShift() }
    fun alt(): AstraKeybind = apply { binding = binding.withAlt() }

    /** Registers a keybind that isn't a `@Keybind` field. Not needed for fields. */
    @JvmOverloads
    fun register(title: String = "Keybind"): AstraKeybind = apply { KeybindRegistry.register(this, null, title) }

    fun unregister(): AstraKeybind = apply { KeybindRegistry.unregister(this) }

    companion object {
        /** A keyboard key: use `InputConstants.KEY_*` (e.g. `InputConstants.KEY_R`), not GLFW codes. */
        @JvmStatic
        fun of(key: Int): AstraKeybind = AstraKeybind(KeyBinding.key(key))

        /** A mouse button: use `InputConstants.MOUSE_BUTTON_*` (the numbers differ on 26.3, which uses SDL). */
        @JvmStatic
        fun mouse(button: Int): AstraKeybind = AstraKeybind(KeyBinding.mouse(button))

        @JvmStatic
        fun unbound(): AstraKeybind = AstraKeybind(KeyBinding.UNBOUND)
    }
}
