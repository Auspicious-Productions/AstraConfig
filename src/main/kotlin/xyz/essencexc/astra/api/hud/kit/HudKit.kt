package xyz.essencexc.astra.api.hud.kit

import foo.starred.cascade.graphics.extensions.arc.arc
import foo.starred.cascade.graphics.extensions.arc.ring
import foo.starred.cascade.graphics.geometry.CascadeGeometricColor
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.hud.HudAnchor
import xyz.essencexc.astra.api.hud.HudContext
import xyz.essencexc.astra.api.hud.HudElement
import xyz.essencexc.astra.api.hud.HudFont
import xyz.essencexc.astra.api.hud.HudText
import xyz.essencexc.astra.ui.util.Colors
import java.util.function.DoubleSupplier
import java.util.function.IntSupplier
import java.util.function.Supplier

/** A circular progress ring. Usually drawn through [HudContext.progressArc]. */
object ProgressArc {
    /**
     * @param progress 0..1, clockwise from the top.
     * @param track colour of the unfilled ring; 0 = a faint version of [color].
     */
    @JvmStatic
    @JvmOverloads
    fun draw(g: GuiGraphicsExtractor, cx: Float, cy: Float, radius: Float, thickness: Float, progress: Float, color: Int, track: Int = 0) {
        val inner = (radius - thickness).coerceAtLeast(0f)
        g.ring(cx, cy, inner, radius, CascadeGeometricColor(if (track != 0) track else Colors.fade(color, 0.22f)))
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f) return
        g.arc(cx, cy, inner, radius, -90f, -90f + 360f * p, true, CascadeGeometricColor(color))
    }
}

/** A grid of label/value pairs. Usually drawn through [HudContext.keyValueGrid]. */
object KeyValueGrid {
    /** One cell. [value] and [color] are read every frame. */
    class Entry @JvmOverloads constructor(
        @get:JvmName("key") val key: String,
        @get:JvmName("value") val value: Supplier<String>,
        /** Value colour; null = the HUD's text colour. */
        @get:JvmName("color") val color: IntSupplier? = null,
    )

    private const val KEY_SIZE = 7f
    private const val VALUE_SIZE = 8f
    private const val ROW_GAP = 5f

    private fun cellHeight(font: HudFont) = HudText.lineHeight(KEY_SIZE, font) + 2f + HudText.lineHeight(VALUE_SIZE, font)

    /** Height of [entries] in [columns] columns for an element using [font]. */
    @JvmStatic
    @JvmOverloads
    fun height(entries: List<Entry>, columns: Int = 2, font: HudFont = HudFont.ASTRA): Float {
        val rows = (entries.size + columns - 1) / columns
        return if (rows == 0) 0f else rows * cellHeight(font) + (rows - 1) * ROW_GAP
    }

    @JvmStatic
    fun height(ctx: HudContext, entries: List<Entry>, columns: Int): Float = height(entries, columns, ctx.style.font)

    @JvmStatic
    @JvmOverloads
    fun draw(ctx: HudContext, x: Float, y: Float, width: Float, entries: List<Entry>, columns: Int = 2) {
        val colW = width / columns
        val font = ctx.style.font
        for ((i, e) in entries.withIndex()) {
            val cx = x + (i % columns) * colW
            val cy = y + (i / columns) * (cellHeight(font) + ROW_GAP)
            ctx.text(e.key, cx, cy, ctx.mutedColor(), KEY_SIZE)
            val v = runCatching { e.value.get() }.getOrDefault("?")
            ctx.text(v, cx, cy + HudText.lineHeight(KEY_SIZE, font) + 2f, e.color?.asInt ?: ctx.textColor(), VALUE_SIZE, true)
        }
    }
}

/**
 * A ready-made stat card HUD: progress ring, title and subtitle, a big headline number and a
 * key/value grid. Every text is a supplier, read each frame. Follows the player's style choices.
 *
 * ```java
 * @Hud public static final StatCard stats = new StatCard("fishing_stats", "Fishing")
 *     .subtitle(() -> "Session " + minutes + "m")
 *     .headline(() -> String.valueOf(catches), "catches")
 *     .progress(() -> catches / 100.0)
 *     .row("Per hour", () -> perHour)
 *     .row("Trophies", () -> trophies, () -> 0xFFFFC44D);
 * ```
 */
