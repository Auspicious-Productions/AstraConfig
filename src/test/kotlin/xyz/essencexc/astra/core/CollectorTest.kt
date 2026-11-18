package xyz.essencexc.astra.core

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.api.annotations.Button
import xyz.essencexc.astra.api.annotations.Color
import xyz.essencexc.astra.api.annotations.DependsOn
import xyz.essencexc.astra.api.annotations.Dropdown
import xyz.essencexc.astra.api.annotations.ItemList
import xyz.essencexc.astra.api.annotations.MasterToggle
import xyz.essencexc.astra.api.annotations.MultiSelect
import xyz.essencexc.astra.api.annotations.PreviousNames
import xyz.essencexc.astra.api.annotations.RangeSlider
import xyz.essencexc.astra.api.annotations.Section
import xyz.essencexc.astra.api.annotations.Slider
import xyz.essencexc.astra.api.annotations.Switch
import xyz.essencexc.astra.api.annotations.Text
import xyz.essencexc.astra.api.function.Migration
import xyz.essencexc.astra.api.kt.KtModule
import xyz.essencexc.astra.api.value.IntRange
import java.lang.invoke.MethodHandles

enum class Mode { FAST, SLOW_AND_STEADY }

class SampleModule : Module("sample", "Sample") {
    @MasterToggle
    var enabled = false

    @Switch(title = "Alpha", section = "First")
    var alpha = true

    @Slider(title = "Speed", section = "First", min = 0f, max = 10f, step = 0.5f)
    @DependsOn("alpha")
    var speed = 2.5f

    @Dropdown(title = "Mode")
    @PreviousNames("oldMode")
    var mode = Mode.FAST

    @RangeSlider(title = "Delay", min = 0f, max = 1000f, step = 10f, minSpread = 20f)
    var delay: IntRange = IntRange.of(100, 200)

    @Color(title = "Colour")
    var colour = 0xFF112233.toInt()

    @Text(title = "Name")
    var name2 = "x"

    var notAnOption = 5

    var pressed = 0

    @Button(title = "Press", text = "Go")
    fun press() {
        pressed++
    }

    // Declared Zulu first, then Alpha: section order must follow the source, not the name.
    @Section("Zulu")
    object Zulu {
        @Switch(title = "Z")
        var z = false
    }

    @Section("Bravo")
    object Bravo {
        @Slider(title = "B", min = 0f, max = 5f)
        var b = 1
    }
}

class FinalFieldModule : Module("final", "Final") {
    @Switch(title = "Nope")
    val nope = false
}

class WrongTypeModule : Module("wrong", "Wrong") {
    @Switch(title = "Nope")
    var nope = 3
}

class BadHideModule : Module("badhide", "Bad Hide") {
    @Switch(title = "A")
    var a = false

    init {
        hideIf("missing") { true }
    }
}

class DelegateModule : KtModule("delegates", "Delegates") {
    val changes = mutableListOf<Boolean>()
    var enabled by masterToggle()
    var flag by switch("Flag").onChange { changes += it }
    var range by rangeSlider("Range", 10..20, bounds = 0..100)
    var mode by dropdown("Mode", Mode.SLOW_AND_STEADY)

    @Switch(title = "Annotated")
    var annotated = false
}

class CollectorTest {
    private val lookup = MethodHandles.lookup()

    private fun sample() = SampleModule().also { Collector.collect(it, lookup) }

    @Test
    fun `collects fields in order with sections`() {
        val m = sample()
        assertEquals(listOf("enabled", "alpha", "speed", "mode", "delay", "colour", "name2", "press", "z", "b"), m.options.map { it.key })
        assertEquals(listOf("General", "First", "First", "General", "General", "General", "General", "General", "Zulu", "Bravo"), m.options.map { it.section })
    }

