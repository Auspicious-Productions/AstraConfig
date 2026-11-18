package xyz.essencexc.astra.ui.menu

import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.core.OptionSpec
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.layout.Column
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint
import xyz.essencexc.astra.ui.widgets.Binding
import xyz.essencexc.astra.ui.widgets.Label
import xyz.essencexc.astra.ui.widgets.Switch

/** Holds one child at full size and runs [onFrame] every frame before it's drawn. */
internal class Frame(child: AstraElement, private val onFrame: () -> Unit) : AstraElement() {
    init {
        adopt(child)
    }

    override fun layout() {
        dirty = false
        for (c in children) (c as? AstraElement)?.place(x, y, width, height)
    }

    override fun draw(graphics: GuiGraphicsExtractor) = onFrame()
}

/** A bold heading followed by a line, with optional muted text after it (e.g. a count). */
internal class HeadingLine(var text: String, var trailing: () -> String = { "" }) : AstraElement() {
    override fun prefHeight(width: Float) = Paint.lineHeight(theme.fontTitle) + 2f

    override fun draw(graphics: GuiGraphicsExtractor) {
        val th = theme
        Paint.text(graphics, text, x + 2f, y, th.text, th.fontTitle, true)
        var lx = x + 2f + Paint.width(text, th.fontTitle, true) + 6f
        val t = trailing()
        if (t.isNotEmpty()) {
            Paint.textMid(graphics, t, lx, y, height, accent ?: th.textFaint, th.fontSmall)
            lx += Paint.width(t, th.fontSmall) + 6f
        }
        Paint.rect(graphics, lx + 2f, y + height / 2f, (x + width - lx - 2f).coerceAtLeast(0f), 1f, th.border)
    }
}

/** A rounded card stacking its children with dividers between them. */
internal open class Card : Column() {
    init {
        fill = { theme.surface }
        border = { theme.border }
        radius = { theme.radiusLarge }
        paddingY = 3f
    }

    override fun draw(graphics: GuiGraphicsExtractor) {
        super.draw(graphics)
        val shown = children.filter { it.visible }
        for (i in 0 until shown.size - 1) {
            val r = shown[i]
            Paint.rect(graphics, x + 10f, r.y + r.height, width - 20f, 1f, theme.border)
        }
    }
}

/** Title and description on the left, a widget on the right: like an option row, for menu settings. */
internal class SettingRow(title: String, description: String?, widget: AstraElement) : Row() {
    init {
        paddingX = 10f
        paddingY = 8f
        gap = 14f
        adopt(Column().apply {
            grow = true
            gap = 3f
            adopt(Label(title))
            if (!description.isNullOrEmpty()) adopt(Label(description, muted = true).apply { textSize = { theme.fontSmall } })
        })
        adopt(widget)
    }
}

/** The square monogram (or first letter) tile used for modules. */
internal fun drawModuleIcon(g: GuiGraphicsExtractor, e: AstraElement, module: Module, x: Float, y: Float, s: Float) {
    val th = e.theme
    val accent = module.accent.takeIf { it != 0 } ?: e.accentColor()
    Paint.rect(g, x, y, s, s, Colors.fade(accent, 0.16f), th.radiusMedium)
    Paint.outline(g, x, y, s, s, Colors.fade(accent, 0.4f), th.radiusMedium)
    // The module's icon (PNG or SVG) inside the tile, else its first letter.
    val pad = s * 0.18f
    if (!xyz.essencexc.astra.ui.util.Images.draw(g, module.icon, x + pad, y + pad, s - pad * 2, s - pad * 2)) {
        Paint.textCentered(g, module.name.take(1).uppercase(), x + s / 2f, y, s, accent, s * 0.42f, true)
    }
}

/**
 * A module on the Modules page: icon, name, master switch, two lines of description and a
 * settings count. Click or Enter opens it; the switch toggles it.
 */
internal class ModuleCard(val module: Module, private val open: () -> Unit) : AstraWidget() {
    private val toggle: Switch? = module.master?.let {
        Switch(Binding.of({ module.isEnabled() }, { on -> if (on) module.enable() else module.disable() }))
    }
    private val settings = module.options.count { it.spec != OptionSpec.MasterToggleSpec && it.spec !is OptionSpec.InfoSpec }
    private val lift = Motion()

    init {
        fixedHeight = 92f
        accent = module.accent.takeIf { it != 0 }
        toggle?.let { adopt(it) }
    }

    override fun prefWidth() = fixedWidth ?: 190f

    override fun layout() {
        dirty = false
        toggle?.let { it.place(x + width - 12f - it.prefWidth(), y + 14f, it.prefWidth(), it.prefHeight(0f)) }
    }

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean = true

    override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
        if (wasPressed && button == 0 && hit(mx, my)) open()
        return true
    }

    override fun onKey(key: Int): Boolean {
        if (!Keys.isActivate(key)) return false
        open()
        return true
    }

    override fun paint(g: GuiGraphicsExtractor) {
        val th = theme
        val accent = accentColor()
        // Hover counts while the cursor is on the card's switch too.
        val over = hovered || toggle?.hovered == true
        lift.to(over)
        val h = lift.get()
        val on = module.isEnabled() && module.master != null
        val r = th.radiusLarge

        Paint.rect(g, x, y, width, height, Colors.mix(Colors.mix(th.surface, accent, 0.03f + 0.05f * h), accent, if (on) 0.05f else 0f), r)
        Paint.outline(g, x, y, width, height, Colors.mix(th.border, Colors.fade(accent, 0.65f), h * 0.8f + if (on) 0.2f else 0f), r)
        focusRing(g, x, y, width, height, r)

        drawModuleIcon(g, this, module, x + 12f, y + 12f, 24f)
        val nameW = width - 12f - 24f - 10f - 12f - (toggle?.prefWidth()?.plus(8f) ?: 0f)
        Paint.text(g, Paint.truncate(module.name, th.fontTitle, nameW, true), x + 46f, y + 13f, th.text, th.fontTitle, true)
        val status = when {
            module.master == null -> "Always on"
            on -> "Enabled"
            else -> "Disabled"
        }
        Paint.text(g, status, x + 46f, y + 15f + Paint.lineHeight(th.fontTitle), if (on) accent else th.textFaint, th.fontSmall - 0.5f)

        // Two lines of description, ellipsised.
        val lines = Paint.wrap(module.description, th.fontSmall, width - 24f)
        val shown = lines.take(2).toMutableList()
        if (lines.size > 2) shown[1] = Paint.truncate(shown[1] + " ...", th.fontSmall, width - 24f)
        Paint.lines(g, shown, x + 12f, y + 46f, th.textMuted, th.fontSmall)

        Paint.text(g, "$settings settings", x + 12f, y + height - 8f - Paint.lineHeight(th.fontSmall - 0.5f), th.textFaint, th.fontSmall - 0.5f)
        Icons.draw(g, Icons.Kind.CHEVRON_RIGHT, x + width - 20f, y + height - 18f, 8f, Colors.mix(th.textFaint, accent, h), th.surface)
    }
}
