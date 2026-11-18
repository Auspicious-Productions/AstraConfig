package xyz.essencexc.astra.ui.util

import xyz.essencexc.astra.ui.theme.Themes
import kotlin.math.abs
import kotlin.math.exp

/**
 * A float that eases toward [target], evaluated when it's read (once per frame while drawing).
 *
 * Widgets animate with these instead of Cascade's property animations: Cascade marks the whole
 * scene dirty every frame an animation runs, which re-lays out every row. A [Motion] only affects
 * drawing, so layout stays untouched.
 *
 * @param speed how fast it closes the gap; ~1/speed seconds to get most of the way there.
 */
class Motion(initial: Float = 0f, var speed: Float = 16f) {
    var target: Float = initial
        private set

    private var current: Float = initial
    private var last: Long = 0L

    /** Moves toward [value] from wherever it is now. */
    fun to(value: Float): Motion = apply { target = value }

    /** Same as [to] with a boolean: 1 or 0. */
    fun to(on: Boolean): Motion = to(if (on) 1f else 0f)

    /** Jumps straight to [value]. */
    fun snap(value: Float): Motion = apply {
        target = value
        current = value
    }

    fun get(): Float {
        val now = System.nanoTime()
        val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
        last = now

        if (!Themes.current.animations) {
            current = target
            return current
        }

        if (current != target) {
            current += (target - current) * (1f - exp(-speed * dt))
            if (abs(target - current) < 0.0015f) current = target
        }
        return current
    }

    val settled: Boolean
        get() = current == target
}
