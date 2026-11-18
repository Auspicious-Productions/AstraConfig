package xyz.essencexc.astra.ui.widgets

import com.mojang.blaze3d.platform.InputConstants
import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint

/**
 * A number box with − and + buttons. Type a value, use Up/Down (Shift = ×10) or the scroll wheel
 * while it's focused, or click the buttons. [unit] is shown after the number.
 */
class NumberField(val binding: Binding<Double>, val numbers: NumberScale, unit: String = "") : AstraWidget() {
    val field = TextField().apply {
        chrome = false
        centered = true
        selectAllOnFocus = true
        filter = TextField.NUMERIC
        suffix = if (unit.isEmpty()) "" else " $unit"
        onCommit = { t -> numbers.parse(t)?.let { binding.set(it) } }
        onKeyHook = { key ->
            val dir = when (key) {
                InputConstants.KEY_UP -> 1
                InputConstants.KEY_DOWN -> -1
                else -> 0
            }
            if (dir != 0) {
                step(dir, numbers.parse(text))
                text = numbers.plain(binding.get())
                selectAll()
                true
            } else false
        }
    }

    private val btn = 16f
    private val minusHover = Motion()
    private val plusHover = Motion()
    private val minusPress = Motion()
    private val plusPress = Motion()

    init {
        focusable = false
        fixedHeight = TextField.CONTROL_HEIGHT
        adopt(field)
    }

    override fun prefWidth(): Float = fixedWidth ?: 100f

    private fun step(dir: Int, from: Double? = null) {
        binding.set(numbers.nudge(from ?: binding.get(), dir))
    }

    override fun layout() {
        dirty = false
        field.place(x + btn, y, width - btn * 2, height)
    }

    override fun find(x: Double, y: Double): IPrimitiveElement<*>? {
        if (!visible || !hit(x, y)) return null
        return field.find(x, y) ?: this
    }

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
        if (button != 0) return true
        when {
            mx < x + btn -> { step(-1); minusPress.snap(1f).to(0f) }
            mx >= x + width - btn -> { step(1); plusPress.snap(1f).to(0f) }
        }
        return true
    }

    override fun onScroll(amount: Double): Boolean {
        if (!field.isFocused) return false
        step(if (amount > 0) 1 else -1)
        field.text = numbers.plain(binding.get())
        return true
    }

    override fun paint(g: GuiGraphicsExtractor) {
        val th = theme
        val v = binding.get()
        if (!field.isFocused) field.text = numbers.plain(v)

        val r = th.radiusSmall
        val focused = field.isFocused
        Paint.rect(g, x, y, width, height, c(th.surfaceRaised), r)
        Paint.outline(g, x, y, width, height, c(if (focused) accentColor() else Colors.mix(th.border, th.borderStrong, hover.get())), r)

        val mx = mouseX
        val overMinus = hovered && mx < x + btn
        val overPlus = hovered && mx >= x + width - btn
        minusHover.to(overMinus); plusHover.to(overPlus)

        val canDown = v > numbers.min
        val canUp = v < numbers.max
        drawButton(g, x, minusHover.get(), minusPress.get(), canDown, false)
        drawButton(g, x + width - btn, plusHover.get(), plusPress.get(), canUp, true)
    }

    private fun drawButton(g: GuiGraphicsExtractor, bx: Float, hov: Float, press: Float, active: Boolean, plus: Boolean) {
        val th = theme
        if (hov > 0.01f && active) Paint.rect(g, bx + 2f, y + 2f, btn - 4f, height - 4f, c(Colors.fade(th.surfaceHover, hov)), th.radiusSmall - 1f)
        val col = c(if (!active) th.textFaint else Colors.mix(th.textMuted, Colors.mix(th.text, accentColor(), press), hov.coerceAtLeast(press)))
        val cx = bx + btn / 2f
        val cy = y + height / 2f
        Paint.line(g, cx - 3f, cy, cx + 3f, cy, col, 1.3f)
        if (plus) Paint.line(g, cx, cy - 3f, cx, cy + 3f, col, 1.3f)
    }
}
