package xyz.essencexc.astra.ui.option

import xyz.essencexc.astra.ui.widgets.IconButton
import xyz.essencexc.astra.ui.util.Images
import xyz.essencexc.astra.ui.layout.Overlay
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.core.FocusManager
import foo.starred.cascade.events.impl.MouseEvent
import foo.starred.cascade.events.impl.KeyEvent
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.keybind.KeyBinding
import xyz.essencexc.astra.api.value.FloatRange
import xyz.essencexc.astra.api.value.IntRange
import xyz.essencexc.astra.core.NumberKind
import xyz.essencexc.astra.core.Option
import xyz.essencexc.astra.core.OptionSpec
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.layout.Column
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint
import xyz.essencexc.astra.ui.widgets.Binding
import xyz.essencexc.astra.ui.widgets.Button
import xyz.essencexc.astra.ui.widgets.Checkbox
import xyz.essencexc.astra.ui.widgets.ColorPicker
import xyz.essencexc.astra.ui.widgets.Dropdown
import xyz.essencexc.astra.ui.widgets.InfoBox
import xyz.essencexc.astra.ui.widgets.KeybindButton
import xyz.essencexc.astra.ui.widgets.NumberField
import xyz.essencexc.astra.ui.widgets.NumberScale
import xyz.essencexc.astra.ui.widgets.RangeSlider
import xyz.essencexc.astra.ui.widgets.Slider
import xyz.essencexc.astra.ui.widgets.Switch
import xyz.essencexc.astra.ui.widgets.TextField

/** Builds the widget for an option. Every write goes through [Option.set], so listeners fire and the config saves. */
internal object OptionWidgets {
    @Suppress("UNCHECKED_CAST")
    fun create(option: Option<*>): AstraElement? {
        val o = option as Option<Any?>
        val w: AstraElement = when (val spec = o.spec) {
            OptionSpec.SwitchSpec, OptionSpec.MasterToggleSpec -> Switch(bind(o) { it as Boolean })
            OptionSpec.CheckboxSpec -> Checkbox(bind(o) { it as Boolean })

            is OptionSpec.SliderSpec -> Slider(
                numeric(o, spec.kind),
                NumberScale(spec.min, spec.max, spec.step, spec.kind == NumberKind.INT, spec.step, spec.logarithmic),
                spec.formatter,
            )

            is OptionSpec.RangeSliderSpec -> RangeSlider(
                Binding.of(
                    {
                        when (val v = o.get()) {
                            is IntRange -> v.from.toDouble() to v.to.toDouble()
                            is FloatRange -> v.from.toDouble() to v.to.toDouble()
                            else -> 0.0 to 0.0
                        }
                    },
                    { (a, b) -> o.set(if (spec.integer) IntRange.of(Math.round(a).toInt(), Math.round(b).toInt()) else FloatRange.of(a.toFloat(), b.toFloat())) },
                ),
                NumberScale(spec.min, spec.max, spec.step, spec.integer),
                spec.minSpread,
                spec.formatter,
            )

            is OptionSpec.NumberSpec -> NumberField(
                numeric(o, spec.kind),
                NumberScale(spec.min, spec.max, 0.0, spec.kind == NumberKind.INT, if (spec.kind == NumberKind.INT) 1.0 else 0.1),
                spec.unit,
            )

            is OptionSpec.DropdownSpec -> {
                val enumType = spec.enumType
                val constants = enumType?.enumConstants
                Dropdown(
                    Binding.of(
                        { if (constants != null) (o.get() as Enum<*>).ordinal else o.get() as Int },
                        { i -> o.set(if (constants != null) constants[i] else i) },
                    ),
                    spec.labels,
                )
            }

            is OptionSpec.TextSpec -> if (spec.multiline) {
                xyz.essencexc.astra.ui.widgets.TextArea(o.get() as String).apply {
                    placeholder = spec.placeholder
                    maxLength = spec.maxLength
                    fixedWidth = 220f
                    onChange = { o.set(it) }
                    onCommit = { o.set(it) }
                }
            } else {
                TextField(o.get() as String).apply {
                    placeholder = spec.placeholder
                    maxLength = spec.maxLength
                    fixedWidth = 150f
                    onChange = { o.set(it) }
                    onCommit = { o.set(it) }
                }
            }

            is OptionSpec.ColorSpec -> ColorPicker(bind(o) { it as Int }, spec.alpha)
            OptionSpec.KeybindSpec -> KeybindButton(bind(o) { it as KeyBinding })
            is OptionSpec.InfoSpec -> InfoBox(spec.type, o.title) { o.get()?.toString() ?: "" }
            is OptionSpec.ButtonSpec -> Button(spec.text, Button.Style.SECONDARY, spec.action)
            is OptionSpec.PositionSpec -> xyz.essencexc.astra.ui.widgets.PositionInput(
                Binding.of(
                    {
                        when (val v = o.get()) {
                            is net.minecraft.core.BlockPos -> doubleArrayOf(v.x.toDouble(), v.y.toDouble(), v.z.toDouble())
                            is net.minecraft.world.phys.Vec3 -> doubleArrayOf(v.x, v.y, v.z)
                            else -> DoubleArray(3)
                        }
                    },
                    { a ->
                        o.set(
                            if (spec.integer) net.minecraft.core.BlockPos(Math.floor(a[0]).toInt(), Math.floor(a[1]).toInt(), Math.floor(a[2]).toInt())
                            else net.minecraft.world.phys.Vec3(a[0], a[1], a[2])
                        )
                    },
                ),
                spec.integer,
            )
            is OptionSpec.MultiSelectSpec ->xyz.essencexc.astra.ui.widgets.MultiSelect(
                Binding.of(
                    { (o.get() as List<*>).mapNotNull { v -> spec.choices.indexOf(v).takeIf { it >= 0 } }.toSet() },
                    { set -> o.set(spec.choices.filterIndexed { i, _ -> i in set }) },
                ),
                spec.labels,
            )
            is OptionSpec.ItemListSpec -> xyz.essencexc.astra.ui.widgets.ItemListEditor(
                Binding.of({ (o.get() as List<*>).map { it.toString() } }, { o.set(it) }),
                spec.placeholder, spec.maxItems, spec.maxLength,
            )
            is OptionSpec.HudSpec -> {
                @Suppress("UNCHECKED_CAST")
                xyz.essencexc.astra.ui.hud.HudRowControls(o as Option<xyz.essencexc.astra.api.hud.HudLayout>, spec.element)
            }
        }
        if (w is AstraWidget) w.enabledWhen = { !o.disabled }
        return w
    }

