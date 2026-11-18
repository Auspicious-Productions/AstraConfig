package xyz.essencexc.astra.ui.widgets

import com.mojang.blaze3d.platform.InputConstants
import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.function.ValueFormatter
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint
import kotlin.math.abs
import kotlin.math.roundToLong

/** Min/max/step maths shared by the sliders and the number input. */
class NumberScale @JvmOverloads constructor(
    val min: Double,
    val max: Double,
    val step: Double,
    val integer: Boolean,
    val increment: Double = step,
    /** Log scale: the track maps to min·(max/min)^t, so each factor of 10 gets the same width. Needs min > 0. */
    val logarithmic: Boolean = false,
) {
    private val log = logarithmic && min > 0.0 && max > min

    fun snap(v: Double): Double {
        val clamped = v.coerceIn(min, max)
        var s = if (step > 0) min + Math.round((clamped - min) / step) * step else clamped
        if (log) s = roundSignificant(s) // on a log track a fixed step is meaningless at the top end
        s = s.coerceIn(min, max)
        if (integer) return Math.round(s).toDouble()
        return (s * 1e6).roundToLong() / 1e6 // drop float noise like 0.30000000000000004
    }

    fun fraction(v: Double): Float = when {
        max <= min -> 0f
        log -> (Math.log(v.coerceIn(min, max) / min) / Math.log(max / min)).toFloat().coerceIn(0f, 1f)
        else -> ((v - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    }

    fun at(fraction: Float): Double {
        val f = fraction.coerceIn(0f, 1f).toDouble()
        return snap(if (log) min * Math.pow(max / min, f) else min + (max - min) * f)
    }

    /** Arrow-key step: one [step] (log scale: 1% of the track), ×10 with Shift. Integer sliders move at least 1. */
    fun nudge(v: Double, dir: Int): Double {
        val mult = if (Keys.shift()) 10 else 1
        if (log) {
            val next = at(fraction(v) + 0.01f * mult * dir)
            // Make sure integer log sliders still move when 1% rounds back to the same value.
            return if (next == v && integer) snap(v + dir) else next
        }
        val base = if (increment > 0) increment else (max - min) / 100.0
        val s = (if (integer) maxOf(base, 1.0) else base) * mult
        return snap(v + s * dir)
    }

    /** Plain editable text: no grouping, no trailing zeros. */
    fun plain(v: Double): String =
        if (integer || v == Math.floor(v) && abs(v) < 1e15) v.toLong().toString()
        else java.math.BigDecimal(v).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** Reads what the user typed; null if it isn't a number. */
    fun parse(text: String): Double? {
        val cleaned = text.replace(",", "").replace(" ", "").takeWhile { it.isDigit() || it == '-' || it == '.' }
        return cleaned.toDoubleOrNull()?.let(::snap)
    }

    private fun roundSignificant(v: Double, digits: Int = 3): Double {
        if (v == 0.0) return 0.0
        return java.math.BigDecimal(v).round(java.math.MathContext(digits)).toDouble()
    }
}

private fun keyDirection(key: Int): Int = when (key) {
    InputConstants.KEY_LEFT, InputConstants.KEY_DOWN -> -1
    InputConstants.KEY_RIGHT, InputConstants.KEY_UP -> 1
    else -> 0
}

private fun drawKnob(g: GuiGraphicsExtractor, w: AstraWidget, cx: Float, cy: Float, active: Float, accent: Int, enabledFade: (Int) -> Int) {
    val th = w.theme
    if (active > 0.01f) Paint.circle(g, cx, cy, 5f + 4f * active, enabledFade(Colors.fade(accent, 0.22f * active)))
    Paint.circle(g, cx, cy, 5.5f, enabledFade(accent))
    Paint.circle(g, cx, cy, 3.6f + 0.6f * active, enabledFade(th.text))
}

private fun drawBubble(g: GuiGraphicsExtractor, w: AstraWidget, cx: Float, top: Float, text: String, t: Float) {
    if (t <= 0.02f) return
    val th = w.theme
    val size = th.fontSmall
    val bw = Paint.width(text, size, true) + 10f
    val bh = Paint.lineHeight(size) + 6f
    val by = top - bh - 4f - 3f * (1f - t)
    Paint.rect(g, cx - bw / 2f, by, bw, bh, Colors.fade(th.text, t), th.radiusSmall)
    Paint.textCentered(g, text, cx, by, bh, Colors.fade(th.background, t), size, true)
}

/**
 * A slider with an editable number box. Drag or click the track, use the arrow keys (Shift = ×10)
 * or Home/End, or type a value into the box.
 */
class Slider(
    val binding: Binding<Double>,
    val numbers: NumberScale,
    val formatter: ValueFormatter = ValueFormatter.Default(),
    /** Show the number box. Off = value only shown in the drag bubble. */
    showBox: Boolean = true,
) : AstraWidget() {
    val box: TextField? = if (showBox) TextField().apply {
        selectAllOnFocus = true
        centered = true
        filter = TextField.NUMERIC
        fixedWidth = 54f
        onCommit = { t -> numbers.parse(t)?.let { binding.set(it) } }
        onKeyHook = { key ->
            val dir = keyDirection(key).takeIf { key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN } ?: 0
            if (dir != 0) {
                val v = numbers.nudge(numbers.parse(text) ?: binding.get(), dir)
                binding.set(v)
                text = numbers.plain(v)
                selectAll()
                true
            } else false
        }
    } else null

    private val gap = 8f
    private val knob = Motion()
    private val shown = Motion(numbers.fraction(binding.get()), speed = 26f)
    private var dragging = false

    init {
        fixedHeight = TextField.CONTROL_HEIGHT
        box?.let { adopt(it) }
    }

    override fun prefWidth(): Float = fixedWidth ?: 200f

    private val trackX get() = x + 6f
    private val trackW get() = (width - 12f - (box?.let { it.prefWidth() + gap } ?: 0f)).coerceAtLeast(10f)

    override fun layout() {
        dirty = false
        box?.let { val bw = it.prefWidth(); it.place(x + width - bw, y, bw, height) }
    }

    override fun find(x: Double, y: Double): IPrimitiveElement<*>? {
        if (!visible) return null
        box?.find(x, y)?.let { return it }
        return if (interact && x >= this.x && x < trackX + trackW + 6f && y >= this.y && y < this.y + height) this else null
    }

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
        if (button != 0) return true
        dragging = true
        update(mx.toFloat())
        return true
    }

    override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
        dragging = false
        return true
    }

    private fun update(mx: Float) {
        val v = numbers.at((mx - trackX) / trackW)
        if (v != binding.get()) binding.set(v)
    }

    override fun onKey(key: Int): Boolean {
        val dir = keyDirection(key)
        val v = when {
            dir != 0 -> numbers.nudge(binding.get(), dir)
            key == InputConstants.KEY_HOME -> numbers.min
            key == InputConstants.KEY_END -> numbers.max
            key == InputConstants.KEY_PAGEUP -> numbers.snap(binding.get() + (numbers.max - numbers.min) / 10)
            key == InputConstants.KEY_PAGEDOWN -> numbers.snap(binding.get() - (numbers.max - numbers.min) / 10)
            else -> return false
        }
        binding.set(v)
        return true
    }

    override fun onScroll(amount: Double): Boolean {
        if (!isFocused) return false
        binding.set(numbers.nudge(binding.get(), if (amount > 0) 1 else -1))
        return true
    }

    override fun paint(g: GuiGraphicsExtractor) {
        if (dragging && pressed) update(mouseX) else dragging = false

        val th = theme
        val accent = accentColor()
        val value = binding.get()
        box?.let { if (!it.isFocused) it.text = formatter.format(value) }

        shown.to(numbers.fraction(value))
        val f = if (dragging) shown.snap(numbers.fraction(value)).get() else shown.get()
        knob.to(if (dragging) 1f else if (hovered || focusGlow.target > 0f) 0.6f else 0f)
        val k = knob.get()

        val cy = y + height / 2f
        val th2 = 4f
        Paint.rect(g, trackX, cy - th2 / 2f, trackW, th2, c(Colors.mix(th.border, th.borderStrong, hover.get())), th2 / 2f)
        Paint.rect(g, trackX, cy - th2 / 2f, trackW * f, th2, c(accent), th2 / 2f)
        val kx = trackX + trackW * f
        drawKnob(g, this, kx, cy, k, accent, ::c)
        drawBubble(g, this, kx, cy - 5f, formatter.format(value), if (dragging) 1f else 0f)
        focusRing(g, trackX - 4f, y + 2f, trackW + 8f, height - 4f, th.radiusSmall)
    }
}

