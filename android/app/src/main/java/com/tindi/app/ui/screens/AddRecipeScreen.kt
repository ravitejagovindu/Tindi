package com.tindi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tindi.app.AppContainer
import com.tindi.app.data.FetchFailure
import com.tindi.app.data.FetchResult
import com.tindi.app.data.RecipeDraft
import com.tindi.app.data.SourceFetcher
import com.tindi.app.domain.draftFromPaste
import com.tindi.app.domain.draftFromVideo
import com.tindi.app.ui.components.Heading
import com.tindi.app.ui.components.IconBtn
import com.tindi.app.ui.components.PrimaryButton
import com.tindi.app.ui.components.SecondaryButton
import com.tindi.app.ui.components.Skeleton
import com.tindi.app.ui.components.StateBlock
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TindiTextField
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddPhase { Ready, Loading, Failed, NotYouTube }

data class AddUiState(
    val link: String = "",
    val linkError: String? = null,
    val phase: AddPhase = AddPhase.Ready,
    val failure: FetchFailure? = null,
    val pasteMode: Boolean = false,
    val pasted: String = "",
    val pastedError: String? = null,
)

class AddRecipeViewModel(
    private val fetcher: SourceFetcher,
    private val onDraft: (RecipeDraft) -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(AddUiState())
    val state: StateFlow<AddUiState> = _state
    private var job: Job? = null

    fun setLink(v: String) = _state.update { it.copy(link = v, linkError = null) }
    fun setPasted(v: String) = _state.update { it.copy(pasted = v, pastedError = null) }
    fun pasteInstead() = _state.update { it.copy(pasteMode = true, phase = AddPhase.Ready, failure = null) }
    fun backToLink() = _state.update { it.copy(pasteMode = false) }

    /** Asks the server for the video, then hands a prefilled draft to Review through [onDone]. */
    fun fetch(onDone: () -> Unit) {
        val s = _state.value
        if (s.phase == AddPhase.Loading) return
        val link = s.link.trim()
        if (link.isEmpty()) {
            _state.update { it.copy(linkError = "Paste a YouTube link.") }
            return
        }
        _state.update { it.copy(phase = AddPhase.Loading, linkError = null, failure = null) }
        job = viewModelScope.launch {
            when (val r = fetcher.fetch(link)) {
                is FetchResult.Success -> {
                    onDraft(draftFromVideo(r.source))
                    _state.update { it.copy(phase = AddPhase.Ready) } // so Back from Review finds the form again
                    onDone()
                }
                FetchResult.NotYouTube -> _state.update { it.copy(phase = AddPhase.NotYouTube) }
                is FetchResult.Failed -> _state.update { it.copy(phase = AddPhase.Failed, failure = r.reason) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(phase = AddPhase.Ready) }
    }

    /** No network: the pasted text is split on the phone. Returns true when a draft was handed to Review. */
    fun usePasted(): Boolean {
        val text = _state.value.pasted
        if (text.isBlank()) {
            _state.update { it.copy(pastedError = "Paste the recipe text first.") }
            return false
        }
        onDraft(draftFromPaste(text))
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
                Spacer(Modifier.height(TindiDims.space1))
                Text(
                    "Lines under an \"Ingredients\" heading and a \"Method\" or \"Steps\" heading are sorted for you. " +
                        "Without headings, everything goes into Steps.",
                    style = TindiType.caption.copy(color = c.muted),
                )
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
                    keyboardActions = KeyboardActions(onGo = { vm.fetch(onUseText) }),
                )
                Spacer(Modifier.height(TindiDims.space3))
                PrimaryButton(
                    if (s.phase == AddPhase.Loading) "Getting recipe" else "Get recipe",
                    { vm.fetch(onUseText) }, enabled = s.phase != AddPhase.Loading, icon = TIcon.Search,
                )
                Spacer(Modifier.height(TindiDims.space5))

                when (s.phase) {
                    AddPhase.Ready -> Unit
                    AddPhase.Loading -> {
                        LoadingBlock()
                        Spacer(Modifier.height(TindiDims.space4))
                        SecondaryButton("Cancel", vm::cancel, icon = TIcon.Close)
                    }
                    AddPhase.Failed -> {
                        val why = s.failure ?: FetchFailure.Other
                        StateBlock(
                            variant = 3,
                            title = if (why == FetchFailure.Network) "Can't reach server" else "Could not get that video",
                            message = if (why == FetchFailure.Network) {
                                "Check your connection and try again, or paste the recipe text instead."
                            } else why.message,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(TindiDims.space2)) {
                                if (why.canRetry) PrimaryButton("Try again", { vm.fetch(onUseText) }, icon = TIcon.Check)
                                if (why.canRetry) SecondaryButton("Paste text instead", vm::pasteInstead, icon = TIcon.Pen)
                                else PrimaryButton("Paste text instead", vm::pasteInstead, icon = TIcon.Pen)
                            }
                        }
                    }
                    AddPhase.NotYouTube -> StateBlock(
                        variant = 1,
                        title = "That is not a YouTube link",
                        message = "Tindi can only fetch YouTube videos. For Instagram and other sites, " +
                            "paste the recipe text instead.",
                    ) { PrimaryButton("Paste recipe text", vm::pasteInstead, icon = TIcon.Pen) }
                }
            }
            Spacer(Modifier.height(TindiDims.space6))
        }

        val showPasteLink = !s.pasteMode && (s.phase == AddPhase.Ready || s.phase == AddPhase.Loading)
        if (s.pasteMode || showPasteLink) {
            Column(Modifier.fillMaxWidth().background(c.bg)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Column(
                    Modifier.padding(horizontal = TindiDims.space5, vertical = TindiDims.space3),
                    verticalArrangement = Arrangement.spacedBy(TindiDims.space2),
                ) {
                    if (s.pasteMode) {
                        PrimaryButton("Use this text", onClick = { if (vm.usePasted()) onUseText() }, icon = TIcon.ArrowRight)
                        SecondaryButton("Use a YouTube link instead", vm::backToLink)
                    } else {
                        SecondaryButton("Paste text instead", vm::pasteInstead, icon = TIcon.Pen)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Getting the video" }) {
        Skeleton(Modifier.fillMaxWidth().height(TindiDims.thumbList + 24.dp), RoundedCornerShape(TindiDims.radiusCard))
        Spacer(Modifier.height(TindiDims.space4))
        repeat(4) {
            Skeleton(Modifier.fillMaxWidth(if (it == 3) 0.6f else 1f).height(16.dp))
            Spacer(Modifier.height(TindiDims.space2))
        }
    }
}