    private fun <T> bind(o: Option<Any?>, cast: (Any?) -> T): Binding<T> = Binding.of({ cast(o.get()) }, { o.set(it) })

    private fun numeric(o: Option<Any?>, kind: NumberKind): Binding<Double> =
        Binding.of({ (o.get() as Number).toDouble() }, { o.set(kind.box(it)) })
}

/**
 * One setting: title and description on the left, the widget on the right. `@Info` options span
 * the whole row instead.
 */
internal class OptionRow(val option: Option<*>) : AstraElement() {
    val widget: AstraElement? = OptionWidgets.create(option)
    private val fullWidth = option.spec is OptionSpec.InfoSpec
    private val padX = 10f
    private val padY = 8f
    private val gap = 14f
    private val hl = Motion()

    /** Small reset arrow next to the widget; only shown while the value differs from its default. */
    private val reset: IconButton? =
        if (option.saved && !fullWidth) IconButton(IconButton.Glyph.RESET, { modified }) { resetToDefault() }.apply {
            hideWhenDisabled = true
            tooltip = "Reset to default"
            fixedWidth = 16f
        } else null

    /** Briefly highlighted, e.g. after a search jump. */
    private val pulse = Motion(0f, speed = 3f)
    private var textHoverSince = 0L

    init {
        interact = true // right-click anywhere on the row resets it
        widget?.let { adopt(it) }
        reset?.let { adopt(it) }
        on<MouseEvent.Press> {
            val row = this@OptionRow
            if (Keys.button(button) == 1 && row.modified) {
                row.resetToDefault()
                cancel()
            }
        }
        on<KeyEvent.Press> {
            if (key == InputConstants.KEY_TAB) {
                FocusManager.move(this@OptionRow, Keys.shift())
                cancel()
            }
        }
    }

    /** Saved options whose value differs from the one their field started with. */
    val modified: Boolean get() = option.saved && option.get() != option.default

    fun resetToDefault() {
        option.reset()
        root.dirty()
    }

    /** 1 = shown, 0 = collapsed; eases when [setShown] changes it. */
    private val appear = Motion(1f, speed = 18f)
    private var wanted = true

    /** Shows or hides the row, sliding it open or closed when [animate]. Returns true if anything changed. */
    fun setShown(show: Boolean, animate: Boolean): Boolean {
        if (wanted == show) return false
        wanted = show
        if (show) visible = true
        if (animate) appear.to(show) else appear.snap(if (show) 1f else 0f)
        if (!show && !animate) visible = false
        return true
    }

    val shownTarget: Boolean get() = wanted

    override fun prefHeight(width: Float): Float = fullHeight(width) * appear.get()

    override fun render(graphics: GuiGraphicsExtractor) {
        if (!visible) return
        val t = appear.get()
        if (!appear.settled) root.dirty()
        if (!wanted && t < 0.02f) {
            visible = false
            root.dirty()
            return
        }
        if (t >= 0.999f) {
            super.render(graphics)
            return
        }
        Paint.clip(graphics, x, y, width, height) { super.render(graphics) }
    }

