package xyz.essencexc.astra.ui.menu

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.CustomPage
import xyz.essencexc.astra.api.annotations.InfoType
import xyz.essencexc.astra.api.keybind.KeyBinding
import xyz.essencexc.astra.core.AstraPrefs
import xyz.essencexc.astra.core.KeybindRegistry
import xyz.essencexc.astra.core.OptionSpec
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.layout.Align
import xyz.essencexc.astra.ui.layout.Column
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.layout.ScrollPane
import xyz.essencexc.astra.ui.theme.Theme
import xyz.essencexc.astra.ui.theme.Themes
import xyz.essencexc.astra.ui.util.Paint
import xyz.essencexc.astra.ui.widgets.Binding
import xyz.essencexc.astra.ui.widgets.Button
import xyz.essencexc.astra.ui.widgets.ColorPicker
import xyz.essencexc.astra.ui.widgets.Dropdown
import xyz.essencexc.astra.ui.widgets.InfoBox
import xyz.essencexc.astra.ui.widgets.KeybindButton
import xyz.essencexc.astra.ui.widgets.Label
import xyz.essencexc.astra.ui.widgets.NumberScale
import xyz.essencexc.astra.ui.widgets.Slider
import xyz.essencexc.astra.ui.widgets.Switch
import xyz.essencexc.astra.ui.core.Keys
import java.io.File

/** A scrolling page body with standard padding. */
private fun pageBody(build: Column.() -> Unit): ScrollPane = ScrollPane().apply {
    content.padding = 20f
    content.gap = 18f
    content.build()
}

private fun section(title: String, vararg rows: AstraElement): Column = Column().apply {
    gap = 8f
    adopt(HeadingLine(title))
    adopt(Card().apply { rows.forEach { adopt(it) } })
}

/**
 * Every keybind of this mod, grouped by key, with conflict badges (also across other Astra mods).
 * Rebind or clear inline; the list regroups after a change.
 */
internal class BoundKeysPage(screen: ConfigScreen) : Page(screen) {
    override val key = KEYS
    override val title = "Bound Keys"
    override val subtitle get() = "${entries().size} keybinds · ${conflicts()} conflicts"
    override val searchPlaceholder = "Search keybinds"

    private val pane = ScrollPane()
    private var query = ""
    private var snapshot = ""

    private fun entries() = KeybindRegistry.entries().filter { it.mod === screen.mod }

    private fun conflicts() = entries().count { e -> e.keybind.binding.bound && KeybindRegistry.entries().count { it.keybind.binding == e.keybind.binding } > 1 }

    /** Change detector: bindings of everything, so a rebind anywhere regroups the list. */
    private fun state() = KeybindRegistry.entries().joinToString("|") { it.keybind.binding.toString() } + "#" + query

    override fun build(): AstraElement {
        pane.content.padding = 20f
        pane.content.gap = 18f
        fill()
        return Frame(pane) {
            val s = state()
            if (s != snapshot && !pane.content.children.any { it is AstraElement && listening(it) }) fill()
        }
    }

    override fun onSearch(query: String) {
        this.query = query.trim().lowercase()
        fill()
    }

    private fun listening(e: AstraElement): Boolean {
        var found = false
        e.iterateChildren { if (it is KeybindButton && it.listening) found = true }
        return found
    }

    private fun fill() {
        snapshot = state()
        pane.content.clear()
        val all = KeybindRegistry.entries()
        val mine = entries().filter { query.isEmpty() || it.title.lowercase().contains(query) || (it.module?.name?.lowercase()?.contains(query) == true) || Keys.describe(it.keybind.binding).lowercase().contains(query) }

        if (mine.isEmpty()) {
            pane.content.adopt(Label(if (query.isEmpty()) "${screen.mod.name} has no keybinds." else "No keybinds match.", muted = true))
            screen.scene.dirty()
            return
        }

        val groups = mine.groupBy { it.keybind.binding }
            .toList()
            .sortedWith(compareBy({ !it.first.bound }, { Keys.describe(it.first) }))
        for ((binding, list) in groups) {
            val users = all.filter { binding.bound && it.keybind.binding == binding }
            val conflict = users.size > 1
            val heading = if (binding.bound) Keys.describe(binding) else "Not bound"
            val others = users.filter { it.mod !== screen.mod }.mapNotNull { it.mod?.name }.distinct()
            pane.content.adopt(Column().apply {
                gap = 8f
                adopt(HeadingLine(heading) {
                    when {
                        !conflict -> ""
                        others.isNotEmpty() -> "Conflict · also used by ${others.joinToString()}"
                        else -> "Conflict"
                    }
                }.also { it.accent = if (conflict) theme.warning else null })
                adopt(Card().apply { list.forEach { adopt(keyRow(it)) } })
            })
        }
        screen.scene.dirty()
    }

