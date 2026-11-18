package xyz.essencexc.astratest.kotlin

import foo.starred.cascade.constraints.impl.position.CenterPositionConstraint
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.annotations.InfoType
import xyz.essencexc.astra.api.function.ValueFormatter
import xyz.essencexc.astra.api.keybind.KeyBinding
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.core.AstraScreen
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.layout.Align
import xyz.essencexc.astra.ui.layout.Column
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.layout.ScrollPane
import xyz.essencexc.astra.ui.layout.Spacer
import xyz.essencexc.astra.ui.option.ModuleSettingsView
import xyz.essencexc.astra.ui.theme.Theme
import xyz.essencexc.astra.ui.theme.Themes
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Paint
import xyz.essencexc.astra.ui.widgets.Binding
import xyz.essencexc.astra.ui.widgets.Button
import xyz.essencexc.astra.ui.widgets.Checkbox
import xyz.essencexc.astra.ui.widgets.ColorPicker
import xyz.essencexc.astra.ui.widgets.Dropdown
import xyz.essencexc.astra.ui.widgets.InfoBox
import xyz.essencexc.astra.ui.widgets.KeybindButton
import xyz.essencexc.astra.ui.widgets.Label
import xyz.essencexc.astra.ui.widgets.NumberField
import xyz.essencexc.astra.ui.widgets.NumberScale
import xyz.essencexc.astra.ui.widgets.RangeSlider
import xyz.essencexc.astra.ui.widgets.Slider
import xyz.essencexc.astra.ui.widgets.Switch
import xyz.essencexc.astra.ui.widgets.TextField

/**
 * Phase 2 test bed (`/astragallery`): every widget on its own (left) and the real modules of both
 * test mods rendered as settings pages (right). Changes on the right write into the modules'
 * fields; check with `/astrajava dump` or `/astrakt dump`.
 */
class WidgetGallery : AstraScreen("Astra Widget Gallery") {
    private val modules: List<Module> = Astra.mods().flatMap { it.modules() }
    private val moduleIndex = Binding.hold(0)
    private val right = ScrollPane()

    // Playground state.
    private val sw = Binding.hold(true)
    private val cb = Binding.hold(false)
    private val slider = Binding.hold(65.0)
    private val fslider = Binding.hold(1.25)
    private val range = Binding.hold(120.0 to 250.0)
    private val number = Binding.hold(42.0)
    private val drop = Binding.hold(1)
    private val key = Binding.hold(KeyBinding.key(com.mojang.blaze3d.platform.InputConstants.KEY_G))
    private val colour = Binding.hold(0xCC8090FF.toInt())
    private val text = TextField().apply { placeholder = "Type something..."; fixedWidth = 160f }
    private var clicks = 0

    /** Module picker; stays put while the page under it is rebuilt. Declared before init, which uses it. */
    private val selector = Row().apply {
        gap = 8f
        adopt(Label("Module", muted = true))
        adopt(Dropdown(Binding.of({ moduleIndex.get() }, { moduleIndex.set(it); showModule() }), modules.map { "${it.mod?.name ?: "?"} > ${it.name}" }).apply { fixedWidth = 220f })
    }

    init {
        val panel = Column().apply {
            fixedWidth = 880f
            fixedHeight = 500f
            position = CenterPositionConstraint()
            fill = { theme.background }
            border = { theme.border }
            radius = { theme.radiusLarge + 3f }
            shadow = true
        }

        panel.adopt(header())
        panel.adopt(Divider())
        panel.adopt(Row().apply {
            align = Align.STRETCH
            fixedHeight = 500f - 46f - 1f
            adopt(ScrollPane().apply {
                fixedWidth = 400f
                content.padding = 16f
                content.gap = 16f
                playground(content)
            })
            adopt(Divider(vertical = true))
            adopt(right.apply {
                grow = true
                content.padding = 16f
                content.gap = 14f
            })
        })
        add(panel)
        showModule()
    }

    // ---- header -------------------------------------------------------------------------------

