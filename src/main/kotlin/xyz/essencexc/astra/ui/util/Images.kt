package xyz.essencexc.astra.ui.util

import foo.starred.cascade.graphics.extensions.image.image
import foo.starred.cascade.graphics.geometry.CascadeGeometricColor
import foo.starred.cascade.wrappers.svg.impl.CascadeSVG
import foo.starred.snowbird.api.client
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import xyz.essencexc.astra.api.Astra
import java.util.concurrent.ConcurrentHashMap

/**
 * Draws icons and logos given as resource ids: `"mymod:textures/gui/fish.png"` or
 * `"mymod:icons/fish.svg"`. SVGs are rasterized (via Cascade + JSVG) at twice the drawn size and
 * cached. A missing or broken image draws nothing and returns false, so callers can fall back;
 * it's logged once.
 */
object Images {
    private val ids = ConcurrentHashMap<String, Identifier>()
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val exists = ConcurrentHashMap<Identifier, Boolean>()

    private fun fail(id: String, why: String): Boolean {
        if (failed.add(id)) Astra.LOGGER.warn("Astra: icon '{}' can't be shown: {}", id, why)
        return false
    }

    /** True if [id] looks drawable (parses, exists, and SVG support is present for .svg). */
    @JvmStatic
    fun available(id: String?): Boolean {
        if (id.isNullOrBlank() || id in failed) return false
        val ident = ids.getOrPut(id) { Identifier.tryParse(id) ?: return fail(id, "not a valid resource id") }
        val present = exists.getOrPut(ident) { client.resourceManager.getResource(ident).isPresent }
        if (!present) return fail(id, "no such resource")
        if (id.endsWith(".svg", ignoreCase = true) && !CascadeSVG.available) return fail(id, "SVG support (JSVG) isn't on the classpath")
        return true
    }

    /** Draws [id] into the box; returns false (drawing nothing) if it can't. [tint] multiplies colours. */
    @JvmStatic
    @JvmOverloads
    fun draw(g: GuiGraphicsExtractor, id: String?, x: Float, y: Float, w: Float, h: Float, tint: Int = -1, radius: Float = 0f): Boolean {
        if (!available(id)) return false
        val ident = ids[id!!] ?: return false
        return try {
            val texture = if (id.endsWith(".svg", ignoreCase = true)) {
                CascadeSVG.load(ident, Math.ceil(w * 2.0).toInt().coerceAtLeast(1), Math.ceil(h * 2.0).toInt().coerceAtLeast(1))
            } else ident
            g.image(texture, x, y, w, h, color = CascadeGeometricColor(tint), radius = Paint.radius(radius))
            true
        } catch (e: Throwable) { // incl. LinkageError from a mismatched SVG library: never crash over an icon
            fail(id, e.toString())
        }
    }
}
