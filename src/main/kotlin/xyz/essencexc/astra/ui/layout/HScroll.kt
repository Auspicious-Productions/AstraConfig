package xyz.essencexc.astra.ui.layout

import foo.starred.cascade.events.impl.MouseEvent
import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import foo.starred.cascade.primitives.base.interfaces.IPrimitiveScrollable
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint

/**
 * A [Row] that scrolls sideways (mouse wheel) when its children don't fit, with fades at the
 * clipped edges. Used for the module tab strip.
 */
open class HScroll : Row(), IPrimitiveScrollable {
    private val motion = Motion(0f, speed = 18f)
    private var applied = 0f
    private var contentWidth = 0f

    init {
        interact = true
        on<MouseEvent.Scroll> {
            val h = this@HScroll
            if (h.maxScroll > 0f) {
                h.scrollTo(h.motion.target - amount.toFloat() * 40f)
                cancel()
            }
        }
    }

    val maxScroll: Float get() = (contentWidth - width).coerceAtLeast(0f)

    fun scrollTo(v: Float) {
        motion.to(v.coerceIn(0f, maxScroll))
    }

    /** Scrolls so [child] is fully visible. */
    fun reveal(child: IPrimitiveElement<*>) {
        val left = child.x + applied - x
        val right = left + child.width
        val s = motion.target
        if (left < s + 8f) scrollTo(left - 8f) else if (right > s + width - 8f) scrollTo(right - width + 8f)
    }

    override fun layout() {
        // Lay out at natural widths (no shrinking), then shift by the scroll offset.
        val list = items()
        contentWidth = list.sumOf { prefW(it, this).toDouble() }.toFloat() + gap * (list.size - 1).coerceAtLeast(0) + px * 2
        val w = width
        width = maxOf(w, contentWidth)
        super.layout()
        width = w
        if (applied > maxScroll) applied = maxScroll
        for (c in list) c.iterateChildren { it.x -= applied }
    }

    override fun find(x: Double, y: Double): IPrimitiveElement<*>? {
        if (!visible || x < this.x || y < this.y || x >= this.x + width || y >= this.y + height) return null
        for (c in children.asReversed()) c.find(x, y)?.let { return it }
        return this
    }

    override fun render(graphics: GuiGraphicsExtractor) {
        if (!visible) return
        val target = motion.get().coerceIn(0f, maxScroll)
        if (target != applied) {
            val d = target - applied
            applied = target
            for (c in children) c.iterateChildren { it.x -= d }
            root.mouseMove(root.mouseX.toDouble(), root.mouseY.toDouble())
        }
        draw(graphics)
        Paint.clip(graphics, x, y, width, height) {
            for (c in children) if (c.visible && c.x + c.width >= x && c.x <= x + width) c.render(graphics)
        }
        // Edge fades hint that there's more.
        val bg = fade?.invoke() ?: return
        if (applied > 1f) Paint.gradient(graphics, x, y, 18f, height, foo.starred.cascade.graphics.geometry.CascadeGeometricColor.horizontal(bg, Colors.fade(bg, 0f)))
        if (applied < maxScroll - 1f) Paint.gradient(graphics, x + width - 18f, y, 18f, height, foo.starred.cascade.graphics.geometry.CascadeGeometricColor.horizontal(Colors.fade(bg, 0f), bg))
    }

    /** Background colour behind the strip, for the edge fades; null = no fades. */
    var fade: (() -> Int)? = null
}
