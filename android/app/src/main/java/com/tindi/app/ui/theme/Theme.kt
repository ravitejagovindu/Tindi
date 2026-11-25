@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.tindi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tindi.app.R

@Immutable
data class TindiColors(
    val bg: Color,
    val surface: Color,
    val chip: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val mango: Color,
    val line: Color,
    val danger: Color,
    val onDanger: Color,
    val tiles: List<Color>,
    val plate: Color,
    val plateInner: Color,
    val isDark: Boolean,
)

/** Values from tokens.json. Kept as plain longs so unit tests can read them without Compose. */
object TindiPalette {
    val light = mapOf(
        "bg" to 0xFFFFF5F7, "surface" to 0xFFFFFFFF, "chip" to 0xFFFFE0E8, "ink" to 0xFF2B0F1B,
        "muted" to 0xFF6D4A58, "accent" to 0xFFB3174F, "on-accent" to 0xFFFFFFFF, "mango" to 0xFFFFB020,
        "line" to 0xFFF2D6DE, "danger" to 0xFFB3261E, "on-danger" to 0xFFFFFFFF,
        "tile-1" to 0xFFFFD0DE, "tile-2" to 0xFFFFE3A8, "tile-3" to 0xFFF3C8F0, "tile-4" to 0xFFFFD7C4,
        "plate" to 0xFFFFFFFF, "plate-inner" to 0xFFFFF0F4,
    )
    val dark = mapOf(
        "bg" to 0xFF140A12, "surface" to 0xFF22121F, "chip" to 0xFF33192E, "ink" to 0xFFFFEFF5,
        "muted" to 0xFFD2B3C3, "accent" to 0xFFFF6B9A, "on-accent" to 0xFF2A0615, "mango" to 0xFFFFB020,
        "line" to 0xFF3A2036, "danger" to 0xFFFFB4AB, "on-danger" to 0xFF410002,
        "tile-1" to 0xFF5A1F3C, "tile-2" to 0xFF5A4310, "tile-3" to 0xFF4A2150, "tile-4" to 0xFF5A2E24,
        "plate" to 0xFFF5DDE6, "plate-inner" to 0xFFFFF3F7,
    )
}

private fun paletteColors(p: Map<String, Long>, dark: Boolean): TindiColors {
    fun c(k: String) = Color(p.getValue(k))
    return TindiColors(
        bg = c("bg"), surface = c("surface"), chip = c("chip"), ink = c("ink"), muted = c("muted"),
        accent = c("accent"), onAccent = c("on-accent"), mango = c("mango"), line = c("line"),
        danger = c("danger"), onDanger = c("on-danger"),
        tiles = listOf(c("tile-1"), c("tile-2"), c("tile-3"), c("tile-4")),
        plate = c("plate"), plateInner = c("plate-inner"), isDark = dark,
    )
}

val LightColors = paletteColors(TindiPalette.light, false)
val DarkColors = paletteColors(TindiPalette.dark, true)

private val SoraFamily = FontFamily(
    Font(R.font.sora, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)
private val ManropeFamily = FontFamily(
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun display(size: Int, line: Int) = TextStyle(
    fontFamily = SoraFamily, fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = (-0.02).em,
)

private fun text(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = ManropeFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
)

@Immutable
object TindiType {
    val wordmark = display(44, 44)
    val titleLg = display(30, 34)
    val titleMd = display(24, 28)
    val titleSm = display(20, 24)
    val cookStep = display(31, 37)
    val bodyLg = text(16, 24, FontWeight.Medium)
    val bodyLgBold = text(16, 24, FontWeight.Bold)
    val body = text(15, 22, FontWeight.Medium)
    val label = text(14, 20, FontWeight.Bold)
    val caption = text(13, 18, FontWeight.SemiBold)
    val navLabel = text(12, 16, FontWeight.Bold)
}

object TindiDims {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp

    val radiusSm = 12.dp
    val radiusInput = 16.dp
    val radiusFab = 18.dp
    val radiusCard = 22.dp
    val radiusSheet = 28.dp

    val touchMin = 48.dp
    val buttonH = 56.dp
    val chipH = 44.dp
    val navH = 80.dp
    val fabH = 56.dp
    val icon = 24.dp
    val thumbList = 76.dp
    val thumbCard = 112.dp
    val heroH = 230.dp
}

val LocalTindiColors = staticCompositionLocalOf { LightColors }

object Tindi {
    val colors: TindiColors
        @Composable @ReadOnlyComposable get() = LocalTindiColors.current
}

/** [darkOverride] null = follow the system setting. */
@Composable
fun TindiTheme(darkOverride: Boolean?, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.chip, onSurfaceVariant = c.muted,
            outline = c.line, error = c.danger, onError = c.onDanger,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.chip, onSurfaceVariant = c.muted,
            outline = c.line, error = c.danger, onError = c.onDanger,
        )
    }
    CompositionLocalProvider(LocalTindiColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
