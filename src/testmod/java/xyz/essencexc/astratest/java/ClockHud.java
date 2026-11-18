package xyz.essencexc.astratest.java;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xyz.essencexc.astra.api.hud.HudAnchor;
import xyz.essencexc.astra.api.hud.HudContext;
import xyz.essencexc.astra.api.hud.HudElement;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** A custom HUD written against HudContext: an item icon, a label and the time. */
public final class ClockHud extends HudElement {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** Created on first draw: item stacks can't be made while mods are still loading. */
    private ItemStack clock;

    public ClockHud() {
        super("clock", "Clock", HudAnchor.TOP_CENTER, 0, 4);
    }

    private String time() {
        return LocalTime.now().format(FORMAT);
    }

    @Override
    public float width() {
        return 4 + 16 + 4 + textWidth("Time") + 6 + textWidth(time(), 8, true) + 6;
    }

    @Override
    public float height() {
        return 20;
    }

    @Override
    public void draw(HudContext ctx) {
        ctx.background(width(), height());
        if (clock == null) clock = new ItemStack(Items.CLOCK);
        ctx.item(clock, 4, 2);
        float y = (height() - ctx.lineHeight()) / 2;
        float x = 24;
        ctx.text("Time", x, y, ctx.mutedColor());
        x += ctx.textWidth("Time") + 6;
        ctx.text(time(), x, y, ctx.accent(), 8, true);
    }
}
