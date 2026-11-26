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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.tindi.app.data.RecipeInput
import com.tindi.app.data.SourceType
import com.tindi.app.data.VideoInfo
import com.tindi.app.domain.RecipeTextSplitter
import com.tindi.app.domain.needsNote
import com.tindi.app.data.remote.ApiError
import com.tindi.app.data.remote.ApiResult
import com.tindi.app.ui.components.CategoryChip
import com.tindi.app.ui.components.CheckChip
import com.tindi.app.ui.components.ErrorLine
import com.tindi.app.ui.components.ErrorState
import com.tindi.app.ui.components.Heading
import com.tindi.app.ui.components.IconBtn
import com.tindi.app.ui.components.PrimaryButton
import com.tindi.app.ui.components.SecondaryButton
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TIconImage
import com.tindi.app.ui.components.Skeleton
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

data class ReviewUiState(
    /** False while an edited recipe is still being fetched. */
    val ready: Boolean = true,
    val loadError: String? = null,
    val busy: Boolean = false,
    val tab: Int = 0,
    val name: String = "",
    val categoryIds: Set<String> = emptySet(),
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val sourceType: SourceType = SourceType.Pasted,
    val sourceUrl: String? = null,
    val channel: String? = null,
    val thumbnailUrl: String? = null,
    /** Short confirmation after "Use as ingredients / steps". */
    val notice: String? = null,
    val qty: String = "",
    val item: String = "",
    val stepText: String = "",
    val nameError: String? = null,
    val saveError: String? = null,
)

/** Create mode (editId null) reads the draft from Add recipe. Edit mode starts from a saved recipe. */
class ReviewViewModel(private val container: AppContainer, private val editId: String?) : ViewModel() {
    val isEdit: Boolean = editId != null
    val draft: StateFlow<RecipeDraft?> = container.draft.draft

    /** The text the recipe was pasted from. Only exists when creating from pasted text. */
    val sourceText: String? = if (isEdit) null else container.draft.draft.value?.sourceText

    /** What the server found for a YouTube link. Drives the "From the video" panel. */
    val video: VideoInfo? = if (isEdit) null else container.draft.draft.value?.video

    var saved = false
        private set
    val categories: StateFlow<List<Category>> = container.categories.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(initial())
    val state: StateFlow<ReviewUiState> = _state

    private fun fromRecipe(r: Recipe) = ReviewUiState(
        name = r.name, categoryIds = r.categoryIds.toSet(), ingredients = r.ingredients, steps = r.steps,
        sourceType = r.sourceType, sourceUrl = r.sourceUrl, thumbnailUrl = r.thumbnailUrl,
    )

    private fun initial(): ReviewUiState {
        if (editId == null) {
            val d = container.draft.draft.value
            return ReviewUiState(
                name = d?.title.orEmpty(),
                ingredients = d?.ingredients.orEmpty(),
                steps = d?.steps.orEmpty(),
                sourceType = d?.sourceType ?: SourceType.Pasted,
                sourceUrl = d?.sourceUrl,
                channel = d?.video?.channel,
                thumbnailUrl = d?.video?.thumbnailUrl,
            )
        }
        val cached = container.recipes.recipes.value.firstOrNull { it.id == editId }
        return if (cached != null) fromRecipe(cached) else ReviewUiState(ready = false)
    }

    init {
        if (!_state.value.ready) load()
    }

