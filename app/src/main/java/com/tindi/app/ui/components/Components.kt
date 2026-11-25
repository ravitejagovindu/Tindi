package com.tindi.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tindi.app.data.SourceType
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.TindiDims
import com.tindi.app.ui.theme.TindiType
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = TindiType.titleLg) {
    Text(text, style = style.copy(color = Tindi.colors.ink), modifier = modifier.semantics { heading() })
}

// ---------- Buttons ----------

@Composable
private fun BaseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: TIcon?,
    container: Color,
    content: Color,
    border: BorderStroke?,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .heightIn(min = TindiDims.buttonH)
            .height(TindiDims.buttonH)
            .clip(shape)
            .background(if (enabled) container else container.copy(alpha = 0.5f), shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            TIconImage(icon, content, null)
            Spacer(Modifier.width(TindiDims.space3))
        }
        Text(text, style = TindiType.bodyLgBold.copy(color = content), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: TIcon? = null,
) = BaseButton(
    text, onClick, modifier.fillMaxWidth(), enabled, icon,
    Tindi.colors.accent, Tindi.colors.onAccent, null,
)

@Composable
fun SecondaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: TIcon? = null,
) = BaseButton(
    text, onClick, modifier.fillMaxWidth(), enabled, icon,
    Color.Transparent, Tindi.colors.ink, BorderStroke(1.5.dp, Tindi.colors.line),
)

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: TIcon? = null) =
    BaseButton(
        text, onClick, modifier.fillMaxWidth(), true, icon,
        Tindi.colors.danger, Tindi.colors.onDanger, null,
    )

/** 48 by 48 circle, chip fill, ink icon. */
@Composable
fun IconBtn(icon: TIcon, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(TindiDims.touchMin)
            .clip(CircleShape)
            .background(Tindi.colors.chip, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TIconImage(icon, Tindi.colors.ink, contentDescription)
    }
}

@Composable
fun ThemeToggle(isDark: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconBtn(
        icon = if (isDark) TIcon.Sun else TIcon.Moon,
        contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
        onClick = onToggle,
        modifier = modifier,
    )
}

/** A text row with a 48dp hit area. Used for small actions like "Clear filters". */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: TIcon? = null) {
    Row(
        modifier = modifier
            .heightIn(min = TindiDims.touchMin)
            .clip(RoundedCornerShape(TindiDims.radiusSm))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TindiDims.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TindiDims.space2),
    ) {
        if (icon != null) TIconImage(icon, Tindi.colors.ink, null)
        Text(text, style = TindiType.label.copy(color = Tindi.colors.ink))
    }
}

// ---------- Fields ----------

@Composable
fun TindiTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    singleLine: Boolean = true,
    minHeight: Dp = TindiDims.buttonH,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var focused by remember { mutableStateOf(false) }
    val c = Tindi.colors
    val borderColor = when {
        error != null -> c.danger
        focused -> c.accent
        else -> c.line
    }
    Column(modifier) {
        Text(label, style = TindiType.caption.copy(color = c.muted))
        Spacer(Modifier.height(TindiDims.space1))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TindiType.bodyLg.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .background(c.surface, RoundedCornerShape(TindiDims.radiusInput))
                        .border(1.5.dp, borderColor, RoundedCornerShape(TindiDims.radiusInput))
                        .padding(horizontal = TindiDims.space4, vertical = 14.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = TindiType.bodyLg.copy(color = c.muted))
                    }
                    inner()
                }
            },
        )
        if (error != null) {
            Spacer(Modifier.height(TindiDims.space1))
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TIconImage(TIcon.Alert, c.danger, null, size = 16.dp, modifier = Modifier.padding(top = 1.dp))
                Text(error, style = TindiType.caption.copy(color = c.danger))
            }
        }
    }
}