    private fun header() = Row().apply {
        fixedHeight = 46f
        paddingX = 16f
        gap = 8f
        adopt(Title())
        adopt(Spacer().apply { grow = true })
        adopt(SmallLabel("Animations"))
        adopt(Switch(Binding.of({ Themes.current.animations }, { Themes.current = Themes.current.copy(animations = it) })))
        adopt(Spacer(6f, 0f))
        adopt(Button("Night", Button.Style.GHOST) { Themes.current = Theme.NIGHT.copy(accent = Themes.current.accent, animations = Themes.current.animations) })
        adopt(Button("Day", Button.Style.GHOST) { Themes.current = Theme.DAY.copy(accent = Themes.current.accent, animations = Themes.current.animations) })
        adopt(Spacer(6f, 0f))
        for (a in Theme.ACCENTS) adopt(AccentDot(a))
    }

    private inner class Title : AstraElement() {
        override fun prefWidth() = Paint.width("Astra", theme.fontHeading, true) + 10f + Paint.width("Widget Gallery", theme.fontBody)
        override fun prefHeight(width: Float) = Paint.lineHeight(theme.fontHeading)
        override fun draw(graphics: GuiGraphicsExtractor) {
            val th = theme
            Paint.text(graphics, "Astra", x, y, accentColor(), th.fontHeading, true)
            val w = Paint.width("Astra", th.fontHeading, true)
            Paint.textMid(graphics, "Widget Gallery", x + w + 10f, y, height, th.textMuted, th.fontBody)
        }
    }

    private class SmallLabel(val text: String) : AstraElement() {
        override fun prefWidth() = Paint.width(text, theme.fontSmall)
        override fun prefHeight(width: Float) = Paint.lineHeight(theme.fontSmall)
        override fun draw(graphics: GuiGraphicsExtractor) = Paint.text(graphics, text, x, y, theme.textMuted, theme.fontSmall)
    }

    private class AccentDot(val colour: Int) : AstraWidget() {
        init {
            fixedWidth = 12f
            fixedHeight = 12f
        }

        override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
            Themes.current = Themes.current.withAccent(colour)
            return true
        }

        override fun onKey(key: Int): Boolean {
            if (!xyz.essencexc.astra.ui.core.Keys.isActivate(key)) return false
            Themes.current = Themes.current.withAccent(colour)
            return true
        }

