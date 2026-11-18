package xyz.essencexc.astra.api

import xyz.essencexc.astra.api.keybind.AstraKeybind
import xyz.essencexc.astra.core.ConfigStore

/** A group of modules shown under a heading on the Modules page. */
class Category internal constructor(
    @get:JvmName("name") val name: String,
    modules: List<Module>,
) {
    @get:JvmName("modules")
    val modules: List<Module> = modules.toList()
}

/** A registered mod: its branding, categories and config file. Created by [AstraBuilder.build]. */
class AstraMod internal constructor(
    @get:JvmName("id") val id: String,
    @get:JvmName("name") val name: String,
    @get:JvmName("logo") val logo: String?,
    @get:JvmName("command") val command: String?,
    categories: List<Category>,
    @get:JvmName("openKey") val openKey: AstraKeybind?,
    pages: List<CustomPage> = emptyList(),
) {
    /** Custom sidebar pages added with `AstraBuilder.page`. */
    @get:JvmName("pages")
    val pages: List<CustomPage> = pages.toList()

    @get:JvmName("categories")
    val categories: List<Category> = categories.toList()

    /** Null until the config is loaded in `build()`. */
    internal var store: ConfigStore? = null

    fun modules(): List<Module> = categories.flatMap { it.modules }

    fun module(id: String): Module? = modules().firstOrNull { it.id == id }

    /** Writes the config to disk now (it is also saved automatically). */
    fun save() {
        store?.saveNow()
    }

    override fun toString(): String = "AstraMod($id)"
}
