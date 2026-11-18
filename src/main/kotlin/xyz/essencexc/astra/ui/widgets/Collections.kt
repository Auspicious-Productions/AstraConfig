package xyz.essencexc.astra.ui.widgets

import com.mojang.blaze3d.platform.InputConstants
import foo.starred.cascade.graphics.extensions.arc.arc
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.layout.Align
import xyz.essencexc.astra.ui.layout.Column
import xyz.essencexc.astra.ui.layout.Overlay
import xyz.essencexc.astra.ui.layout.Popup
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.layout.ScrollPane
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint

/**
 * Pick any number of [labels]. Shows a summary ("Zombie, Skeleton +2"); click or Enter opens a
 * checklist. In the list: Up/Down move, Space/Enter toggle, A toggles all, Escape closes.
 */
class MultiSelect(val binding: Binding<Set<Int>>, val labels: List<String>) : AstraWidget() {
    private var menu: Menu? = null
    private val chevron = Motion()
    internal var highlight = 0

    val isOpen: Boolean get() = menu?.parent != null

    init {
        fixedHeight = TextField.CONTROL_HEIGHT
    }

    override fun prefWidth(): Float = fixedWidth ?: 160f

    private fun summary(): String {
        val sel = binding.get()
        return when {
            sel.isEmpty() -> "None"
            sel.size == labels.size -> "All"
            else -> {
                val names = labels.indices.filter { it in sel }.map { labels[it] }
                val first = names.take(2).joinToString(", ")
                if (names.size > 2) "$first +${names.size - 2}" else first
            }
        }
    }

    fun toggle(i: Int) {
        val s = binding.get().toMutableSet()
        if (!s.add(i)) s.remove(i)
        binding.set(s)
    }

    fun toggleAll() = binding.set(if (binding.get().size == labels.size) emptySet() else labels.indices.toSet())

    fun open() {
        if (isOpen || labels.isEmpty()) return
        val overlay = Overlay.of(this) ?: return
        val m = Menu()
        menu = m
        overlay.open(m)
    }

