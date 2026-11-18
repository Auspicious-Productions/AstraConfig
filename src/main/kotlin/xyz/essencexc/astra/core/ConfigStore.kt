package xyz.essencexc.astra.core

import com.google.gson.JsonObject
import foo.starred.snowbird.api.storage.AbstractJsonStore
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.AstraMod
import xyz.essencexc.astra.api.event.ConfigLoadEvent
import xyz.essencexc.astra.api.event.ConfigSaveEvent
import xyz.essencexc.astra.api.function.Migration

/**
 * `config/<modId>/config.json`, through Snowbird's [AbstractJsonStore] (atomic writes, 5 s debounce).
 * The dev's fields are the source of truth: values are read back from them before every save, so
 * direct field writes are saved too.
 */
internal class ConfigStore(private val mod: AstraMod, private val schemaVersion: Int, private val migrations: List<Migration>) {
    private val json = AbstractJsonStore(mod.id, "config")

    fun load() {
        ConfigSerializer.read(json.load(), mod.modules(), schemaVersion, migrations)
        // Write back defaults for options that weren't saved yet, so the file lists every option.
        if (ConfigSerializer.write(json.load(), mod.modules(), schemaVersion)) json.dirty()
        Astra.EVENTS.post(ConfigLoadEvent(mod))
    }

    /** Something changed through Astra: schedule a (debounced) save. */
    fun markDirty() {
        if (ConfigSerializer.write(json.load(), mod.modules(), schemaVersion)) json.dirty()
    }

    /** Picks up direct field writes. Called periodically and before shutdown. */
    fun syncFromFields(): Boolean {
        val changed = ConfigSerializer.write(json.load(), mod.modules(), schemaVersion)
        if (changed) json.dirty()
        return changed
    }

    /** The current values as config JSON (same shape as the file). */
    fun snapshot(): JsonObject = JsonObject().also { ConfigSerializer.write(it, mod.modules(), schemaVersion) }

    /**
     * Loads [json] (config-file shape, any schema version) into the options. Changed options go
     * through [Option.set], so `onChange` listeners and module toggles fire as if the player had
     * changed them. Options missing from [json] keep their value.
     */
    fun apply(json: JsonObject) {
        val options = mod.modules().flatMap { it.options }.filter { it.saved }
        val before = options.associateWith { it.get() }
        ConfigSerializer.read(json.deepCopy(), mod.modules(), schemaVersion, migrations)
        for (o in options) {
            val now = o.get()
            val old = before[o]
            if (now == old) continue
            @Suppress("UNCHECKED_CAST")
            (o as Option<Any?>).setSilently(old)
            o.setUnchecked(now)
        }
        for (m in mod.modules()) Lifecycle.sync(m)
        markDirty()
    }

    fun saveNow() {
        Astra.EVENTS.post(ConfigSaveEvent(mod))
        ConfigSerializer.write(json.load(), mod.modules(), schemaVersion)
        json.dirty()
        json.save()
    }
}
