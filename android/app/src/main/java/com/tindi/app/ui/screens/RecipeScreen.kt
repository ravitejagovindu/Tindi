package com.tindi.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tindi.app.AppContainer
import com.tindi.app.data.Categories
import com.tindi.app.data.Recipe
import com.tindi.app.data.SourceType
import com.tindi.app.ui.components.Heading
import com.tindi.app.ui.components.IconBtn
import com.tindi.app.ui.components.PlateIllustration
import com.tindi.app.ui.components.PrimaryButton
import com.tindi.app.ui.components.SourceBadge
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TIconImage
import com.tindi.app.ui.components.ThemeToggle
import com.tindi.app.ui.components.TindiTabs
import com.tindi.app.ui.theme.LocalThemeControl
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RecipeUiState(
    val loaded: Boolean = false,
    val recipe: Recipe? = null,
    val categoryNames: List<String> = emptyList(),
)

class RecipeViewModel(container: AppContainer, id: String) : ViewModel() {
    val state: StateFlow<RecipeUiState> = combine(
        container.recipes.loaded, container.recipes.recipes, container.categories.categories,
    ) { loaded, recipes, categories ->
        val r = recipes.firstOrNull { it.id == id }
        val names = r?.categoryIds?.mapNotNull { cid -> categories.firstOrNull { it.id == cid }?.name }.orEmpty()
        RecipeUiState(loaded, r, names.ifEmpty { listOf(Categories.UNCATEGORISED_NAME) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeUiState())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeScreen(container: AppContainer, id: String, onBack: () -> Unit, onStartCooking: () -> Unit) {
    val vm = appViewModel(key = "recipe-$id") { RecipeViewModel(container, id) }
    val s by vm.state.collectAsStateWithLifecycle()
    val theme = LocalThemeControl.current
    val c = Tindi.colors
    val recipe = s.recipe ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TindiDims.heroH)
                    .clip(RoundedCornerShape(bottomStart = TindiDims.radiusCard, bottomEnd = TindiDims.radiusCard)),
            ) {
                PlateIllustration(recipeVariant(recipe.id), Modifier.fillMaxSize())
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TindiDims.space5, vertical = TindiDims.space3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconBtn(TIcon.ArrowLeft, "Back", onBack)
                    ThemeToggle(theme.isDark, theme.toggle)
                }
                SourceBadge(
                    recipe.sourceType,
                    Modifier.align(Alignment.BottomStart).padding(start = TindiDims.space5, bottom = TindiDims.space4),
                )
            }

            Column(Modifier.padding(horizontal = TindiDims.space5)) {
                Spacer(Modifier.height(TindiDims.space5))
                Heading(recipe.name)
                Spacer(Modifier.height(TindiDims.space3))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TindiDims.space2),
                    verticalArrangement = Arrangement.spacedBy(TindiDims.space2),
                ) {
                    s.categoryNames.forEach { name ->
                        Text(
                            name,
                            style = TindiType.label.copy(color = c.ink),
                            modifier = Modifier
                                .background(c.chip, RoundedCornerShape(percent = 50))
                                .padding(horizontal = TindiDims.space3, vertical = 6.dp),
                        )
                    }
                }

                if (recipe.sourceType == SourceType.YouTube && recipe.sourceUrl != null) {
                    Spacer(Modifier.height(TindiDims.space5))
                    WatchRow(onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(recipe.sourceUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (_: ActivityNotFoundException) {
                        }
                    })
                }

                Spacer(Modifier.height(TindiDims.space5))
                TindiTabs(listOf("Ingredients", "Steps"), tab, { tab = it })
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Spacer(Modifier.height(TindiDims.space3))

                if (tab == 0) {
                    recipe.ingredients.forEach { ing ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = TindiDims.touchMin).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(c.mango, CircleShape))
                            Spacer(Modifier.width(TindiDims.space3))
                            Text(ing.display, style = TindiType.bodyLg.copy(color = c.ink))
                        }
                    }
                } else {
                    recipe.steps.forEachIndexed { i, step ->
                        Row(Modifier.fillMaxWidth().padding(vertical = TindiDims.space2)) {
                            Box(
                                Modifier.size(32.dp).background(c.chip, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", style = TindiType.label.copy(color = c.ink)) }
                            Spacer(Modifier.width(TindiDims.space3))
                            Text(step, style = TindiType.bodyLg.copy(color = c.ink), modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(TindiDims.space6))
            }
        }

        Column(Modifier.fillMaxWidth().background(c.bg)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Box(Modifier.padding(horizontal = TindiDims.space5, vertical = TindiDims.space3)) {
                PrimaryButton("Start cooking", onStartCooking, icon = TIcon.Play)
            }
        }
    }
}

@Composable
private fun WatchRow(onClick: () -> Unit) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusInput)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TindiDims.buttonH)
            .clip(shape)
            .background(c.surface, shape)
            .border(1.5.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TindiDims.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TindiDims.space3),
    ) {
        TIconImage(TIcon.Play, c.ink, null)
        Text("Watch original video", style = TindiType.bodyLgBold.copy(color = c.ink), modifier = Modifier.weight(1f))
        TIconImage(TIcon.External, c.muted, "Opens outside the app")
    }
}
