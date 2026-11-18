package xyz.essencexc.astratest.kotlin

import foo.starred.snowbird.api.lie
import com.mojang.blaze3d.platform.InputConstants
import meteordevelopment.orbit.EventHandler
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import xyz.essencexc.astra.api.Astra
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.annotations.Button
import xyz.essencexc.astra.api.annotations.Hud
import xyz.essencexc.astra.api.hud.HudAnchor
import xyz.essencexc.astra.api.hud.kit.StatCard
import xyz.essencexc.astra.api.hud.kit.TextHud
import xyz.essencexc.astra.api.annotations.Dropdown
import xyz.essencexc.astra.api.annotations.MasterToggle
import xyz.essencexc.astra.api.annotations.Section
import xyz.essencexc.astra.api.annotations.Slider
import xyz.essencexc.astra.api.annotations.Switch
import xyz.essencexc.astra.api.event.TickEvent
import xyz.essencexc.astra.api.kt.KtModule
import java.lang.invoke.MethodHandles

enum class MiningTarget { GEMSTONE, MITHRIL, GLACITE }
enum class DianaMob { MINOS_INQUISITOR, MINOS_CHAMPION, GAIA_CONSTRUCT, SIAMESE_LYNX, MINOTAUR }

/** PLAN.md §3.2: the same annotations on Kotlin properties of an object. */
object MiningModule : Module("mining", "Mining") {
    @MasterToggle
    var enabled = false

    @Dropdown(title = "Target", section = "Mining Macro")
    var target = MiningTarget.GEMSTONE

    @Switch(title = "Use Pickobulus", section = "Mining Macro")
    var pickobulus = true

    @Slider(title = "Rotation Speed", section = "Mining Macro", min = 1f, max = 20f, step = 0.5f)
    var rotationSpeed = 8.5f

    @Section("Safety")
    object Safety {
        @Switch(title = "Stop When Player Nearby")
        var stopNearPlayers = true

        @Button(title = "Clear Blacklist", text = "Clear")
        fun clearBlacklist() {
            "[Astra Kotlin test] blacklist cleared".lie()
        }
    }

    @Hud(title = "Coordinates HUD", section = "Overlay")
    val coords = TextHud("mining_coords", "Coordinates", {
        foo.starred.snowbird.api.client.player?.let { "%d %d %d".format(it.blockX, it.blockY, it.blockZ) } ?: "12 64 -8"
    }, "XYZ", HudAnchor.TOP_RIGHT, -4f, 4f)

    var enabledTicks = 0

    init {
        description = "Gemstone, mithril and glacite mining."
        icon = "astraconfig_testmod:icons/pick.svg"
        hideIf(::rotationSpeed) { !pickobulus }
    }

    @EventHandler
    private fun onTick(e: TickEvent) {
        enabledTicks++
    }
}

/** PLAN.md §3.3: delegates, like OneConfig's KtConfig. */
object DianaModule : KtModule("diana", "Diana") {
    var enabled by masterToggle()
    var autoWarp by switch("Auto Warp", true, section = "Burrows")
        .onChange { "[Astra Kotlin test] autoWarp = $it".lie() }
    var walkSpeed by slider("Walk Speed", 1.0, 0.5..2.0, step = 0.05, section = "Burrows")
        .hideIf { !autoWarp }
    var clickDelay by rangeSlider("Click Delay", 80..140, bounds = 0..500, step = 5, minSpread = 10, section = "Burrows")
    val toggleKey by keybind("Toggle Diana", InputConstants.KEY_G, section = "Burrows") { onPress { toggle() } }
    var mobName by text("Mob Filter", placeholder = "Minos Inquisitor", section = "Mobs")
    var mobs by multiSelect("Highlight Mobs", setOf(DianaMob.MINOS_INQUISITOR), section = "Mobs")
    var guessSpot by position("Guess Spot", net.minecraft.core.BlockPos(12, 70, -40), section = "Burrows")
    var maxDistance by slider("Max Distance", 100.0, 1.0..100_000.0, step = 1.0, section = "Burrows", logarithmic = true)
    var waypoints by itemList("Waypoints", listOf("12 70 -40"), placeholder = "x y z", section = "Burrows")
    val resetButton by button("Reset Session", text = "Reset", section = "Mobs") { "[Astra Kotlin test] session reset".lie() }
    val burrowHud by hud(
        StatCard("diana_burrows", "Burrows", HudAnchor.CENTER_RIGHT, -4f, 0f)
            .subtitle { if (isEnabled()) "Tracking" else "Paused" }
            .headline({ "${burrows}" }, "found")
            .progress { (burrows % 10) / 10.0 }
            .row("Chain", { "${burrows % 4}/4" })
            .row("Warp", { if (autoWarp) "on" else "off" }, { if (autoWarp) 0xFF47DE9C.toInt() else 0xFFFF6B7D.toInt() }),
        section = "Overlay",
    )
    var burrows = 0

    init {
        description = "Burrow solver and mob helpers."
        icon = "astraconfig_testmod:icons/shovel.png"
    }
}

object KotlinTestMod : ClientModInitializer {
    override fun onInitializeClient() {
        Astra.create("astratest_kt") {
            name = "Astra Kotlin Test"
            command = "astrakt"
            lookup = MethodHandles.lookup()
            category("Mining", MiningModule)
            category("Events", DianaModule)
            openKey = InputConstants.KEY_RCONTROL
            // A custom sidebar page (PLAN §4: Routes, Webhooks, ...).
            page("Routes", "Example custom page") {
                xyz.essencexc.astra.ui.layout.ScrollPane().apply {
                    content.padding = 20f
                    content.gap = 10f
                    content.adopt(xyz.essencexc.astra.ui.widgets.Label("Custom pages are any Astra element. This one is a scroll pane with a few widgets.", muted = true))
                    content.adopt(xyz.essencexc.astra.ui.widgets.InfoBox(xyz.essencexc.astra.api.annotations.InfoType.INFO, "Routes") { "No routes recorded yet." })
                    content.adopt(xyz.essencexc.astra.ui.widgets.Button("Record route", xyz.essencexc.astra.ui.widgets.Button.Style.PRIMARY) { "[Astra Kotlin test] recording...".lie() }.apply { grow = false })
                }
            }
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(ClientCommands.literal("astragallery").executes {
                WidgetGallery().open()
                1
            })
        }
    }
}
