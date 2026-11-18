package xyz.essencexc.astra.ui.layout

import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.util.Paint

enum class Align { START, CENTER, END, STRETCH }

/**
 * A container with optional fill, border and padding. [Column], [Row] and [Grid] build on it.
 * Colours are suppliers so they follow the live theme.
 */
abstract class Box : AstraElement() {
    var padding: Float = 0f
    var paddingX: Float? = null
    var paddingY: Float? = null
    var gap: Float = 0f
    var fill: (() -> Int)? = null
    var border: (() -> Int)? = null
    var radius: () -> Float = { 0f }
    var shadow: Boolean = false

    protected val px: Float get() = paddingX ?: padding
    protected val py: Float get() = paddingY ?: padding

    /** Children taking part in layout: visible ones. */
    protected fun items(): List<IPrimitiveElement<*>> = children.filter { it.visible }

    override fun draw(graphics: GuiGraphicsExtractor) {
        val r = radius()
        if (shadow) Paint.shadow(graphics, x, y, width, height, r)
        fill?.let { Paint.rect(graphics, x, y, width, height, it(), r) }
        border?.let { Paint.outline(graphics, x, y, width, height, it(), r) }
    }

    fun add(vararg elements: IPrimitiveElement<*>): Box = apply { for (e in elements) adopt(e) }

    /** Clears every child. */
    fun clear() {
        for (c in children.toList()) disown(c)
    }

    companion object {
        /** Preferred width of any primitive: Astra elements report it, others use their size constraint. */
        fun prefW(e: IPrimitiveElement<*>, parent: IPrimitiveElement<*>): Float =
            if (e is AstraElement) e.prefWidth() else { e.constrain(parent); e.width }

        fun prefH(e: IPrimitiveElement<*>, width: Float, parent: IPrimitiveElement<*>): Float =
            if (e is AstraElement) e.prefHeight(width) else { e.constrain(parent); e.height }

        fun put(e: IPrimitiveElement<*>, x: Float, y: Float, w: Float, h: Float) {
            if (e is AstraElement) {
                e.place(x, y, w, h)
            } else {
                e.width = w
                e.height = h
                e.x = x
                e.y = y
                e.layout()
            }
        }

        fun grows(e: IPrimitiveElement<*>) = e is AstraElement && e.grow
    }
}

/** Stacks children top to bottom. Its height is the sum of theirs unless fixed. */
open class Column : Box() {
    /** How children are placed horizontally. STRETCH (default) gives them the full width. */
    var align: Align = Align.STRETCH

    override fun prefWidth(): Float {
        fixedWidth?.let { return it }
        return (items().maxOfOrNull { prefW(it, this) } ?: 0f) + px * 2
    }

    override fun prefHeight(width: Float): Float {
        fixedHeight?.let { return it }
        val inner = width - px * 2
        val list = items()
        if (list.isEmpty()) return py * 2
        return list.sumOf { prefH(it, childWidth(it, inner), this).toDouble() }.toFloat() + gap * (list.size - 1) + py * 2
    }

    private fun childWidth(e: IPrimitiveElement<*>, inner: Float): Float =
        if (align == Align.STRETCH || grows(e)) inner else prefW(e, this).coerceAtMost(inner)

    override fun layout() {
        dirty = false
        val inner = width - px * 2
        val list = items()
        val widths = FloatArray(list.size) { childWidth(list[it], inner) }
        val heights = FloatArray(list.size) { prefH(list[it], widths[it], this) }
        val fillers = list.indices.filter { (list[it] as? AstraElement)?.fillHeight == true }
        if (fillers.isNotEmpty()) {
            val used = list.indices.filter { it !in fillers }.sumOf { heights[it].toDouble() }.toFloat() + gap * (list.size - 1)
            val each = ((height - py * 2 - used) / fillers.size).coerceAtLeast(0f)
            for (i in fillers) heights[i] = each
        }
        var cy = y + py
        for ((i, c) in list.withIndex()) {
            val w = widths[i]
            val h = heights[i]
            val cx = when (align) {
                Align.CENTER -> x + px + (inner - w) / 2f
                Align.END -> x + width - px - w
                else -> x + px
            }
            put(c, cx, cy, w, h)
            cy += h + gap
        }
    }
}

/** Places children left to right, vertically centred. Children with `grow` share the leftover width. */
open class Row : Box() {
    var align: Align = Align.CENTER

    override fun prefWidth(): Float {
        fixedWidth?.let { return it }
        val list = items()
        if (list.isEmpty()) return px * 2
        return list.sumOf { prefW(it, this).toDouble() }.toFloat() + gap * (list.size - 1) + px * 2
    }

    override fun prefHeight(width: Float): Float {
        fixedHeight?.let { return it }
        val widths = widths(width - px * 2)
        return (items().mapIndexed { i, c -> prefH(c, widths[i], this) }.maxOrNull() ?: 0f) + py * 2
    }

    private fun widths(inner: Float): FloatArray {
        val list = items()
        val w = FloatArray(list.size) { prefW(list[it], this) }
        val growers = list.count { grows(it) }
        if (growers > 0) {
            val used = list.indices.filter { !grows(list[it]) }.sumOf { w[it].toDouble() }.toFloat() + gap * (list.size - 1)
            val each = ((inner - used) / growers).coerceAtLeast(0f)
            for (i in list.indices) if (grows(list[i])) w[i] = each
        }
        return w
    }

    override fun layout() {
        dirty = false
        val list = items()
        val inner = height - py * 2
        val widths = widths(width - px * 2)
        var cx = x + px
        for ((i, c) in list.withIndex()) {
            val w = widths[i]
            val h = if (align == Align.STRETCH) inner else prefH(c, w, this).coerceAtMost(inner.coerceAtLeast(0f))
            val cy = when (align) {
                Align.START, Align.STRETCH -> y + py
                Align.CENTER -> y + py + (inner - h) / 2f
                Align.END -> y + height - py - h
            }
            put(c, cx, cy, w, h)
            cx += w + gap
        }
    }
}

/** Equal-width columns, wrapping. Uses as many columns as fit [minCellWidth], up to [maxColumns]. */
open class Grid : Box() {
    var minCellWidth: Float = 160f
    var maxColumns: Int = 4
    var rowGap: Float? = null

    private fun columns(inner: Float): Int =
        ((inner + gap) / (minCellWidth + gap)).toInt().coerceIn(1, maxColumns)

    override fun prefHeight(width: Float): Float {
        fixedHeight?.let { return it }
        val inner = width - px * 2
        val cols = columns(inner)
        val cell = (inner - gap * (cols - 1)) / cols
        val rows = items().chunked(cols)
        if (rows.isEmpty()) return py * 2
        return rows.sumOf { r -> (r.maxOf { prefH(it, cell, this) }).toDouble() }.toFloat() + (rowGap ?: gap) * (rows.size - 1) + py * 2
    }

    override fun layout() {
        dirty = false
        val inner = width - px * 2
        val cols = columns(inner)
        val cell = (inner - gap * (cols - 1)) / cols
        var cy = y + py
        for (row in items().chunked(cols)) {
            val h = row.maxOf { prefH(it, cell, this) }
            for ((i, c) in row.withIndex()) put(c, x + px + i * (cell + gap), cy, cell, h)
            cy += h + (rowGap ?: gap)
        }
    }
}

/** Fixed-size empty space. */
class Spacer(w: Float = 0f, h: Float = 0f) : AstraElement() {
    init {
        fixedWidth = w
        fixedHeight = h
    }
}
