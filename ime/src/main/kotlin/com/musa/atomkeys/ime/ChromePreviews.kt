package com.musa.atomkeys.ime

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.musa.atomkeys.design.AtomThemes
import com.musa.atomkeys.engine.KeySize
import com.musa.atomkeys.engine.KeyboardConfig

/** Preview for the candidate strip component. */
@Preview(name = "Candidate Strip", widthDp = 412, heightDp = 200)
@Composable
private fun CandidateStripPreview() {
    // Render the keyboard preview with the strip visible; other UI elements are okay.
    AtomKeyboardPreview(
        config = KeyboardConfig(),
        theme = AtomThemes.Default,
        keySize = KeySize.Default,
        showStrip = true,
        invisible = false,
    )
}

/** Preview for the middle rule shimmer animation. */
@Preview(name = "Middle Rule", widthDp = 412, heightDp = 100)
@Composable
private fun MiddleRulePreview() {
    // Directly invoke the private MiddleRule composable (same file, so accessible).
    MiddleRule(theme = AtomThemes.Default, keySize = KeySize.Default)
}
