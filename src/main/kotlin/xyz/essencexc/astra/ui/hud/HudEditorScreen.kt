package xyz.essencexc.astra.ui.hud

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.cursor.CursorType
import com.mojang.blaze3d.platform.cursor.CursorTypes
import foo.starred.cascade.constraints.base.IPositionConstraint
import foo.starred.cascade.constraints.impl.position.FixedPositionConstraint
import foo.starred.cascade.constraints.impl.size.FillSizeConstraint
import foo.starred.cascade.graphics.geometry.CascadeGeometricResolution
import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import foo.starred.snowbird.api.client
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import xyz.essencexc.astra.api.AstraMod
import xyz.essencexc.astra.api.hud.HudElement
import xyz.essencexc.astra.api.hud.HudLayout
import xyz.essencexc.astra.core.HudRegistry
import xyz.essencexc.astra.ui.core.AstraScreen
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.layout.Column
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Paint
import xyz.essencexc.astra.ui.widgets.Binding
import xyz.essencexc.astra.ui.widgets.Button
import xyz.essencexc.astra.ui.widgets.Checkbox
import xyz.essencexc.astra.ui.widgets.Label
import kotlin.math.abs

/**
 * Drag HUD elements around the real game screen.
 *
 * - Snaps to screen edges, the centre, the hotbar and other elements (hold Shift to place freely).
 * - Scroll to scale (Shift: finer); right-click resets position and scale.
 * - Tab / Shift+Tab select elements; arrows nudge (Shift: 10 px); Space shows/hides; R resets.
 * - Ctrl+Z / Ctrl+Y undo and redo; H collapses the side panel.
 *
 * Works in GUI pixels (no virtual resolution), so positions match the in-game HUD exactly.
 */
