package xyz.essencexc.astra.api.function

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats numbers shown by sliders. Referenced from annotations by class, so implementations need a
 * public no-argument constructor. `ValueFormatter.class` itself means "default formatting".
 */
fun interface ValueFormatter {
    fun format(value: Double): String

    /** `100000` -> `100,000`, decimals trimmed. */
    class Default : ValueFormatter {
        override fun format(value: Double): String = FORMAT.get().format(value)
    }

    /** `75` -> `75%`. */
    class Percent : ValueFormatter {
        override fun format(value: Double): String = FORMAT.get().format(value) + "%"
    }

    /** `250` -> `250ms`. */
    class Millis : ValueFormatter {
        override fun format(value: Double): String = FORMAT.get().format(value) + "ms"
    }

    /** `2.5` -> `2.5s`. */
    class Seconds : ValueFormatter {
        override fun format(value: Double): String = FORMAT.get().format(value) + "s"
    }

    companion object {
        private val FORMAT: ThreadLocal<DecimalFormat> = ThreadLocal.withInitial {
            DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT))
        }
    }
}
