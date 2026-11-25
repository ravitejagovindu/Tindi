package com.tindi.app

import com.tindi.app.ui.theme.TindiPalette
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Every text or icon colour the README names, on the ground it sits on, in both themes. Needs 4.5:1. */
class ContrastTest {
    private fun lin(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    fun ratio(a: Long, b: Long): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    /** foreground token to ground token. */
    private val pairs = listOf(
        "ink" to "bg", "ink" to "surface", "ink" to "chip",
        "ink" to "tile-1", "ink" to "tile-2", "ink" to "tile-3", "ink" to "tile-4",
        "muted" to "bg", "muted" to "surface", "muted" to "chip",
        "on-accent" to "accent",
        "on-danger" to "danger",
        "danger" to "bg", "danger" to "surface",
        "accent" to "on-accent", // check icon inside the switch thumb
    )

    private fun check(name: String, p: Map<String, Long>): List<String> =
        pairs.mapNotNull { (fg, bg) ->
            val r = ratio(p.getValue(fg), p.getValue(bg))
            println("%-5s %-10s on %-8s %.2f:1".format(name, fg, bg, r))
            if (r < 4.5) "$name: $fg on $bg is %.2f:1".format(r) else null
        }

    @Test
    fun allNamedPairsReach45to1InBothThemes() {
        val failures = check("light", TindiPalette.light) + check("dark", TindiPalette.dark)
        assertTrue("Contrast failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