internal class HudEditorScreen(
    private val mod: AstraMod?,
    private val parent: Screen?,
    focus: HudElement? = null,
) : AstraScreen("HUD Editor", CascadeGeometricResolution.MINECRAFT) {
    /** One change, for undo/redo. */
    private class Edit(val entry: HudRegistry.Entry, val before: HudLayout, val after: HudLayout)

    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()

    /** Every Astra mod's HUDs, or only [mod]'s. */
    private var showAll = mod == null || allModsDefault

    private var entries: List<HudRegistry.Entry> = currentEntries()
    private var selected: HudRegistry.Entry? = entries.firstOrNull { it.element === focus } ?: entries.firstOrNull()
    private val canvas = Canvas()
    private val panel = Panel()

    init {
        add(canvas)
        add(panel)
        scene.focused = canvas
    }

    private fun currentEntries() = if (showAll || mod == null) HudRegistry.entries() else HudRegistry.entries(mod)

    private fun setShowAll(all: Boolean) {
        showAll = all
        allModsDefault = all
        entries = currentEntries()
        if (selected !in entries) selected = entries.firstOrNull()
        panel.rebuildList()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0x30000000)
    }

    override fun onClose() {
        if (parent != null) {
            //~ if >= 26.2 'client.setScreen' -> 'client.gui.setScreen'
            client.setScreen(parent)
        } else {
            super.onClose()
        }
    }

    // ---- changes, with undo -------------------------------------------------------------------

    private fun commit(e: HudRegistry.Entry, layout: HudLayout) {
        val before = e.option.get()
        if (before == layout) return
        e.option.set(layout)
        undoStack.addLast(Edit(e, before, layout))
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun undo() {
        val edit = undoStack.removeLastOrNull() ?: return
        edit.entry.option.set(edit.before)
        redoStack.addLast(edit)
        selected = edit.entry
    }

    private fun redo() {
        val edit = redoStack.removeLastOrNull() ?: return
        edit.entry.option.set(edit.after)
        undoStack.addLast(edit)
        selected = edit.entry
    }

    /** Default position and scale; keeps whether it's shown and its style. */
    private fun defaultFor(e: HudRegistry.Entry): HudLayout {
        val d = e.element.defaultLayout
        return e.option.get().withPosition(d.anchor, d.x, d.y).withScale(d.scale)
    }

    private fun dims(e: HudRegistry.Entry): Pair<Float, Float> = e.element.width().coerceAtLeast(4f) to e.element.height().coerceAtLeast(4f)

    // ---- the canvas: draws and moves the elements ---------------------------------------------

    private inner class Canvas : AstraWidget() {
        private var drag: HudRegistry.Entry? = null
        val dragging: Boolean get() = drag != null
        private var grabX = 0f
        private var grabY = 0f
        private var dragLeft = 0f
        private var dragTop = 0f
        private var guideX: Float? = null
        private var guideY: Float? = null

        init {
            size = FillSizeConstraint()
            position = FixedPositionConstraint(0, 0)
        }

        override fun cursorType(): CursorType = if (drag != null) CursorTypes.RESIZE_ALL else if (hit(mouseX, mouseY) != null) CursorTypes.POINTING_HAND else CursorTypes.ARROW

        /** Vanilla hotbar bounds (left, top, width, height), a snap target. */
        private fun hotbar() = floatArrayOf(width / 2f - 91f, height - 22f, 182f, 22f)

        /** Scaled bounds: left, top, width, height. */
        fun bounds(e: HudRegistry.Entry): FloatArray {
            val (w, h) = dims(e)
            val s = e.element.layout.scale
            if (e === drag) return floatArrayOf(dragLeft, dragTop, w * s, h * s)
            val (x, y) = e.element.layout.topLeft(width, height, w, h)
            return floatArrayOf(x, y, w * s, h * s)
        }

        fun hit(mx: Float, my: Float): HudRegistry.Entry? = entries.lastOrNull {
            val b = bounds(it)
            mx >= b[0] && my >= b[1] && mx < b[0] + b[2] && my < b[1] + b[3]
        }

        override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
            val e = hit(mx.toFloat(), my.toFloat())
            when (button) {
                0 -> {
                    selected = e
                    if (e != null) {
                        val b = bounds(e)
                        drag = e
                        grabX = mx.toFloat() - b[0]
                        grabY = my.toFloat() - b[1]
                        dragLeft = b[0]
                        dragTop = b[1]
                    }
                }
                1 -> if (e != null) {
                    selected = e
                    commit(e, defaultFor(e))
                }
            }
            return true
        }

        override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
            val e = drag ?: return true
            drag = null
            guideX = null
            guideY = null
            val (w, h) = dims(e)
            val l = e.element.layout
            commit(e, HudLayout.at(dragLeft, dragTop, w, h, l.scale, l.shown, width, height, l.style))
            return true
        }

        override fun onScroll(amount: Double): Boolean {
            val e = hit(mouseX, mouseY) ?: selected ?: return false
            selected = e
            val step = if (Keys.shift()) 0.05f else 0.1f
            val l = e.element.layout
            commit(e, l.withScale(Math.round((l.scale + if (amount > 0) step else -step) * 100f) / 100f))
            return true
        }

        override fun onKey(key: Int): Boolean {
            val ctrl = Keys.ctrl()
            when {
                ctrl && key == InputConstants.KEY_Z -> { if (Keys.shift()) redo() else undo(); return true }
                ctrl && key == InputConstants.KEY_Y -> { redo(); return true }
                key == InputConstants.KEY_H -> { panel.toggleCollapsed(); return true }
                key == InputConstants.KEY_TAB -> {
                    // Tab cycles the HUD elements instead of widget focus.
                    if (entries.isEmpty()) return true
                    val i = entries.indexOf(selected)
                    val n = entries.size
                    selected = entries[if (i < 0) 0 else (i + if (Keys.shift()) n - 1 else 1) % n]
                    return true
                }
            }
            val e = selected ?: return false
            val l = e.element.layout
            val d = if (Keys.shift()) 10f else 1f
            when (key) {
                InputConstants.KEY_LEFT -> commit(e, l.withPosition(l.anchor, l.x - d, l.y))
                InputConstants.KEY_RIGHT -> commit(e, l.withPosition(l.anchor, l.x + d, l.y))
                InputConstants.KEY_UP -> commit(e, l.withPosition(l.anchor, l.x, l.y - d))
                InputConstants.KEY_DOWN -> commit(e, l.withPosition(l.anchor, l.x, l.y + d))
                InputConstants.KEY_SPACE -> commit(e, l.withShown(!l.shown))
                InputConstants.KEY_R -> commit(e, defaultFor(e))
                else -> return false
            }
            return true
        }

        /** Snaps one axis. Returns the new start and the guide line to draw (or null). */
        private fun snap(pos: Float, size: Float, total: Float, others: List<Pair<Float, Float>>): Pair<Float, Float?> {
            val gap = 2f
            val candidates = ArrayList<Pair<Float, Float>>() // new start to guide
            candidates += gap to 0f
            candidates += (total - size - gap) to total
            candidates += ((total - size) / 2f) to (total / 2f)
            for ((s, len) in others) {
                candidates += s to s
                candidates += (s + len - size) to (s + len)
                candidates += (s + len + gap) to (s + len)
                candidates += (s - size - gap) to s
                candidates += (s + len / 2f - size / 2f) to (s + len / 2f)
            }
            val best = candidates.minByOrNull { abs(it.first - pos) } ?: return pos to null
            return if (abs(best.first - pos) <= SNAP) best.first to best.second else pos to null
        }

        override fun paint(g: GuiGraphicsExtractor) {
            val th = theme
            val accent = accentColor()

            // Drag with snapping (Shift = free).
            val d = drag
            if (d != null) {
                if (!pressed) {
                    drag = null
                } else {
                    val b = bounds(d)
                    var left = mouseX - grabX
                    var top = mouseY - grabY
                    guideX = null
                    guideY = null
                    if (!Keys.shift()) {
                        val others = entries.filter { it !== d }.map { bounds(it) } + listOf(hotbar())
                        snap(left, b[2], width, others.map { it[0] to it[2] }).let { (p, gl) -> left = p; guideX = gl }
                        snap(top, b[3], height, others.map { it[1] to it[3] }).let { (p, gl) -> top = p; guideY = gl }
                    }
                    dragLeft = left.coerceIn(0f, (width - b[2]).coerceAtLeast(0f))
                    dragTop = top.coerceIn(0f, (height - b[3]).coerceAtLeast(0f))
                }
            }

            // Centre lines and the hotbar outline, faint.
            Paint.rect(g, width / 2f - 0.5f, 0f, 1f, height, 0x14FFFFFF)
            Paint.rect(g, 0f, height / 2f - 0.5f, width, 1f, 0x14FFFFFF)
            hotbar().let { Paint.outline(g, it[0], it[1], it[2], it[3], if (drag != null) 0x40FFFFFF else 0x18FFFFFF, 2f) }

            val hover = if (drag == null) hit(mouseX, mouseY) else null
            for (e in entries) {
                val b = bounds(e)
                HudRegistry.draw(g, e.element, b[0], b[1], e.element.layout.scale, true)
                val off = !e.module.isEnabled()
                val hidden = !e.element.layout.shown
                if (off || hidden) Paint.rect(g, b[0], b[1], b[2], b[3], 0x99000000.toInt())
                val sel = e === selected
                val col = when {
                    sel -> accent
                    e === hover -> Colors.fade(th.text, 0.7f)
                    else -> 0x40FFFFFF
                }
                Paint.outline(g, b[0] - 1f, b[1] - 1f, b[2] + 2f, b[3] + 2f, col, 1.5f, 1f)
                if (sel || e === hover) tag(g, e, b, off, hidden)
            }

            guideX?.let { Paint.rect(g, it - 0.5f, 0f, 1f, height, Colors.fade(accent, 0.8f)) }
            guideY?.let { Paint.rect(g, 0f, it - 0.5f, width, 1f, Colors.fade(accent, 0.8f)) }

            // Help line.
            val help = if (entries.isEmpty()) "No HUD elements registered${if (!showAll) mod?.let { " by ${it.name}" } ?: "" else ""}." else
                "Drag: move · Shift: no snap · Scroll: scale · Right-click / R: reset · Tab: select · Arrows: nudge · Space: show/hide · Ctrl+Z/Y: undo/redo · H: panel · Esc: done"
            val size = th.fontSmall
            val hw = Paint.width(help, size) + 16f
            val hh = Paint.lineHeight(size) + 8f
            Paint.rect(g, (width - hw) / 2f, height - hh - 30f, hw, hh, 0xCC0C0E14.toInt(), hh / 2f)
            Paint.textCentered(g, help, width / 2f, height - hh - 30f, hh, 0xFFE8EAF2.toInt(), size)
        }

        private fun tag(g: GuiGraphicsExtractor, e: HudRegistry.Entry, b: FloatArray, off: Boolean, hidden: Boolean) {
            val th = theme
            val parts = buildString {
                append(e.option.title)
                append("  ×").append(String.format("%.2f", e.element.layout.scale).trimEnd('0').trimEnd('.'))
                if (showAll) e.mod?.let { append("  · ").append(it.name) }
                if (hidden) append("  · hidden")
                if (off) append("  · module off")
            }
            val size = th.fontSmall
            val tw = Paint.width(parts, size) + 10f
            val th2 = Paint.lineHeight(size) + 5f
            val above = b[1] - th2 - 3f >= 0f
            val ty = if (above) b[1] - th2 - 3f else b[1] + b[3] + 3f
            val tx = b[0].coerceIn(2f, (width - tw - 2f).coerceAtLeast(2f))
            Paint.rect(g, tx, ty, tw, th2, accentColor(), th2 / 2f)
            Paint.textMid(g, parts, tx + 5f, ty, th2, th.onAccent, size)
        }
    }

    // ---- the side panel ------------------------------------------------------------------------

    /**
     * Side panel: element list, all-mods toggle, undo/redo, Reset all, Done. Drag it by its title
     * bar, collapse it with the chevron (or H); it steps out of the way while an element is dragged.
     */
    private inner class Panel : Column() {
        val body = Column().apply { gap = 4f }
        private val list = Column().apply { gap = 2f }

        init {
            fixedWidth = 180f
            padding = 8f
            gap = 4f
            interact = true
            fill = { Colors.fade(theme.surface, 0.94f) }
            border = { theme.border }
            radius = { theme.radiusLarge }
            shadow = true
            position = object : IPositionConstraint {
                override fun _x(element: IPrimitiveElement<*>, parent: IPrimitiveElement<*>) =
                    (panelX ?: (parent.width - element.width - 8f)).coerceIn(0f, (parent.width - element.width).coerceAtLeast(0f))
                override fun _y(element: IPrimitiveElement<*>, parent: IPrimitiveElement<*>) =
                    (panelY ?: 8f).coerceIn(0f, (parent.height - element.height).coerceAtLeast(0f))
            }
            adopt(PanelHeader())
            body.adopt(Label("Click to select · tick to show", muted = true).apply { textSize = { theme.fontSmall } })
            if (mod != null) {
                body.adopt(Row().apply {
                    gap = 6f
                    adopt(Checkbox(Binding.of({ showAll }, { setShowAll(it) })))
                    adopt(Label("All Astra mods", muted = true).apply { grow = true; textSize = { theme.fontSmall + 0.5f } })
                })
            }
            body.adopt(list)
            body.adopt(Row().apply {
                gap = 4f
                adopt(Button("Undo", Button.Style.GHOST) { undo() }.apply { grow = true; enabledWhen = { undoStack.isNotEmpty() } })
                adopt(Button("Redo", Button.Style.GHOST) { redo() }.apply { grow = true; enabledWhen = { redoStack.isNotEmpty() } })
            })
            body.adopt(Row().apply {
                gap = 4f
                adopt(Button("Reset all", Button.Style.GHOST) {
                    for (e in entries) commit(e, defaultFor(e).withShown(true))
                }.apply { grow = true })
                adopt(Button("Done", Button.Style.PRIMARY) { onClose() }.apply { grow = true })
            })
            body.visible = !collapsed
            adopt(body)
            rebuildList()
        }

        fun rebuildList() {
            list.clear()
            for (e in entries) list.adopt(ElementRow(e))
            root.dirty()
        }

        fun toggleCollapsed() {
            collapsed = !collapsed
            body.visible = !collapsed
            root.dirty()
        }

        // Out of the way while an element is dragged: not drawn, not hit.
        override fun render(graphics: GuiGraphicsExtractor) {
            if (canvas.dragging) return
            super.render(graphics)
        }

        override fun find(x: Double, y: Double): IPrimitiveElement<*>? = if (canvas.dragging) null else super.find(x, y)
    }

    /** Title bar: drag handle for the panel, with a collapse chevron. */
    private inner class PanelHeader : AstraWidget() {
        private var grabX = 0f
        private var grabY = 0f
        private var moved = false
        private var downX = 0f
        private var downY = 0f

        init {
            fixedHeight = 16f
            focusable = false
        }

        override fun cursorType(): CursorType = CursorTypes.RESIZE_ALL

        override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
            grabX = mx.toFloat() - panel.x
            grabY = my.toFloat() - panel.y
            downX = mx.toFloat()
            downY = my.toFloat()
            moved = false
            return true
        }

        override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
            // A click (no drag) on the chevron collapses / expands.
            if (wasPressed && !moved && mx >= x + width - 16f) panel.toggleCollapsed()
            return true
        }

        override fun paint(g: GuiGraphicsExtractor) {
            if (pressed) {
                if (abs(mouseX - downX) + abs(mouseY - downY) > 2f) moved = true
                if (moved) {
                    panelX = mouseX - grabX
                    panelY = mouseY - grabY
                    root.dirty()
                }
            }
            val th = theme
            val title = if (showAll || mod == null) "HUD elements" else "${mod.name} HUD"
            Paint.textMid(g, Paint.truncate(title, th.fontBody, width - 22f, true), x, y, height, th.text, th.fontBody, true)
            Paint.chevron(g, x + width - 7f, y + height / 2f, 6f, Colors.mix(th.textMuted, th.text, hover.get()), if (collapsed) 0f else 1f)
        }
    }

    private inner class ElementRow(val e: HudRegistry.Entry) : AstraWidget() {
        init {
            fixedHeight = 22f
        }

        override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
            if (mx < x + 18f) commit(e, e.element.layout.withShown(!e.element.layout.shown)) else selected = e
            return true
        }

        override fun onKey(key: Int): Boolean {
            if (!Keys.isActivate(key)) return false
            commit(e, e.element.layout.withShown(!e.element.layout.shown))
            return true
        }

        override fun paint(g: GuiGraphicsExtractor) {
            val th = theme
            val accent = accentColor()
            val sel = selected === e
            val h = hover.get()
            if (sel || h > 0.01f) Paint.rect(g, x, y, width, height, if (sel) Colors.fade(accent, 0.16f) else Colors.fade(th.surfaceHover, h), th.radiusSmall)
            focusRing(g, x, y, width, height, th.radiusSmall)
            val shown = e.element.layout.shown
            val bx = x + 4f
            val by = y + (height - 10f) / 2f
            Paint.rect(g, bx, by, 10f, 10f, if (shown) accent else th.surfaceRaised, 2.5f)
            Paint.outline(g, bx, by, 10f, 10f, if (shown) accent else th.borderStrong, 2.5f)
            if (shown) Paint.check(g, bx, by, 10f, th.onAccent, 1f, 1.3f)
            val tx = x + 20f
            val title = Paint.truncate(e.option.title, th.fontBody - 0.5f, width - 24f)
            Paint.text(g, title, tx, y + 2f, if (sel) th.text else th.textMuted, th.fontBody - 0.5f)
            val owner = if (showAll) (e.mod?.name?.let { "$it · " } ?: "") else ""
            val sub = owner + e.module.name + if (!e.module.isEnabled()) " · off" else ""
            Paint.text(g, Paint.truncate(sub, th.fontSmall - 1f, width - 24f), tx, y + 3f + Paint.lineHeight(th.fontBody - 0.5f), th.textFaint, th.fontSmall - 1f)
        }
    }

    companion object {
        /** Snap distance in GUI pixels. */
        const val SNAP = 5f

        // Panel placement and the all-mods toggle survive reopening the editor (null = default, top right).
        private var panelX: Float? = null
        private var panelY: Float? = null
        private var collapsed = false
        private var allModsDefault = false

        /** Opens the editor over whatever screen is showing (returning to it after), with [focus] selected. */
        fun open(mod: AstraMod?, focus: HudElement? = null) {
            //~ if >= 26.2 'client.screen' -> 'client.gui.screen()'
            val parent = client.screen
            HudEditorScreen(mod, parent, focus).open()
        }
    }
}
