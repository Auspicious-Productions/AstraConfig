package xyz.essencexc.astra.ui.widgets

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.keybind.InputType
import xyz.essencexc.astra.api.keybind.KeyBinding
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint
import kotlin.math.sin

/**
 * Shows a binding ("Ctrl + R") and records a new one.
 *
 * Click (or Enter/Space) to listen, then press a key, optionally with Ctrl/Shift/Alt, or a mouse
 * button other than left. A modifier pressed and released on its own binds the modifier. Escape or
 * left-click cancels, Backspace/Delete clears. Right-click while not listening also clears.
 */
class KeybindButton(val binding: Binding<KeyBinding>) : AstraWidget() {
    var listening: Boolean = false
        private set

    /** Modifier held down while listening, waiting to see if another key follows. */
    private var pendingModifier = -1
    private val listen = Motion()

    init {
        fixedHeight = TextField.CONTROL_HEIGHT
    }

    override fun prefWidth(): Float = fixedWidth ?: (Paint.width(Keys.describe(binding.get()), theme.fontBody, true) + 22f).coerceIn(70f, 180f)

    private fun start() {
        listening = true
        pendingModifier = -1
        unfocus = false // every click now comes to us
        focus()
    }

    private fun stop() {
        listening = false
        pendingModifier = -1
        unfocus = true
    }

    private fun bind(b: KeyBinding) {
        binding.set(b)
        stop()
        root.dirty() // the label width may change
    }

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
        if (listening) {
            when (button) {
                0 -> stop()
                else -> bind(withMods(KeyBinding.mouse(Keys.rawButton(button))))
            }
            return true
        }
        when (button) {
            0 -> start()
            1 -> bind(KeyBinding.UNBOUND)
        }
        return true
    }

    override fun onKey(key: Int): Boolean {
        if (!listening) {
            if (Keys.isActivate(key)) {
                start()
                return true
            }
            if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                bind(KeyBinding.UNBOUND)
                return true
            }
            return false
        }
        when {
            key == InputConstants.KEY_ESCAPE -> stop()
            key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE -> bind(KeyBinding.UNBOUND)
            Keys.isModifier(key) -> pendingModifier = key
            else -> bind(withMods(KeyBinding.key(key)))
        }
        return true
    }

    override fun onKeyUp(key: Int): Boolean {
        if (!listening) return false
        if (key == pendingModifier) bind(KeyBinding.key(key))
        return true
    }

    override fun onFocusChanged(focused: Boolean) {
        if (!focused && listening) stop()
    }

    private fun withMods(b: KeyBinding): KeyBinding {
        var r = b
        if (Keys.ctrl()) r = r.withCtrl()
        if (Keys.shift()) r = r.withShift()
        if (Keys.alt()) r = r.withAlt()
        return r
    }

    override fun paint(g: GuiGraphicsExtractor) {
        val th = theme
        val accent = accentColor()
        listen.to(listening)
        val l = listen.get()
        val h = hover.get()
        val r = th.radiusSmall
        val b = binding.get()

        val pulse = if (listening) (0.55f + 0.45f * sin(System.currentTimeMillis() / 180.0).toFloat()) else 1f
        Paint.rect(g, x, y, width, height, c(Colors.mix(Colors.mix(th.surfaceRaised, th.surfaceHover, h), Colors.fade(accent, 0.16f), l)), r)
        Paint.outline(g, x, y, width, height, c(Colors.mix(Colors.mix(th.border, th.borderStrong, h), Colors.fade(accent, pulse), l)), r)
        focusRing(g, x, y, width, height, r)

        val (text, col, bold) = when {
            listening -> Triple(if (pendingModifier >= 0) Keys.keyName(pendingModifier) + " + ..." else "Press a key...", accent, false)
            !b.bound -> Triple("None", th.textFaint, false)
            else -> Triple(Keys.describe(b), th.text, true)
        }
        Paint.textCentered(g, Paint.truncate(text, th.fontBody, width - 10f, bold), x + width / 2f, y, height, c(col), th.fontBody, bold)

        if (!listening && b.bound && b.type == InputType.MOUSE) {
            // Tiny mouse glyph so mouse binds stand out.
            Paint.outline(g, x + 4f, y + height / 2f - 3.5f, 5f, 7f, c(th.textMuted), 2.5f, 0.8f)
        }
    }
}
