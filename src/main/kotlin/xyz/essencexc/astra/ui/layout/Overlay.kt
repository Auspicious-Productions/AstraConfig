package xyz.essencexc.astra.ui.layout

import com.mojang.blaze3d.platform.InputConstants
import foo.starred.cascade.constraints.impl.position.FixedPositionConstraint
import foo.starred.cascade.constraints.impl.size.FillSizeConstraint
import foo.starred.cascade.events.impl.KeyEvent
import foo.starred.cascade.events.impl.MouseEvent
import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.core.FocusManager
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint

/**
 * The top layer: dropdown menus, the colour picker and other popups. Always the scene's last child,
 * so it draws last and is hit-tested first. While a popup is open, a click anywhere else closes it.
 */
class Overlay : AstraElement() {
    init {
        size = FillSizeConstraint()
        position = FixedPositionConstraint(0, 0)

        on<MouseEvent.Press> {
            this@Overlay.closeAll()
            cancel()
        }
        on<KeyEvent.Press> {
            val o = this@Overlay
            when (key) {
                InputConstants.KEY_ESCAPE -> o.closeAll()
                InputConstants.KEY_TAB -> FocusManager.move(o, Keys.shift())
                else -> return@on
            }
            cancel()
        }
    }

    val isOpen: Boolean
        get() = children.isNotEmpty()

    val popups: List<Popup>
        get() = children.filterIsInstance<Popup>()

    fun open(popup: Popup) {
        if (popup.parent === this) return
        adopt(popup)
        interact = true
        popup.appear.snap(0f).to(1f)
    }

    fun close(popup: Popup) {
        if (popup.parent !== this) return
        val owner = popup.owner
        disown(popup)
        interact = isOpen
        popup.onClosed?.invoke()
        // Give focus back to whatever opened it, unless focus already moved on elsewhere.
        val focused = root.focused
        if (owner != null && (focused == null || focused === this || focused.isIn(popup))) root.focused = owner
    }

    fun closeAll() {
        for (p in popups.asReversed()) close(p)
    }

    override fun layout() {
        dirty = false
        for (p in popups) p.position(width, height)
    }

    override fun render(graphics: GuiGraphicsExtractor) {
        // Follow anchors that moved (scrolling) without a layout pass.
        if (popups.any { it.anchorMoved() }) root.dirty()
        super.render(graphics)
        drawTooltip(graphics)
    }

    private var tip: String? = null
    private var tipX = 0f
    private var tipY = 0f

    /** Shows [text] near (x, y) this frame. Call it every frame while it should stay up. */
    fun tooltip(text: String, x: Float, y: Float) {
        tip = text
        tipX = x
        tipY = y
    }

    private fun drawTooltip(g: GuiGraphicsExtractor) {
        val text = tip ?: return
        tip = null
        val th = theme
        val size = th.fontSmall + 0.5f
        val lines = Paint.wrap(text, size, 220f)
        val w = (lines.maxOfOrNull { Paint.width(it, size) } ?: 0f) + 12f
        val h = Paint.wrappedHeight(lines.size, size) + 10f
        val x = (tipX + 10f).coerceAtMost(width - w - 4f).coerceAtLeast(4f)
        val y = (tipY + 12f).let { if (it + h > height - 4f) tipY - h - 6f else it }.coerceAtLeast(4f)
        Paint.shadow(g, x, y, w, h, th.radiusSmall, 10f, 0x55000000)
        Paint.rect(g, x, y, w, h, th.surfaceRaised, th.radiusSmall)
        Paint.outline(g, x, y, w, h, th.borderStrong, th.radiusSmall)
        Paint.lines(g, lines, x + 6f, y + 5f, th.text, size)
    }

    override fun draw(graphics: GuiGraphicsExtractor) {}

    companion object {
        /** The overlay of the scene [root] belongs to, if it's an [xyz.essencexc.astra.ui.core.AstraScreen]. */
        @JvmStatic
        fun of(root: IPrimitiveElement<*>): Overlay? = root.root.children.lastOrNull { it is Overlay } as? Overlay

        private fun IPrimitiveElement<*>.isIn(ancestor: IPrimitiveElement<*>): Boolean {
            var e: IPrimitiveElement<*>? = this
            while (e != null) {
                if (e === ancestor) return true
                e = e.parent
            }
            return false
        }
    }
}

/**
 * A floating panel shown in the [Overlay], placed under (or above, if there's no room) its [anchor].
 * It's a [Column], so add content to it directly.
 */
open class Popup(
    /** What it hangs off; null = centred on screen. */
    val anchor: IPrimitiveElement<*>?,
    /** Gets focus back when the popup closes. */
    val owner: IPrimitiveElement<*>? = anchor,
) : Column() {
    var onClosed: (() -> Unit)? = null

    /** Match the anchor's width at least. */
    var matchAnchorWidth: Boolean = false

    /** Horizontal alignment to the anchor. */
    var alignEnd: Boolean = false

    var spacing: Float = 4f

    internal val appear = Motion(0f, speed = 22f)
    private var lastAnchorX = Float.NaN
    private var lastAnchorY = Float.NaN
    private var above = false

    init {
        interact = true
        fill = { theme.surfaceRaised }
        border = { theme.borderStrong }
        radius = { theme.radiusMedium }
        shadow = true
        padding = 4f
    }

    fun overlay(): Overlay? = parent as? Overlay

    fun close() {
        overlay()?.close(this)
    }

    internal fun anchorMoved(): Boolean {
        val a = anchor ?: return false
        return a.x != lastAnchorX || a.y != lastAnchorY
    }

    internal fun position(screenW: Float, screenH: Float) {
        val a = anchor
        var w = prefWidth()
        if (a != null && matchAnchorWidth) w = maxOf(w, a.width)
        w = w.coerceAtMost(screenW - 16f)
        val h = prefHeight(w).coerceAtMost(screenH - 16f)

        if (a == null) {
            place((screenW - w) / 2f, (screenH - h) / 2f, w, h)
            return
        }

        lastAnchorX = a.x
        lastAnchorY = a.y
        val below = a.y + a.height + spacing
        above = below + h > screenH - 8f && a.y - spacing - h >= 8f
        val py = if (above) a.y - spacing - h else below.coerceAtMost(screenH - 8f - h)
        val px = (if (alignEnd) a.x + a.width - w else a.x).coerceIn(8f, (screenW - 8f - w).coerceAtLeast(8f))
        place(px, py, w, h)
    }

    override fun render(graphics: GuiGraphicsExtractor) {
        if (!visible) return
        val t = appear.get()
        if (t >= 0.999f) {
            super.render(graphics)
            return
        }
        // Unroll from the anchor side.
        val h = height * (0.25f + 0.75f * t)
        val top = if (above) y + height - h else y
        Paint.clip(graphics, x - 20f, top, width + 40f, h + 20f) { super.render(graphics) }
    }
}
