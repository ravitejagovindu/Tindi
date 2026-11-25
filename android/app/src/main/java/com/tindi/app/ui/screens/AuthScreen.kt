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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tindi.app.AppContainer
import com.tindi.app.data.AuthErrors
import com.tindi.app.data.AuthRepository
import com.tindi.app.data.AuthResult
import com.tindi.app.ui.components.PrimaryButton
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TIconImage
import com.tindi.app.ui.components.TindiTextField
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val signUp: Boolean = false,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val errors: AuthErrors = AuthErrors(),
    val busy: Boolean = false,
)

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun setMode(signUp: Boolean) = _state.update { it.copy(signUp = signUp, errors = AuthErrors()) }
    fun setName(v: String) = _state.update { it.copy(name = v, errors = it.errors.copy(name = null, form = null)) }
    fun setEmail(v: String) = _state.update { it.copy(email = v, errors = it.errors.copy(email = null, form = null)) }
    fun setPassword(v: String) = _state.update { it.copy(password = v, errors = it.errors.copy(password = null, form = null)) }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = if (s.signUp) auth.signUp(s.name, s.email, s.password) else auth.logIn(s.email, s.password)
            _state.update {
                it.copy(busy = false, errors = (result as? AuthResult.Failure)?.errors ?: AuthErrors())
            }
        }
    }
}

@Composable
fun AuthScreen(container: AppContainer) {
    val vm = appViewModel { AuthViewModel(container.auth) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = Tindi.colors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TindiDims.space5),
    ) {
        Spacer(Modifier.height(TindiDims.space8))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Tindi", style = TindiType.wordmark.copy(color = c.ink))
            Box(Modifier.padding(start = 4.dp, bottom = 8.dp).size(12.dp).background(c.mango, CircleShape))
        }
        Spacer(Modifier.height(TindiDims.space2))
        Text(
            "Turn cooking videos into your own recipe book.",
            style = TindiType.body.copy(color = c.muted),
        )
        Spacer(Modifier.height(TindiDims.space6))
        ModeToggle(signUp = s.signUp, onChange = vm::setMode)
        Spacer(Modifier.height(TindiDims.space5))

        if (s.signUp) {
            TindiTextField(
                label = "Name", value = s.name, onValueChange = vm::setName, error = s.errors.name,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(TindiDims.space4))
        }
        TindiTextField(
            label = "Email", value = s.email, onValueChange = vm::setEmail, error = s.errors.email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(TindiDims.space4))
        TindiTextField(
            label = "Password", value = s.password, onValueChange = vm::setPassword, error = s.errors.password,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submit() }),
        )
        if (s.errors.form != null) {
            Spacer(Modifier.height(TindiDims.space3))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TIconImage(TIcon.Alert, c.danger, null, size = 16.dp, modifier = Modifier.padding(top = 1.dp))
                Text(s.errors.form.orEmpty(), style = TindiType.caption.copy(color = c.danger))
            }
        }
        Spacer(Modifier.height(TindiDims.space6))
        PrimaryButton(
            text = if (s.busy) "Please wait" else if (s.signUp) "Create account" else "Log in",
            onClick = vm::submit,
            enabled = !s.busy,
        )
        Spacer(Modifier.height(TindiDims.space6))
    }
}

@Composable
private fun ModeToggle(signUp: Boolean, onChange: (Boolean) -> Unit) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(c.chip, shape)
            .padding(4.dp),
    ) {
        listOf("Log in" to false, "Sign up" to true).forEach { (label, isSignUp) ->
            val sel = signUp == isSignUp
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(if (sel) c.accent else androidx.compose.ui.graphics.Color.Transparent, shape)
                    .selectable(selected = sel, role = Role.Tab, onClick = { onChange(isSignUp) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = TindiType.bodyLgBold.copy(
                        color = if (sel) c.onAccent else c.ink, textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}
