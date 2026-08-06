package com.musa.atomkeys.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musa.atomkeys.design.AtomTheme
import com.musa.atomkeys.design.AtomThemes
import com.musa.atomkeys.design.KeycapFont
import com.musa.atomkeys.engine.Candidate
import com.musa.atomkeys.engine.Gesture
import com.musa.atomkeys.engine.KeySize
import com.musa.atomkeys.engine.KeyboardConfig
import com.musa.atomkeys.engine.KeyboardLayout
import com.musa.atomkeys.engine.Layer
import com.musa.atomkeys.engine.Row as LayoutRow
import com.musa.atomkeys.engine.RowItem
import com.musa.atomkeys.engine.ShiftState
import com.musa.atomkeys.engine.SpecialKey

/**
 * The keyboard with **no service wiring** — safe to render anywhere.
 *
 * This is roadmap 1.2's deferred item, which was held back until there was a real keyboard to
 * preview rather than the Phase 1 placeholder. Three consumers share it, which is the point: the
 * settings app's live theme/height preview (5.2), Roborazzi's screenshot matrix (8.4), and the
 * frame previews below. One renderer means a regression cannot pass a screenshot test while
 * breaking the settings preview.
 *
 * It differs from [AtomKeyboard] in exactly two ways, both because it is embedded in ordinary UI
 * rather than in an IME window: no reserved overlay headroom, and no action callback.
 */
@Composable
fun AtomKeyboardPreview(
    modifier: Modifier = Modifier,
    config: KeyboardConfig = KeyboardConfig(),
    theme: AtomTheme = AtomThemes.Default,
    keySize: KeySize = KeySize.Default,
    keycapFont: KeycapFont = KeycapFont.ATOM,
    candidates: List<Candidate> = Candidate.Placeholder,
    showStrip: Boolean = true,
    invisible: Boolean = false,
    pressedKeyId: String? = null,
    gesture: Gesture? = null,
) {
    AtomKeyboard(
        config = config,
        modifier = modifier,
        theme = theme,
        keySize = keySize,
        keycapFont = keycapFont,
        candidates = candidates,
        showStrip = showStrip,
        invisible = invisible,
        reserveOverlayHeadroom = false,
        pressedKeyId = pressedKeyId,
        gesture = gesture,
    )
}

/*
 * ---------------------------------------------------------------------------------------------
 * Frames L1–L8, one preview each, named to match `AtomKeyboard.dc.html` so they can be compared
 * against it side by side. This is Phase 3.2's gate.
 *
 * Widths are 412dp — the design doc's own preview width — so proportions match the source directly.
 * ---------------------------------------------------------------------------------------------
 */

/** **L1** Letters, with the press bubble on a key. `q` is held. */
@Preview(name = "L1 · Letters + press bubble", widthDp = 412, heightDp = 340)
@Composable
private fun L1LettersPressed() = Frame("L1 · Letters, press bubble on q") {
    // A top-row key deliberately: that is the only position where the bubble leaves the keyboard's
    // bounds, which is what spike 0.2's headroom band exists for. Looked up rather than hardcoded
    // as "r0c0", so enabling the numbers row cannot silently point this at a digit.
    AtomKeyboardPreview(pressedKeyId = keyIdFor("q"))
}

/** Resolves a key's positional id from its label, so previews never hardcode one. */
internal fun keyIdFor(label: String, config: KeyboardConfig = KeyboardConfig()): String? =
    KeyboardLayout.rows(config)
        .filterIsInstance<LayoutRow.Keys>()
        .flatMap { it.items }
        .filterIsInstance<RowItem.Key>()
        .firstOrNull { it.label == label }
        ?.id

/** **L2** Shift locked, case-sensitive keycaps. */
@Preview(name = "L2 · Shift locked", widthDp = 412, heightDp = 340)
@Composable
private fun L2ShiftLocked() = Frame("L2 · Shift locked, uppercase keycaps") {
    AtomKeyboardPreview(config = KeyboardConfig(shift = ShiftState.LOCKED))
}