    @Test
    fun `specs match annotations`() {
        val m = sample()
        assertEquals(OptionSpec.MasterToggleSpec, m.find("enabled").spec)
        val slider = m.find("speed").spec as OptionSpec.SliderSpec
        assertEquals(NumberKind.FLOAT, slider.kind)
        assertEquals(0.5, slider.step)
        val dropdown = m.find("mode").spec as OptionSpec.DropdownSpec
        assertEquals(listOf("Fast", "Slow And Steady"), dropdown.labels)
        val range = m.find("delay").spec as OptionSpec.RangeSliderSpec
        assertTrue(range.integer)
        assertEquals(20.0, range.minSpread)
        assertEquals(OptionSpec.SliderSpec::class, m.find("b").spec::class)
    }

    @Test
    fun `master toggle drives isEnabled`() {
        val m = sample()
        assertFalse(m.isEnabled())
        m.enabled = true
        assertTrue(m.isEnabled())
    }

    @Test
    fun `set fires onChange and reads fields`() {
        val m = sample()
        val seen = mutableListOf<Any?>()
        m.onChange("alpha") { v: Boolean -> seen += v }
        m.set("alpha", false)
        assertFalse(m.alpha)
        assertEquals(listOf<Any?>(false), seen)

        m.alpha = true // direct write: no listener, but get() sees it
        assertEquals(true, m.get("alpha"))
        assertEquals(1, seen.size)
    }

    @Test
    fun `depends on and hideIf`() {
        val m = sample()
        m.hideIf("speed") { m.mode == Mode.SLOW_AND_STEADY }
        assertFalse(m.find("speed").disabled)
        m.alpha = false
        assertTrue(m.find("speed").disabled)
        assertFalse(m.find("speed").hidden)
        m.mode = Mode.SLOW_AND_STEADY
        assertTrue(m.find("speed").hidden)
    }

    @Test
    fun `button runs method`() {
        val m = sample()
        val spec = m.find("press").spec as OptionSpec.ButtonSpec
        spec.action()
        assertEquals(1, m.pressed)
    }

    @Test
    fun `validation errors are clear`() {
        val final = assertThrows<AstraConfigException> { Collector.collect(FinalFieldModule(), lookup) }
        assertTrue("final" in final.message!!, final.message)
        val type = assertThrows<AstraConfigException> { Collector.collect(WrongTypeModule(), lookup) }
        assertTrue("expected boolean" in type.message!!, type.message)
        val hide = assertThrows<AstraConfigException> { Collector.collect(BadHideModule(), lookup) }
        assertTrue("missing" in hide.message!!, hide.message)
    }

    @Test
    fun `save and load round trip`() {
        val m = sample()
        m.alpha = false
        m.speed = 7.5f
        m.mode = Mode.SLOW_AND_STEADY
        m.delay = IntRange.of(300, 400)
        m.colour = 0x80FFFFFF.toInt()
        SampleModule.Bravo.b = 4

        val json = JsonObject()
        assertTrue(ConfigSerializer.write(json, listOf(m), 1))
        assertEquals(JsonPrimitive("#80FFFFFF"), json.get("sample.colour"))
        assertEquals(300, json.getAsJsonObject("sample.delay").get("from").asInt)
        assertFalse(ConfigSerializer.write(json, listOf(m), 1), "second write with no changes reports no change")

        val fresh = sample()
        SampleModule.Bravo.b = 1
        ConfigSerializer.read(json, listOf(fresh), 1, emptyList())
        assertEquals(false, fresh.alpha)
        assertEquals(7.5f, fresh.speed)
        assertEquals(Mode.SLOW_AND_STEADY, fresh.mode)
        assertEquals(IntRange.of(300, 400), fresh.delay)
        assertEquals(0x80FFFFFF.toInt(), fresh.colour)
        assertEquals(4, SampleModule.Bravo.b)
    }

    @Test
    fun `previous names and bad values`() {
        val json = JsonObject()
        json.addProperty("sample.oldMode", "SLOW_AND_STEADY")
        json.addProperty("sample.speed", "not a number")
        val m = sample()
        ConfigSerializer.read(json, listOf(m), 1, emptyList())
        assertEquals(Mode.SLOW_AND_STEADY, m.mode)
        assertEquals(2.5f, m.speed, "invalid saved value is ignored")
        assertFalse(json.has("sample.oldMode"), "old key is migrated away")
    }

