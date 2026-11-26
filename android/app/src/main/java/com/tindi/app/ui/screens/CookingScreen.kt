package com.tindi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tindi.app.AppContainer
import com.tindi.app.domain.CookIngredient
import com.tindi.app.domain.CookSession
import com.tindi.app.domain.CookStep
import com.tindi.app.domain.cheerFor
import com.tindi.app.domain.cookSteps
import com.tindi.app.domain.mainButtonLabel
import com.tindi.app.ui.components.CookIllustration
import com.tindi.app.ui.components.ErrorState
import com.tindi.app.ui.components.IconBtn
import com.tindi.app.ui.components.PrimaryButton
import com.tindi.app.ui.components.SecondaryButton
import com.tindi.app.ui.components.Skeleton
import com.tindi.app.ui.components.StateBlock
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TIconImage
import com.tindi.app.ui.components.ThemeToggle
import com.tindi.app.ui.components.rememberReducedMotion
import com.tindi.app.ui.theme.LocalThemeControl
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Holds one cooking session. It goes away with the screen, so the ticks are never saved. */
class CookingViewModel : ViewModel() {
    private val _session = MutableStateFlow(CookSession())
    val session: StateFlow<CookSession> = _session

    fun next(total: Int) = _session.update { it.next(total) }
    fun previous(total: Int) = _session.update { it.previous(total) }
    fun jump(to: Int, total: Int) = _session.update { it.jump(to, total) }
    fun toggle(step: Int, ingredient: Int) = _session.update { it.toggle(step, ingredient) }
    fun again() { _session.value = CookSession() }
}

/** What the cooking view can ask for. */
class CookActions(
    val close: () -> Unit,
    val backToLibrary: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val jump: (Int) -> Unit,
    val toggle: (step: Int, ingredient: Int) -> Unit,
    val again: () -> Unit,
)