    private fun keyRow(e: KeybindRegistry.Entry): AstraElement {
        val option = e.module?.options?.firstOrNull { it.spec == OptionSpec.KeybindSpec && it.title == e.title }
        @Suppress("UNCHECKED_CAST")
        val binding: Binding<KeyBinding> = when {
            option != null -> Binding.of({ option.get() as KeyBinding }, { (option as xyz.essencexc.astra.core.Option<KeyBinding>).set(it) })
            e.keybind === screen.mod.openKey -> Binding.of({ e.keybind.binding }, { AstraPrefs.saveMenuKey(screen.mod, it) })
            else -> Binding.of({ e.keybind.binding }, { e.keybind.binding = it; screen.mod.store?.markDirty() })
        }
        val where = e.module?.name ?: "Menu"
        val reset = Button("Reset", Button.Style.GHOST) { binding.set(e.keybind.defaultBinding) }
        return SettingRow(e.title, where, Row().apply {
            gap = 6f
            align = Align.CENTER
            adopt(reset)
            adopt(KeybindButton(binding))
        }).also { reset.enabledWhen = { e.keybind.binding != e.keybind.defaultBinding } }
    }
}

/** Astra preferences (shared by every mod) plus this mod's menu key and config actions. */
internal class SettingsPage(screen: ConfigScreen) : Page(screen) {
    override val key = SETTINGS
    override val title = "Settings"
    override val subtitle = "Menu behaviour and config file"

    private var confirmReset = 0L

    override fun build(): AstraElement = pageBody {
        val mod = screen.mod
        val openKey = mod.openKey
        adopt(section("Menu",
            if (openKey != null) SettingRow("Menu key", "Opens this menu from anywhere in game.", KeybindButton(Binding.of({ openKey.binding }, { AstraPrefs.saveMenuKey(mod, it) })))
            else SettingRow("Menu key", "${mod.name} doesn't define a menu key. Use /${mod.command ?: "its command"}.", Label("")),
            SettingRow("Animations", "Transitions, hovers and the open/close motion. Applies to every Astra menu.", Switch(Binding.of({ AstraPrefs.animations }, { AstraPrefs.animations = it }))),
            SettingRow("Background blur", "Blur the game behind the menu.", Switch(Binding.of({ AstraPrefs.blur }, { AstraPrefs.blur = it }))),
            SettingRow("Menu size", "Scale of every Astra menu.", Dropdown(
                Binding.of({ AstraPrefs.uiScale.ordinal }, { AstraPrefs.uiScale = AstraPrefs.UiScale.entries[it]; screen.rescale() }),
                AstraPrefs.UiScale.entries.map { it.label },
            )),
        ))

        val huds = xyz.essencexc.astra.core.HudRegistry.entries(mod)
        if (huds.isNotEmpty()) adopt(section("HUD",
            SettingRow("HUD layout", "Move, scale and show/hide ${mod.name}'s ${huds.size} HUD element(s). Also /${mod.command ?: "<command>"} hud.", Button("Edit HUD", Button.Style.PRIMARY) {
                xyz.essencexc.astra.ui.hud.HudEditorScreen.open(mod)
            }),
        ))

        val dir = File(net.fabricmc.loader.api.FabricLoader.getInstance().configDir.toFile(), mod.id)
        adopt(section("Config",
            SettingRow("Save now", "Settings save automatically; this writes the file immediately.", Button("Save") {
                mod.save()
            }),
            SettingRow("Open config folder", dir.path, Button("Open") { openFolder(dir) }),
            SettingRow("Reset all settings", "Puts every setting of ${mod.name} back to its default. Click twice to confirm.", Button("Reset", Button.Style.DANGER).also { b ->
                b.action = {
                    val now = System.currentTimeMillis()
                    if (now - confirmReset < 3000) {
                        for (m in mod.modules()) for (o in m.options) if (o.saved) o.reset()
                        mod.save()
                        b.label = "Done"
                        confirmReset = 0
                    } else {
                        confirmReset = now
                        b.label = "Confirm?"
                    }
                    screen.scene.dirty()
                }
            }),
        ))

        adopt(InfoBox(InfoType.INFO, "About") { "${mod.name} uses Astra ${Astra.ID} · ${Astra.mods().size} mod(s) registered." })
    }
}

