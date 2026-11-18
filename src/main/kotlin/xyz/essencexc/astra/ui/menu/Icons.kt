package xyz.essencexc.astra.ui.menu

import net.minecraft.client.gui.GuiGraphicsExtractor
import xyz.essencexc.astra.ui.util.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small line icons drawn with shapes, so the menu needs no texture or SVG support (Cascade's SVG
 * loader needs JSVG, which isn't on the classpath). Each draws into an [s]×[s] box at (x, y).
 */
internal object Icons {
    enum class Kind { GRID, KEY, GEAR, PALETTE, PAGE, SEARCH, BACK, CLOSE, CHEVRON_RIGHT, HUD }

    fun draw(g: GuiGraphicsExtractor, kind: Kind, x: Float, y: Float, s: Float, c: Int, bg: Int) {
        val t = 1.2f
        when (kind) {
            Kind.GRID -> {
                val q = (s - 2f) / 2f
                for (i in 0..1) for (j in 0..1) Paint.outline(g, x + i * (q + 2f), y + j * (q + 2f), q, q, c, 1.5f, t)
            }
            Kind.KEY -> {
                Paint.outline(g, x, y + s * 0.18f, s, s * 0.64f, c, 2f, t)
                for (i in 0..2) Paint.rect(g, x + s * (0.2f + i * 0.25f) - 0.6f, y + s * 0.36f, 1.2f, 1.2f, c)
                Paint.rect(g, x + s * 0.28f, y + s * 0.58f, s * 0.44f, 1.2f, c)
            }
            Kind.GEAR -> {
                val cx = x + s / 2f; val cy = y + s / 2f
                for (i in 0 until 8) {
                    val a = i * PI.toFloat() / 4f
                    Paint.line(g, cx + cos(a) * s * 0.3f, cy + sin(a) * s * 0.3f, cx + cos(a) * s * 0.48f, cy + sin(a) * s * 0.48f, c, 1.8f)
                }
                Paint.circle(g, cx, cy, s * 0.33f, c)
                Paint.circle(g, cx, cy, s * 0.14f, bg)
            }
            Kind.PALETTE -> {
                val cx = x + s / 2f; val cy = y + s / 2f
                for (i in 0 until 3) {
                    val a = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / 3f
                    Paint.circle(g, cx + cos(a) * s * 0.22f, cy + sin(a) * s * 0.22f, s * 0.2f, c)
                }
            }
            Kind.PAGE -> {
                Paint.outline(g, x + s * 0.15f, y, s * 0.7f, s, c, 1.5f, t)
                for (i in 0..2) Paint.rect(g, x + s * 0.3f, y + s * (0.28f + i * 0.2f), s * 0.4f, 1.1f, c)
            }
            Kind.SEARCH -> {
                val r = s * 0.32f
                val cx = x + r + 1f; val cy = y + r + 1f
                Paint.circle(g, cx, cy, r, c)
                Paint.circle(g, cx, cy, r - t, bg)
                Paint.line(g, cx + r * 0.7f, cy + r * 0.7f, x + s - 0.5f, y + s - 0.5f, c, 1.5f)
            }
            Kind.BACK -> {
                Paint.line(g, x + s * 0.2f, y + s / 2f, x + s * 0.85f, y + s / 2f, c, 1.4f)
                Paint.line(g, x + s * 0.2f, y + s / 2f, x + s * 0.5f, y + s * 0.2f, c, 1.4f)
                Paint.line(g, x + s * 0.2f, y + s / 2f, x + s * 0.5f, y + s * 0.8f, c, 1.4f)
            }
            Kind.CLOSE -> {
                Paint.line(g, x + s * 0.2f, y + s * 0.2f, x + s * 0.8f, y + s * 0.8f, c, 1.4f)
                Paint.line(g, x + s * 0.8f, y + s * 0.2f, x + s * 0.2f, y + s * 0.8f, c, 1.4f)
            }
            Kind.HUD -> {
                Paint.outline(g, x, y + s * 0.1f, s, s * 0.8f, c, 1.5f, t)
                Paint.rect(g, x + s * 0.18f, y + s * 0.26f, s * 0.32f, s * 0.2f, c, 0.8f)
                Paint.rect(g, x + s * 0.6f, y + s * 0.58f, s * 0.22f, s * 0.14f, c, 0.8f)
            }
            Kind.CHEVRON_RIGHT -> {
                Paint.line(g, x + s * 0.35f, y + s * 0.2f, x + s * 0.65f, y + s / 2f, c, 1.3f)
                Paint.line(g, x + s * 0.65f, y + s / 2f, x + s * 0.35f, y + s * 0.8f, c, 1.3f)
            }
        }
    }
}
