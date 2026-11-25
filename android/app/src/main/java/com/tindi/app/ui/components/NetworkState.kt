package com.tindi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tindi.app.data.remote.ApiError
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiType

/** Full-area error with a retry, for a screen whose data could not be loaded. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val offline = message == ApiError.CANT_REACH
    StateBlock(
        variant = 2,
        title = if (offline) "Can't reach server" else "Something went wrong",
        message = if (offline) "Make sure your laptop is on and Tailscale is connected, then try again." else message,
        modifier = modifier,
    ) { PrimaryButton("Try again", onRetry) }
}

/** One red line with the alert icon, for form and save errors. */
@Composable
fun ErrorLine(text: String, modifier: Modifier = Modifier) {
    val c = Tindi.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TIconImage(TIcon.Alert, c.danger, null, size = 16.dp, modifier = Modifier.padding(top = 1.dp))
        Text(text, style = TindiType.caption.copy(color = c.danger))
    }
}
