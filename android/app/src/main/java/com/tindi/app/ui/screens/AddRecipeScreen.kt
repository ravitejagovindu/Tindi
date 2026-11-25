package com.tindi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tindi.app.AppContainer
import com.tindi.app.data.FetchResult
import com.tindi.app.data.RecipeDraft
import com.tindi.app.data.SourceFetcher
import com.tindi.app.data.SourceType
import com.tindi.app.data.VideoSource
import com.tindi.app.ui.components.Heading
import com.tindi.app.ui.components.IconBtn
import com.tindi.app.ui.components.PlateIllustration
import com.tindi.app.ui.components.PrimaryButton
import com.tindi.app.ui.components.SecondaryButton
import com.tindi.app.ui.components.Skeleton
import com.tindi.app.ui.components.StateBlock
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TindiTabs
import com.tindi.app.ui.components.TindiTextField
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddPhase { Ready, Loading, Loaded, Error }

data class AddUiState(
    val link: String = "",
    val linkError: String? = null,
    val phase: AddPhase = AddPhase.Ready,
    val source: VideoSource? = null,
    val tab: Int = 0,
    val pasteMode: Boolean = false,
    val pasted: String = "",
    val pastedError: String? = null,
) {
    /** The text the user would carry into Review. */
    val selectedText: String
        get() = if (pasteMode) pasted else when (tab) {
            0 -> source?.description.orEmpty()
            1 -> source?.pinnedComment.orEmpty()
            else -> source?.topComments?.joinToString("\n\n").orEmpty()
        }
}

class AddRecipeViewModel(
    private val fetcher: SourceFetcher,
    private val onDraft: (RecipeDraft) -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(AddUiState())
    val state: StateFlow<AddUiState> = _state
    private var job: Job? = null

    fun setLink(v: String) = _state.update { it.copy(link = v, linkError = null) }
    fun setTab(i: Int) = _state.update { it.copy(tab = i) }
    fun setPasted(v: String) = _state.update { it.copy(pasted = v, pastedError = null) }
    fun pasteInstead() = _state.update { it.copy(pasteMode = true) }
    fun backToLink() = _state.update { it.copy(pasteMode = false) }

    fun fetch() {
        val link = _state.value.link.trim()
        if (link.isEmpty()) {
            _state.update { it.copy(linkError = "Paste a YouTube link.") }
            return
        }
        job?.cancel()
        _state.update { it.copy(phase = AddPhase.Loading, linkError = null) }
        job = viewModelScope.launch {
            when (val r = fetcher.fetch(link)) {
                is FetchResult.Success -> _state.update { it.copy(phase = AddPhase.Loaded, source = r.source, tab = 0) }
                FetchResult.NotYouTube -> _state.update {
                    it.copy(phase = AddPhase.Ready, linkError = "That is not a YouTube link. Check it and try again.")
                }
                FetchResult.Failed -> _state.update { it.copy(phase = AddPhase.Error) }
            }
        }
    }

    /** Returns true when a draft was handed to Review. */
    fun useText(): Boolean {
        val s = _state.value
        if (s.pasteMode) {
            if (s.pasted.isBlank()) {
                _state.update { it.copy(pastedError = "Paste the recipe text first.") }
                return false
            }
            onDraft(RecipeDraft(s.pasted.trim(), SourceType.Pasted, null))
        } else {
            val src = s.source ?: return false
            onDraft(RecipeDraft(s.selectedText, SourceType.YouTube, src.url, src.title))
        }
        return true
    }
}

