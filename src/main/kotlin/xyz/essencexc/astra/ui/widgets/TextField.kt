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
 * Single-line text input: caret, mouse and Shift selection, Ctrl word jumps, clipboard
 * (Ctrl+A/C/X/V), horizontal scrolling and a placeholder.
 *
 * [onChange] fires on every edit, [onCommit] on Enter or when focus leaves. Escape reverts to the
 * text it had when it was focused.
 */
open class TextField(initial: String = "") : AstraWidget() {
    /** The current text. Setting it from code doesn't fire [onChange]. */
    var text: String = initial
        set(value) {
            field = value.take(maxLength)
            caret = caret.coerceIn(0, field.length)
            anchor = anchor.coerceIn(0, field.length)
        }

    var placeholder: String = ""
    var maxLength: Int = 256

    /** Characters allowed to be typed or pasted. */
    var filter: (Char) -> Boolean = { true }

    var onChange: ((String) -> Unit)? = null
    var onCommit: ((String) -> Unit)? = null

    /** Called on key press before the field's own handling; return true to consume. */
    var onKeyHook: ((Int) -> Boolean)? = null

    var selectAllOnFocus: Boolean = false
    var centered: Boolean = false

    /** Drawn after the text in muted colour, e.g. a unit ("ms"). */
    var suffix: String = ""

    /** Draws its own box; turn off when embedding in another widget's chrome. */
    var chrome: Boolean = true

    var caret: Int = initial.length
        private set
    private var anchor: Int = caret
    private var scrollX = 0f
    private var revertTo = initial
    private var lastInput = 0L
    private var lastClick = 0L
    private val flashGlow = xyz.essencexc.astra.ui.util.Motion(0f, speed = 4f)

    /** Briefly highlights the field, e.g. to point at it. */
    fun flash() {
        flashGlow.snap(1f).to(0f)
    }

    private val padX = 5f
    private val fontSize get() = theme.fontBody

    init {
        fixedHeight = CONTROL_HEIGHT
    }

    override fun prefWidth(): Float = fixedWidth ?: 120f
    override fun cursorType(): CursorType = CursorTypes.IBEAM

    fun selectAll() {
        anchor = 0
        caret = text.length
    }

    val selection: IntRange
        get() = minOf(anchor, caret) until maxOf(anchor, caret)

    val hasSelection: Boolean get() = anchor != caret

    // ---- input --------------------------------------------------------------------------------

