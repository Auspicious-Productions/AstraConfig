package xyz.essencexc.astra.ui.menu

import foo.starred.cascade.constraints.base.ISizeConstraint
import foo.starred.cascade.constraints.impl.position.CenterPositionConstraint
import foo.starred.cascade.graphics.geometry.CascadeGeometricResolution
import foo.starred.cascade.primitives.base.impl.IPrimitiveElement
import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.AstraMod
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.core.AstraPrefs
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.core.AstraScreen
import xyz.essencexc.astra.ui.layout.Align
import xyz.essencexc.astra.ui.layout.Column
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.theme.Themes
import xyz.essencexc.astra.ui.util.Motion
import xyz.essencexc.astra.ui.util.Paint
import xyz.essencexc.astra.ui.widgets.TextField

/** Where each mod's menu was left, so reopening it lands on the same page. */
internal object MenuState {
    data class Place(val page: String, val module: String? = null)

    private val places = HashMap<String, Place>()

    fun get(mod: AstraMod): Place = places[mod.id] ?: Place(Page.MODULES)
    fun set(mod: AstraMod, place: Place) {
        places[mod.id] = place
    }
}

/** One page of the menu. Builds its content once per visit. */
internal abstract class Page(val screen: ConfigScreen) {
    /** Which sidebar entry lights up. */
    abstract val key: String
    abstract val title: String
    open val subtitle: String get() = ""

    /** Shows the header search box with this placeholder; null hides it. */
    open val searchPlaceholder: String? get() = null

    abstract fun build(): AstraElement
    open fun onSearch(query: String) {}
    open fun place(): MenuState.Place = MenuState.Place(key)

    companion object {
        const val MODULES = "modules"
        const val KEYS = "keys"
        const val SETTINGS = "settings"
        const val CUSTOMIZE = "customize"
        const val PROFILES = "profiles"
    }
}

/**
 * A mod's config menu: sidebar (branding, pages, categories), header (title, search) and the
 * current page. Opens on the page it was last left on.
 */
internal class ConfigScreen(val mod: AstraMod) : AstraScreen(mod.name) {
    private val appear = Motion(0f, speed = 16f)
    private var closing = false

    private val host = PageHost()
    private val search = TextField().apply {
        fixedWidth = 190f
        maxLength = 64
        onChange = { q -> page?.onSearch(q) }
    }
    private val sidebar = Sidebar(this)
    private val header = Header()

    var page: Page? = null
        private set

    private val panel = object : Row() {
        override fun render(graphics: GuiGraphicsExtractor) {
            val t = appear.get()
            if (closing && t < 0.04f) {
                finishClose()
                return
            }
            if (t >= 0.999f) {
                super.render(graphics)
                return
            }
            // Open/close: grow from 96% around the centre.
            val s = 0.96f + 0.04f * t
            val pose = graphics.pose()
            pose.pushMatrix()
            pose.translate(x + width / 2f, y + height / 2f)
            pose.scale(s, s)
            pose.translate(-(x + width / 2f), -(y + height / 2f))
            super.render(graphics)
            pose.popMatrix()
        }
    }.apply {
        align = Align.STRETCH
        fill = { theme.background }
        border = { theme.border }
        radius = { theme.radiusLarge + 3f }
        shadow = true
        position = CenterPositionConstraint()
        size = object : ISizeConstraint {
            override fun _width(element: IPrimitiveElement<*>, parent: IPrimitiveElement<*>) = minOf(1000f, parent.width - 32f)
            override fun _height(element: IPrimitiveElement<*>, parent: IPrimitiveElement<*>) = minOf(580f, parent.height - 32f)
        }
    }

