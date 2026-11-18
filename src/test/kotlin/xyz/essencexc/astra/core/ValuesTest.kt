package xyz.essencexc.astra.core

import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xyz.essencexc.astra.api.function.ValueFormatter
import xyz.essencexc.astra.api.hud.HudAnchor
import xyz.essencexc.astra.api.hud.HudLayout
import xyz.essencexc.astra.api.keybind.KeyBinding
import xyz.essencexc.astra.api.value.FloatRange
import xyz.essencexc.astra.api.value.IntRange

class ValuesTest {
    @Test
    fun `int range`() {
        val r = IntRange.of(250, 120)
        assertEquals(120, r.from)
        assertEquals(250, r.to)
        assertEquals(130, r.span())
        repeat(1000) { assertTrue(r.random() in 120..250) }
        assertEquals(120, IntRange.of(5, 5).let { IntRange.of(120, 120) }.random())
        assertEquals(120..250, r.toKotlin())
        assertEquals(200, r.clamp(200))
        assertEquals(250, r.clamp(999))
    }

    @Test
    fun `float range`() {
        val r = FloatRange.of(1f, 2f)
        repeat(1000) { assertTrue(r.random() in 1f..2f) }
    }

    @Test
    fun `codecs round trip`() {
        val kb = KeyBinding.key(82).withCtrl()
        assertEquals(kb, Codecs.decode(Codecs.KEY_BINDING, Codecs.encode(Codecs.KEY_BINDING, kb)!!))
        assertEquals(KeyBinding.UNBOUND, Codecs.decode(Codecs.KEY_BINDING, Codecs.encode(Codecs.KEY_BINDING, KeyBinding.UNBOUND)!!))
        val fr = FloatRange.of(0.5f, 1.5f)
        assertEquals(fr, Codecs.decode(Codecs.FLOAT_RANGE, Codecs.encode(Codecs.FLOAT_RANGE, fr)!!))
    }

    @Test
    fun `colour codec`() {
        assertEquals(JsonPrimitive("#FF3B82F6"), Codecs.encode(Codecs.COLOR, 0xFF3B82F6.toInt()))
        assertEquals(0xFF3B82F6.toInt(), Codecs.decode(Codecs.COLOR, JsonPrimitive("#3B82F6")))
        assertNull(Codecs.decode(Codecs.COLOR, JsonPrimitive("blue")))
    }

    @Test
    fun `hud layout anchors and round trips`() {
        val w = 427f; val h = 240f // a GUI-scaled screen
        // Dropped near the bottom right: anchors there, and maps back to the same spot.
        val l = HudLayout.at(380f, 200f, 40f, 20f, 1f, true, w, h)
        assertEquals(HudAnchor.BOTTOM_RIGHT, l.anchor)
        assertEquals(380f to 200f, l.topLeft(w, h, 40f, 20f))
        // Same layout on a bigger screen keeps its distance from the corner.
        val (x2, y2) = l.topLeft(640f, 360f, 40f, 20f)
        assertEquals(640f - (w - 380f), x2)
        assertEquals(360f - (h - 200f), y2)
        // Centre third -> CENTER anchor; scale is clamped by withScale.
        assertEquals(HudAnchor.CENTER, HudLayout.at(200f, 110f, 20f, 10f, 1f, true, w, h).anchor)
        assertEquals(HudLayout.MAX_SCALE, l.withScale(10f).scale)
        assertEquals(l, Codecs.decode(Codecs.HUD_LAYOUT, Codecs.encode(Codecs.HUD_LAYOUT, l)!!))
    }

    @Test
    fun formatters() {
        assertEquals("100,000", ValueFormatter.Default().format(100_000.0))
        assertEquals("2.5", ValueFormatter.Default().format(2.5))
        assertEquals("250ms", ValueFormatter.Millis().format(250.0))
        assertEquals("75%", ValueFormatter.Percent().format(75.0))
    }
}