    override fun onFocusChanged(focused: Boolean) {
        Keys.textInput(this, focused)
        if (focused) {
            revertTo = text
            if (selectAllOnFocus) selectAll()
            lastInput = System.currentTimeMillis()
        } else {
            anchor = caret
            scrollX = 0f
            onCommit?.invoke(text)
        }
    }

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
        if (button != 0) return true
        val i = indexAt(mx.toFloat())
        val now = System.currentTimeMillis()
        if (now - lastClick < 300 && i == caret && !hasSelection) {
            selectWord(i)
        } else {
            caret = i
            if (!Keys.shift()) anchor = i
        }
        lastClick = now
        lastInput = now
        return true
    }

    override fun onKey(key: Int): Boolean {
        if (onKeyHook?.invoke(key) == true) return true
        val shift = Keys.shift()
        val ctrl = Keys.ctrl()
        lastInput = System.currentTimeMillis()

        when {
            key == InputConstants.KEY_LEFT -> move(if (ctrl) wordLeft(caret) else if (hasSelection && !shift) selection.first else caret - 1, shift)
            key == InputConstants.KEY_RIGHT -> move(if (ctrl) wordRight(caret) else if (hasSelection && !shift) selection.last + 1 else caret + 1, shift)
            key == InputConstants.KEY_HOME -> move(0, shift)
            key == InputConstants.KEY_END -> move(text.length, shift)
            key == InputConstants.KEY_BACKSPACE -> when {
                hasSelection -> replace("")
                caret > 0 -> {
                    anchor = if (ctrl) wordLeft(caret) else caret - 1
                    replace("")
                }
            }
            key == InputConstants.KEY_DELETE -> when {
                hasSelection -> replace("")
                caret < text.length -> {
                    anchor = if (ctrl) wordRight(caret) else caret + 1
                    replace("")
                }
            }
            ctrl && key == InputConstants.KEY_A -> selectAll()
            ctrl && key == InputConstants.KEY_C -> if (hasSelection) Keys.clipboard = text.substring(selection.first, selection.last + 1)
            ctrl && key == InputConstants.KEY_X -> if (hasSelection) {
                Keys.clipboard = text.substring(selection.first, selection.last + 1)
                replace("")
            }
            ctrl && key == InputConstants.KEY_V -> replace(Keys.clipboard.replace("\n", " ").filter(filter))
            Keys.isEnter(key) -> {
                onCommit?.invoke(text)
                revertTo = text
                blur()
            }
            key == InputConstants.KEY_ESCAPE -> {
                if (text != revertTo) {
                    text = revertTo
                    onChange?.invoke(text)
                }
                caret = text.length
                anchor = caret
                blur()
            }
            key == InputConstants.KEY_TAB -> return false
            else -> return !Keys.isModifier(key) && key != InputConstants.KEY_UP && key != InputConstants.KEY_DOWN
        }
        return true
    }

    override fun onChar(c: Char): Boolean {
        if (c < ' ' || !filter(c)) return true
        replace(c.toString())
        lastInput = System.currentTimeMillis()
        return true
    }

    private fun move(to: Int, extend: Boolean) {
        caret = to.coerceIn(0, text.length)
        if (!extend) anchor = caret
    }

    private fun replace(insert: String) {
        val sel = selection
        val start = if (hasSelection) sel.first else caret
        val end = if (hasSelection) sel.last + 1 else caret
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
        while (i > 0 && text[i - 1] == ' ') i--
        while (i > 0 && text[i - 1] != ' ') i--
        return i
    }

    private fun wordRight(from: Int): Int {
        var i = from
        while (i < text.length && text[i] == ' ') i++
        while (i < text.length && text[i] != ' ') i++
        return i
    }

    private fun selectWord(at: Int) {
        anchor = wordLeft(at)
        caret = wordRight(at)
    }

    private fun textX(): Float {
        if (!centered || isFocused) return x + padX - scrollX
        return x + (width - Paint.width(text + suffix, fontSize)) / 2f
    }

    private fun indexAt(mx: Float): Int {
        val rel = mx - textX()
        if (rel <= 0f) return 0
        var prev = 0f
        for (i in 1..text.length) {
            val w = Paint.width(text.substring(0, i), fontSize)
            if (rel < (prev + w) / 2f) return i - 1
            prev = w
        }
        return text.length
    }

    // ---- drawing ------------------------------------------------------------------------------

    override fun paint(g: GuiGraphicsExtractor) {
        val t = theme
        val focused = isFocused

        if (pressed && focused) caret = indexAt(mouseX)

        if (chrome) {
            val r = t.radiusSmall
            Paint.rect(g, x, y, width, height, c(Colors.mix(t.surfaceRaised, t.surfaceHover, hover.get())), r)
            val border = if (focused) accentColor() else Colors.mix(t.border, t.borderStrong, hover.get())
            Paint.outline(g, x, y, width, height, c(border), r)
            focusRing(g, x, y, width, height, r)
            val fl = flashGlow.get()
            if (fl > 0.01f) Paint.outline(g, x - 2f, y - 2f, width + 4f, height + 4f, Colors.fade(accentColor(), fl), r + 2f, 1.5f)
        }

        // Keep the caret in view.
        val inner = width - padX * 2
        val caretPx = Paint.width(text.substring(0, caret), fontSize)
        if (focused) {
            if (caretPx - scrollX > inner) scrollX = caretPx - inner
            if (caretPx - scrollX < 0f) scrollX = caretPx
            scrollX = scrollX.coerceIn(0f, (Paint.width(text, fontSize) - inner).coerceAtLeast(0f))
        }

        val tx = textX()
        val lh = Paint.lineHeight(fontSize)
        val ty = y + (height - lh) / 2f

        Paint.clip(g, x + 2f, y, width - 4f, height) {
            if (focused && hasSelection) {
                val sx = tx + Paint.width(text.substring(0, selection.first), fontSize)
                val ex = tx + Paint.width(text.substring(0, selection.last + 1), fontSize)
                Paint.rect(g, sx, ty - 1f, ex - sx, lh + 2f, Colors.fade(accentColor(), 0.35f), 1.5f)
            }

            if (text.isEmpty() && !focused) {
                Paint.text(g, placeholder, x + padX, ty, c(t.textFaint), fontSize)
            } else {
                Paint.text(g, text, tx, ty, c(t.text), fontSize)
                if (suffix.isNotEmpty() && !focused) Paint.text(g, suffix, tx + Paint.width(text, fontSize) + 1f, ty, c(t.textMuted), fontSize)
            }

            if (focused && ((System.currentTimeMillis() - lastInput) / 530) % 2 == 0L) {
                val cx = tx + caretPx
                Paint.rect(g, cx, ty - 0.5f, 1f, lh + 1f, accentColor())
            }
        }
    }

    companion object {
        /** Standard height for single-line controls. */
        const val CONTROL_HEIGHT = 18f

        /** Accepts digits, sign and decimal point. */
        @JvmField
        val NUMERIC: (Char) -> Boolean = { it.isDigit() || it == '-' || it == '.' || it == ',' }
    }
}
