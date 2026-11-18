package xyz.essencexc.astratest.java;

import com.mojang.blaze3d.platform.InputConstants;
import foo.starred.snowbird.api.ChatKt;
import meteordevelopment.orbit.EventHandler;
import xyz.essencexc.astra.api.Module;
import xyz.essencexc.astra.api.annotations.*;
import xyz.essencexc.astra.api.event.TickEvent;
import xyz.essencexc.astra.api.function.ValueFormatter;
import net.minecraft.core.BlockPos;
import xyz.essencexc.astra.api.hud.HudAnchor;
import xyz.essencexc.astra.api.hud.kit.StatCard;
import xyz.essencexc.astra.api.keybind.AstraKeybind;
import xyz.essencexc.astra.api.value.IntRange;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** PLAN.md §3.1: a Java module declared OneConfig-style with annotated static fields. */
public final class FishingModule extends Module {
    public static final FishingModule INSTANCE = new FishingModule();

    @MasterToggle
    public static boolean enabled = false;

    @Keybind(title = "Toggle Fishing Macro", section = "Fishing Macro")
    public static AstraKeybind toggleKey = AstraKeybind.of(InputConstants.KEY_R).onPress(() -> INSTANCE.toggle());

    @Dropdown(title = "Fishing Type", section = "Fishing Macro")
    public static FishingType type = FishingType.NORMAL;

    @Switch(title = "Sneak While Fishing", section = "Fishing Macro", icon = "astraconfig_testmod:icons/fish.svg")
    public static boolean sneak = false;

    @Switch(title = "Trash Removal", section = "Fishing Macro",
            description = "Removes junk items from your inventory between casts. Requires trash locations to be added first with a command, and only runs while you are standing still near the water so it never interrupts a catch.")
    public static boolean trash = false;

    @Slider(title = "Use Axe On Mobs Below HP", section = "Fishing Macro", min = 0, max = 1_000_000, step = 1_000)
    @DependsOn("sneak")
    public static int axeHp = 100_000;

    @RangeSlider(title = "Reel Delay", section = "Fishing Macro", min = 0, max = 1_000, step = 10, minSpread = 20,
                 formatter = ValueFormatter.Millis.class)
    public static IntRange reelDelay = IntRange.of(120, 250);

    @Color(title = "Highlight Colour", section = "Visuals")
    public static int highlight = 0xFF3B82F6;

    @Text(title = "Webhook Name", section = "Visuals", placeholder = "Fishing Bot")
    public static String webhookName = "";

    @Info(title = "Note", section = "Visuals", type = InfoType.WARNING)
    public static String note = "Colours apply after the next cast.";

    @MultiSelect(title = "Catch Types", section = "Fishing Macro", description = "Which catches count for statistics")
    public static EnumSet<FishingType> catchTypes = EnumSet.of(FishingType.NORMAL);

    @ItemList(title = "Trusted Players", section = "Visuals", placeholder = "Player name", maxItems = 20)
    public static List<String> trusted = new ArrayList<>(List.of("Notch"));

    @Text(title = "Notes", section = "Visuals", multiline = true, placeholder = "Anything to remember...")
    public static String notes = "";

    @Slider(title = "Max Catch Weight", section = "Fishing Macro", min = 1, max = 1_000_000, logarithmic = true,
            description = "Log scale: every factor of ten gets the same slider distance.")
    public static int maxWeight = 1_000;

    @Position(title = "Fishing Spot", section = "Fishing Macro", description = "Where to stand. \"Here\" uses your position.")
    public static BlockPos spot = new BlockPos(0, 64, 0);

    @Hud(title = "Clock", section = "Visuals", description = "A custom HUD drawn through HudContext. Try the Style button: font, colours, background and hide rules all apply.")
    public static final ClockHud clock = new ClockHud();

    @Hud(section = "Visuals")
    public static final StatCard stats = new StatCard("fishing_stats", "Fishing", HudAnchor.TOP_LEFT, 4, 30)
            .subtitle(() -> enabled ? "Session running" : "Paused")
            .headline(() -> String.valueOf(FishingModule.enabledTicks / 20), "seconds")
            .progress(() -> (FishingModule.enabledTicks % 1200) / 1200.0)
            .row("Ticks", () -> String.valueOf(FishingModule.enabledTicks))
            .row("Type", () -> FishingModule.type.name())
            .row("Reel", () -> FishingModule.reelDelay.from() + "-" + FishingModule.reelDelay.to() + "ms")
            .row("Sneak", () -> FishingModule.sneak ? "on" : "off", () -> FishingModule.sneak ? 0xFF47DE9C : 0xFFFF6B7D);

    @Section("Pond Scanning")
    public static final class Pond {
        @Dropdown(title = "Pond Selection", description = "Controls where the macro looks when casting")
        public static PondMode selection = PondMode.AUTOMATIC;

        @Slider(title = "Pond Edge Threshold", min = 0, max = 10)
        @PreviousNames("edgeThreshold")
        public static int edge = 2;
    }

    /** Counts ticks while enabled, to check @EventHandler subscription follows the master toggle. */
    public static int enabledTicks = 0;

    @Button(title = "Reset Statistics", text = "Reset", section = "Statistics")
    private void resetStats() {
        enabledTicks = 0;
    }

    private FishingModule() {
        super("fishing", "Fishing");
        description("A feature-rich fishing macro with support for hotspots, trophies and more.");
        accent(0xFF3B82F6);
        icon("astraconfig_testmod:icons/fish.svg");

        hideIf("axeHp", () -> type != FishingType.NORMAL);
        onChange("sneak", (Boolean v) -> ChatKt.lie("[Astra Java test] sneak = " + v));
    }

    @EventHandler
    private void onTick(TickEvent e) {
        enabledTicks++;
    }

    @Override
    public void onEnable() {
        ChatKt.lie("[Astra Java test] Fishing enabled");
    }

    @Override
    public void onDisable() {
        ChatKt.lie("[Astra Java test] Fishing disabled after " + enabledTicks + " ticks");
    }
}
