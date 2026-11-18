package xyz.essencexc.astra.api.hud

import foo.starred.cascade.graphics.font.CascadeFonts
import foo.starred.cascade.graphics.geometry.CascadeGeometricColor
import foo.starred.snowbird.api.client
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import xyz.essencexc.astra.api.hud.kit.KeyValueGrid
import xyz.essencexc.astra.api.hud.kit.ProgressArc
import xyz.essencexc.astra.ui.theme.Theme
import xyz.essencexc.astra.ui.theme.Themes
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Images
import xyz.essencexc.astra.ui.util.Paint

/** Text measuring and drawing in either HUD font. */
object HudText {
    private fun component(text: String, bold: Boolean): Component =
        if (bold) Component.literal(text).withStyle(ChatFormatting.BOLD) else Component.literal(text)

    @JvmStatic
    fun width(text: String, size: Float, bold: Boolean, font: HudFont): Float = when {
        text.isEmpty() -> 0f
        font == HudFont.MINECRAFT -> client.font.width(component(text, bold)) * (size / 8f)
        else -> Paint.width(text, size, bold)
    }

    @JvmStatic
    fun lineHeight(size: Float, font: HudFont): Float =
        if (font == HudFont.MINECRAFT) client.font.lineHeight * (size / 8f) - size / 8f else Paint.lineHeight(size)

    @JvmStatic
    fun draw(g: GuiGraphicsExtractor, text: String, x: Float, y: Float, color: Int, size: Float, bold: Boolean, font: HudFont, shadow: Boolean) {
        if (text.isEmpty() || Colors.alpha(color) == 0) return
        if (font == HudFont.ASTRA) {
            CascadeFonts.sans.extract(g, component(text, bold), x, y, CascadeGeometricColor(color), shadow, size)
            return
        }
        val pose = g.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        val s = size / 8f
        if (s != 1f) pose.scale(s, s)
        g.text(client.font, component(text, bold), 0, 0, color, shadow)
        pose.popMatrix()
    }
}

/**
 * What a [HudElement] draws with. Coordinates are the element's own (0, 0 = top-left; Astra has
 * already applied position and scale). Text, colours and the background follow the player's
 * [style] choices, so elements drawn through the context respect them automatically.
 */
class HudContext internal constructor(
    /** The raw graphics, for anything the context doesn't cover. */
    @get:JvmName("graphics") val graphics: GuiGraphicsExtractor,
    /** True in the HUD editor and style previews: draw example data. */
    @get:JvmName("preview") val preview: Boolean,
    @get:JvmName("element") val element: HudElement,
) {
    @get:JvmName("style") val style: HudStyle get() = element.layout.style
    @get:JvmName("theme") val theme: Theme get() = Themes.current

    /** Accent: the player's choice, else the module's colour, else the theme's. */
    fun accent(): Int = style.accent.takeIf { it != 0 } ?: element.module?.accent?.takeIf { it != 0 } ?: theme.accent

    /** Main text colour. */
    fun textColor(): Int = style.textColor.takeIf { it != 0 } ?: theme.text

    /** Secondary text colour (labels). */
    fun mutedColor(): Int = style.textColor.takeIf { it != 0 }?.let { Colors.fade(it, 0.65f) } ?: theme.textMuted

    fun backgroundColor(): Int = style.backgroundColor.takeIf { it != 0 } ?: Colors.fade(theme.surface, 0.88f)

    // ---- text ---------------------------------------------------------------------------------

    @JvmOverloads
    fun text(text: String, x: Float, y: Float, color: Int = textColor(), size: Float = DEFAULT_TEXT_SIZE, bold: Boolean = false) =
        HudText.draw(graphics, text, x, y, color, size, bold, style.font, style.shadow)

    /** Draws [text] right-aligned so it ends at [right]. */
    @JvmOverloads
    fun textRight(text: String, right: Float, y: Float, color: Int = textColor(), size: Float = DEFAULT_TEXT_SIZE, bold: Boolean = false) =
        text(text, right - textWidth(text, size, bold), y, color, size, bold)

    /** Draws [text] centred on [cx]. */
    @JvmOverloads
    fun textCentered(text: String, cx: Float, y: Float, color: Int = textColor(), size: Float = DEFAULT_TEXT_SIZE, bold: Boolean = false) =
        text(text, cx - textWidth(text, size, bold) / 2f, y, color, size, bold)

    @JvmOverloads
    fun textWidth(text: String, size: Float = DEFAULT_TEXT_SIZE, bold: Boolean = false): Float = HudText.width(text, size, bold, style.font)

    @JvmOverloads
    fun lineHeight(size: Float = DEFAULT_TEXT_SIZE): Float = HudText.lineHeight(size, style.font)

    // ---- shapes -------------------------------------------------------------------------------

    /** The element's background panel, if the player has it on. Usually the first thing drawn. */
    @JvmOverloads
    fun background(width: Float, height: Float, radius: Float = theme.radiusMedium) {
        if (style.background) Paint.rect(graphics, 0f, 0f, width, height, backgroundColor(), radius)
    }

    @JvmOverloads
    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 0f) = Paint.rect(graphics, x, y, w, h, color, radius)

    @JvmOverloads
    fun outline(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 0f, thickness: Float = 1f) = Paint.outline(graphics, x, y, w, h, color, radius, thickness)

    fun circle(cx: Float, cy: Float, r: Float, color: Int) = Paint.circle(graphics, cx, cy, r, color)

    @JvmOverloads
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, thickness: Float = 1f) = Paint.line(graphics, x1, y1, x2, y2, color, thickness)

    /** A progress ring (0..1, clockwise from the top) in the accent colour unless given. */
    @JvmOverloads
    fun progressArc(cx: Float, cy: Float, radius: Float, thickness: Float, progress: Float, color: Int = accent()) =
        ProgressArc.draw(graphics, cx, cy, radius, thickness, progress, color)

    /** A label/value grid; returns its height. */
    @JvmOverloads
    fun keyValueGrid(x: Float, y: Float, width: Float, entries: List<KeyValueGrid.Entry>, columns: Int = 2): Float {
        KeyValueGrid.draw(this, x, y, width, entries, columns)
        return KeyValueGrid.height(this, entries, columns)
    }

    /** A 16×16 item icon at (x, y). */
    fun item(stack: ItemStack, x: Float, y: Float) {
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        graphics.item(stack, 0, 0)
        pose.popMatrix()
    }

    /** A PNG/SVG resource (e.g. `"mymod:icons/fish.svg"`); false if it couldn't be drawn. */
    @JvmOverloads
    fun image(id: String, x: Float, y: Float, w: Float, h: Float, tint: Int = -1): Boolean = Images.draw(graphics, id, x, y, w, h, tint)

    companion object {
        /** Default HUD text size: Minecraft's own text height at GUI scale. */
        const val DEFAULT_TEXT_SIZE = 8f
    }
}