@Composable
fun AddRecipeScreen(container: AppContainer, onBack: () -> Unit, onUseText: () -> Unit) {
    val vm = appViewModel { AddRecipeViewModel(container.sourceFetcher) { container.draft.set(it) } }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = Tindi.colors

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TindiDims.space5, vertical = TindiDims.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TindiDims.space3),
        ) {
            IconBtn(TIcon.ArrowLeft, "Back", onBack)
            Heading("Add recipe", style = TindiType.titleMd)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TindiDims.space5),
        ) {
            if (s.pasteMode) {
                Text("Paste the recipe text from anywhere.", style = TindiType.body.copy(color = c.muted))
                Spacer(Modifier.height(TindiDims.space4))
                TindiTextField(
                    label = "Recipe text", value = s.pasted, onValueChange = vm::setPasted,
                    singleLine = false, minHeight = 220.dp, error = s.pastedError,
                    placeholder = "Ingredients, steps, anything you want to split up next.",
                )
            } else {
                Text("Paste a YouTube cooking link.", style = TindiType.body.copy(color = c.muted))
                Spacer(Modifier.height(TindiDims.space3))
                TindiTextField(
                    label = "YouTube link", value = s.link, onValueChange = vm::setLink,
                    error = s.linkError, placeholder = "https://www.youtube.com/watch?v=",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.fetch() }),
                )
                Spacer(Modifier.height(TindiDims.space3))
                SecondaryButton(
                    "Get recipe text", vm::fetch, enabled = s.phase != AddPhase.Loading,
                    icon = TIcon.Search,
                )
                Spacer(Modifier.height(TindiDims.space5))

                when (s.phase) {
                    AddPhase.Ready -> Unit
                    AddPhase.Loading -> LoadingBlock()
                    AddPhase.Error -> StateBlock(
                        variant = 3,
                        title = "Could not read that video",
                        message = "Something went wrong while getting the text. Check the link and try again.",
                    ) { PrimaryButton("Try again", vm::fetch, icon = TIcon.Check) }
                    AddPhase.Loaded -> s.source?.let { src ->
                        VideoCard(src)
                        Spacer(Modifier.height(TindiDims.space4))
                        TindiTabs(listOf("Description", "Pinned", "Comments"), s.tab, vm::setTab)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                        Spacer(Modifier.height(TindiDims.space4))
                        SourceText(s.selectedText, empty = "Nothing here.")
                        if (s.tab == 0) {
                            Spacer(Modifier.height(TindiDims.space3))
                            Text(
                                "Tip: many creators put the full recipe in the Pinned tab.",
                                style = TindiType.caption.copy(color = c.muted),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(TindiDims.space6))
        }

        val showUse = s.pasteMode || s.phase == AddPhase.Loaded
        Column(Modifier.fillMaxWidth().background(c.bg)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Column(
                Modifier.padding(horizontal = TindiDims.space5, vertical = TindiDims.space3),
                verticalArrangement = Arrangement.spacedBy(TindiDims.space2),
            ) {
                if (showUse) {
                    PrimaryButton("Use this text", onClick = { if (vm.useText()) onUseText() }, icon = TIcon.ArrowRight)
                }
                if (s.pasteMode) SecondaryButton("Use a YouTube link instead", vm::backToLink)
                else SecondaryButton("Paste text instead", vm::pasteInstead, icon = TIcon.Pen)
            }
        }
    }
}

@Composable
private fun VideoCard(src: VideoSource) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusCard)
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.line, shape)
            .padding(TindiDims.space3),
        horizontalArrangement = Arrangement.spacedBy(TindiDims.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlateIllustration(0, Modifier.size(TindiDims.thumbList).clip(RoundedCornerShape(TindiDims.radiusSm)))
        Column(Modifier.weight(1f)) {
            Text(src.title, style = TindiType.bodyLgBold.copy(color = c.ink), maxLines = 2)
            Text(src.channel, style = TindiType.caption.copy(color = c.muted), maxLines = 1)
        }
    }
}

@Composable
private fun SourceText(text: String, empty: String) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusCard)
    SelectionContainer {
        Text(
            text.ifBlank { empty },
            style = TindiType.body.copy(color = c.ink),
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface, shape)
                .border(1.dp, c.line, shape)
                .padding(TindiDims.space4),
        )
    }
}

@Composable
private fun LoadingBlock() {
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Loading video" }) {
        Skeleton(Modifier.fillMaxWidth().height(TindiDims.thumbList + 24.dp), RoundedCornerShape(TindiDims.radiusCard))
        Spacer(Modifier.height(TindiDims.space4))
        Skeleton(Modifier.fillMaxWidth().height(40.dp))
        Spacer(Modifier.height(TindiDims.space4))
        repeat(4) {
            Skeleton(Modifier.fillMaxWidth(if (it == 3) 0.6f else 1f).height(16.dp))
            Spacer(Modifier.height(TindiDims.space2))
        }
    }
}
