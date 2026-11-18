package xyz.essencexc.astra.ui.menu

import xyz.essencexc.astra.api.annotations.InfoType
import xyz.essencexc.astra.core.Profiles
import xyz.essencexc.astra.ui.core.AstraElement
import xyz.essencexc.astra.ui.core.Keys
import xyz.essencexc.astra.ui.layout.Align
import xyz.essencexc.astra.ui.layout.Column
import xyz.essencexc.astra.ui.layout.Row
import xyz.essencexc.astra.ui.layout.ScrollPane
import xyz.essencexc.astra.ui.widgets.Button
import xyz.essencexc.astra.ui.widgets.InfoBox
import xyz.essencexc.astra.ui.widgets.Label
import xyz.essencexc.astra.ui.widgets.TextField

/** Save the current settings as named profiles, switch between them, and share settings as text. */
internal class ProfilesPage(screen: ConfigScreen) : Page(screen) {
    override val key = PROFILES
    override val title = "Profiles"
    override val subtitle get() = "${Profiles.list(screen.mod).size} saved · import and export"

    private val pane = ScrollPane()
    private var status: Pair<InfoType, String>? = null
    private val name = TextField().apply {
        grow = true
        placeholder = "Profile name"
        maxLength = 32
        filter = { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
        onKeyHook = { key -> if (Keys.isEnter(key)) { saveNew(); true } else false }
    }
    private val confirm = HashMap<String, Long>()

    override fun build(): AstraElement {
        pane.content.padding = 20f
        pane.content.gap = 18f
        fill()
        return pane
    }

    private fun say(type: InfoType, text: String) {
        status = type to text
        fill()
    }

    private fun saveNew() {
        val n = name.text.trim()
        when {
            !Profiles.valid(n) -> say(InfoType.WARNING, "Use letters, digits, spaces, - or _ (up to 32).")
            Profiles.save(screen.mod, n) -> { name.text = ""; say(InfoType.SUCCESS, "Saved profile \"$n\".") }
            else -> say(InfoType.ERROR, "Couldn't save \"$n\". See the log.")
        }
    }

    /** Destructive buttons need a second click within 3 s. */
    private fun confirmed(id: String): Boolean {
        val now = System.currentTimeMillis()
        val last = confirm[id]
        if (last != null && now - last < 3000) {
            confirm.remove(id)
            return true
        }
        confirm[id] = now
        return false
    }

    private fun fill() {
        val mod = screen.mod
        val c = pane.content
        c.clear()
        status?.let { (t, s) -> c.adopt(InfoBox(t) { s }) }

        c.adopt(Column().apply {
            gap = 8f
            adopt(HeadingLine("Save current settings"))
            adopt(Card().apply {
                adopt(Row().apply {
                    paddingX = 10f
                    paddingY = 8f
                    gap = 8f
                    align = Align.CENTER
                    adopt(name)
                    adopt(Button("Save profile", Button.Style.PRIMARY) { saveNew() })
                })
            })
        })

        val profiles = Profiles.list(mod)
        c.adopt(Column().apply {
            gap = 8f
            adopt(HeadingLine("Saved profiles") { "${profiles.size}" })
            if (profiles.isEmpty()) {
                adopt(Label("No profiles yet. Save one above to switch between setups.", muted = true))
            } else {
                adopt(Card().apply {
                    for (p in profiles) {
                        adopt(SettingRow(p, null, Row().apply {
                            gap = 4f
                            align = Align.CENTER
                            adopt(Button("Load", Button.Style.PRIMARY) {
                                if (Profiles.load(mod, p)) say(InfoType.SUCCESS, "Loaded \"$p\".") else say(InfoType.ERROR, "Couldn't load \"$p\". See the log.")
                            })
                            adopt(Button("Overwrite") {
                                if (confirmed("over:$p")) {
                                    if (Profiles.save(mod, p)) say(InfoType.SUCCESS, "Overwrote \"$p\" with the current settings.")
                                } else say(InfoType.INFO, "Click Overwrite again to replace \"$p\" with the current settings.")
                            })
                            adopt(Button("Delete", Button.Style.DANGER) {
                                if (confirmed("del:$p")) {
                                    Profiles.delete(mod, p)
                                    say(InfoType.SUCCESS, "Deleted \"$p\".")
                                } else say(InfoType.WARNING, "Click Delete again to remove \"$p\".")
                            })
                        }))
                    }
                })
            }
        })

        c.adopt(Column().apply {
            gap = 8f
            adopt(HeadingLine("Share"))
            adopt(Card().apply {
                adopt(SettingRow("Export", "Copies all of ${mod.name}'s settings to the clipboard as text.", Button("Copy") {
                    val text = Profiles.export(mod)
                    if (text != null) {
                        Keys.clipboard = text
                        say(InfoType.SUCCESS, "Settings copied to the clipboard.")
                    }
                }))
                adopt(SettingRow("Import", "Applies settings from the clipboard. Save a profile first if you want to keep the current ones.", Button("Paste") {
                    if (!confirmed("import")) {
                        say(InfoType.INFO, "Click Paste again to replace the current settings with the clipboard's.")
                    } else when (val r = Profiles.import(mod, Keys.clipboard)) {
                        Profiles.ImportResult.Ok -> say(InfoType.SUCCESS, "Imported settings from the clipboard.")
                        is Profiles.ImportResult.Failed -> say(InfoType.ERROR, r.reason)
                    }
                }))
            })
        })
        screen.scene.dirty()
    }
}
