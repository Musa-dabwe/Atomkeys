package com.musa.atomkeys.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Material3 theme for the **settings app chrome only**.
 *
 * Two deliberate departures from the scaffold this replaced:
 *
 *  - **No dynamic colour.** The design doc specifies a warm sand surface and a coral accent; letting
 *    the device wallpaper repaint the settings app would put it visibly at odds with the keyboard
 *    sitting right below it in the live preview (Phase 5.2).
 *  - **No template Purple/Pink.** Those were scaffold defaults, not a design decision.
 *
 * Keyboard surfaces never come through here — they use `AtomKeyboardTheme` from `:design`, whose
 * 14 tokens have no Material equivalent.
 */
private val LightColors = lightColorScheme(
    primary = AtomCoral,
    onPrimary = AtomPaper,
    background = AtomSand,
    onBackground = AtomInk,
    surface = AtomPaper,
    onSurface = AtomInk,
    onSurfaceVariant = AtomInkMuted,
    outlineVariant = AtomHairline,
)

private val DarkColors = darkColorScheme(
    primary = AtomCoral,
    onPrimary = AtomSandDark,
    background = AtomSandDark,
    onBackground = AtomInkDark,
    surface = AtomPaperDark,
    onSurface = AtomInkDark,
    onSurfaceVariant = AtomInkMutedDark,
    outlineVariant = AtomHairlineDark,
)

@Composable
fun AtomKeysTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
