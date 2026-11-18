package xyz.essencexc.astra.ui.core

import foo.starred.cascade.graphics.geometry.CascadeGeometricColor
import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import xyz.essencexc.astra.ui.theme.Theme
import xyz.essencexc.astra.ui.theme.Themes

/**
 * Base for everything Astra draws. Adds a simple measuring protocol on top of Cascade's
 * constraints so [xyz.essencexc.astra.ui.layout.Column] and friends can stack elements:
 * [prefWidth] and [prefHeight] (height for a given width, so text can wrap).
 *
 * Inside an Astra layout the layout sets x/y/width/height directly. Placed straight into a Cascade
 * container it behaves like any primitive: `size`/`position` constraints win, else it uses its
 * preferred size.
 */
abstract class AstraElement : IPrimitiveElement<AstraElement>() {
    override var x: Float = 0f
    override var y: Float = 0f
    override var width: Float = 0f
    override var height: Float = 0f
    override var color: CascadeGeometricColor = CascadeGeometricColor.WHITE

    /** Fixed preferred size; null = let the element decide. */
    var fixedWidth: Float? = null
    var fixedHeight: Float? = null

    /** Take all the space the parent layout offers on its cross axis (Column) or main axis (Row). */
    var grow: Boolean = false

    /** In a [xyz.essencexc.astra.ui.layout.Column]: take the height left over after the other children. */
    var fillHeight: Boolean = false

    /** Accent override for this subtree, e.g. a module's colour. Null = inherit. */
    var accent: Int? = null

    init {
        interact = false
    }

    open fun prefWidth(): Float = fixedWidth ?: 0f
    open fun prefHeight(width: Float): Float = fixedHeight ?: 0f

    val theme: Theme get() = Themes.current

    /** The nearest [accent] up the tree, else the theme's. */
    fun accentColor(): Int {
        var e: IPrimitiveElement<*>? = this
        while (e != null) {
            if (e is AstraElement) e.accent?.let { return it }
            e = e.parent
        }
        return theme.accent
    }

    /** Sets the bounds and lays out children. Used by Astra's layouts. */
    fun place(x: Float, y: Float, width: Float, height: Float) {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
        layout()
    }

    override fun constrain(parent: IPrimitiveElement<*>) {
        val s = size
        if (s != null) {
            width = s._width(this, parent)
            height = s._height(this, parent)
        } else {
            width = prefWidth()
            height = prefHeight(width)
        }
        position?.let {
            x = it._x(this, parent) + offset.x
            y = it._y(this, parent) + offset.y
        }
    }

    /** True when this and every ancestor is visible. */
    fun shown(): Boolean {
        var e: IPrimitiveElement<*>? = this
        while (e != null) {
            if (!e.visible) return false
            e = e.parent
        }
        return true
    }

    fun hit(mx: Double, my: Double): Boolean = mx >= x && my >= y && mx < x + width && my < y + height
}