@Composable
fun CookingScreen(
    container: AppContainer,
    id: String,
    onClose: () -> Unit,
    onBackToLibrary: () -> Unit,
) {
    val vm = appViewModel(key = "recipe-$id") { RecipeViewModel(container, id) }
    val cooking = appViewModel(key = "cooking-$id") { CookingViewModel() }
    val s by vm.state.collectAsStateWithLifecycle()
    val session by cooking.session.collectAsStateWithLifecycle()

    KeepScreenOn()

    val recipe = s.recipe
    if (recipe == null) {
        CookingUnavailable(loaded = s.loaded, error = s.error, onRetry = vm::reload, onClose = onClose)
        return
    }
    val steps = remember(recipe) { cookSteps(recipe) }
    val total = steps.size
    CookingContent(
        recipeName = recipe.name,
        steps = steps,
        session = session,
        reducedMotion = rememberReducedMotion(),
        actions = CookActions(
            close = onClose,
            backToLibrary = onBackToLibrary,
            next = { cooking.next(total) },
            previous = { cooking.previous(total) },
            jump = { cooking.jump(it, total) },
            toggle = cooking::toggle,
            again = cooking::again,
        ),
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** The cooking view for a loaded recipe: empty, one step, or finished. */
@Composable
fun CookingContent(
    recipeName: String,
    steps: List<CookStep>,
    session: CookSession,
    reducedMotion: Boolean,
    actions: CookActions,
) {
    when {
        steps.isEmpty() -> CookingMessage(actions.close) {
            StateBlock(
                variant = 1,
                title = "No steps yet",
                message = "Add steps to this recipe, then start cooking.",
            ) { PrimaryButton("Back", actions.close, icon = TIcon.ArrowLeft) }
        }
        session.finished -> CookingFinished(recipeName, steps.size, reducedMotion, actions.backToLibrary, actions.again)
        else -> CookingStep(steps, session, reducedMotion, actions)
    }
}

/** While the recipe is loading, and when it cannot be shown. */
@Composable
private fun CookingUnavailable(loaded: Boolean, error: String?, onRetry: () -> Unit, onClose: () -> Unit) {
    CookingMessage(onClose) {
        when {
            error != null -> ErrorState(error, onRetry)
            !loaded -> Column(Modifier.fillMaxWidth().padding(horizontal = TindiDims.space5)) {
                Skeleton(Modifier.fillMaxWidth().height(StageHeight), RoundedCornerShape(TindiDims.radiusSheet))
                Spacer(Modifier.height(TindiDims.space4))
                Skeleton(Modifier.fillMaxWidth(0.9f).height(28.dp))
                Spacer(Modifier.height(TindiDims.space3))
                Skeleton(Modifier.fillMaxWidth(0.6f).height(28.dp))
            }
            else -> StateBlock(
                variant = 1,
                title = "Recipe not found",
                message = "It may have been deleted on another device.",
            ) { SecondaryButton("Back", onClose) }
        }
    }
}

@Composable
private fun CookingMessage(onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = TindiDims.space5, vertical = TindiDims.space3)) {
            IconBtn(TIcon.Close, "Close cooking view", onClose)
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

private val StageHeight = 168.dp
private val Gap = 14.dp

@Composable
private fun CookingStep(steps: List<CookStep>, session: CookSession, reducedMotion: Boolean, actions: CookActions) {
    val c = Tindi.colors
    val total = steps.size
    val index = session.index(total)
    val step = steps[index]
    val allTicked = session.allTicked(index, step.rows)

    Column(Modifier.fillMaxSize()) {
        CookHeader(step.title, index, total, actions.close)
        JourneyBar(steps.map { it.title }, index, actions.jump)

        // Each step starts at the top.
        val scroll = key(index) { rememberScrollState() }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = TindiDims.space5)
                .padding(bottom = TindiDims.space3),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            StageCard(step, index, cheerFor(index, total, allTicked), reducedMotion)
            Text(step.text, style = TindiType.cookStep.copy(color = c.ink, fontSize = 26.sp, lineHeight = 32.sp))
            if (step.rows.isNotEmpty()) NeedPanel(step, index, session, actions.toggle)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TindiDims.space5)
                .padding(top = TindiDims.space3, bottom = TindiDims.space4),
            horizontalArrangement = Arrangement.spacedBy(TindiDims.space3),
        ) {
            PreviousButton(enabled = index > 0, onClick = actions.previous)
            PrimaryButton(
                mainButtonLabel(index, total),
                onClick = actions.next,
                modifier = Modifier.weight(1f).then(if (allTicked) Modifier.glow(inner = c.chip, outer = c.mango) else Modifier),
            )
        }
    }
}

/** Close, the chapter name over "Step N of M", and the theme toggle. */
@Composable
private fun CookHeader(title: String, index: Int, total: Int, onClose: () -> Unit) {
    val c = Tindi.colors
    val theme = LocalThemeControl.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TindiDims.space5)
            .padding(top = TindiDims.space3, bottom = TindiDims.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TindiDims.space3),
    ) {
        IconBtn(TIcon.Close, "Close cooking view", onClose)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = TindiType.titleSm.copy(color = c.ink, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text("Step ${index + 1} of $total", style = TindiType.caption.copy(color = c.muted))
        }
        ThemeToggle(theme.isDark, theme.toggle)
    }
}

