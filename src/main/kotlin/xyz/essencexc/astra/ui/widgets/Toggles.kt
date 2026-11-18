package xyz.essencexc.astra.ui.widgets

import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint

/** Base for on/off widgets: click, Space or Enter flips the value. */
abstract class ToggleWidget(val binding: Binding<Boolean>) : AstraWidget() {
    protected val on = Motion(if (binding.get()) 1f else 0f, speed = 18f)

    fun toggle() = binding.set(!binding.get())

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
        if (button == 0) toggle()
        return true
    }

    override fun onKey(key: Int): Boolean {
        if (!Keys.isActivate(key)) return false
        toggle()
        return true
    }

    override fun paint(g: GuiGraphicsExtractor) {
        on.to(binding.get()) // follows changes made from code too
        paintToggle(g, on.get())
    }

    protected abstract fun paintToggle(g: GuiGraphicsExtractor, t: Float)
}

/** A pill switch. Used for `@Switch` and the module master toggle. */
class Switch(binding: Binding<Boolean>) : ToggleWidget(binding) {
    init {
        fixedWidth = 28f
        fixedHeight = 15f
    }

    override fun paintToggle(g: GuiGraphicsExtractor, t: Float) {
        val th = theme
        val accent = accentColor()
        val r = height / 2f
        val h = hover.get()

        val track = Colors.mix(Colors.mix(th.surfaceRaised, th.surfaceHover, h), accent, t)
        Paint.rect(g, x, y, width, height, c(track), r)
        Paint.outline(g, x, y, width, height, c(Colors.mix(Colors.mix(th.border, th.borderStrong, h), accent, t)), r)
        focusRing(g, x, y, width, height, r)

        // Knob stretches a little while pressed, like a squish.
        val pad = 2.5f
        val kd = height - pad * 2
        val stretch = if (pressed) 3f else 0f
        val kw = kd + stretch
        val kx = x + pad + (width - pad * 2 - kw) * t
        val knob = Colors.mix(th.textMuted, th.onAccent, t)
        Paint.rect(g, kx, y + pad, kw, kd, c(Colors.mix(knob, th.text, h * (1f - t) * 0.5f)), kd / 2f)
    }
}

/** A tick box. Used for `@Checkbox`. */
class Checkbox(binding: Binding<Boolean>) : ToggleWidget(binding) {
    init {
        fixedWidth = 14f
        fixedHeight = 14f
    }

    override fun paintToggle(g: GuiGraphicsExtractor, t: Float) {
        val th = theme
        val accent = accentColor()
        val r = th.radiusSmall
        val h = hover.get()
        Paint.rect(g, x, y, width, height, c(Colors.mix(Colors.mix(th.surfaceRaised, th.surfaceHover, h), accent, t)), r)
        Paint.outline(g, x, y, width, height, c(Colors.mix(Colors.mix(th.borderStrong, th.textFaint, h), accent, t)), r)
        focusRing(g, x, y, width, height, r)
        Paint.check(g, x, y, width, c(th.onAccent), t)
    }
}

/** A push button. */
class Button(var label: String, var style: Style = Style.SECONDARY, var action: () -> Unit = {}) : AstraWidget() {
    /** Fire when the mouse button goes down instead of up (for buttons whose click rebuilds their parent). */
    var fireOnPress: Boolean = false

    enum class Style { PRIMARY, SECONDARY, GHOST, DANGER }

    private val press = Motion()

    init {
        fixedHeight = TextField.CONTROL_HEIGHT
    }

    override fun prefWidth(): Float = fixedWidth ?: (Paint.width(label, theme.fontBody, true) + 20f).coerceAtLeast(48f)

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
        if (fireOnPress && button == 0) fire()
        return true
    }

    override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
        if (!fireOnPress && wasPressed && button == 0 && hit(mx, my)) fire()
        return true
    }

    override fun onKey(key: Int): Boolean {
        if (!Keys.isActivate(key)) return false
        press.snap(1f).to(0f)
        fire()
        return true
    }

    private fun fire() {
        runCatching(action).onFailure { xyz.essencexc.astra.api.Astra.LOGGER.error("Button '{}' threw", label, it) }
    }

    override fun paint(g: GuiGraphicsExtractor) {
        val th = theme
        val accent = accentColor()
        val h = hover.get()
        if (pressed) press.to(1f) else if (press.target == 1f && !pressed) press.to(0f)
        val p = press.get()
        val r = th.radiusSmall

        val (fill, border, text) = when (style) {
            Style.PRIMARY -> Triple(Colors.mix(accent, Colors.mix(accent, th.text, 0.25f), h), 0, th.onAccent)
            Style.SECONDARY -> Triple(Colors.mix(th.surfaceRaised, th.surfaceHover, h), Colors.mix(th.border, th.borderStrong, h), th.text)
            Style.GHOST -> Triple(Colors.fade(th.surfaceHover, h), 0, Colors.mix(th.textMuted, th.text, h))
            Style.DANGER -> Triple(Colors.mix(Colors.fade(th.danger, 0.14f), Colors.fade(th.danger, 0.24f), h), Colors.fade(th.danger, 0.5f), th.danger)
        }

        // Pressed: shrink by a pixel and darken a touch.
        val inset = p * 1f
        val bx = x + inset; val by = y + inset; val bw = width - inset * 2; val bh = height - inset * 2
        Paint.rect(g, bx, by, bw, bh, c(Colors.mix(fill, 0xFF000000.toInt(), p * 0.15f)), r)
        if (border != 0) Paint.outline(g, bx, by, bw, bh, c(border), r)
        focusRing(g, x, y, width, height, r)
        Paint.textCentered(g, label, x + width / 2f, y, height, c(text), th.fontBody, true)
    }
}

/** Static text. [muted] draws it in the secondary colour; it wraps to the width it's given. */
class Label(var text: String, var muted: Boolean = false, var bold: Boolean = false, var textSize: (() -> Float)? = null) : xyz.essencexc.astra.ui.core.AstraElement() {
    var colour: (() -> Int)? = null

    private fun fontSize() = textSize?.invoke() ?: theme.fontBody

    override fun prefWidth(): Float = fixedWidth ?: Paint.width(text, fontSize(), bold)
    override fun prefHeight(width: Float): Float = fixedHeight ?: Paint.wrappedHeight(Paint.wrap(text, fontSize(), width + 0.5f, bold).size.coerceAtLeast(1), fontSize())

    override fun draw(graphics: GuiGraphicsExtractor) {
        val col = colour?.invoke() ?: if (muted) theme.textMuted else theme.text
        val lines = Paint.wrap(text, fontSize(), width + 0.5f, bold)
        var yy = y
        for (l in lines) {
            Paint.text(graphics, l, x, yy, col, fontSize(), bold)
            yy += Paint.lineHeight(fontSize()) + 2f
        }
    }
}