    fun flash() {
        pulse.snap(1f).to(0f)
    }

    private val hasIcon: Boolean get() = Images.available(option.icon.ifBlank { null })
    private val iconW: Float get() = if (hasIcon) 14f else 0f
    private val resetW: Float get() = if (reset != null) 20f else 0f

    private fun widgetW(width: Float): Float = widget?.let { minOf(it.prefWidth(), width * 0.55f) } ?: 0f

    private fun textW(width: Float) = width - padX * 2 - widgetW(width) - gap - resetW - iconW

    /** Description lines as shown: at most [MAX_DESCRIPTION_LINES], the last cut with "..." if longer. */
    private fun descriptionLines(width: Float): Pair<List<String>, Boolean> {
        if (option.description.isEmpty()) return emptyList<String>() to false
        val th = theme
        val all = Paint.wrap(option.description, th.fontSmall, textW(width))
        if (all.size <= MAX_DESCRIPTION_LINES) return all to false
        val shown = all.take(MAX_DESCRIPTION_LINES).toMutableList()
        shown[shown.lastIndex] = Paint.truncate(shown.last() + " ...", th.fontSmall, textW(width))
        return shown to true
    }

    /** Height when fully shown (prefHeight scales it while animating in or out). */
    private fun fullHeight(width: Float): Float {
        val w = widget ?: return padY * 2
        if (fullWidth) return w.prefHeight(width - padX * 2) + padY * 2
        val th = theme
        var text = Paint.lineHeight(th.fontBody)
        val (lines, _) = descriptionLines(width)
        if (lines.isNotEmpty()) text += 3f + Paint.wrappedHeight(lines.size, th.fontSmall)
        return maxOf(text, w.prefHeight(widgetW(width))) + padY * 2
    }

    override fun layout() {
        dirty = false
        val w = widget ?: return
        if (fullWidth) {
            w.place(x + padX, y + padY, width - padX * 2, fullHeight(width) - padY * 2)
            return
        }
        val ww = widgetW(width)
        val wh = w.prefHeight(ww)
        val wx = x + width - padX - ww
        w.place(wx, y + (fullHeight(width) - wh) / 2f, ww, wh)
        reset?.place(wx - 18f, y + (fullHeight(width) - 16f) / 2f, 16f, 16f)
    }

    override fun draw(graphics: GuiGraphicsExtractor) {
        val th = theme
        // Hovered only if nothing (an open dropdown, say) sits on top of the row at the cursor.
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        val over = hit(mx, my) && Overlay.of(this)?.popups?.none { it.hit(mx, my) } != false
        hl.to(over)
        val h = hl.get()
        val p = pulse.get()
        if (h > 0.01f || p > 0.01f) Paint.rect(graphics, x + 3f, y + 2f, width - 6f, height - 4f, Colors.mix(Colors.fade(th.surfaceHover, h * 0.7f), Colors.fade(accentColor(), 0.25f), p), th.radiusMedium)
        if (fullWidth) return

        val disabled = option.disabled
        val title = if (disabled) th.textFaint else th.text
        val (lines, cut) = descriptionLines(width)
        val textBlock = Paint.lineHeight(th.fontBody) + if (lines.isNotEmpty()) 3f + Paint.wrappedHeight(lines.size, th.fontSmall) else 0f
        val ty = y + (fullHeight(width) - textBlock) / 2f
        val tx = x + padX + iconW

        // Changed from its default: a small accent dot in the left margin.
        if (modified) Paint.circle(graphics, x + 5f, ty + Paint.lineHeight(th.fontBody) / 2f, 1.8f, accentColor())
        if (hasIcon) Images.draw(graphics, option.icon, x + padX, ty + (Paint.lineHeight(th.fontBody) - 10f) / 2f, 10f, 10f, if (disabled) Colors.fade(-1, 0.5f) else -1)

        Paint.text(graphics, option.title, tx, ty, title, th.fontBody)
        if (lines.isNotEmpty()) {
            Paint.lines(graphics, lines, tx, ty + Paint.lineHeight(th.fontBody) + 3f, if (disabled) th.textFaint else th.textMuted, th.fontSmall)
        }

        // Full description as a tooltip when it had to be shortened.
        val overText = over && mx >= tx && mx < tx + textW(width) && my >= ty && my < ty + textBlock
        if (cut && overText) {
            if (textHoverSince == 0L) textHoverSince = System.currentTimeMillis()
            if (System.currentTimeMillis() - textHoverSince > 400) Overlay.of(this)?.tooltip(option.description, mouseX, mouseY)
        } else {
            textHoverSince = 0L
        }
    }

