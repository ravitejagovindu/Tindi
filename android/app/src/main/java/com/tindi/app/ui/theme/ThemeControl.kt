package com.tindi.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Effective dark mode (override or system) plus the toggle, for every theme button. */
@Immutable
class ThemeControl(val isDark: Boolean, val toggle: () -> Unit)

val LocalThemeControl = staticCompositionLocalOf { ThemeControl(false) {} }