@Composable
fun TindiSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search recipes or ingredients",
) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(percent = 50)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TindiType.bodyLg.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(TindiDims.buttonH)
                    .background(c.surface, shape)
                    .border(1.5.dp, c.line, shape)
                    .padding(horizontal = TindiDims.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TindiDims.space3),
            ) {
                TIconImage(TIcon.Search, c.muted, null)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = TindiType.bodyLg.copy(color = c.muted), maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(TindiDims.touchMin)
                            .offset(x = 12.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) { TIconImage(TIcon.Close, c.muted, "Clear search", size = 20.dp) }
                }
            }
        },
    )
}

// ---------- Chips and tabs ----------

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp) = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
    )
}

/** 44dp chip inside a 48dp hit area. Selected state is exposed and also shown with a check icon. */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dashed: Boolean = false,
    leadingIcon: TIcon? = null,
) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .heightIn(min = TindiDims.touchMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(TindiDims.chipH)
                .clip(shape)
                .background(if (selected) c.accent else c.surface, shape)
                .then(
                    when {
                        selected -> Modifier
                        dashed -> Modifier.dashedBorder(c.muted, 22.dp)
                        else -> Modifier.border(1.5.dp, c.line, shape)
                    },
                )
                .padding(horizontal = TindiDims.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val fg = if (selected) c.onAccent else c.ink
            if (selected) TIconImage(TIcon.Check, fg, null, size = 18.dp)
            else if (leadingIcon != null) TIconImage(leadingIcon, fg, null, size = 18.dp)
            Text(label, style = TindiType.label.copy(color = fg), maxLines = 1)
        }
    }
}

/** Multi-select variant (Review > Details). */
@Composable
fun CheckChip(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .heightIn(min = TindiDims.touchMin)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(TindiDims.chipH)
                .clip(shape)
                .background(if (checked) c.accent else c.surface, shape)
                .then(if (checked) Modifier else Modifier.border(1.5.dp, c.line, shape))
                .padding(horizontal = TindiDims.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val fg = if (checked) c.onAccent else c.ink
            if (checked) TIconImage(TIcon.Check, fg, null, size = 18.dp)
            Text(label, style = TindiType.label.copy(color = fg), maxLines = 1)
        }
    }
}

@Composable
fun TindiTabs(titles: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Tindi.colors
    Row(modifier.fillMaxWidth().background(Color.Transparent)) {
        titles.forEachIndexed { i, title ->
            val sel = i == selectedIndex
            Column(
                Modifier
                    .weight(1f)
                    .height(TindiDims.touchMin)
                    .selectable(selected = sel, role = Role.Tab, onClick = { onSelect(i) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(title, style = TindiType.label.copy(color = if (sel) c.ink else c.muted), maxLines = 1)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (sel) c.accent else c.line, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                )
            }
        }
    }
}

// ---------- Switch ----------

/** 52 by 32. Whole row (label + switch) is the toggle target, at least 56dp tall. */
@Composable
fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Tindi.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TindiDims.buttonH)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { stateDescription = if (checked) "On" else "Off" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TindiType.bodyLgBold.copy(color = c.ink), modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .background(if (checked) c.accent else c.line, CircleShape)
                .then(if (checked) Modifier else Modifier.border(1.5.dp, c.muted, CircleShape)),
        ) {
            Box(
                Modifier
                    .padding(4.dp)
                    .offset(x = if (checked) 20.dp else 0.dp)
                    .size(24.dp)
                    .background(if (checked) c.onAccent else c.muted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) TIconImage(TIcon.Check, c.accent, null, size = 14.dp)
            }
        }
    }
}

// ---------- Skeleton ----------

@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(TindiDims.radiusSm)) {
    val c = Tindi.colors
    val reduced = rememberReducedMotion()
    if (reduced) {
        Box(modifier.background(c.chip, shape))
    } else {
        val t = rememberInfiniteTransition(label = "shimmer")
        val x by t.animateFloat(
            initialValue = -400f, targetValue = 1200f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "x",
        )
        val brush = Brush.linearGradient(
            colors = listOf(c.chip, c.line, c.chip),
            start = Offset(x, 0f), end = Offset(x + 400f, 200f),
        )
        Box(modifier.background(brush, shape))
    }
}