    fun load() {
        val id = editId ?: return
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            when (val r = container.recipes.get(id)) {
                is ApiResult.Ok -> _state.value = fromRecipe(r.value)
                is ApiResult.Fail -> _state.update { it.copy(loadError = r.error.message) }
            }
        }
    }

    fun setTab(i: Int) = _state.update { it.copy(tab = i, notice = null) }

    /** Wizard step for new recipes: Details -> Ingredients -> Steps. */
    fun next() = _state.update {
        when {
            it.tab == 0 && it.name.isBlank() -> it.copy(nameError = "Give the recipe a name.")
            it.tab < 3 -> it.copy(tab = it.tab + 1, notice = null, saveError = null, nameError = null)
            else -> it
        }
    }

    fun previous() = _state.update { it.copy(tab = (it.tab - 1).coerceAtLeast(0), notice = null, saveError = null) }
    fun setName(v: String) = _state.update { it.copy(name = v, nameError = null, saveError = null) }
    fun toggleCategory(id: String, on: Boolean) =
        _state.update { it.copy(categoryIds = if (on) it.categoryIds + id else it.categoryIds - id) }

    /** Creates a category from the last step and ticks it, so the recipe saves straight into it. */
    suspend fun createCategoryAndSelect(name: String): String? {
        val error = container.categories.create(name)
        if (error == null) {
            val made = categories.value.lastOrNull { it.name.equals(name.trim(), ignoreCase = true) && !it.builtIn }
                ?: container.categories.categories.value.lastOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            if (made != null) _state.update { it.copy(categoryIds = it.categoryIds + made.id) }
        }
        return error
    }

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

    /** Replaces the ingredient list with the lines of [text] (its ingredients section if it has one). */
    fun useAsIngredients(text: String) {
        val lines = RecipeTextSplitter.asIngredients(text)
        _state.update {
            if (lines.isEmpty()) it.copy(notice = "Nothing to use in that text.")
            else it.copy(
                ingredients = lines.map(RecipeTextSplitter::parseIngredient), saveError = null,
                notice = "Ingredients filled with ${lines.size} lines. Check them in the Ingredients tab.",
            )
        }
    }

    /** Replaces the steps with the lines of [text] (its method section if it has one). */
    fun useAsSteps(text: String) {
        val lines = RecipeTextSplitter.asSteps(text)
        _state.update {
            if (lines.isEmpty()) it.copy(notice = "Nothing to use in that text.")
            else it.copy(
                steps = lines, saveError = null,
                notice = "Steps filled with ${lines.size} lines. Check them in the Steps tab.",
            )
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    /** Puts a server rejection where the user can see it: the name field, or the tab that holds the bad row. */
    private fun withServerError(s: ReviewUiState, e: ApiError): ReviewUiState {
        val name = e.fields["name"]
        val tab = when {
            name != null -> 0
            e.fields.keys.any { it.startsWith("ingredients") } -> 1
            e.fields.keys.any { it.startsWith("steps") } -> 2
            e.fields.keys.any { it.startsWith("categoryIds") } -> 3
            else -> s.tab
        }
        val line = if (e.code == "VALIDATION_FAILED") e.fields.values.firstOrNull() ?: e.message else e.message
        return s.copy(busy = false, tab = tab, nameError = name, saveError = line)
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.busy || !s.ready) return
        when {
            s.name.isBlank() -> _state.update {
                it.copy(tab = 0, nameError = "Give the recipe a name.", saveError = "Add a name before saving.")
            }
            s.steps.isEmpty() -> _state.update {
                it.copy(tab = 2, saveError = "Add at least one step.")
            }
            else -> {
                _state.update { it.copy(busy = true, saveError = null, nameError = null) }
                viewModelScope.launch {
                    val input = RecipeInput(
                        name = s.name.trim(),
                        ingredients = s.ingredients,
                        steps = s.steps,
                        categoryIds = s.categoryIds.toList(),
                        sourceType = s.sourceType,
                        sourceUrl = s.sourceUrl,
                        thumbnailUrl = s.thumbnailUrl,
                    )
                    val result = if (editId == null) container.recipes.create(input) else container.recipes.update(editId, input)
                    when (result) {
                        is ApiResult.Ok -> {
                            saved = true
                            if (editId == null) container.draft.set(null)
                            _state.update { it.copy(busy = false) }
                            onSaved()
                        }
                        is ApiResult.Fail -> _state.update { withServerError(it, result.error) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(container: AppContainer, editId: String? = null, onBack: () -> Unit, onSaved: () -> Unit) {
    val vm = appViewModel(key = "review-${editId ?: "new"}") { ReviewViewModel(container, editId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val c = Tindi.colors
    var helperOpen by rememberSaveable { mutableStateOf(vm.video?.needsNote == true) }
    var showNewCategory by remember { mutableStateOf(false) }

    // The draft only lives in memory. If it is gone (process death, logout), go back to Add recipe.
    if (!vm.isEdit) {
        LaunchedEffect(draft) { if (draft == null && !vm.saved) onBack() }
        if (draft == null) return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TindiDims.space5, vertical = TindiDims.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TindiDims.space3),
        ) {
            IconBtn(TIcon.ArrowLeft, "Back", { if (!vm.isEdit && s.ready && s.tab > 0) vm.previous() else onBack() })
            Heading(if (vm.isEdit) "Edit recipe" else "Review recipe", style = TindiType.titleMd)
        }
        if (!s.ready) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (s.loadError != null) ErrorState(s.loadError.orEmpty(), vm::load)
                else Column(Modifier.fillMaxWidth().padding(horizontal = TindiDims.space5)) {
                    Skeleton(Modifier.fillMaxWidth().height(56.dp), RoundedCornerShape(TindiDims.radiusInput))
                    Spacer(Modifier.height(TindiDims.space4))
                    Skeleton(Modifier.fillMaxWidth(0.6f).height(20.dp))
                }
            }
            return@Column
        }
        Box(Modifier.padding(horizontal = TindiDims.space5)) {
            TindiTabs(listOf("Details", "Ingredients", "Steps", "Category"), s.tab, vm::setTab)
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
                    if (vm.video != null) {
                        VideoSummaryCard(s.channel, s.thumbnailUrl, s.sourceUrl)
                        Spacer(Modifier.height(TindiDims.space5))
                        if (vm.video.needsNote) {
                            LowConfidenceNote()
                            Spacer(Modifier.height(TindiDims.space5))
                        }
                    }
                    TindiTextField(
                        label = "Recipe name", value = s.name, onValueChange = vm::setName, error = s.nameError,
                        placeholder = "For example, Paneer Butter Masala",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Spacer(Modifier.height(TindiDims.space5))
                    Text(
                        if (s.sourceUrl != null) "Source: YouTube link kept with the recipe." else "Source: pasted text.",
                        style = TindiType.caption.copy(color = c.muted),
                    )
                }
                1 -> {
                    SourceHelp(vm, s.notice, helperOpen, { helperOpen = !helperOpen }, "ingredients")
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
                2 -> {
                    SourceHelp(vm, s.notice, helperOpen, { helperOpen = !helperOpen }, "steps")
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
                else -> {
                    Text("Where should it go?", style = TindiType.titleSm.copy(color = c.ink))
                    Spacer(Modifier.height(TindiDims.space2))
                    Text(
                        "Pick one or more. Pick none to save it as Uncategorised.",
                        style = TindiType.caption.copy(color = c.muted),
                    )
                    Spacer(Modifier.height(TindiDims.space4))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(TindiDims.space2)) {
                        categories.forEach { cat ->
                            CheckChip(cat.name, cat.id in s.categoryIds, { vm.toggleCategory(cat.id, it) })
                        }
                        CategoryChip("New", false, { showNewCategory = true }, dashed = true, leadingIcon = TIcon.Plus)
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(c.bg)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Column(Modifier.padding(horizontal = TindiDims.space5, vertical = TindiDims.space3)) {
                if (s.saveError != null) {
                    ErrorLine(s.saveError.orEmpty())
                    Spacer(Modifier.height(TindiDims.space2))
                }
                if (!vm.isEdit && s.tab < 3) {
                    PrimaryButton(
                        when (s.tab) { 0 -> "Next: ingredients"; 1 -> "Next: steps"; else -> "Next: category" },
                        onClick = vm::next, icon = TIcon.ArrowRight,
                    )
                } else {
                    PrimaryButton(
                        if (s.busy) "Saving" else if (vm.isEdit) "Save changes" else "Save recipe",
                        onClick = { vm.save(onSaved) }, icon = TIcon.Check, enabled = !s.busy,
                    )
                }
            }
        }
    }

    if (showNewCategory) {
        NewCategorySheet(
            onDismiss = { showNewCategory = false },
            onCreate = { name -> vm.createCategoryAndSelect(name) },
        )
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

/** Under the Ingredients and Steps tabs: the video helper for YouTube recipes, the pasted text for pasted ones. */
@Composable
private fun SourceHelp(
    vm: ReviewViewModel,
    notice: String?,
    open: Boolean,
    onToggle: () -> Unit,
    forWhat: String,
) {
    val video = vm.video
    if (video != null) {
        if (video.needsNote) {
            LowConfidenceNote()
            Spacer(Modifier.height(TindiDims.space3))
        }
        VideoHelperPanel(video, open, onToggle, notice, vm::useAsIngredients, vm::useAsSteps)
    } else {
        vm.sourceText?.let { SourceTextCard(it, forWhat) }
    }
}