/**
 * Two-knob slider for a from/to range. Grab the nearer knob, or the bar between them to move both.
 * Knobs push each other along, keeping at least `minSpread` apart. Keyboard: arrows move the active
 * knob, Space switches which knob is active.
 */
class RangeSlider(
    val binding: Binding<Pair<Double, Double>>,
    val numbers: NumberScale,
    val minSpread: Double = 0.0,
    val formatter: ValueFormatter = ValueFormatter.Default(),
) : AstraWidget() {
    private enum class Grab { NONE, FROM, TO, BAR }

    private var grab = Grab.NONE
    private var barOffset = 0.0
    private var activeTo = false
    private val fromKnob = Motion()
    private val toKnob = Motion()
    private val shownFrom = Motion(numbers.fraction(binding.get().first), 26f)
    private val shownTo = Motion(numbers.fraction(binding.get().second), 26f)
    private val labelWidth = 92f

    init {
        fixedHeight = TextField.CONTROL_HEIGHT
    }

    override fun prefWidth(): Float = fixedWidth ?: 240f

    private val trackX get() = x + 6f
    private val trackW get() = (width - 12f - labelWidth).coerceAtLeast(10f)
    private fun valueAt(mx: Float) = numbers.min + (numbers.max - numbers.min) * ((mx - trackX) / trackW).toDouble().coerceIn(0.0, 1.0)
    private fun pxOf(v: Double) = trackX + trackW * numbers.fraction(v)

    private val spread: Double get() = minSpread.coerceIn(0.0, numbers.max - numbers.min)

    /** Moves one end, pushing the other if they get closer than [spread]. */
    private fun moveEnd(isTo: Boolean, raw: Double) {
        var (from, to) = binding.get()
        val s = spread
        if (isTo) {
            to = numbers.snap(raw).coerceIn(numbers.min + s, numbers.max)
            if (to - from < s) from = numbers.snap(to - s)
        } else {
            from = numbers.snap(raw).coerceIn(numbers.min, numbers.max - s)
            if (to - from < s) to = numbers.snap(from + s)
        }
        commit(from, to)
    }

    private fun commit(from: Double, to: Double) {
        val next = minOf(from, to) to maxOf(from, to)
        if (next != binding.get()) binding.set(next)
    }

    override fun onMouseDown(mx: Double, my: Double, button: Int): Boolean {
        if (button != 0) return true
        val (from, to) = binding.get()
        val fx = pxOf(from)
        val tx = pxOf(to)
        val m = mx.toFloat()
        grab = when {
            abs(m - fx) <= 6f && abs(m - tx) <= 6f -> if (m < fx) Grab.FROM else Grab.TO
            abs(m - fx) <= 6f -> Grab.FROM
            abs(m - tx) <= 6f -> Grab.TO
            m > fx && m < tx -> Grab.BAR
            m <= fx -> Grab.FROM
            else -> Grab.TO
        }
        when (grab) {
            Grab.BAR -> barOffset = valueAt(m) - from
            Grab.FROM -> { activeTo = false; moveEnd(false, valueAt(m)) }
            Grab.TO -> { activeTo = true; moveEnd(true, valueAt(m)) }
            Grab.NONE -> {}
        }
        return true
    }

    override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
        grab = Grab.NONE
        return true
    }

    private fun drag(mx: Float) {
        when (grab) {
            Grab.FROM -> moveEnd(false, valueAt(mx))
            Grab.TO -> moveEnd(true, valueAt(mx))
            Grab.BAR -> {
                val (from, to) = binding.get()
                val span = to - from
                val nf = numbers.snap((valueAt(mx) - barOffset).coerceIn(numbers.min, numbers.max - span))
                commit(nf, numbers.snap(nf + span))
            }
            Grab.NONE -> {}
        }
    }

    override fun onKey(key: Int): Boolean {
        val (from, to) = binding.get()
        val dir = keyDirection(key)
        when {
            dir != 0 -> moveEnd(activeTo, numbers.nudge(if (activeTo) to else from, dir))
            key == InputConstants.KEY_SPACE -> activeTo = !activeTo
            key == InputConstants.KEY_HOME -> moveEnd(activeTo, numbers.min)
            key == InputConstants.KEY_END -> moveEnd(activeTo, numbers.max)
            else -> return false
        }
        return true
    }

    override fun paint(g: GuiGraphicsExtractor) {
        if (grab != Grab.NONE && pressed) drag(mouseX) else grab = Grab.NONE

        val th = theme
        val accent = accentColor()
        val (from, to) = binding.get()
        val dragging = grab != Grab.NONE

        shownFrom.to(numbers.fraction(from)); shownTo.to(numbers.fraction(to))
        val ff = if (dragging) shownFrom.snap(numbers.fraction(from)).get() else shownFrom.get()
        val tf = if (dragging) shownTo.snap(numbers.fraction(to)).get() else shownTo.get()

        val kbd = focusGlow.target > 0f
        fromKnob.to(if (grab == Grab.FROM || grab == Grab.BAR) 1f else if (kbd && !activeTo) 0.8f else if (hovered) 0.4f else 0f)
        toKnob.to(if (grab == Grab.TO || grab == Grab.BAR) 1f else if (kbd && activeTo) 0.8f else if (hovered) 0.4f else 0f)

        val cy = y + height / 2f
        val h = 4f
        Paint.rect(g, trackX, cy - h / 2f, trackW, h, c(Colors.mix(th.border, th.borderStrong, hover.get())), h / 2f)
        val fx = trackX + trackW * ff
        val tx = trackX + trackW * tf
        Paint.rect(g, fx, cy - h / 2f, tx - fx, h, c(accent), h / 2f)

        drawKnob(g, this, fx, cy, fromKnob.get(), accent, ::c)
        drawKnob(g, this, tx, cy, toKnob.get(), accent, ::c)
        focusRing(g, trackX - 4f, y + 2f, trackW + 8f, height - 4f, th.radiusSmall)

        if (dragging) {
            if (grab != Grab.TO) drawBubble(g, this, fx, cy - 5f, formatter.format(from), 1f)
            if (grab != Grab.FROM) drawBubble(g, this, tx, cy - 5f, formatter.format(to), 1f)
        }

        // Value label on the right.
        val label = "${formatter.format(from)} – ${formatter.format(to)}"
        val lx = x + width - labelWidth
        Paint.rect(g, lx + 6f, y, labelWidth - 6f, height, c(th.surfaceRaised), th.radiusSmall)
        Paint.outline(g, lx + 6f, y, labelWidth - 6f, height, c(th.border), th.radiusSmall)
        Paint.textCentered(g, Paint.truncate(label, th.fontSmall + 0.5f, labelWidth - 12f), lx + 6f + (labelWidth - 6f) / 2f, y, height, c(th.text), th.fontSmall + 0.5f)
    }
}
