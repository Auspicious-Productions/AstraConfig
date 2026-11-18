package xyz.essencexc.astra.ui.widgets

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.layout.Overlay
import xyz.essencexc.astra.ui.layout.Popup
import xyz.essencexc.astra.ui.layout.ScrollPane
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint

/**
 * Pick one of [labels]. Click (or Enter/Space) opens a menu in the overlay; while closed, Up/Down
 * change the value directly. In the menu: Up/Down/Home/End move, Enter/Space picks, Escape closes.
 */
class Dropdown(val binding: Binding<Int>, val labels: List<String>) : AstraWidget() {
    private var menu: Menu? = null
    private val chevron = Motion()
    internal var highlight = 0

    val isOpen: Boolean get() = menu?.parent != null

    init {
        fixedHeight = TextField.CONTROL_HEIGHT
    }

    override fun prefWidth(): Float =
        fixedWidth ?: ((labels.maxOfOrNull { Paint.width(it, theme.fontBody) } ?: 40f) + 32f).coerceIn(90f, 220f)

    fun open() {
        if (isOpen || labels.isEmpty()) return
        val overlay = Overlay.of(this) ?: return
        highlight = binding.get().coerceIn(0, labels.lastIndex)
        val m = Menu()
        menu = m
        overlay.open(m)
        m.revealHighlight()
    }

    fun close() {
        menu?.close()
        menu = null
    }

    internal fun pick(index: Int) {
        if (index in labels.indices && index != binding.get()) binding.set(index)
        close()
        focus()
    }

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
        if (button == 0) if (isOpen) close() else open()
        return true
    }

    override fun onKey(key: Int): Boolean {
        if (isOpen) {
            when {
                key == InputConstants.KEY_UP -> move(-1)
                key == InputConstants.KEY_DOWN -> move(1)
                key == InputConstants.KEY_HOME -> move(-labels.size)
                key == InputConstants.KEY_END -> move(labels.size)
                Keys.isActivate(key) -> pick(highlight)
                key == InputConstants.KEY_ESCAPE -> close()
                key == InputConstants.KEY_TAB -> { close(); return false }
                else -> return false
            }
            return true
        }
        when {
            Keys.isActivate(key) -> open()
            key == InputConstants.KEY_UP -> binding.set((binding.get() - 1).coerceAtLeast(0))
            key == InputConstants.KEY_DOWN -> binding.set((binding.get() + 1).coerceAtMost(labels.lastIndex))
            else -> return false
        }
        return true
    }

    private fun move(d: Int) {
        highlight = (highlight + d).coerceIn(0, labels.lastIndex)
        menu?.revealHighlight()
    }

    override fun onFocusChanged(focused: Boolean) {
        if (focused) return
        // Focus moving into our own menu (a click on an item) is fine; anywhere else closes it.
        val now = root.focused
        val m = menu ?: return
        var e = now
        while (e != null) {
            if (e === m) return
            e = e.parent
        }
        close()
    }

    override fun paint(g: GuiGraphicsExtractor) {
        val th = theme
        val r = th.radiusSmall
        val open = isOpen
        chevron.to(open)
        val h = hover.get()

        Paint.rect(g, x, y, width, height, c(Colors.mix(th.surfaceRaised, th.surfaceHover, h)), r)
        Paint.outline(g, x, y, width, height, c(if (open) accentColor() else Colors.mix(th.border, th.borderStrong, h)), r)
        focusRing(g, x, y, width, height, r)

        val label = labels.getOrNull(binding.get()) ?: "—"
        Paint.textMid(g, Paint.truncate(label, th.fontBody, width - 26f), x + 7f, y, height, c(th.text), th.fontBody)
        Paint.chevron(g, x + width - 10f, y + height / 2f, 5f, c(Colors.mix(th.textMuted, th.text, h)), chevron.get())
    }

    /** The open list. */
    private inner class Menu : Popup(this@Dropdown) {
        val pane = ScrollPane()
        private val itemH = 18f

        init {
            matchAnchorWidth = true
            padding = 3f
            pane.gutter = if (labels.size > MAX_VISIBLE) 6f else 0f
            pane.fixedHeight = minOf(labels.size, MAX_VISIBLE) * itemH
            pane.content.gap = 0f
            labels.indices.forEach { pane.content.adopt(Item(it)) }
            adopt(pane)
            onClosed = { if (menu === this) menu = null }
        }

        override fun prefWidth(): Float = (labels.maxOfOrNull { Paint.width(it, theme.fontBody) } ?: 40f) + 34f

        fun revealHighlight() {
            pane.content.children.getOrNull(highlight)?.let { pane.reveal(it, 0f) }
        }

        inner class Item(val index: Int) : AstraWidget() {
            init {
                focusable = false
                fixedHeight = itemH
            }

            override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean = true

            override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
                if (wasPressed && button == 0 && hit(mx, my)) pick(index)
                return true
            }

            override fun paint(g: GuiGraphicsExtractor) {
                val th = theme
                if (hovered) highlight = index
                val selected = index == binding.get()
                val hl = index == highlight
                if (hl) Paint.rect(g, x, y, width, height, th.surfaceHover, th.radiusSmall)
                val col = if (selected) accentColor() else if (hl) th.text else th.textMuted
                Paint.textMid(g, Paint.truncate(labels[index], th.fontBody, width - 26f), x + 7f, y, height, col, th.fontBody, selected)
                if (selected) Paint.check(g, x + width - 15f, y + (height - 10f) / 2f, 10f, accentColor(), 1f, 1.3f)
            }
        }
    }

    companion object {
        const val MAX_VISIBLE = 8
    }
}
