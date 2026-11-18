package xyz.essencexc.astra.core

import foo.starred.kommand.scopes.KommandCommandScope
import foo.starred.snowbird.api.lie
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.AstraMod

/** `/<command>` for each mod: opens the menu; `dump` and `save` subcommands for debugging. */
internal object Commands {
    val scope: KommandCommandScope<FabricClientCommandSource> = KommandCommandScope()

    fun register(mod: AstraMod) {
        val name = mod.command ?: return
        scope.command(name) {
            executes { openMenu(mod.id) }
            "dump" { dump(mod) }
            "hud" { xyz.essencexc.astra.ui.hud.HudEditorScreen(mod, null).open() }
            "save" {
                mod.save()
                "[${mod.name}] Config saved.".lie()
            }
        }
    }

    fun openMenu(modId: String) {
        val mod = Astra.mod(modId) ?: return
        xyz.essencexc.astra.ui.menu.ConfigScreen(mod).open()
    }

    private fun dump(mod: AstraMod) {
        "[${mod.name}] ${mod.modules().size} modules:".lie()
        for (module in mod.modules()) {
            "${module.name} (${if (module.isEnabled()) "on" else "off"})".lie()
            for (option in module.options) {
                if (option.codec == null) continue
                "  ${option.section} > ${option.title} = ${option.get().let { if (it is xyz.essencexc.astra.api.keybind.KeyBinding) xyz.essencexc.astra.ui.core.Keys.describe(it) else it }}".lie()
            }
        }
    }
}
