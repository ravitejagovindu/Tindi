package com.tindi.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.tindi.app.domain.Illustration
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiColors
import kotlin.math.min

/** The only colours an illustration may use, all from the design tokens. */
private enum class Ink { Plate, PlateInner, Accent, Mango, Text, OnAccent }

private fun TindiColors.of(token: Ink): Color = when (token) {
    Ink.Plate -> plate
    Ink.PlateInner -> plateInner
    Ink.Accent -> accent
    Ink.Mango -> mango
    Ink.Text -> ink
    Ink.OnAccent -> onAccent
}

/** One shape of a drawing, in the coordinates of its view box. */
private sealed interface Shape {
    val ink: Ink
    val alpha: Float

    class Block(
        val x: Float, val y: Float, val w: Float, val h: Float, val r: Float,
        override val ink: Ink, override val alpha: Float = 1f,
    ) : Shape

    class Oval(
        val cx: Float, val cy: Float, val rx: Float, val ry: Float,
        override val ink: Ink, override val alpha: Float = 1f,
    ) : Shape

    class Fill(val data: String, override val ink: Ink, override val alpha: Float = 1f) : Shape

    class Line(val data: String, override val ink: Ink, val width: Float, override val alpha: Float = 1f) : Shape
}

private fun block(x: Int, y: Int, w: Int, h: Int, r: Int, ink: Ink, alpha: Float = 1f) =
    Shape.Block(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r.toFloat(), ink, alpha)

private fun oval(cx: Int, cy: Int, rx: Int, ry: Int, ink: Ink, alpha: Float = 1f) =
    Shape.Oval(cx.toFloat(), cy.toFloat(), rx.toFloat(), ry.toFloat(), ink, alpha)

private fun dot(cx: Int, cy: Int, r: Int, ink: Ink, alpha: Float = 1f) = oval(cx, cy, r, r, ink, alpha)

private fun fill(data: String, ink: Ink, alpha: Float = 1f) = Shape.Fill(data, ink, alpha)

private fun line(data: String, ink: Ink, width: Int, alpha: Float = 1f) = Shape.Line(data, ink, width.toFloat(), alpha)

private const val ART_W = 300f
private const val ART_H = 150f

/** The frying pan, shared by Simmer (steam) and Saute (tossed pieces). */
private val pan = listOf(
    oval(140, 100, 86, 26, Ink.Text),
    oval(140, 92, 80, 22, Ink.Accent),
    oval(140, 90, 58, 14, Ink.Mango, 0.55f),
    block(214, 90, 64, 12, 6, Ink.Text),
)

