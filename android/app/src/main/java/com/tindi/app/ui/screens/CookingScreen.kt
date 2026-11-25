package com.tindi.app.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tindi.app.AppContainer
import com.tindi.app.domain.ingredientsForStep
import com.tindi.app.ui.components.IconBtn
import com.tindi.app.ui.components.PrimaryButton
import com.tindi.app.ui.components.SecondaryButton
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.ThemeToggle
import com.tindi.app.ui.theme.LocalThemeControl
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CookingScreen(
    container: AppContainer,
    id: String,
    onClose: () -> Unit,
    onFinish: () -> Unit,
) {
    val vm = appViewModel(key = "recipe-$id") { RecipeViewModel(container, id) }
    val s by vm.state.collectAsStateWithLifecycle()
    val theme = LocalThemeControl.current
    val c = Tindi.colors
    val recipe = s.recipe ?: return
    var step by rememberSaveable { mutableIntStateOf(0) }
    val total = recipe.steps.size.coerceAtLeast(1)
    val index = step.coerceIn(0, total - 1)

    // Keep the screen on while cooking.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val text = recipe.steps.getOrElse(index) { "" }
    val need = ingredientsForStep(text, recipe.ingredients)
    val last = index == total - 1

    Column(Modifier.fillMaxSize().padding(horizontal = TindiDims.space5)) {
        Row(
            Modifier.fillMaxWidth().padding(top = TindiDims.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(TIcon.Close, "Close cooking view", onClose)
            Text(
                "Step ${index + 1} of $total",
                style = TindiType.titleSm.copy(color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            ThemeToggle(theme.isDark, theme.toggle)
        }

        Spacer(Modifier.height(TindiDims.space4))
        Row(
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Progress: step ${index + 1} of $total" },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(total) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(if (i <= index) c.accent else c.line, RoundedCornerShape(percent = 50)),
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = TindiDims.space6),
        ) {
            Box(
                Modifier.size(56.dp).background(c.accent, RoundedCornerShape(TindiDims.radiusInput)),
                contentAlignment = Alignment.Center,
            ) { Text("${index + 1}", style = TindiType.titleMd.copy(color = c.onAccent)) }
            Spacer(Modifier.height(TindiDims.space5))
            Text(text, style = TindiType.cookStep.copy(color = c.ink))

            if (need.isNotEmpty()) {
                Spacer(Modifier.height(TindiDims.space6))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(c.chip, RoundedCornerShape(TindiDims.radiusCard))
                        .padding(TindiDims.space4),
                ) {
                    Text("You need", style = TindiType.label.copy(color = c.muted))
                    Spacer(Modifier.height(TindiDims.space2))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(TindiDims.space2),
                        verticalArrangement = Arrangement.spacedBy(TindiDims.space2),
                    ) {
                        need.forEach { ing ->
                            Text(
                                ing.display,
                                style = TindiType.body.copy(color = c.ink),
                                modifier = Modifier
                                    .background(c.surface, RoundedCornerShape(TindiDims.radiusInput))
                                    .border(1.dp, c.line, RoundedCornerShape(TindiDims.radiusInput))
                                    .padding(horizontal = TindiDims.space3, vertical = TindiDims.space2),
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = TindiDims.space3),
            horizontalArrangement = Arrangement.spacedBy(TindiDims.space3),
        ) {
            SecondaryButton(
                "Back",
                onClick = { if (index == 0) onClose() else step = index - 1 },
                modifier = Modifier.weight(1f),
                icon = TIcon.ArrowLeft,
            )
            PrimaryButton(
                if (last) "Finish" else "Next",
                onClick = { if (last) onFinish() else step = index + 1 },
                modifier = Modifier.weight(1f),
                icon = if (last) TIcon.Check else TIcon.ArrowRight,
            )
        }
    }
}
