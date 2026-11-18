package xyz.essencexc.astra.core

import com.google.gson.JsonObject
import foo.starred.snowbird.api.storage.AbstractJsonStore
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.AstraMod
import xyz.essencexc.astra.api.keybind.KeyBinding
import xyz.essencexc.astra.ui.theme.Theme
import xyz.essencexc.astra.ui.theme.Themes

/**
 * The player's Astra-wide preferences (`config/astraconfig/prefs.json`): theme, accent, animations,
 * background blur, UI scale, and each mod's menu key. Shared by every mod using Astra.
 */
internal object AstraPrefs {
    enum class UiScale(val label: String, val factor: Float) {
        SMALL("Small", 0.88f), NORMAL("Normal", 1f), LARGE("Large", 1.14f)
    }

    private val store by lazy { AbstractJsonStore(Astra.ID, "prefs") }

    var themeName: String = Theme.NIGHT.name
        set(value) { field = value; apply(); save() }

    /** 0 = the theme's own accent. */
    var accent: Int = 0
        set(value) { field = value; apply(); save() }

    var animations: Boolean = true
        set(value) { field = value; apply(); save() }

    var blur: Boolean = true
        set(value) { field = value; save() }

    var uiScale: UiScale = UiScale.NORMAL
        set(value) { field = value; save() }

    private var loading = false

    val presets: List<Theme> = listOf(Theme.NIGHT, Theme.DAY)

    fun load() {
        loading = true
        try {
            val o = store.load()
            o.get("theme")?.asString?.let { themeName = it }
            o.get("accent")?.asString?.let { s -> xyz.essencexc.astra.ui.util.Colors.parseHex(s)?.let { accent = it } }
            o.get("animations")?.asBoolean?.let { animations = it }
            o.get("blur")?.asBoolean?.let { blur = it }
            o.get("uiScale")?.asString?.let { s -> UiScale.entries.firstOrNull { it.name == s }?.let { uiScale = it } }
        } catch (e: Exception) {
            xyz.essencexc.astra.api.Astra.LOGGER.warn("Astra prefs unreadable, using defaults: {}", e.toString())
        } finally {
            loading = false
        }
        apply()
    }

    /** Pushes theme settings into [Themes.current]. */
    fun apply() {
        val base = presets.firstOrNull { it.name == themeName } ?: Theme.NIGHT
        Themes.current = base.copy(accent = if (accent != 0) accent else base.accent, animations = animations)
    }

    private fun save() {
        if (loading) return
        val o = store.load()
        o.addProperty("theme", themeName)
        o.addProperty("accent", if (accent == 0) "" else xyz.essencexc.astra.ui.util.Colors.hex(accent, true))
        o.addProperty("animations", animations)
        o.addProperty("blur", blur)
        o.addProperty("uiScale", uiScale.name)
        store.dirty()
    }

    // ---- menu keys (one per mod, rebindable on the Settings page) ------------------------------

    private fun keys(): JsonObject {
        val o = store.load()
        if (!o.has("menuKeys")) o.add("menuKeys", JsonObject())
        return o.getAsJsonObject("menuKeys")
    }

    /** Restores a mod's saved menu key, if the player changed it. */
    fun loadMenuKey(mod: AstraMod) {
        val kb = mod.openKey ?: return
        val saved = keys().get(mod.id) ?: return
        Codecs.decode(Codecs.KEY_BINDING, saved)?.let { kb.binding = it }
    }

    fun saveMenuKey(mod: AstraMod, binding: KeyBinding) {
        mod.openKey?.binding = binding
        Codecs.encode(Codecs.KEY_BINDING, binding)?.let { keys().add(mod.id, it) }
        store.dirty()
    }

    fun flush() = store.save()
}