/** **L3** Symbols layer. */
@Preview(name = "L3 · Symbols", widthDp = 412, heightDp = 340)
@Composable
private fun L3Symbols() = Frame("L3 · Symbols page 1") {
    AtomKeyboardPreview(config = KeyboardConfig(layer = Layer.SYMBOLS_1))
}

/** Symbols page two — the confirmed `#+=` extension. Not a design-doc frame. */
@Preview(name = "L3b · Symbols page 2 (extension)", widthDp = 412, heightDp = 340)
@Composable
private fun L3bSymbolsTwo() = Frame("L3b · Symbols page 2 — extension, not in the doc") {
    AtomKeyboardPreview(config = KeyboardConfig(layer = Layer.SYMBOLS_2))
}

/** **L4** Emoji layer. */
@Preview(name = "L4 · Emoji", widthDp = 412, heightDp = 340)
@Composable
private fun L4Emoji() = Frame("L4 · Emoji") {
    AtomKeyboardPreview(config = KeyboardConfig(layer = Layer.EMOJI))
}

/** **L5** Numbers row extension — note the per-key hints disappear. */
@Preview(name = "L5 · Numbers row", widthDp = 412, heightDp = 400)
@Composable
private fun L5NumbersRow() = Frame("L5 · Numbers row (hints suppressed)") {
    AtomKeyboardPreview(config = KeyboardConfig(numbersRow = true))
}

/** **L6** Minimal mode — bottom row hidden, no middle rule. */
@Preview(name = "L6 · Minimal", widthDp = 412, heightDp = 300)
@Composable
private fun L6Minimal() = Frame("L6 · Minimal — no bottom row, no rule") {
    AtomKeyboardPreview(config = KeyboardConfig(minimal = true, middleRule = false))
}

/** **L7** Invisible keyboard — transparent keys, dashed edges, transparent labels. */
@Preview(name = "L7 · Invisible", widthDp = 412, heightDp = 340)
@Composable
private fun L7Invisible() = Frame("L7 · Invisible") {
    AtomKeyboardPreview(invisible = true)
}

/** **L8** Auto-correct off (`AC✕`), with comma as the special key. */
@Preview(name = "L8 · Auto-correct off", widthDp = 412, heightDp = 340)
@Composable
private fun L8AutoCorrectOff() = Frame("L8 · AC✕, comma special key") {
    AtomKeyboardPreview(
        config = KeyboardConfig(autoCorrect = false, specialKey = SpecialKey.COMMA),
    )
}

/** All five selectable themes at a glance, on the letters layer. */
@Preview(name = "Themes · all five selectable", widthDp = 412, heightDp = 1500)
@Composable
private fun ThemeSweep() {
    Column(
        Modifier.background(Color(0xFFF5F4EF)).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AtomThemes.selectable.forEach { theme ->
            BasicText(theme.name, Modifier.padding(start = 12.dp), style = CaptionStyle)
            AtomKeyboardPreview(theme = theme)
        }
    }
}

/** The four key heights, rendered as the design doc's height section does — minimal, no strip. */
@Preview(name = "Heights · S M L XL", widthDp = 412, heightDp = 1100)
@Composable
private fun HeightSweep() {
    Column(
        Modifier.background(Color(0xFFF5F4EF)).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KeySize.entries.forEach { size ->
            BasicText(
                "${size.name} · ${size.heightDp}dp",
                Modifier.padding(start = 12.dp),
                style = CaptionStyle,
            )
            AtomKeyboardPreview(
                config = KeyboardConfig(minimal = true),
                keySize = size,
                showStrip = false,
            )
        }
    }
}

/** Caption plus the keyboard, so a preview identifies itself when they are viewed as a grid. */
@Composable
private fun Frame(label: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF5F4EF)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(label, Modifier.padding(start = 12.dp, top = 8.dp), style = CaptionStyle)
        content()
    }
}

private val CaptionStyle = TextStyle(fontSize = 11.sp, color = Color(0xFF6B6559))
