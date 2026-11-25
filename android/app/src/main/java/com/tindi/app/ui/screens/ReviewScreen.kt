package com.tindi.app.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tindi.app.AppContainer
import com.tindi.app.data.Category
import com.tindi.app.data.Ingredient
import com.tindi.app.data.Recipe
import com.tindi.app.data.RecipeDraft
import com.tindi.app.ui.components.CheckChip
import com.tindi.app.ui.components.Heading
import com.tindi.app.ui.components.IconBtn
import com.tindi.app.ui.components.PrimaryButton
import com.tindi.app.ui.components.SecondaryButton
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TIconImage
import com.tindi.app.ui.components.TindiTabs
import com.tindi.app.ui.components.TindiTextField
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ReviewUiState(
    val tab: Int = 0,
    val name: String = "",
    val categoryIds: Set<String> = emptySet(),
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val qty: String = "",
    val item: String = "",
    val stepText: String = "",
    val nameError: String? = null,
    val saveError: String? = null,
)

class ReviewViewModel(private val container: AppContainer) : ViewModel() {
    val draft: StateFlow<RecipeDraft?> = container.draft.draft
    var saved = false
        private set
    val categories: StateFlow<List<Category>> = container.categories.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(ReviewUiState(name = container.draft.draft.value?.suggestedName.orEmpty()))
    val state: StateFlow<ReviewUiState> = _state

    fun setTab(i: Int) = _state.update { it.copy(tab = i) }
    fun setName(v: String) = _state.update { it.copy(name = v, nameError = null, saveError = null) }
    fun toggleCategory(id: String, on: Boolean) =
        _state.update { it.copy(categoryIds = if (on) it.categoryIds + id else it.categoryIds - id) }

    fun setQty(v: String) = _state.update { it.copy(qty = v) }
    fun setItem(v: String) = _state.update { it.copy(item = v) }
    fun setStepText(v: String) = _state.update { it.copy(stepText = v) }

    fun addIngredient() = _state.update {
        if (it.item.isBlank()) it
        else it.copy(
            ingredients = it.ingredients + Ingredient(it.qty.trim(), it.item.trim()),
            qty = "", item = "", saveError = null,
        )
    }

    fun removeIngredient(i: Int) = _state.update { it.copy(ingredients = it.ingredients.filterIndexed { n, _ -> n != i }) }

    fun addStep() = _state.update {
        if (it.stepText.isBlank()) it
        else it.copy(steps = it.steps + it.stepText.trim(), stepText = "", saveError = null)
    }

