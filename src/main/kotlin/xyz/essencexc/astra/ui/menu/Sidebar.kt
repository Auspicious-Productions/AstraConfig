package xyz.essencexc.astra.ui.menu

import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.layout.Column
import xyz.essencexc.astra.ui.layout.Spacer
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint

/**
 * Left column: the mod's name, then grouped navigation. Modules expands into its categories,
 * which jump to their heading on the Modules page.
 */
internal class Sidebar(private val screen: ConfigScreen) : Column() {
    init {
        fixedWidth = 184f
        paddingX = 10f
        paddingY = 14f
        gap = 2f
        val mod = screen.mod

        adopt(Brand())
        adopt(Spacer(0f, 12f))

        adopt(GroupLabel("MAIN"))
        adopt(Item("Modules", Icons.Kind.GRID, { screen.page?.key == Page.MODULES }) { screen.openModules() })
        for (c in mod.categories) {
            adopt(SubItem(c.name, { (screen.page as? ModulesPage)?.activeCategory == c.name || (screen.page as? ModulePage)?.category?.name == c.name }) { screen.openModules(c.name) })
        }
        for (p in mod.pages) {
            adopt(Item(p.name, Icons.Kind.PAGE, { screen.page?.key == "custom:${p.name}" }) { screen.show(CustomPageView(screen, p)) })
        }

        adopt(Spacer(0f, 10f))
        adopt(GroupLabel("CONFIGURATION"))
        adopt(Item("Bound Keys", Icons.Kind.KEY, { screen.page?.key == Page.KEYS }) { screen.show(BoundKeysPage(screen)) })
        if (xyz.essencexc.astra.core.HudRegistry.entries(mod).isNotEmpty()) {
            adopt(Item("HUD Editor", Icons.Kind.HUD, { false }) { xyz.essencexc.astra.ui.hud.HudEditorScreen.open(mod) })
        }
        adopt(Item("Profiles", Icons.Kind.PAGE, { screen.page?.key == Page.PROFILES }) { screen.show(ProfilesPage(screen)) })
        adopt(Item("Settings", Icons.Kind.GEAR, { screen.page?.key == Page.SETTINGS }) { screen.show(SettingsPage(screen)) })

        adopt(Spacer(0f, 10f))
        adopt(GroupLabel("OPTIONS"))
        adopt(Item("Customize", Icons.Kind.PALETTE, { screen.page?.key == Page.CUSTOMIZE }) { screen.show(CustomizePage(screen)) })
    }

    override fun draw(graphics: GuiGraphicsExtractor) {
        val th = theme
        val r = th.radiusLarge + 3f
        Paint.rect4(graphics, x, y, width, height, th.surface, r, 0f, r, 0f)
        Paint.rect(graphics, x + width - 1f, y, 1f, height, th.border)
    }

    /** Mod name with a monogram tile in the accent colour. */
    private inner class Brand : AstraElement() {
        override fun prefHeight(width: Float) = 26f

        override fun draw(graphics: GuiGraphicsExtractor) {
            val th = theme
            val accent = accentColor()
            val name = screen.mod.name
            // The mod's logo if it has one (PNG or SVG), else a monogram tile.
            if (!xyz.essencexc.astra.ui.util.Images.draw(graphics, screen.mod.logo, x + 2f, y, 26f, 26f, radius = th.radiusMedium)) {
                Paint.rect(graphics, x + 2f, y, 26f, 26f, Colors.fade(accent, 0.18f), th.radiusMedium)
                Paint.outline(graphics, x + 2f, y, 26f, 26f, Colors.fade(accent, 0.45f), th.radiusMedium)
                Paint.textCentered(graphics, name.take(1).uppercase(), x + 15f, y, 26f, accent, th.fontTitle + 1f, true)
            }
            val tx = x + 36f
            Paint.text(graphics, Paint.truncate(name, th.fontTitle, width - 40f, true), tx, y + 2f, th.text, th.fontTitle, true)
            Paint.text(graphics, "Powered by Astra", tx, y + 4f + Paint.lineHeight(th.fontTitle), th.textFaint, th.fontSmall - 0.5f)
        }
    }

    private class GroupLabel(val text: String) : AstraElement() {
        override fun prefHeight(width: Float) = Paint.lineHeight(theme.fontSmall - 0.5f) + 8f
        override fun draw(graphics: GuiGraphicsExtractor) =
            Paint.text(graphics, text, x + 8f, y + 4f, theme.textFaint, theme.fontSmall - 0.5f, true)
    }

    /** A navigation entry. */
    private open inner class Item(
        val label: String,
        val icon: Icons.Kind?,
        val active: () -> Boolean,
        val action: () -> Unit,
    ) : AstraWidget() {
        protected val on = Motion()

        init {
            fixedHeight = 24f
        }

        override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
            if (button == 0) action()
            return true
        }

        override fun onKey(key: Int): Boolean {
            if (!Keys.isActivate(key)) return false
            action()
            return true
        }

        override fun paint(g: GuiGraphicsExtractor) {
            val th = theme
            val accent = accentColor()
            on.to(active())
            val a = on.get()
            val h = hover.get()
            val bg = Colors.mix(Colors.fade(th.surfaceHover, h), Colors.fade(accent, 0.14f), a)
            Paint.rect(g, x, y, width, height, bg, th.radiusMedium)
            if (a > 0.01f) Paint.rect(g, x, y + 6f, 2.5f * a, height - 12f, accent, 1.2f)
            focusRing(g, x, y, width, height, th.radiusMedium)
            val col = Colors.mix(Colors.mix(th.textMuted, th.text, h), accent, a)
            var tx = x + 10f
            if (icon != null) {
                Icons.draw(g, icon, tx, y + (height - 10f) / 2f, 10f, col, Colors.mix(th.surface, bg, 1f))
                tx += 18f
            }
            Paint.textMid(g, Paint.truncate(label, th.fontBody, x + width - tx - 6f), tx, y, height, col, th.fontBody, a > 0.5f)
        }
    }

    /** An indented category under Modules. */
    private inner class SubItem(label: String, active: () -> Boolean, action: () -> Unit) : Item(label, null, active, action) {
        init {
            fixedHeight = 20f
        }

        override fun paint(g: GuiGraphicsExtractor) {
            val th = theme
            val accent = accentColor()
            on.to(active())
            val a = on.get()
            val h = hover.get()
            Paint.rect(g, x + 28f, y + 3f, 1f, height - 6f, Colors.mix(th.border, accent, a))
            if (h > 0.01f) Paint.rect(g, x + 22f, y, width - 22f, height, Colors.fade(th.surfaceHover, h * 0.7f), th.radiusSmall)
            focusRing(g, x + 22f, y, width - 22f, height, th.radiusSmall)
            Paint.textMid(g, Paint.truncate(label, th.fontSmall + 0.5f, width - 46f), x + 36f, y, height, Colors.mix(Colors.mix(th.textFaint, th.textMuted, h), th.text, a), th.fontSmall + 0.5f)
        }
    }
}