    fun close() {
        menu?.close()
        menu = null
    }

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
        if (button == 0) if (isOpen) close() else open()
        return true
    }

    override fun onKey(key: Int): Boolean {
        if (!isOpen) {
            if (!Keys.isActivate(key)) return false
            open()
            return true
        }
        when {
            key == InputConstants.KEY_UP -> highlight = (highlight - 1).coerceAtLeast(0)
            key == InputConstants.KEY_DOWN -> highlight = (highlight + 1).coerceAtMost(labels.lastIndex)
            Keys.isActivate(key) -> toggle(highlight)
            key == InputConstants.KEY_A -> toggleAll()
            key == InputConstants.KEY_ESCAPE -> close()
            key == InputConstants.KEY_TAB -> { close(); return false }
            else -> return false
        }
        menu?.reveal()
        return true
    }

    override fun onFocusChanged(focused: Boolean) {
        if (focused) return
        val m = menu ?: return
        var e = root.focused
        while (e != null) {
            if (e === m) return
            e = e.parent
        }
        close()
    }

    override fun paint(g: GuiGraphicsExtractor) {
        val th = theme
        val r = th.radiusSmall
        val h = hover.get()
        chevron.to(isOpen)
        Paint.rect(g, x, y, width, height, c(Colors.mix(th.surfaceRaised, th.surfaceHover, h)), r)
        Paint.outline(g, x, y, width, height, c(if (isOpen) accentColor() else Colors.mix(th.border, th.borderStrong, h)), r)
        focusRing(g, x, y, width, height, r)
        val count = binding.get().size
        val badge = if (count in 1 until labels.size) "$count" else ""
        val bw = if (badge.isEmpty()) 0f else Paint.width(badge, th.fontSmall, true) + 8f
        Paint.textMid(g, Paint.truncate(summary(), th.fontBody, width - 26f - bw), x + 7f, y, height, c(if (count == 0) th.textFaint else th.text), th.fontBody)
        if (bw > 0f) {
            val bx = x + width - 18f - bw
            Paint.rect(g, bx, y + 4f, bw - 2f, height - 8f, c(Colors.fade(accentColor(), 0.2f)), (height - 8f) / 2f)
            Paint.textCentered(g, badge, bx + (bw - 2f) / 2f, y + 4f, height - 8f, c(accentColor()), th.fontSmall, true)
        }
        Paint.chevron(g, x + width - 10f, y + height / 2f, 5f, c(Colors.mix(th.textMuted, th.text, h)), chevron.get())
    }

    private inner class Menu : Popup(this@MultiSelect) {
        val pane = ScrollPane()
        private val itemH = 18f

        init {
            matchAnchorWidth = true
            padding = 3f
            gap = 2f
            pane.gutter = if (labels.size > Dropdown.MAX_VISIBLE) 6f else 0f
            pane.fixedHeight = minOf(labels.size, Dropdown.MAX_VISIBLE) * itemH
            labels.indices.forEach { pane.content.adopt(Item(it)) }
            adopt(pane)
            adopt(Row().apply {
                gap = 3f
                paddingX = 3f
                adopt(Button("All", Button.Style.GHOST) { binding.set(labels.indices.toSet()) }.apply { grow = true })
                adopt(Button("None", Button.Style.GHOST) { binding.set(emptySet()) }.apply { grow = true })
            })
            onClosed = { if (menu === this) menu = null }
        }

        override fun prefWidth(): Float = (labels.maxOfOrNull { Paint.width(it, theme.fontBody) } ?: 40f) + 40f

        fun reveal() {
            pane.content.children.getOrNull(highlight)?.let { pane.reveal(it, 0f) }
        }

        inner class Item(val index: Int) : AstraWidget() {
            init {
                focusable = false
                fixedHeight = itemH
            }

            override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
                toggle(index)
                highlight = index
                return true
            }

            override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
                this@MultiSelect.focus() // keep keyboard control on the field
                return true
            }

            override fun paint(g: GuiGraphicsExtractor) {
                val th = theme
                if (hovered) highlight = index
                val on = index in binding.get()
                if (index == highlight) Paint.rect(g, x, y, width, height, th.surfaceHover, th.radiusSmall)
                val bx = x + 6f
                val by = y + (height - 10f) / 2f
                val accent = accentColor()
                Paint.rect(g, bx, by, 10f, 10f, if (on) accent else th.surfaceRaised, 2.5f)
                Paint.outline(g, bx, by, 10f, 10f, if (on) accent else th.borderStrong, 2.5f)
                if (on) Paint.check(g, bx, by, 10f, th.onAccent, 1f, 1.3f)
                Paint.textMid(g, Paint.truncate(labels[index], th.fontBody, width - 26f), x + 22f, y, height, if (on) th.text else th.textMuted, th.fontBody)
            }
        }
    }
}

/**
 * An editable list of strings: edit entries in place, move them up, remove them, or type into the
 * last box and press Enter (or Add) to append.
 */
class ItemListEditor(
    val binding: Binding<List<String>>,
    val placeholder: String = "",
    val maxItems: Int = 100,
    val maxLength: Int = 128,
) : Column() {
    private var shown: List<String> = emptyList()
    private val adder = TextField().apply {
        grow = true
        this.placeholder = this@ItemListEditor.placeholder.ifEmpty { "Add entry..." }
        maxLength = this@ItemListEditor.maxLength
        onKeyHook = { key ->
            if (Keys.isEnter(key)) {
                addEntry()
                true
            } else false
        }
    }

    init {
        gap = 4f
        rebuild()
    }

    override fun prefWidth() = fixedWidth ?: 220f

    private fun addEntry() {
        val t = adder.text.trim()
        if (binding.get().size >= maxItems) return
        if (t.isEmpty()) {
            // Nothing typed yet: put the cursor in the box (and flash it) so it's clear where to type.
            adder.focus()
            adder.flash()
            return
        }
        binding.set(binding.get() + t)
        adder.text = ""
        rebuild()
        adder.focus()
    }

    private fun rebuild() {
        shown = binding.get()
        clear()
        for ((i, value) in shown.withIndex()) {
            adopt(Row().apply {
                gap = 3f
                align = Align.CENTER
                adopt(TextField(value).apply {
                    grow = true
                    maxLength = this@ItemListEditor.maxLength
                    onCommit = { t ->
                        val list = binding.get().toMutableList()
                        if (i < list.size && list[i] != t) {
                            if (t.isBlank()) list.removeAt(i) else list[i] = t
                            binding.set(list)
                        }
                    }
                })
                adopt(IconButton(IconButton.Glyph.UP, enabledIf = { i > 0 }) {
                    val list = binding.get().toMutableList()
                    if (i in 1 until list.size) {
                        list.add(i - 1, list.removeAt(i))
                        binding.set(list)
                        rebuild()
                    }
                })
                adopt(IconButton(IconButton.Glyph.REMOVE) {
                    val list = binding.get().toMutableList()
                    if (i < list.size) {
                        list.removeAt(i)
                        binding.set(list)
                        rebuild()
                    }
                })
            })
        }
        adopt(Row().apply {
            gap = 3f
            align = Align.CENTER
            adopt(adder)
            // this@ItemListEditor: inside Row().apply, a bare add() would call Box.add(vararg) on the row.
            adopt(Button("Add", Button.Style.SECONDARY) { this@ItemListEditor.addEntry() }.apply { enabledWhen = { binding.get().size < maxItems } })
        })
        root.dirty()
    }

    override fun draw(graphics: GuiGraphicsExtractor) {
        // Changed from outside (reset, profile load...)? Rebuild, unless the player is typing in it.
        if (binding.get() != shown) {
            var typing = false
            iterateChildren { if (it is TextField && it.isFocused) typing = true }
            if (!typing) rebuild()
        }
        super.draw(graphics)
    }
}