// ---------- Plate illustration ----------

@Composable
fun PlateIllustration(variant: Int, modifier: Modifier = Modifier) {
    val c = Tindi.colors
    val idx = Math.floorMod(variant, 4)
    Canvas(modifier.background(c.tiles[idx])) {
        val r = size.minDimension * 0.36f
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(c.plate, r, centre)
        drawCircle(c.plateInner, r * 0.68f, centre)
        val dot = r * 0.13f
        val rot = idx * 0.9f
        fun at(angle: Float, dist: Float) =
            Offset(centre.x + cos(angle + rot) * r * dist, centre.y + sin(angle + rot) * r * dist)
        drawCircle(c.accent, dot * 1.2f, at(-0.6f, 0.38f))
        drawCircle(c.mango, dot, at(2.4f, 0.4f))
        drawCircle(c.ink.copy(alpha = 0.25f), dot * 0.9f, at(4.3f, 0.36f))
        drawCircle(c.mango, dot * 0.6f, at(1.0f, 0.86f))
        drawCircle(c.accent, dot * 0.6f, at(3.6f, 0.86f))
        drawCircle(c.ink.copy(alpha = 0.25f), dot * 0.6f, at(5.5f, 0.86f))
    }
}

@Composable
fun SourceBadge(type: SourceType, modifier: Modifier = Modifier) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .background(c.surface, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TIconImage(if (type == SourceType.YouTube) TIcon.Play else TIcon.Pen, c.ink, null, size = 12.dp)
        Text(if (type == SourceType.YouTube) "YouTube" else "Pasted", style = TindiType.navLabel.copy(color = c.ink))
    }
}

// ---------- Navigation and FAB ----------

enum class NavItem(val route: String, val label: String, val icon: TIcon) {
    Library("library", "Library", TIcon.Library),
    Categories("categories", "Categories", TIcon.Grid),
    Profile("profile", "Profile", TIcon.User),
}

@Composable
fun BottomNav(currentRoute: String?, onSelect: (NavItem) -> Unit) {
    val c = Tindi.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().height(TindiDims.navH)) {
            NavItem.entries.forEach { item ->
                val sel = item.route == currentRoute
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = sel, role = Role.Tab, onClick = { onSelect(item) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 64.dp, height = 32.dp)
                            .background(if (sel) c.accent else Color.Transparent, RoundedCornerShape(percent = 50)),
                        contentAlignment = Alignment.Center,
                    ) {
                        TIconImage(item.icon, if (sel) c.onAccent else c.muted, null)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(item.label, style = TindiType.navLabel.copy(color = if (sel) c.ink else c.muted))
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
fun ExtendedFab(text: String, icon: TIcon, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tindi.colors
    val shape = RoundedCornerShape(TindiDims.radiusFab)
    Row(
        modifier
            .height(TindiDims.fabH)
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.28f))
            .clip(shape)
            .background(c.accent, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TindiDims.space5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TindiDims.space3),
    ) {
        TIconImage(icon, c.onAccent, null)
        Text(text, style = TindiType.bodyLgBold.copy(color = c.onAccent))
    }
}

// ---------- Sheet ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TindiSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Tindi.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = TindiDims.radiusSheet, topEnd = TindiDims.radiusSheet),
        containerColor = c.surface,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(TindiDims.space6)
                .padding(bottom = TindiDims.space2),
        ) { content() }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** Empty / error block: illustration on a tile, headline, helper copy, one action slot. */
@Composable
fun StateBlock(
    variant: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    val c = Tindi.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = TindiDims.space5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlateIllustration(variant, Modifier.size(160.dp).clip(RoundedCornerShape(TindiDims.radiusCard)))
        Spacer(Modifier.height(TindiDims.space6))
        Text(title, style = TindiType.titleSm.copy(color = c.ink, textAlign = TextAlign.Center), modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(TindiDims.space2))
        Text(message, style = TindiType.body.copy(color = c.muted, textAlign = TextAlign.Center))
        Spacer(Modifier.height(TindiDims.space6))
        action()
    }
}