/** Theme preset, accent colour and a live preview. Shared by every Astra menu. */
internal class CustomizePage(screen: ConfigScreen) : Page(screen) {
    override val key = CUSTOMIZE
    override val title = "Customize"
    override val subtitle = "Theme and accent colour for every Astra menu"

    private val demoSwitch = Binding.hold(true)
    private val demoSlider = Binding.hold(60.0)

    override fun build(): AstraElement = pageBody {
        adopt(section("Theme",
            SettingRow("Preset", "Base colours.", Dropdown(
                Binding.of({ AstraPrefs.presets.indexOfFirst { it.name == AstraPrefs.themeName }.coerceAtLeast(0) }, { AstraPrefs.themeName = AstraPrefs.presets[it].name }),
                AstraPrefs.presets.map { it.name },
            )),
            SettingRow("Accent", "Highlights, switches and sliders. Modules with their own colour keep it.", Row().apply {
                gap = 5f
                align = Align.CENTER
                for (c in Theme.ACCENTS) adopt(AccentDot(c))
            }),
            SettingRow("Custom accent", null, ColorPicker(Binding.of({ Themes.current.accent }, { AstraPrefs.accent = it or 0xFF000000.toInt() }), alpha = false)),
            SettingRow("Use the theme's accent", null, Button("Reset") { AstraPrefs.accent = 0 }),
        ))
        adopt(section("Preview",
            SettingRow("A switch", "How settings look with these colours.", Switch(demoSwitch)),
            SettingRow("A slider", null, Slider(demoSlider, NumberScale(0.0, 100.0, 1.0, true))),
            SettingRow("A note", null, InfoBox(InfoType.SUCCESS) { "Looking good." }.apply { fixedWidth = 200f }),
        ))
    }

    private class AccentDot(val colour: Int) : xyz.essencexc.astra.ui.core.AstraWidget() {
        init {
            fixedWidth = 14f
            fixedHeight = 14f
        }

        override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
            AstraPrefs.accent = colour
            return true
        }

        override fun onKey(key: Int): Boolean {
            if (!Keys.isActivate(key)) return false
            AstraPrefs.accent = colour
            return true
        }

        override fun paint(g: GuiGraphicsExtractor) {
            val r = 7f + 1.2f * hover.get()
            Paint.circle(g, x + 7f, y + 7f, r, colour)
            if (Themes.current.accent == colour) Paint.circle(g, x + 7f, y + 7f, 2.5f, theme.background)
            focusRing(g, x, y, width, height, 7f)
        }
    }
}

/** A page a mod added with `AstraBuilder.page`. */
internal class CustomPageView(screen: ConfigScreen, private val custom: CustomPage) : Page(screen) {
    override val key = "custom:${custom.name}"
    override val title = custom.name
    override val subtitle = custom.subtitle

    override fun build(): AstraElement = try {
        custom.factory.create()
    } catch (e: Exception) {
        Astra.LOGGER.error("Custom page '{}' of {} failed to build", custom.name, screen.mod.id, e)
        pageBody { adopt(InfoBox(InfoType.ERROR, "This page failed to load") { e.toString() }) }
    }

}

private fun openFolder(dir: File) {
    dir.mkdirs()
    //~ if >= 26.3 'Util.getPlatform().openFile(dir)' -> 'com.mojang.blaze3d.Blaze3D.openPath(dir.toPath())'
    Util.getPlatform().openFile(dir)
}