        override fun paint(g: GuiGraphicsExtractor) {
            val r = 6f + 1.2f * hover.get()
            Paint.circle(g, x + 6f, y + 6f, r, colour)
            if (Themes.current.accent == colour) Paint.circle(g, x + 6f, y + 6f, 2.2f, theme.background)
            focusRing(g, x, y, width, height, 6f)
        }
    }

    private class Divider(val vertical: Boolean = false) : AstraElement() {
        init {
            if (vertical) fixedWidth = 1f else fixedHeight = 1f
        }

        override fun draw(graphics: GuiGraphicsExtractor) = Paint.rect(graphics, x, y, width, height, theme.border)
    }

    // ---- left: every widget on its own ---------------------------------------------------------

    private fun playground(c: Column) {
        c.adopt(card("Toggles",
            row("Switch", "Pill toggle with a squish on press.", Switch(sw)),
            row("Checkbox", "Animated tick.", Checkbox(cb)),
            row("Disabled", "Follows the switch above.", Switch(Binding.hold(true)).apply { enabledWhen = { sw.get() } }),
        ))
        c.adopt(card("Numbers",
            row("Slider", "Drag, click, arrows (Shift ×10), Home/End, or type in the box.", Slider(slider, NumberScale(0.0, 100.0, 1.0, true), ValueFormatter.Percent())),
            row("Decimal slider", null, Slider(fslider, NumberScale(0.0, 5.0, 0.05, false), ValueFormatter.Seconds())),
            row("Range slider", "Grab a knob or the bar. Knobs push each other (min spread 20).", RangeSlider(range, NumberScale(0.0, 1000.0, 10.0, true), 20.0, ValueFormatter.Millis())),
            row("Number", "Up/Down or scroll while focused.", NumberField(number, NumberScale(0.0, 500.0, 0.0, true, 1.0), "blocks")),
        ))
        c.adopt(card("Pickers",
            row("Dropdown", "Up/Down change it while closed.", Dropdown(drop, listOf("Automatic", "Nearest", "Largest", "Furthest", "Random", "Manual", "Closest to cursor", "Off", "Something else"))),
            row("Keybind", "Click, then press a key. Right-click clears.", KeybindButton(key)),
            row("Colour", "Square, hue, alpha, hex, presets.", ColorPicker(colour, true)),
            row("Text", "Ctrl+A/C/X/V, Shift+arrows, Ctrl jumps words.", text),
        ))
        c.adopt(card("Actions and notes",
            row("Button", null, Row().apply {
                gap = 6f
                adopt(Button("Primary", Button.Style.PRIMARY) { clicks++ })
                adopt(Button("Secondary") { clicks++ })
                adopt(Button("Danger", Button.Style.DANGER) { clicks = 0 })
            }),
            full(InfoBox(InfoType.INFO, "Info") { "Buttons clicked $clicks times. Danger resets it." }),
            full(InfoBox(InfoType.WARNING) { "Warning style, for things that need attention." }),
            full(InfoBox(InfoType.SUCCESS) { "Success style." }),
            full(InfoBox(InfoType.ERROR) { "Error style." }),
        ))
        // Live readout of the bindings above.
        c.adopt(object : AstraElement() {
            override fun prefHeight(width: Float) = Paint.wrappedHeight(4, theme.fontSmall)
            override fun draw(graphics: GuiGraphicsExtractor) {
                val (f, t) = range.get()
                Paint.lines(graphics, listOf(
                    "switch=${sw.get()}  checkbox=${cb.get()}  slider=${slider.get()}  decimal=${fslider.get()}",
                    "range=$f..$t  number=${number.get()}  dropdown=${drop.get()}",
                    "key=${xyz.essencexc.astra.ui.core.Keys.describe(key.get())}  colour=${Colors.hex(colour.get(), true)}",
                    "text=\"${text.text}\"",
                ), x, y, theme.textMuted, theme.fontSmall)
            }
        })
    }

    private fun card(title: String, vararg rows: AstraElement) = Column().apply {
        gap = 6f
        adopt(Label(title, bold = true).apply { textSize = { theme.fontTitle } })
        adopt(Column().apply {
            fill = { theme.surface }
            border = { theme.border }
            radius = { theme.radiusLarge }
            paddingY = 3f
            rows.forEach { adopt(it) }
        })
    }

    /** A row like the real option rows, for standalone widgets. */
    private fun row(title: String, desc: String?, widget: AstraElement): AstraElement = object : Row() {
        init {
            paddingX = 10f
            paddingY = 7f
            gap = 12f
            adopt(Column().apply {
                grow = true
                gap = 3f
                adopt(Label(title))
                if (desc != null) adopt(Label(desc, muted = true).apply { textSize = { theme.fontSmall } })
            })
            adopt(widget)
        }
    }

    private fun full(e: AstraElement): AstraElement = Column().apply {
        paddingX = 10f
        paddingY = 6f
        adopt(e)
    }

    // ---- right: real modules -------------------------------------------------------------------


    private fun showModule() {
        // Keep the selector (it may be mid-click); replace everything under it.
        for (c in right.content.children.toList()) if (c !== selector) right.content.disown(c)
        if (selector.parent == null) right.content.adopt(selector)
        val module = modules.getOrNull(moduleIndex.get()) ?: return
        right.content.adopt(ModuleHeader(module))
        right.content.adopt(ModuleSettingsView(module))
        right.scrollTo(0f, animate = false)
    }

    private class ModuleHeader(val module: Module) : Row() {
        init {
            gap = 10f
            align = Align.CENTER
            accent = module.accent.takeIf { it != 0 }
            adopt(Column().apply {
                grow = true
                gap = 4f
                adopt(Label(module.name, bold = true).apply { textSize = { theme.fontHeading } })
                if (module.description.isNotEmpty()) adopt(Label(module.description, muted = true))
            })
            module.master?.let { adopt(Switch(Binding.of({ module.isEnabled() }, { if (it) module.enable() else module.disable() }))) }
        }
    }
}
