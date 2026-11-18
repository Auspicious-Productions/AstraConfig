package xyz.essencexc.astra.ui.widgets

import com.mojang.blaze3d.platform.InputConstants
import foo.starred.cascade.graphics.geometry.CascadeGeometricColor
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.layout.Align
import xyz.essencexc.astra.ui.layout.Overlay
import xyz.essencexc.astra.ui.layout.Popup
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.theme.Theme
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Paint

/**
 * A colour swatch with its hex code. Click (or Enter/Space) opens a picker: saturation/value
 * square, hue bar, optional alpha bar, a hex box and preset swatches.
 */
class ColorPicker(val binding: Binding<Int>, val alpha: Boolean = true) : AstraWidget() {
    private var popup: PickerPopup? = null

    init {
        fixedHeight = TextField.CONTROL_HEIGHT
    }

    override fun prefWidth(): Float = fixedWidth ?: if (alpha) 96f else 82f

    val isOpen: Boolean get() = popup?.parent != null

    fun open() {
        if (isOpen) return
        val overlay = Overlay.of(this) ?: return
        val p = PickerPopup()
        popup = p
        overlay.open(p)
    }

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
        if (button == 0) if (isOpen) popup?.close() else open()
        return true
    }

    override fun onKey(key: Int): Boolean {
        if (!Keys.isActivate(key)) return false
        open()
        popup?.area?.focus()
        return true
    }

    override fun paint(g: GuiGraphicsExtractor) {
        val th = theme
        val r = th.radiusSmall
        val h = hover.get()
        val value = binding.get()
        Paint.rect(g, x, y, width, height, c(Colors.mix(th.surfaceRaised, th.surfaceHover, h)), r)
        Paint.outline(g, x, y, width, height, c(if (isOpen) accentColor() else Colors.mix(th.border, th.borderStrong, h)), r)
        focusRing(g, x, y, width, height, r)

        val sw = 12f
        val sx = x + 4f
        val sy = y + (height - sw) / 2f
        Paint.clip(g, sx, sy, sw, sw) { Paint.checker(g, sx, sy, sw, sw, 3f) }
        Paint.rect(g, sx, sy, sw, sw, c(value), 2f)
        Paint.outline(g, sx, sy, sw, sw, c(Colors.fade(th.text, 0.25f)), 2f)
        Paint.textMid(g, Colors.hex(value, alpha), sx + sw + 5f, y, height, c(th.text), th.fontBody - 0.5f)
    }

    private inner class PickerPopup : Popup(this@ColorPicker) {
        var hue: Float
        var sat: Float
        var bright: Float
        var a: Float
        val area = Area()
        val hex = TextField()

        init {
            alignEnd = true
            padding = 8f
            gap = 8f
            val c = binding.get()
            val hsv = Colors.toHsv(c)
            hue = hsv[0]; sat = hsv[1]; bright = hsv[2]; a = Colors.alpha(c) / 255f
            adopt(area)

            hex.filter = { it.isLetterOrDigit() || it == '#' }
            hex.maxLength = 9
            hex.grow = true
            hex.onCommit = { t -> Colors.parseHex(t)?.let { setColor(if (alpha) it else it or 0xFF000000.toInt(), true) } }
            adopt(Row().apply {
                gap = 6f
                align = Align.CENTER
                adopt(hex)
                adopt(Preview())
            })

            adopt(Row().apply {
                gap = 4f
                for (preset in Theme.ACCENTS) adopt(Preset(preset))
            })
            onClosed = { if (popup === this) popup = null }
        }

        fun current(): Int = Colors.hsv(hue, sat, bright, if (alpha) a else 1f)

        fun push() {
            val v = current()
            if (v != binding.get()) binding.set(v)
        }

        fun setColor(c: Int, updateHsv: Boolean) {
            if (updateHsv) {
                val hsv = Colors.toHsv(c)
                if (hsv[1] > 0.001f && hsv[2] > 0.001f) hue = hsv[0]
                sat = hsv[1]; bright = hsv[2]; a = Colors.alpha(c) / 255f
            }
            binding.set(c)
        }

        override fun draw(graphics: GuiGraphicsExtractor) {
            super.draw(graphics)
            if (!hex.isFocused) hex.text = Colors.hex(binding.get(), alpha)
        }

        /** SV square + hue bar (+ alpha bar). */
        inner class Area : AstraWidget() {
            private var region = 0 // 1 = SV, 2 = hue, 3 = alpha

            init {
                fixedWidth = SQUARE + GAP + BAR + if (alpha) GAP + BAR else 0f
                fixedHeight = SQUARE
            }

            private val barX get() = x + SQUARE + GAP
            private val alphaX get() = barX + BAR + GAP

            override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
                region = when {
                    mx < x + SQUARE -> 1
                    mx >= barX && mx < barX + BAR -> 2
                    alpha && mx >= alphaX -> 3
                    else -> 0
                }
                drag(mx.toFloat(), my.toFloat())
                return true
            }

            override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
                region = 0
                return true
            }

            private fun drag(mx: Float, my: Float) {
                val fy = ((my - y) / SQUARE).coerceIn(0f, 1f)
                when (region) {
                    1 -> { sat = ((mx - x) / SQUARE).coerceIn(0f, 1f); bright = 1f - fy }
                    2 -> hue = fy.coerceAtMost(0.9999f)
                    3 -> a = 1f - fy
                    else -> return
                }
                push()
            }

            override fun onKey(key: Int): Boolean {
                val s = if (Keys.shift()) 0.1f else 0.02f
                when (key) {
                    InputConstants.KEY_LEFT -> sat = (sat - s).coerceIn(0f, 1f)
                    InputConstants.KEY_RIGHT -> sat = (sat + s).coerceIn(0f, 1f)
                    InputConstants.KEY_UP -> bright = (bright + s).coerceIn(0f, 1f)
                    InputConstants.KEY_DOWN -> bright = (bright - s).coerceIn(0f, 1f)
                    InputConstants.KEY_PAGEUP -> hue = (hue - s + 1f) % 1f
                    InputConstants.KEY_PAGEDOWN -> hue = (hue + s) % 1f
                    else -> return false
                }
                push()
                return true
            }

            override fun cursorType() = com.mojang.blaze3d.platform.cursor.CursorTypes.CROSSHAIR

            override fun paint(g: GuiGraphicsExtractor) {
                if (pressed && region != 0) drag(mouseX, mouseY) else region = 0
                val th = theme
                val pure = Colors.hsv(hue, 1f, 1f)

                // SV square: white -> hue horizontally, then transparent -> black vertically.
                Paint.gradient(g, x, y, SQUARE, SQUARE, CascadeGeometricColor.horizontal(-1, pure), 3f)
                Paint.gradient(g, x, y, SQUARE, SQUARE, CascadeGeometricColor.vertical(0x00000000, 0xFF000000.toInt()), 3f)
                val px = x + sat * SQUARE
                val py = y + (1f - bright) * SQUARE
                Paint.circle(g, px, py, 4.5f, 0xFFFFFFFF.toInt())
                Paint.circle(g, px, py, 3.2f, Colors.hsv(hue, sat, bright))

                // Hue bar: six vertical gradient segments.
                val seg = SQUARE / 6f
                for (i in 0 until 6) {
                    val top = Colors.hsv(i / 6f, 1f, 1f)
                    val bottom = Colors.hsv(((i + 1) / 6f) % 1f, 1f, 1f)
                    Paint.gradient(g, barX, y + seg * i, BAR, seg + 0.5f, CascadeGeometricColor.vertical(top, bottom), 0f)
                }
                Paint.outline(g, barX, y, BAR, SQUARE, Colors.fade(th.text, 0.15f), 2f)
                marker(g, barX, y + hue * SQUARE)

                if (alpha) {
                    Paint.clip(g, alphaX, y, BAR, SQUARE) { Paint.checker(g, alphaX, y, BAR, SQUARE, 3f) }
                    val solid = Colors.hsv(hue, sat, bright)
                    Paint.gradient(g, alphaX, y, BAR, SQUARE, CascadeGeometricColor.vertical(solid, solid and 0x00FFFFFF), 0f)
                    Paint.outline(g, alphaX, y, BAR, SQUARE, Colors.fade(th.text, 0.15f), 2f)
                    marker(g, alphaX, y + (1f - a) * SQUARE)
                }
                focusRing(g, x, y, SQUARE, SQUARE, 3f)
            }

            private fun marker(g: GuiGraphicsExtractor, bx: Float, my: Float) {
                Paint.rect(g, bx - 1.5f, my - 2f, BAR + 3f, 4f, 0xFFFFFFFF.toInt(), 1.5f)
                Paint.outline(g, bx - 1.5f, my - 2f, BAR + 3f, 4f, 0x66000000, 1.5f)
            }
        }

        inner class Preview : AstraWidget() {
            init {
                focusable = false
                fixedWidth = 26f
                fixedHeight = TextField.CONTROL_HEIGHT
                interact = false
            }

            override fun paint(g: GuiGraphicsExtractor) {
                Paint.clip(g, x, y, width, height) { Paint.checker(g, x, y, width, height, 3f) }
                Paint.rect(g, x, y, width, height, binding.get(), theme.radiusSmall)
                Paint.outline(g, x, y, width, height, Colors.fade(theme.text, 0.2f), theme.radiusSmall)
            }
        }

        inner class Preset(val colour: Int) : AstraWidget() {
            init {
                focusable = false
                fixedWidth = 14f
                fixedHeight = 14f
            }

            override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
                setColor(if (alpha) Colors.withAlpha(colour, a) else colour, true)
                return true
            }

            override fun paint(g: GuiGraphicsExtractor) {
                val h = hover.get()
                val grow = 1.5f * h
                Paint.rect(g, x - grow, y - grow, width + grow * 2, height + grow * 2, colour, 4f)
                if ((binding.get() and 0xFFFFFF) == (colour and 0xFFFFFF)) Paint.outline(g, x - 2f, y - 2f, width + 4f, height + 4f, theme.text, 5f, 1.2f)
            }
        }
    }

    private companion object {
        const val SQUARE = 110f
        const val BAR = 10f
        const val GAP = 8f
    }
}
