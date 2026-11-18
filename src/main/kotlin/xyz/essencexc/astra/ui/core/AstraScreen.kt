package xyz.essencexc.astra.ui.core

import com.mojang.blaze3d.platform.InputConstants
import foo.starred.cascade.constraints.impl.position.FixedPositionConstraint
import foo.starred.cascade.constraints.impl.size.FillSizeConstraint
import foo.starred.cascade.events.impl.KeyEvent
import foo.starred.cascade.events.impl.MouseEvent
import foo.starred.cascade.graphics.geometry.CascadeGeometricResolution
import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import foo.starred.cascade.screen.CascadeScreen
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.layout.Overlay

/**
 * Base screen for Astra UIs. Uses a fixed virtual resolution (1920x1080 at 2x, so a 960x540 canvas)
 * so it looks the same at every GUI scale, and sets up the [Backdrop] (focus fallback, Tab handling)
 * and the [Overlay] (popups).
 *
 * Add content with [add]; it keeps the overlay on top.
 */
open class AstraScreen(title: String, resolution: CascadeGeometricResolution = CascadeGeometricResolution.FHD.of(2f)) : CascadeScreen(title, resolution) {
    val backdrop = Backdrop()
    val overlay = Overlay()

    init {
        scene.adopt(backdrop)
        scene.adopt(overlay)
        scene.focused = backdrop
    }

    /** Adds [element] to the scene below the overlay. */
    fun add(element: IPrimitiveElement<*>): AstraScreen {
        scene.adopt(element)
        scene.adopt(overlay) // re-adopt moves it back to the end
        return this
    }

    override fun isPauseScreen(): Boolean = false

    override fun removed() {
        Keys.stopTextInput()
        super.removed()
    }
}

/** Full-screen layer under everything: catches clicks on empty space and handles Tab from there. */
class Backdrop : AstraElement() {
    init {
        interact = true
        size = FillSizeConstraint()
        position = FixedPositionConstraint(0, 0)

        on<MouseEvent.Press> {
            FocusManager.keyboard = false
        }
        on<KeyEvent.Press> {
            if (key == InputConstants.KEY_TAB) {
                FocusManager.move(this@Backdrop, Keys.shift())
                cancel()
            }
        }
    }

    override fun draw(graphics: GuiGraphicsExtractor) {}
}
