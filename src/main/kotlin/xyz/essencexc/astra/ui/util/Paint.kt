package xyz.essencexc.astra.ui.util

import foo.starred.cascade.graphics.extensions.circle.circle
import foo.starred.cascade.graphics.extensions.rectangle.hollow.hollowRectangle
import foo.starred.cascade.graphics.extensions.rectangle.rounded.roundedRectangle
import foo.starred.cascade.graphics.extensions.scissor.scissor
import foo.starred.cascade.graphics.extensions.shadow.dropShadow
import foo.starred.cascade.graphics.extensions.stroke.stroke
import foo.starred.cascade.graphics.font.CascadeFonts
import foo.starred.cascade.graphics.geometry.CascadeGeometricColor
import foo.starred.cascade.graphics.geometry.CascadeGeometricOffset
import foo.starred.cascade.graphics.geometry.CascadeGeometricRadius
import foo.starred.cascade.wrappers.text.impl.CascadeTextWrapper
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** Thin drawing helpers over Cascade's graphics extensions, taking plain ARGB ints. */
object Paint {
    private val radii = HashMap<Float, CascadeGeometricRadius>()

    fun radius(r: Float): CascadeGeometricRadius =
        if (r <= 0f) CascadeGeometricRadius.ZERO else radii.getOrPut(r) { CascadeGeometricRadius(r) }

    fun rect(g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, color: Int, r: Float = 0f) {
        if (w <= 0f || h <= 0f || Colors.alpha(color) == 0) return
        g.roundedRectangle(x, y, w, h, CascadeGeometricColor(color), radius(r.coerceAtMost(minOf(w, h) / 2f)))
    }

    /** Rectangle with a different radius per corner. */
    fun rect4(g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, color: Int, tl: Float, tr: Float, bl: Float, br: Float) {
        if (w <= 0f || h <= 0f || Colors.alpha(color) == 0) return
        g.roundedRectangle(x, y, w, h, CascadeGeometricColor(color), CascadeGeometricRadius(tl, tr, bl, br))
    }

    /** Rounded rectangle with a four-corner gradient. */
    fun gradient(g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, color: CascadeGeometricColor, r: Float = 0f) {
        if (w <= 0f || h <= 0f) return
        g.roundedRectangle(x, y, w, h, color, radius(r.coerceAtMost(minOf(w, h) / 2f)))
    }

    fun outline(g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, color: Int, r: Float = 0f, thickness: Float = 1f) {
        if (w <= 0f || h <= 0f || Colors.alpha(color) == 0) return
        g.hollowRectangle(x, y, w, h, thickness, CascadeGeometricColor(color), radius(r.coerceAtMost(minOf(w, h) / 2f)), true)
    }

    fun shadow(g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, r: Float, blur: Float = 16f, color: Int = 0x66000000, dy: Float = 4f) {
        g.dropShadow(x, y, w, h, CascadeGeometricOffset(0f, dy), blur, 0f, CascadeGeometricColor(color), radius(r))
    }

    fun circle(g: GuiGraphicsExtractor, cx: Float, cy: Float, r: Float, color: Int) {
        if (r <= 0f || Colors.alpha(color) == 0) return
        g.circle(cx, cy, r, CascadeGeometricColor(color))
    }

    fun line(g: GuiGraphicsExtractor, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, thickness: Float = 1f) {
        if (Colors.alpha(color) == 0) return
        g.stroke(x1, y1, x2, y2, CascadeGeometricColor(color), thickness, true)
    }

    /** A chevron centred on (cx, cy). [rotation] 0 points down, 1 points up. */
    fun chevron(g: GuiGraphicsExtractor, cx: Float, cy: Float, size: Float, color: Int, rotation: Float = 0f, thickness: Float = 1.2f) {
        val dy = size / 4f * (1f - 2f * rotation)
        line(g, cx - size / 2f, cy - dy, cx, cy + dy, color, thickness)
        line(g, cx, cy + dy, cx + size / 2f, cy - dy, color, thickness)
    }

