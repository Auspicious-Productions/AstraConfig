package xyz.essencexc.astra.ui.core

import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import xyz.essencexc.astra.ui.layout.Overlay
import xyz.essencexc.astra.ui.layout.ScrollPane

/** Tab / Shift+Tab traversal and the "was the last focus change from the keyboard" flag. */
object FocusManager {
    /** True after Tab, false after a click. Focus rings only show in keyboard mode. */
    @JvmStatic
    var keyboard: Boolean = false

    /** Moves focus to the next (or previous) focusable widget after [from]. */
    @JvmStatic
    fun move(from: IPrimitiveElement<*>, backwards: Boolean) {
        keyboard = true
        val root = from.root
        val overlay = Overlay.of(root)
        val scope: IPrimitiveElement<*> = if (overlay != null && overlay.isOpen) overlay else root

        val list = ArrayList<AstraWidget>()
        scope.iterateChildren {
            if (it is AstraWidget && it.focusable && it.isEnabled && it.shown()) {
                if (scope === root && overlay != null && it.isInside(overlay)) return@iterateChildren
                list += it
            }
        }
        if (list.isEmpty()) return

        val current = list.indexOf(owner(root.focused))
        val next = when {
            current < 0 -> if (backwards) list.last() else list.first()
            backwards -> list[(current - 1 + list.size) % list.size]
            else -> list[(current + 1) % list.size]
        }
        root.focused = next
        reveal(next)
    }

    /** Scrolls every scroll pane above [element] so it's visible. */
    @JvmStatic
    fun reveal(element: IPrimitiveElement<*>) {
        var p = element.parent
        while (p != null) {
            if (p is ScrollPane) p.reveal(element)
            p = p.parent
        }
    }

    /** What gets focus when a widget lets go of it: the screen's backdrop, if there is one. */
    @JvmStatic
    fun fallback(root: IPrimitiveElement<*>): IPrimitiveElement<*>? = root.children.firstOrNull { it is Backdrop }

    private fun owner(e: IPrimitiveElement<*>?): AstraWidget? {
        var c = e
        while (c != null) {
            if (c is AstraWidget) return c
            c = c.parent
        }
        return null
    }

    private fun IPrimitiveElement<*>.isInside(ancestor: IPrimitiveElement<*>): Boolean {
        var p = parent
        while (p != null) {
            if (p === ancestor) return true
            p = p.parent
        }
        return false
    }
}
