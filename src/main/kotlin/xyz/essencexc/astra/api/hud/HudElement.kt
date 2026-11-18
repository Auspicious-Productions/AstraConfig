package xyz.essencexc.astra.api.hud

import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.function.Condition

/** Which point of the screen (and of the element) a HUD is pinned to. */
enum class HudAnchor(@get:JvmName("fx") val fx: Float, @get:JvmName("fy") val fy: Float) {
    TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
    CENTER_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), CENTER_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f);

    companion object {
        /** The anchor for a point at fractions (fx, fy) of the screen: whichever third it falls in. */
        @JvmStatic
        fun nearest(fx: Float, fy: Float): HudAnchor {
            fun third(f: Float) = when {
                f < 1f / 3f -> 0
                f > 2f / 3f -> 2
                else -> 1
            }
            return entries[third(fy) * 3 + third(fx)]
        }
    }
}

/** Font for HUD text: Astra's smooth TTF font, or Minecraft's own pixel font. */
enum class HudFont { ASTRA, MINECRAFT }

/**
 * How a HUD looks and when it hides, chosen by the player (the "Style" popup on the HUD's row).
 * Colours are ARGB; 0 means "use the theme / module colour".
 */
class HudStyle @JvmOverloads constructor(
    @get:JvmName("background") val background: Boolean = true,
    @get:JvmName("backgroundColor") val backgroundColor: Int = 0,
    @get:JvmName("textColor") val textColor: Int = 0,
    @get:JvmName("accent") val accent: Int = 0,
    @get:JvmName("shadow") val shadow: Boolean = false,
    @get:JvmName("font") val font: HudFont = HudFont.ASTRA,
    /** Hide while the chat is open. */
    @get:JvmName("hideWithChat") val hideWithChat: Boolean = false,
    /** Hide while the F3 debug screen is open. */
    @get:JvmName("hideWithDebug") val hideWithDebug: Boolean = true,
) {
    fun copy(
        background: Boolean = this.background,
        backgroundColor: Int = this.backgroundColor,
        textColor: Int = this.textColor,
        accent: Int = this.accent,
        shadow: Boolean = this.shadow,
        font: HudFont = this.font,
        hideWithChat: Boolean = this.hideWithChat,
        hideWithDebug: Boolean = this.hideWithDebug,
    ) = HudStyle(background, backgroundColor, textColor, accent, shadow, font, hideWithChat, hideWithDebug)

    override fun equals(other: Any?) = other is HudStyle && other.background == background && other.backgroundColor == backgroundColor &&
        other.textColor == textColor && other.accent == accent && other.shadow == shadow && other.font == font &&
        other.hideWithChat == hideWithChat && other.hideWithDebug == hideWithDebug

    override fun hashCode() = listOf(background, backgroundColor, textColor, accent, shadow, font, hideWithChat, hideWithDebug).hashCode()

    companion object {
        @JvmField
        val DEFAULT = HudStyle()
    }
}

/**
 * Where a HUD sits and how it looks, saved in the config. The element's [anchor] point is placed at
 * the screen's [anchor] point plus ([x], [y]) GUI pixels, so it stays in place across window sizes
 * and GUI scales.
 */
class HudLayout @JvmOverloads constructor(
    @get:JvmName("anchor") val anchor: HudAnchor,
    @get:JvmName("x") val x: Float,
    @get:JvmName("y") val y: Float,
    @get:JvmName("scale") val scale: Float = 1f,
    /** Turned off in the HUD editor or the menu. */
    @get:JvmName("shown") val shown: Boolean = true,
    @get:JvmName("style") val style: HudStyle = HudStyle.DEFAULT,
) {
    fun withShown(shown: Boolean) = HudLayout(anchor, x, y, scale, shown, style)
    fun withScale(scale: Float) = HudLayout(anchor, x, y, scale.coerceIn(MIN_SCALE, MAX_SCALE), shown, style)
    fun withStyle(style: HudStyle) = HudLayout(anchor, x, y, scale, shown, style)
    fun withPosition(anchor: HudAnchor, x: Float, y: Float) = HudLayout(anchor, x, y, scale, shown, style)

    /** Top-left corner on a [screenW]×[screenH] screen for an element of size [w]×[h] (unscaled). */
    fun topLeft(screenW: Float, screenH: Float, w: Float, h: Float): Pair<Float, Float> {
        val sw = w * scale
        val sh = h * scale
        return (screenW * anchor.fx + x - sw * anchor.fx) to (screenH * anchor.fy + y - sh * anchor.fy)
    }

    override fun equals(other: Any?) = other is HudLayout && other.anchor == anchor && other.x == x && other.y == y &&
        other.scale == scale && other.shown == shown && other.style == style

    override fun hashCode() = (((anchor.hashCode() * 31 + x.hashCode()) * 31 + y.hashCode()) * 31 + scale.hashCode() * 2 + if (shown) 1 else 0) * 31 + style.hashCode()
    override fun toString() = "HudLayout($anchor, $x, $y, x$scale${if (shown) "" else ", hidden"})"

    companion object {
        const val MIN_SCALE = 0.25f
        const val MAX_SCALE = 4f

        /** The layout that puts an element's top-left at ([left], [top]), re-anchored to the nearest third. */
        @JvmStatic
        @JvmOverloads
        fun at(left: Float, top: Float, w: Float, h: Float, scale: Float, shown: Boolean, screenW: Float, screenH: Float, style: HudStyle = HudStyle.DEFAULT): HudLayout {
            val sw = w * scale
            val sh = h * scale
            val anchor = HudAnchor.nearest((left + sw / 2f) / screenW, (top + sh / 2f) / screenH)
            val x = left + sw * anchor.fx - screenW * anchor.fx
            val y = top + sh * anchor.fy - screenH * anchor.fy
            return HudLayout(anchor, Math.round(x).toFloat(), Math.round(y).toFloat(), scale, shown, style)
        }
    }
}

