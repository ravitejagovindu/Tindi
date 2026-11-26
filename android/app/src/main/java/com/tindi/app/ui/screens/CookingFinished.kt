package com.tindi.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tindi.app.domain.finishedLine
import com.tindi.app.ui.components.DonePlate
import com.tindi.app.ui.components.PrimaryButton
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType

/** A gentle 5dp rise and fall, for illustrations only. Does nothing when [enabled] is false. */
@Composable
internal fun Modifier.bob(enabled: Boolean): Modifier {
    if (!enabled) return this
    val transition = rememberInfiniteTransition(label = "bob")
    val lift = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "lift",
    )
    // Read inside the layer block, so only the layer moves and nothing recomposes.
    return graphicsLayer { translationY = -5.dp.toPx() * lift.value }
}

@Composable
internal fun CookingFinished(
    recipeName: String,
    totalSteps: Int,
    reducedMotion: Boolean,
    onBackToLibrary: () -> Unit,
    onAgain: () -> Unit,
) {
    val c = Tindi.colors
    // Where the words and buttons are on screen, so no confetti comes to rest behind them.
    var screenTop by remember { mutableFloatStateOf(0f) }
    var words by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { screenTop = it.positionInRoot().y }) {
        val screen = maxHeight
        Confetti(
            animate = !reducedMotion,
            words = { words?.let { (it.start - screenTop)..(it.endInclusive - screenTop) } },
            Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = screen)
                .padding(horizontal = TindiDims.space5, vertical = TindiDims.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.bob(!reducedMotion).size(190.dp).background(c.tiles[0], CircleShape),
                contentAlignment = Alignment.Center,
            ) { DonePlate(Modifier.size(150.dp)) }
            Spacer(Modifier.height(TindiDims.space4))
            Column(
                Modifier.fillMaxWidth().onGloballyPositioned {
                    val top = it.positionInRoot().y
                    words = top..(top + it.size.height)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "$recipeName is ready!",
                    style = TindiType.titleLg.copy(color = c.ink, textAlign = TextAlign.Center),
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(TindiDims.space3))
                Text(finishedLine(totalSteps), style = TindiType.bodyLg.copy(color = c.muted, textAlign = TextAlign.Center))
                Spacer(Modifier.height(TindiDims.space8))
                PrimaryButton("Back to library", onBackToLibrary)
                Spacer(Modifier.height(TindiDims.space1))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TindiDims.touchMin)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(role = Role.Button, onClick = onAgain),
                    contentAlignment = Alignment.Center,
                ) { Text("Cook it again", style = TindiType.bodyLgBold.copy(color = c.ink)) }
            }
        }
    }
}

private enum class Piece { Dot, Bar, Triangle }

/** From this height on, an artboard piece belongs under the buttons. */
private const val LOWER_PIECES = 390f

/** Where a piece rests on the artboard's 390 by 780 screen. [angle] is for bars and triangles. */
private class Confetto(
    val piece: Piece, val x: Float, val y: Float, val size: Float, val mango: Boolean, val angle: Float = 0f,
)

private val confetti = listOf(
    Confetto(Piece.Dot, 50f, 120f, 20f, false),
    Confetto(Piece.Dot, 330f, 90f, 16f, true),
    Confetto(Piece.Bar, 313f, 176f, 26f, false, 30f),
    Confetto(Piece.Bar, 73f, 206f, 26f, true, -25f),
    Confetto(Piece.Triangle, 345f, 287f, 18f, false),
    Confetto(Piece.Triangle, 39f, 304f, 18f, true, 60f),
    Confetto(Piece.Dot, 350f, 430f, 18f, true),
    Confetto(Piece.Dot, 36f, 470f, 14f, false),
    Confetto(Piece.Bar, 323f, 566f, 26f, false, -30f),
    Confetto(Piece.Bar, 59f, 596f, 26f, true, 20f),
)

/**
 * Falls into place once. Without animation the pieces are simply there. [words] is the band of the canvas that
 * holds text and buttons: pieces from the upper part of the artboard rest above it, the others below it when
 * there is room, so the text always sits on the plain background.
 */
@Composable
private fun Confetti(animate: Boolean, words: () -> ClosedFloatingPointRange<Float>?, modifier: Modifier) {
    val c = Tindi.colors
    // Saved, so turning the phone does not drop the confetti again.
    var played by rememberSaveable { mutableStateOf(false) }
    val fall = remember { Animatable(if (animate && !played) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate && !played) {
            fall.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
        } else {
            fall.snapTo(1f)
        }
        played = true
    }
    val triangle = remember { Path() }
    Canvas(modifier) {
        val progress = fall.value
        val band = words() ?: return@Canvas
        val below = size.height - band.endInclusive
        confetti.forEachIndexed { i, p ->
            // Positions stretch with the screen; the shapes keep their size.
            val rest = when {
                p.y < LOWER_PIECES -> p.y / 340f * band.start
                below >= 72.dp.toPx() -> band.endInclusive + (p.y - LOWER_PIECES) / 250f * below
                else -> return@forEachIndexed
            }
            val drop = (1f - progress) * (80 + 30 * (i % 4)).dp.toPx()
            val centre = Offset(p.x / 390f * size.width, rest - drop)
            val color = if (p.mango) c.mango else c.accent
            val s = p.size.dp.toPx()
            val spin = p.angle + (1f - progress) * if (i % 2 == 0) 180f else -180f
            rotate(spin, centre) {
                when (p.piece) {
                    Piece.Dot -> drawCircle(color, s / 2, centre, alpha = progress)
                    Piece.Bar -> drawRoundRect(
                        color, Offset(centre.x - s / 2, centre.y - s * 0.23f), Size(s, s * 0.46f),
                        CornerRadius(s * 0.23f), alpha = progress,
                    )
                    Piece.Triangle -> {
                        triangle.reset()
                        triangle.moveTo(centre.x - s / 2, centre.y - s / 2)
                        triangle.lineTo(centre.x + s / 2, centre.y - s * 0.1f)
                        triangle.lineTo(centre.x - s * 0.15f, centre.y + s / 2)
                        triangle.close()
                        drawPath(triangle, color, alpha = progress)
                    }
                }
            }
        }
    }
}
