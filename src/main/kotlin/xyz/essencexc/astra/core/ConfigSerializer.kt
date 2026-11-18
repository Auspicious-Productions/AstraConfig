package xyz.essencexc.astra.core

import com.google.gson.JsonObject
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.function.Migration

/**
 * Maps options to and from a JSON object. Pure (no files, no Minecraft), so it's unit-tested
 * directly. Keys are `"<moduleId>.<fieldName>"`; `"@schema"` holds the schema version.
 */
internal object ConfigSerializer {
    const val SCHEMA_KEY: String = "@schema"

    /** Loads saved values into the fields. Unknown keys are kept, so downgrading doesn't lose data. */
    fun read(json: JsonObject, modules: List<Module>, schemaVersion: Int, migrations: List<Migration>) {
        val saved = json.get(SCHEMA_KEY)?.takeIf { it.isJsonPrimitive }?.asInt ?: schemaVersion
        if (saved < schemaVersion) {
            for (m in migrations) m.migrate(saved, json)
        }
        json.addProperty(SCHEMA_KEY, schemaVersion)

        for (module in modules) for (option in module.options) readOption(json, option)
    }

    private fun <T> readOption(json: JsonObject, option: Option<T>) {
        val codec = option.codec ?: return
        val key = option.savedKey
        var element = json.get(key)

        if (element == null) {
            val old = option.previousNames.map { "${option.module.id}.$it" }.firstOrNull { json.has(it) } ?: return
            element = json.remove(old)
        }

        val value = Codecs.decode(codec, element)
        if (value == null) {
            Astra.LOGGER.warn("Astra: ignoring invalid saved value for {}: {}", key, element)
            return
        }
        option.setSilently(value)
    }

    /** Writes every saved option's current value. Returns true if anything changed. */
    fun write(json: JsonObject, modules: List<Module>, schemaVersion: Int): Boolean {
        var changed = false
        json.addProperty(SCHEMA_KEY, schemaVersion)
        for (module in modules) for (option in module.options) {
            if (writeOption(json, option)) changed = true
        }
        return changed
    }

    private fun <T> writeOption(json: JsonObject, option: Option<T>): Boolean {
        val codec = option.codec ?: return false
        val element = Codecs.encode(codec, option.get()) ?: return false
        if (json.get(option.savedKey) == element) return false
        json.add(option.savedKey, element)
        return true
    }
}
