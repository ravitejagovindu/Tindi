package com.tindi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tindi.app.AppContainer
import com.tindi.app.data.Categories
import com.tindi.app.data.Category
import com.tindi.app.data.Recipe
import com.tindi.app.domain.filterRecipes
import com.tindi.app.ui.components.CategoryChip
import com.tindi.app.ui.components.IconBtn
import com.tindi.app.ui.components.ExtendedFab
import com.tindi.app.ui.components.Heading
import com.tindi.app.ui.components.ErrorState
import com.tindi.app.ui.components.PlateIllustration
import com.tindi.app.ui.components.PrimaryButton
import com.tindi.app.ui.components.SecondaryButton
import com.tindi.app.ui.components.Skeleton
import com.tindi.app.ui.components.SourceBadge
import com.tindi.app.ui.components.StateBlock
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.ThemeToggle
import com.tindi.app.ui.components.TindiSearchBar
import com.tindi.app.ui.components.TindiSheet
import com.tindi.app.ui.components.TindiTextField
import com.tindi.app.ui.theme.LocalThemeControl
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

fun recipeVariant(id: String): Int = abs(id.hashCode()) % 4

data class LibraryUiState(
    val loaded: Boolean = false,
    /** Why the first load failed. Null while loading or once loaded. */
    val error: String? = null,
    val allRecipes: List<Recipe> = emptyList(),
    val categories: List<Category> = emptyList(),
    val hasUncategorised: Boolean = false,
    val selectedCategory: String? = null,
    val query: String = "",
    val visible: List<Recipe> = emptyList(),
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    val state: StateFlow<LibraryUiState> = combine(
        combine(
            container.recipes.loaded, container.categories.loaded,
            container.recipes.loadError, container.categories.loadError,
        ) { rl, cl, re, ce -> Pair(rl && cl, if (rl && cl) null else re ?: ce) },
        container.recipes.recipes, container.categories.categories, selected, query,
    ) { (loaded, error), recipes, categories, sel, q ->
        val hasUncat = recipes.any { it.categoryIds.isEmpty() }
        // A category that no longer exists (or an emptied Uncategorised) falls back to All.
        val validSel = when (sel) {
            null -> null
            Categories.UNCATEGORISED_ID -> sel.takeIf { hasUncat }
            else -> sel.takeIf { id -> categories.any { it.id == id } }
        }
        LibraryUiState(
            loaded = loaded, error = error, allRecipes = recipes, categories = categories, hasUncategorised = hasUncat,
            selectedCategory = validSel, query = q, visible = filterRecipes(recipes, validSel, q),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun reload() {
        viewModelScope.launch {
            launch { container.categories.refresh() }
            container.recipes.refresh()
        }
    }

    fun selectCategory(id: String?) { selected.value = id }
    fun setQuery(q: String) { query.value = q }
    fun clearFilters() { selected.value = null; query.value = "" }

    suspend fun createCategory(name: String): String? = container.categories.create(name)
}

@Composable
fun LibraryScreen(container: AppContainer, onOpenRecipe: (String) -> Unit, onAdd: () -> Unit, onOpenProfile: () -> Unit) {
    val vm = appViewModel { LibraryViewModel(container) }
    val s by vm.state.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }

    // Fetch again each time the tab is opened. Quiet when recipes are already on screen.
    LaunchedEffect(Unit) { vm.reload() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TindiDims.space5).padding(top = TindiDims.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Heading("Library", Modifier.weight(1f))
                IconBtn(TIcon.User, "Profile", onOpenProfile)
            }

            val firstRun = s.loaded && s.allRecipes.isEmpty()
            if (!firstRun && s.error == null) {
                Spacer(Modifier.height(TindiDims.space3))
                TindiSearchBar(
                    s.query, vm::setQuery,
                    Modifier.padding(horizontal = TindiDims.space5),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = TindiDims.space5),
                    horizontalArrangement = Arrangement.spacedBy(TindiDims.space2),
                    modifier = Modifier.padding(top = TindiDims.space1),
                ) {
                    item("all") {
                        CategoryChip("All", s.selectedCategory == null, { vm.selectCategory(null) })
                    }
                    items(s.categories, key = { it.id }) { cat ->
                        CategoryChip(cat.name, s.selectedCategory == cat.id, { vm.selectCategory(cat.id) })
                    }
                    if (s.hasUncategorised) {
                        item("uncat") {
                            CategoryChip(
                                Categories.UNCATEGORISED_NAME,
                                s.selectedCategory == Categories.UNCATEGORISED_ID,
                                { vm.selectCategory(Categories.UNCATEGORISED_ID) },
                            )
                        }
                    }
                    item("new") {
                        CategoryChip("New", false, { showNew = true }, dashed = true, leadingIcon = TIcon.Plus)
                    }
                }
            }

            when {
                s.error != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ErrorState(s.error.orEmpty(), vm::reload)
                }
                !s.loaded -> LoadingGrid()
                firstRun -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StateBlock(
                        variant = 0,
                        title = "Your recipe book is empty",
                        message = "Paste a YouTube cooking link and Tindi will help you save the recipe.",
                    ) { PrimaryButton("Add recipe", onAdd, icon = TIcon.Plus) }
                }
                s.visible.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val catName = s.categories.firstOrNull { it.id == s.selectedCategory }?.name
                        ?: if (s.selectedCategory == Categories.UNCATEGORISED_ID) Categories.UNCATEGORISED_NAME else null
                    StateBlock(
                        variant = 1,
                        title = "No matches",
                        message = when {
                            s.query.isNotBlank() && catName != null -> "Nothing in $catName matches \"${s.query.trim()}\"."
                            s.query.isNotBlank() -> "Nothing matches \"${s.query.trim()}\"."
                            else -> "You have no recipes in $catName yet."
                        },
                    ) { SecondaryButton("Clear filters", vm::clearFilters) }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = TindiDims.space5, end = TindiDims.space5,
                        top = TindiDims.space3, bottom = 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TindiDims.space4),
                    verticalArrangement = Arrangement.spacedBy(TindiDims.space4),
                    modifier = Modifier.weight(1f),
                ) {
                    items(s.visible, key = { it.id }) { r ->
                        RecipeCard(r, s.categories, onClick = { onOpenRecipe(r.id) })
                    }
                }
            }
        }

        if (s.loaded && s.allRecipes.isNotEmpty()) {
            ExtendedFab(
                "Add recipe", TIcon.Plus, onAdd,
                Modifier.align(Alignment.BottomEnd).padding(end = TindiDims.space5, bottom = TindiDims.space4),
            )
        }
    }

    if (showNew) {
        NewCategorySheet(
            onDismiss = { showNew = false },
            onCreate = { name -> vm.createCategory(name) },
        )
    }
}

