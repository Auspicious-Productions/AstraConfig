package xyz.essencexc.astra.ui.hud

import foo.starred.cascade.graphics.geometry.CascadeGeometricColor
import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.hud.HudElement
import xyz.essencexc.astra.api.hud.HudFont
import xyz.essencexc.astra.api.hud.HudLayout
import xyz.essencexc.astra.api.hud.HudStyle
import xyz.essencexc.astra.core.HudRegistry
import xyz.essencexc.astra.core.Option
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.layout.Align
import xyz.essencexc.astra.ui.layout.Popup
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Paint
import xyz.essencexc.astra.ui.widgets.Binding
import xyz.essencexc.astra.ui.widgets.Button
import xyz.essencexc.astra.ui.widgets.ColorPicker
import xyz.essencexc.astra.ui.widgets.Dropdown
import xyz.essencexc.astra.ui.widgets.Label
import xyz.essencexc.astra.ui.widgets.Switch

/** Draws a HUD element (in preview mode) scaled to fit its box, on a dark game-like backdrop. */
internal class HudPreview(private val element: HudElement) : AstraElement() {
    init {
        fixedHeight = 84f
    }

    override fun draw(graphics: GuiGraphicsExtractor) {
        val th = theme
        Paint.gradient(graphics, x, y, width, height, CascadeGeometricColor.vertical(0xFF2B3A4E.toInt(), 0xFF1B2330.toInt()), th.radiusMedium)
        Paint.outline(graphics, x, y, width, height, th.border, th.radiusMedium)
        val w = element.width().coerceAtLeast(1f)
        val h = element.height().coerceAtLeast(1f)
        val s = minOf(2f, (width - 16f) / w, (height - 12f) / h).coerceAtLeast(0.1f)
        Paint.clip(graphics, x, y, width, height) {
            HudRegistry.draw(graphics, element, x + (width - w * s) / 2f, y + (height - h * s) / 2f, s, true)
        }
    }
}

/**
 * The HUD's Style popup: a live preview plus background, colours, font, shadow and hide rules.
 * Every change is saved through the option, like any other setting.
 */
internal class HudStylePopup(anchor: IPrimitiveElement<*>, private val option: Option<HudLayout>, element: HudElement) : Popup(anchor) {
    private fun style(): HudStyle = option.get().style
    private fun update(f: (HudStyle) -> HudStyle) = option.set(option.get().withStyle(f(style())))

    init {
        alignEnd = true
        padding = 10f
        gap = 6f
        fixedWidth = 260f

        adopt(Label("${element.title} style", bold = true))
        adopt(HudPreview(element))

        adopt(row("Background", Switch(Binding.of({ style().background }, { v -> update { it.copy(background = v) } }))))
        adopt(row("Background colour", colour({ style().backgroundColor }, { v -> update { it.copy(backgroundColor = v) } }, { Colors.fade(theme.surface, 0.88f) }, alpha = true)))
        adopt(row("Text colour", colour({ style().textColor }, { v -> update { it.copy(textColor = v) } }, { theme.text })))
        adopt(row("Accent", colour({ style().accent }, { v -> update { it.copy(accent = v) } }, { element.module?.accent?.takeIf { it != 0 } ?: theme.accent })))
        adopt(row("Font", Dropdown(
            Binding.of({ style().font.ordinal }, { i -> update { it.copy(font = HudFont.entries[i]) } }),
            listOf("Astra", "Minecraft"),
        ).apply { fixedWidth = 100f }))
        adopt(row("Text shadow", Switch(Binding.of({ style().shadow }, { v -> update { it.copy(shadow = v) } }))))
        adopt(row("Hide while chat is open", Switch(Binding.of({ style().hideWithChat }, { v -> update { it.copy(hideWithChat = v) } }))))
        adopt(row("Hide with F3 open", Switch(Binding.of({ style().hideWithDebug }, { v -> update { it.copy(hideWithDebug = v) } }))))
        adopt(Row().apply {
            gap = 6f
            adopt(Button("Reset style", Button.Style.GHOST) { update { element.defaultLayout.style } }.apply { grow = true })
            adopt(Button("Done", Button.Style.PRIMARY) { close() }.apply { grow = true })
        })
    }

    private fun row(title: String, widget: AstraElement) = Row().apply {
        gap = 8f
        align = Align.CENTER
        adopt(Label(title).apply { grow = true; textSize = { theme.fontBody - 0.5f } })
        adopt(widget)
    }

    /**
     * A colour picker where 0 means "use the default" ([fallback]): shows the fallback colour, and
     * a small "×" button clears a custom colour back to 0.
     */
    private fun colour(get: () -> Int, set: (Int) -> Unit, fallback: () -> Int, alpha: Boolean = false) = Row().apply {
        gap = 3f
        align = Align.CENTER
        adopt(xyz.essencexc.astra.ui.widgets.IconButton(xyz.essencexc.astra.ui.widgets.IconButton.Glyph.REMOVE, { get() != 0 }) { set(0) }.apply {
            hideWhenDisabled = true
            tooltip = "Back to the default colour"
            fixedWidth = 14f
        })
        adopt(ColorPicker(Binding.of({ get().takeIf { it != 0 } ?: fallback() }, set), alpha))
    }

}