    init {
        panel.adopt(sidebar)
        panel.adopt(Column().apply {
            grow = true
            adopt(header)
            adopt(Divider())
            adopt(host.apply { fillHeight = true })
        })
        add(panel)

        val place = MenuState.get(mod)
        show(when (place.page) {
            Page.KEYS -> BoundKeysPage(this)
            Page.SETTINGS -> SettingsPage(this)
            Page.CUSTOMIZE -> CustomizePage(this)
            Page.PROFILES -> ProfilesPage(this)
            Page.MODULES -> place.module?.let { id -> mod.module(id)?.let { ModulePage(this, it) } } ?: ModulesPage(this)
            else -> mod.pages.firstOrNull { "custom:${it.name}" == place.page }?.let { CustomPageView(this, it) } ?: ModulesPage(this)
        })
        appear.snap(0f).to(1f)
    }

    override fun init() {
        resolution = CascadeGeometricResolution.FHD.of(2f * AstraPrefs.uiScale.factor)
        super.init()
    }

    /** Re-applies the UI scale after it changes. */
    fun rescale() = init()

    fun show(next: Page) {
        page = next
        MenuState.set(mod, next.place())
        search.text = ""
        search.visible = next.searchPlaceholder != null
        search.placeholder = next.searchPlaceholder ?: ""
        host.set(next.build())
        if (search.isFocused) search.blur()
        scene.dirty()
    }

    fun openModule(module: Module, focusOption: String? = null) = show(ModulePage(this, module, focusOption))
    fun openModules(category: String? = null) = show(ModulesPage(this, category))

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (AstraPrefs.blur) super.extractBackground(graphics, mouseX, mouseY, delta)
        else extractTransparentBackground(graphics)
    }

    override fun onClose() {
        if (closing) return
        if (!Themes.current.animations) {
            super.onClose()
            return
        }
        closing = true
        appear.to(0f)
    }

    private fun finishClose() {
        closing = false
        super.onClose()
    }

    // ---- pieces --------------------------------------------------------------------------------

    /** Holds the current page's content at full size. */
    private class PageHost : AstraElement() {
        fun set(content: AstraElement) {
            for (c in children.toList()) disown(c)
            adopt(content)
        }

        override fun layout() {
            dirty = false
            for (c in children) (c as? AstraElement)?.place(x, y, width, height)
        }
    }

    private inner class Header : Row() {
        init {
            fixedHeight = 52f
            paddingX = 20f
            gap = 12f
            adopt(TitleBlock().apply { grow = true })
            adopt(SearchBox())
        }
    }

    private inner class TitleBlock : AstraElement() {
        override fun prefHeight(width: Float): Float {
            val th = theme
            return Paint.lineHeight(th.fontHeading) + if (page?.subtitle.isNullOrEmpty()) 0f else 3f + Paint.lineHeight(th.fontSmall)
        }

        override fun draw(graphics: GuiGraphicsExtractor) {
            val th = theme
            val p = page ?: return
            Paint.text(graphics, Paint.truncate(p.title, th.fontHeading, width, true), x, y, th.text, th.fontHeading, true)
            if (p.subtitle.isNotEmpty()) Paint.text(graphics, Paint.truncate(p.subtitle, th.fontSmall, width), x, y + Paint.lineHeight(th.fontHeading) + 3f, th.textMuted, th.fontSmall)
        }
    }

    /** The search field with a magnifier drawn over its left side. */
    private inner class SearchBox : Row() {
        init {
            adopt(search)
        }

        override fun prefWidth() = if (search.visible) search.prefWidth() else 0f

        override fun draw(graphics: GuiGraphicsExtractor) {}

        override fun render(graphics: GuiGraphicsExtractor) {
            if (!visible || !search.visible) return
            super.render(graphics)
            if (search.text.isEmpty() && !search.isFocused) {
                Icons.draw(graphics, Icons.Kind.SEARCH, search.x + search.width - 15f, search.y + 5f, 8f, theme.textFaint, theme.surfaceRaised)
            }
        }
    }

    class Divider(val vertical: Boolean = false) : AstraElement() {
        init {
            if (vertical) fixedWidth = 1f else fixedHeight = 1f
        }

        override fun draw(graphics: GuiGraphicsExtractor) = Paint.rect(graphics, x, y, width, height, theme.border)
    }
}