    companion object {
        /** Longer descriptions are cut and shown in full in a tooltip. */
        const val MAX_DESCRIPTION_LINES = 2
    }
}


/** A rounded card of [OptionRow]s with dividers. Hides rows whose `hideIf` is true, live. */
internal class OptionCard(rows: List<OptionRow>) : Column() {
    init {
        fill = { theme.surface }
        border = { theme.border }
        radius = { theme.radiusLarge }
        paddingY = 3f
        for (r in rows) adopt(r)
    }

    val rows: List<OptionRow> get() = children.filterIsInstance<OptionRow>()

    override fun draw(graphics: GuiGraphicsExtractor) {
        super.draw(graphics)
        val shown = rows.filter { it.visible }
        for (i in 0 until shown.size - 1) {
            val r = shown[i]
            Paint.rect(graphics, x + 10f, r.y + r.height, width - 20f, 1f, theme.border)
        }
    }
}

/** A section heading followed by its card. */
internal class SectionView(val title: String, rows: List<OptionRow>) : Column() {
    val card = OptionCard(rows)
    val heading: AstraElement = Heading()

    /** Resets every changed row in this section; only shown while something here was changed. */
    private val resetAll = IconButton(IconButton.Glyph.RESET, { card.rows.any { it.modified } }) {
        card.rows.filter { it.modified }.forEach { it.resetToDefault() }
    }.apply {
        hideWhenDisabled = true
        tooltip = "Reset this section"
        fixedWidth = 16f
        fixedHeight = 16f
    }

    init {
        gap = 6f
        adopt(xyz.essencexc.astra.ui.layout.Row().apply {
            gap = 4f
            adopt(heading.apply { grow = true })
            adopt(resetAll)
        })
        adopt(card)
    }

    private inner class Heading : AstraElement() {
        override fun prefHeight(width: Float) = maxOf(Paint.lineHeight(theme.fontTitle) + 2f, 16f)

        override fun draw(graphics: GuiGraphicsExtractor) {
            val th = theme
            val ty = y + (height - Paint.lineHeight(th.fontTitle)) / 2f
            Paint.text(graphics, title, x + 2f, ty, th.text, th.fontTitle, true)
            val lx = x + 2f + Paint.width(title, th.fontTitle, true) + 8f
            Paint.rect(graphics, lx, y + height / 2f, (x + width - lx).coerceAtLeast(0f), 1f, th.border)
        }
    }
}

/**
 * Every setting of a module grouped into sections: what a module page's right column shows.
 * The master toggle isn't a row (the page header has it).
 *
 * Rows hide live when their `hideIf` is true or they don't match [filter]; sections with no
 * visible rows hide too.
 */
internal class ModuleSettingsView(val module: Module) : Column() {
    val sections: List<SectionView>

    private var initialized = false
    private var lastQuery = ""

    /** Per-module search text. Empty = show everything. */
    var filter: String = ""

    init {
        gap = 16f
        val groups = LinkedHashMap<String, MutableList<OptionRow>>()
        for (o in module.options) {
            if (o.spec == OptionSpec.MasterToggleSpec) continue
            groups.getOrPut(o.section.ifEmpty { "General" }) { mutableListOf() } += OptionRow(o)
        }
        sections = groups.map { (name, rows) -> SectionView(name, rows) }
        sections.forEach { adopt(it) }
        accent = module.accent.takeIf { it != 0 }
    }

    fun row(key: String): OptionRow? = sections.flatMap { it.card.rows }.firstOrNull { it.option.key == key }

    /** True if nothing matches the filter. */
    val empty: Boolean get() = sections.none { it.visible }

    override fun draw(graphics: GuiGraphicsExtractor) {
        val q = filter.trim().lowercase()
        var changed = false
        for (s in sections) {
            val sectionMatch = q.isNotEmpty() && s.title.lowercase().contains(q)
            var any = false
            for (r in s.card.rows) {
                val show = !r.option.hidden && (q.isEmpty() || sectionMatch || r.matches(q))
                // Animate hideIf / DependsOn changes; snap on the first frame and while searching.
                if (r.setShown(show, animate = initialized && q == lastQuery)) changed = true
                if (show || r.visible) any = true // keep the section while a row slides out
            }
            if (s.visible != any) {
                s.visible = any
                changed = true
            }
        }
        if (changed) root.dirty()
        initialized = true
        lastQuery = q
        super.draw(graphics)
    }
}

/** True if the option's title, description, keywords or field name contain [q] (lowercase). */
internal fun OptionRow.matches(q: String): Boolean {
    val o = option
    return o.title.lowercase().contains(q) || o.description.lowercase().contains(q) ||
        o.key.lowercase().contains(q) || o.keywords.any { it.lowercase().contains(q) }
}
