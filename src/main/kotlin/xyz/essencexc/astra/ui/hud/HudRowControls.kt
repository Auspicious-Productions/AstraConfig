package xyz.essencexc.astra.ui.hud

import xyz.essencexc.astra.api.hud.HudElement
import xyz.essencexc.astra.api.hud.HudLayout
import xyz.essencexc.astra.core.Option
import xyz.essencexc.astra.ui.layout.Align
import xyz.essencexc.astra.ui.layout.Overlay
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.widgets.Binding
import xyz.essencexc.astra.ui.widgets.Button
import xyz.essencexc.astra.ui.widgets.Switch

/**
 * The controls on a HUD's settings row: live thumbnail, Style, Edit and a show/hide switch.
 * Adapts to the space it gets (e.g. the Large menu size): first drops the thumbnail, then narrows
 * the buttons, so nothing spills out of the row.
 */
internal class HudRowControls(option: Option<HudLayout>, element: HudElement) : Row() {
    private val thumbnail = HudPreview(element).apply { fixedWidth = 84f; fixedHeight = 36f }
    private val style = Button("Style", Button.Style.GHOST)
    private val edit = Button("Edit", Button.Style.GHOST) { HudEditorScreen.open(option.module.mod, element) }
    private val toggle = Switch(Binding.of({ option.get().shown }, { option.set(option.get().withShown(it)) }))

    init {
        gap = GAP
        align = Align.CENTER
        style.action = { Overlay.of(style)?.open(HudStylePopup(style, option, element)) }
        adopt(thumbnail)
        adopt(style)
        adopt(edit)
        adopt(toggle)
    }

    private fun buttonsWidth() = style.prefWidth() + edit.prefWidth() + toggle.prefWidth() + GAP * 2

    override fun prefWidth(): Float = fixedWidth ?: (thumbnail.prefWidth() + GAP + buttonsWidth())

    override fun layout() {
        // Reset any squeezing from a previous, narrower layout before measuring.
        style.fixedWidth = null
        edit.fixedWidth = null
        val showThumb = width >= thumbnail.prefWidth() + GAP + buttonsWidth()
        if (thumbnail.visible != showThumb) thumbnail.visible = showThumb
        if (!showThumb && width < buttonsWidth()) {
            val each = ((width - toggle.prefWidth() - GAP * 2) / 2f).coerceAtLeast(20f)
            style.fixedWidth = each
            edit.fixedWidth = each
        }
        super.layout()
    }

    private companion object {
        const val GAP = 6f
    }
}