@Composable
fun RecipeCard(recipe: Recipe, categories: List<Category>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusCard)
    val firstCat = recipe.categoryIds.firstNotNullOfOrNull { id -> categories.firstOrNull { it.id == id }?.name }
        ?: Categories.UNCATEGORISED_NAME
    val n = recipe.ingredients.size
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface, shape)
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.fillMaxWidth().height(TindiDims.thumbCard)) {
            PlateIllustration(recipeVariant(recipe.id), Modifier.fillMaxSize())
            SourceBadge(recipe.sourceType, Modifier.padding(TindiDims.space2))
        }
        Column(Modifier.padding(TindiDims.space3)) {
            Text(recipe.name, style = TindiType.bodyLgBold.copy(color = c.ink), maxLines = 2, minLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                "$firstCat · $n ingredient${if (n == 1) "" else "s"}",
                style = TindiType.caption.copy(color = c.muted), maxLines = 1,
            )
        }
    }
}

@Composable
private fun LoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = TindiDims.space5, vertical = TindiDims.space3),
        horizontalArrangement = Arrangement.spacedBy(TindiDims.space4),
        verticalArrangement = Arrangement.spacedBy(TindiDims.space4),
        userScrollEnabled = false,
        modifier = Modifier.semantics { contentDescription = "Loading recipes" },
    ) {
        items(6) {
            Column(Modifier.fillMaxWidth()) {
                Skeleton(Modifier.fillMaxWidth().height(TindiDims.thumbCard), RoundedCornerShape(TindiDims.radiusCard))
                Spacer(Modifier.height(TindiDims.space3))
                Skeleton(Modifier.fillMaxWidth(0.9f).height(16.dp))
                Spacer(Modifier.height(TindiDims.space2))
                Skeleton(Modifier.fillMaxWidth(0.5f).height(12.dp))
            }
        }
    }
}

@Composable
fun NewCategorySheet(onDismiss: () -> Unit, onCreate: suspend (String) -> String?) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    TindiSheet(onDismiss) {
        Heading("New category", style = TindiType.titleSm)
        Spacer(Modifier.height(TindiDims.space4))
        TindiTextField(
            label = "Category name", value = name,
            onValueChange = { name = it; error = null },
            placeholder = "For example, Tiffin box", error = error,
        )
        Spacer(Modifier.height(TindiDims.space5))
        PrimaryButton("Create category", onClick = {
            scope.launch {
                val e = onCreate(name)
                if (e == null) onDismiss() else error = e
            }
        })
    }
}