// Blend, Mix (spice jars), Serve (bowl), the pan and Generic (plate) are the artboard's own drawings.
// The rest are drawn the same way: flat shapes, 300 by 150.
private fun shapes(picture: Illustration): List<Shape> = when (picture) {
    Illustration.Blend -> listOf(
        fill("M96 24h108l-14 96a14 14 0 0 1-14 12h-52a14 14 0 0 1-14-12z", Ink.Plate),
        block(88, 12, 124, 16, 8, Ink.Text),
        dot(130, 86, 14, Ink.Accent),
        dot(162, 98, 12, Ink.Accent),
        dot(150, 66, 10, Ink.Mango),
        dot(124, 112, 8, Ink.Mango),
        block(104, 136, 92, 10, 5, Ink.Text, 0.85f),
    )

    Illustration.Simmer -> pan + line(
        "M110 60c-10-12 10-18 0-30M140 56c-10-12 10-18 0-30M170 60c-10-12 10-18 0-30", Ink.Plate, 5,
    )

    Illustration.Saute -> pan + listOf(
        block(96, 42, 24, 22, 7, Ink.Mango),
        block(134, 24, 24, 22, 7, Ink.Accent),
        block(174, 40, 22, 20, 7, Ink.Plate),
        dot(124, 18, 5, Ink.Plate),
        dot(180, 18, 4, Ink.Mango),
        line("M92 76c4-6 8-8 12-9M190 74c-4-6-8-8-12-9", Ink.Plate, 5),
    )

    Illustration.Mix -> listOf(
        block(52, 52, 56, 78, 14, Ink.Plate),
        block(52, 40, 56, 18, 8, Ink.Accent),
        block(122, 52, 56, 78, 14, Ink.Plate),
        block(122, 40, 56, 18, 8, Ink.Mango),
        block(192, 52, 56, 78, 14, Ink.Plate),
        block(192, 40, 56, 18, 8, Ink.Text),
        dot(80, 92, 10, Ink.Accent),
        dot(150, 92, 10, Ink.Mango),
        dot(220, 92, 10, Ink.Text, 0.5f),
        dot(96, 22, 4, Ink.Accent),
        dot(150, 16, 5, Ink.Mango),
        dot(206, 24, 4, Ink.Accent),
    )

    Illustration.Serve -> listOf(
        fill("M70 70h160a80 70 0 0 1-160 0z", Ink.Plate),
        oval(150, 70, 80, 12, Ink.Accent),
        block(100, 42, 30, 30, 8, Ink.PlateInner),
        block(140, 34, 30, 30, 8, Ink.PlateInner),
        block(178, 44, 30, 30, 8, Ink.PlateInner),
        line("M110 76c20 14 60 14 80 0", Ink.Mango, 6),
    )

    Illustration.Prep -> listOf(
        block(52, 94, 196, 36, 14, Ink.Plate),
        dot(228, 112, 7, Ink.Mango),
        dot(100, 74, 24, Ink.Accent),
        line("M100 50c2-8 8-12 14-12", Ink.Text, 5),
        dot(143, 84, 11, Ink.Mango),
        dot(167, 86, 10, Ink.Mango),
        dot(189, 87, 8, Ink.Accent),
        fill("M146 54L220 22l10 20c-18 18-48 24-84 12z", Ink.Plate),
        fill("M218 20l34-15a9 9 0 0 1 8 17l-33 17z", Ink.Accent),
    )

    Illustration.Soak -> listOf(
        fill("M70 70h160a80 70 0 0 1-160 0z", Ink.Plate),
        oval(150, 70, 80, 12, Ink.Accent, 0.4f),
        dot(118, 68, 9, Ink.Mango),
        dot(146, 73, 9, Ink.Mango),
        dot(176, 68, 9, Ink.Mango),
        fill("M114 16c8 11 12 17 12 23a12 12 0 0 1-24 0c0-6 4-12 12-23z", Ink.Accent),
        fill("M154 4c7 10 10 15 10 20a10 10 0 0 1-20 0c0-5 3-10 10-20z", Ink.Plate),
        fill("M190 18c8 11 12 17 12 23a12 12 0 0 1-24 0c0-6 4-12 12-23z", Ink.Accent),
        line("M122 104c18 10 38 10 56 0", Ink.Mango, 6),
    )

    Illustration.Boil -> listOf(
        block(64, 76, 34, 12, 6, Ink.Text),
        block(202, 76, 34, 12, 6, Ink.Text),
        block(90, 62, 120, 76, 18, Ink.Plate),
        block(80, 52, 140, 14, 7, Ink.Accent),
        line("M108 98c11-10 21 10 32 0s21-10 32 0 14 6 20 0", Ink.Mango, 6),
        dot(116, 34, 9, Ink.Plate),
        dot(147, 18, 12, Ink.Plate),
        dot(183, 32, 8, Ink.Plate),
        dot(133, 42, 5, Ink.Mango),
        dot(166, 40, 5, Ink.Accent),
        block(104, 140, 92, 8, 4, Ink.Text, 0.85f),
    )

    Illustration.Fry -> listOf(
        fill("M58 78h172a86 52 0 0 1-172 0z", Ink.Text),
        block(222, 74, 62, 12, 6, Ink.Text),
        oval(144, 78, 86, 12, Ink.Mango),
        block(100, 44, 28, 24, 8, Ink.Accent),
        block(142, 26, 26, 22, 8, Ink.Plate),
        block(180, 46, 24, 22, 8, Ink.Accent),
        line("M84 52l-10-12M126 30l-6-14M190 30l8-14M214 56l12-10", Ink.Plate, 5),
        fill("M114 148c-10-8 0-16 2-22 9 7 16 15 6 22z", Ink.Accent),
        fill("M144 148c-12-8 0-17 2-24 10 8 18 17 7 24z", Ink.Mango),
        fill("M176 148c-10-8 0-16 2-22 9 7 16 15 6 22z", Ink.Accent),
    )

    Illustration.Bake -> listOf(
        block(96, 134, 20, 12, 4, Ink.Text),
        block(184, 134, 20, 12, 4, Ink.Text),
        block(76, 12, 148, 128, 18, Ink.Plate),
        dot(100, 31, 7, Ink.Accent),
        dot(122, 31, 7, Ink.Mango),
        block(156, 27, 48, 8, 4, Ink.Text, 0.5f),
        block(92, 50, 116, 74, 14, Ink.Accent),
        block(116, 86, 68, 28, 8, Ink.Mango),
        fill("M116 92c0-18 68-18 68 0z", Ink.Plate),
        dot(150, 70, 7, Ink.Mango),
    )

    Illustration.Steam -> listOf(
        line("M112 38c-9-10 9-15 0-28M150 28c-7-7 7-12 0-22M188 38c-9-10 9-15 0-28", Ink.Plate, 5),
        fill("M92 72c8-36 108-36 116 0z", Ink.Accent),
        dot(150, 43, 8, Ink.Text),
        block(88, 72, 124, 30, 9, Ink.Plate),
        block(88, 105, 124, 30, 9, Ink.Plate),
        dot(118, 87, 5, Ink.Mango),
        dot(150, 87, 5, Ink.Mango),
        dot(182, 87, 5, Ink.Mango),
        dot(118, 120, 5, Ink.Accent),
        dot(150, 120, 5, Ink.Accent),
        dot(182, 120, 5, Ink.Accent),
        block(78, 138, 144, 10, 5, Ink.Text, 0.85f),
    )

    Illustration.Rest -> listOf(
        fill("M48 84h132a66 54 0 0 1-132 0z", Ink.Plate),
        fill("M48 84c6-52 126-52 132 0z", Ink.Accent),
        dot(114, 42, 9, Ink.Text),
        block(42, 80, 144, 10, 5, Ink.Mango),
        dot(228, 62, 38, Ink.Plate),
        dot(228, 62, 29, Ink.PlateInner),
        // The clock face stays light in both themes, so its hands use the accent, not the text colour.
        line("M228 44v18l13 9", Ink.Accent, 6),
    )

    Illustration.Generic -> listOf(
        dot(150, 78, 64, Ink.Plate),
        dot(150, 78, 48, Ink.PlateInner),
        dot(150, 78, 30, Ink.Accent),
        line("M134 76c8-12 20-12 32 0", Ink.Mango, 6),
        fill("M226 40l32 14-24 26z", Ink.Mango),
        fill("M46 100l34-6-8 30z", Ink.Mango),
    )
}

