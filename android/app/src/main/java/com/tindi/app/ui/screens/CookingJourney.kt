package com.tindi.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.tindi.app.domain.journeyScrollFor
import com.tindi.app.domain.journeyStopWidths
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TIconImage
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType

/** Room for the current node (36dp) and its 5dp halo, so every stop's line runs at the same height. */
private val NodeArea = 46.dp
private val MinStop = 64.dp

/**
 * One stop for each step, joined by a line, with the chapter name under it. Scrolls when the stops do not fit,
 * keeping the current one in the middle. Tapping a stop jumps to its step.
 */
@Composable
fun JourneyBar(titles: List<String>, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val side = TindiDims.space3
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val viewport = constraints.maxWidth
        val sidePx = with(density) { side.roundToPx() }
        val minPx = with(density) { MinStop.roundToPx() }
        val labelGap = with(density) { TindiDims.space2.roundToPx() }
        val widths = remember(titles, viewport, density) {
            val labels = titles.map { measurer.measure(it, TindiType.navLabel, maxLines = 1).size.width + labelGap }
            journeyStopWidths(labels, viewport - sidePx * 2, minPx)
        }
        val scroll = rememberScrollState()
        // The first placement jumps there; later step changes glide.
        var placed by remember { mutableStateOf(false) }
        LaunchedEffect(current, widths, viewport) {
            val target = journeyScrollFor(widths, current, viewport, sidePx)
            if (placed) scroll.animateScrollTo(target) else scroll.scrollTo(target)
            placed = true
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = side)
                .padding(bottom = TindiDims.space2),
        ) {
            titles.forEachIndexed { i, title ->
                Stop(
                    title = title,
                    index = i,
                    count = titles.size,
                    current = current,
                    onClick = { onSelect(i) },
                    modifier = Modifier.width(with(density) { widths[i].toDp() }),
                )
            }
        }
    }
}

@Composable
private fun Stop(title: String, index: Int, count: Int, current: Int, onClick: () -> Unit, modifier: Modifier) {
    val c = Tindi.colors
    val done = index < current
    val here = index == current
    val state = if (done) "done" else if (here) "current" else "not started"
    Column(
        modifier
            .clip(RoundedCornerShape(TindiDims.radiusSm))
            .selectable(selected = here, role = Role.Tab, onClick = onClick)
            // Read as one thing: "Step 2 of 5, Simmer, current".
            .clearAndSetSemantics {
                contentDescription = "Step ${index + 1} of $count, $title, $state"
                role = Role.Tab
                selected = here
                onClick { onClick(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(NodeArea), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val middle = Offset(size.width / 2, size.height / 2)
                val stroke = 3.dp.toPx()
                // Each stop draws its half of the line to either neighbour. Walked parts are accent.
                if (index > 0) {
                    drawLine(if (index <= current) c.accent else c.line, Offset(0f, middle.y), middle, stroke)
                }
                if (index < count - 1) {
                    drawLine(if (index < current) c.accent else c.line, middle, Offset(size.width, middle.y), stroke)
                }
                if (here) drawCircle(c.chip, 23.dp.toPx(), middle)
            }
            Box(
                Modifier
                    .size(if (here) 36.dp else 30.dp)
                    .background(if (done || here) c.accent else c.surface, CircleShape)
                    .border(
                        if (here) 3.dp else 2.dp,
                        if (here) c.mango else if (done) c.accent else c.line,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    TIconImage(TIcon.Check, c.onAccent, null, size = 16.dp)
                } else {
                    // Sized in dp so a large font setting cannot push the number out of its circle.
                    val size = with(LocalDensity.current) { 13.dp.toSp() }
                    Text(
                        "${index + 1}",
                        style = TindiType.caption.copy(
                            color = if (here) c.onAccent else c.muted,
                            fontWeight = TindiType.label.fontWeight,
                            fontSize = size,
                            lineHeight = size,
                        ),
                    )
                }
            }
        }
        Text(title, style = TindiType.navLabel.copy(color = if (here) c.ink else c.muted), maxLines = 1, softWrap = false)
    }
}