open class StatCard @JvmOverloads constructor(
    id: String,
    title: String,
    anchor: HudAnchor = HudAnchor.TOP_LEFT,
    x: Float = 4f,
    y: Float = 4f,
) : HudElement(id, title, anchor, x, y) {
    private var subtitle: Supplier<String>? = null
    private var headline: Supplier<String>? = null
    private var headlineLabel: String = ""
    private var progress: DoubleSupplier? = null
    private var ringText: Supplier<String>? = null
    private var cardWidth = 150f
    private var columns = 2
    private val rows = ArrayList<KeyValueGrid.Entry>()

    fun subtitle(text: Supplier<String>): StatCard = apply { subtitle = text }

    /** The big number, with an optional muted [label] after it ("catches"). */
    @JvmOverloads
    fun headline(text: Supplier<String>, label: String = ""): StatCard = apply { headline = text; headlineLabel = label }

    /** Fills the ring, 0..1. Without it the ring shows the title's first letter. */
    fun progress(value: DoubleSupplier): StatCard = apply { progress = value }

    /** Text inside the ring; defaults to the progress percentage. */
    fun ringText(text: Supplier<String>): StatCard = apply { ringText = text }

    @JvmOverloads
    fun row(key: String, value: Supplier<String>, color: IntSupplier? = null): StatCard = apply { rows += KeyValueGrid.Entry(key, value, color) }

    fun width(w: Float): StatCard = apply { cardWidth = w }
    fun columns(n: Int): StatCard = apply { columns = n.coerceAtLeast(1) }

    private val pad = 8f
    private val ring = 26f
    private val headlineSize = 16f

    override fun width(): Float = cardWidth

    override fun height(): Float {
        var h = pad + ring
        if (headline != null) h += 6f + lineHeight(headlineSize)
        if (rows.isNotEmpty()) h += 8f + 1f + 7f + KeyValueGrid.height(rows, columns, layout.style.font)
        return h + pad
    }

    override fun draw(ctx: HudContext) {
        val a = ctx.accent()
        val w = width()
        val h = height()
        val r = ctx.theme.radiusLarge
        ctx.background(w, h, r)
        if (layout.style.background) ctx.outline(0f, 0f, w, h, Colors.fade(a, 0.35f), r)

        // Ring + title.
        val cx = pad + ring / 2f
        val cy = pad + ring / 2f
        val p = progress?.let { runCatching { it.asDouble.toFloat() }.getOrDefault(0f) }
        if (p != null) {
            ctx.progressArc(cx, cy, ring / 2f, 3f, p, a)
            val inner = ringText?.let { runCatching { it.get() }.getOrDefault("") } ?: "${Math.round(p * 100)}%"
            ctx.textCentered(inner, cx, cy - ctx.lineHeight(6.5f) / 2f, ctx.textColor(), 6.5f, true)
        } else {
            ctx.circle(cx, cy, ring / 2f, Colors.fade(a, 0.18f))
            ctx.textCentered(title.take(1).uppercase(), cx, cy - ctx.lineHeight(10f) / 2f, a, 10f, true)
        }
        val tx = pad + ring + 8f
        val sub = subtitle?.let { runCatching { it.get() }.getOrDefault("") }.orEmpty()
        val titleY = if (sub.isEmpty()) cy - ctx.lineHeight() / 2f else pad + 2f
        ctx.text(title, tx, titleY, ctx.textColor(), bold = true)
        if (sub.isNotEmpty()) ctx.text(sub, tx, titleY + ctx.lineHeight() + 2f, ctx.mutedColor(), 7f)

        var y = pad + ring
        headline?.let { s ->
            y += 6f
            val text = runCatching { s.get() }.getOrDefault("?")
            ctx.text(text, pad, y, a, headlineSize, true)
            if (headlineLabel.isNotEmpty()) {
                val lw = ctx.textWidth(text, headlineSize, true)
                ctx.text(headlineLabel, pad + lw + 4f, y + ctx.lineHeight(headlineSize) - ctx.lineHeight(7f) - 1f, ctx.mutedColor(), 7f)
            }
            y += ctx.lineHeight(headlineSize)
        }
        if (rows.isNotEmpty()) {
            y += 8f
            ctx.rect(pad, y, w - pad * 2, 1f, Colors.fade(ctx.mutedColor(), 0.35f))
            y += 1f + 7f
            ctx.keyValueGrid(pad, y, w - pad * 2, rows, columns)
        }
    }
}

/** The simplest HUD: one line of text with an optional label, e.g. "Speed  12.4 m/s". */
open class TextHud @JvmOverloads constructor(
    id: String,
    title: String,
    private val text: Supplier<String>,
    private val label: String = "",
    anchor: HudAnchor = HudAnchor.TOP_LEFT,
    x: Float = 4f,
    y: Float = 4f,
) : HudElement(id, title, anchor, x, y) {
    private val pad = 5f

    private fun value() = runCatching { text.get() }.getOrDefault("?")

    override fun width(): Float {
        val lw = if (label.isEmpty()) 0f else textWidth(label) + 6f
        return pad * 2 + lw + textWidth(value(), bold = true)
    }

    override fun height(): Float = lineHeight() + pad * 2

    override fun draw(ctx: HudContext) {
        ctx.background(width(), height())
        var x = pad
        if (label.isNotEmpty()) {
            ctx.text(label, x, pad, ctx.mutedColor())
            x += ctx.textWidth(label) + 6f
        }
        ctx.text(value(), x, pad, ctx.accent(), bold = true)
    }
}
