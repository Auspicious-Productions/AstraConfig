package xyz.essencexc.astra.ui.core

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.cursor.CursorTypes
import foo.starred.cascade.events.impl.FocusEvent
import foo.starred.cascade.events.impl.KeyEvent
import foo.starred.cascade.events.impl.MouseEvent
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.layout.Overlay
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint

/**
 * An interactive element: takes focus, mouse and keyboard input, and can be disabled.
 *
 * Subclasses override the `on*` hooks and return true when they handled the input (which stops
 * Minecraft from also acting on it, e.g. Escape closing the screen).
 */
abstract class AstraWidget : AstraElement() {
    /** Turned off by code. */
    var enabled: Boolean = true

    /** Extra live condition, e.g. a `@DependsOn` switch. */
    var enabledWhen: (() -> Boolean)? = null

    /** Whether Tab stops here. */
    var focusable: Boolean = true

    val isEnabled: Boolean
        get() = enabled && (enabledWhen?.invoke() ?: true)

    val isFocused: Boolean
        get() = root.focused === this

    /** True between a mouse press on this widget and the release (wherever it happens). */
    var pressed: Boolean = false
        private set

    protected val hover = Motion()
    protected val focusGlow = Motion(speed = 20f)

    init {
        interact = true
        cursor = CursorTypes.POINTING_HAND

        on<MouseEvent.Press> {
            val w = this@AstraWidget
            if (!w.isEnabled) {
                cancel()
                return@on
            }
            FocusManager.keyboard = false
            w.pressed = true
            if (w.onMouseDown(x, y, Keys.button(button))) cancel()
        }

        on<MouseEvent.Release> {
            val w = this@AstraWidget
            val was = w.pressed
            w.pressed = false
            if (w.onMouseUp(x, y, Keys.button(button), was)) cancel()
        }

        on<MouseEvent.Scroll> {
            val w = this@AstraWidget
            if (w.isEnabled && w.onScroll(amount)) cancel()
        }

        on<KeyEvent.Press> {
            val w = this@AstraWidget
            if (w.isEnabled && w.onKey(key)) {
                cancel()
                return@on
            }
            if (key == InputConstants.KEY_TAB) {
                FocusManager.move(w, Keys.shift())
                cancel()
            } else if (key == InputConstants.KEY_ESCAPE) {
                val overlay = Overlay.of(w)
                if (overlay != null && overlay.isOpen) {
                    overlay.closeAll()
                    cancel()
                }
            }
        }

        on<KeyEvent.Release> {
            val w = this@AstraWidget
            if (w.isEnabled && w.onKeyUp(key)) cancel()
        }

        on<KeyEvent.Type> {
            val w = this@AstraWidget
            if (w.isEnabled && w.onChar(char)) cancel()
        }

        on<FocusEvent.Gain> { this@AstraWidget.onFocusChanged(true) }
        on<FocusEvent.Lose> {
            val w = this@AstraWidget
            w.pressed = false
            w.onFocusChanged(false)
        }
    }

    protected open fun onMouseDown(mx: Double, my: Double, button: Int): Boolean = true
    protected open fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean = false
    protected open fun onScroll(amount: Double): Boolean = false
    protected open fun onKey(key: Int): Boolean = false
    protected open fun onKeyUp(key: Int): Boolean = false
    protected open fun onChar(c: Char): Boolean = false
    protected open fun onFocusChanged(focused: Boolean) {}

    /** Gives this widget keyboard focus. */
    fun focus() {
        root.focused = this
    }

    /** Hands focus back to the screen. */
    fun blur() {
        if (isFocused) root.focused = FocusManager.fallback(root)
    }

    final override fun draw(graphics: GuiGraphicsExtractor) {
        hover.to(hovered && isEnabled)
        focusGlow.to(isFocused && FocusManager.keyboard)
        cursor = if (isEnabled) cursorType() else CursorTypes.NOT_ALLOWED
        paint(graphics)
    }

    protected open fun cursorType() = CursorTypes.POINTING_HAND

    protected abstract fun paint(g: GuiGraphicsExtractor)

    /** [color] faded when the widget is disabled. */
    protected fun c(color: Int): Int = if (isEnabled) color else Colors.fade(color, 0.4f)

    /** Draws the keyboard focus ring around the given box. */
    protected fun focusRing(g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, r: Float) {
        val t = focusGlow.get()
        if (t <= 0.01f) return
        Paint.outline(g, x - 2f, y - 2f, w + 4f, h + 4f, Colors.fade(accentColor(), 0.7f * t), r + 2f, 1.2f)
    }
}
