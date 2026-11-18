package xyz.essencexc.astra.api

import meteordevelopment.orbit.EventBus
import meteordevelopment.orbit.IEventBus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Entry point. Register your mod once in `onInitializeClient`:
 *
 * ```java
 * Astra.builder("mymod")
 *      .name("My Mod")
 *      .command("mymod")
 *      .lookup(MethodHandles.lookup())
 *      .category("Skills", FishingModule.INSTANCE)
 *      .build();
 * ```
 */
object Astra {
    const val ID: String = "astraconfig"

    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger("AstraConfig")

    /** Astra's event bus (Orbit). Enabled modules are subscribed automatically. */
    @JvmField
    val EVENTS: IEventBus = EventBus()

    private val mods = CopyOnWriteArrayList<AstraMod>()

    @JvmStatic
    fun builder(modId: String): AstraBuilder = AstraBuilder(modId)

    /** Kotlin: `Astra.create("mymod") { name = "My Mod"; lookup = MethodHandles.lookup(); category("Skills", FishingModule) }`. */
    @JvmSynthetic
    fun create(modId: String, block: AstraBuilder.() -> Unit): AstraMod = AstraBuilder(modId).apply(block).build()

    /** Every registered mod, in registration order. */
    @JvmStatic
    fun mods(): List<AstraMod> = mods.toList()

    @JvmStatic
    fun mod(id: String): AstraMod? = mods.firstOrNull { it.id == id }

    internal fun add(mod: AstraMod) {
        require(mods.none { it.id == mod.id }) { "Astra: a mod with id '${mod.id}' is already registered." }
        mods += mod
    }
}
