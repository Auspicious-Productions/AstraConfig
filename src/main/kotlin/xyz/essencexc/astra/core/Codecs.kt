package xyz.essencexc.astra.core

import com.google.gson.JsonElement
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.RecordCodecBuilder
import xyz.essencexc.astra.api.hud.HudAnchor
import xyz.essencexc.astra.api.hud.HudFont
import xyz.essencexc.astra.api.hud.HudLayout
import xyz.essencexc.astra.api.hud.HudStyle
import xyz.essencexc.astra.api.keybind.InputType
import xyz.essencexc.astra.api.keybind.KeyBinding
import xyz.essencexc.astra.api.value.FloatRange
import xyz.essencexc.astra.api.value.IntRange

/** Codecs for every value type an option can hold. */
internal object Codecs {
    val INT_RANGE: Codec<IntRange> = RecordCodecBuilder.create { i ->
        i.group(
            Codec.INT.fieldOf("from").forGetter(IntRange::from),
            Codec.INT.fieldOf("to").forGetter(IntRange::to),
        ).apply(i) { a, b -> IntRange.of(a, b) }
    }

    val FLOAT_RANGE: Codec<FloatRange> = RecordCodecBuilder.create { i ->
        i.group(
            Codec.FLOAT.fieldOf("from").forGetter(FloatRange::from),
            Codec.FLOAT.fieldOf("to").forGetter(FloatRange::to),
        ).apply(i) { a, b -> FloatRange.of(a, b) }
    }

    private val INPUT_TYPE: Codec<InputType> = enumCodec(InputType::class.java)

    /**
     * Saves the key's name ("key.keyboard.r") plus type and code. On load the name wins when this
     * version knows it, so a config from 26.1 binds the same keys on 26.3 (whose codes differ).
     */
    val KEY_BINDING: Codec<KeyBinding> = RecordCodecBuilder.create { i ->
        i.group(
            Codec.STRING.optionalFieldOf("key").forGetter { java.util.Optional.ofNullable(KeyNames.name(it)) },
            INPUT_TYPE.fieldOf("type").forGetter(KeyBinding::type),
            Codec.INT.fieldOf("code").forGetter(KeyBinding::code),
            Codec.BOOL.optionalFieldOf("ctrl", false).forGetter(KeyBinding::ctrl),
            Codec.BOOL.optionalFieldOf("shift", false).forGetter(KeyBinding::shift),
            Codec.BOOL.optionalFieldOf("alt", false).forGetter(KeyBinding::alt),
        ).apply(i) { key, t, c, ctrl, shift, alt ->
            val (type, code) = key.orElse(null)?.let(KeyNames::resolve) ?: (t to c)
            KeyBinding(type, code, ctrl, shift, alt)
        }
    }

    /** A colour as "#AARRGGBB", or "" for 0 (= use the theme colour). Declared before its users. */
    private val COLOR_OR_ZERO: Codec<Int> = Codec.STRING.comapFlatMap(
        { s ->
            val hex = s.removePrefix("#")
            when {
                hex.isEmpty() -> DataResult.success(0)
                hex.length == 6 -> hex.toLongOrNull(16)?.let { DataResult.success((0xFF000000L or it).toInt()) } ?: DataResult.error { "Invalid colour '$s'" }
                hex.length == 8 -> hex.toLongOrNull(16)?.let { DataResult.success(it.toInt()) } ?: DataResult.error { "Invalid colour '$s'" }
                else -> DataResult.error { "Invalid colour '$s'" }
            }
        },
        { if (it == 0) "" else "#%08X".format(it) },
    )

    private val HUD_STYLE: Codec<HudStyle> = RecordCodecBuilder.create { i ->
        i.group(
            Codec.BOOL.optionalFieldOf("background", true).forGetter(HudStyle::background),
            COLOR_OR_ZERO.optionalFieldOf("backgroundColor", 0).forGetter(HudStyle::backgroundColor),
            COLOR_OR_ZERO.optionalFieldOf("textColor", 0).forGetter(HudStyle::textColor),
            COLOR_OR_ZERO.optionalFieldOf("accent", 0).forGetter(HudStyle::accent),
            Codec.BOOL.optionalFieldOf("shadow", false).forGetter(HudStyle::shadow),
            enumCodec(HudFont::class.java).optionalFieldOf("font", HudFont.ASTRA).forGetter(HudStyle::font),
            Codec.BOOL.optionalFieldOf("hideWithChat", false).forGetter(HudStyle::hideWithChat),
            Codec.BOOL.optionalFieldOf("hideWithDebug", true).forGetter(HudStyle::hideWithDebug),
        ).apply(i) { bg, bgc, tc, ac, sh, font, chat, debug -> HudStyle(bg, bgc, tc, ac, sh, font, chat, debug) }
    }

    /** Position, scale, visibility and style. `style` is optional, so older configs still load. */
    val HUD_LAYOUT: Codec<HudLayout> = RecordCodecBuilder.create { i ->
        i.group(
            enumCodec(HudAnchor::class.java).fieldOf("anchor").forGetter(HudLayout::anchor),
            Codec.FLOAT.fieldOf("x").forGetter(HudLayout::x),
            Codec.FLOAT.fieldOf("y").forGetter(HudLayout::y),
            Codec.FLOAT.optionalFieldOf("scale", 1f).forGetter(HudLayout::scale),
            Codec.BOOL.optionalFieldOf("shown", true).forGetter(HudLayout::shown),
            HUD_STYLE.optionalFieldOf("style", HudStyle.DEFAULT).forGetter(HudLayout::style),
        ).apply(i) { a, x, y, s, shown, style -> HudLayout(a, x, y, s.coerceIn(HudLayout.MIN_SCALE, HudLayout.MAX_SCALE), shown, style) }
    }

    /** ARGB int saved as `"#AARRGGBB"` so config files stay readable. */
    val COLOR: Codec<Int> = Codec.STRING.comapFlatMap(
        { s ->
            val hex = s.removePrefix("#")
            val parsed = hex.toLongOrNull(16)
            when {
                parsed == null || hex.length !in setOf(6, 8) -> DataResult.error { "Invalid colour '$s'" }
                hex.length == 6 -> DataResult.success((0xFF000000L or parsed).toInt())
                else -> DataResult.success(parsed.toInt())
            }
        },
        { "#%08X".format(it) },
    )

    fun <E : Enum<*>> enumCodec(type: Class<E>): Codec<E> {
        val constants = type.enumConstants
        return Codec.STRING.comapFlatMap(
            { name -> constants.firstOrNull { it.name == name }?.let { DataResult.success(it) } ?: DataResult.error { "Unknown ${type.simpleName} '$name'" } },
            { it.name },
        )
    }

    fun number(kind: NumberKind): Codec<*> = when (kind) {
        NumberKind.INT -> Codec.INT
        NumberKind.FLOAT -> Codec.FLOAT
        NumberKind.DOUBLE -> Codec.DOUBLE
    }

    fun <T> encode(codec: Codec<T>, value: T): JsonElement? = codec.encodeStart(JsonOps.INSTANCE, value).result().orElse(null)

    fun <T> decode(codec: Codec<T>, json: JsonElement): T? = codec.parse(JsonOps.INSTANCE, json).result().orElse(null)
}
