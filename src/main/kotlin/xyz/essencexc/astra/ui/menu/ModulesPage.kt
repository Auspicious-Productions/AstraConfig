package xyz.essencexc.astra.ui.menu

import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.api.Category
import xyz.essencexc.astra.api.Module
import xyz.essencexc.astra.core.Option
import xyz.essencexc.astra.core.OptionSpec
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.core.AstraWidget
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.layout.Column
import xyz.essencexc.astra.ui.layout.Grid
import xyz.essencexc.astra.ui.layout.ScrollPane
import xyz.essencexc.astra.ui.util.Colors
import xyz.essencexc.astra.ui.util.Paint
import xyz.essencexc.astra.ui.widgets.Label

/** One searchable thing: a module, or one of its settings. */
internal class SearchHit(val module: Module, val option: Option<*>?, val score: Int)

/** Matches modules and settings of a mod against a query. */
internal object SearchIndex {
    fun search(screen: ConfigScreen, query: String): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val hits = ArrayList<SearchHit>()
        for (m in screen.mod.modules()) {
            score(q, m.name, m.description, emptyList())?.let { hits += SearchHit(m, null, it + 5) }
            for (o in m.options) {
                if (o.spec == OptionSpec.MasterToggleSpec) continue
                score(q, o.title, o.description, o.keywords + o.section)?.let { hits += SearchHit(m, o, it) }
            }
        }
        return hits.sortedByDescending { it.score }
    }

    /** Higher is better; null = no match. Title prefix > title word > title > keywords/description. */
    private fun score(q: String, title: String, description: String, extra: List<String>): Int? {
        val t = title.lowercase()
        return when {
            t == q -> 100
            t.startsWith(q) -> 80
            t.split(' ').any { it.startsWith(q) } -> 60
            t.contains(q) -> 40
            extra.any { it.lowercase().contains(q) } -> 20
            description.lowercase().contains(q) -> 10
            else -> null
        }
    }
}

/**
 * Every module as a card, grouped under its category heading. The header search shows matching
 * modules and individual settings; clicking a setting opens its module and highlights it.
 */
internal class ModulesPage(screen: ConfigScreen, private val jumpTo: String? = null) : Page(screen) {
    override val key = MODULES
    override val title = "Modules"
    override val subtitle: String
        get() {
            val all = screen.mod.modules()
            val on = all.count { it.master != null && it.isEnabled() }
            return "${all.size} modules · $on enabled"
        }
    override val searchPlaceholder = "Search modules and settings"

    /** The category heading currently at the top (for the sidebar). */
    var activeCategory: String? = null
        private set

    private val pane = ScrollPane()
    private val blocks = LinkedHashMap<Category, Column>()
    private val results = Column().apply { gap = 10f; visible = false }
    private var pendingJump = jumpTo

    override fun build(): AstraElement {
        pane.content.padding = 20f
        pane.content.gap = 22f
        for (c in screen.mod.categories) {
            val block = Column().apply {
                gap = 10f
                adopt(HeadingLine(c.name) { "${c.modules.size}" })
                adopt(Grid().apply {
                    gap = 10f
                    minCellWidth = 170f
                    maxColumns = 4
                    c.modules.forEach { m -> adopt(ModuleCard(m) { screen.openModule(m) }) }
                })
            }
            blocks[c] = block
            pane.content.adopt(block)
        }
        pane.content.adopt(results)
        return Frame(pane, ::frame)
    }

    private fun frame() {
        pendingJump?.let { name ->
            val block = blocks.entries.firstOrNull { it.key.name == name }?.value
            if (block != null && pane.content.height > 0f) {
                pane.scrollToTop(block, 4f)
                pendingJump = null
            }
        }
        // Scroll-spy: last heading above the top edge (+ a little).
        activeCategory = blocks.entries.lastOrNull { it.value.visible && it.value.y <= pane.y + 40f }?.key?.name
            ?: blocks.keys.firstOrNull()?.name
        if (pane.maxScroll > 0f && pane.scroll >= pane.maxScroll - 1f) activeCategory = blocks.entries.lastOrNull { it.value.visible }?.key?.name
    }

    override fun onSearch(query: String) {
        val searching = query.isNotBlank()
        blocks.values.forEach { it.visible = !searching }
        results.visible = searching
        results.clear()
        if (searching) {
            val hits = SearchIndex.search(screen, query)
            val modules = hits.filter { it.option == null }.map { it.module }.distinct()
            val settings = hits.filter { it.option != null }.take(60)
            if (modules.isNotEmpty()) {
                results.adopt(HeadingLine("Modules") { "${modules.size}" })
                results.adopt(Grid().apply {
                    gap = 10f
                    minCellWidth = 170f
                    maxColumns = 4
                    modules.forEach { m -> adopt(ModuleCard(m) { screen.openModule(m) }) }
                })
            }
            if (settings.isNotEmpty()) {
                results.adopt(HeadingLine("Settings") { "${settings.size}" })
                results.adopt(Card().apply { settings.forEach { adopt(ResultRow(it)) } })
            }
            if (hits.isEmpty()) results.adopt(Label("Nothing matches \"${query.trim()}\".", muted = true))
        }
        pane.scrollTo(0f, animate = false)
        screen.scene.dirty()
    }

    /** A setting found by search: title, breadcrumb, and where it lives. */
    private inner class ResultRow(val hit: SearchHit) : AstraWidget() {
        private val option = hit.option!!
        private val crumb = buildString {
            val cat = screen.mod.categories.firstOrNull { hit.module in it.modules }?.name
            if (cat != null) append(cat).append("  ›  ")
            append(hit.module.name)
            if (option.section.isNotEmpty()) append("  ›  ").append(option.section)
        }

        init {
            fixedHeight = 36f
        }

        private fun go() = screen.openModule(hit.module, option.key)

        override fun onMouseUp(mx: Double, my: Double, button: Int, wasPressed: Boolean): Boolean {
            if (wasPressed && button == 0 && hit(mx, my)) go()
            return true
        }

        override fun onKey(key: Int): Boolean {
            if (!Keys.isActivate(key)) return false
            go()
            return true
        }

        override fun paint(g: GuiGraphicsExtractor) {
            val th = theme
            val h = hover.get()
            if (h > 0.01f) Paint.rect(g, x + 3f, y + 2f, width - 6f, height - 4f, Colors.fade(th.surfaceHover, h), th.radiusMedium)
            focusRing(g, x + 3f, y + 2f, width - 6f, height - 4f, th.radiusMedium)
            drawModuleIcon(g, this, hit.module, x + 10f, y + 8f, 20f)
            Paint.text(g, option.title, x + 38f, y + 7f, th.text, th.fontBody)
            Paint.text(g, Paint.truncate(crumb, th.fontSmall, width - 80f), x + 38f, y + 9f + Paint.lineHeight(th.fontBody), th.textMuted, th.fontSmall)
            Icons.draw(g, Icons.Kind.CHEVRON_RIGHT, x + width - 20f, y + (height - 8f) / 2f, 8f, Colors.mix(th.textFaint, accentColor(), h), th.surface)
        }
    }
}
