package xyz.essencexc.astra.ui.menu

import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.layout.Align
import xyz.essencexc.astra.ui.layout.Column
import xyz.essencexc.astra.ui.layout.HScroll
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.layout.ScrollPane
import xyz.essencexc.astra.ui.layout.Spacer
import xyz.essencexc.astra.ui.option.ModuleSettingsView
import xyz.essencexc.astra.ui.option.SectionView
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint
import xyz.essencexc.astra.ui.widgets.Binding
import xyz.essencexc.astra.ui.widgets.Label
import xyz.essencexc.astra.ui.widgets.Switch

/**
 * One module: a tab strip of its category's modules (then the other categories), a left column
 * with its name, description, master switch and section links (scroll-spy), and its settings.
 */
internal class ModulePage(screen: ConfigScreen, val module: Module, private var focusOption: String? = null) : Page(screen) {
    val category = screen.mod.categories.first { module in it.modules }

    override val key = MODULES
    override val title get() = module.name
    override val subtitle get() = "${category.name}  ›  ${module.name}"
    override val searchPlaceholder get() = "Filter ${module.name}"
    override fun place() = MenuState.Place(MODULES, module.id)

    private val view = ModuleSettingsView(module)
    private val pane = ScrollPane()
    private val tabs = HScroll()
    private val links = ArrayList<SectionLink>()
    private var activeSection: SectionView? = null
    private var revealedTab = false
    private val empty = Label("No settings match.", muted = true).apply { visible = false }

    override fun build(): AstraElement {
        // Tabs: back to all modules, this category's modules, then the other categories.
        tabs.apply {
            fixedHeight = 36f
            paddingX = 12f
            gap = 2f
            align = Align.CENTER
            fade = { theme.background }
            adopt(Tab("All modules", Icons.Kind.GRID, { false }, muted = true) { screen.openModules() })
            adopt(TabDivider())
            for (m in category.modules) adopt(Tab(m.name, null, { m === module }) { screen.openModule(m) })
            val others = screen.mod.categories.filter { it !== category }
            if (others.isNotEmpty()) adopt(TabDivider())
            for (c in others) adopt(Tab(c.name, null, { false }, muted = true) {
                c.modules.firstOrNull()?.let { screen.openModule(it) } ?: screen.openModules(c.name)
            })
        }

        val left = Column().apply {
            fixedWidth = 200f
            padding = 16f
            gap = 10f
            accent = module.accent.takeIf { it != 0 }
            adopt(ModuleTitle())
            if (module.description.isNotEmpty()) adopt(Label(module.description, muted = true).apply { textSize = { theme.fontSmall + 0.5f } })
            module.master?.let {
                adopt(Row().apply {
                    gap = 8f
                    paddingY = 4f
                    adopt(Label("Enabled").apply { grow = true })
                    adopt(Switch(Binding.of({ module.isEnabled() }, { on -> if (on) module.enable() else module.disable() })))
                })
            }
            adopt(Spacer(0f, 4f))
            adopt(Label("SECTIONS", bold = true).apply { textSize = { theme.fontSmall - 0.5f }; colour = { theme.textFaint } })
            for (s in view.sections) adopt(SectionLink(s).also { links += it })
        }

        pane.apply {
            grow = true
            content.padding = 18f
            content.gap = 12f
            content.adopt(view)
            content.adopt(empty)
        }

        val body = Row().apply {
            align = Align.STRETCH
            fillHeight = true
            adopt(left)
            adopt(ConfigScreen.Divider(vertical = true))
            adopt(pane)
        }

        return Frame(Column().apply {
            adopt(tabs)
            adopt(ConfigScreen.Divider())
            adopt(body)
        }, ::frame)
    }

    private fun frame() {
        if (!revealedTab && tabs.width > 0f) {
            tabs.children.firstOrNull { (it as? Tab)?.active?.invoke() == true }?.let { tabs.reveal(it) }
            revealedTab = true
        }

        focusOption?.let { key ->
            val row = view.row(key)
            if (row == null) {
                focusOption = null
            } else if (pane.content.height > 0f && row.height > 0f) {
                pane.scrollToTop(row, 40f)
                row.flash()
                focusOption = null
            }
        }

        // Links follow their sections' visibility (filter); scroll-spy picks the active one.
        var changed = false
        for (l in links) {
            if (l.visible != l.section.visible) {
                l.visible = l.section.visible
                changed = true
            }
        }
        if (changed) screen.scene.dirty()
        if (empty.visible != view.empty) {
            empty.visible = view.empty
            screen.scene.dirty()
        }
        val shown = view.sections.filter { it.visible }
        activeSection = if (pane.maxScroll > 0f && pane.scroll >= pane.maxScroll - 1f) shown.lastOrNull()
        else shown.lastOrNull { it.y <= pane.y + 30f } ?: shown.firstOrNull()
    }

