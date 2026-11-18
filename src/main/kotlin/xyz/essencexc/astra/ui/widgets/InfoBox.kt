package xyz.essencexc.astra.ui.widgets

import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.annotations.InfoType
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.theme.Theme
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Paint

/** A tinted note with an icon. Used for `@Info`. [text] is read live, so it can change. */
class InfoBox(var type: InfoType, var title: String = "", var text: () -> String) : AstraElement() {
    private val pad = 7f
    private val icon = 12f

    override fun prefWidth(): Float = fixedWidth ?: 220f

    override fun prefHeight(width: Float): Float {
        fixedHeight?.let { return it }
        val tw = width - pad * 3 - icon
        var h = 0f
        if (title.isNotEmpty()) h += Paint.lineHeight(theme.fontBody) + 3f
        h += Paint.wrappedHeight(Paint.wrap(text(), theme.fontBody, tw).size.coerceAtLeast(1), theme.fontBody)
        return maxOf(h, icon) + pad * 2
    }

    override fun draw(graphics: GuiGraphicsExtractor) {
        val th = theme
        val tint = colour(th)
        val r = th.radiusMedium
        Paint.rect(graphics, x, y, width, height, Colors.fade(tint, 0.10f), r)
        Paint.outline(graphics, x, y, width, height, Colors.fade(tint, 0.30f), r)

        val ix = x + pad
        val iy = y + pad
        Paint.circle(graphics, ix + icon / 2f, iy + icon / 2f, icon / 2f, Colors.fade(tint, 0.9f))
        drawGlyph(graphics, ix, iy, th)

        val tx = ix + icon + pad
        val tw = width - pad * 3 - icon
        var ty = y + pad
        if (title.isNotEmpty()) {
            Paint.text(graphics, title, tx, ty, tint, th.fontBody, true)
            ty += Paint.lineHeight(th.fontBody) + 3f
        }
        Paint.lines(graphics, Paint.wrap(text(), th.fontBody, tw), tx, ty, th.text, th.fontBody)
    }

    private fun drawGlyph(g: GuiGraphicsExtractor, ix: Float, iy: Float, th: Theme) {
        val c = th.background
        val cx = ix + icon / 2f
        when (type) {
            InfoType.INFO -> {
                Paint.circle(g, cx, iy + 3.3f, 0.95f, c)
                Paint.line(g, cx, iy + 5.6f, cx, iy + 9f, c, 1.6f)
            }
            InfoType.WARNING, InfoType.ERROR -> {
                Paint.line(g, cx, iy + 3f, cx, iy + 6.6f, c, 1.6f)
                Paint.circle(g, cx, iy + 8.9f, 0.95f, c)
            }
            InfoType.SUCCESS -> Paint.check(g, ix + 1f, iy + 1f, icon - 2f, c, 1f, 1.5f)
        }
    }

    private fun colour(th: Theme): Int = when (type) {
        InfoType.INFO -> th.info
        InfoType.WARNING -> th.warning
        InfoType.ERROR -> th.danger
        InfoType.SUCCESS -> th.success
    }
}
