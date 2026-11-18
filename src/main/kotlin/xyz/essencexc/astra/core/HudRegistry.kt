package xyz.essencexc.astra.core

import foo.starred.snowbird.api.client
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.AstraMod
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.hud.HudElement
import xyz.essencexc.astra.api.hud.HudLayout
import java.util.concurrent.CopyOnWriteArrayList

/** Every HUD element across all mods, and the in-game rendering. */
internal object HudRegistry {
    class Entry(val element: HudElement, val module: Module, val option: Option<HudLayout>) {
        val mod: AstraMod? get() = module.mod
    }

    private val entries = CopyOnWriteArrayList<Entry>()
    private val failed = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<HudElement, Boolean>())

    /** Creates the option backing a HUD element (its value is the layout) and registers the element. */
    fun option(module: Module, element: HudElement, title: String, description: String, section: String, keywords: List<String>, key: String): Option<HudLayout> {
        element.module = module
        val option = Option(
            module, key, title, description, section, "", keywords, emptyList(),
            OptionSpec.HudSpec(element), Codecs.HUD_LAYOUT,
            MappedAccessor({ element }, HudElement::layout) { e, l: HudLayout -> e.layout = l },
        )
        register(element, module, option)
        return option
    }

    fun register(element: HudElement, module: Module, option: Option<HudLayout>) {
        element.module = module
        entries.removeIf { it.element === element }
        entries += Entry(element, module, option)
    }

    fun entries(): List<Entry> = entries.toList()
    fun entries(mod: AstraMod): List<Entry> = entries.filter { it.mod === mod }
    fun entry(element: HudElement): Entry? = entries.firstOrNull { it.element === element }

    /** Shown in game right now: module on, not hidden by the player or by its own condition. */
    fun active(e: Entry): Boolean = e.module.mod != null && e.module.isEnabled() && e.element.layout.shown && !e.element.hiddenByCondition() && !hiddenByGame(e.element.layout.style)

    /** F1 hides every HUD; the style can also hide it with chat or the F3 debug screen open. */
    private fun hiddenByGame(style: xyz.essencexc.astra.api.hud.HudStyle): Boolean {
        //~ if >= 26.2 'client.options.hideGui' -> 'client.gui.hud.isHidden()'
        if (client.options.hideGui) return true
        //~ if >= 26.2 'client.screen' -> 'client.gui.screen()'
        if (style.hideWithChat && client.screen is net.minecraft.client.gui.screens.ChatScreen) return true
        if (style.hideWithDebug && client.debugOverlay.showDebugScreen()) return true
        return false
    }

    /** Fabric HUD callback. The HUD editor draws elements itself, so skip while it's open. */
    fun render(g: GuiGraphicsExtractor) {
        //~ if >= 26.2 'client.screen' -> 'client.gui.screen()'
        if (client.screen is xyz.essencexc.astra.ui.hud.HudEditorScreen) return
        val w = g.guiWidth().toFloat()
        val h = g.guiHeight().toFloat()
        for (e in entries) {
            if (!active(e)) continue
            val el = e.element
            val (x, y) = el.layout.topLeft(w, h, el.width(), el.height())
            draw(g, el, x, y, el.layout.scale, false)
        }
    }

    /** Draws [element] with its top-left at (x, y), scaled. Errors are logged once per element. */
    fun draw(g: GuiGraphicsExtractor, element: HudElement, x: Float, y: Float, scale: Float, preview: Boolean) {
        val pose = g.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        if (scale != 1f) pose.scale(scale, scale)
        try {
            element.draw(xyz.essencexc.astra.api.hud.HudContext(g, preview, element))
        } catch (e: Exception) {
            if (failed.add(element)) Astra.LOGGER.error("HUD element '{}' failed to draw", element.id, e)
        } finally {
            pose.popMatrix()
        }
    }
}
