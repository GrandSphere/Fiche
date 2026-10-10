package com.grandsphere.fiche.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import com.grandsphere.fiche.domain.model.AppearanceDefaults
import com.grandsphere.fiche.domain.model.ThemeMode

private val Ink = Color(0xFF111827)
private val Mist = Color(0xFFE5E7EB)

val LocalAlternateColor = staticCompositionLocalOf { Color(AppearanceDefaults.DARK_ALTERNATE) }

@Composable
fun FicheTheme(
    themeMode: ThemeMode,
    backgroundArgb: Int = AppearanceDefaults.DARK_BACKGROUND,
    groupArgb: Int = AppearanceDefaults.DARK_GROUP,
    actionArgb: Int = AppearanceDefaults.DARK_ACTION,
    alternateArgb: Int = AppearanceDefaults.DARK_ALTERNATE,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val action = Color(actionArgb)
    val group = Color(groupArgb)
    val background = Color(backgroundArgb)
    val alternate = Color(alternateArgb)
    val onAction = if (action.luminance() > 0.5f) Color(0xFF042F2E) else Color.White
    val onBg = if (background.luminance() > 0.5f) Ink else Mist
    val scheme = if (dark) {
        darkColorScheme(
            primary = action,
            onPrimary = onAction,
            secondary = action,
            onSecondary = onAction,
            secondaryContainer = action,
            onSecondaryContainer = onAction,
            background = background,
            onBackground = onBg,
            surface = background,
            onSurface = onBg,
            surfaceVariant = group,
            surfaceContainer = background,
            surfaceContainerLow = background,
            surfaceContainerLowest = background,
            surfaceContainerHigh = background,
            surfaceContainerHighest = background,
            error = Color(0xFFF97066)
        )
    } else {
        lightColorScheme(
            primary = action,
            onPrimary = onAction,
            secondary = action,
            onSecondary = onAction,
            secondaryContainer = action,
            onSecondaryContainer = onAction,
            background = background,
            onBackground = onBg,
            surface = background,
            onSurface = onBg,
            surfaceVariant = group,
            error = Color(0xFFB42318)
        )
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.setBackgroundDrawable(ColorDrawable(backgroundArgb))
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
    CompositionLocalProvider(LocalAlternateColor provides alternate) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
