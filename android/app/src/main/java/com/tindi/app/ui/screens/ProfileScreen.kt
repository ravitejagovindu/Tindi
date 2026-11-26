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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.tindi.app.ui.components.IconBtn
import com.tindi.app.ui.components.TIconImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tindi.app.AppContainer
import com.tindi.app.data.Profile
import com.tindi.app.data.Session
import com.tindi.app.ui.components.ErrorLine
import com.tindi.app.ui.components.Heading
import com.tindi.app.ui.components.LabeledSwitch
import com.tindi.app.ui.components.SecondaryButton
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TextAction
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

data class ProfileUiState(
    val session: Session? = null,
    /** From GET /me. Null until it has loaded. */
    val profile: Profile? = null,
    val error: String? = null,
    val loggingOut: Boolean = false,
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    private val loggingOut = MutableStateFlow(false)

    val state: StateFlow<ProfileUiState> = combine(
        container.auth.session, container.auth.profile, error, loggingOut,
    ) { session, profile, err, out ->
        ProfileUiState(session, profile, err, out)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun reload() {
        viewModelScope.launch { error.value = container.auth.refreshProfile()?.message }
    }

    fun logOut() {
        if (loggingOut.value) return
        loggingOut.value = true
        viewModelScope.launch {
            container.auth.logOut()
            loggingOut.value = false
        }
    }
}

@Composable
fun ProfileScreen(container: AppContainer, onBack: () -> Unit) {
    val vm = appViewModel { ProfileViewModel(container) }
    val s by vm.state.collectAsStateWithLifecycle()
    val theme = LocalThemeControl.current
    val c = Tindi.colors
    LaunchedEffect(Unit) { vm.reload() }
    var catsOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TindiDims.space5),
    ) {
        Spacer(Modifier.height(TindiDims.space4))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TindiDims.space3)) {
            IconBtn(TIcon.ArrowLeft, "Back", onBack)
            Heading("Profile")
        }
        Spacer(Modifier.height(TindiDims.space6))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(88.dp).background(c.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.session?.name?.trim()?.firstOrNull()?.uppercase() ?: "?",
                    style = TindiType.titleLg.copy(color = c.onAccent),
                )
            }
            Spacer(Modifier.height(TindiDims.space4))
            Text(s.session?.name.orEmpty(), style = TindiType.titleSm.copy(color = c.ink, textAlign = TextAlign.Center))
            Spacer(Modifier.height(4.dp))
            Text(s.session?.email.orEmpty(), style = TindiType.body.copy(color = c.muted))
        }
        Spacer(Modifier.height(TindiDims.space6))
        Row(horizontalArrangement = Arrangement.spacedBy(TindiDims.space3)) {
            CountCard("Recipes", s.profile?.recipeCount?.toString() ?: "-", Modifier.weight(1f))
            CountCard("Your categories", s.profile?.categoryCount?.toString() ?: "-", Modifier.weight(1f))
        }
        if (s.error != null && s.profile == null) {
            Spacer(Modifier.height(TindiDims.space3))
            ErrorLine(s.error.orEmpty())
            TextAction("Try again", vm::reload)
        }
        Spacer(Modifier.height(TindiDims.space6))
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.surface, RoundedCornerShape(TindiDims.radiusCard))
                .border(1.dp, c.line, RoundedCornerShape(TindiDims.radiusCard)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TindiDims.radiusCard))
                    .clickable(role = Role.Button, onClickLabel = if (catsOpen) "Collapse categories" else "Expand categories") { catsOpen = !catsOpen }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = TindiDims.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Categories", style = TindiType.bodyLgBold.copy(color = c.ink), modifier = Modifier.weight(1f))
                TIconImage(if (catsOpen) TIcon.ChevronUp else TIcon.ChevronDown, c.ink, null)
            }
            if (catsOpen) {
                Box(Modifier.padding(start = TindiDims.space4, end = TindiDims.space4, bottom = TindiDims.space4)) {
                    CategoriesSection(container)
                }
            }
        }
        Spacer(Modifier.height(TindiDims.space6))
        Box(
            Modifier
                .fillMaxWidth()
                .background(c.surface, RoundedCornerShape(TindiDims.radiusCard))
                .border(1.dp, c.line, RoundedCornerShape(TindiDims.radiusCard))
                .padding(horizontal = TindiDims.space4),
        ) {
            LabeledSwitch("Dark mode", theme.isDark, onCheckedChange = { theme.toggle() })
        }
        Spacer(Modifier.height(TindiDims.space6))
        SecondaryButton(if (s.loggingOut) "Logging out" else "Log out", onClick = vm::logOut, icon = TIcon.Logout, enabled = !s.loggingOut)
        Spacer(Modifier.height(TindiDims.space6))
    }
}

@Composable
private fun CountCard(label: String, count: String, modifier: Modifier) {
    val c = Tindi.colors
    Column(
        modifier
            .background(c.surface, RoundedCornerShape(TindiDims.radiusCard))
            .border(1.dp, c.line, RoundedCornerShape(TindiDims.radiusCard))
            .padding(TindiDims.space4)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(count, style = TindiType.titleMd.copy(color = c.ink))
        Text(label, style = TindiType.caption.copy(color = c.muted))
    }
}