/** A small square button with a drawn glyph. */
class IconButton(val glyph: Glyph, private val enabledIf: () -> Boolean = { true }, val action: () -> Unit) : AstraWidget() {
    enum class Glyph { UP, REMOVE, RESET }

    /** Draw nothing (and take no clicks or focus) while disabled, instead of greying out. */
    var hideWhenDisabled: Boolean = false

    /** Shown on hover after a short delay. */
    var tooltip: String? = null
    private var hoverSince = 0L

    init {
        fixedWidth = TextField.CONTROL_HEIGHT
        fixedHeight = TextField.CONTROL_HEIGHT
        enabledWhen = enabledIf
    }

    override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
        if (wasPressed && button == 0 && hit(mx, my)) action()
        return true
    }

    override fun onKey(key: Int): Boolean {
        if (!Keys.isActivate(key)) return false
        action()
        return true
    }

    override fun paint(g: GuiGraphicsExtractor) {
        val th = theme
        val h = hover.get()
        interact = isEnabled || !hideWhenDisabled
        if (hideWhenDisabled && !isEnabled) return
        tooltip?.let { t ->
            if (!hovered) hoverSince = 0L else if (hoverSince == 0L) hoverSince = System.currentTimeMillis()
            if (hoverSince != 0L && System.currentTimeMillis() - hoverSince > 400) xyz.essencexc.astra.ui.layout.Overlay.of(this)?.tooltip(t, mouseX, mouseY)
        }
        val danger = glyph == Glyph.REMOVE
        if (h > 0.01f) Paint.rect(g, x, y, width, height, c(Colors.fade(if (danger) th.danger else th.surfaceHover, if (danger) 0.15f * h else h)), th.radiusSmall)
        focusRing(g, x, y, width, height, th.radiusSmall)
        val col = c(Colors.mix(th.textMuted, if (danger) th.danger else th.text, h))
        val cx = x + width / 2f
        val cy = y + height / 2f
        when (glyph) {
            Glyph.UP -> Paint.chevron(g, cx, cy, 6f, col, 1f)
            Glyph.RESET -> {
                // Counter-clockwise arrow: an open ring with a head at the top.
                g.arc(cx, cy, 2.6f, 3.8f, 40f, 330f, true, foo.starred.cascade.graphics.geometry.CascadeGeometricColor(col))
                Paint.line(g, cx + 1.2f, cy - 3.6f, cx - 1.2f, cy - 5f, col, 1.1f)
                Paint.line(g, cx + 1.2f, cy - 3.6f, cx - 0.4f, cy - 1.6f, col, 1.1f)
            }
            Glyph.REMOVE -> {
                Paint.line(g, cx - 3f, cy - 3f, cx + 3f, cy + 3f, col, 1.2f)
                Paint.line(g, cx + 3f, cy - 3f, cx - 3f, cy + 3f, col, 1.2f)
            }
        }
    }
}
