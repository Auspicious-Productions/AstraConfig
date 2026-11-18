package xyz.essencexc.astra.ui.layout

import com.mojang.blaze3d.platform.cursor.CursorTypes
import foo.starred.cascade.events.impl.MouseEvent
import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import foo.starred.cascade.primitives.base.interfaces.IPrimitiveScrollable
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint

/**
 * A vertically scrolling viewport around a [Column] ([content]).
 *
 * Smooth (eased) scrolling, a draggable scrollbar that appears on hover, and [reveal] for jumping to
 * an element. Scrolling moves the children's real positions instead of translating the render, so
 * mouse coordinates stay correct for everything inside (sliders, text fields...).
 */
class ScrollPane : AstraElement(), IPrimitiveScrollable {
    val content = Column()

    /** Pixels per wheel notch. */
    var step: Float = 36f

    /** Space kept free on the right for the scrollbar. */
    var gutter: Float = 8f

    private val motion = Motion(0f, speed = 18f)
    private var applied = 0f
    private val barHover = Motion()
    private var dragging = false
    private var grab = 0f

    init {
        interact = true
        adopt(content)

        on<MouseEvent.Scroll> {
            val pane = this@ScrollPane
            if (pane.maxScroll > 0f) {
                pane.scrollTo(pane.motion.target - amount.toFloat() * pane.step)
                cancel()
            }
        }

        on<MouseEvent.Press> {
            val pane = this@ScrollPane
            if (xyz.essencexc.astra.ui.core.Keys.button(button) == 0 && pane.overBar(x) && pane.maxScroll > 0f) {
                val (ty, th) = pane.thumb()
                pane.dragging = true
                pane.grab = if (y >= ty && y < ty + th) (y - ty).toFloat() else th / 2f
                cancel()
            }
        }

        on<MouseEvent.Release> { this@ScrollPane.dragging = false }
    }

    val maxScroll: Float
        get() = (content.height - height).coerceAtLeast(0f)

    /** Current scroll target in pixels. */
    val scroll: Float
        get() = motion.target

    fun scrollTo(value: Float, animate: Boolean = true) {
        val v = value.coerceIn(0f, maxScroll)
        if (animate) motion.to(v) else motion.snap(v)
    }

    /** Scrolls the least amount needed to show [element] (with a little margin). */
    fun reveal(element: IPrimitiveElement<*>, margin: Float = 8f) {
        val top = element.y - content.y
        val bottom = top + element.height
        val s = motion.target
        when {
            top - margin < s -> scrollTo(top - margin)
            bottom + margin > s + height -> scrollTo(bottom + margin - height)
        }
    }

    /** Scrolls so [element] sits at the top. */
    fun scrollToTop(element: IPrimitiveElement<*>, margin: Float = 8f) = scrollTo(element.y - content.y - margin)

    override fun prefWidth(): Float = fixedWidth ?: (content.prefWidth() + gutter)
    override fun prefHeight(width: Float): Float = fixedHeight ?: 0f

    override fun layout() {
        dirty = false
        val cw = width - gutter
        val ch = content.prefHeight(cw)
        content.place(x, y - applied, cw, ch)
        if (applied > maxScroll) {
            motion.snap(maxScroll)
            shift(maxScroll)
        }
    }

    private fun shift(to: Float) {
        val d = to - applied
        if (d == 0f) return
        applied = to
        content.iterateChildren { it.y -= d }
    }

    private fun overBar(mx: Double) = mx >= x + width - gutter

    /** Inset of the scrollbar track from the top and bottom, so it clears rounded panel corners. */
    var barInset: Float = 6f

    private val trackTop get() = y + barInset
    private val trackHeight get() = (height - barInset * 2).coerceAtLeast(1f)

    private fun thumb(): Pair<Float, Float> {
        val th = (trackHeight * height / content.height.coerceAtLeast(1f)).coerceIn(18f, trackHeight)
        val ty = trackTop + (trackHeight - th) * (applied / maxScroll.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return ty to th
    }

    override fun find(x: Double, y: Double): IPrimitiveElement<*>? {
        if (!visible || !hit(x, y)) return null
        if (overBar(x) && maxScroll > 0f) return this
        return content.find(x, y) ?: this
    }

    override fun render(graphics: GuiGraphicsExtractor) {
        if (!visible) return

        if (dragging) {
            val (_, th) = thumb()
            val track = (trackHeight - th).coerceAtLeast(1f)
            motion.snap(((mouseY - grab - trackTop) / track * maxScroll).coerceIn(0f, maxScroll))
        }

        val target = motion.get().coerceIn(0f, maxScroll)
        if (target != applied) {
            shift(target)
            root.mouseMove(root.mouseX.toDouble(), root.mouseY.toDouble())
        }

        Paint.clip(graphics, x, y, width, height) {
            content.draw(graphics)
            val top = y - 40f
            val bottom = y + height + 40f
            for (c in content.children) {
                if (!c.visible || c.y + c.height < top || c.y > bottom) continue
                c.render(graphics)
            }
        }

        if (maxScroll > 0f) {
            val over = hovered && overBar(mouseX.toDouble()) || dragging
            barHover.to(if (over) 1f else if (hit(mouseX.toDouble(), mouseY.toDouble())) 0.55f else 0.25f)
            val t = barHover.get()
            val (ty, th) = thumb()
            val w = 2f + 1.5f * t
            Paint.rect(graphics, x + width - gutter / 2f - w / 2f, ty, w, th, Colors.fade(theme.textFaint, 0.35f + 0.65f * t), w / 2f)
        }
        cursor = if (hovered && overBar(mouseX.toDouble()) && maxScroll > 0f) CursorTypes.POINTING_HAND else null
    }
}
