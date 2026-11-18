package xyz.essencexc.astra.ui.widgets

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.cursor.CursorType
import com.mojang.blaze3d.platform.cursor.CursorTypes
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Paint

/**
 * Multi-line text input with word wrap. Grows from [minLines] to [maxLines] and scrolls beyond.
 * Enter inserts a line break; Ctrl+Enter (or leaving the field) commits; Escape reverts.
 * Arrows, Home/End (per line), Shift selection, Ctrl word jumps and Ctrl+A/C/X/V work like
 * [TextField].
 */
open class TextArea(initial: String = "") : AstraWidget() {
    var text: String = initial
        set(value) {
            field = value.take(maxLength)
            caret = caret.coerceIn(0, field.length)
            anchor = anchor.coerceIn(0, field.length)
        }

    var placeholder: String = ""
    var maxLength: Int = 2048
    var minLines: Int = 3
    var maxLines: Int = 6
    var onChange: ((String) -> Unit)? = null
    var onCommit: ((String) -> Unit)? = null

    private var caret = initial.length
    private var anchor = caret
    private var scrollY = 0f
    private var revertTo = initial
    private var lastInput = 0L
    private var lastLineCount = -1

    /** Remembered x while moving up/down, so the caret doesn't drift left on short lines. */
    private var goalX = -1f

    private val padX = 5f
    private val padY = 4f
    private val gap = 2f
    private val fontSize get() = theme.fontBody
    private val lineH get() = Paint.lineHeight(fontSize) + gap

    override fun prefWidth(): Float = fixedWidth ?: 200f

    override fun prefHeight(width: Float): Float {
        val lines = lines(width - padX * 2).size.coerceIn(minLines, maxLines)
        return lines * lineH - gap + padY * 2
    }

    override fun cursorType(): CursorType = CursorTypes.IBEAM

    // ---- wrapping -----------------------------------------------------------------------------