    fun removeStep(i: Int) = _state.update { it.copy(steps = it.steps.filterIndexed { n, _ -> n != i }) }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        val d = container.draft.draft.value ?: return
        when {
            s.name.isBlank() -> _state.update {
                it.copy(tab = 0, nameError = "Give the recipe a name.", saveError = "Add a name before saving.")
            }
            s.ingredients.isEmpty() -> _state.update {
                it.copy(tab = 1, saveError = "Add at least one ingredient.")
            }
            s.steps.isEmpty() -> _state.update {
                it.copy(tab = 2, saveError = "Add at least one step.")
            }
            else -> viewModelScope.launch {
                container.recipes.add(
                    Recipe(
                        id = UUID.randomUUID().toString(),
                        name = s.name.trim(),
                        ingredients = s.ingredients,
                        steps = s.steps,
                        categoryIds = s.categoryIds.toList(),
                        sourceType = d.sourceType,
                        sourceUrl = d.sourceUrl,
                    ),
                )
                saved = true
                container.draft.set(null)
                onSaved()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(container: AppContainer, onBack: () -> Unit, onSaved: () -> Unit) {
    val vm = appViewModel { ReviewViewModel(container) }
    val s by vm.state.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val c = Tindi.colors

    // The draft only lives in memory. If it is gone (process death), go back to Add recipe.
    LaunchedEffect(draft) { if (draft == null && !vm.saved) onBack() }
    val d = draft ?: return

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TindiDims.space5, vertical = TindiDims.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TindiDims.space3),
        ) {
            IconBtn(TIcon.ArrowLeft, "Back", onBack)
            Heading("Review recipe", style = TindiType.titleMd)
        }
        Box(Modifier.padding(horizontal = TindiDims.space5)) {
            TindiTabs(listOf("Details", "Ingredients", "Steps"), s.tab, vm::setTab)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TindiDims.space5)
                .padding(top = TindiDims.space4, bottom = TindiDims.space6),
        ) {
            when (s.tab) {
                0 -> {
                    TindiTextField(
                        label = "Recipe name", value = s.name, onValueChange = vm::setName, error = s.nameError,
                        placeholder = "For example, Paneer Butter Masala",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Spacer(Modifier.height(TindiDims.space5))
                    Text("Categories", style = TindiType.caption.copy(color = c.muted))
                    Spacer(Modifier.height(TindiDims.space1))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(TindiDims.space2)) {
                        categories.forEach { cat ->
                            CheckChip(cat.name, cat.id in s.categoryIds, { vm.toggleCategory(cat.id, it) })
                        }
                    }
                    Text(
                        "Pick none to save it as Uncategorised.",
                        style = TindiType.caption.copy(color = c.muted),
                    )
                    Spacer(Modifier.height(TindiDims.space5))
                    Text(
                        if (d.sourceUrl != null) "Source: YouTube link kept with the recipe." else "Source: pasted text.",
                        style = TindiType.caption.copy(color = c.muted),
                    )
                }
                1 -> {
                    SourceTextCard(d.sourceText, "ingredients")
                    Spacer(Modifier.height(TindiDims.space4))
                    s.ingredients.forEachIndexed { i, ing ->
                        ListRow(ing.display, "Remove ingredient ${ing.display}") { vm.removeIngredient(i) }
                    }
                    if (s.ingredients.isNotEmpty()) Spacer(Modifier.height(TindiDims.space3))
                    Row(horizontalArrangement = Arrangement.spacedBy(TindiDims.space2)) {
                        TindiTextField(
                            label = "Quantity", value = s.qty, onValueChange = vm::setQty,
                            placeholder = "250 g", modifier = Modifier.weight(0.4f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        TindiTextField(
                            label = "Ingredient", value = s.item, onValueChange = vm::setItem,
                            placeholder = "paneer, cubed", modifier = Modifier.weight(0.6f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                    }
                    Spacer(Modifier.height(TindiDims.space3))
                    SecondaryButton("Add ingredient", vm::addIngredient, icon = TIcon.Plus, enabled = s.item.isNotBlank())
                }
                else -> {
                    SourceTextCard(d.sourceText, "steps")
                    Spacer(Modifier.height(TindiDims.space4))
                    s.steps.forEachIndexed { i, step ->
                        ListRow("${i + 1}. $step", "Remove step ${i + 1}") { vm.removeStep(i) }
                    }
                    if (s.steps.isNotEmpty()) Spacer(Modifier.height(TindiDims.space3))
                    TindiTextField(
                        label = "Next step", value = s.stepText, onValueChange = vm::setStepText,
                        singleLine = false, minHeight = 96.dp, placeholder = "For example, Melt the butter in a pan.",
                    )
                    Spacer(Modifier.height(TindiDims.space3))
                    SecondaryButton("Add step", vm::addStep, icon = TIcon.Plus, enabled = s.stepText.isNotBlank())
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(c.bg)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Column(Modifier.padding(horizontal = TindiDims.space5, vertical = TindiDims.space3)) {
                if (s.saveError != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TIconImage(TIcon.Alert, c.danger, null, size = 16.dp, modifier = Modifier.padding(top = 1.dp))
                        Text(s.saveError.orEmpty(), style = TindiType.caption.copy(color = c.danger))
                    }
                    Spacer(Modifier.height(TindiDims.space2))
                }
                PrimaryButton("Save recipe", onClick = { vm.save(onSaved) }, icon = TIcon.Check)
            }
        }
    }
}

@Composable
private fun ListRow(text: String, removeLabel: String, onRemove: () -> Unit) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusInput)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = TindiDims.space2)
            .background(c.surface, shape)
            .border(1.dp, c.line, shape)
            .padding(start = TindiDims.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = TindiType.body.copy(color = c.ink), modifier = Modifier.weight(1f).padding(vertical = TindiDims.space3))
        Box(
            Modifier
                .size(TindiDims.touchMin)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { TIconImage(TIcon.Close, c.muted, removeLabel) }
    }
}

/** Collapsed by default. Tap to expand. The text is selectable so the user can copy lines out of it. */
@Composable
private fun SourceTextCard(text: String, forWhat: String) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusCard)
    var open by rememberSaveable(forWhat) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.chip, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = TindiDims.touchMin)
                .clip(shape)
                .clickable(role = Role.Button, onClick = { open = !open })
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(horizontal = TindiDims.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Source text", style = TindiType.label.copy(color = c.ink), modifier = Modifier.weight(1f))
            Text(
                if (open) "Hide" else "Show to copy $forWhat",
                style = TindiType.caption.copy(color = c.muted),
            )
            Spacer(Modifier.size(TindiDims.space2))
            TIconImage(if (open) TIcon.ChevronUp else TIcon.ChevronDown, c.ink, null)
        }
        if (open) {
            SelectionContainer {
                Text(
                    text,
                    style = TindiType.body.copy(color = c.ink),
                    modifier = Modifier.fillMaxWidth().padding(start = TindiDims.space4, end = TindiDims.space4, bottom = TindiDims.space4),
                )
            }
        }
    }
}