/** The picture on its tile, with the cheer bubble and the step number. */
@Composable
private fun StageCard(step: CookStep, index: Int, cheer: String, reducedMotion: Boolean) {
    val c = Tindi.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(StageHeight)
            .clip(RoundedCornerShape(TindiDims.radiusSheet))
            .background(c.tiles[index % c.tiles.size]),
    ) {
        CookIllustration(step.illustration, Modifier.fillMaxSize().bob(!reducedMotion))
        val tail = TindiDims.space1
        val round = TindiDims.radiusSm
        Text(
            cheer,
            style = TindiType.caption.copy(color = c.ink, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(TindiDims.space3)
                .widthIn(max = 170.dp)
                .background(c.surface, RoundedCornerShape(topStart = round, topEnd = round, bottomEnd = round, bottomStart = tail))
                .padding(horizontal = TindiDims.space3, vertical = TindiDims.space2)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = TindiDims.space3, bottom = 10.dp)
                .rotate(-6f)
                .defaultMinSize(minWidth = 52.dp, minHeight = 52.dp)
                .background(c.accent, RoundedCornerShape(TindiDims.radiusFab))
                .padding(horizontal = TindiDims.space1),
            contentAlignment = Alignment.Center,
        ) {
            // Sized in dp: the badge is a fixed shape, and the header already says the number in scalable text.
            val size = with(LocalDensity.current) { 24.dp.toSp() }
            Text("${index + 1}", style = TindiType.titleMd.copy(color = c.onAccent, fontSize = size, lineHeight = size))
        }
    }
}

/** "Tap as you add": a tickable row for each ingredient of the step. */
@Composable
private fun NeedPanel(step: CookStep, index: Int, session: CookSession, onToggle: (Int, Int) -> Unit) {
    val c = Tindi.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.chip, RoundedCornerShape(TindiDims.radiusCard))
            .padding(Gap),
        verticalArrangement = Arrangement.spacedBy(TindiDims.space2),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TAP AS YOU ADD",
                style = TindiType.caption.copy(color = c.muted, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(
                "${session.tickedCount(index, step.rows)} of ${step.rows.size}",
                style = TindiType.caption.copy(color = c.ink, fontWeight = FontWeight.Bold),
            )
        }
        step.ingredients.forEach { row ->
            NeedRow(row, session.isTicked(index, row.index)) { onToggle(index, row.index) }
        }
        if (step.alsoNeeded.isNotEmpty()) {
            Text(
                "Also needed",
                style = TindiType.label.copy(color = c.muted),
                modifier = Modifier.padding(top = TindiDims.space1).semantics { heading() },
            )
            step.alsoNeeded.forEach { row ->
                NeedRow(row, session.isTicked(index, row.index)) { onToggle(index, row.index) }
            }
        }
    }
}

@Composable
private fun NeedRow(row: CookIngredient, ticked: Boolean, onToggle: () -> Unit) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusInput)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TindiDims.touchMin)
            .clip(shape)
            .background(if (ticked) c.tiles[1] else c.surface, shape)
            .border(1.5.dp, if (ticked) c.accent else c.line, shape)
            .toggleable(value = ticked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = TindiDims.space2, end = Gap, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TindiDims.space3),
    ) {
        Box(
            Modifier
                .size(TindiDims.space8)
                .then(
                    if (ticked) Modifier.background(c.accent, CircleShape)
                    else Modifier.border(2.dp, c.muted, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) { if (ticked) TIconImage(TIcon.Check, c.onAccent, null, size = 18.dp) }
        if (row.quantity.isNotEmpty()) {
            Text(
                row.quantity,
                style = TindiType.bodyLgBold.copy(color = c.ink),
                modifier = Modifier.widthIn(max = 128.dp),
            )
        }
        Text(
            row.name,
            style = TindiType.bodyLg.copy(
                color = c.ink,
                textDecoration = if (ticked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PreviousButton(enabled: Boolean, onClick: () -> Unit) {
    val c = Tindi.colors
    Box(
        Modifier
            .size(TindiDims.buttonH)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(c.chip, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { TIconImage(TIcon.ChevronLeft, c.ink, "Previous step") }
}

/** Two rings around the pill: the artboard's glow once everything on the step is in. */
private fun Modifier.glow(inner: Color, outer: Color) = drawBehind {
    fun ring(color: Color, spread: Float) = drawRoundRect(
        color,
        topLeft = Offset(-spread, -spread),
        size = Size(size.width + spread * 2, size.height + spread * 2),
        cornerRadius = CornerRadius(size.height / 2 + spread),
    )
    ring(outer, 7.dp.toPx())
    ring(inner, 4.dp.toPx())
}