/**
 * Something drawn on the in-game HUD. Subclass it, then declare it as a `@Hud` field on a module
 * (or `val x by hud(...)` in a `KtModule`). Astra draws it while the module is enabled, saves its
 * position, scale and style, and lists it in the HUD editor.
 *
 * Draw through the [HudContext] at (0, 0) = your top-left; Astra applies position and scale. The
 * context follows the player's style choices (font, colours, background, shadow).
 *
 * ```java
 * public final class SpeedHud extends HudElement {
 *     public SpeedHud() { super("speed", "Speed", HudAnchor.TOP_LEFT, 4, 4); }
 *     public float width()  { return 8 + textWidth("Speed 12.4"); }
 *     public float height() { return 6 + lineHeight(); }
 *     public void draw(HudContext ctx) {
 *         ctx.background(width(), height());
 *         ctx.text(ctx.preview() ? "Speed 12.4" : "Speed " + speed(), 4, 3);
 *     }
 * }
 * ```
 *
 * @param id stable id used in the config; unique within the module.
 */
abstract class HudElement @JvmOverloads constructor(
    @get:JvmName("id") val id: String,
    @get:JvmName("title") val title: String,
    defaultAnchor: HudAnchor = HudAnchor.TOP_LEFT,
    defaultX: Float = 4f,
    defaultY: Float = 4f,
    defaultStyle: HudStyle = HudStyle.DEFAULT,
) {
    /** The layout it starts with and resets to. */
    @get:JvmName("defaultLayout")
    val defaultLayout: HudLayout = HudLayout(defaultAnchor, defaultX, defaultY, 1f, true, defaultStyle)

    /** Current position, scale, visibility and style. Changed by the editor and Style popup; saved. */
    @Volatile
    var layout: HudLayout = defaultLayout

    /** The module it belongs to; set when the module is registered. */
    var module: Module? = null
        internal set

    private var hide: Condition? = null

    /** Unscaled width in GUI pixels. May change from frame to frame. */
    abstract fun width(): Float

    /** Unscaled height in GUI pixels. */
    abstract fun height(): Float

    /**
     * Draws the element at (0, 0). `ctx.preview()` is true in the HUD editor and style previews: show
     * example data so it can be placed even when there's nothing real to display.
     */
    abstract fun draw(ctx: HudContext)

    /** Extra in-game visibility rule (e.g. only in a certain area). The editor ignores it. */
    open fun visible(): Boolean = true

    /** Hides the element in game while [condition] is true. */
    fun hideIf(condition: Condition): HudElement = apply { hide = condition }

    internal fun hiddenByCondition(): Boolean = hide?.test() == true || !visible()

    /** Back to the default position and scale, keeping the shown flag and style. */
    fun resetLayout() {
        val d = defaultLayout
        layout = HudLayout(d.anchor, d.x, d.y, d.scale, layout.shown, layout.style)
    }

    // ---- measuring, for width() / height(): follows the player's font choice ------------------

    /** Width of [text] in the current HUD font. */
    @JvmOverloads
    fun textWidth(text: String, size: Float = HudContext.DEFAULT_TEXT_SIZE, bold: Boolean = false): Float =
        HudText.width(text, size, bold, layout.style.font)

    /** Height of one line of text in the current HUD font. */
    @JvmOverloads
    fun lineHeight(size: Float = HudContext.DEFAULT_TEXT_SIZE): Float = HudText.lineHeight(size, layout.style.font)

    override fun toString(): String = "HudElement($id)"
}
