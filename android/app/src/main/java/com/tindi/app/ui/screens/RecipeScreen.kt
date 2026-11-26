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
import androidx.compose.runtime.mutableStateOf
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
import com.tindi.app.data.remote.ApiResult
import com.tindi.app.data.Recipe
import com.tindi.app.data.SourceType
import com.tindi.app.ui.components.DangerButton
import com.tindi.app.ui.components.ErrorLine
import com.tindi.app.ui.components.ErrorState
import com.tindi.app.ui.components.Heading
import com.tindi.app.ui.components.IconBtn
import com.tindi.app.ui.components.PlateIllustration
import com.tindi.app.ui.components.PrimaryButton
import com.tindi.app.ui.components.SecondaryButton
import com.tindi.app.ui.components.Skeleton
import com.tindi.app.ui.components.SourceBadge
import com.tindi.app.ui.components.StateBlock
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TIconImage
import com.tindi.app.ui.components.TindiTabs
import com.tindi.app.ui.components.TindiSheet
import com.tindi.app.domain.thumbnail
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RecipeUiState(
    val loaded: Boolean = false,
    val error: String? = null,
    val recipe: Recipe? = null,
    val categoryNames: List<String> = emptyList(),
    val deleting: Boolean = false,
    val deleteError: String? = null,
    /** Set once the server confirmed the delete, so the screen does not flash "not found" while closing. */
    val deleted: Boolean = false,
)

class RecipeViewModel(private val container: AppContainer, private val id: String) : ViewModel() {
    private data class Deletion(val busy: Boolean = false, val error: String? = null, val done: Boolean = false)

    private val deletion = MutableStateFlow(Deletion())

    val state: StateFlow<RecipeUiState> = combine(
        container.recipes.loaded, container.recipes.loadError, container.recipes.recipes,
        container.categories.categories, deletion,
    ) { loaded, error, recipes, categories, del ->
        val r = recipes.firstOrNull { it.id == id }
        val names = r?.categoryIds?.mapNotNull { cid -> categories.firstOrNull { it.id == cid }?.name }.orEmpty()
        RecipeUiState(
            loaded = loaded, error = error, recipe = r,
            categoryNames = names.ifEmpty { listOf(Categories.UNCATEGORISED_NAME) },
            deleting = del.busy, deleteError = del.error, deleted = del.done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeUiState())

    init { reload() }

    /** First open loads everything. Later opens just refresh this one recipe. */
    fun reload() {
        viewModelScope.launch {
            if (container.recipes.loaded.first()) container.recipes.get(id) else {
                launch { container.categories.refresh() }
                container.recipes.refresh()
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (deletion.value.busy) return
        deletion.value = Deletion(busy = true)
        viewModelScope.launch {
            when (val r = container.recipes.delete(id)) {
                is ApiResult.Ok -> { deletion.value = Deletion(done = true); onDeleted() }
                is ApiResult.Fail ->
                    if (r.error.code == "NOT_FOUND") { deletion.value = Deletion(done = true); onDeleted() }
                    else deletion.value = Deletion(error = r.error.message)
            }
        }
    }

    fun clearDeleteError() { deletion.value = Deletion() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeScreen(
    container: AppContainer,
    id: String,
    onBack: () -> Unit,
    onStartCooking: () -> Unit,
    onEdit: () -> Unit,
) {
    val vm = appViewModel(key = "recipe-$id") { RecipeViewModel(container, id) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = Tindi.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val recipe = s.recipe
    if (recipe == null) {
        if (s.deleted) return
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = TindiDims.space5, vertical = TindiDims.space3)) {
                IconBtn(TIcon.ArrowLeft, "Back", onBack)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    s.error != null -> ErrorState(s.error.orEmpty(), vm::reload)
                    !s.loaded -> Column(Modifier.fillMaxWidth().padding(horizontal = TindiDims.space5)) {
                        Skeleton(Modifier.fillMaxWidth().height(TindiDims.heroH), RoundedCornerShape(TindiDims.radiusCard))
                        Spacer(Modifier.height(TindiDims.space4))
                        Skeleton(Modifier.fillMaxWidth(0.7f).height(24.dp))
                        Spacer(Modifier.height(TindiDims.space3))
                        Skeleton(Modifier.fillMaxWidth(0.4f).height(16.dp))
                    }
                    else -> StateBlock(
                        variant = 1,
                        title = "Recipe not found",
                        message = "It may have been deleted on another device.",
                    ) { SecondaryButton("Back to library", onBack) }
                }
            }
        }
        return
    }

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
                RemoteThumbnail(recipe.thumbnail, Modifier.fillMaxSize(), recipeVariant(recipe.id))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TindiDims.space5, vertical = TindiDims.space3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconBtn(TIcon.ArrowLeft, "Back", onBack)
                    Row(horizontalArrangement = Arrangement.spacedBy(TindiDims.space2)) {
                        IconBtn(TIcon.Edit, "Edit recipe", onEdit)
                        IconBtn(TIcon.Trash, "Delete recipe", { vm.clearDeleteError(); confirmDelete = true })
                    }
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

    if (confirmDelete) {
        TindiSheet(onDismiss = { if (!s.deleting) confirmDelete = false }) {
            Heading("Delete ${recipe.name}?", style = TindiType.titleSm)
            Spacer(Modifier.height(TindiDims.space3))
            Text(
                "This removes the recipe from your library. This cannot be undone.",
                style = TindiType.body.copy(color = c.muted),
            )
            if (s.deleteError != null) {
                Spacer(Modifier.height(TindiDims.space3))
                ErrorLine(s.deleteError.orEmpty())
            }
            Spacer(Modifier.height(TindiDims.space6))
            DangerButton(
                if (s.deleting) "Deleting" else "Delete recipe",
                onClick = { vm.delete { confirmDelete = false; onBack() } },
                icon = TIcon.Trash,
                enabled = !s.deleting,
            )
            Spacer(Modifier.height(TindiDims.space3))
            SecondaryButton("Keep recipe", onClick = { confirmDelete = false }, enabled = !s.deleting)
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