    /** A check mark inside a box at (x, y, size), drawn up to [progress] (0..1). */
    fun check(g: GuiGraphicsExtractor, x: Float, y: Float, size: Float, color: Int, progress: Float = 1f, thickness: Float = 1.6f) {
        if (progress <= 0f) return
        val ax = x + size * 0.22f; val ay = y + size * 0.52f
        val bx = x + size * 0.42f; val by = y + size * 0.72f
        val cx = x + size * 0.78f; val cy = y + size * 0.3f
        val p1 = (progress / 0.4f).coerceAtMost(1f)
        line(g, ax, ay, ax + (bx - ax) * p1, ay + (by - ay) * p1, color, thickness)
        if (progress > 0.4f) {
            val p2 = ((progress - 0.4f) / 0.6f).coerceAtMost(1f)
            line(g, bx, by, bx + (cx - bx) * p2, by + (cy - by) * p2, color, thickness)
        }
    }

    inline fun clip(g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, block: () -> Unit) {
        g.scissor(x, y, w, h, block)
    }

    /** A checkerboard, shown behind translucent colours. */
    fun checker(g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, cell: Float = 3f) {
        rect(g, x, y, w, h, 0xFFE6E6E6.toInt())
        var row = 0
        var yy = y
        while (yy < y + h) {
            var xx = x + if (row % 2 == 0) 0f else cell
            val ch = minOf(cell, y + h - yy)
            while (xx < x + w) {
                g.roundedRectangle(xx, yy, minOf(cell, x + w - xx), ch, CascadeGeometricColor(0xFFB4B4B4.toInt()), CascadeGeometricRadius.ZERO)
                xx += cell * 2
            }
            yy += cell
            row++
        }
    }

    // ---- text ---------------------------------------------------------------------------------

    private fun component(text: String, bold: Boolean): Component =
        if (bold) Component.literal(text).withStyle(ChatFormatting.BOLD) else Component.literal(text)

    fun text(g: GuiGraphicsExtractor, text: String, x: Float, y: Float, color: Int, size: Float, bold: Boolean = false) {
        if (text.isEmpty() || Colors.alpha(color) == 0) return
        CascadeFonts.sans.extract(g, component(text, bold), x, y, CascadeGeometricColor(color), false, size)
    }

    /** Draws [text] vertically centred in a band starting at [y] of height [h]. */
    fun textMid(g: GuiGraphicsExtractor, text: String, x: Float, y: Float, h: Float, color: Int, size: Float, bold: Boolean = false) =
        text(g, text, x, y + (h - lineHeight(size)) / 2f, color, size, bold)

    fun textCentered(g: GuiGraphicsExtractor, text: String, cx: Float, y: Float, h: Float, color: Int, size: Float, bold: Boolean = false) =
        textMid(g, text, cx - width(text, size, bold) / 2f, y, h, color, size, bold)

    fun width(text: String, size: Float, bold: Boolean = false): Float =
        if (text.isEmpty()) 0f else CascadeFonts.sans.width(component(text, bold), size)

    fun lineHeight(size: Float): Float = CascadeTextWrapper.height(size)

    fun truncate(text: String, size: Float, max: Float, bold: Boolean = false): String =
        CascadeFonts.sans.truncate(text, size, max, "...", bold, true)

    /** Greedy word wrap. Long words are broken by character. */
    fun wrap(text: String, size: Float, maxWidth: Float, bold: Boolean = false): List<String> {
        if (text.isEmpty()) return emptyList()
        if (maxWidth <= 0f) return listOf(text)
        val out = ArrayList<String>()
        for (paragraph in text.split('\n')) {
            var line = StringBuilder()
            for (word in paragraph.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (width(candidate, size, bold) <= maxWidth) {
                    line = StringBuilder(candidate)
                    continue
                }
                if (line.isNotEmpty()) out += line.toString()
                line = StringBuilder()
                var rest = word
                while (width(rest, size, bold) > maxWidth && rest.length > 1) {
                    var cut = rest.length - 1
                    while (cut > 1 && width(rest.substring(0, cut), size, bold) > maxWidth) cut--
                    out += rest.substring(0, cut)
                    rest = rest.substring(cut)
                }
                line.append(rest)
            }
            out += line.toString()
        }
        return out
    }

    fun wrappedHeight(lines: Int, size: Float, gap: Float = 2f): Float =
        if (lines <= 0) 0f else lines * lineHeight(size) + (lines - 1) * gap

    fun lines(g: GuiGraphicsExtractor, lines: List<String>, x: Float, y: Float, color: Int, size: Float, gap: Float = 2f) {
        var yy = y
        for (l in lines) {
            text(g, l, x, yy, color, size)
            yy += lineHeight(size) + gap
        }
    }
}
