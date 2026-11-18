package xyz.essencexc.astra.ui.theme

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Every colour, radius and font size the UI uses. Widgets read [Themes.current] each frame, so
 * swapping the theme restyles an open menu immediately.
 *
 * Colours are ARGB ints.
 */
data class Theme(
    val name: String,
    /** Behind everything: panel fill. */
    val background: Int,
    /** Cards and sidebars. */
    val surface: Int,
    /** Inputs, buttons and other things sitting on a card. */
    val surfaceRaised: Int,
    /** Hover fill for rows and list items. */
    val surfaceHover: Int,
    val border: Int,
    val borderStrong: Int,
    val text: Int,
    val textMuted: Int,
    val textFaint: Int,
    val accent: Int,
    /** Text and icons drawn on top of [accent]. */
    val onAccent: Int,
    val danger: Int,
    val warning: Int,
    val success: Int,
    val info: Int,
    val radiusSmall: Float = 4f,
    val radiusMedium: Float = 6f,
    val radiusLarge: Float = 9f,
    val fontSmall: Float = 7f,
    val fontBody: Float = 8.5f,
    val fontTitle: Float = 10f,
    val fontHeading: Float = 14f,
    /** Off = every transition snaps. */
    val animations: Boolean = true,
) {
    fun withAccent(argb: Int): Theme = copy(accent = argb)

    companion object {
        /** Astra's default: deep ink blue with a periwinkle accent. */
        @JvmField
        val NIGHT = Theme(
            name = "Night",
            background = 0xFF0C0E14.toInt(),
            surface = 0xFF13161F.toInt(),
            surfaceRaised = 0xFF1A1E2A.toInt(),
            surfaceHover = 0xFF1F2433.toInt(),
            border = 0xFF242939.toInt(),
            borderStrong = 0xFF343B50.toInt(),
            text = 0xFFE8EAF2.toInt(),
            textMuted = 0xFF979EB5.toInt(),
            textFaint = 0xFF5E6580.toInt(),
            accent = 0xFF8090FF.toInt(),
            onAccent = 0xFF0C0E14.toInt(),
            danger = 0xFFFF6B7D.toInt(),
            warning = 0xFFFFB547.toInt(),
            success = 0xFF47DE9C.toInt(),
            info = 0xFF5AB4FF.toInt(),
        )

        /** A light variant, mostly to prove every colour goes through the theme. */
        @JvmField
        val DAY = Theme(
            name = "Day",
            background = 0xFFF3F4F8.toInt(),
            surface = 0xFFFFFFFF.toInt(),
            surfaceRaised = 0xFFF0F2F7.toInt(),
            surfaceHover = 0xFFE9ECF4.toInt(),
            border = 0xFFDDE1EB.toInt(),
            borderStrong = 0xFFC3C9D8.toInt(),
            text = 0xFF161925.toInt(),
            textMuted = 0xFF5B6276.toInt(),
            textFaint = 0xFF9097AA.toInt(),
            accent = 0xFF5664F0.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            danger = 0xFFE5485D.toInt(),
            warning = 0xFFD98A0B.toInt(),
            success = 0xFF16A36A.toInt(),
            info = 0xFF2F8AE0.toInt(),
        )

        /** Accent presets offered by the colour picker and (later) the Customize page. */
        @JvmField
        val ACCENTS: List<Int> = listOf(
            0xFF8090FF, 0xFFB18CFF, 0xFFFF7EB6, 0xFFFF8A5B, 0xFFFFC44D, 0xFF5BDB8C, 0xFF3CC8D8, 0xFF5AB4FF,
        ).map { it.toInt() }
    }
}

/** The active theme. Change it with [Themes.current]; listeners hear about it. */
object Themes {
    private val listeners = CopyOnWriteArrayList<(Theme) -> Unit>()

    @JvmStatic
    var current: Theme = Theme.NIGHT
        set(value) {
            if (field == value) return
            field = value
            for (l in listeners) l(value)
        }

    @JvmStatic
    fun listen(listener: (Theme) -> Unit) {
        listeners += listener
    }
}
