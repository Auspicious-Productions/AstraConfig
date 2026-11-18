package xyz.essencexc.astra.api

import xyz.essencexc.astra.api.function.Migration
import xyz.essencexc.astra.api.keybind.AstraKeybind
import xyz.essencexc.astra.core.AstraPrefs
import xyz.essencexc.astra.core.Collector
import xyz.essencexc.astra.core.ConfigStore
import xyz.essencexc.astra.core.Commands
import xyz.essencexc.astra.core.KeybindRegistry
import xyz.essencexc.astra.core.Lifecycle
import xyz.essencexc.astra.core.AstraConfigException
import java.lang.invoke.MethodHandles

/** Builds an [AstraMod]. Java: chain the methods; Kotlin: set the properties inside `Astra.create { }`. */
class AstraBuilder internal constructor(private val modId: String) {
    /** Display name in the menu's sidebar. Defaults to the mod id. */
    var name: String = modId

    /** Logo resource id, e.g. `"mymod:textures/gui/logo.svg"`. */
    var logo: String? = null

    /** Client command that opens the menu, without the slash. */
    var command: String? = null

    /** Key that opens the menu (GLFW key code); -1 = none. */
    var openKey: Int = -1

    /**
     * `MethodHandles.lookup()` called in your mod. Required: Astra uses it to read and write your
     * option fields and to call your `@EventHandler` methods, including private ones.
     */
    var lookup: MethodHandles.Lookup? = null

    /** Bump when you need a [Migration]. Stored in the config file. */
    var schemaVersion: Int = 1

    private val categories = mutableListOf<Category>()
    private val migrations = mutableListOf<Migration>()

    fun name(name: String): AstraBuilder = apply { this.name = name }
    fun logo(logo: String): AstraBuilder = apply { this.logo = logo }
    fun command(command: String): AstraBuilder = apply { this.command = command.removePrefix("/") }
    fun openKey(key: Int): AstraBuilder = apply { this.openKey = key }
    fun lookup(lookup: MethodHandles.Lookup): AstraBuilder = apply { this.lookup = lookup }
    fun schemaVersion(version: Int): AstraBuilder = apply { this.schemaVersion = version }

    /** Runs when the saved schema version is lower than [schemaVersion]. */
    fun migrate(migration: Migration): AstraBuilder = apply { migrations += migration }

    /** Adds a category (heading on the Modules page) with its modules, in order. */
    fun category(name: String, vararg modules: Module): AstraBuilder = apply { categories += Category(name, modules.toList()) }

    private val pages = mutableListOf<CustomPage>()

    /** Adds a custom page to the menu's sidebar, below Modules. [factory] builds its content when opened. */
    @JvmOverloads
    fun page(name: String, subtitle: String = "", factory: PageFactory): AstraBuilder = apply { pages += CustomPage(name, subtitle, factory) }

    /** Validates and registers the mod, loads its config, and enables modules that are on. */
    fun build(): AstraMod {
        val lookup = lookup ?: throw AstraConfigException(
            "Astra: mod '$modId' has no lookup. Add .lookup(MethodHandles.lookup()) (Java) or lookup = MethodHandles.lookup() (Kotlin) to the builder."
        )
        if (categories.isEmpty()) throw AstraConfigException("Astra: mod '$modId' has no categories. Add at least one .category(\"Name\", module...).")

        val openKeybind = if (openKey >= 0) AstraKeybind.of(openKey).onPress { Commands.openMenu(modId) } else null
        val mod = AstraMod(modId, name, logo, command, categories, openKeybind, pages.toList())

        val seen = mutableSetOf<String>()
        for (module in mod.modules()) {
            if (!seen.add(module.id)) throw AstraConfigException("Astra: mod '$modId' registers two modules with id '${module.id}'.")
            if (module.mod != null && module.mod !== mod) throw AstraConfigException("Astra: module '${module.id}' is already registered with mod '${module.mod!!.id}'.")
            module.mod = mod
            Collector.collect(module, lookup)
            registerOrbit(module.javaClass, lookup)
        }

        mod.store = ConfigStore(mod, schemaVersion, migrations.toList()).also { it.load() }

        Astra.add(mod)
        openKeybind?.let {
            AstraPrefs.loadMenuKey(mod)
            KeybindRegistry.register(it, null, "Open ${mod.name}", mod)
        }
        command?.let { Commands.register(mod) }
        for (module in mod.modules()) Lifecycle.sync(module)

        Astra.LOGGER.info("Registered {} with {} modules and {} options.", mod.name, mod.modules().size, mod.modules().sumOf { it.options.size })
        return mod
    }

    private fun registerOrbit(cls: Class<*>, lookup: MethodHandles.Lookup) {
        if (!orbitPackages.add(cls.packageName)) return
        // Orbit asks this factory for a lookup per listener class; privateLookupIn gives full access
        // to private @EventHandler methods in the dev's own classes.
        Astra.EVENTS.registerLambdaFactory(cls.packageName) { _, klass -> MethodHandles.privateLookupIn(klass, lookup) }
    }

    private companion object {
        val orbitPackages: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    }
}
