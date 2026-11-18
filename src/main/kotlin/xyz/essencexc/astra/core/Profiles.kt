package xyz.essencexc.astra.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.AstraMod
import java.io.File

/**
 * Named copies of a mod's settings in `config/<modId>/profiles/<name>.json`, plus clipboard
 * export/import. A profile is a plain config snapshot, so it survives schema migrations.
 */
internal object Profiles {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** Marks exported text so imports can check it's for the right mod. */
    private const val EXPORT_KEY = "astraconfig"

    private fun dir(mod: AstraMod) = File(FabricLoader.getInstance().configDir.toFile(), "${mod.id}/profiles")

    /** Letters, digits, spaces, - and _; up to 32 characters. */
    fun valid(name: String): Boolean = name.isNotBlank() && name.length <= 32 && name.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }

    private fun file(mod: AstraMod, name: String) = File(dir(mod), "${name.trim()}.json")

    fun list(mod: AstraMod): List<String> =
        dir(mod).listFiles { f -> f.isFile && f.name.endsWith(".json") }?.map { it.name.removeSuffix(".json") }?.sortedBy { it.lowercase() } ?: emptyList()

    fun save(mod: AstraMod, name: String): Boolean {
        val store = mod.store ?: return false
        if (!valid(name)) return false
        return runCatching {
            dir(mod).mkdirs()
            file(mod, name).writeText(gson.toJson(store.snapshot()))
        }.onFailure { Astra.LOGGER.error("Saving profile '{}' of {} failed", name, mod.id, it) }.isSuccess
    }

    fun load(mod: AstraMod, name: String): Boolean {
        val store = mod.store ?: return false
        return runCatching {
            val json = JsonParser.parseString(file(mod, name).readText()).asJsonObject
            store.apply(json)
        }.onFailure { Astra.LOGGER.error("Loading profile '{}' of {} failed", name, mod.id, it) }.isSuccess
    }

    fun delete(mod: AstraMod, name: String): Boolean = file(mod, name).delete()

    /** The current settings as compact JSON, tagged with the mod id. */
    fun export(mod: AstraMod): String? {
        val store = mod.store ?: return null
        val o = JsonObject()
        o.addProperty(EXPORT_KEY, mod.id)
        o.add("config", store.snapshot())
        return o.toString()
    }

    sealed class ImportResult {
        data object Ok : ImportResult()
        data class Failed(val reason: String) : ImportResult()
    }

    fun import(mod: AstraMod, text: String): ImportResult {
        val store = mod.store ?: return ImportResult.Failed("${mod.name} has no config loaded.")
        val root = runCatching { JsonParser.parseString(text.trim()).asJsonObject }.getOrNull()
            ?: return ImportResult.Failed("The clipboard doesn't contain Astra settings.")
        val owner = root.get(EXPORT_KEY)?.takeIf { it.isJsonPrimitive }?.asString
            ?: return ImportResult.Failed("The clipboard doesn't contain Astra settings.")
        if (owner != mod.id) return ImportResult.Failed("Those settings are for '$owner', not ${mod.name}.")
        val config = root.get("config")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return ImportResult.Failed("The settings are incomplete.")
        store.apply(config)
        return ImportResult.Ok
    }
}