    override fun onSearch(query: String) {
        view.filter = query
        pane.scrollTo(0f, animate = false)
    }

    // ---- pieces --------------------------------------------------------------------------------

    private inner class ModuleTitle : AstraElement() {
        override fun prefHeight(width: Float) = 30f

        override fun draw(graphics: GuiGraphicsExtractor) {
            val th = theme
            drawModuleIcon(graphics, this, module, x, y, 30f)
            Paint.textMid(graphics, Paint.truncate(module.name, th.fontHeading - 1f, width - 40f, true), x + 40f, y, 30f, th.text, th.fontHeading - 1f, true)
        }
    }

    private inner class SectionLink(val section: SectionView) : AstraWidget() {
        private val on = Motion()

        init {
            fixedHeight = 20f
        }

        override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
            if (button == 0) pane.scrollToTop(section, 6f)
            return true
        }

        override fun onKey(key: Int): Boolean {
            if (!Keys.isActivate(key)) return false
            pane.scrollToTop(section, 6f)
            return true
        }

        override fun paint(g: GuiGraphicsExtractor) {
            val th = theme
            val accent = accentColor()
            on.to(activeSection === section)
            val a = on.get()
            val h = hover.get()
            Paint.rect(g, x, y + 3f, 2f, height - 6f, Colors.mix(th.border, accent, a))
            if (h > 0.01f) Paint.rect(g, x + 6f, y, width - 6f, height, Colors.fade(th.surfaceHover, h * 0.7f), th.radiusSmall)
            focusRing(g, x + 6f, y, width - 6f, height, th.radiusSmall)
            Paint.textMid(g, Paint.truncate(section.title, th.fontBody - 0.5f, width - 20f), x + 12f, y, height, Colors.mix(Colors.mix(th.textMuted, th.text, h), accent, a), th.fontBody - 0.5f, a > 0.5f)
        }
    }

    private inner class Tab(
        val label: String,
        val icon: Icons.Kind?,
        val active: () -> Boolean,
        val muted: Boolean = false,
        val action: () -> Unit,
    ) : AstraWidget() {
        private val on = Motion()

        init {
            fixedHeight = 26f
        }

        override fun prefWidth() = Paint.width(label, theme.fontBody, true) + 20f + if (icon != null) 16f else 0f

        override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
            if (button == 0) action()
            return true
        }

        override fun onKey(key: Int): Boolean {
            if (!Keys.isActivate(key)) return false
            action()
            return true
        }

        override fun onFocusChanged(focused: Boolean) {
            if (focused) tabs.reveal(this)
        }

        override fun paint(g: GuiGraphicsExtractor) {
            val th = theme
            val accent = module.accent.takeIf { it != 0 } ?: accentColor()
            on.to(active())
            val a = on.get()
            val h = hover.get()
            if (h > 0.01f || a > 0.01f) Paint.rect(g, x, y, width, height, Colors.mix(Colors.fade(th.surfaceHover, h), Colors.fade(accent, 0.14f), a), th.radiusMedium)
            focusRing(g, x, y, width, height, th.radiusMedium)
            val base = if (muted) th.textMuted else th.textMuted
            val col = Colors.mix(Colors.mix(base, th.text, h), accent, a)
            var tx = x + 10f
            if (icon != null) {
                Icons.draw(g, icon, tx, y + (height - 9f) / 2f, 9f, col, th.background)
                tx += 15f
            }
            Paint.textMid(g, label, tx, y, height, col, th.fontBody, a > 0.5f)
            if (a > 0.01f) Paint.rect(g, x + 8f, y + height + 4f, (width - 16f) * a, 2f, accent, 1f)
        }
    }

    private class TabDivider : AstraElement() {
        init {
            fixedWidth = 9f
            fixedHeight = 16f
        }

        override fun draw(graphics: GuiGraphicsExtractor) = Paint.rect(graphics, x + 4f, y, 1f, height, theme.border)
    }
}
