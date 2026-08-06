package com.musa.atomkeys.ime

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.musa.atomkeys.design.AtomThemes
import com.musa.atomkeys.engine.KeyboardConfig
import com.musa.atomkeys.engine.ShiftState

/** Preview showing the three visual shift states: off, on, locked. */
@Preview(name = "Shift States – off, on, locked", widthDp = 412, heightDp = 360)
@Composable
private fun ShiftStatesPreview() {
    // Off (default)
    AtomKeyboardPreview(
        config = KeyboardConfig(shift = ShiftState.OFF),
        theme = AtomThemes.Default,
        keySize = com.musa.atomkeys.engine.KeySize.Default,
        showStrip = false,
        invisible = false
    )
    // On (temporary)
    AtomKeyboardPreview(
        config = KeyboardConfig(shift = ShiftState.MANUAL),
        theme = AtomThemes.Default,
        keySize = com.musa.atomkeys.engine.KeySize.Default,
        showStrip = false,
        invisible = false
    )
    // Locked
    AtomKeyboardPreview(
        config = KeyboardConfig(shift = ShiftState.LOCKED),
        theme = AtomThemes.Default,
        keySize = com.musa.atomkeys.engine.KeySize.Default,
        showStrip = false,
        invisible = false
    )
}