    /** Visual lines as [start, end) ranges into [text] (the end excludes a trailing newline). */
    private fun lines(maxW: Float): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = 0
        while (true) {
            val nl = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            var s = start
            // Wrap this paragraph.
            while (true) {
                if (Paint.width(text.substring(s, nl), fontSize) <= maxW || nl - s <= 1) {
                    out += s until nl
                    break
                }
                var e = s + 1
                while (e < nl && Paint.width(text.substring(s, e + 1), fontSize) <= maxW) e++
                val space = text.lastIndexOf(' ', e - 1).takeIf { it >= s + 1 }
                val cut = if (space != null) space + 1 else e
                out += s until cut
                s = cut
            }
            if (nl >= text.length) break
            start = nl + 1
        }
        return out
    }

    private fun lineOf(index: Int, ls: List<IntRange>): Int {
        for ((i, r) in ls.withIndex()) {
            val end = r.last + 1
            if (index < end || (index == end && (i == ls.lastIndex || text.getOrNull(end) == '\n'))) return i
        }
        return ls.lastIndex
    }

    private fun xOf(index: Int, r: IntRange): Float = Paint.width(text.substring(r.first, index.coerceIn(r.first, r.last + 1)), fontSize)

    private fun indexAtX(r: IntRange, x: Float): Int {
        var prev = 0f
        for (i in r.first + 1..r.last + 1) {
            val w = Paint.width(text.substring(r.first, i), fontSize)
            if (x < (prev + w) / 2f) return i - 1
            prev = w
        }
        return r.last + 1
    }

    private fun indexAt(mx: Float, my: Float): Int {
        val ls = lines(width - padX * 2)
        val li = ((my - y - padY + scrollY) / lineH).toInt().coerceIn(0, ls.lastIndex)
        return indexAtX(ls[li], mx - x - padX)
    }

    // ---- input --------------------------------------------------------------------------------

    private val selection get() = minOf(anchor, caret) until maxOf(anchor, caret)
    private val hasSelection get() = anchor != caret

    override fun onFocusChanged(focused: Boolean) {
        Keys.textInput(this, focused)
        if (focused) {
            revertTo = text
            lastInput = System.currentTimeMillis()
        } else {
            anchor = caret
            onCommit?.invoke(text)
        }
    }

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
        if (button != 0) return true
        caret = indexAt(mx.toFloat(), my.toFloat())
        if (!Keys.shift()) anchor = caret
        goalX = -1f
        lastInput = System.currentTimeMillis()
        return true
    }

    override fun onScroll(amount: Double): Boolean {
        val ls = lines(width - padX * 2)
        val max = (ls.size * lineH - gap + padY * 2 - height).coerceAtLeast(0f)
        if (max <= 0f) return false
        scrollY = (scrollY - amount.toFloat() * lineH).coerceIn(0f, max)
        return true
    }

    override fun onKey(key: Int): Boolean {
        val shift = Keys.shift()
        val ctrl = Keys.ctrl()
        lastInput = System.currentTimeMillis()
        val ls = lines(width - padX * 2)
        val li = lineOf(caret, ls)
        if (key != InputConstants.KEY_UP && key != InputConstants.KEY_DOWN) goalX = -1f

        when {
            key == InputConstants.KEY_LEFT -> move(if (hasSelection && !shift) selection.first else if (ctrl) wordLeft(caret) else caret - 1, shift)
            key == InputConstants.KEY_RIGHT -> move(if (hasSelection && !shift) selection.last + 1 else if (ctrl) wordRight(caret) else caret + 1, shift)
            key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN -> {
                val target = li + if (key == InputConstants.KEY_UP) -1 else 1
                if (goalX < 0f) goalX = xOf(caret, ls[li])
                when {
                    target < 0 -> move(0, shift)
                    target > ls.lastIndex -> move(text.length, shift)
                    else -> move(indexAtX(ls[target], goalX), shift)
                }
            }
            key == InputConstants.KEY_HOME -> move(if (ctrl) 0 else ls[li].first, shift)
            key == InputConstants.KEY_END -> move(if (ctrl) text.length else ls[li].last + 1, shift)
            key == InputConstants.KEY_BACKSPACE -> when {
                hasSelection -> replace("")
                caret > 0 -> { anchor = if (ctrl) wordLeft(caret) else caret - 1; replace("") }
            }
            key == InputConstants.KEY_DELETE -> when {
                hasSelection -> replace("")
                caret < text.length -> { anchor = if (ctrl) wordRight(caret) else caret + 1; replace("") }
            }
            ctrl && key == InputConstants.KEY_A -> { anchor = 0; caret = text.length }
            ctrl && key == InputConstants.KEY_C -> if (hasSelection) Keys.clipboard = text.substring(selection.first, selection.last + 1)
            ctrl && key == InputConstants.KEY_X -> if (hasSelection) {
                Keys.clipboard = text.substring(selection.first, selection.last + 1)
                replace("")
            }
            ctrl && key == InputConstants.KEY_V -> replace(Keys.clipboard.replace("\r", ""))
            Keys.isEnter(key) && ctrl -> {
                onCommit?.invoke(text)
                revertTo = text
                blur()
            }
            Keys.isEnter(key) -> replace("\n")
            key == InputConstants.KEY_ESCAPE -> {
                if (text != revertTo) {
                    text = revertTo
                    onChange?.invoke(text)
                }
                blur()
            }
            key == InputConstants.KEY_TAB -> return false
            else -> return !Keys.isModifier(key)
        }
        return true
    }

    override fun onChar(c: Char): Boolean {
        if (c < ' ') return true
        replace(c.toString())
        return true
    }

    private fun move(to: Int, extend: Boolean) {
        caret = to.coerceIn(0, text.length)
        if (!extend) anchor = caret
    }

    private fun replace(insert: String) {
        val start = if (hasSelection) selection.first else caret
        val end = if (hasSelection) selection.last + 1 else caret
        val room = maxLength - (text.length - (end - start))
        val ins = insert.take(room.coerceAtLeast(0))
        val next = text.substring(0, start) + ins + text.substring(end)
        caret = start + ins.length
        anchor = caret
        if (next != text) {
            text = next
            onChange?.invoke(text)
        }
    }

    private fun wordLeft(from: Int): Int {
        var i = from
        while (i > 0 && text[i - 1].isWhitespace()) i--
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        return i
    }

    private fun wordRight(from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        while (i < text.length && !text[i].isWhitespace()) i++
        return i
    }

    // ---- drawing ------------------------------------------------------------------------------

    override fun paint(g: GuiGraphicsExtractor) {
        val th = theme
        val focused = isFocused
        val innerW = width - padX * 2
        val ls = lines(innerW)

        // Height follows the line count (within min..max): relayout when it changes.
        val shownLines = ls.size.coerceIn(minLines, maxLines)
        if (lastLineCount != -1 && shownLines != lastLineCount) root.dirty()
        lastLineCount = shownLines

        if (pressed && focused) caret = indexAt(mouseX, mouseY)

        val r = th.radiusSmall
        Paint.rect(g, x, y, width, height, c(Colors.mix(th.surfaceRaised, th.surfaceHover, hover.get())), r)
        Paint.outline(g, x, y, width, height, c(if (focused) accentColor() else Colors.mix(th.border, th.borderStrong, hover.get())), r)
        focusRing(g, x, y, width, height, r)

        // Keep the caret line visible.
        val li = lineOf(caret, ls)
        val viewH = height - padY * 2
        val caretTop = li * lineH
        if (focused) {
            if (caretTop - scrollY < 0f) scrollY = caretTop
            if (caretTop + lineH - gap - scrollY > viewH) scrollY = caretTop + lineH - gap - viewH
        }
        scrollY = scrollY.coerceIn(0f, (ls.size * lineH - gap - viewH).coerceAtLeast(0f))

        Paint.clip(g, x + 1f, y + 1f, width - 2f, height - 2f) {
            if (text.isEmpty() && !focused) {
                Paint.text(g, placeholder, x + padX, y + padY, c(th.textFaint), fontSize)
            }
            for ((i, lr) in ls.withIndex()) {
                val ly = y + padY + i * lineH - scrollY
                if (ly + lineH < y || ly > y + height) continue
                if (focused && hasSelection) {
                    val s = maxOf(selection.first, lr.first)
                    val e = minOf(selection.last + 1, lr.last + 1)
                    if (s < e || (selection.first <= lr.last + 1 && selection.last + 1 > lr.last + 1 && lr.isEmpty())) {
                        val sx = x + padX + xOf(s, lr)
                        val ex = x + padX + xOf(maxOf(e, s), lr) + if (selection.last + 1 > lr.last + 1) 3f else 0f
                        Paint.rect(g, sx, ly - 1f, ex - sx, lineH - gap + 2f, Colors.fade(accentColor(), 0.35f), 1.5f)
                    }
                }
                if (!lr.isEmpty()) Paint.text(g, text.substring(lr.first, lr.last + 1), x + padX, ly, c(th.text), fontSize)
            }
            if (focused && ((System.currentTimeMillis() - lastInput) / 530) % 2 == 0L) {
                val cx = x + padX + xOf(caret, ls[li])
                val cy = y + padY + li * lineH - scrollY
                Paint.rect(g, cx, cy - 0.5f, 1f, lineH - gap + 1f, accentColor())
            }
        }

        // Scroll hint.
        val total = ls.size * lineH - gap
        if (total > viewH) {
            val th2 = (viewH * viewH / total).coerceAtLeast(8f)
            val ty = y + padY + (viewH - th2) * (scrollY / (total - viewH))
            Paint.rect(g, x + width - 3f, ty, 1.5f, th2, Colors.fade(th.textFaint, 0.6f), 0.75f)
        }
    }
}
