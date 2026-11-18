package xyz.essencexc.astra.ui.widgets

import com.mojang.blaze3d.platform.InputConstants
import foo.starred.snowbird.api.client
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.layout.Align
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Paint

/**
 * X / Y / Z boxes and a "Here" button that fills in the player's position. [integer] rounds to
 * whole blocks. Up/Down in a box nudge it by 1 (Shift: 10).
 */
class PositionInput(val binding: Binding<DoubleArray>, val integer: Boolean) : Row() {
    private val numbers = NumberScale(-30_000_000.0, 30_000_000.0, if (integer) 1.0 else 0.0, integer, 1.0)
    private val boxes = Array(3) { axis -> box(axis) }

    init {
        gap = 3f
        align = Align.CENTER
        for ((axis, box) in boxes.withIndex()) {
            adopt(AxisLabel("XYZ"[axis].toString()))
            adopt(box)
        }
        adopt(HereButton())
    }

    override fun prefWidth() = fixedWidth ?: 230f

    private fun box(axis: Int) = TextField().apply {
        grow = true
        centered = true
        selectAllOnFocus = true
        filter = TextField.NUMERIC
        maxLength = 12
        onCommit = { t -> numbers.parse(t)?.let { set(axis, it) } }
        onKeyHook = { key ->
            val dir = when (key) {
                InputConstants.KEY_UP -> 1
                InputConstants.KEY_DOWN -> -1
                else -> 0
            }
            if (dir != 0) {
                set(axis, numbers.nudge(numbers.parse(text) ?: binding.get()[axis], dir))
                text = numbers.plain(binding.get()[axis])
                selectAll()
                true
            } else false
        }
    }

    private fun set(axis: Int, value: Double) {
        val v = binding.get().copyOf()
        v[axis] = if (integer) Math.floor(value) else value
        binding.set(v)
    }

    override fun draw(graphics: GuiGraphicsExtractor) {
        val v = binding.get()
        for (i in 0..2) if (!boxes[i].isFocused) boxes[i].text = numbers.plain(if (integer) Math.floor(v[i]) else Math.round(v[i] * 100.0) / 100.0)
        super.draw(graphics)
    }

    private class AxisLabel(val text: String) : AstraElement() {
        init {
            fixedWidth = 8f
            fixedHeight = TextField.CONTROL_HEIGHT
        }

        override fun draw(graphics: GuiGraphicsExtractor) =
            Paint.textCentered(graphics, text, x + width / 2f, y, height, theme.textMuted, theme.fontSmall, true)
    }

    /** Fills in where the player stands (block position for whole-block fields). */
    private inner class HereButton : AstraWidget() {
        private var hoverSince = 0L

        init {
            fixedWidth = TextField.CONTROL_HEIGHT
            fixedHeight = TextField.CONTROL_HEIGHT
            enabledWhen = { client.player != null }
        }

        private fun fill() {
            val p = client.player ?: return
            binding.set(if (integer) p.blockPosition().let { doubleArrayOf(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) } else p.position().let { doubleArrayOf(it.x, it.y, it.z) })
        }

        override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
            if (wasPressed && button == 0 && hit(mx, my)) fill()
            return true
        }

        override fun onKey(key: Int): Boolean {
            if (!Keys.isActivate(key)) return false
            fill()
            return true
        }

        override fun paint(g: GuiGraphicsExtractor) {
            val th = theme
            val h = hover.get()
            Paint.rect(g, x, y, width, height, c(Colors.mix(th.surfaceRaised, th.surfaceHover, h)), th.radiusSmall)
            Paint.outline(g, x, y, width, height, c(Colors.mix(th.border, th.borderStrong, h)), th.radiusSmall)
            focusRing(g, x, y, width, height, th.radiusSmall)
            // Crosshair: "use my position".
            val cx = x + width / 2f
            val cy = y + height / 2f
            val col = c(Colors.mix(th.textMuted, accentColor(), h))
            Paint.circle(g, cx, cy, 3.6f, col)
            Paint.circle(g, cx, cy, 2.5f, c(Colors.mix(th.surfaceRaised, th.surfaceHover, h)))
            Paint.circle(g, cx, cy, 1f, col)
            Paint.line(g, cx - 6f, cy, cx - 4.2f, cy, col, 1f)
            Paint.line(g, cx + 4.2f, cy, cx + 6f, cy, col, 1f)
            Paint.line(g, cx, cy - 6f, cx, cy - 4.2f, col, 1f)
            Paint.line(g, cx, cy + 4.2f, cx, cy + 6f, col, 1f)

            if (!hovered) hoverSince = 0L else if (hoverSince == 0L) hoverSince = System.currentTimeMillis()
            if (hoverSince != 0L && System.currentTimeMillis() - hoverSince > 400) {
                xyz.essencexc.astra.ui.layout.Overlay.of(this)?.tooltip(if (isEnabled) "Use my position" else "Join a world first", mouseX, mouseY)
            }
        }
    }
}
