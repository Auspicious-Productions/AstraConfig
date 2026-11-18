package xyz.essencexc.astra.core

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.event.TickEvent

/**
 * Astra's own entrypoint. Hooks into Fabric events once; mods using Astra may register before or
 * after this runs, which is fine because nothing here depends on them yet.
 */
object AstraInit : ClientModInitializer {
    /** How often to check fields for direct writes that need saving. */
    private const val SYNC_INTERVAL_TICKS = 100
    private var ticks = 0

    override fun onInitializeClient() {
        AstraPrefs.load()

        // One Fabric HUD layer for every Astra HUD element, just under the chat.
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(
            net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(Astra.ID, "huds"),
        ) { graphics, _ -> HudRegistry.render(graphics) }

        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            KeybindRegistry.tick()
            Lifecycle.syncAll()
            Astra.EVENTS.post(TickEvent)

            if (++ticks % SYNC_INTERVAL_TICKS == 0) {
                for (mod in Astra.mods()) mod.store?.syncFromFields()
            }
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register { _ ->
            for (mod in Astra.mods()) runCatching { mod.store?.saveNow() }.onFailure { Astra.LOGGER.error("Saving {} failed", mod.id, it) }
            runCatching { AstraPrefs.flush() }
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            Commands.scope.register(dispatcher)
        }

        Astra.LOGGER.info("Astra initialised.")
    }
}