    @Test
    fun `schema migrations run once`() {
        val json = JsonObject()
        json.addProperty("@schema", 1)
        json.addProperty("sample.legacySpeed", 9.0)
        val calls = mutableListOf<Int>()
        val migration = Migration { from, obj ->
            calls += from
            obj.add("sample.speed", obj.remove("sample.legacySpeed"))
        }
        val m = sample()
        ConfigSerializer.read(json, listOf(m), 2, listOf(migration))
        assertEquals(listOf(1), calls)
        assertEquals(9f, m.speed)
        assertEquals(2, json.get("@schema").asInt)

        ConfigSerializer.read(json, listOf(sample()), 2, listOf(migration))
        assertEquals(listOf(1), calls, "already migrated")
    }

    @Test
    fun `kotlin delegates`() {
        val m = DelegateModule()
        Collector.collect(m, lookup)
        assertEquals(listOf("enabled", "flag", "range", "mode", "annotated"), m.options.map { it.key })
        m.flag = true
        assertEquals(listOf(true), m.changes)
        assertEquals(10, m.range.from)
        m.set("range", IntRange.of(1, 2))
        assertEquals(IntRange.of(1, 2), m.range)
        assertNotNull(m.master)

        val json = JsonObject()
        ConfigSerializer.write(json, listOf(m), 1)
        assertEquals("SLOW_AND_STEADY", json.get("delegates.mode").asString)
    }

    class CollectionsModule : Module("coll", "Collections") {
        @MultiSelect(title = "Modes")
        var modes: java.util.EnumSet<Mode> = java.util.EnumSet.of(Mode.FAST)

        @MultiSelect(title = "Tags", options = ["a", "b", "c"])
        var tags: List<String> = listOf("b")

        @ItemList(title = "Names")
        var names: List<String> = listOf("x")
    }

    class CollectionsKt : KtModule("collkt", "Collections Kt") {
        var mobs by multiSelect("Mobs", setOf(Mode.SLOW_AND_STEADY))
        var list by itemList("List", listOf("one"))
    }

    @Test
    fun `multi select and item list`() {
        val m = CollectionsModule().also { Collector.collect(it, lookup) }
        val modes = m.find("modes") as Option<Any?>
        assertTrue(modes.spec is OptionSpec.MultiSelectSpec)
        assertEquals(listOf(Mode.FAST), modes.get())
        modes.set(listOf(Mode.FAST, Mode.SLOW_AND_STEADY))
        assertTrue(m.modes is java.util.EnumSet<*> && m.modes.size == 2, "field gets a fresh EnumSet")

        (m.find("names") as Option<Any?>).set(listOf("x", "y"))
        assertEquals(listOf("x", "y"), m.names)

        val json = JsonObject()
        ConfigSerializer.write(json, listOf(m), 1)
        assertEquals(listOf("FAST", "SLOW_AND_STEADY"), json.getAsJsonArray("coll.modes").map { it.asString })
        json.add("coll.tags", com.google.gson.JsonArray().apply { add("c"); add("zzz") }) // unknown entries are dropped

        val fresh = CollectionsModule().also { Collector.collect(it, lookup) }
        ConfigSerializer.read(json, listOf(fresh), 1, emptyList())
        assertEquals(java.util.EnumSet.of(Mode.FAST, Mode.SLOW_AND_STEADY), fresh.modes)
        assertEquals(listOf("c"), fresh.tags)
        assertEquals(listOf("x", "y"), fresh.names)

        val k = CollectionsKt().also { Collector.collect(it, lookup) }
        k.mobs = setOf(Mode.FAST, Mode.SLOW_AND_STEADY)
        assertEquals(setOf(Mode.FAST, Mode.SLOW_AND_STEADY), k.mobs)
        k.list = k.list + "two"
        assertEquals(listOf("one", "two"), k.list)
    }
}
