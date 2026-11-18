package xyz.essencexc.astra.api

import xyz.essencexc.astra.ui.core.AstraElement

/** Builds a custom page's content each time the page is opened. */
fun interface PageFactory {
    fun create(): AstraElement
}

/**
 * A page a mod adds to its menu's sidebar (MAIN group), e.g. "Routes" or "Webhooks". The content
 * is any Astra element; a `ScrollPane` with `Column`s and widgets is the usual choice.
 */
class CustomPage internal constructor(
    @get:JvmName("name") val name: String,
    @get:JvmName("subtitle") val subtitle: String,
    internal val factory: PageFactory,
)