private val plateDone = listOf(
    dot(60, 60, 46, Ink.Plate),
    dot(60, 60, 34, Ink.PlateInner),
    dot(60, 60, 20, Ink.Accent),
    line("M50 60l8 8 14-16", Ink.OnAccent, 5),
)

@Composable
private fun Drawing(shapes: List<Shape>, viewW: Float, viewH: Float, modifier: Modifier) {
    val colors = Tindi.colors
    val paths = remember(shapes) {
        shapes.map { shape ->
            val data = when (shape) {
                is Shape.Fill -> shape.data
                is Shape.Line -> shape.data
                else -> null
            }
            data?.let { PathParser().parsePathString(it).toPath() }
        }
    }
    Canvas(modifier) {
        // Fit the view box inside the canvas and centre it, like an SVG.
        val scale = min(size.width / viewW, size.height / viewH)
        withTransform({
            translate((size.width - viewW * scale) / 2f, (size.height - viewH * scale) / 2f)
            scale(scale, scale, Offset.Zero)
        }) {
            shapes.forEachIndexed { i, shape -> draw(shape, paths[i], colors) }
        }
    }
}

private fun DrawScope.draw(shape: Shape, path: Path?, colors: TindiColors) {
    val color = colors.of(shape.ink)
    when (shape) {
        is Shape.Block -> drawRoundRect(
            color, Offset(shape.x, shape.y), Size(shape.w, shape.h), CornerRadius(shape.r), alpha = shape.alpha,
        )
        is Shape.Oval -> drawOval(
            color, Offset(shape.cx - shape.rx, shape.cy - shape.ry), Size(shape.rx * 2, shape.ry * 2), alpha = shape.alpha,
        )
        is Shape.Fill -> drawPath(path!!, color, alpha = shape.alpha)
        is Shape.Line -> drawPath(
            path!!, color, alpha = shape.alpha,
            style = Stroke(shape.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** The picture on a step card. Decorative: the step text says the same. */
@Composable
fun CookIllustration(picture: Illustration, modifier: Modifier = Modifier) {
    val list = remember(picture) { shapes(picture) }
    Drawing(list, ART_W, ART_H, modifier)
}

/** The finished plate with its tick. */
@Composable
fun DonePlate(modifier: Modifier = Modifier) = Drawing(plateDone, 120f, 120f, modifier)
