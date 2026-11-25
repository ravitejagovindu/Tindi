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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tindi.app.AppContainer
import com.tindi.app.data.Category
import com.tindi.app.ui.components.DangerButton
import com.tindi.app.ui.components.Heading
import com.tindi.app.ui.components.PlateIllustration
import com.tindi.app.ui.components.SecondaryButton
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TIconImage
import com.tindi.app.ui.components.TindiSheet
import com.tindi.app.ui.components.TindiTextField
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val starter: List<Category> = emptyList(),
    val own: List<Category> = emptyList(),
    /** Recipes per category id. */
    val counts: Map<String, Int> = emptyMap(),
    /** Recipes that would end up Uncategorised if this category went, per category id. */
    val onlyIn: Map<String, Int> = emptyMap(),
)

class CategoriesViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<CategoriesUiState> = combine(
        container.categories.categories, container.recipes.recipes,
    ) { cats, recipes ->
        CategoriesUiState(
            starter = cats.filter { it.builtIn },
            own = cats.filterNot { it.builtIn },
            counts = cats.associate { cat -> cat.id to recipes.count { cat.id in it.categoryIds } },
            onlyIn = cats.associate { cat -> cat.id to recipes.count { it.categoryIds == listOf(cat.id) } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    suspend fun create(name: String) = container.categories.create(name)
    suspend fun rename(id: String, name: String) = container.categories.rename(id, name)
    fun delete(id: String) {
        viewModelScope.launch { container.categories.delete(id) }
    }
}

@Composable
fun CategoriesScreen(container: AppContainer) {
    val vm = appViewModel { CategoriesViewModel(container) }
    val s by vm.state.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val c = Tindi.colors

    var newName by remember { mutableStateOf("") }
    var newError by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    fun create() {
        scope.launch {
            val e = vm.create(newName)
            if (e == null) newName = "" else newError = e
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TindiDims.space5, vertical = TindiDims.space4),
    ) {
        item {
            Heading("Categories")
            Spacer(Modifier.height(TindiDims.space5))
            Row(horizontalArrangement = Arrangement.spacedBy(TindiDims.space3)) {
                TindiTextField(
                    label = "New category", value = newName,
                    onValueChange = { newName = it; newError = null },
                    placeholder = "For example, Tiffin box", error = newError,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                )
                Box(Modifier.padding(top = 22.dp)) {
                    Box(
                        Modifier
                            .size(TindiDims.buttonH)
                            .clip(RoundedCornerShape(TindiDims.radiusInput))
                            .background(c.accent, RoundedCornerShape(TindiDims.radiusInput))
                            .clickable(role = Role.Button, onClick = ::create),
                        contentAlignment = Alignment.Center,
                    ) { TIconImage(TIcon.Plus, c.onAccent, "Create category") }
                }
            }
            Spacer(Modifier.height(TindiDims.space6))
            SectionLabel("Your categories")
            Spacer(Modifier.height(TindiDims.space2))
        }

        if (s.own.isEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(c.chip, RoundedCornerShape(TindiDims.radiusCard))
                        .padding(TindiDims.space4),
                    horizontalArrangement = Arrangement.spacedBy(TindiDims.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlateIllustration(3, Modifier.size(TindiDims.thumbList).clip(RoundedCornerShape(TindiDims.radiusSm)))
                    Column(Modifier.weight(1f)) {
                        Text("No categories of your own yet", style = TindiType.bodyLgBold.copy(color = c.ink))
                        Text(
                            "Add one above, like Tiffin box or Sunday special.",
                            style = TindiType.body.copy(color = c.muted),
                        )
                    }
                }
            }
        } else {
            items(s.own, key = { it.id }) { cat ->
                if (editingId == cat.id) {
                    EditRow(
                        value = editName, error = editError,
                        onValueChange = { editName = it; editError = null },
                        onSave = {
                            scope.launch {
                                val e = vm.rename(cat.id, editName)
                                if (e == null) editingId = null else editError = e
                            }
                        },
                        onCancel = { editingId = null },
                    )
                } else {
                    CategoryRow(
                        name = cat.name, caption = recipeCount(s.counts[cat.id] ?: 0),
                        onRename = { editingId = cat.id; editName = cat.name; editError = null },
                        onDelete = { deleting = cat },
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(TindiDims.space6))
            SectionLabel("Starter set")
            Text(
                "These come with Tindi and cannot be renamed or deleted.",
                style = TindiType.caption.copy(color = c.muted),
            )
            Spacer(Modifier.height(TindiDims.space2))
        }
        items(s.starter, key = { it.id }) { cat ->
            CategoryRow(name = cat.name, caption = recipeCount(s.counts[cat.id] ?: 0), onRename = null, onDelete = null)
        }
    }

    deleting?.let { cat ->
        val moving = s.onlyIn[cat.id] ?: 0
        TindiSheet(onDismiss = { deleting = null }) {
            Heading("Delete ${cat.name}?", style = TindiType.titleSm)
            Spacer(Modifier.height(TindiDims.space3))
            Text(
                if (moving > 0) {
                    "$moving ${if (moving == 1) "recipe" else "recipes"} will move to Uncategorised. " +
                        "Recipes are never deleted."
                } else {
                    "No recipe will be deleted. Recipes keep any other category they have."
                },
                style = TindiType.body.copy(color = c.muted),
            )
            Spacer(Modifier.height(TindiDims.space6))
            DangerButton("Delete category", onClick = { vm.delete(cat.id); deleting = null }, icon = TIcon.Trash)
            Spacer(Modifier.height(TindiDims.space3))
            SecondaryButton("Keep category", onClick = { deleting = null })
        }
    }
}

private fun recipeCount(n: Int) = "$n ${if (n == 1) "recipe" else "recipes"}"

@Composable
private fun SectionLabel(text: String) {
    Heading(text, style = TindiType.label, modifier = Modifier.padding(bottom = 2.dp))
}

@Composable
private fun CategoryRow(name: String, caption: String, onRename: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusInput)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = TindiDims.space2)
            .background(c.surface, shape)
            .border(1.dp, c.line, shape)
            .padding(start = TindiDims.space4)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = TindiDims.space2)) {
            Text(name, style = TindiType.bodyLgBold.copy(color = c.ink))
            Text(caption, style = TindiType.caption.copy(color = c.muted))
        }
        if (onRename != null) RowAction(TIcon.Edit, "Rename $name", onRename)
        if (onDelete != null) RowAction(TIcon.Trash, "Delete $name", onDelete)
        if (onRename == null) {
            Text(
                "Built in",
                style = TindiType.caption.copy(color = c.muted),
                modifier = Modifier.padding(end = TindiDims.space4),
            )
        }
    }
}

@Composable
private fun RowAction(icon: TIcon, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(TindiDims.touchMin).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { TIconImage(icon, Tindi.colors.ink, label) }
}

@Composable
private fun EditRow(
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = TindiDims.space2)) {
        Row(verticalAlignment = Alignment.Top) {
            TindiTextField(
                label = "Rename category", value = value, onValueChange = onValueChange, error = error,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
            )
            Row(Modifier.padding(top = 18.dp)) {
                RowAction(TIcon.Check, "Save name", onSave)
                RowAction(TIcon.Close, "Cancel rename", onCancel)
            }
        }
    }
}
