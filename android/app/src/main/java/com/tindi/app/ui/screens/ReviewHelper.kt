package com.tindi.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tindi.app.data.VideoInfo
import com.tindi.app.ui.components.PlateIllustration
import com.tindi.app.ui.components.TIcon
import com.tindi.app.ui.components.TIconImage
import com.tindi.app.ui.components.TextAction
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val thumbCache = object : LruCache<String, Bitmap>(64) {}

/** Thumbnail from an https URL. Shows the plate illustration while loading and if it cannot be loaded. */
@Composable
fun RemoteThumbnail(url: String?, modifier: Modifier = Modifier, fallbackVariant: Int = 0) {
    val bitmap by produceState<Bitmap?>(initialValue = url?.let { thumbCache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val u = URL(url)
                require(u.protocol == "https")
                val conn = (u.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 8_000
                }
                try {
                    conn.inputStream.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }?.also { thumbCache.put(url, it) }
    }
    val b = bitmap
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        PlateIllustration(fallbackVariant, modifier)
    }
}

/** Details tab: the video the recipe comes from. */
@Composable
fun VideoSummaryCard(channel: String?, thumbnailUrl: String?, link: String?) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface, shape)
            .border(1.dp, c.line, shape),
    ) {
        RemoteThumbnail(thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(TindiDims.space3)) {
            if (!channel.isNullOrBlank()) Text(channel, style = TindiType.bodyLgBold.copy(color = c.ink), maxLines = 1)
            if (link != null) Text(link, style = TindiType.caption.copy(color = c.muted), maxLines = 1)
        }
    }
}

/** How the suggestion was made. Short, and it can be closed. */
@Composable
fun SuggestionBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tindi.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.chip, RoundedCornerShape(TindiDims.radiusInput))
            .padding(start = TindiDims.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TIconImage(TIcon.Alert, c.ink, null, size = 18.dp)
        Text(
            message,
            style = TindiType.caption.copy(color = c.ink),
            modifier = Modifier.weight(1f).padding(start = TindiDims.space2, top = TindiDims.space2, bottom = TindiDims.space2),
        )
        Box(
            Modifier
                .size(TindiDims.touchMin)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { TIconImage(TIcon.Close, c.ink, "Dismiss message", size = 20.dp) }
    }
}

/** Collapsed by default. Lists what the server asked the user to check. */
@Composable
fun CheckTheseRow(warnings: List<String>, open: Boolean, onToggle: () -> Unit) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusCard)
    Column(Modifier.fillMaxWidth().background(c.chip, shape)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = TindiDims.touchMin)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(horizontal = TindiDims.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TIconImage(TIcon.Warning, c.ink, null, size = 18.dp)
            Spacer(Modifier.size(TindiDims.space2))
            Text("Check these (${warnings.size})", style = TindiType.label.copy(color = c.ink), modifier = Modifier.weight(1f))
            Text(if (open) "Hide" else "Show", style = TindiType.caption.copy(color = c.muted))
            Spacer(Modifier.size(TindiDims.space2))
            TIconImage(if (open) TIcon.ChevronUp else TIcon.ChevronDown, c.ink, null)
        }
        if (open) {
            Column(
                Modifier.padding(start = TindiDims.space4, end = TindiDims.space4, bottom = TindiDims.space4),
                verticalArrangement = Arrangement.spacedBy(TindiDims.space2),
            ) {
                warnings.forEach { Text(it, style = TindiType.body.copy(color = c.ink)) }
            }
        }
    }
}

/**
 * Collapsible "From the video" section: the description and top comments, each with Copy and
 * "Use as ingredients / steps". Nothing here saves anything.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoHelperPanel(
    video: VideoInfo,
    open: Boolean,
    onToggle: () -> Unit,
    notice: String?,
    onUseIngredients: (String) -> Unit,
    onUseSteps: (String) -> Unit,
) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusCard)
    Column(Modifier.fillMaxWidth().background(c.chip, shape)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = TindiDims.touchMin)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(horizontal = TindiDims.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("From the video", style = TindiType.label.copy(color = c.ink), modifier = Modifier.weight(1f))
            Text(if (open) "Hide" else "Show", style = TindiType.caption.copy(color = c.muted))
            Spacer(Modifier.size(TindiDims.space2))
            TIconImage(if (open) TIcon.ChevronUp else TIcon.ChevronDown, c.ink, null)
        }
        if (open) {
            Column(
                Modifier.padding(start = TindiDims.space3, end = TindiDims.space3, bottom = TindiDims.space3),
                verticalArrangement = Arrangement.spacedBy(TindiDims.space3),
            ) {
                if (notice != null) Text(notice, style = TindiType.caption.copy(color = c.ink))
                HelperBlock("Description", video.description, onUseIngredients, onUseSteps)
                video.comments.forEach { cm ->
                    val likes = if (cm.likeCount > 0) " · ${cm.likeCount} likes" else ""
                    val label = (if (cm.isPinnedLikely) "Comment by the creator" else "Comment") +
                        (if (cm.author.isNotBlank()) " · ${cm.author}" else "") + likes
                    HelperBlock(label, cm.text, onUseIngredients, onUseSteps)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelperBlock(
    label: String,
    text: String,
    onUseIngredients: (String) -> Unit,
    onUseSteps: (String) -> Unit,
) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusInput)
    val clipboard = LocalClipboardManager.current
    var copied by remember(text) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.line, shape)
            .padding(TindiDims.space3),
    ) {
        Text(label, style = TindiType.label.copy(color = c.muted), maxLines = 2)
        Spacer(Modifier.height(TindiDims.space1))
        if (text.isBlank()) {
            Text("Nothing here.", style = TindiType.body.copy(color = c.muted))
        } else {
            // Long descriptions scroll inside their own box so the page stays easy to move through.
            Box(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(text, style = TindiType.body.copy(color = c.ink), modifier = Modifier.fillMaxWidth()) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TindiDims.space2)) {
                TextAction(if (copied) "Copied" else "Copy", {
                    clipboard.setText(AnnotatedString(text))
                    copied = true
                })
                TextAction("Use as ingredients", { onUseIngredients(text) })
                TextAction("Use as steps", { onUseSteps(text) })
            }
        }
    }
}
